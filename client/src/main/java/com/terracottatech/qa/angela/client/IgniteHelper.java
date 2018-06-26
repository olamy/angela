package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.AngelaVersion;
import com.terracottatech.qa.angela.common.util.FileMetadata;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCompute;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class IgniteHelper {

  private final static Logger logger = LoggerFactory.getLogger(IgniteHelper.class);

  public static void checkAgentHealth(Ignite ignite, String nodeName) {
    ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
    IgniteFuture<Collection<Map<String, ?>>> future = ignite.compute(location).broadcastAsync((IgniteCallable<Map<String, ?>>) () -> Agent.CONTROLLER.getNodeAttributes());
    try {
      Collection<Map<String, ?>> attributeMaps = future.get(10, TimeUnit.SECONDS);
      if (attributeMaps.size() != 1) {
        throw new IllegalStateException("Detected " + attributeMaps.size() + " agents with node name [" + nodeName + "] while expected exactly one");
      }
      Map<String, ?> attributeMap = attributeMaps.iterator().next();
      if (!nodeName.equals(attributeMap.get("nodename"))) {
        throw new IllegalStateException("Agent " + nodeName + " mistakenly identifies itself as " + attributeMap);
      }
      if (!AngelaVersion.getAngelaVersion().equals(attributeMap.get("angela.version"))) {
        throw new IllegalStateException("Agent " + nodeName + " is running version [" + attributeMap.get("angela.version") + "]" +
            " but the expected version is [" + AngelaVersion.getAngelaVersion() + "]");
      }
    } catch (IgniteException e) {
      throw new IllegalStateException("Node with name '" + nodeName + "' not found in the cluster", e);
    }
  }

  public static IgniteFuture<Void> executeRemotely(final Ignite ignite, final TerracottaServer terracottaServer, final IgniteRunnable job) {
    return executeRemotely(ignite, terracottaServer.getHostname(), job);
  }

  public static <R> IgniteFuture<R> executeRemotely(final Ignite ignite, final TerracottaServer terracottaServer, final IgniteCallable<R> job) {
    return executeRemotely(ignite, terracottaServer.getHostname(), job);
  }


  public static IgniteFuture<Void> executeRemotely(final Ignite ignite, final String hostname, final IgniteRunnable job) {
    IgniteHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    return ignite.compute(location).runAsync(job);
  }

  public static <R> IgniteFuture<R> executeRemotely(final Ignite ignite, final String hostname, final IgniteCallable<R> job) {
    IgniteHelper.checkAgentHealth(ignite, hostname);
    ClusterGroup location = ignite.cluster().forAttribute("nodename", hostname);
    return ignite.compute(location).callAsync(job);
  }

  public static void uploadKit(final Ignite ignite, final String hostname, final InstanceId instanceId, final Distribution distribution,
                               final String kitInstallationName, final File kitInstallationPath) throws IOException, InterruptedException {
    IgniteFuture<Void> remoteDownloadFuture = executeRemotely(ignite, hostname,
        () -> Agent.CONTROLLER.downloadKit(instanceId, distribution, kitInstallationName));

    final BlockingQueue<Object> queue = ignite.queue(instanceId + "@file-transfer-queue@tsa", 100, new CollectionConfiguration());
    uploadFile(queue, kitInstallationPath, null);
    queue.put(Boolean.TRUE); // end of upload marker

    remoteDownloadFuture.get();
  }

  private static void uploadFile(BlockingQueue<Object> queue, File file, String path) throws InterruptedException, IOException {
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


}
