package com.terracottatech.qa.angela.common.http;

import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.security.KeyStore;
import java.util.Map;

public class HttpsUtils {

  public static String sendGetRequest(String url, TmsClientSecurityConfig tmsClientSecurityConfig) throws Exception {
    try {
      StringBuilder response = new StringBuilder();
      URL obj = new URL(url);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmsClientSecurityConfig.getTrustManagerFactory().getTrustManagers(), null);

      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
      con.setSSLSocketFactory(context.getSocketFactory());

      int responseCode = con.getResponseCode();
      if (responseCode < 200 || responseCode > 299) {
        throw new HttpsUtils.FailedHttpsRequestException(responseCode);
      }
      try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
        }
      }
      return response.toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String sendPostRequest(String url, String payload, Map<String, String> headers, TmsClientSecurityConfig tmsClientSecurityConfig) throws Exception {

    try {
      StringBuilder response = new StringBuilder();
      URL obj = new URL(url);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmsClientSecurityConfig.getTrustManagerFactory().getTrustManagers(), null);

      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
      con.setSSLSocketFactory(context.getSocketFactory());
      con.setRequestMethod("POST");

      for (Map.Entry<String, String> stringStringEntry : headers.entrySet()) {
        con.setRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
      }

      con.setDoOutput(true);
      try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
        wr.writeBytes(payload);
        wr.flush();
      }

      int responseCode = con.getResponseCode();
      if (responseCode < 200 || responseCode > 299) {
        throw new HttpsUtils.FailedHttpsRequestException(responseCode);
      }

      try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
        }
      }
      return response.toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class FailedHttpsRequestException extends RuntimeException {

    public FailedHttpsRequestException(int responseCode) {
      super("The HTTPS request failed with error code : " + responseCode);
    }
  }
}
