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

  private SimpleProcess process = null;

  public static STAT parse() {
    final String stats = System.getProperty("stats");
    if (stats == null) {
      return STAT.none;
    } else {
      return STAT.valueOf(stats);
    }
  }

  public String[] startCommand(final STAT stats) {
    OS os = OS.INSTANCE;

    String command[] = null;
    switch (stats) {
      case vmstat:
        if (os.isUnix()) {
          command = new String[] { "/usr/bin/vmstat", "-t", "15" };
        } else if (os.isMac()) {
          command = new String[] { "/usr/bin/vm_stat", "15" };
        } else {
          throw new UnsupportedOperationException("Monitoring with vmstat on OS " + os.toString() + " is not supported");
        }
        break;
    }
    if (command == null) {
      throw new RuntimeException("Call to HardwareStats.startCommand() while no monitoring is defined.");
    }
    return command;
  }

  public void startMonitoring(final File installLocation, final STAT stats) {
    if (stats != STAT.none) {

      final FileOutputStream output;
      try {
        final File statsDirectory = new File(installLocation, "stats");
        statsDirectory.mkdirs();
        final File logFile = new File(statsDirectory, "vmstat.log");
        logger.info("stat log file: {}" , logFile.getAbsolutePath());
        output = new FileOutputStream(logFile);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }

      process = new SimpleProcess(new ProcessExecutor()
          .command(startCommand(stats))
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
