package com.terracottatech.qa.angela.client.remote.agent;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.util.AngelaVersions;
import com.terracottatech.qa.angela.common.util.JDK;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.InMemoryDestFile;
import net.schmizz.sshj.xfer.scp.SCPRemoteException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.terracottatech.qa.angela.agent.Agent.DFLT_ANGELA_PORT_RANGE;

public class SshRemoteAgentLauncher implements RemoteAgentLauncher {

  private static final Logger LOGGER = LoggerFactory.getLogger(SshRemoteAgentLauncher.class);
  private static final int MAX_LINE_LENGTH = 1024;

  private final Map<String, RemoteAgentHolder> clients = new HashMap<>();
  private final String remoteUserName;
  private final String remoteUserNameKeyPath;
  private final TerracottaCommandLineEnvironment tcEnv;
  private File agentJarFile;
  private boolean agentJarFileShouldBeRemoved;

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
    this(TerracottaCommandLineEnvironment.DEFAULT);
  }

  public SshRemoteAgentLauncher(TerracottaCommandLineEnvironment tcEnv) {
    this.tcEnv = tcEnv;
    this.remoteUserName = System.getProperty("tc.qa.angela.ssh.user.name", System.getProperty("user.name"));
    this.remoteUserNameKeyPath = System.getProperty("tc.qa.angela.ssh.user.name.key.path");
  }

  private void initAgentJar() {
    if (agentJarFile != null) {
      return;
    }
    Map.Entry<File, Boolean> agentJar = findAgentJarFile();
    this.agentJarFile = agentJar.getKey();
    this.agentJarFileShouldBeRemoved = agentJar.getValue();
    if (this.agentJarFile == null) {
      throw new RuntimeException("agent JAR file not found, cannot use SSH remote agent launcher");
    }
  }

  @Override
  public void remoteStartAgentOn(String targetServerName, Collection<String> nodesToJoin) {
    initAgentJar();
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

      // load provided private key file, if available.
      if (remoteUserNameKeyPath == null) {
        ssh.authPublickey(remoteUserName);
      } else {
        ssh.authPublickey(remoteUserName, remoteUserNameKeyPath);
      }

      Path baseDir = Paths.get(Agent.ROOT_DIR, angelaHome);
      Path jarsDir = baseDir.resolve("jars");
      exec(ssh, "mkdir -p " + baseDir.toString());
      exec(ssh, "chmod a+w " + baseDir.toString());
      exec(ssh, "mkdir -p " + jarsDir.toString());
      if (agentJarFile.getName().endsWith("-SNAPSHOT.jar") || exec(ssh, "[ -e " + jarsDir.resolve(agentJarFile.getName()).toString() + " ]") != 0) {
        // jar file is a snapshot or does not exist, upload it
        LOGGER.info("uploading agent jar {} ...", agentJarFile.getName());
        uploadJar(ssh, agentJarFile, jarsDir);
      }

      LOGGER.info("looking up remote JDK ...");
      String remoteJavaHome = findJavaHomeFromRemoteToolchains(ssh);

      Session session = ssh.startSession();
      session.allocateDefaultPTY();
      LOGGER.info("starting agent");
      String joinHosts = nodesToJoin.stream().map(node -> {
        try {
          String str = node + ":40000";
          String resolvedIPAddr = InetAddress.getByName(node).getHostAddress();
          if (!node.equals(resolvedIPAddr)) {
            str += "/" + resolvedIPAddr;
          }
          return str;
        } catch (UnknownHostException e) {
          throw new IllegalArgumentException(e);
        }
      }).collect(Collectors.joining(","));

      Session.Command cmd = session.exec(remoteJavaHome + "/bin/java " +
          "-Dtc.qa.nodeName=" + targetServerName + " " +
          "-Dtc.qa.directjoin=" + joinHosts + " " +
          "-DkitsDir=" + baseDir.toString() + " " +
          "-Dtc.qa.portrange=" + System.getProperty("tc.qa.portrange", "" + DFLT_ANGELA_PORT_RANGE) + " " +
          "-jar " + jarsDir.resolve(agentJarFile.getName()).toString());

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
          LOGGER.info("Found agent jar at " + snapshotLocation);
          return new HashMap.SimpleEntry<>(snapshot, false);
        }

        // are we building angela? if yes, find the built agent jar in the module's target folder
        String mavenBaseDir = System.getProperty("basedir");
        if (mavenBaseDir != null && new File(mavenBaseDir + "/../agent").isDirectory()) {
          snapshotLocation = mavenBaseDir + "/../agent/target" +
              "/angela-agent-" +
              AngelaVersions.INSTANCE.getAngelaVersion() +
              ".jar";
          snapshot = new File(snapshotLocation);
          if (snapshot.isFile()) {
            LOGGER.info("Found agent jar at " + snapshotLocation);
            return new HashMap.SimpleEntry<>(snapshot, false);
          }
        }

        throw new RuntimeException("Agent SNAPSHOT jar file not found at " + snapshotLocation);
      } else {
        File agentFile = Files.createTempFile("angela-agent", ".jar").toFile();
        String releaseUrl = "http://nexus.terracotta.eur.ad.sag:8080/service/local/repositories/terracotta-ee-releases/content/com/terracottatech/qa/angela-agent/" +
                            AngelaVersions.INSTANCE.getAngelaVersion() +
                            "/angela-agent-" +
                            AngelaVersions.INSTANCE.getAngelaVersion() +
                            ".jar";
        URL jarUrl = new URL(releaseUrl);
        try (InputStream jarIs = jarUrl.openStream(); FileOutputStream fileOutputStream = new FileOutputStream(agentFile)) {
          IOUtils.copy(jarIs, fileOutputStream);
        }
        LOGGER.info("Installed agent jar from Nexus at " + agentFile.getAbsolutePath());
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
        cmd.join(10, TimeUnit.SECONDS);
      } finally {
        cmd.close();
      }
      return cmd.getExitStatus();
    }
  }

  private void uploadJar(SSHClient ssh, File agentJarFile, Path targetFolder) throws IOException {
    String remotePath = targetFolder.resolve(agentJarFile.getName()).toString();
    ssh.newSCPFileTransfer().upload(agentJarFile.getPath(), remotePath);
  }

  private String findJavaHomeFromRemoteToolchains(SSHClient ssh) throws IOException {
    InMemoryDestFile localFile = new InMemoryDestFile() {
      private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      @Override
      public OutputStream getOutputStream() {
        return baos;
      }
    };
    try {
      ssh.newSCPFileTransfer().download("$HOME/.m2/toolchains.xml", localFile);
    } catch (SCPRemoteException sre) {
      throw new RuntimeException("Remote does not have $HOME/.m2/toolchains.xml file");
    }
    byte[] bytes = ((ByteArrayOutputStream) localFile.getOutputStream()).toByteArray();
    JavaLocationResolver javaLocationResolver = new JavaLocationResolver(new ByteArrayInputStream(bytes));
    List<JDK> jdks = javaLocationResolver.resolveJavaLocations(tcEnv, false);
    // check JDK validity remotely
    for (JDK jdk : jdks) {
      String remoteHome = jdk.getHome();
      if (exec(ssh, "[ -d \"" + remoteHome + "\" ]") == 0) {
        LOGGER.info("found remote JDK : home='{}' version='{}' vendor='{}'", jdk.getHome(), jdk.getVersion(), jdk.getVendor());
        return remoteHome;
      }
    }
    throw new RuntimeException("No JDK configured in remote toolchains.xml is valid; wanted : " + tcEnv + ", found : " + jdks);
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
