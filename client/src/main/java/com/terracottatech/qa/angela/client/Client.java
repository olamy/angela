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
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.FileMetadata;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Ludovic Orban
 */
public class Client implements Closeable {

  private final static Logger logger = LoggerFactory.getLogger(Client.class);

  private final InstanceId instanceId;
  private final String nodeName;
  private final Ignite ignite;
  private final String subNodeName;
  private final URI clusterUri;
  private final int subClientPid;
  private boolean closed = false;

  Client(Ignite ignite, InstanceId instanceId, String nodeName, URI clusterUri) {
    this.instanceId = instanceId;
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.subNodeName = nodeName + "_" + UUID.randomUUID().toString();
    this.clusterUri = clusterUri;
    this.subClientPid = spawnSubClient();
  }

  private int spawnSubClient() {
    logger.info("Spawning client '{}' on {}", subNodeName, nodeName);
    try {
      final BlockingQueue<Object> queue = ignite.queue(instanceId + "@file-transfer-queue@" + subNodeName, 100, new CollectionConfiguration());

      ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
      IgniteFuture<Void> remoteDownloadFuture = ignite.compute(location).broadcastAsync((IgniteRunnable)() -> Agent.CONTROLLER.downloadClient(instanceId, subNodeName));

      uploadClasspath(queue);
      remoteDownloadFuture.get();

      Collection<Integer> results = ignite.compute(location).broadcast((IgniteCallable<Integer>) () -> Agent.CONTROLLER.spawnClient(instanceId, subNodeName));
      int pid = results.iterator().next();
      logger.info("client '{}' on {} started with PID {}", subNodeName, nodeName, pid);

      return pid;
    } catch (Exception e) {
      throw new RuntimeException("Cannot create client on " + nodeName, e);
    }
  }

  private void uploadClasspath(BlockingQueue<Object> queue) throws InterruptedException, IOException {
    File javaHome = new File(System.getProperty("java.home"));
    String[] classpathJarNames = System.getProperty("java.class.path").split(File.pathSeparator);
    for (String classpathJarName : classpathJarNames) {
      if (classpathJarName.startsWith(javaHome.getPath()) || classpathJarName.startsWith(javaHome.getParentFile().getPath())) {
        logger.debug("skipping {}", classpathJarName);
        continue; // part of the JVM, skip it
      }

      uploadFile(queue, new File(classpathJarName), null);
    }
    queue.put(Boolean.TRUE); // end of upload marker
  }

  private void uploadFile(BlockingQueue<Object> queue, File file, String path) throws InterruptedException, IOException {
    FileMetadata fileMetadata = new FileMetadata(path, file);
    if (!file.exists()) {
      logger.debug("skipping upload of non-existent classpath entry {}", fileMetadata);
      return;
    }
    queue.put(fileMetadata);
    logger.debug("uploading {}", fileMetadata);

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
      logger.debug("uploaded {}", fileMetadata);
    }
  }

  public Future<Void> submit(ClientJob clientJob) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", subNodeName);
    IgniteFuture<?> igniteFuture = ignite.compute(location).broadcastAsync((IgniteCallable<Void>) () -> {
      clientJob.run(new Context(nodeName, ignite, instanceId, clusterUri));
      return null;
    });
    return new ClientJobFuture(igniteFuture);
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;

    logger.info("Wiping up client '{}' on {}", subNodeName, nodeName);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    ignite.compute(location).broadcast((IgniteRunnable) () -> {
      Agent.CONTROLLER.destroyClient(instanceId, subNodeName, subClientPid);
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
