package com.terracottatech.qa.angela.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.ProcessUtil;
import org.zeroturnaround.process.Processes;

import com.terracottatech.qa.angela.common.util.OS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Aurelien Broszniowski
 */

public class HardwareMetricsCollector {

  private final static Logger logger = LoggerFactory.getLogger(HardwareMetricsCollector.class);
  private final static String METRICS_DIRECTORY = "metrics";;

  public enum TYPE {vmstat, none;}

  private StartedProcess process = null;

  public String[] startCommand(final TYPE stats) {
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

  public void startMonitoring(final File installLocation, final TYPE stats) {
    if (stats != TYPE.none) {

      final FileOutputStream output;
      try {
        final File statsDirectory = new File(installLocation, METRICS_DIRECTORY);
        statsDirectory.mkdirs();
        final File logFile = new File(statsDirectory, "vmstat.log");
        logger.info("stat log file: {}", logFile.getAbsolutePath());
        output = new FileOutputStream(logFile);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }

      try {
        process = new ProcessExecutor()
            .command(startCommand(stats))
            .directory(installLocation)
            .redirectError(System.err)
            .redirectOutput(output).start();
      } catch (IOException e) {
        throw new RuntimeException("Can not start hardware monitoring process", e);
      }
    }
  }

  public void stopMonitoring() {
    if (this.process != null) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(Processes.newPidProcess(process.getProcess()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }


}
