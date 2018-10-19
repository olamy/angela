package com.terracottatech.qa.angela.common.tcconfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class TsaStripeConfig {

  private List<String> hostnames;
  private TsaOffheapConfig tsaOffheapConfig = null;
  private TsaDataDirectory tsaDataDirectory = null;

  public TsaStripeConfig(final List<String> hostnames) {
    this.hostnames = hostnames;
  }

  public static TsaStripeConfig stripe(String hostname, int serverCount) {
    if (serverCount < 1) {
      throw new IllegalArgumentException("Server count can be lower than 1 in a Tsa Stripe");
    }
    List<String> hostnamesList = new ArrayList<>();
    for (int i = 0; i < serverCount; i++) {
      hostnamesList.add(hostname);
    }
    return new TsaStripeConfig(hostnamesList);
  }

  public static TsaStripeConfig stripe(String hostname, String... hostnames) {
    List<String> hostnamesList = new ArrayList<>();
    hostnamesList.add(hostname);
    Collections.addAll(hostnamesList, hostnames);
    return new TsaStripeConfig(hostnamesList);
  }

  public TsaStripeConfig offheap(String resourceName, String size, String unit) {
    this.tsaOffheapConfig = new TsaOffheapConfig(resourceName, size, unit);
    return this;
  }

  public TsaStripeConfig data(String dataName, String pathname) {
    return data(dataName, pathname, false);
  }

  public TsaStripeConfig data(String dataName, String pathname, boolean useForPlatform) {
    this.tsaDataDirectory = new TsaDataDirectory(dataName, pathname, useForPlatform);
    return this;
  }

  public List<String> getHostnames() {
    return hostnames;
  }

  public TsaOffheapConfig getTsaOffheapConfig() {
    return tsaOffheapConfig;
  }

  public TsaDataDirectory getTsaDataDirectory() {
    return tsaDataDirectory;
  }

  public class TsaOffheapConfig {

    private final String resourceName;
    private final String size;
    private final String unit;

    public TsaOffheapConfig(final String resourceName, final String size, String unit) {
      this.resourceName = resourceName;
      this.size = size;
      this.unit = unit;
    }

    public String getResourceName() {
      return resourceName;
    }

    public String getSize() {
      return size;
    }

    public String getUnit() {
      return unit;
    }
  }

  public class TsaDataDirectory {

    private final String dataName;
    private final String location;
    private final boolean useForPlatform;

    public TsaDataDirectory(String dataName, String location, boolean useForPlatform) {
      this.dataName = dataName;
      this.location = location;
      this.useForPlatform = useForPlatform;
    }

    public String getDataName() {
      return dataName;
    }

    public String getLocation() {
      return location;
    }

    public boolean isUseForPlatform() {
      return useForPlatform;
    }
  }
}
