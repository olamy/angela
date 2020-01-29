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

package org.terracotta.angela.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalLoggers {

  private static final String PREFIX = "org.terracotta.angela.external";

  public static final Logger clusterToolLogger = LoggerFactory.getLogger(PREFIX + ".cluster-tool");
  public static final Logger configToolLogger = LoggerFactory.getLogger(PREFIX + ".config-tool");
  public static final Logger clientLogger = LoggerFactory.getLogger(PREFIX + ".client");
  public static final Logger sshLogger = LoggerFactory.getLogger(PREFIX + ".ssh");
  public static final Logger tsaLogger = LoggerFactory.getLogger(PREFIX + ".tsa");
  public static final Logger tmsLogger = LoggerFactory.getLogger(PREFIX + ".tms");

}
