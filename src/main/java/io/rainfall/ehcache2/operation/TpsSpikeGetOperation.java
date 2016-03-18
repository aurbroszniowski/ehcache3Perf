package io.rainfall.ehcache2.operation;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.TestException;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache2.CacheConfig;
import io.rainfall.statistics.StatisticsHolder;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

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
    List<Ehcache> caches = cacheConfig.getCaches();
    long currentTps = statisticsHolder.getCurrentTps(EhcacheResult.GET);

    if (currentTps < this.currentTpsLimit) {
      for (final Ehcache cache : caches) {
        Element value;
        Object k = keyGenerator.generate(next);

        long start = getTimeInNs();
        try {
          value = cache.get(k);
          long end = getTimeInNs();
          if (value == null) {
            statisticsHolder.record(cache.getName(), (end - start), MISS);
          } else {
            statisticsHolder.record(cache.getName(), (end - start), GET);
          }
        } catch (Exception e) {
          long end = getTimeInNs();
          statisticsHolder.record(cache.getName(), (end - start), EXCEPTION);
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
