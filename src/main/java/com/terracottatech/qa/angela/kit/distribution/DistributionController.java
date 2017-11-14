package com.terracottatech.qa.angela.kit.distribution;

import org.zeroturnaround.exec.StartedProcess;

import com.terracottatech.qa.angela.kit.ServerLogOutputStream;
import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

import java.io.File;

/**
 * @author Aurelien Broszniowski
 */

public abstract class DistributionController {

  protected final Distribution distribution;

  public DistributionController(final Distribution distribution) {
    this.distribution = distribution;
  }

  public abstract TerracottaServerInstance.TerracottaServerState start(TerracottaServer terracottaServer, Topology topology,
                                       File installLocation);

  public abstract void stop();

}
