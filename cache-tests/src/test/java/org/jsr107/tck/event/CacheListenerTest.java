/**
 *  Copyright 2011 Terracotta, Inc.
 *  Copyright 2011 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jsr107.tck.event;

import org.jsr107.tck.processor.MultiArgumentHandlingEntryProcessor;
import org.jsr107.tck.processor.RemoveEntryProcessor;
import org.jsr107.tck.processor.SetEntryProcessor;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.*;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.processor.EntryProcessor;
import java.io.IOError;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.cache.event.EventType.CREATED;
import static javax.cache.event.EventType.REMOVED;
import static javax.cache.event.EventType.UPDATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Unit tests for Cache Listeners.
 * <p/>
 *
 * @author Greg Luck
 * @author Brian Oliver
 * @since 1.0
 */
public class CacheListenerTest extends CacheTestSupport<Long, String> {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass()) {

    /* (non-Javadoc)
     * @see javax.cache.util.ExcludeListExcluder#isExcluded(java.lang.String)
     */
    @Override
    protected boolean isExcluded(String methodName) {
      if ("testUnwrap".equals(methodName) && getUnwrapClass(CacheManager.class) == null) {
        return true;
      }

      return super.isExcluded(methodName);
    }
  };

  private MyCacheEntryListener<Long, String> listener;
  public MutableCacheEntryListenerConfiguration<Long,String> listenerConfiguration;


  @Before
  public void moreSetUp() {
    cache = getCacheManager().getCache(getTestCacheName(), Long.class, String.class);
  }


  @Override
  protected MutableConfiguration<Long, String> newMutableConfiguration() {
    return new MutableConfiguration<Long, String>().setTypes(Long.class, String.class);
  }

  @Override
  protected MutableConfiguration<Long, String> extraSetup(MutableConfiguration<Long, String> configuration) {
    listener = new MyCacheEntryListener<Long, String>();
    listenerConfiguration = new MutableCacheEntryListenerConfiguration<Long, String>(FactoryBuilder.factoryOf(listener), null, false, true);
    return configuration.addCacheEntryListenerConfiguration(listenerConfiguration);
  }

  /**
   * Check the listener is getting reads
   */
  @Test
  public void testCacheEntryListener() {
    assertEquals(0, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.put(1l, "Sooty");
    assertEquals(1, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    Map<Long, String> entries = new HashMap<Long, String>();
    entries.put(2l, "Lucky");
    entries.put(3l, "Prince");
    cache.putAll(entries);
    assertEquals(3, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.put(1l, "Sooty");
    assertEquals(3, listener.getCreated());
    assertEquals(1, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.putAll(entries);
    assertEquals(3, listener.getCreated());
    assertEquals(3, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.getAndPut(4l, "Cody");
    assertEquals(4, listener.getCreated());
    assertEquals(3, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.getAndPut(4l, "Cody");
    assertEquals(4, listener.getCreated());
    assertEquals(4, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    String value = cache.get(1l);
    assertEquals(4, listener.getCreated());
    assertEquals(4, listener.getUpdated());
    assertEquals(0, listener.getRemoved());


    EntryProcessor<Long, String, String> multiArgEP = new MultiArgumentHandlingEntryProcessor<>(value);
    String result = cache.invoke(1l, multiArgEP, "These", "are", "arguments", 1l);
    assertEquals(value, result);
    assertEquals(4, listener.getCreated());
    assertEquals(4, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    result = cache.invoke(1l, new SetEntryProcessor<Long, String, String>("Zoot"));
    assertEquals("Zoot", result);
    assertEquals(4, listener.getCreated());
    assertEquals(5, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    result = cache.invoke(1l, new RemoveEntryProcessor<Long, String, String>());
    assertNull(result);
    assertEquals(4, listener.getCreated());
    assertEquals(5, listener.getUpdated());
    assertEquals(1, listener.getRemoved());

    result = cache.invoke(1l, new SetEntryProcessor<Long, String, String>("Moose"));
    assertEquals("Moose", result);
    assertEquals(5, listener.getCreated());
    assertEquals(5, listener.getUpdated());
    assertEquals(1, listener.getRemoved());

    Iterator<Cache.Entry<Long, String>> iterator = cache.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
    assertEquals(5, listener.getCreated());
    assertEquals(5, listener.getUpdated());
    assertEquals(5, listener.getRemoved());
  }

  /**
   * Check the listener is only throwing CacheException
   */
  @Test
  public void testBrokenCacheEntryListener() {

    //setup
    MyBrokenCacheEntryListener<Long, String> listener = new MyBrokenCacheEntryListener<Long, String>();
    MutableCacheEntryListenerConfiguration<Long,String> listenerConfiguration
     = new MutableCacheEntryListenerConfiguration<Long, String>(FactoryBuilder.factoryOf(listener), null, false, true);
    cache.registerCacheEntryListener(listenerConfiguration);

    try {
      cache.put(1l, "Sooty");
    } catch (CacheEntryListenerException e) {
      //expected
    }

    Map<Long, String> entries = new HashMap<Long, String>();
    entries.put(2l, "Lucky");
    entries.put(3l, "Prince");

    try {
      cache.put(1l, "Sooty");
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      cache.putAll(entries);
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      cache.put(1l, "Sooty");
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      cache.putAll(entries);
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      cache.getAndPut(4l, "Cody");
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      cache.remove(4l);
    } catch (IOError e) {
      //expected. We don't wrap Error
    }

    try {
      cache.remove(4l);
    } catch (IOError e) {
      //expected. We don't wrap Error
    }

    String value = cache.get(1l);
    EntryProcessor<Long, String, String> multiArgEP = new MultiArgumentHandlingEntryProcessor<>(null);
    try {
      String result = cache.invoke(1l, multiArgEP, "These", "are", "arguments", 1l);
    } catch (CacheEntryListenerException e) {
      //expected
    }

    try {
      String result = cache.invoke(1l, new SetEntryProcessor<Long, String,
          String>("Zoot"));

      Iterator<Cache.Entry<Long, String>> iterator = cache.iterator();
      while (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    } catch (CacheEntryListenerException e) {
      //expected
    }

  }

  /**
   * Check the listener doesn't get removes from a cache.clear
   */
  @Test
  public void testCacheClearListener() {
    assertEquals(0, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.put(1l, "Sooty");
    assertEquals(1, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());

    cache.clear();

    //there should be no change in events!
    assertEquals(1, listener.getCreated());
    assertEquals(0, listener.getUpdated());
    assertEquals(0, listener.getRemoved());
  }

  private static class MyCacheEntryEventFilter implements CacheEntryEventFilter<Long, String>, Serializable {
      @Override
      public boolean evaluate(
          CacheEntryEvent<? extends Long, ? extends String> event)
          throws CacheEntryListenerException {
          return event.getValue().contains("a") ||
              event.getValue().contains("e") ||
              event.getValue().contains("i") ||
              event.getValue().contains("o") ||
              event.getValue().contains("u");
      }
  }
/**
 * Checks that the correct listeners are called the correct number of times from all of our access and mutation operations.
 *
 * @throws InterruptedException
 */
@Test
public void testFilteredListener() throws InterruptedException {
  MyCacheEntryListener<Long, String> listener = new MyCacheEntryListener<>();
  CacheEntryListenerConfiguration<Long, String> listenerConfiguration =
      new MutableCacheEntryListenerConfiguration<Long, String>(
          FactoryBuilder.factoryOf(listener),
          FactoryBuilder.factoryOf(new MyCacheEntryEventFilter()),
          true, true);
  cache.registerCacheEntryListener(listenerConfiguration);

  assertEquals(0, listener.getCreated());
  assertEquals(0, listener.getUpdated());
  assertEquals(0, listener.getRemoved());

  cache.put(1l, "Sooty");
  assertEquals(1, listener.getCreated());
  assertEquals(0, listener.getUpdated());
  assertEquals(0, listener.getRemoved());

  Map<Long, String> entries = new HashMap<Long, String>();
  entries.put(2l, "Lucky");
  entries.put(3l, "Bryn");
  cache.putAll(entries);
  assertEquals(2, listener.getCreated());
  assertEquals(0, listener.getUpdated());
  assertEquals(0, listener.getRemoved());

  cache.put(1l, "Zyn");
  assertEquals(2, listener.getCreated());
  assertEquals(0, listener.getUpdated());
  assertEquals(0, listener.getRemoved());

  cache.remove(2l);
  assertEquals(2, listener.getCreated());
  assertEquals(0, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.replace(1l, "Fred");
  assertEquals(2, listener.getCreated());
  assertEquals(1, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.replace(3l, "Bryn", "Sooty");
  assertEquals(2, listener.getCreated());
  assertEquals(2, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.get(1L);
  assertEquals(2, listener.getCreated());
  assertEquals(2, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  //containsKey is not a read for listener purposes.
  cache.containsKey(1L);
  assertEquals(2, listener.getCreated());
  assertEquals(2, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  //iterating should cause read events on non-expired entries
  for (Cache.Entry<Long, String> entry : cache) {
    String value = entry.getValue();
    System.out.println(value);
  }
  assertEquals(2, listener.getCreated());
  assertEquals(2, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.getAndPut(1l, "Pistachio");
  assertEquals(2, listener.getCreated());
  assertEquals(3, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  Set<Long> keys = new HashSet<Long>();
  keys.add(1L);
  cache.getAll(keys);
  assertEquals(2, listener.getCreated());
  assertEquals(3, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.getAndReplace(1l, "Prince");
  assertEquals(2, listener.getCreated());
  assertEquals(4, listener.getUpdated());
  assertEquals(1, listener.getRemoved());

  cache.getAndRemove(1l);
  assertEquals(2, listener.getCreated());
  assertEquals(4, listener.getUpdated());
  assertEquals(2, listener.getRemoved());

  assertEquals(2, listener.getCreated());
  assertEquals(4, listener.getUpdated());
  assertEquals(2, listener.getRemoved());
}

  @Test
  public void  testDynamicRegistration() {

    assertEquals(1, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    MyCacheEntryListener secondListener = new MyCacheEntryListener<Long, String>();
    MutableCacheEntryListenerConfiguration<Long,
        String> listenerConfiguration = new
        MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(secondListener), null, false, true);
    cache.registerCacheEntryListener(listenerConfiguration);

    assertEquals(2, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    //Can only register the same configuration once
    try {
      cache.registerCacheEntryListener(listenerConfiguration);
      fail();
    } catch (IllegalArgumentException e) {
      //expected
    }


  }

  @Test
  public void  testDeregistration() {

    assertEquals(1, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    MyCacheEntryListener secondListener = new MyCacheEntryListener<Long, String>();
    MutableCacheEntryListenerConfiguration<Long,
        String> secondListenerConfiguration = new
        MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(secondListener), null, false, true);
    cache.registerCacheEntryListener(secondListenerConfiguration);

    assertEquals(2, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    cache.deregisterCacheEntryListener(secondListenerConfiguration);

    assertEquals(1, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    //no effect if called after it has been removed
    cache.deregisterCacheEntryListener(secondListenerConfiguration);
    assertEquals(1, cache.getConfiguration().getCacheEntryListenerConfigurations().size());

    //Deregister the listener registered at configuration time
    cache.deregisterCacheEntryListener(listenerConfiguration);
    assertEquals(0, cache.getConfiguration().getCacheEntryListenerConfigurations().size());


  }


  /**
   * Test listener
   *
   * @param <K>
   * @param <V>
   */
  static class MyCacheEntryListener<K, V> implements CacheEntryCreatedListener<K, V>,
      CacheEntryUpdatedListener<K, V>, CacheEntryExpiredListener<K, V>,
      CacheEntryRemovedListener<K, V>, Serializable {

    AtomicInteger created = new AtomicInteger();
    AtomicInteger updated = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();

    ArrayList<CacheEntryEvent<K, V>> entries = new ArrayList<CacheEntryEvent<K, V>>();

    public int getCreated() {
      return created.get();
    }

    public int getUpdated() {
      return updated.get();
    }

    public int getRemoved() {
      return removed.get();
    }

    public ArrayList<CacheEntryEvent<K, V>> getEntries() {
      return entries;
    }

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(CREATED, event.getEventType());
        created.incrementAndGet();
      }
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      //SKIP: we don't count expiry events as they can occur asynchronously
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(REMOVED, event.getEventType());
        removed.incrementAndGet();
      }
    }

    @Override
    public void onUpdated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(UPDATED, event.getEventType());
        updated.incrementAndGet();
      }
    }
  }

  /**
   * Test listener which throws all sorts of Throwables.
   *
   * @param <K>
   * @param <V>
   */
  static class MyBrokenCacheEntryListener<K, V> implements CacheEntryCreatedListener<K,
      V>,
      CacheEntryUpdatedListener<K, V>, CacheEntryExpiredListener<K, V>,
      CacheEntryRemovedListener<K, V>, Serializable {

    AtomicInteger created = new AtomicInteger();
    AtomicInteger updated = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();

    ArrayList<CacheEntryEvent<K, V>> entries = new ArrayList<CacheEntryEvent<K, V>>();

    public int getCreated() {
      return created.get();
    }

    public int getUpdated() {
      return updated.get();
    }

    public int getRemoved() {
      return removed.get();
    }

    public ArrayList<CacheEntryEvent<K, V>> getEntries() {
      return entries;
    }

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(CREATED, event.getEventType());
        throw new CacheEntryListenerException("I broke");
      }
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      //SKIP: we don't count expiry events as they can occur asynchronously
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(REMOVED, event.getEventType());
        throw new IOError(null);
      }
    }

    @Override
    public void onUpdated(Iterable<CacheEntryEvent<? extends K, ? extends V>> events) throws CacheEntryListenerException {
      for (CacheEntryEvent<? extends K, ? extends V> event : events) {
        assertEquals(UPDATED, event.getEventType());
        throw new UnsupportedOperationException();
      }
    }
  }

}
