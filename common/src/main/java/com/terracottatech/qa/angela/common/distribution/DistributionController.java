package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.ToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.provider.ConfigurationProvider;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;
import com.terracottatech.qa.angela.common.util.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */
public abstract class DistributionController {

  private final static Logger LOGGER = LoggerFactory.getLogger(DistributionController.class);

  protected final Distribution distribution;

  protected final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();


  DistributionController(Distribution distribution) {
    this.distribution = distribution;
  }

  protected Map<String, String> buildEnv(TerracottaCommandLineEnvironment tcEnv) {
    Map<String, String> env = new HashMap<>();
    String javaHome = javaLocationResolver.resolveJavaLocation(tcEnv).getHome();
    env.put("JAVA_HOME", javaHome);
    LOGGER.info(" JAVA_HOME = {}", javaHome);

    List<String> javaOpts = tcEnv.getJavaOpts();
    if (javaOpts != null) {
      String joinedJavaOpts = String.join(" ", javaOpts);
      env.put("JAVA_OPTS", joinedJavaOpts);
      LOGGER.info(" JAVA_OPTS = {}", joinedJavaOpts);
    }
    return env;
  }

  public ToolExecutionResult invokeJcmd(TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv, String... arguments) {
    Number javaPid = terracottaServerInstanceProcess.getJavaPid();
    if (javaPid == null) {
      return new ToolExecutionResult(-1, Collections.singletonList("PID of java process could not be figured out"));
    }

    String javaHome = javaLocationResolver.resolveJavaLocation(tcEnv).getHome();

    List<String> cmdLine = new ArrayList<>();
    if (OS.INSTANCE.isWindows()) {
      cmdLine.add(javaHome + "\\bin\\jcmd.exe");
    } else {
      cmdLine.add(javaHome + "/bin/jcmd");
    }
    cmdLine.add(javaPid.toString());
    cmdLine.addAll(Arrays.asList(arguments));

    try {
      ProcessResult processResult = new ProcessExecutor(cmdLine)
          .redirectErrorStream(true)
          .readOutput(true)
          .execute();
      return new ToolExecutionResult(processResult.getExitValue(), processResult.getOutput().getLines());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public abstract void disrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled);

  public abstract void undisrupt(ServerSymbolicName serverSymbolicName, Collection<TerracottaServer> targets, boolean netDisruptionEnabled);

  public abstract void removeDisruptionLinks(ServerSymbolicName serverSymbolicName, boolean netDisruptionEnabled);

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess createTsa(ServerSymbolicName serverSymbolicName, File installLocation, Topology topology, TerracottaCommandLineEnvironment tcEnv, List<String> startUpArgs);

  public abstract TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment env);

  public abstract void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv);

  public abstract void stopTsa(ServerSymbolicName serverSymbolicName, File location, TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv);

  public abstract void configureTsaLicense(String clusterName, File location, String licensePath, List<TcConfig> tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment env, boolean verbose);

  public abstract ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments);

  public abstract URI tsaUri(Collection<TerracottaServer> servers, Map<ServerSymbolicName, Integer> proxyTsaPorts);

  public abstract String clientJarsRootFolderName(Distribution distribution);

  public abstract String pluginJarsRootFolderName(Distribution distribution);
}
