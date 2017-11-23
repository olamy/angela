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
package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.client.Context;
import com.terracottatech.qa.angela.common.kit.KitManager;
import com.terracottatech.qa.angela.common.util.FileMetadata;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import org.apache.commons.io.FileUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Ludovic Orban
 */
public class ClientControl implements Closeable {

  private final static Logger logger = LoggerFactory.getLogger(ClientControl.class);

  private final String topologyId;
  private final String nodeName;
  private final Ignite ignite;
  private final String subNodeName;
  private final int subClientPid;
  private boolean closed = false;

  ClientControl(String topologyId, String nodeName, Ignite ignite) {
    this.topologyId = topologyId;
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.subNodeName = nodeName + "_" + UUID.randomUUID().toString();
    this.subClientPid = spawnSubClient();
  }

  private int spawnSubClient() {
    logger.info("Spawning sub-client '{}' on {}", subNodeName, nodeName);
    try {
      final BlockingQueue<Object> queue = ignite.queue("queue-upload-" + subNodeName, 100, new CollectionConfiguration());

      ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
      IgniteFuture<Void> remoteDownloadFuture = ignite.compute(location).broadcastAsync(remoteClientDownloadClasspathLambda(subNodeName, queue));
      uploadClasspath(queue);
      remoteDownloadFuture.get();

      Collection<Integer> results = ignite.compute(location).broadcast((IgniteCallable<Integer>) () -> {
        try {
          JavaLocationResolver javaLocationResolver = new JavaLocationResolver();
          List<String> j8Homes = javaLocationResolver.resolveJava8Location();
          String j8Home = j8Homes.get(0);

          final AtomicBoolean started = new AtomicBoolean(false);
          logger.info("Spawning sub-agent " + Arrays.asList(j8Home + "/bin/java", "-classpath", buildClasspath(topologyId, subNodeName), "-Dtc.qa.nodeName=" + subNodeName, Agent.class.getName()));
          ProcessExecutor processExecutor = new ProcessExecutor().command(j8Home + "/bin/java", "-classpath", buildClasspath(topologyId, subNodeName), "-Dtc.qa.nodeName=" + subNodeName, Agent.class.getName())
                  .redirectOutput(new LogOutputStream() {
                    @Override
                    protected void processLine(String s) {
                      System.out.println(s);
                      if (s.startsWith("Registered node ")) {
                        started.set(true);
                      }
                    }
                  }).directory(new File(subClientDir(topologyId, subNodeName)));
          StartedProcess startedProcess = processExecutor.start();

          while (!started.get()) {
            Thread.sleep(1000);
          }

          return PidUtil.getPid(startedProcess.getProcess());
        } catch (Exception e) {
          throw new RuntimeException("Error spawning sub-client " + subNodeName, e);
        }
      });
      int pid = results.iterator().next();
      logger.info("sub-agent '{}' on {} started with PID {}", subNodeName, nodeName, pid);

      return pid;
    } catch (Exception e) {
      throw new RuntimeException("Cannot create sub-client on " + nodeName, e);
    }
  }

  private static String buildClasspath(String topologyId, String subNodeName) {
    File subClientDir = new File(subClientDir(topologyId, subNodeName), "lib");
    String[] cpentries = subClientDir.list();

    StringBuilder sb = new StringBuilder();
    for (String cpentry : cpentries) {
      sb.append("lib/" + cpentry).append(File.pathSeparator);
    }

    // if
    //   file:/Users/lorban/.m2/repository/org/slf4j/slf4j-api/1.7.22/slf4j-api-1.7.22.jar!/org/slf4j/Logger.class
    // else
    //   /work/terracotta/irepo/lorban/angela/agent/target/classes/com/terracottatech/qa/angela/agent/Agent.class

    String agentClassPath = Agent.class.getResource('/' + Agent.class.getName().replace('.', '/') + ".class").getPath();

    if (agentClassPath.startsWith("file:")) {
      sb.append(agentClassPath.substring("file:".length(), agentClassPath.lastIndexOf('!')));
    } else {
      sb.append(agentClassPath.substring(0, agentClassPath.lastIndexOf(Agent.class.getName().replace('.', '/'))));
    }

    return sb.toString();
  }

  private static String subClientDir(String topologyId, String subNodeName) {
    return KitManager.KITS_DIR + "/" + topologyId.replace(':', '_') + "/" + subNodeName;
  }

  private void uploadClasspath(BlockingQueue<Object> queue) throws InterruptedException, IOException {
    File javaHome = new File(System.getProperty("java.home"));
    String[] classpathJarNames = System.getProperty("java.class.path").split(File.pathSeparator);
    for (String classpathJarName : classpathJarNames) {
      if (classpathJarName.startsWith(javaHome.getPath()) || classpathJarName.startsWith(javaHome.getParentFile().getPath())) {
        logger.debug("skipping " + classpathJarName);
        continue; // part of the JVM, skip it
      }

      uploadFile(queue, new File(classpathJarName), null);
    }
    queue.put(Boolean.TRUE); // end of upload marker
  }

  private void uploadFile(BlockingQueue<Object> queue, File file, String path) throws InterruptedException, IOException {
    FileMetadata fileMetadata = new FileMetadata(path, file);
    queue.put(fileMetadata);
    logger.debug("uploading " + fileMetadata);

    if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File _file : files) {
        String parentPath = path == null ? "" : path + "/";
        uploadFile(queue, _file, parentPath + file.getName());
      }
    } else {
      byte[] buffer = new byte[64 * 1024];
      try (FileInputStream fis = new FileInputStream(file)) {
        while (true) {
          int read = fis.read(buffer);
          if (read < 0) {
            break;
          }
          byte[] toSend;
          if (read != buffer.length) {
            toSend = new byte[read];
            System.arraycopy(buffer, 0, toSend, 0, read);
          } else {
            toSend = buffer;
          }
          queue.put(toSend);
        }
      }
      logger.debug("uploaded " + fileMetadata);
    }
  }

  private IgniteRunnable remoteClientDownloadClasspathLambda(String subNodeName, BlockingQueue<Object> queue) {
    return () -> {
      try {
        File subClientDir = new File(subClientDir(topologyId, subNodeName), "lib");
        logger.info("Downloading sub-agent '{}' into {}", subNodeName, subClientDir);
        if (!subClientDir.mkdirs()) {
          throw new RuntimeException("Cannot create sub-client directory '" + subClientDir + "' on " + nodeName);
        }

        while (true) {
          Object read = queue.take();
          if (read.equals(Boolean.TRUE)) {
            logger.info("Downloaded sub-agent '{}' into {}", subNodeName, subClientDir);
            break;
          }

          FileMetadata fileMetadata = (FileMetadata) read;
          logger.debug("downloading " + fileMetadata);
          if (!fileMetadata.isDirectory()) {
            long readFileLength = 0L;
            File file = new File(subClientDir + "/" + fileMetadata.getPathName());
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
              while (true) {
                byte[] buffer = (byte[]) queue.take();
                fos.write(buffer);
                readFileLength += buffer.length;
                if (readFileLength == fileMetadata.getLength()) {
                  break;
                }
                if (readFileLength > fileMetadata.getLength()) {
                  throw new RuntimeException("Error creating client classpath on " + nodeName);
                }
              }
            }
            logger.debug("downloaded " + fileMetadata);
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Cannot upload sub-client on " + nodeName, e);
      }
    };
  }

  public Future<Void> submit(ClientJob clientJob) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", subNodeName);
    IgniteFuture<Void> igniteFuture = ignite.compute(location).broadcastAsync((IgniteRunnable) () -> {clientJob.run(new Context(nodeName, ignite));});
    return new ClientJobFuture<>(igniteFuture);
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;

    logger.info("Wiping up sub-agent '{}' on {}", subNodeName, nodeName);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    ignite.compute(location).broadcast((IgniteRunnable) () -> {
      try {
        logger.info("killing sub-agent '{}' with PID {}", subNodeName, subClientPid);
        PidProcess pidProcess = Processes.newPidProcess(subClientPid);
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(pidProcess, 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);

        File subAgentRoot = new File(subClientDir(topologyId, subNodeName));
        logger.info("cleaning up directory structure '{}' of sub-agent {}", subAgentRoot, subNodeName);
        FileUtils.deleteDirectory(subAgentRoot);
      } catch (Exception e) {
        throw new RuntimeException("Error cleaning up sub-client", e);
      }
    });
  }

  static class ClientJobFuture<V> implements Future<V> {
    private final IgniteFuture<V> igniteFuture;
    ClientJobFuture(IgniteFuture<V> igniteFuture) {
      this.igniteFuture = igniteFuture;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return igniteFuture.cancel();
    }

    @Override
    public boolean isCancelled() {
      return igniteFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
      return igniteFuture.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      return igniteFuture.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return igniteFuture.get(timeout, unit);
    }
  }

}
