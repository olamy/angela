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

import org.junit.Test;
import org.terracotta.angela.common.tms.security.config.TmsServerSecurityConfig;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author yfe
 */
public class TmsServerSecurityConfigTest {


  @Test
  public void testSettersGetters() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with($ -> {
      $.tmsSecurityRootDirectory = "TmsSecurityRootDirectory()";
      $.tmsSecurityHttpsEnabled = "TmsSecurityHttpsEnabled()";
      $.tmsSecurityAuthenticationScheme = "TmsSecurityAuthenticationScheme()";
      $.tmsSecurityAuthorizationScheme = "TmsSecurityAuthorizationScheme()";
      $.tmsSecurityRootDirectoryConnectionDefault = "TmsSecurityRootDirectoryConnectionDefault()";
      $.tmsSecurityAuditDirectory = "TmsSecurityAuditDirectory()";
      $.deprecatedSecurityRootDirectory = "DeprecatedSecurityRootDirectory()";
      $.deprecatedSecurityLevel = "DeprecatedSecurityLevel()";
    }).build();


    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectory(), is("TmsSecurityRootDirectory()"));
    assertThat(tmsServerSecurityConfig.getTmsSecurityHttpsEnabled(), is("TmsSecurityHttpsEnabled()"));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuthenticationScheme(), is("TmsSecurityAuthenticationScheme()"));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuthorizationScheme(), is("TmsSecurityAuthorizationScheme()"));
    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectoryConnectionDefault(), is("TmsSecurityRootDirectoryConnectionDefault()"));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuditDirectory(), is("TmsSecurityAuditDirectory()"));
    assertThat(tmsServerSecurityConfig.getDeprecatedSecurityRootDirectory(), is("DeprecatedSecurityRootDirectory()"));
    assertThat(tmsServerSecurityConfig.getDeprecatedSecurityLevel(), is("DeprecatedSecurityLevel()"));
  }

  @Test
  public void testNullValue() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with($ -> {
      $.tmsSecurityRootDirectory = null;
    }).build();

    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectory(), is(nullValue()));
  }

  @Test
  public void testEmptyValues() {


    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with($ -> {
      $.tmsSecurityRootDirectory = "";
    }).build();

    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectory().length(), is(0));
    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectory(), is(""));
  }

  @Test
  public void testNotSetValues() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().build();

    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectory(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getTmsSecurityHttpsEnabled(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuthenticationScheme(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuthorizationScheme(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getTmsSecurityRootDirectoryConnectionDefault(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getTmsSecurityAuditDirectory(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getDeprecatedSecurityRootDirectory(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
    assertThat(tmsServerSecurityConfig.getDeprecatedSecurityLevel(), is(TmsServerSecurityConfig.NOT_SET_VALUE));
  }

  @Test
  public void testToMapValues() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with($ -> {
      $.tmsSecurityRootDirectory = "TmsSecurityRootDirectory()";
      $.tmsSecurityHttpsEnabled = "TmsSecurityHttpsEnabled()";
      $.tmsSecurityAuthenticationScheme = "TmsSecurityAuthenticationScheme()";
      $.tmsSecurityAuthorizationScheme = "TmsSecurityAuthorizationScheme()";
      $.tmsSecurityRootDirectoryConnectionDefault = "TmsSecurityRootDirectoryConnectionDefault()";
      $.tmsSecurityAuditDirectory = "TmsSecurityAuditDirectory()";
      $.deprecatedSecurityRootDirectory = "DeprecatedSecurityRootDirectory()";
      $.deprecatedSecurityLevel = "DeprecatedSecurityLevel()";
    }).build();

    Map<String, String> map = tmsServerSecurityConfig.toMap();

    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY), is("TmsSecurityRootDirectory()"));
    assertThat(map.get(TmsServerSecurityConfig.HTTPS_ENABLED), is("TmsSecurityHttpsEnabled()"));
    assertThat(map.get(TmsServerSecurityConfig.AUTHENTICATION_SCHEME), is("TmsSecurityAuthenticationScheme()"));
    assertThat(map.get(TmsServerSecurityConfig.AUTHORIZATION_SCHEME), is("TmsSecurityAuthorizationScheme()"));
    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT), is("TmsSecurityRootDirectoryConnectionDefault()"));
    assertThat(map.get(TmsServerSecurityConfig.AUDIT_DIRECTORY), is("TmsSecurityAuditDirectory()"));
    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY), is("DeprecatedSecurityRootDirectory()"));
    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL), is("DeprecatedSecurityLevel()"));
  }

  @Test
  public void testToMapNullValues() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with($ -> {
      $.tmsSecurityRootDirectory = null;
      $.tmsSecurityHttpsEnabled = null;
      $.tmsSecurityAuthenticationScheme = null;
      $.tmsSecurityAuthorizationScheme = null;
      $.tmsSecurityRootDirectoryConnectionDefault = null;
      $.tmsSecurityAuditDirectory = null;
      $.deprecatedSecurityRootDirectory = null;
      $.deprecatedSecurityLevel = null;
    }).build();

    Map<String, String> map = tmsServerSecurityConfig.toMap();

    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY), is(true));


    assertThat(map.get(TmsServerSecurityConfig.HTTPS_ENABLED), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.HTTPS_ENABLED), is(true));

    assertThat(map.get(TmsServerSecurityConfig.AUTHENTICATION_SCHEME), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUTHENTICATION_SCHEME), is(true));

    assertThat(map.get(TmsServerSecurityConfig.AUTHORIZATION_SCHEME), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUTHORIZATION_SCHEME), is(true));

    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT), is(true));

    assertThat(map.get(TmsServerSecurityConfig.AUDIT_DIRECTORY), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUDIT_DIRECTORY), is(true));

    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY), is(true));

    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL), is(nullValue()));
    assertThat(map.containsKey(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL), is(true));
  }

  @Test
  public void testToMapEmptyValues() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().with(config -> {
      config.tmsSecurityRootDirectory = "";
      config.tmsSecurityHttpsEnabled = "";
      config.tmsSecurityAuthenticationScheme = "";
      config.tmsSecurityAuthorizationScheme = "";
      config.tmsSecurityRootDirectoryConnectionDefault = "";
      config.tmsSecurityAuditDirectory = "";
      config.deprecatedSecurityRootDirectory = "";
      config.deprecatedSecurityLevel = "";
    }).build();

    Map<String, String> map = tmsServerSecurityConfig.toMap();

    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY), is(""));
    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY).length(), is(0));

    assertThat(map.get(TmsServerSecurityConfig.HTTPS_ENABLED), is(""));
    assertThat(map.get(TmsServerSecurityConfig.HTTPS_ENABLED).length(), is(0));

    assertThat(map.get(TmsServerSecurityConfig.AUTHENTICATION_SCHEME), is(""));
    assertThat(map.get(TmsServerSecurityConfig.AUTHENTICATION_SCHEME).length(), is(0));

    assertThat(map.get(TmsServerSecurityConfig.AUTHORIZATION_SCHEME), is(""));
    assertThat(map.get(TmsServerSecurityConfig.AUTHORIZATION_SCHEME).length(), is(0));


    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT), is(""));
    assertThat(map.get(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT).length(), is(0));


    assertThat(map.get(TmsServerSecurityConfig.AUDIT_DIRECTORY), is(""));
    assertThat(map.get(TmsServerSecurityConfig.AUDIT_DIRECTORY).length(), is(0));

    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY), is(""));
    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY).length(), is(0));

    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL), is(""));
    assertThat(map.get(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL).length(), is(0));
  }

  @Test
  public void testToMapNotSetValues() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder().build();

    Map<String, String> map = tmsServerSecurityConfig.toMap();

    assertThat(map.containsKey(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.HTTPS_ENABLED), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUTHENTICATION_SCHEME), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUTHORIZATION_SCHEME), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.SECURITY_ROOT_DIRECTORY_CONNECTION_DEFAULT), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.AUDIT_DIRECTORY), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.DEPRECATED_SECURITY_ROOT_DIRECTORY), is(false));
    assertThat(map.containsKey(TmsServerSecurityConfig.DEPRECATED_SECURITY_LEVEL), is(false));
  }

  @Test
  public void testToString() {

    TmsServerSecurityConfig tmsServerSecurityConfig = new TmsServerSecurityConfig.Builder()
        .with(s -> {
          s.tmsSecurityRootDirectory = "TmsSecurityRootDirectory()";
          s.tmsSecurityHttpsEnabled = "";
          s.tmsSecurityAuthenticationScheme = null;
        }).build();

    assertThat(tmsServerSecurityConfig.toString(),
        is("tmsSecurityRootDirectory:TmsSecurityRootDirectory(), tmsSecurityHttpsEnabled:, tmsSecurityAuthenticationScheme:null, tmsSecurityAuthorizationScheme:was not set, tmsSecurityRootDirectoryConnectionDefault:was not set, tmsSecurityAuditDirectory:was not set, deprecatedSecurityRootDirectory:was not set, deprecatedSecurityLevel:was not set")
    );
  }
}