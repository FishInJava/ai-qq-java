package com.hobozoo.aiqq.common.util;


import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hbz
 */
public class CacheUtil {

    /**
     * 自动过期
     * LoadingCache这个是自动过期的缓存
     * 线程安全
     * AtomicInteger
     */
    public static void m1(){
        LoadingCache<String, AtomicInteger> map = CacheBuilder.newBuilder()
                .initialCapacity(50)
                .maximumSize(10000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .softValues().build(new CacheLoader<String, AtomicInteger>() {

                    @Override
                    public AtomicInteger load(String s) throws Exception {
                        return new AtomicInteger(0);
                    }
                });
    }

}
