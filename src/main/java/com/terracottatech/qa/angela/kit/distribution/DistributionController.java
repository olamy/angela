package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.kit.TerracottaServerState;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

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

  public abstract TerracottaServerInstance.TerracottaServerInstanceProcess start(final String serverSymbolicName, File installLocation);

  public abstract TerracottaServerState stop(final String serverSymbolicName, final File location, final TerracottaServerInstance.TerracottaServerInstanceProcess terracottaServerInstanceProcess);
}
