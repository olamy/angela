package com.terracottatech.qa.angela.client.remote.agent;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.util.AngelaVersions;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SshRemoteAgentLauncher implements RemoteAgentLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SshRemoteAgentLauncher.class);
  private static final int MAX_LINE_LENGTH = 1024;

  private final Map<String, RemoteAgentHolder> clients = new HashMap<>();
  private final String remoteUserName;
  private final File agentJarFile;
  private final boolean agentJarFileShouldBeRemoved;

  static class RemoteAgentHolder {
    RemoteAgentHolder(SSHClient sshClient, Session session, Session.Command command) {
      this.sshClient = sshClient;
      this.session = session;
      this.command = command;
    }

    SSHClient sshClient;
    Session session;
    Session.Command command;
  }


  public SshRemoteAgentLauncher() {
    this.remoteUserName = System.getProperty("tc.qa.angela.ssh.user.name", System.getProperty("user.name"));
    Map.Entry<File, Boolean> agentJar = findAgentJarFile();
    this.agentJarFile = agentJar.getKey();
    this.agentJarFileShouldBeRemoved = agentJar.getValue();
    if (this.agentJarFile == null) {
      throw new RuntimeException("agent JAR file not found, cannot use SSH remote agent launcher");
    }
  }

  @Override
  public void remoteStartAgentOn(String targetServerName) {
    if (clients.containsKey(targetServerName)) {
      return;
    }

    LOGGER.info("spawning {} agent via SSH", targetServerName);

    try {
      SSHClient ssh = new SSHClient();
      final String angelaHome = ".angela/" + targetServerName;

      if (!Boolean.parseBoolean(System.getProperty("tc.qa.angela.ssh.strictHostKeyChecking", "true"))) {
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
      }
      ssh.loadKnownHosts();
      ssh.connect(targetServerName);
      ssh.authPublickey(remoteUserName);

      exec(ssh, "mkdir -p $HOME/" + angelaHome + "/jars");
      if (agentJarFile.getName().endsWith("-SNAPSHOT.jar") || exec(ssh, "[ -e $HOME/" + angelaHome + "/jars/" + agentJarFile.getName() + " ]") != 0) {
        // jar file is a snapshot or does not exist, upload it
        LOGGER.info("uploading agent jar {} ...", agentJarFile.getName());
        uploadJar(ssh, agentJarFile, angelaHome + "/jars");
      }

      Session session = ssh.startSession();
      session.allocateDefaultPTY();
      LOGGER.info("starting agent");
      Session.Command cmd = session.exec("java -Dtc.qa.nodeName=" + targetServerName + " -DkitsDir=$HOME/" + angelaHome +
                                         " -jar $HOME/" + angelaHome + "/jars/" + agentJarFile.getName());

      SshLogOutputStream sshLogOutputStream = new SshLogOutputStream(targetServerName, cmd);
      new StreamCopier(cmd.getInputStream(), sshLogOutputStream, net.schmizz.sshj.common.LoggerFactory.DEFAULT).bufSize(MAX_LINE_LENGTH)
          .spawnDaemon("stdout");
      new StreamCopier(cmd.getErrorStream(), sshLogOutputStream, net.schmizz.sshj.common.LoggerFactory.DEFAULT).bufSize(MAX_LINE_LENGTH)
          .spawnDaemon("stderr");

      sshLogOutputStream.waitForStartedState();

      LOGGER.info("agent started on {}", targetServerName);
      clients.put(targetServerName, new RemoteAgentHolder(ssh, session, cmd));

    } catch (Exception e) {
      throw new RuntimeException("Failed to connect to " + remoteUserName + "@" + targetServerName + " (using SSH)", e);
    }
  }

  private static Map.Entry<File, Boolean> findAgentJarFile() {
    try {
      if (AngelaVersions.INSTANCE.isSnapshot()) {
        String snapshotLocation =
            System.getProperty("user.home") + "/.m2/repository/com/terracottatech/qa/angela-agent/" +
            AngelaVersions.INSTANCE.getAngelaVersion() +
            "/angela-agent-" +
            AngelaVersions.INSTANCE.getAngelaVersion() +
            ".jar";

        File snapshot = new File(snapshotLocation);
        if (snapshot.isFile()) {
          return new HashMap.SimpleEntry<>(snapshot, false);
        }

        // are we building angela? if yes, find the built agent jar in the module's target folder
        String mavenBaseDir = System.getProperty("basedir", ".");
        if (mavenBaseDir != null) {
          snapshotLocation = mavenBaseDir + "/../agent/target/" +
              "/angela-agent-" +
              AngelaVersions.INSTANCE.getAngelaVersion() +
              ".jar";
          snapshot = new File(snapshotLocation);
          if (snapshot.isFile()) {
            return new HashMap.SimpleEntry<>(snapshot, false);
          }
        }

        throw new RuntimeException("Agent SNAPSHOT jar file not found at " + snapshotLocation);
      } else {
        File agentFile = Files.createTempFile("angela-agent", ".jar").toFile();
        String releaseUrl = "http://nexus.terracotta.eur.ad.sag/service/local/repositories/terracotta-ee-releases/content/com/terracottatech/qa/angela-agent/" +
                            AngelaVersions.INSTANCE.getAngelaVersion() +
                            "/angela-agent-" +
                            AngelaVersions.INSTANCE.getAngelaVersion() +
                            ".jar";
        URL jarUrl = new URL(releaseUrl);
        try (InputStream jarIs = jarUrl.openStream(); FileOutputStream fileOutputStream = new FileOutputStream(agentFile)) {
          IOUtils.copy(jarIs, fileOutputStream);
        }
        return new HashMap.SimpleEntry<>(agentFile, true);
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not get angela-agent jar", e);
    }
  }

  private Integer exec(SSHClient ssh, String line) throws TransportException, ConnectionException {
    try (Session session = ssh.startSession()) {
      Session.Command cmd = session.exec(line);
      try {
        cmd.join(100, TimeUnit.MILLISECONDS);
      } finally {
        cmd.close();
      }
      return cmd.getExitStatus();
    }
  }

  private void uploadJar(SSHClient ssh, File agentJarFile, String targetFolder) throws IOException {
    ssh.newSCPFileTransfer().upload(agentJarFile.getPath(), targetFolder + "/" + agentJarFile.getName());
  }

  @Override
  public void close() throws Exception {
    if (agentJarFileShouldBeRemoved) {
      agentJarFile.delete();
    }
    for (Map.Entry<String, RemoteAgentHolder> entry : clients.entrySet()) {
      RemoteAgentHolder holder = entry.getValue();
      LOGGER.info("Cleaning up SSH agent on {}", entry.getKey());

      // 0x03 is the character for CTRL-C -> send it to the remote PTY
      holder.session.getOutputStream().write(0x03);
      safeClose(holder.command);
      safeClose(holder.session);
      safeClose(holder.sshClient);
    }
    clients.clear();
  }

  private static void safeClose(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      LOGGER.warn("Error while cleaning up SSH agent", e);
    }
  }
}
