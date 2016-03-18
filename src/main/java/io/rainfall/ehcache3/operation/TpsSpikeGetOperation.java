/*
 * Copyright 2014 Aurélien Broszniowski
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

package io.rainfall.ehcache3.operation;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.TestException;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache3.CacheConfig;
import io.rainfall.statistics.StatisticsHolder;
import org.ehcache.Cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.rainfall.ehcache.statistics.EhcacheResult.EXCEPTION;
import static io.rainfall.ehcache.statistics.EhcacheResult.GET;
import static io.rainfall.ehcache.statistics.EhcacheResult.MISS;

/**
 * @author Aurelien Broszniowski
 */
public class TpsSpikeGetOperation<K, V> extends GetOperation<K, V> {

  private final long tpsLimit;
  private final long tpsInitialValue;
  private long tpsIncreaseStep;
  private long currentTpsLimit;

  public TpsSpikeGetOperation(final long tpsLimit) {
    this.tpsLimit = tpsLimit;
    tpsInitialValue = tpsLimit / 100;
    tpsIncreaseStep = tpsLimit / 100;
    this.currentTpsLimit = tpsIncreaseStep;

    Runnable maxTpsUpdater = new Runnable() {

      @Override
      public void run() {
        try {
          while (true) {
            Thread.sleep(500);
            currentTpsLimit += tpsIncreaseStep;
            if (currentTpsLimit >= tpsLimit) {
              currentTpsLimit = tpsLimit;
              tpsIncreaseStep = -tpsIncreaseStep;
            } else if (currentTpsLimit <= tpsInitialValue) {
              currentTpsLimit = tpsInitialValue;
              tpsIncreaseStep = -tpsIncreaseStep;
            }
          }
        } catch (InterruptedException e) { e.printStackTrace(); }
      }
    };

    Thread t = new Thread(maxTpsUpdater);
    t.start();
//    try {
//      t.join();
//    } catch (InterruptedException e) { e.printStackTrace(); }
  }

  @Override
  public void exec(final StatisticsHolder statisticsHolder, final Map<Class<? extends Configuration>,
      Configuration> configurations, final List<AssertionEvaluator> assertions) throws TestException {

    CacheConfig<K, V> cacheConfig = (CacheConfig<K, V>)configurations.get(CacheConfig.class);
    final long next = this.sequenceGenerator.next();
    List<Cache<K, V>> caches = cacheConfig.getCaches();
    long currentTps = statisticsHolder.getCurrentTps(EhcacheResult.GET);

    if (currentTps < this.currentTpsLimit) {
      for (final Cache<K, V> cache : caches) {
        K k = keyGenerator.generate(next);
        V value;

        long start = getTimeInNs();
        try {
          value = cache.get(k);
          long end = getTimeInNs();
          if (value == null) {
            statisticsHolder.record(cacheConfig.getCacheName(cache), (end - start), MISS);
          } else {
            statisticsHolder.record(cacheConfig.getCacheName(cache), (end - start), GET);
          }
        } catch (Exception e) {
          long end = getTimeInNs();
          statisticsHolder.record(cacheConfig.getCacheName(cache), (end - start), EXCEPTION);
        }
      }
    }
  }

  @Override
  public List<String> getDescription() {
    List<String> desc = new ArrayList<String>();
    desc.add(getWeightInPercent() + "% SPIKED get(" + keyGenerator.getDescription() + " key)");
    desc.add(sequenceGenerator.getDescription());
    return desc;
  }
}
