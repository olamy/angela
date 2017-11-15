package com.terracottatech.qa.angela.kit.distribution;

import org.zeroturnaround.exec.StartedProcess;

import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
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

  public abstract TerracottaServerInstance.TerracottaServerState start(TerracottaServer terracottaServer,
                                       File installLocation);

  public abstract TerracottaServerInstance.TerracottaServerState stop(TerracottaServer terracottaServer,
                                       File installLocation);
}
