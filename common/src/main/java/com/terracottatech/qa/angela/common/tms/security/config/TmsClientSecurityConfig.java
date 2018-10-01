package com.terracottatech.qa.angela.common.tms.security.config;

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
