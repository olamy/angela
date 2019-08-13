package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientArray;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tms;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.client.filesystem.RemoteFile;
import com.terracottatech.qa.angela.client.filesystem.RemoteFolder;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.ClientArrayTopology;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import com.terracottatech.qa.angela.test.Versions;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.terracottatech.qa.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.fail;

/**
 * @author Ludovic Orban
 */
public class BrowseTest {

  @Test
  public void testClient() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig().host("localhost")))
        );
    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testClient", configContext)) {
      ClientArray clientArray = factory.clientArray();
      Client client = clientArray.getClients().stream().findFirst().get();

      File fileToUpload = new File("target/toUpload", "uploaded-data.txt");
      fileToUpload.getParentFile().mkdir();
      try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(fileToUpload))) {
        dos.writeUTF("uploaded : hello, world!");
      }

      client.browse("uploaded").upload(new File("target/toUpload"));

      clientArray.executeOnAll(cluster -> {
        try (DataInputStream dis = new DataInputStream(new FileInputStream("uploaded/uploaded-data.txt"))) {
          String line = dis.readUTF();
          assertThat(line, is("uploaded : hello, world!"));
        }

        File file = new File("toDownload", "downloaded-data.txt");
        file.getParentFile().mkdir();
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
          dos.writeUTF("downloaded: hello, world!");
        }
      }).get();

      client.browse("toDownload").list().stream().filter(remoteFile -> remoteFile.getName().equals("downloaded-data.txt")).findAny().get().downloadTo(new File("target/downloaded-data.txt"));

      try (DataInputStream dis = new DataInputStream(new FileInputStream("target/downloaded-data.txt"))) {
        assertThat(dis.readUTF(), is("downloaded: hello, world!"));
      }
    }
  }

  @Test
  public void testTsa() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(tsa -> tsa.topology(new Topology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA),
            tcConfig(version(Versions.TERRACOTTA_VERSION), getClass().getResource("/terracotta/10/tc-config-ap.xml"))))
            .license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
        );

    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testTsa", configContext)) {
      Tsa tsa = factory.tsa()
          .startAll()
          .licenseAll();

      TerracottaServer active = tsa.getActive();
      tsa.stopAll();
      tsa.browse(active, "logs-0-1").downloadTo(new File("target/logs-active"));

      try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("target/logs-active/terracotta.server.log")))) {
        String line = br.readLine();
        assertThat(line.contains("TCServerMain - Terracotta"), is(true));
      }

      tsa.downloadDataDirectories(new File("target/data-directories"));
      assertThat(Arrays.asList(new File("target/data-directories").list()), containsInAnyOrder("Server1", "Server2"));
      assertThat(Arrays.asList(new File("target/data-directories/Server1").list()), containsInAnyOrder("data-folder", "platform-folder"));
      assertThat(Arrays.asList(new File("target/data-directories/Server2").list()), containsInAnyOrder("data-folder", "platform-folder"));
    }

    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testTsa_restart", configContext)) {
      Tsa tsa = factory.tsa();
      tsa.uploadDataDirectories(new File("target/data-directories"));
      tsa.startAll();
      try {
        tsa.licenseAll();
        fail("expected the licensing step to fail");
      } catch (Exception e) {
        // expected; restoring the data directories also restored the license
      }
    }
  }

  @Test
  public void testTms() throws Exception {
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tms(tms -> tms
            .distribution(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA))
            .license(license)
            .hostname("localhost")
        );
    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testTms", configContext)) {
      Tms tms = factory.tms()
          .start();
      tms.browse("tools/management/logs").downloadTo(new File("target/logs-tmc"));
      assertThat(Files.lines(Paths.get("target/logs-tmc/tmc.log"))
                      .anyMatch((l) -> l.contains("Starting TmsApplication")), is(true));
    }
  }

  @Test
  public void testNonExistentFolder() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig().host("localhost")))
        );

    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testNonExistentFolder", configContext)) {
      ClientArray clientArray = factory.clientArray();
      try {
        Client localhost = clientArray.getClients().stream().findFirst().get();
        localhost.browse("/does/not/exist").downloadTo(new File("target/destination"));
        fail("expected IOException");
      } catch (IOException e) {
        // expected
      }
    }
  }

  @Test
  public void testUpload() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .clientArray(clientArray -> clientArray.license(new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml")))
            .clientArrayTopology(new ClientArrayTopology(distribution(version(Versions.TERRACOTTA_VERSION), PackageType.KIT, LicenseType.TERRACOTTA), newClientArrayConfig().host("localhost")))
        );

    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testUpload", configContext)) {
      ClientArray clientArray = factory.clientArray();
      Client localhost = clientArray.getClients().stream().findFirst().get();
      RemoteFolder folder = localhost.browse("does-not-exist"); // check that we can upload to non-existent folder & the folder will be created

      folder.upload("license.xml", getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

      Optional<RemoteFile> createdFolder = localhost.browse(".").list().stream().filter(remoteFile -> remoteFile.getName().equals("does-not-exist") && remoteFile.isFolder()).findAny();
      assertThat(createdFolder.isPresent(), is(true));

      List<RemoteFile> remoteFiles = ((RemoteFolder) createdFolder.get()).list();
      Optional<RemoteFile> remoteFileOpt = remoteFiles.stream().filter(remoteFile -> remoteFile.getName().equals("license.xml")).findAny();
      assertThat(remoteFileOpt.isPresent(), is(true));
    }
  }
}
