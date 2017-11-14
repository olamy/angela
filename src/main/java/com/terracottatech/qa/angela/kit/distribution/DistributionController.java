package com.terracottatech.qa.angela.kit.distribution;

import com.terracottatech.qa.angela.kit.TerracottaServerInstance;
import com.terracottatech.qa.angela.topology.LicenseType;
import com.terracottatech.qa.angela.topology.PackageType;
import com.terracottatech.qa.angela.topology.Version;

/**
 * @author Aurelien Broszniowski
 */

public abstract class DistributionController {

  private final Distribution distribution;

  public DistributionController(final Distribution distribution) {
    this.distribution = distribution;
  }

  public abstract TerracottaServerInstance.TerracottaServerState start();

  public abstract void stop();

}
