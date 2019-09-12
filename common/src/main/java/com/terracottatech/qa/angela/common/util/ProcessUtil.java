package com.terracottatech.qa.angela.common.util;

import org.zeroturnaround.process.Processes;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ProcessUtil {

  public static void destroyGracefullyOrForcefullyAndWait(int pid) throws IOException, InterruptedException, TimeoutException {
    org.zeroturnaround.process.ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(pid), 30, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
  }

}
