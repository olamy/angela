package com.terracottatech.qa.angela.common.tcconfig.holders;

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
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Terracotta config for Terracotta 5.0
 * <p>
 * 10 -> 5.0
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig10Holder extends TcConfigHolder {

  public TcConfig10Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected NodeList getServersList(Document tcConfigXml, XPath xPath) throws XPathExpressionException {
    return (NodeList) xPath.evaluate("//*[name()='servers']//*[name()='server']", tcConfigXml.getDocumentElement(), XPathConstants.NODESET);
  }

  @Override
  public void updateDataDirectory(final String rootId, final String newlocation) {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

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

        this.tcConfigContent = domToString(tcConfigXml);
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

  @Override
  public void updateHostname(final String serverName, final String hostname) {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document tcConfigXml = builder.parse(new ByteArrayInputStream(this.tcConfigContent.getBytes(Charset.forName("UTF-8"))));

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

        this.tcConfigContent = domToString(tcConfigXml);
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot parse tc-config xml", e);
    }
  }

}
