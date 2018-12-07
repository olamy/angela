package com.terracottatech.qa.angela.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;
import org.zeroturnaround.process.PidUtil;

import com.terracottatech.qa.angela.common.util.ExternalLoggers;
import com.terracottatech.qa.angela.common.util.ProcessUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aurelien Broszniowski
 */

public class HardwareMetricsCollector {
  private final static Logger LOGGER = LoggerFactory.getLogger(HardwareMetricsCollector.class);
  public final static String METRICS_DIRECTORY = "metrics";

  private FileOutputStream outputStream;
  private Map<HardwareMetric, StartedProcess> processes = new HashMap<>();

  public void startMonitoring(final File installLocation, final Map<HardwareMetric, MonitoringCommand> commands) {
    if (commands != null && commands.size() != 0) {
      File statsDirectory = new File(installLocation, METRICS_DIRECTORY);
      statsDirectory.mkdirs();

      commands.forEach((hardwareMetric, command) -> {
        File statsFile = new File(statsDirectory, hardwareMetric.name().toLowerCase() + "-stats.log");
        LOGGER.info("HardwareMetric log file: {}", statsFile.getAbsolutePath());
        try {
          outputStream = new FileOutputStream(statsFile);
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }

        ProcessExecutor pe = new ProcessExecutor()
            .environment(System.getenv())
            .command(command.getCommand())
            .directory(installLocation)
            .redirectError(Slf4jStream.of(ExternalLoggers.monitoring).asInfo())
            .redirectOutput(outputStream);

        try {
          LOGGER.info("Starting process with env: {}", pe.getEnvironment());
          processes.put(hardwareMetric, startProcess(pe));
        } catch (IOException e) {
          try (FileWriter fileWriter = new FileWriter(statsFile)) {
            fileWriter.write("Error executing command '" + command.getCommandName() + "': " + e.getMessage());
          } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  private StartedProcess startProcess(ProcessExecutor pe) throws IOException, InterruptedException {
    final StartedProcess startedProcess = pe.start();
    // Process can fail after getting started up for different reasons (e.g. wrong arguments). Sleep for a couple of seconds
    Thread.sleep(2000);
    final boolean isAlive = startedProcess.getProcess().isAlive();
    if (!isAlive) {
      throw new IOException("Command " + pe.getCommand() + " failed. Check error logs.");
    }
    return startedProcess;
  }

  public boolean isMonitoringRunning(HardwareMetric hardwareMetric) {
    final StartedProcess process = processes.get(hardwareMetric);
    if (process == null) {
      return false; // No process was found - i.e. it failed at startup
    } else {
      return process.getProcess().isAlive();
    }
  }

  public void stopMonitoring() {
    List<Exception> exceptions = new ArrayList<>();

    processes.values().forEach(process -> {
      try {
        ProcessUtil.destroyGracefullyOrForcefullyAndWait(PidUtil.getPid(process.getProcess()));
      } catch (Exception e) {
        exceptions.add(e);
      }
    });

    processes.clear();

    if (this.outputStream != null) {
      try {
        this.outputStream.close();
      } catch (IOException e) {
        exceptions.add(e);
      }
      this.outputStream = null;
    }

    if (!exceptions.isEmpty()) {
      RuntimeException runtimeException = new RuntimeException();
      exceptions.forEach(runtimeException::addSuppressed);
      throw runtimeException;
    }
  }
}
