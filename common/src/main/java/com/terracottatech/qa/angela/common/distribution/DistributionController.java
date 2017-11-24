package com.terracottatech.qa.angela.common.distribution;

import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.TerracottaServerState;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;

/**
 * @author Aurelien Broszniowski
 */

public abstract class DistributionController {

  protected final Distribution distribution;
  protected final Topology topology;

  public DistributionController(final Distribution distribution, final Topology topology) {
    this.distribution = distribution;
    this.topology = topology;
  }

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess start(final ServerSymbolicName serverSymbolicName, File installLocation);

  public abstract TerracottaServerState stop(final ServerSymbolicName serverSymbolicName, final File location, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess);

  public abstract void configureLicense(final InstanceId instanceId, final File location, final License license, final TcConfig[] tcConfigs);
}
