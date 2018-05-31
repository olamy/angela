package com.terracottatech.qa.angela.common.tms.security.config;

import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;

public class TmsClientSecurityConfig {

  private final URI truststoreUri;
  private final String password;

  public TmsClientSecurityConfig(String password, URI truststoreUri) {
    this.password = password;
    this.truststoreUri = truststoreUri;
  }

  public TrustManagerFactory getTrustManagerFactory() throws Exception {
    InputStream truststoreStream = new FileInputStream(truststoreUri.getPath());
    KeyStore truststore = KeyStore.getInstance("JKS");
    truststore.load(truststoreStream, password.toCharArray());
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(truststore);
    return trustManagerFactory;
  }
}
