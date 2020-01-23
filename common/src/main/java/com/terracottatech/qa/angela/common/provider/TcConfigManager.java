package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.net.DisruptionProvider;
import com.terracottatech.qa.angela.common.net.Disruptor;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TcConfigManager implements ConfigurationManager {
  private final List<TcConfig> tcConfigs;

  private TcConfigManager(List<TcConfig> tcConfigs) {
    this.tcConfigs = new ArrayList<>(tcConfigs);
  }

  public static TcConfigManager withTcConfig(List<TcConfig> tcConfigs, boolean netDisruptionEnabled) {
    TcConfigManager tcConfigProvider = new TcConfigManager(tcConfigs);
    if (netDisruptionEnabled) {
      for (TcConfig cfg : tcConfigProvider.tcConfigs) {
        cfg.createOrUpdateTcProperty("topology.validate", "false");
        cfg.createOrUpdateTcProperty("l1redirect.enabled", "false");
      }
    }
    return tcConfigProvider;
  }

  public static List<TcConfig> mergeTcConfigs(final TcConfig tcConfig, final TcConfig[] tcConfigs) {
    final ArrayList<TcConfig> configs = new ArrayList<>();
    configs.add(tcConfig);
    configs.addAll(Arrays.asList(tcConfigs));
    return configs;
  }

  @Override
  public void addStripe(TerracottaServer... newServers) {
    throw new UnsupportedOperationException("Addition of stripe not allowed at runtime");
  }

  @Override
  public void removeStripe(int stripeIndex) {
    throw new UnsupportedOperationException("Removal of stripe not allowed at runtime");
  }

  @Override
  public List<List<TerracottaServer>> getStripes() {
    List<List<TerracottaServer>> stripes = new ArrayList<>();
    for (TcConfig tcConfig : this.tcConfigs) {
      stripes.add(tcConfig.getServers());
    }
    return stripes;
  }

  @Override
  public void addServer(int stripeId, TerracottaServer newServer) {
    throw new UnsupportedOperationException("Addition of server not allowed at runtime");
  }

  @Override
  public void removeServer(int stripeIndex, int serverIndex) {
    throw new UnsupportedOperationException("Removal of server not allowed at runtime");
  }

  @Override
  public List<TerracottaServer> getServers() {
    List<TerracottaServer> servers = new ArrayList<>();
    for (TcConfig tcConfig : this.tcConfigs) {
      servers.addAll(tcConfig.getServers());
    }
    return servers;
  }

  @Override
  public TerracottaServer getServer(UUID serverId) {
    for (TcConfig tcConfig : tcConfigs) {
      List<TerracottaServer> servers = tcConfig.getServers();
      for (TerracottaServer server : servers) {
        if (server.getId().equals(serverId)) {
          return server;
        }
      }
    }
    return null;
  }

  @Override
  public TerracottaServer getServer(int stripeId, int serverIndex) {
    if (stripeId >= tcConfigs.size()) {
      throw new IllegalArgumentException("No such stripe #" + stripeId + " (there are: " + tcConfigs.size() + ")");
    }
    List<TerracottaServer> servers = tcConfigs.get(stripeId).getServers();
    if (serverIndex >= servers.size()) {
      throw new IllegalArgumentException("No such server #" + serverIndex + " (there are: " + servers.size() + " in stripe " + stripeId + ")");
    }
    return servers.get(serverIndex);
  }

  @Override
  public int getStripeIndexOf(UUID serverId) {
    for (int i = 0; i < tcConfigs.size(); i++) {
      TcConfig tcConfig = tcConfigs.get(i);
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getId().equals(serverId)) {
          return i;
        }
      }
    }
    return -1;
  }

  public TcConfig findTcConfig(UUID serverId) {
    for (TcConfig tcConfig : tcConfigs) {
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getId().equals(serverId)) {
          return tcConfig;
        }
      }
    }
    throw new IllegalArgumentException("Topology doesn't contain any server with id: " + serverId);
  }

  @Override
  public Collection<String> getServersHostnames() {
    return getServers().stream().map(TerracottaServer::getHostname).collect(Collectors.toList());
  }

  public void setUpInstallation(TcConfig tcConfig,
                                ServerSymbolicName serverSymbolicName,
                                UUID serverId,
                                Map<ServerSymbolicName, Integer> proxiedPorts,
                                File installLocation,
                                SecurityRootDirectory securityRootDirectory) {
    int stripeId = getStripeIndexOf(serverId);
    tcConfig.substituteToken(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
    String modifiedTcConfigName = tcConfig.getTcConfigName().substring(0, tcConfig.getTcConfigName().length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
    if (!proxiedPorts.isEmpty()) {
      tcConfig.updateServerGroupPort(proxiedPorts);
    }
    tcConfig.updateLogsLocation(installLocation, stripeId);
    setupSecurityDirectories(securityRootDirectory, stripeId, installLocation, serverSymbolicName, tcConfig);
    // all config mutations must happen before this line as the file gets written to disk here
    tcConfig.writeTcConfigFile(installLocation, modifiedTcConfigName);
  }

  private void setupSecurityDirectories(SecurityRootDirectory securityRootDirectory,
                                        int stripeId,
                                        File installLocation,
                                        ServerSymbolicName serverSymbolicName,
                                        TcConfig tcConfig) {
    if (securityRootDirectory != null) {
      installSecurityRootDirectory(securityRootDirectory, serverSymbolicName, installLocation, tcConfig);
      createAuditDirectory(installLocation, stripeId, tcConfig);
    }
  }

  private void installSecurityRootDirectory(SecurityRootDirectory securityRootDirectory,
                                            ServerSymbolicName serverSymbolicName,
                                            File installLocation,
                                            TcConfig tcConfig) {
    final String serverName = serverSymbolicName.getSymbolicName();
    Path securityRootDirectoryPath = installLocation.toPath().resolve("security-root-directory-" + serverName);
    securityRootDirectory.createSecurityRootDirectory(securityRootDirectoryPath);
    tcConfig.updateSecurityRootDirectoryLocation(securityRootDirectoryPath.toString());
  }

  private void createAuditDirectory(File installLocation, int stripeId, TcConfig tcConfig) {
    tcConfig.updateAuditDirectoryLocation(installLocation, stripeId);
  }

  public List<TcConfig> getTcConfigs() {
    return tcConfigs;
  }

  @Override
  public void createDisruptionLinks(TerracottaServer terracottaServer,
                                    DisruptionProvider disruptionProvider,
                                    Map<ServerSymbolicName, Disruptor> disruptionLinks,
                                    Map<ServerSymbolicName, Integer> proxiedPorts) {
    TcConfig tcConfig = findTcConfig(terracottaServer.getId());
    TcConfig modifiedConfig = TcConfig.copy(tcConfig);
    List<TerracottaServer> members = modifiedConfig.retrieveGroupMembers(terracottaServer.getServerSymbolicName().getSymbolicName(), disruptionProvider.isProxyBased());
    TerracottaServer thisMember = members.get(0);
    for (int i = 1; i < members.size(); ++i) {
      TerracottaServer otherMember = members.get(i);
      final InetSocketAddress src = new InetSocketAddress(thisMember.getHostname(), otherMember.getProxyPort() > 0 ? otherMember
          .getProxyPort() : thisMember.getTsaGroupPort());
      final InetSocketAddress dest = new InetSocketAddress(otherMember.getHostname(), otherMember.getTsaGroupPort());
      disruptionLinks.put(otherMember.getServerSymbolicName(), disruptionProvider.createLink(src, dest));
      proxiedPorts.put(otherMember.getServerSymbolicName(), src.getPort());
    }
  }
}
