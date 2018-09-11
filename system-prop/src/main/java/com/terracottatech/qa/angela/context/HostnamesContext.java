package com.terracottatech.qa.angela.context;

import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.systemprop.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class HostnamesContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(HostnamesContext.class);

  // keeping track of available hostnames which can be injected.
  private final List<String> hostnames;

  // mapping between the original hostname and injected hostnames.
  private final Map<String, List<String>> hostnameMapping;

  public HostnamesContext() {
    List<String> hosts = SystemProperties.hostnames();
    this.hostnames = (hosts == null) ? null : new LinkedList<>(hosts);
    this.hostnameMapping = new HashMap<>();
  }

  /**
   * Returns the injected hostnames corresponding to the given hostname.
   * To be used in client/tms method call.
   *
   * @param hostname
   * @return
   */
  public String getInjectedHostName(String hostname) {
    if (hostnames == null) {
      return hostname;
    }

    // Select the first hostname from the injected list.
    return hostnameMapping.get(hostname).get(0);
  }

  /**
   * Inject hostnames corresponding to the hostnames mentioned in the topology, if not already injected.
   * @param topology
   */
  public void injectHostnames(Topology topology) {
    // do nothing in case of hostnames are not available.
    if (hostnames == null) {
      return;
    }

    // if topology is already injected in previous run then return without doing any injection, in case when reusing the topology object during multiple tsa invocation.
    if (hostnameMapping.values().stream().flatMap(List::stream).collect(Collectors.toSet()).containsAll(topology.getServersHostnames())) {
      return;
    }

    // ensuring we have sufficient number of spare hostnames to inject.
    TcConfig[] tcConfigs = topology.getTcConfigs();
    if (topology.getServersHostnames().size() > hostnames.size()) {
      throw new IllegalArgumentException("'" + SystemProperties.SYSTEM_PROP_HOSTNAMES + "' system property is not having sufficient hostnames.");
    }

    // inject hostnames in tcconfigs.
    Arrays.stream(tcConfigs).forEach(tcConfig -> {
      int numServers = tcConfig.getServers().size();
      for (int i = 0; i < numServers; i++) {
        String tcConfigHostname = tcConfig.getTerracottaServer(i).getHostname();
        String newHostName = hostnames.remove(0);

        tcConfig.updateServerHost(i, newHostName);
        hostnameMapping.computeIfAbsent(tcConfigHostname, hostname -> new ArrayList<>());
        hostnameMapping.get(tcConfigHostname).add(newHostName);
      }
    });

    LOGGER.debug("Servers with injected hostnames: {}", Arrays.stream(tcConfigs).flatMap(tcConfig -> tcConfig.getServers().values().stream()).collect(Collectors.toList()));
  }

}
