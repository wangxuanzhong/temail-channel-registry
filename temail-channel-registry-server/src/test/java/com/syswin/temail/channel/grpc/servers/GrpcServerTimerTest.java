package com.syswin.temail.channel.grpc.servers;

import com.syswin.temail.channel.account.CommonDataGeneUtil;
import com.syswin.temail.channel.account.beans.CdtpServer;
import com.syswin.temail.channel.account.beans.ComnRespData;
import com.syswin.temail.channel.account.service.TemailAcctStsService;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@Slf4j
public class GrpcServerTimerTest {

  private ExecutorService executors = Executors.newFixedThreadPool(5);

  private TemailAcctStsService temailAcctStsService;

  private GrpcServerTimer grpcServerTimer;

  private final List<GatewayServer> serverskeepHeart = new LinkedList<>();
  //0.2 percent
  private final List<GatewayServer> serversLoseHeart = new LinkedList<>();

  private int serverskeepHeartSize = 0;

  private final List<GatewayServer> allServers = new LinkedList<>();

  private final int gatewayServerPoolSize = 10;

  private final Random random = new Random(20);

  private final double loseHeartBeatRatio = 0.5;

  @Before
  public void setUp() throws Exception {
    GatewayServer gatewayServer = null;
    for (int i = 0; i < gatewayServerPoolSize; i++) {
      gatewayServer = buildAGatewayServer();
      if (random.nextDouble() < loseHeartBeatRatio) {
        serversLoseHeart.add(gatewayServer);
      } else {
        serverskeepHeart.add(gatewayServer);
      }
    }
    allServers.addAll(serversLoseHeart);
    allServers.addAll(serverskeepHeart);
    serverskeepHeartSize = serverskeepHeart.size();
    printServerList("serversLoseHeart", serversLoseHeart);
    printServerList("serverskeepHeart", serverskeepHeart);
    printServerList("allServers", allServers);
    temailAcctStsService = Mockito.mock(TemailAcctStsService.class);
    when(temailAcctStsService.offLineTheServer(buildACdtpServer())).thenReturn(new ComnRespData("OK", true));
    grpcServerTimer = new GrpcServerTimer(temailAcctStsService,
      new Consumer() {
        @Override
        public void accept(Object o) {
          if (o instanceof GatewayServer) {
            serversLoseHeart.remove(o);
            serverskeepHeart.remove(o);
            log.info("**** **** **** remove timeout server : {}-{} **** **** ****",
                ((GatewayServer) o).getIp(), ((GatewayServer) o).getProcessId());
          }
        }
      });
  }

  @Test
  public void addHeartBeatTimeout() throws Exception {
    //for serverskeepHeart elements, we keep sending heartbeat
    executors.submit(() -> {
      try {
        for(int i = 0; i < 4; i++) {
           for (GatewayServer server : serverskeepHeart) {
             log.info("{} - add heart beat for : {}-{}", "serversKeepHeart",
                 server.getIp(), server.getProcessId());
             grpcServerTimer.addHeartBeatTimeout(server);
             TimeUnit.MILLISECONDS.sleep(random.nextInt(10));
           }
           TimeUnit.SECONDS.sleep(10);
         }
         log.info("leave task1!");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    log.info("add task1 successfully.");
    executors.submit(() -> {
      try {
        for(int i = 0; i < 1; i++) {
           for (GatewayServer server : serversLoseHeart) {
             log.info("{} - add heart beat for : {}-{}", "serversLoseHeart",
                 server.getIp(), server.getProcessId());
             grpcServerTimer.addHeartBeatTimeout(server);
             TimeUnit.MILLISECONDS.sleep(random.nextInt(10));
           }
           TimeUnit.SECONDS.sleep(15);
         }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    log.info("add task2 successfully.");

    await().atMost(90, TimeUnit.SECONDS).until(() -> {
      return serversLoseHeart.isEmpty()
          && serverskeepHeartSize == serverskeepHeart.size();
    });
  }

  public GatewayServer buildAGatewayServer() {
    return GatewayServer.newBuilder().
        setIp(CommonDataGeneUtil.extractIp()).
        setProcessId(CommonDataGeneUtil.extractChar(CommonDataGeneUtil.ExtractType.NUM, 32)).
        setCurStateBeginTime(CommonDataGeneUtil.extractChar(CommonDataGeneUtil.ExtractType.NUM, 10))
        .build();
  }

  public CdtpServer buildACdtpServer() {
    return new CdtpServer(
        CommonDataGeneUtil.extractIp(),
        CommonDataGeneUtil.extractChar(CommonDataGeneUtil.ExtractType.NUM, 32),
        CommonDataGeneUtil.extractChar(CommonDataGeneUtil.ExtractType.NUM, 10), null);
  }

  public void printServerList(String listName, List<GatewayServer> servers) {
    log.info("{} size: {}", listName, servers.size());
    for (GatewayServer server : servers) {
      log.info("{}-{}", server.getIp(), server.getProcessId());
    }
  }

}