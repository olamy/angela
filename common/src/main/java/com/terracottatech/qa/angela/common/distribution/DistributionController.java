package com.terracottatech.qa.angela.common.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.ClusterToolExecutionResult;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.metrics.HardwareMetricsCollector;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */
public abstract class DistributionController {

  private final static Logger LOGGER = LoggerFactory.getLogger(DistributionController.class);

  protected final Distribution distribution;
  protected final Topology topology;

  protected final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();


  public DistributionController(Distribution distribution, Topology topology) {
    this.distribution = distribution;
    this.topology = topology;
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

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess create(ServerSymbolicName serverSymbolicName, File installLocation, TcConfig tcConfig, TerracottaCommandLineEnvironment tcEnv, HardwareMetricsCollector.TYPE type);

  public abstract TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation, TerracottaCommandLineEnvironment env);

  public abstract void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv);

  public abstract void stop(ServerSymbolicName serverSymbolicName, File location, TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess, TerracottaCommandLineEnvironment tcEnv);

  public abstract void configureLicense(String clusterName, File location, String licensePath, TcConfig[] tcConfigs, SecurityRootDirectory securityRootDirectory, TerracottaCommandLineEnvironment env, boolean verbose);

  public abstract ClusterToolExecutionResult invokeClusterTool(File installLocation, TerracottaCommandLineEnvironment env, String... arguments);
}
