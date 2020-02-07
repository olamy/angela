package org.terracotta.angela.agent.kit;

import org.junit.Test;
import org.terracotta.angela.common.distribution.Distribution;
import org.terracotta.angela.common.topology.PackageType;
import org.terracotta.angela.common.topology.Version;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.terracotta.angela.agent.kit.LocalKitManager.ANGELA_LOCK_FILE;

/**
 * @author Aurelien Broszniowski
 */

public class LocalKitManagerTest {

  @Test
  public void testLock() throws InterruptedException {
    final File file = Paths.get(".").resolve(ANGELA_LOCK_FILE).toFile();

    System.out.println(file.getAbsolutePath());
    file.delete();

    Distribution distribution = mock(Distribution.class);
    when(distribution.getPackageType()).thenReturn(PackageType.KIT);
    when(distribution.getVersion()).thenReturn(new Version("10.5.0.0.1"));

    final LocalKitManager localKitManager = new LocalKitManager(distribution);

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
