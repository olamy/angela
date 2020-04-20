package org.terracotta.angela.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerracottaVoter {
  private final String id;
  private final String hostName;
  private final List<String> hostPorts = new ArrayList<>();

  private TerracottaVoter(String id, String hostName, List<String> hostPorts) {
    this.id = id;
    this.hostName = hostName;
    this.hostPorts.addAll(hostPorts);
  }

  public static TerracottaVoter voter(String id, String hostName, String... hostPorts) {
    return new TerracottaVoter(id, hostName, Arrays.asList(hostPorts));
  }

  public String getId() {
    return id;
  }

  public String getHostName() {
    return hostName;
  }

  public List<String> getHostPorts() {
    return hostPorts;
  }

}
