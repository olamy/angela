package org.terracotta.angela.agent.kit;

import org.junit.Test;
import org.terracotta.angela.common.distribution.Distribution;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.terracotta.angela.agent.kit.LocalKitManager.INSTALLATION_LOCK_FILE_NAME;

/**
 * @author Aurelien Broszniowski
 */

public class LocalKitManagerTest {

  @Test
  public void testLock() throws InterruptedException {
    final File file = Paths.get(".").resolve(INSTALLATION_LOCK_FILE_NAME).toFile();

    System.out.println(file.getAbsolutePath());
    file.delete();

    final LocalKitManager localKitManager = new LocalKitManager(null);

    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final Thread thread = new Thread(() -> {
        localKitManager.lockConcurrentInstall(Paths.get("."));
        localKitManager.unlockConcurrentInstall(Paths.get("."));
      });
      thread.start();
      threads.add(thread);
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(file.exists(), equalTo(false));
  }


}
