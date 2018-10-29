package com.terracottatech.qa.angela.common.util;

import org.apache.commons.lang.SystemUtils;
import org.zeroturnaround.process.Processes;
import org.zeroturnaround.process.UnixProcess;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProcessUtil {

  public static void destroyGracefullyOrForcefullyAndWait(int pid) throws IOException, InterruptedException, TimeoutException {
    org.zeroturnaround.process.ProcessUtil.destroyGracefullyOrForcefullyAndWait(SystemUtils.IS_OS_LINUX ? new LinuxProcess(pid) : Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
  }

  /**
   * Workaround as lower case 'kill' or 'term' signal used
   * in UnixProcess doesn't work on some environment(especially on Suse).
   * A proper fix is in https://github.com/zeroturnaround/zt-process-killer/pull/16 but hasn't been released yet.
   */
  private static class LinuxProcess extends UnixProcess {

    public LinuxProcess(final int pid) {
      super(pid);
    }

    @Override
    public void destroy(boolean forceful) throws IOException, InterruptedException {
      kill(forceful ? "KILL" : "TERM");
    }
  }
}
