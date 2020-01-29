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

import org.zeroturnaround.process.Processes;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProcessUtil {

  public static void destroyGracefullyOrForcefullyAndWait(int pid) throws IOException, InterruptedException, TimeoutException {
    org.zeroturnaround.process.ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
  }

}
