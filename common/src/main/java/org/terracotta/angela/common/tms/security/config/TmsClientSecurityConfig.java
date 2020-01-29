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

package org.terracotta.angela.common.tms.security.config;

import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;

public class TmsClientSecurityConfig {

  private final URI trustStoreUri;
  private final String trustStorePassword;
  private final String username;
  private final String password;

  public TmsClientSecurityConfig(String trustStorePassword, URI trustStoreUri, String username, String password) {
    this.trustStorePassword = trustStorePassword;
    this.trustStoreUri = trustStoreUri;
    this.username = username;
    this.password = password;
  }

  public TrustManagerFactory getTrustManagerFactory() throws IOException, GeneralSecurityException {
    InputStream truststoreStream = new FileInputStream(trustStoreUri.getPath());
    KeyStore truststore = KeyStore.getInstance("JKS");
    truststore.load(truststoreStream, trustStorePassword.toCharArray());
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(truststore);
    return trustManagerFactory;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TmsClientSecurityConfig that = (TmsClientSecurityConfig) o;
    return Objects.equals(trustStoreUri, that.trustStoreUri) &&
        Objects.equals(trustStorePassword, that.trustStorePassword) &&
        Objects.equals(username, that.username) &&
        Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(trustStoreUri, trustStorePassword, username, password);
  }

  @Override
  public String toString() {
    return "TmsClientSecurityConfig{" +
        "trustStoreUri=" + trustStoreUri +
        ", trustStorePassword='" + trustStorePassword + '\'' +
        ", username='" + username + '\'' +
        ", password='" + password + '\'' +
        '}';
  }
}
