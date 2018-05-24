package org.apache.hadoop.hive.llap.daemon.impl.comparator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.llap.DaemonId;
import org.apache.hadoop.hive.llap.daemon.QueryFailedHandler;
import org.apache.hadoop.hive.llap.daemon.impl.AMReporter;
import org.apache.hadoop.hive.llap.daemon.impl.QueryIdentifier;
import org.apache.hadoop.hive.llap.protocol.LlapTaskUmbilicalProtocol;
import org.apache.hadoop.io.Text;
import org.apache.hive.common.util.RetryTestRunner;
import org.apache.tez.dag.records.TezTaskAttemptID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RetryTestRunner.class)
public class TestAMReporter {
  @Test(timeout = 5000)
  public void testMultipleAM() throws InterruptedException {
    int numExecutors = 1;
    int maxThreads = 1;
    AtomicReference<InetSocketAddress> localAddress = new AtomicReference<>(new InetSocketAddress(12345));
    QueryFailedHandler queryFailedHandler = mock(QueryFailedHandler.class);
    Configuration conf = new Configuration();
    HiveConf.setVar(conf, HiveConf.ConfVars.LLAP_DAEMON_AM_LIVENESS_HEARTBEAT_INTERVAL_MS, "100ms");
    DaemonId daemonId = mock(DaemonId.class);
    when(daemonId.getUniqueNodeIdInCluster()).thenReturn("nodeId");
    SocketFactory socketFactory = mock(SocketFactory.class);
    AMReporterForTest amReporter = new AMReporterForTest(numExecutors, maxThreads, localAddress,
      queryFailedHandler, conf, daemonId, socketFactory);
    amReporter.init(conf);
    amReporter.start();

    // register two tasks of same query but different am
    int am1Port = 123;
    int am2Port = 456;
    String am1Location = "am1";
    String am2Location = "am2";
    String umbilicalUser = "user";
    QueryIdentifier queryId = new QueryIdentifier("app", 0);
    amReporter.registerTask(am1Location, am1Port, umbilicalUser, null, queryId,
      mock(TezTaskAttemptID.class));
    amReporter.registerTask(am2Location, am2Port, umbilicalUser, null, queryId,
      mock(TezTaskAttemptID.class));

    Thread.currentThread().sleep(2000);
    // verify both am get node heartbeat
    assertEquals(2, amReporter.heartbeatedHost.size());

    amReporter.stop();
  }

  class AMReporterForTest extends AMReporter {
    Set<AMNodeInfo> heartbeatedHost = new HashSet<>();

    public AMReporterForTest(int numExecutors, int maxThreads, AtomicReference<InetSocketAddress> localAddress, QueryFailedHandler queryFailedHandler, Configuration conf, DaemonId daemonId, SocketFactory socketFactory) {
      super(numExecutors, maxThreads, localAddress, queryFailedHandler, conf, daemonId, socketFactory);
    }

    @Override
    protected LlapTaskUmbilicalProtocol createUmbilical(final AMNodeInfo amNodeInfo) throws IOException {
      LlapTaskUmbilicalProtocol umbilical = mock(LlapTaskUmbilicalProtocol.class);
      doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
          heartbeatedHost.add(amNodeInfo);
          return null;
        }
      }).when(umbilical).nodeHeartbeat(any(Text.class), any(Text.class), anyInt(),
        any(LlapTaskUmbilicalProtocol.TezAttemptArray.class));
      return umbilical;
    }
  }
}