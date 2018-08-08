package com.terracottatech.qa.angela.common.http;

import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

  public static String sendGetRequest(String url, TmsClientSecurityConfig tmsClientSecurityConfig) {
    try {
      HttpURLConnection con = prepareHttpConnection(tmsClientSecurityConfig, new URL(url));

      checkResponseCode(con);
      return inputStreamToString(con);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public static String sendPostRequest(String url, String payload, TmsClientSecurityConfig tmsClientSecurityConfig) {

    try {
      HttpURLConnection con = prepareHttpConnection(tmsClientSecurityConfig, new URL(url));

      addHttpPostPayload(payload, con);
      checkResponseCode(con);
      return inputStreamToString(con);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static HttpURLConnection prepareHttpConnection(TmsClientSecurityConfig tmsClientSecurityConfig, URL obj) throws IOException, GeneralSecurityException {
    HttpURLConnection con;
    if(tmsClientSecurityConfig == null) {
      // not secured
      con = (HttpURLConnection) obj.openConnection();
    } else {
      //secured
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmsClientSecurityConfig.getTrustManagerFactory().getTrustManagers(), null);
      con = (HttpURLConnection) obj.openConnection();

      if(con instanceof HttpsURLConnection) {
        ((HttpsURLConnection) con).setSSLSocketFactory(context.getSocketFactory());
      }
    }
    return con;
  }

  private static void addHttpPostPayload(String payload, HttpURLConnection con) throws IOException {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("content-type", "application/json");
    con.setRequestMethod("POST");
    for (Map.Entry<String, String> stringStringEntry : headers.entrySet()) {
      con.setRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
    }
    con.setDoOutput(true);
    try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
      wr.writeBytes(payload);
    }
  }

  private static void checkResponseCode(HttpURLConnection con) throws IOException {
    int responseCode = con.getResponseCode();
    if (responseCode < 200 || responseCode > 299) {
      throw new FailedHttpRequestException(responseCode);
    }
  }

  private static String inputStreamToString(HttpURLConnection con) throws IOException {
    StringBuilder response = new StringBuilder();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
    }
    return response.toString();
  }

  public static class FailedHttpRequestException extends RuntimeException {
    public FailedHttpRequestException(int responseCode) {
      super("The HTTP request failed with error code : " + responseCode);
    }
  }
}
