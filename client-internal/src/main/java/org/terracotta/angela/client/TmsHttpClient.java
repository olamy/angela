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

package org.terracotta.angela.client;

import org.terracotta.angela.common.tms.security.config.TmsClientSecurityConfig;
import io.restassured.path.json.JsonPath;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TmsHttpClient {

  private final static Logger LOGGER = LoggerFactory.getLogger(TmsHttpClient.class);

  private static final String SET_COOKIE = "Set-Cookie";
  private static final String POST = "POST";
  private final String tmsBaseUrl;
  private final TmsClientSecurityConfig tmsClientSecurityConfig;
  private final Set<HttpCookie> cookies = new HashSet<>();
  private final Map<String, String> additionnalHeaders = new HashMap<>();
  private static final String API_CONNECTIONS_PROBE_OLD = "/api/connections/probe/";
  private static final String API_CONNECTIONS_PROBE_NEW = "/api/connections/probe?uri=";
  private static final String APPLICATION_JSON = "application/json";


  TmsHttpClient(String tmsBaseUrl, TmsClientSecurityConfig tmsClientSecurityConfig) {
    this.tmsBaseUrl = Objects.requireNonNull(tmsBaseUrl);
    this.tmsClientSecurityConfig = tmsClientSecurityConfig;
  }

  /**
   * This method connects to the TMS via HTTPS (securely) REST calls.  It also creates a TMS connection to the cluster.
   * If cluster security is enabled it will connect to the cluster via SSL/TLS, otherwise if connects via plain text.
   *
   * @param uri                     of the cluster to connect to
   * @return connectionName
   */
  public String createConnectionToCluster(URI uri) {
    String connectionName;
    LOGGER.info("connecting TMS to {}", uri.toString());
    // probe
    String response;
    try {
      response = probeOldStyle(uri);
    } catch (FailedHttpRequestException e) {
      // in 10.3, probe calls need to use /probe?uri=host:port instead of : /probe/host:port in 10.2
      response = probeNewStyle(uri);
    }

    // create connection
    response = sendPostRequest("/api/connections", response);
    LOGGER.info("tms connect result :" + response);

    connectionName = JsonPath.from(response).get("config.connectionName");

    return connectionName;
  }

  private String probeOldStyle(URI uri) {
    return probe(uri, API_CONNECTIONS_PROBE_OLD);
  }

  private String probeNewStyle(URI uri) {
    return probe(uri, API_CONNECTIONS_PROBE_NEW);
  }

  private String probe(URI uri, String probeEndpoint) {
    try {
      String response = sendGetRequest(probeEndpoint +
          URLEncoder.encode(uri.toString(), String.valueOf(UTF_8)));
      LOGGER.info("tms probe result :" + response);
      return response;
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Could not encode terracotta connection url", e);
    }
  }

  public void login() throws IOException, GeneralSecurityException {
    // get the login page to load the XSRF token
    sendGetRequest("/login.html");

    String urlParameters = "username=" + tmsClientSecurityConfig.getUsername() + "&password=" + tmsClientSecurityConfig.getPassword();
    URL url = new URL(tmsBaseUrl + "/api/security/login");
    byte[] postData = urlParameters.getBytes(UTF_8);
    int postDataLength = postData.length;
    HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, url);

    connection.setRequestMethod(POST);
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

  public String sendGetRequest(String path) {
    try {
      HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, new URL(tmsBaseUrl + path));
      saveHeaders(connection);
      checkResponseCode(connection);
      try (InputStream inputStream = connection.getInputStream()) {
        return IOUtils.toString(inputStream);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public String sendPostRequest(String path, String payload) {
    try {
      HttpURLConnection connection = prepareHttpConnection(tmsClientSecurityConfig, new URL(tmsBaseUrl + path));
      addHttpPostPayload(payload, connection);
      saveHeaders(connection);
      checkResponseCode(connection);
      try (InputStream inputStream = connection.getInputStream()) {
        return IOUtils.toString(inputStream);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpURLConnection prepareHttpConnection(TmsClientSecurityConfig tmsClientSecurityConfig, URL obj) throws IOException, GeneralSecurityException {
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
    headers.put("Accept", APPLICATION_JSON);
    headers.put("content-type", APPLICATION_JSON);
    con.setRequestMethod(POST);
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

  private void saveHeaders(HttpURLConnection connection) {
    String cookiesHeader = connection.getHeaderField(SET_COOKIE);
    if (cookiesHeader != null) {
      List<HttpCookie> httpCookiesParsed = connection.getHeaderFields()
          .entrySet()
          .stream()
          .filter(headers -> headers.getKey() != null)
          .filter(headers -> headers.getKey().equals(SET_COOKIE))
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

  public static class FailedHttpRequestException extends RuntimeException {
    public FailedHttpRequestException(int responseCode) {
      super("The HTTP request failed with response code : " + responseCode);
    }
  }
}
