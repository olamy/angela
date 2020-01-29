/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */

package org.terracotta.angela.common.tcconfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class TsaStripeConfig {

  private List<String> hostnames;
  private TsaOffheapConfig tsaOffheapConfig = null;
  private List<TsaDataDirectory> tsaDataDirectory = new ArrayList<>();
  private String persistenceDataName = null;

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
    this.tsaDataDirectory.add(new TsaDataDirectory(dataName, pathname, useForPlatform));
    return this;
  }

  public TsaStripeConfig persistence(String dataName) {
    this.persistenceDataName = dataName;
    return this;
  }

  public List<String> getHostnames() {
    return hostnames;
  }

  public TsaOffheapConfig getTsaOffheapConfig() {
    return tsaOffheapConfig;
  }

  public List<TsaDataDirectory> getTsaDataDirectoryList() {
    return tsaDataDirectory;
  }

  public String getPersistenceDataName() {
    return persistenceDataName;
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
