package com.terracottatech.qa.angela.kit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.commons.FileUtils;

import com.terracottatech.qa.angela.kit.distribution.DistributionController;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.topology.Topology;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;


/**
 * Installation instance of a Terracotta server
 */
public class TerracottaInstall {

  private static final Logger logger = LoggerFactory.getLogger(TerracottaInstall.class);

  private final File location;
  private final Topology topology;
  private final DistributionController distributionController;
//  private final NetworkController networkController;


  public TerracottaInstall(final File location, final Topology topology) {
    this.location = location;
    this.topology = topology;
    this.distributionController = topology.createDistributionController();
//    this.networkController = networkController;
  }

  public File getLocation() {
    return location;
  }

  public DistributionController getDistributionController() {
    return distributionController;
  }
}
