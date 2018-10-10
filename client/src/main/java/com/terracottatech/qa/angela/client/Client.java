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
import com.terracottatech.qa.angela.agent.kit.LocalKitManager;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.client.util.IgniteClientHelper;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.cluster.Cluster;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  private final int subClientPid;
  private boolean closed = false;


  Client(Ignite ignite, InstanceId instanceId, String nodeName, TerracottaCommandLineEnvironment tcEnv, LocalKitManager localKitManager) {
    this.instanceId = instanceId;
    this.nodeName = nodeName;
    this.ignite = ignite;
    this.subClientPid = spawnSubClient(
        Objects.requireNonNull(tcEnv),
        Objects.requireNonNull(localKitManager)
    );
  }

  private int spawnSubClient(TerracottaCommandLineEnvironment tcEnv, LocalKitManager localKitManager) {
    logger.info("Spawning client '{}' on {}", instanceId, nodeName);

    try {
      IgniteClientHelper.uploadClientJars(ignite, nodeName, instanceId, listClasspathFiles(localKitManager));

      int pid = IgniteClientHelper.executeRemotely(ignite, nodeName, (IgniteCallable<Integer>) () -> Agent.CONTROLLER.spawnClient(instanceId, tcEnv));
      logger.info("client '{}' on {} started with PID {}", instanceId, nodeName, pid);

      return pid;
    } catch (Exception e) {
      throw new RuntimeException("Cannot create client on " + nodeName, e);
    }
  }

  private List<File> listClasspathFiles(LocalKitManager localKitManager) throws IOException {
    List<File> files = new ArrayList<>();

    File javaHome = new File(System.getProperty("java.home"));
    String[] classpathJarNames = System.getProperty("java.class.path").split(File.pathSeparator);
    for (String classpathJarName : classpathJarNames) {
      if (classpathJarName.startsWith(javaHome.getPath()) || classpathJarName.startsWith(javaHome.getParentFile()
          .getPath())) {
        logger.debug("skipping {}", classpathJarName);
        continue; // part of the JVM, skip it
      }

      File fileToUpload = checkKitContents(localKitManager, classpathJarName);
      logger.debug("file to upload : {}", fileToUpload);

      files.add(fileToUpload);
    }

    return files;
  }

  private File checkKitContents(LocalKitManager localKitManager, String classpathJarName) throws IOException {
    File fileInKit = localKitManager.findEquivalent(classpathJarName);
    if (fileInKit != null) {
      logger.info("Substituting '{}' with kit's equivalent JAR '{}'", new File(classpathJarName).getName(), fileInKit);
      return fileInKit;
    } else {
      return new File(classpathJarName);
    }
  }

  Future<Void> submit(ClientJob clientJob) {
    IgniteFuture<Void> igniteFuture = IgniteClientHelper.executeRemotelyAsync(ignite, instanceId.toString(), (IgniteCallable<Void>) () -> {
      clientJob.run(new Cluster(ignite));
      return null;
    });
    return new ClientJobFuture(igniteFuture);
  }

  public RemoteFolder browse(String root) {
    return new RemoteFolder(ignite, instanceId.toString(), null, root);
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;

    if (!ClusterFactory.SKIP_UNINSTALL) {
      logger.info("Wiping up client '{}' on {}", instanceId, nodeName);
      IgniteClientHelper.executeRemotely(ignite, nodeName, (IgniteRunnable)() -> Agent.CONTROLLER.destroyClient(instanceId, subClientPid));
    }
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
