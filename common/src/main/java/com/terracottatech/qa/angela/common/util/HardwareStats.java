package com.terracottatech.qa.angela.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.distribution.SimpleProcess;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * @author Aurelien Broszniowski
 */

public class HardwareStats {

  private final static Logger logger = LoggerFactory.getLogger(HardwareStats.class);

  public static enum STAT {vmstat, none;}

  ;
  private final STAT stats;
  private SimpleProcess process = null;

  public HardwareStats() {
    final String stats = System.getProperty("stats");
    if (stats == null) {
      this.stats = STAT.none;
    } else {
      this.stats = STAT.valueOf(stats);
    }
  }

  public boolean shouldMonitor() {
    return this.stats != STAT.none;
  }

  public String[] startCommand() {
    OS os = OS.INSTANCE;

    String command[] = null;
    switch (this.stats) {
      case vmstat:
        if (os.isUnix()) {
          command = new String[] { "/usr/bin/vmstat", "-td", "15" };
        } else if (os.isMac()) {
          command = new String[] { "/usr/bin/vm_stat", "15" };
        }
        break;
    }
    if (command == null) {
      throw new RuntimeException("Call to HardwareStats.startCommand() while there should be no monitoring");
    }
    return command;
  }

  public void startMonitoring(final File installLocation) {
    if (shouldMonitor()) {

      final FileOutputStream output;
      try {
        final File statsDirectory = new File(installLocation, "stats");
        statsDirectory.mkdirs();
        final File logFile = new File(statsDirectory, "vmstat.log");
        logger.info("stat log file: {}" , logFile.getAbsolutePath());
        System.out.println("stat log file: {}" + logFile.getAbsolutePath());
        output = new FileOutputStream(logFile);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }

      process = new SimpleProcess(new ProcessExecutor()
          .command(startCommand())
          .directory(installLocation)
          .redirectError(System.err)
          .redirectOutput(output));
    }
  }

  public void stopMonitoring() {
    if (this.process != null) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(process.getPid()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }


}
