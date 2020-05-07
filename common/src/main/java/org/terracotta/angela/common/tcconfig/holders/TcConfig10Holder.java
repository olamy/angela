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

package org.terracotta.angela.common.tcconfig.holders;

import org.terracotta.angela.common.net.PortAllocator;
import org.terracotta.angela.common.tcconfig.SecurityRootDirectory;
import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.tcconfig.TsaStripeConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.io.File.separatorChar;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.terracotta.angela.common.tcconfig.ServerSymbolicName.symbolicName;

/**
 * Terracotta config for Terracotta 10.x
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig10Holder extends TcConfigHolder {

  public TcConfig10Holder(final TcConfig10Holder tcConfig10Holder) {
    super(tcConfig10Holder);
  }

  public TcConfig10Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected int defaultManagementPort() {
    return 9440;
  }

  @Override
  protected int defaultTsaGroupPort() {
    return 9430;
  }

  @Override
  protected int defaultJmxPort() {
    return 9420;
  }

  @Override
  protected int defaultTsaPort() {
    return 9410;
  }

  @Override
  protected NodeList getServersList(Document tcConfigXml, XPath xPath) throws XPathExpressionException {
    return (NodeList) xPath.evaluate("//*[name()='servers']//*[name()='server']", tcConfigXml.getDocumentElement(), XPathConstants.NODESET);
  }

  @Override
  public void updateSecurityRootDirectoryLocation(final String securityRootDirectory) {
    modifyXml((tcConfigXml, xPath) -> {
      Node securityRootDirectoryNode = (Node) xPath.evaluate("//*[local-name()='security-root-directory']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);
      Node securityWhiteListDeprectedNode = (Node) xPath.evaluate("//*[local-name()='white-list']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);

      if (securityRootDirectoryNode != null) {
        securityRootDirectoryNode.setTextContent(securityRootDirectory);
      }

      if (securityWhiteListDeprectedNode != null) {
        securityWhiteListDeprectedNode.getAttributes()
            .getNamedItem("path").setNodeValue(securityRootDirectory + "/"
            + SecurityRootDirectory.WHITE_LIST_DEPRECATED_DIR_NAME + "/"
            + SecurityRootDirectory.WHITE_LIST_DEPRECATED_FILE_NAME);
      }
    });
  }

  @Override
  public void updateDataDirectory(final String rootId, final String newlocation) {
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);
      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node datarootNode = (Node) xPath.evaluate("//*[name()='data:directory']", server, XPathConstants.NODE);

        if (datarootNode != null) {
          String id = ((Element) datarootNode).getAttribute("id");
          if (rootId.equalsIgnoreCase(id)) {
            datarootNode.setTextContent(newlocation);
          }
        }
      }
    });
  }

  @Override
  public void updateHostname(final String serverName, final String hostname) {
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);
      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node datarootNode = (Node) xPath.evaluate("//*[name()='data:directory']", server, XPathConstants.NODE);

        if (datarootNode != null) {
          String name = ((Element) datarootNode).getAttribute("name");
          if (name.equalsIgnoreCase(serverName)) {
            ((Element) server).setAttribute("host", hostname);
          }
        }
      }
    });
  }

  public List<TerracottaServer> retrieveGroupMembers(final String serverName, final boolean updateProxy, PortAllocator portAllocator) {
    List<TerracottaServer> members = new ArrayList<>();
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);

      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);
        String name = nameNode.getTextContent();
        Node hostNode = (Node) xPath.evaluate("@host", server, XPathConstants.NODE);
        String host = hostNode.getTextContent();

        Node tsaGroupPortNode = (Node) xPath.evaluate("*[name()='tsa-group-port']", server, XPathConstants.NODE);
        int groupPort = Integer.parseInt(tsaGroupPortNode.getTextContent().trim());
        TerracottaServer member;
        if (name.equals(serverName) || !updateProxy) {
          member = TerracottaServer.server(name, host).tsaGroupPort(groupPort);
        } else {
          int proxyPort = portAllocator.reserve(1).next();
          member = TerracottaServer.server(name, host).tsaGroupPort(groupPort).proxyPort(proxyPort);
          tsaGroupPortNode.setTextContent(String.valueOf(proxyPort));
        }
        if (name.equals(serverName)) {
          members.add(0, member);
        } else {
          members.add(member);
        }
      }
    });
    return members;
  }

  @Override
  public Map<ServerSymbolicName, Integer> retrieveTsaPorts(final boolean updateForProxy, PortAllocator portAllocator) {
    Map<ServerSymbolicName, Integer> tsaPorts = new HashMap<>();
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);

      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);
        String name = nameNode.getTextContent();

        Node tsaPortNode = (Node) xPath.evaluate("*[name()='tsa-port']", server, XPathConstants.NODE);
        int tsaPort = Integer.parseInt(tsaPortNode.getTextContent().trim());
        if (updateForProxy) {
          tsaPort = portAllocator.reserve(1).next();
          tsaPortNode.setTextContent(String.valueOf(tsaPort));
        }
        tsaPorts.put(new ServerSymbolicName(name), tsaPort);
      }
    });
    return tsaPorts;
  }

  @Override
  public void updateServerGroupPort(Map<ServerSymbolicName, Integer> proxiedPorts) {
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);

      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);
        String name = nameNode.getTextContent();
        if (proxiedPorts.containsKey(new ServerSymbolicName(name))) {
          Node tsaGroupPortNode = (Node) xPath.evaluate("*[name()='tsa-group-port']", server, XPathConstants.NODE);
          tsaGroupPortNode.setTextContent(String.valueOf(proxiedPorts.get(symbolicName(name))));
        }
      }
    });
  }

  @Override
  public void updateServerTsaPort(Map<ServerSymbolicName, Integer> proxiedPorts) {
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);

      for (int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);
        String name = nameNode.getTextContent();
        if (proxiedPorts.containsKey(new ServerSymbolicName(name))) {
          Node tsaPortNode = (Node) xPath.evaluate("*[name()='tsa-port']", server, XPathConstants.NODE);
          tsaPortNode.setTextContent(String.valueOf(proxiedPorts.get(symbolicName(name))));
        }
      }
    });
  }

  @Override
  public void addOffheap(String resourceName, String size, String unit) {
    modifyXml((tcConfigXml, xPath) -> {
      Node serverElt = (Node) xPath.evaluate("//*[name()='plugins']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);

      Element node = tcConfigXml.createElement("config");
      Element node2 = tcConfigXml.createElement("ohr:offheap-resources");
      node2.setAttribute("xmlns:ohr", "http://www.terracotta.org/config/offheap-resource");
      node.appendChild(node2);

      Element node3 = tcConfigXml.createElement("ohr:resource");
      node3.setAttribute("name", resourceName);
      node3.setAttribute("unit", unit);
      node3.appendChild(tcConfigXml.createTextNode("" + size));
      node2.appendChild(node3);

      serverElt.appendChild(node);
    });
  }

  @Override
  public void addDataDirectory(List<TsaStripeConfig.TsaDataDirectory> tsaDataDirectoryList) {
    modifyXml((tcConfigXml, xPath) -> {
      Node serverElt = (Node) xPath.evaluate("//*[name()='plugins']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);

      Element node = tcConfigXml.createElement("config");

      Element node2 = tcConfigXml.createElement("data:data-directories");
      node2.setAttribute("xmlns:data", "http://www.terracottatech.com/config/data-roots");
      node.appendChild(node2);

      for (TsaStripeConfig.TsaDataDirectory tsaDataDirectory : tsaDataDirectoryList) {
        Element node3 = tcConfigXml.createElement("data:directory");
        node3.setAttribute("name", tsaDataDirectory.getDataName());
        node3.setAttribute("use-for-platform", "" + tsaDataDirectory.isUseForPlatform());
        node3.appendChild(tcConfigXml.createTextNode(tsaDataDirectory.getLocation()));
        node2.appendChild(node3);

      }

      serverElt.appendChild(node);
    });
  }

  public Map<String, String> getDataDirectories() {
    try {
      Map<String, String> dataDirectories = new HashMap<>();

      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setNamespaceAware(true);
      DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(UTF_8)));

      NodeList nodes = (NodeList) xPath.evaluate("//*[local-name()='directory' and namespace-uri()='http://www.terracottatech.com/config/data-roots']",
          tcConfigXml.getDocumentElement(), XPathConstants.NODESET);

      for (int i = 0; i < nodes.getLength(); i++) {
        Node item = nodes.item(i);
        String textContent = item.getTextContent().trim();
        String name = item.getAttributes().getNamedItem("name").getNodeValue();
        dataDirectories.put(name, textContent);
      }

      return dataDirectories;
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  /*
     <service xmlns:persistence="http://www.terracottatech.com/config/platform-persistence">
      <persistence:platform-persistence data-directory-id="data"/>
    </service>
   */
  @Override
  public void addPersistencePlugin(String persistenceDataName) {
    modifyXml((tcConfigXml, xPath) -> {
      Node serverElt = (Node) xPath.evaluate("//*[name()='plugins']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);

      Element node1 = tcConfigXml.createElement("service");
      node1.setAttribute("xmlns:persistence", "http://www.terracottatech.com/config/platform-persistence");

      Element node2 = tcConfigXml.createElement("persistence:platform-persistence");
      node2.setAttribute("data-directory-id", persistenceDataName);
      node1.appendChild(node2);

      serverElt.appendChild(node1);
    });
  }

  @Override
  public List<String> getPluginServices() {
    try {
      List<String> pluginServices = new ArrayList<>();

      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setNamespaceAware(true);
      DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(UTF_8)));

      NodeList nodes = (NodeList) xPath.evaluate("//*[local-name()='platform-persistence' and namespace-uri()='http://www.terracottatech.com/config/platform-persistence']",
          tcConfigXml.getDocumentElement(), XPathConstants.NODESET);

      for (int i = 0; i < nodes.getLength(); i++) {
        Node item = nodes.item(i);
        String datadirId = item.getAttributes().getNamedItem("data-directory-id").getNodeValue();
        pluginServices.add(datadirId);
      }

      return pluginServices;
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  String getSecurityRootDirectory() {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(UTF_8)));

      Node securityRootDirectoryNode = (Node) xPath.evaluate("//*[local-name()='security-root-directory']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);
      return securityRootDirectoryNode != null ? securityRootDirectoryNode.getTextContent() : null;
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  @Override
  public synchronized void updateAuditDirectoryLocation(final File kitDir, final int stripeId) {
    modifyXml((tcConfigXml, xPath) -> {
      Node auditLogNode = (Node) xPath.evaluate("//*[local-name()='audit-directory']", tcConfigXml.getDocumentElement(), XPathConstants.NODE);
      if (auditLogNode != null) {
        String logsPath = kitDir.getAbsolutePath() + separatorChar + "audit-" + stripeId;
        Files.createDirectories(Paths.get(logsPath));
        auditLogNode.setTextContent(logsPath);
      }
    });
  }

}
