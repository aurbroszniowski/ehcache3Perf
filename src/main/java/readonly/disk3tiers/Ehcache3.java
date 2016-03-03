/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package readonly.disk3tiers;

import io.rainfall.Runner;
import io.rainfall.Scenario;
import io.rainfall.configuration.ConcurrencyConfig;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache3.CacheConfig;
import io.rainfall.ehcache3.Ehcache3Operations;
import io.rainfall.generator.LongGenerator;
import io.rainfall.generator.StringGenerator;
import io.rainfall.generator.sequence.Distribution;
import io.rainfall.unit.TimeDivision;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration;
import org.ehcache.impl.config.serializer.DefaultSerializationProviderConfiguration;
import org.ehcache.impl.serialization.CompactJavaSerializer;

import java.io.File;

import static io.rainfall.configuration.ReportingConfig.html;
import static io.rainfall.configuration.ReportingConfig.report;
import static io.rainfall.execution.Executions.during;
import static io.rainfall.execution.Executions.times;

/**
 * @author Ludovic Orban
 */
public class Ehcache3 {

  public static void main(String[] args) throws Exception {
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .using(new DefaultSerializationProviderConfiguration()
            .addSerializerFor(Long.class, (Class) CompactJavaSerializer.class)
            .addSerializerFor(String.class, (Class) CompactJavaSerializer.class)
        )
        .withCache("cache1", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class)
            .withResourcePools(ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(1000, EntryUnit.ENTRIES).offheap(32, MemoryUnit.MB).disk(2, MemoryUnit.GB))
            )
        .with(new CacheManagerPersistenceConfiguration(new File("target/rainfall/disk3tiers/ehcache3-persistence")))
        .build(true);

    final Cache<Long, String> cache1 = cacheManager.getCache("cache1", Long.class, String.class);

    LongGenerator keyGenerator = new LongGenerator();
    StringGenerator valueGenerator = new StringGenerator(4096);

    CacheConfig<Long, String> cacheConfig = new CacheConfig<Long, String>();
    cacheConfig.cache("cache1", cache1);

    final int nbElementsPerThread = 100000;
    final File reportPath = new File("target/rainfall/" + Ehcache3.class.getName().replace('.', '/'));
    Runner.setUp(
        Scenario.scenario("Loading phase")
            .exec(
                Ehcache3Operations.put(Long.class, String.class).using(keyGenerator, valueGenerator)
                    .sequentially()
            ))
        .executed(times(nbElementsPerThread))
        .config(
            ConcurrencyConfig.concurrencyConfig().threads(1),
            report(EhcacheResult.class),
            cacheConfig)
        .start();

    System.out.println("testing...");

//    Timer t = new Timer(true);
//    t.schedule(new TimerTask() {
//      @Override
//      public void run() {
//        long onHeapHits = findStat(cache1, "getOrComputeIfAbsent", "onheap-store").count(CachingTierOperationOutcomes.GetOrComputeIfAbsentOutcome.HIT);
//        long offHeapHits = findStat(cache1, "getAndRemove", "local-offheap").count(LowerCachingTierOperationsOutcome.GetAndRemoveOutcome.HIT_REMOVED);
//        long diskHits = findStat(cache1, "computeIfAbsentAndFault", "local-disk").count(AuthoritativeTierOperationOutcomes.ComputeIfAbsentAndFaultOutcome.HIT);
//        long total = onHeapHits + offHeapHits + diskHits;
//        System.out.println("        heap hits: " + onHeapHits);
//        System.out.println("     offheap hits: " + offHeapHits);
//        System.out.println("        disk hits: " + diskHits);
//        System.out.printf ("   heap hit ratio: %.1f%%\n", ((double) onHeapHits / total * 100.0));
//        System.out.printf ("offheap hit ratio: %.1f%%\n", ((double) offHeapHits / total * 100.0));
//        System.out.printf ("   disk hit ratio: %.1f%%\n", ((double) diskHits / total * 100.0));
//      }
//    }, 1000, 1000);


    Runner.setUp(
        Scenario.scenario("Testing phase")
            .exec(
                Ehcache3Operations.get(Long.class, String.class).using(keyGenerator, valueGenerator)
                    .atRandom(Distribution.GAUSSIAN, 0, nbElementsPerThread, nbElementsPerThread/10)
            ))
        .executed(during(120, TimeDivision.seconds))
        .config(
            ConcurrencyConfig.concurrencyConfig().threads(Runtime.getRuntime().availableProcessors()),
            report(EhcacheResult.class, new EhcacheResult[] {EhcacheResult.GET, EhcacheResult.MISS}).log(html(reportPath.getPath())),
            cacheConfig)
        .start();

    cacheManager.close();

    System.exit(0);
  }

}
