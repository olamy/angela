package com.terracottatech.qa.angela.common.tcconfig.holders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * This holds the contents of the Tc Config
 * It is subcassed into versions
 *
 * @author Aurelien Broszniowski
 */
public abstract class TcConfigHolder {

  private final static Logger logger = LoggerFactory.getLogger(TcConfigHolder.class);

  protected volatile String tcConfigContent;        // tc config content
  private volatile String installedTcConfigPath;
  private volatile Properties tcProperties = null;
  private final List<String> logsPathList = new ArrayList<String>();

  public TcConfigHolder() {
  }

  public TcConfigHolder(TcConfigHolder tcConfigHolder){
    this.tcConfigContent = tcConfigHolder.tcConfigContent;
    this.installedTcConfigPath = tcConfigHolder.installedTcConfigPath;
    if (tcConfigHolder.tcProperties != null){
      this.tcProperties = new Properties(tcConfigHolder.tcProperties);
    }
    this.logsPathList.addAll(tcConfigHolder.logsPathList);
  }

  public TcConfigHolder(final InputStream tcConfigInputStream) {
    try {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(tcConfigInputStream);

      this.tcConfigContent = domToString(tcConfigXml);
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  public void writeTcConfigFile(File kitDir, final String tcConfigFilename) {
    File tempConfigFile;
    try {
      tempConfigFile = new File(kitDir.getAbsolutePath() + File.separatorChar + tcConfigFilename);
      try (FileOutputStream fos = new FileOutputStream(tempConfigFile)) {
        fos.write(this.tcConfigContent.getBytes(Charset.defaultCharset()));
      }
      this.installedTcConfigPath = tempConfigFile.getAbsolutePath();
      logger.info("Installed Tc Config path: {}", installedTcConfigPath);
    } catch (IOException e) {
      throw new RuntimeException("Cannot write tc-config xml to kitDir location: " + kitDir.getAbsolutePath(), e);
    }
  }

  protected abstract NodeList getServersList(Document tcConfigXml, XPath xPath) throws XPathExpressionException;

  public Map<ServerSymbolicName, TerracottaServer> getServers() {
    Map<ServerSymbolicName, TerracottaServer> servers = new LinkedHashMap<>();

    // read servers list from XML
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

      NodeList serversList = getServersList(tcConfigXml, xPath);
      for (int i=0; i<serversList.getLength(); i++) {
        Node server = serversList.item(i);

        Node hostNode = (Node) xPath.evaluate("@host", server, XPathConstants.NODE);
        String hostname =
            hostNode == null || hostNode.getTextContent().equals("%i") || hostNode.getTextContent()
                .equals("%h") ? "localhost" : hostNode.getTextContent();

        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);

        //TODO : create client and send command to get free port -> can't connect ? agent exception!
        // add into xml the port!!
        // log it!!!
        // remove below updatePorts method

        Node tsaPortNode = (Node) xPath.evaluate("*[name()='tsa-port']", server, XPathConstants.NODE);
        int tsaPort = tsaPortNode == null ? 9510 : Integer.parseInt(tsaPortNode.getTextContent());

        Node jmxPortNode = (Node) xPath.evaluate("*[name()='jmx-port']", server, XPathConstants.NODE);
        int jmxPort = jmxPortNode == null ? 9520 : Integer.parseInt(jmxPortNode.getTextContent());

        Node tsaGroupPortNode = (Node) xPath.evaluate("*[name()='tsa-group-port']", server, XPathConstants.NODE);
        int tsaGroupPort = tsaGroupPortNode == null ? 9530 : Integer.parseInt(tsaGroupPortNode.getTextContent());

        Node managementPortNode = (Node) xPath.evaluate("*[name()='management-port']", server, XPathConstants.NODE);
        int managementPort = managementPortNode == null ? 9540 : Integer.parseInt(managementPortNode.getTextContent());

        String symbolicName = nameNode == null ? hostname + ":" + tsaPort : nameNode.getTextContent();

        TerracottaServer terracottaServer = new TerracottaServer(symbolicName, hostname, tsaPort, tsaGroupPort, managementPort, jmxPort);
        servers.put(terracottaServer.getServerSymbolicName(), terracottaServer);
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
    return servers;
  }

  public String getTcConfigContent() {
    return tcConfigContent;
  }

  public String getTcConfigPath() {
    return this.installedTcConfigPath;
  }

  public void setTcProperties(final Properties tcProperties) {
    this.tcProperties = tcProperties;
  }

  public Object getTcProperty(final String key) {
    if (tcProperties == null) {
      return null;
    } else {
      return tcProperties.get(key);
    }
  }

  public synchronized void updateLogsLocation(final File kitDir, final int tcConfigIndex) {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

      NodeList serversList = getServersList(tcConfigXml, xPath);
      int cnt = 1;
      for (int i=0; i<serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node logsNode = (Node) xPath.evaluate("*[name()='logs']", server, XPathConstants.NODE);

        String logsPath = kitDir.getAbsolutePath() + File.separatorChar + "logs-" + tcConfigIndex + "-" + cnt;
        logsPathList.add(logsPath);
        if (logsNode != null) {
          logsNode.setTextContent(logsPath);
        } else {
          Element newElement = tcConfigXml.createElement("logs");
          newElement.setTextContent(logsPath);
          server.getParentNode().insertBefore(newElement, server.getNextSibling());
        }
        cnt++;

        this.tcConfigContent = domToString(tcConfigXml);
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  public void updateServerHost(int serverIndex, String newServerHost) {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

      NodeList serversList = getServersList(tcConfigXml, xPath);
      if (serverIndex > serversList.getLength()) {
        throw new ArrayIndexOutOfBoundsException("Server index " + serverIndex + " out of bounds: " + serversList.getLength());
      }
      Node server = serversList.item(serverIndex);

      Attr attribute = tcConfigXml.createAttribute("host");
      attribute.setValue(newServerHost);
      server.getAttributes().setNamedItem(attribute);

      this.tcConfigContent = domToString(tcConfigXml);
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  public List<String> getLogsLocation() {
    return this.logsPathList;
  }

  public abstract void updateSecurityRootDirectoryLocation(final String securityRootDirectory);

  public abstract void updateDataDirectory(final String rootId, final String newlocation);

  public abstract void updateHostname(final String serverName, final String hostname);

  public abstract List<GroupMember> retrieveGroupMembers(final String serverName, final boolean updateProxy);

  public abstract Map<ServerSymbolicName, Integer> retrieveTsaPorts(final boolean updateForProxy);

  public void substituteToken(String token, String value){
      this.tcConfigContent = this.tcConfigContent.replaceAll(token, value);
  }

  protected static String domToString(Document document) throws TransformerException, IOException {
    DOMSource domSource = new DOMSource(document);
    try (StringWriter writer = new StringWriter()) {
      StreamResult result = new StreamResult(writer);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.transform(domSource, result);
      return writer.toString();
    }
  }

}
