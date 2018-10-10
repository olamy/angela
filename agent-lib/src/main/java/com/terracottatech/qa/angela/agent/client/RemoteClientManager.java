package com.terracottatech.qa.angela.agent.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import com.terracottatech.qa.angela.common.util.LogOutputStream;
import com.terracottatech.qa.angela.common.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.terracottatech.qa.angela.agent.Agent.DFLT_ANGELA_PORT_RANGE;

/**
 * @author Aurelien Broszniowski
 */

public class RemoteClientManager {

  private final static Logger logger = LoggerFactory.getLogger(RemoteClientManager.class);
  private final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();

  private static final String CLASSPATH_SUBDIR_NAME = "lib";
  private final File kitInstallationPath;

  public RemoteClientManager(InstanceId instanceId) {
    this.kitInstallationPath = new File(Agent.WORK_DIR, instanceId.toString());
  }

  public File getClientInstallationPath() {
    return kitInstallationPath;
  }

  public File getClientClasspathRoot() {
    return new File(kitInstallationPath, CLASSPATH_SUBDIR_NAME);
  }

  public int spawnClient(InstanceId instanceId, TerracottaCommandLineEnvironment tcEnv, Collection<String> joinedNodes) {
    try {
      String javaHome = javaLocationResolver.resolveJavaLocation(tcEnv).getHome();

      final AtomicBoolean started = new AtomicBoolean(false);
      List<String> cmdLine = new ArrayList<>();
      if (OS.INSTANCE.isWindows()) {
        cmdLine.add(javaHome + "\\bin\\java.exe");
      } else {
        cmdLine.add(javaHome + "/bin/java");
      }
      if (tcEnv.getJavaOpts() != null) {
        cmdLine.addAll(tcEnv.getJavaOpts());
      }
      cmdLine.add("-classpath");
      cmdLine.add(buildClasspath());

      cmdLine.add("-Dtc.qa.portrange=" + System.getProperty("tc.qa.portrange", "" + DFLT_ANGELA_PORT_RANGE));
      cmdLine.add("-Dtc.qa.directjoin=" + String.join(",", joinedNodes));
      cmdLine.add("-Dtc.qa.nodeName=" + instanceId);
      cmdLine.add("-D" + Agent.ROOT_DIR_SYSPROP_NAME + "=" + Agent.ROOT_DIR);
      cmdLine.add(Agent.class.getName());

      logger.info("Spawning client {}", cmdLine);
      ProcessExecutor processExecutor = new ProcessExecutor().command(cmdLine)
          .redirectOutput(new LogOutputStream() {
            @Override
            protected void processLine(String line) {
              System.out.println(" |" + instanceId + "| " + line);
              if (line.equals(Agent.AGENT_IS_READY_MARKER_LOG)) {
                started.set(true);
              }
            }
          }).directory(getClientInstallationPath());
      StartedProcess startedProcess = processExecutor.start();

      while (startedProcess.getProcess().isAlive() && !started.get()) {
        logger.debug("Waiting for spawned agent to be ready having PID: {}", PidUtil.getPid(startedProcess.getProcess()));
        Thread.sleep(100);
      }
      if (!startedProcess.getProcess().isAlive()) {
        throw new RuntimeException("Client process died in infancy");
      }

      int pid = PidUtil.getPid(startedProcess.getProcess());
      logger.info("Spawned client with PID {}", pid);
      return pid;
    } catch (Exception e) {
      throw new RuntimeException("Error spawning client " + instanceId, e);
    }
  }

  private String buildClasspath() {
    String[] cpEntries = getClientClasspathRoot().list();
    if (cpEntries == null) {
      throw new RuntimeException("Cannot build client classpath before the classpath root is uploaded");
    }

    StringBuilder sb = new StringBuilder();
    for (String cpentry : cpEntries) {
      sb.append(CLASSPATH_SUBDIR_NAME).append(File.separator).append(cpentry).append(File.pathSeparator);
    }

    // if
    //   file:/Users/lorban/.m2/repository/org/slf4j/slf4j-api/1.7.22/slf4j-api-1.7.22.jar!/org/slf4j/Logger.class
    // else
    //   /work/terracotta/irepo/lorban/angela/agent/target/classes/com/terracottatech/qa/angela/agent/Agent.class

    String agentClassName = Agent.class.getName().replace('.', '/');
    String agentClassPath = Agent.class.getResource("/" + agentClassName + ".class").getPath();

    if (agentClassPath.startsWith("file:")) {
      sb.append(agentClassPath, "file:".length(), agentClassPath.lastIndexOf('!'));
    } else {
      sb.append(agentClassPath, 0, agentClassPath.lastIndexOf(agentClassName));
    }

    return sb.toString();
  }

}
