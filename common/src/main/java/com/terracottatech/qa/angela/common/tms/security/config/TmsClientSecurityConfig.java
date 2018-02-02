package com.terracottatech.qa.angela.common.tms.security.config;

import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;

public class TmsClientSecurityConfig {

  private final URL truststoreUrl;
  private final String password;

  public TmsClientSecurityConfig(String password, URL truststoreUrl) {
    this.password = password;
    this.truststoreUrl = truststoreUrl;
  }

  public TrustManagerFactory getTrustManagerFactory() throws Exception {
    InputStream truststoreStream = new FileInputStream(truststoreUrl.getPath());
    KeyStore truststore = KeyStore.getInstance("JKS");
    truststore.load(truststoreStream, password.toCharArray());
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(truststore);
    return trustManagerFactory;
  }
}
