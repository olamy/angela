package com.terracottatech.qa.angela.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.process.PidUtil;

import com.terracottatech.qa.angela.common.util.OS;
import com.terracottatech.qa.angela.common.util.ProcessUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Aurelien Broszniowski
 */

public class HardwareMetricsCollector {

  private final static Logger logger = LoggerFactory.getLogger(HardwareMetricsCollector.class);
  private final static String METRICS_DIRECTORY = "metrics";;
  private FileOutputStream output = null;

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
      File statsDirectory = new File(installLocation, METRICS_DIRECTORY);
      statsDirectory.mkdirs();
      File logFile = new File(statsDirectory, "vmstat.log");
      logger.info("stat log file: {}", logFile.getAbsolutePath());

      try {
        output = new FileOutputStream(logFile);
        ProcessExecutor pe = new ProcessExecutor()
            .command(startCommand(stats))
            .directory(installLocation)
            .redirectError(System.err)
            .redirectOutput(output);
        process = pe.start();

      } catch (IOException e) {
        throw new RuntimeException("Can not start hardware monitoring process", e);
      }
    }
  }

  public void stopMonitoring() {
    List<Exception> exceptions = new ArrayList<>();

    if (this.process != null) {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(PidUtil.getPid(this.process.getProcess()));
      } catch (Exception e) {
        exceptions.add(e);
      }
      this.process = null;

    }

    if (this.output != null) {
      try {
        this.output.close();
      } catch (IOException e) {
        exceptions.add(e);
      }
      this.output = null;
    }

    if (!exceptions.isEmpty()) {
      RuntimeException runtimeException = new RuntimeException();
      exceptions.forEach(runtimeException::addSuppressed);
      throw runtimeException;
    }
  }


}
