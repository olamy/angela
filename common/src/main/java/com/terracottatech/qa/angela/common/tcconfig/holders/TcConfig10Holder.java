package com.terracottatech.qa.angela.common.tcconfig.holders;

import com.terracottatech.qa.angela.common.net.GroupMember;
import com.terracottatech.qa.angela.common.net.PortChooser;
import com.terracottatech.qa.angela.common.tcconfig.SecurityRootDirectory;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terracotta config for Terracotta 5.0
 * <p>
 * 10 -> 5.0
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig10Holder extends TcConfigHolder {

  private static final PortChooser PORT_CHOOSER = new PortChooser();

  public TcConfig10Holder(final TcConfig10Holder tcConfig10Holder){
    super(tcConfig10Holder);
  }

  public TcConfig10Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
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

      if(securityWhiteListDeprectedNode != null){
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
      for (int i=0; i<serversList.getLength(); i++) {
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
      for (int i=0; i<serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node datarootNode = (Node) xPath.evaluate("//*[name()='data:directory']", server, XPathConstants.NODE);

        if (datarootNode != null) {
          String name = ((Element) datarootNode).getAttribute("name");
          if (name.equalsIgnoreCase(serverName)) {
            ((Element)server).setAttribute("host", hostname);
          }
        }
      }
    });
  }


  public List<GroupMember> retrieveGroupMembers(final String serverName, final boolean updateProxy){
    List<GroupMember> members = new ArrayList<>();
    modifyXml((tcConfigXml, xPath) -> {
      NodeList serversList = getServersList(tcConfigXml, xPath);

      for(int i = 0; i < serversList.getLength(); i++) {
        Node server = serversList.item(i);
        Node nameNode = (Node) xPath.evaluate("@name", server, XPathConstants.NODE);
        String name = nameNode.getTextContent();
        Node hostNode = (Node) xPath.evaluate("@host", server, XPathConstants.NODE);
        String host = hostNode.getTextContent();

        Node tsaGroupPortNode = (Node) xPath.evaluate("*[name()='tsa-group-port']", server, XPathConstants.NODE);
        int groupPort = Integer.parseInt(tsaGroupPortNode.getTextContent().trim());
        GroupMember member = null;
        if (name.equals(serverName) || !updateProxy){
          member = new GroupMember(name,host,groupPort);
        }else {
          int proxyPort = PORT_CHOOSER.chooseRandomPort();
          member = new GroupMember(name,host,groupPort, proxyPort);
          tsaGroupPortNode.setTextContent(String.valueOf(proxyPort));
        }
        if (name.equals(serverName)){
          members.add(0, member);
        } else {
          members.add(member);
        }
      }
    });
    return members;
  }

  @Override
  public Map<ServerSymbolicName, Integer> retrieveTsaPorts(final boolean updateForProxy) {
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
          tsaPort = PORT_CHOOSER.chooseRandomPort();
          tsaPortNode.setTextContent(String.valueOf(tsaPort));
        }
        tsaPorts.put(new ServerSymbolicName(name), tsaPort);
      }
    });
    return tsaPorts;
  }

  String getSecurityRootDirectory() {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

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

        String logsPath = kitDir.getAbsolutePath() + File.separatorChar + "audit-" + stripeId;

        Files.createDirectories(Paths.get(logsPath));

        auditLogNode.setTextContent(logsPath);
      }
    });
  }

}
