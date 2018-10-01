package com.terracottatech.qa.angela.common.http;

import com.terracottatech.qa.angela.common.tms.security.config.TmsClientSecurityConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.apache.commons.lang.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HttpUtils {

  private final static Set<HttpCookie> cookies = new HashSet<>();
  private final static Map<String, String> additionnalHeaders = new HashMap<>();

  public static String sendGetRequest(String url, TmsClientSecurityConfig tmsClientSecurityConfig) {
    try {
      HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, new URL(url));
      saveHeaders(connection);
      checkResponseCode(connection);
      return inputStreamToString(connection);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public static String sendPostRequest(String url, String payload, TmsClientSecurityConfig tmsClientSecurityConfig) {

    try {
      HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, new URL(url));
      addHttpPostPayload(payload, connection);
      saveHeaders(connection);
      checkResponseCode(connection);
      return inputStreamToString(connection);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static HttpURLConnection prepareHttpConnection(TmsClientSecurityConfig tmsClientSecurityConfig, URL obj) throws IOException, GeneralSecurityException {
    HttpURLConnection connection;
    if (tmsClientSecurityConfig == null) {
      // not secured
      connection = (HttpURLConnection) obj.openConnection();
    } else {
      //secured
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmsClientSecurityConfig.getTrustManagerFactory().getTrustManagers(), null);
      connection = (HttpURLConnection) obj.openConnection();

      if (connection instanceof HttpsURLConnection) {
        ((HttpsURLConnection) connection).setSSLSocketFactory(context.getSocketFactory());
      }
    }
    if (additionnalHeaders.size() > 0) {
      additionnalHeaders.forEach((key, value) -> connection.setRequestProperty(key, value));
    }
    if (!cookies.isEmpty()) {
      connection.setRequestProperty("Cookie", StringUtils.join(cookies, ";"));
    }
    return connection;
  }

  private static void addHttpPostPayload(String payload, HttpURLConnection con) throws IOException {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("content-type", "application/json");
    con.setRequestMethod("POST");
    for (Map.Entry<String, String> headersEntries : headers.entrySet()) {
      con.setRequestProperty(headersEntries.getKey(), headersEntries.getValue());
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

  private static void saveHeaders(HttpURLConnection connection) {
    String cookiesHeader = connection.getHeaderField("Set-Cookie");
    if (cookiesHeader != null) {
      List<HttpCookie> httpCookiesParsed = connection.getHeaderFields()
          .entrySet()
          .stream()
          .filter(headers -> headers.getKey() != null)
          .filter(headers -> headers.getKey().equals("Set-Cookie"))
          .map(headers -> headers.getValue())
          .flatMap(headerValues -> headerValues.stream())
          .map(header -> HttpCookie.parse(header))
          .flatMap(cookiesLists -> cookiesLists.stream())
          .collect(Collectors.toList());
      List<HttpCookie> cookiesToRemove = new ArrayList<>();
      httpCookiesParsed
          .forEach(cookieParsed ->
              cookies.stream().filter(cookie -> cookie.getName().equals(cookieParsed.getName()))
                  .forEach(cookiesToRemove::add)
          );
      cookies.removeAll(cookiesToRemove);
      cookies.addAll(httpCookiesParsed);
    }
    cookies
        .stream()
        .filter(httpCookie -> httpCookie.getName().equals("XSRF-TOKEN"))
        .findFirst()
        .ifPresent(xsrfTokenCookie -> additionnalHeaders.put("X-XSRF-TOKEN", xsrfTokenCookie.getValue()));
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

  public static void login(String tmsUrl, TmsClientSecurityConfig tmsClientSecurityConfig) throws IOException, GeneralSecurityException {

    // get the login page to load the XSRF token
    sendGetRequest(tmsUrl + "/login.html", tmsClientSecurityConfig);

    String urlParameters = "username=" + tmsClientSecurityConfig.getUsername() + "&password=" + tmsClientSecurityConfig.getPassword();
    URL url = new URL(tmsUrl + "/api/security/login");
    byte[] postData = urlParameters.getBytes(UTF_8);
    int postDataLength = postData.length;
    HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, url);

    connection.setRequestMethod("POST");
    connection.setInstanceFollowRedirects(false);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.setRequestProperty("charset", "utf-8");
    connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
    try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
      wr.write(postData);
    }

    saveHeaders(connection);

    int responseCode = connection.getResponseCode();
    if (responseCode != 302) {
      throw new FailedHttpRequestException(responseCode);
    }

  }

  public static class FailedHttpRequestException extends RuntimeException {
    public FailedHttpRequestException(int responseCode) {
      super("The HTTP request failed with error code : " + responseCode);
    }
  }
}
