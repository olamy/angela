package com.terracottatech.qa.angela.common.provider;

import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TcConfigProvider implements ConfigurationProvider {
  private final List<TcConfig> tcConfigs;

  private TcConfigProvider() {
    tcConfigs  = new ArrayList<>();
  }

  public static TcConfigProvider withTcConfig(List<TcConfig> tcConfigs, boolean netDisruptionEnabled) {
    TcConfigProvider tcConfigProvider = new TcConfigProvider();
    tcConfigProvider.tcConfigs.addAll(tcConfigs);
    checkConfigsHaveNoSymbolicNameDuplicate(tcConfigProvider.tcConfigs);
    if (netDisruptionEnabled) {
      for (TcConfig cfg : tcConfigProvider.tcConfigs) {
        cfg.createOrUpdateTcProperty("topology.validate", "false");
        cfg.createOrUpdateTcProperty("l1redirect.enabled", "false");
      }
    }
    return tcConfigProvider;
  }

  private static void checkConfigsHaveNoSymbolicNameDuplicate(List<TcConfig> tcConfigs) {
    Set<ServerSymbolicName> names = new HashSet<>();
    for (TcConfig tcConfig : tcConfigs) {
      tcConfig.getServers().forEach(server -> {
        ServerSymbolicName serverSymbolicName = server.getServerSymbolicName();
        if (names.contains(serverSymbolicName)) {
          throw new IllegalArgumentException("Duplicate name found in TC configs : " + server);
        } else {
          names.add(serverSymbolicName);
        }
      });
    }
  }

  public static List<TcConfig> mergeTcConfigs(final TcConfig tcConfig, final TcConfig[] tcConfigs) {
    final ArrayList<TcConfig> configs = new ArrayList<>();
    configs.add(tcConfig);
    configs.addAll(Arrays.asList(tcConfigs));
    return configs;
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
  public TerracottaServer findServer(ServerSymbolicName serverSymbolicName) {
    for (TcConfig tcConfig : tcConfigs) {
      List<TerracottaServer> servers = tcConfig.getServers();
      for (TerracottaServer server : servers) {
        if (server.getServerSymbolicName().equals(serverSymbolicName)) {
          return server;
        }
      }
    }
    return null;
  }

  @Override
  public TerracottaServer findServer(int stripeId, int serverIndex) {
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
  public int findStripeIdOf(ServerSymbolicName serverSymbolicName) {
    for (int i = 0; i < tcConfigs.size(); i++) {
      TcConfig tcConfig = tcConfigs.get(i);
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getServerSymbolicName().equals(serverSymbolicName)) {
          return i;
        }
      }
    }
    return -1;
  }

  public TcConfig findTcConfig(ServerSymbolicName serverSymbolicName) {
    for (int i = 0; i < tcConfigs.size(); ++i) {
      TcConfig tcConfig = tcConfigs.get(i);
      for (TerracottaServer server : tcConfig.getServers()) {
        if (server.getServerSymbolicName().equals(serverSymbolicName)) {
          return tcConfig;
        }
      }
    }
    throw new IllegalArgumentException("Invalid serverSymbolicName " + serverSymbolicName + " topology don't contain any such server");
  }

  @Override
  public Collection<String> getServersHostnames() {
    return getServers().stream().map(TerracottaServer::getHostname).collect(Collectors.toList());
  }

  public void setUpInstallation(TcConfig tcConfig,
                                ServerSymbolicName serverSymbolicName,
                                File installLocation,
                                SecurityRootDirectory securityRootDirectory) {
    int stripeId = findStripeIdOf(serverSymbolicName);
    tcConfig.substituteToken(Pattern.quote("${SERVER_NAME_TEMPLATE}"), serverSymbolicName.getSymbolicName());
    String modifiedTcConfigName = tcConfig.getTcConfigName()
        .substring(0, tcConfig.getTcConfigName()
            .length() - 4) + "-" + serverSymbolicName.getSymbolicName() + ".xml";
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
}
