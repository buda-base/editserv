package io.bdrc.edit.users;

import org.apache.commons.jcs.access.exception.CacheException;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsersCache {

    public static Cache<Integer, Object> CACHE;
    public final static Logger log = LoggerFactory.getLogger(UsersCache.class);

    public static void init() {
        CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
        cacheManager.init();
        CACHE = cacheManager.createCache("users", CacheConfigurationBuilder.newCacheConfigurationBuilder(Integer.class, Object.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder().heap(500, EntryUnit.ENTRIES)));
        log.debug("Cache was initialized {}", CACHE);
    }

    public static void addToCache(Object res, int hash) {
        try {
            CACHE.put(Integer.valueOf(hash), res);
            res = null;
        } catch (CacheException e) {
            log.error("Problem putting Users Results -->" + res + " in the cache, for key -->" + hash + " Exception:" + e.getMessage());
        }
    }

    public static Object getObjectFromCache(int hash) {
        return CACHE.get(Integer.valueOf(hash));
    }

    public static boolean clearCache() {
        try {
            CACHE.clear();
            log.info("The users cache has been cleared");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
