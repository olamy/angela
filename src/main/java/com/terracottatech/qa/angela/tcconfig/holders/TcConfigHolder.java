package com.terracottatech.qa.angela.tcconfig.holders;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;
import com.terracottatech.qa.angela.kit.distribution.Distribution102Controller;
import com.terracottatech.qa.angela.tcconfig.TerracottaServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This holds the contents of the Tc Config
 * It is subcassed into versions
 *
 * @author Aurelien Broszniowski
 */
public abstract class TcConfigHolder {

  private final static Logger logger = LoggerFactory.getLogger(TcConfigHolder.class);

  String tcConfigContent;        // tc config content
  private String installedTcConfigPath;
  private Properties tcProperties = null;
  private List<String> logsPathList = new ArrayList<String>();

  public TcConfigHolder() {
  }

  public TcConfigHolder(final InputStream tcConfigInputStream) {
    try {
      SAXReader reader = new SAXReader();
      try {
        Document tcConfigXml = reader.read(tcConfigInputStream);

        this.tcConfigContent = tcConfigXml.asXML();
      } finally {
        tcConfigInputStream.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read tc-config xml input stream : " + tcConfigInputStream.toString(), e);
    }
  }

  public void writeTcConfigFile(File kitDir, final String tcConfigFilename) {
    File tempConfigFile;
    try {
      tempConfigFile = new File(kitDir.getAbsolutePath() + File.separatorChar + tcConfigFilename);
      tempConfigFile.deleteOnExit();
      Files.write(this.tcConfigContent, tempConfigFile, Charset.defaultCharset());
      this.installedTcConfigPath = tempConfigFile.getAbsolutePath();
      logger.info("Installed Tc Config path: {}", installedTcConfigPath);
    } catch (IOException e) {
      throw new RuntimeException("Cannot write tc-config xml to kitDir location: " + kitDir.getAbsolutePath(), e);
    }
  }

  protected abstract List<Node> getServersList(final Document tcConfigXml);

  public Map<String, TerracottaServer> getServers() {
    Map<String, TerracottaServer> servers = new LinkedHashMap<String, TerracottaServer>();

    // read servers list from XML
    SAXReader reader = new SAXReader();
    try {
      Document tcConfigXml = reader.read(new StringReader(tcConfigContent));
      List<Node> serversList = getServersList(tcConfigXml);
      for (Node server : serversList) {

        Node hostNode = server.selectSingleNode("@host");
        String hostname =
            hostNode == null || hostNode.getText().equals("%i") || hostNode.getText()
                .equals("%h") ? "localhost" : hostNode.getText();

        Node nameNode = server.selectSingleNode("@name");

        //TODO : create client and send command to get free port -> can't connect ? agent exception!
        // add into xml the port!!
        // log it!!!
        // remove below updatePorts method

        Node tsaPortNode = server.selectSingleNode("*[name()='tsa-port']");
        int tsaPort = tsaPortNode == null ? 9510 : Integer.parseInt(tsaPortNode.getText());

        Node jmxPortNode = server.selectSingleNode("*[name()='jmx-port']");
        int jmxPort = jmxPortNode == null ? 9520 : Integer.parseInt(jmxPortNode.getText());

        Node tsaGroupPortNode = server.selectSingleNode("*[name()='tsa-group-port']");
        int tsaGroupPort = tsaGroupPortNode == null ? 9530 : Integer.parseInt(tsaGroupPortNode.getText());

        Node managementPortNode = server.selectSingleNode("*[name()='management-port']");
        int managementPort = managementPortNode == null ? 9540 : Integer.parseInt(managementPortNode.getText());

        String serverSymbolicName = nameNode == null ? hostname + ":" + tsaPort : nameNode.getText();

        TerracottaServer terracottaServer = new TerracottaServer(serverSymbolicName, hostname, tsaPort, tsaGroupPort, managementPort, jmxPort);
        servers.put(serverSymbolicName, terracottaServer);
      }
    } catch (DocumentException e) {
      e.printStackTrace();
    }
    return servers;
  }

  public String getTcConfigContent() {
    return tcConfigContent;
  }

  public String getTcConfigPath() {
    logger.info("Tc Config installed config path : {}", installedTcConfigPath);
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
    StringReader stringInputStream = new StringReader(this.tcConfigContent);
    try {
      SAXReader reader = new SAXReader();
      try {
        Document tcConfigXml = reader.read(stringInputStream);
        List<Node> serversList = getServersList(tcConfigXml);
        int cnt = 1;
        for (Node server : serversList) {
          Node logsNode = server.selectSingleNode("*[name()='logs']");
          String logsPath = kitDir.getAbsolutePath() + File.separatorChar + "logs-" + tcConfigIndex + "-" + cnt;
          logsPathList.add(logsPath);
          if (logsNode != null) {
            logsNode.setText(logsPath);
          } else {
            ((Element)server).addElement("logs").addText(logsPath);
          }
          cnt++;
        }
        this.tcConfigContent = tcConfigXml.asXML();
      } finally {
        stringInputStream.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read tc-config xml input stream : " + stringInputStream.toString(), e);
    }
  }

  public List<String> getLogsLocation() {
    return this.logsPathList;
  }

  public abstract void updateDataDirectory(final String rootId, final String newlocation);

  public abstract void updateHostname(final String serverName, final String hostname);
}
