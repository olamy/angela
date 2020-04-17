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

import org.terracotta.angela.common.net.PortProvider;
import org.terracotta.angela.common.tcconfig.ServerSymbolicName;
import org.terracotta.angela.common.tcconfig.TerracottaServer;
import org.terracotta.angela.common.tcconfig.TsaStripeConfig;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Terracotta config for Terracotta 4.1+
 * <p>
 * 9.0 -&gt; 4.1.x
 * 9.1 -&gt; 4.2.x
 * 9.2 -&gt; 4.3.x
 *
 * @author Aurelien Broszniowski
 */
public class TcConfig9Holder extends TcConfigHolder {

  public TcConfig9Holder(TcConfig9Holder tcConfig9Holder) {
    super(tcConfig9Holder);
  }

  public TcConfig9Holder(final InputStream tcConfigInputStream) {
    super(tcConfigInputStream);
  }

  @Override
  protected NodeList getServersList(Document tcConfigXml, XPath xPath) throws XPathExpressionException {
    return (NodeList) xPath.evaluate("//*[name()='servers']//*[name()='server']", tcConfigXml.getDocumentElement(), XPathConstants.NODESET);
  }

  @Override
  public void updateSecurityRootDirectoryLocation(String securityRootDirectory) {
    throw new UnsupportedOperationException("security-root-directory configuration is not available in TcConfig9");
  }

  @Override
  public void updateDataDirectory(final String rootId, final String newlocation) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void updateHostname(final String serverName, final String hostname) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public List<TerracottaServer> retrieveGroupMembers(String serverName, boolean updateProxy, PortProvider portProvider) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public Map<ServerSymbolicName, Integer> retrieveTsaPorts(final boolean updateForProxy, PortProvider portProvider) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void updateServerGroupPort(Map<ServerSymbolicName, Integer> proxiedPorts) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void updateServerTsaPort(Map<ServerSymbolicName, Integer> proxiedPorts) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void addOffheap(String resourceName, String size, String unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDataDirectory(List<TsaStripeConfig.TsaDataDirectory> tsaDataDirectoryList) {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public Map<String, String> getDataDirectories() {
    throw new UnsupportedOperationException("Unimplemented");
  }

  @Override
  public void addPersistencePlugin(String persistenceDataName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> getPluginServices() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateAuditDirectoryLocation(final File kitDir, final int stripeId) {
    throw new UnsupportedOperationException("Unimplemented");
  }

}
