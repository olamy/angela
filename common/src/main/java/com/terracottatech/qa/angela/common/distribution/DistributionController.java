package com.terracottatech.qa.angela.common.distribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.TerracottaManagementServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.common.util.JDK;
import com.terracottatech.qa.angela.common.util.JavaLocationResolver;


import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public abstract class DistributionController {

  private final static Logger logger = LoggerFactory.getLogger(DistributionController.class);

  protected final Distribution distribution;
  protected final Topology topology;

  protected final JavaLocationResolver javaLocationResolver = new JavaLocationResolver();


  public DistributionController(final Distribution distribution, final Topology topology) {
    this.distribution = distribution;
    this.topology = topology;
  }

  protected Map<String, String> buildEnv() {
    Map<String, String> env = new HashMap<>();
    List<JDK> j8Homes = javaLocationResolver.resolveJavaLocation("1.8", JavaLocationResolver.Vendor.ORACLE);
    if (j8Homes.size() > 1) {
      logger.warn("Multiple JDK 8 homes found: {} - using the 1st one", j8Homes);
    }
    env.put("JAVA_HOME", j8Homes.get(0).getHome());
    logger.info(" JAVA_HOME = {}", j8Homes.get(0).getHome());
    return env;
  }

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess start(final ServerSymbolicName serverSymbolicName, File installLocation);

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess create(final ServerSymbolicName serverSymbolicName, File installLocation);

  public abstract TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess startTms(File installLocation);

  public abstract void stopTms(File installLocation, TerracottaManagementServerInstance.TerracottaManagementServerInstanceProcess terracottaServerInstanceProcess);

  public abstract void stop(final ServerSymbolicName serverSymbolicName, final File location, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess);

  public abstract void configureLicense(final InstanceId instanceId, final File location, final License license, final TcConfig[] tcConfigs, final SecurityRootDirectory securityRootDirectory);
}
