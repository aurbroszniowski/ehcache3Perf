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
package readonly.onheap;

import io.rainfall.Runner;
import io.rainfall.Scenario;
import io.rainfall.SyntaxException;
import io.rainfall.configuration.ConcurrencyConfig;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache3.CacheConfig;
import io.rainfall.ehcache3.Ehcache3Operations;
import io.rainfall.ehcache3.operation.TpsSpikeGetOperation;
import io.rainfall.generator.LongGenerator;
import io.rainfall.generator.StringGenerator;
import io.rainfall.generator.sequence.Distribution;
import io.rainfall.statistics.StatisticsPeekHolder;
import io.rainfall.unit.TimeDivision;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;

import java.io.File;

import static io.rainfall.configuration.ReportingConfig.html;
import static io.rainfall.configuration.ReportingConfig.report;
import static io.rainfall.execution.Executions.during;
import static io.rainfall.execution.Executions.times;

/**
 * analyze churn with $JAVA_HOME/bin/jmc
 * <p/>
 * Goal : Run a spike-based access to the cache, throttling TP to 90% of limit
 *
 * @author Ludovic Orban
 */
public class Ehcache3 {

  public static void main(String[] args) throws Exception {
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
//        .using(new DefaultSerializationProviderConfiguration()
//            .addSerializerFor(Long.class, (Class) CompactJavaSerializer.class)
//            .addSerializerFor(String.class, (Class) CompactJavaSerializer.class)
//        )
        .withCache("cache1", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class)
//            .withKeySerializingCopier().withValueSerializingCopier()
            .build())
        .build(true);

    Cache<Long, String> cache1 = cacheManager.getCache("cache1", Long.class, String.class);

    LongGenerator keyGenerator = new LongGenerator();
    StringGenerator valueGenerator = new StringGenerator(4096);

    CacheConfig<Long, String> cacheConfig = new CacheConfig<Long, String>();
    cacheConfig.cache("cache1", cache1);

    int nbElementsPerThread = 100000;

    loadCache(nbElementsPerThread, keyGenerator, valueGenerator, cacheConfig);

    int nbThreads = Integer.parseInt(System.getProperty("nbThreads", "1"));
    final File reportPath = new File(System.getProperty("reportDir"));

    StatisticsPeekHolder finalStats = accessTest(nbThreads, keyGenerator, valueGenerator, nbElementsPerThread, cacheConfig, reportPath);

    System.out.println("---> Max TPS" + finalStats.getTotalStatisticsPeeks().getCumulativeTps(EhcacheResult.GET));

    cacheManager.close();

    System.exit(0);
  }

  private static void loadCache(final int nbElementsPerThread, final LongGenerator keyGenerator, final StringGenerator valueGenerator, final CacheConfig<Long, String> cacheConfig) throws SyntaxException {
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

  }

  private static StatisticsPeekHolder accessTest(int nbThreads, LongGenerator keyGenerator, StringGenerator valueGenerator,
                                                 int nbElementsPerThread, CacheConfig<Long, String> cacheConfig, File reportPath) throws SyntaxException {
    System.out.println("testing...");

    return Runner.setUp(
        Scenario.scenario("Testing phase")
            .exec(
                new TpsSpikeGetOperation<Long, String >(4_000_000)
                .using(keyGenerator, valueGenerator)
                    .atRandom(Distribution.GAUSSIAN, 0, nbElementsPerThread, nbElementsPerThread / 10)
            ))
        .executed(during(180, TimeDivision.seconds))
        .config(
            ConcurrencyConfig.concurrencyConfig().threads(nbThreads),
            report(EhcacheResult.class, new EhcacheResult[] { EhcacheResult.GET, EhcacheResult.MISS })
                .log(html(reportPath.getPath())), cacheConfig)
        .start();

  }

}
