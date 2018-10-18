package com.terracottatech.qa.angela.client.util;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.agent.client.RemoteClientManager;
import com.terracottatech.qa.angela.agent.kit.RemoteKitManager;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.AngelaVersion;
import com.terracottatech.qa.angela.common.util.FileMetadata;
import com.terracottatech.qa.angela.common.util.IgniteCommonHelper;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class IgniteClientHelper {

  private final static Logger logger = LoggerFactory.getLogger(IgniteClientHelper.class);

  public static void executeRemotely(Ignite ignite, String hostname, IgniteRunnable job) {
    executeRemotelyAsync(ignite, hostname, job).get();
  }

  public static IgniteFuture<Void> executeRemotelyAsync(Ignite ignite, String hostname, IgniteRunnable job) {
    IgniteClientHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    return ignite.compute(location).runAsync(job);
  }

  public static <R> R executeRemotely(Ignite ignite, String hostname, IgniteCallable<R> job) {
    return executeRemotelyAsync(ignite, hostname, job).get();
  }

  public static <R> IgniteFuture<R> executeRemotelyAsync(Ignite ignite, String hostname, IgniteCallable<R> job) {
    IgniteClientHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    return ignite.compute(location).callAsync(job);
  }

  private static void checkAgentHealth(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<Map<String, ?>>> future = ignite.compute(location)
        .broadcastAsync((IgniteCallable<Map<String, ?>>)() -> Agent.CONTROLLER.getNodeAttributes());
    try {
      Collection<Map<String, ?>> attributeMaps = future.get(60, TimeUnit.SECONDS);
      if (attributeMaps.size() != 1) {
        throw new IllegalStateException("Detected " + attributeMaps.size() + " agents with node name [" + nodeName + "] while expected exactly one");
      }
      Map<String, ?> attributeMap = attributeMaps.iterator().next();
      if (!nodeName.equals(attributeMap.get("nodename"))) {
        throw new IllegalStateException("Agent " + nodeName + " mistakenly identifies itself as " + attributeMap.get("nodename"));
      }
      if (!AngelaVersion.getAngelaVersion().equals(attributeMap.get("angela.version"))) {
        throw new IllegalStateException("Agent " + nodeName + " is running version [" + attributeMap.get("angela.version") + "]" +
            " but the expected version is [" + AngelaVersion.getAngelaVersion() + "]");
      }
    } catch (IgniteException e) {
      throw new IllegalStateException("Node with name '" + nodeName + "' not found in the cluster", e);
    }
  }

  public static void uploadKit(Ignite ignite, String hostname, InstanceId instanceId, Distribution distribution,
                               String kitInstallationName, File kitInstallationPath) throws IOException, InterruptedException {
    IgniteFuture<Void> remoteDownloadFuture = executeRemotelyAsync(ignite, hostname,
        () -> Agent.CONTROLLER.downloadFiles(instanceId, new RemoteKitManager(instanceId, distribution, kitInstallationName)
            .getKitInstallationPath()
            .getParentFile()));

    uploadFiles(ignite, instanceId, Collections.singletonList(kitInstallationPath), remoteDownloadFuture);
  }

  public static void uploadClientJars(Ignite ignite, String hostname, InstanceId instanceId, List<File> filesToUpload) throws IOException, InterruptedException {
    IgniteFuture<Void> remoteDownloadFuture = executeRemotelyAsync(ignite, hostname,
        () -> Agent.CONTROLLER.downloadFiles(instanceId, new RemoteClientManager(instanceId).getClientClasspathRoot()));

    uploadFiles(ignite, instanceId, filesToUpload, remoteDownloadFuture);
  }

  private static void uploadFiles(Ignite ignite, InstanceId instanceId, List<File> files, IgniteFuture<Void> remoteDownloadFuture) throws InterruptedException, IOException {
    try {
      BlockingQueue<Object> queue = IgniteCommonHelper.fileTransferQueue(ignite, instanceId);
      for (File file : files) {
        uploadFile(remoteDownloadFuture, queue, file, null);
      }
      queue.put(Boolean.TRUE); // end of upload marker
    } finally {
      remoteDownloadFuture.get();
    }
  }

  private static void uploadFile(IgniteFuture<Void> remoteDownloadFuture, BlockingQueue<Object> queue, File file, String path) throws InterruptedException, IOException {
    if (remoteDownloadFuture.isDone()) {
      throw new RuntimeException("Download process failed, cancelling upload");
    }
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
        uploadFile(remoteDownloadFuture, queue, _file, parentPath + file.getName());
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

}
