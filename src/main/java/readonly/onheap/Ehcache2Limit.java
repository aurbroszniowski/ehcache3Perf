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
import io.rainfall.ehcache2.CacheConfig;
import io.rainfall.ehcache2.Ehcache2Operations;
import io.rainfall.ehcache2.operation.TpsSpikeGetOperation;
import io.rainfall.generator.LongGenerator;
import io.rainfall.generator.StringGenerator;
import io.rainfall.generator.sequence.Distribution;
import io.rainfall.statistics.StatisticsPeekHolder;
import io.rainfall.unit.TimeDivision;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;

import java.io.File;

import static io.rainfall.configuration.ReportingConfig.html;
import static io.rainfall.configuration.ReportingConfig.report;
import static io.rainfall.execution.Executions.during;
import static io.rainfall.execution.Executions.times;

/**
 * @author Ludovic Orban
 */
public class Ehcache2Limit {

  public static void main(String[] args) throws Exception {
    System.setProperty("com.tc.productkey.path", System.getProperty("user.home") + "/.tc/terracotta-license.key");
    Configuration configuration = new Configuration();
    CacheConfiguration cacheConfiguration = new CacheConfiguration("cache1", 0);
//    cacheConfiguration.setCopyOnRead(true);
//    cacheConfiguration.setCopyOnWrite(true);
    configuration.addCache(cacheConfiguration);
    CacheManager cacheManager = new CacheManager(configuration);

    final Cache cache1 = cacheManager.getCache("cache1");

    LongGenerator keyGenerator = new LongGenerator();
    StringGenerator valueGenerator = new StringGenerator(4096);

    CacheConfig<Long, String> cacheConfig = new CacheConfig<Long, String>();
    cacheConfig.caches(cache1);

    final int nbElementsPerThread = 100000;

    loadCache(nbElementsPerThread, keyGenerator, valueGenerator, cacheConfig);

    int nbThreads = Integer.parseInt(System.getProperty("nbThreads", "1"));
    final File reportPath = new File(System.getProperty("reportDir"));

    StatisticsPeekHolder finalStats = accessTest(nbThreads, keyGenerator, valueGenerator, nbElementsPerThread, cacheConfig, reportPath);

    System.out.println("---> Max TPS" + finalStats.getTotalStatisticsPeeks().getCumulativeTps(EhcacheResult.GET));

    cacheManager.shutdown();

    System.exit(0);
  }

  private static void loadCache(final int nbElementsPerThread, final LongGenerator keyGenerator, final StringGenerator valueGenerator, CacheConfig<Long, String> cacheConfig) throws SyntaxException {
    final File reportPath = new File("target/rainfall/" + Ehcache2Limit.class.getName().replace('.', '/'));
    Runner.setUp(
        Scenario.scenario("Loading phase")
            .exec(
                Ehcache2Operations.put(Long.class, String.class).using(keyGenerator, valueGenerator)
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
                Ehcache2Operations.get(Long.class, String.class).using(keyGenerator, valueGenerator)
                    .atRandom(Distribution.GAUSSIAN, 0, nbElementsPerThread, nbElementsPerThread / 10)
            ))
        .executed(during(180, TimeDivision.seconds))
        .config(
            ConcurrencyConfig.concurrencyConfig().threads(Runtime.getRuntime().availableProcessors()),
            report(EhcacheResult.class, new EhcacheResult[] { EhcacheResult.GET, EhcacheResult.MISS }).log(html(reportPath
                .getPath())),
            cacheConfig)
        .start();

  }

}
