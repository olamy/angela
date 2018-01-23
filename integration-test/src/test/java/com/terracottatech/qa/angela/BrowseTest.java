package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.Client;
import com.terracottatech.qa.angela.client.ClientJob;
import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.Tsa;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Ludovic Orban
 */
public class BrowseTest {

  @Test
  public void testClient() throws Exception {
    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testClient")) {
      Client client = factory.client("localhost");
      client.submit((ClientJob) context -> {
        File file = new File("newFolder", "data.txt");
        file.getParentFile().mkdir();
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
          dos.writeUTF("hello, world!");
        }
      }).get();

      client.browse("newFolder").list().stream().filter(remoteFile -> remoteFile.getName().equals("data.txt")).findAny().get().downloadTo(new File("target/data.txt"));

      try (DataInputStream dis = new DataInputStream(new FileInputStream("target/data.txt"))) {
        assertThat(dis.readUTF(), is("hello, world!"));
      }
    }
  }

  @Test
  public void testTsa() throws Exception {
    Topology topology = new Topology(distribution(version("10.2.0.0.224"), PackageType.KIT, LicenseType.TC_DB),
        tcConfig(version("10.2.0.0.224"), getClass().getResource("/terracotta/10/tc-config-a.xml")));
    License license = new License(getClass().getResource("/terracotta/10/TerracottaDB101_license.xml"));

    try (ClusterFactory factory = new ClusterFactory("BrowseTest::testTsa")) {
      Tsa tsa = factory.tsa(topology, license);
      tsa.installAll();
      tsa.startAll();

      TerracottaServer active = tsa.getActive();
      tsa.stopAll();
      tsa.browse(active, "logs-0-1").downloadTo(new File("target/logs-active"));

      try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("target/logs-active/terracotta.server.log")))) {
        String line = br.readLine();
        assertThat(line.contains("TCServerMain - Terracotta"), is(true));
      }
    }
  }

}
