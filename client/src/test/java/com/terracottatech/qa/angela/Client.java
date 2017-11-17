/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.terracottatech.qa.angela;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Ludovic Orban
 */
public class Client {

  public static void main(String[] args) throws Exception {
    IgniteConfiguration cfg = new IgniteConfiguration();

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    ipFinder.setAddresses(Arrays.asList("tc-perf-001.eur.ad.sag"));
    spi.setIpFinder(ipFinder);

    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);

    Ignite ignite = Ignition.start(cfg);

    new Thread(() -> {
      try {
        System.out.println("Sending file...");
        sendFile(ignite.queue("file", 16, new CollectionConfiguration()), "target/ignite-sample-1.0.0-SNAPSHOT.jar");
        System.out.println("file sent!");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).start();



/*
    ignite.compute().broadcast((IgniteRunnable) () -> System.out.println("hi everywhere!"));

    ignite.compute(ignite.cluster().forAttribute("nodename", "tc-perf-001.eur.ad.sag")).broadcast((IgniteRunnable) () -> System.out.println("hi on node3!"));
    ignite.compute(ignite.cluster().forAttribute("nodename", "tc-perf-002.eur.ad.sag")).broadcast((IgniteRunnable) () -> System.out.println("hi on node4!"));
*/

    System.out.println("Remotely updating process...");
    try {
      ignite.compute(ignite.cluster().forAttribute("nodename", "tc-perf-002.eur.ad.sag")).broadcast((IgniteRunnable) () -> {
        try {
          System.out.println("updating process jar");
          recvFile(ignite.queue("file", 16, new CollectionConfiguration()), "ignite-sample-1.0.0-SNAPSHOT.jar.part");
          new File("ignite-sample-1.0.0-SNAPSHOT.jar.part").renameTo(new File("ignite-sample-1.0.0-SNAPSHOT.jar"));

          System.out.println("respawning current process");
          List<String> cmdLine = new ArrayList<>();
          cmdLine.add("java");
          cmdLine.add("-jar");
          cmdLine.add("ignite-sample-1.0.0-SNAPSHOT.jar");
//          cmdLine.addAll(Arrays.asList(System.getProperty("sun.java.command").split(" ")));
          new ProcessExecutor().command(cmdLine).start();

          new Thread(() -> {
            try {
              Thread.sleep(2000);
              System.out.println("clean shutdown myself");
              System.exit(0);
              Thread.sleep(4000);
              System.out.println("killing myself");
              ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(PidUtil.getMyPid()), 1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }).start();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } catch (IgniteException e) {
      System.out.println("got exception : " + e);
    }


    ignite.close();
  }

  private static void recvFile(IgniteQueue<Object> fileQueue, String filename) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(filename)) {
      while (true) {
        byte[] buffer = (byte[]) fileQueue.take();
        if (buffer.length == 0) {
          break;
        }
        fos.write(buffer);
      }
    }
  }

  private static void sendFile(IgniteQueue<Object> fileQueue, String filename) throws IOException {
    try (FileInputStream fis = new FileInputStream(filename)) {
      byte[] buffer = new byte[64 * 1024];
      while (true) {
        int read = fis.read(buffer);
        if (read < 0) {
          break;
        }
        if (read == 0) {
          continue;
        }
        if (read < 512) {
          byte[] betterBuf = new byte[read];
          System.arraycopy(buffer, 0, betterBuf, 0, read);
          buffer = betterBuf;
        }
        fileQueue.add(buffer);
      }
      fileQueue.add(new byte[0]); // EOF marker
    }
  }

}
