package com.terracottatech.qa.angela;

import com.terracottatech.qa.angela.client.ClusterFactory;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.custom.CustomConfigurationContext;
import com.terracottatech.qa.angela.common.topology.LicenseType;
import com.terracottatech.qa.angela.common.topology.PackageType;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.junit.Ignore;
import org.junit.Test;

import static com.terracottatech.qa.angela.TestUtils.TC_CONFIG_OS;
import static com.terracottatech.qa.angela.common.distribution.Distribution.distribution;
import static com.terracottatech.qa.angela.common.tcconfig.TcConfig.tcConfig;
import static com.terracottatech.qa.angela.common.topology.Version.version;
import static com.terracottatech.qa.angela.test.Versions.EHCACHE_OS_VERSION;

public class EhcacheOsTest {
  @Test
  public void testTsaWithEhcacheReleaseKit() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(
            tsa -> tsa.topology(
                new Topology(
                    distribution(version(EHCACHE_OS_VERSION), PackageType.KIT, LicenseType.EHCACHE_OS),
                    tcConfig(version(EHCACHE_OS_VERSION), TC_CONFIG_OS)
                )
            )
        );

    try (ClusterFactory factory = new ClusterFactory("EhcacheOsTest::testTsaWithEhcacheReleaseKit", configContext)) {
      factory.tsa().startAll();
    }
  }

  @Test
  public void testTsaWithEhcacheSnapshotKit() throws Exception {
    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
        .tsa(
            tsa -> tsa.topology(
                new Topology(
                    distribution(version("3.8-SNAPSHOT"), PackageType.KIT, LicenseType.EHCACHE_OS),
                    tcConfig(version("3.8-SNAPSHOT"), TC_CONFIG_OS)
                )
            )
        );

    try (ClusterFactory factory = new ClusterFactory("EhcacheOsTest::testTsaWithEhcacheSnapshotKit", configContext)) {
      factory.tsa().startAll();
    }
  }

  @Test
  @Ignore("TDB-4667")
  public void testClusteredEhcacheOperations() throws Exception {
    // The following code needs clustered-ehcache jar to compile. Having the jar messes us other tests like TcDBTest

//    ConfigurationContext configContext = CustomConfigurationContext.customConfigurationContext()
//        .tsa(
//            tsa -> tsa.topology(
//                new Topology(
//                    distribution(version(EHCACHE_OS_VERSION), PackageType.KIT, LicenseType.EHCACHE_OS),
//                    tcConfig(version(EHCACHE_OS_VERSION), TC_CONFIG_OS)
//                )
//            )
//        ).clientArray(
//            clientArray -> clientArray.clientArrayTopology(
//                new ClientArrayTopology(
//                    distribution(version(EHCACHE_OS_VERSION), PackageType.KIT, LicenseType.EHCACHE_OS),
//                    newClientArrayConfig().hostSerie(1, "localhost")
//                )
//            )
//        );
//
//    try (ClusterFactory factory = new ClusterFactory("EhcacheOsTest::testConnection", configContext)) {
//      factory.tsa().startAll();
//      ClientArray clientArray = factory.clientArray();
//      String uri = factory.tsa().uri().toString() + "/clustered-cache-manager";
//      String cacheAlias = "clustered-cache";
//
//      ClientJob clientJob = (cluster) -> {
//        try (CacheManager cacheManager = createCacheManager(uri, cacheAlias)) {
//          Cache<Long, String> cache = cacheManager.getCache(cacheAlias, Long.class, String.class);
//          final int numKeys = 10;
//          for (long key = 0; key < numKeys; key++) {
//            cache.put(key, String.valueOf(key) + key);
//          }
//
//          for (long key = 0; key < numKeys; key++) {
//            assertEquals(cache.get(key), String.valueOf(key) + key);
//          }
//        }
//      };
//
//      ClientArrayFuture caf = clientArray.executeOnAll(clientJob);
//      caf.get();
//    }
//  }
//
//  private static CacheManager createCacheManager(String uri, String cacheAlias) {
//    return CacheManagerBuilder
//        .newCacheManagerBuilder()
//        .with(ClusteringServiceConfigurationBuilder
//            .cluster(URI.create(uri))
//            .autoCreate(s -> s.defaultServerResource("main").resourcePool("resource-pool-a", 10, MB))
//        ).withCache(cacheAlias, CacheConfigurationBuilder.newCacheConfigurationBuilder(
//            Long.class,
//            String.class,
//            ResourcePoolsBuilder.newResourcePoolsBuilder()
//                .heap(1000, EntryUnit.ENTRIES)
//                .offheap(1, MB)
//                .with(ClusteredResourcePoolBuilder.clusteredShared("resource-pool-a"))
//            )
//        ).build(true);
  }
}