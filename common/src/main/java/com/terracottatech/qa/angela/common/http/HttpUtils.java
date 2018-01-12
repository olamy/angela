package com.terracottatech.qa.angela.common.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpUtils {
  public static String sendGetRequest(String url) {
    try {
      StringBuilder response = new StringBuilder();
      URL obj = new URL(url);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      int responseCode = con.getResponseCode();
      if (responseCode < 200 || responseCode > 299) {
        throw new FailedHttpRequestException(responseCode);
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


  public static String sendPostRequest(String url, String payload, Map<String, String> headers) {

    try {
      StringBuilder response = new StringBuilder();
      URL obj = new URL(url);
      HttpURLConnection con = (HttpURLConnection) obj.openConnection();
      con.setRequestMethod("POST");
      for (Map.Entry<String, String> stringStringEntry : headers.entrySet()) {
        con.setRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
      }

      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(payload);
      wr.flush();
      wr.close();

      int responseCode = con.getResponseCode();
      if (responseCode < 200 || responseCode > 299) {
        throw new FailedHttpRequestException(responseCode);
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

  public static class FailedHttpRequestException extends RuntimeException {

    public FailedHttpRequestException(int responseCode) {
      super("The HTTP request failed with error code : " + responseCode);
    }
  }
}
