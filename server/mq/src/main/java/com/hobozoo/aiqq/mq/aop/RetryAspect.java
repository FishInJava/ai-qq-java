package com.hobozoo.aiqq.mq.aop;


import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 随机切面，从缓存中获取数据，如果是单数
 * @author hbz
 */
@Aspect
@Slf4j
@Component
@Order(100)
public class RetryAspect {

    /**
     * initialCapacity初始容量
     * maximumSize最大容量
     * expireAfterAccess：创建后5min没有获取后，key-value失效，还有一种策略是创建后多少时间后直接失效
     * CacheLoader：如果获取不到，通过load方法初始化
     */
    private static final LoadingCache<String, AtomicInteger> DATA_MAP = CacheBuilder.newBuilder()
            .initialCapacity(50)
            .maximumSize(1000)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .softValues().build(new CacheLoader<String, AtomicInteger>() {
                @Override
                public AtomicInteger load(String key) throws Exception {
                    return new AtomicInteger(0);
                }
            });

    @Pointcut("")
    public void pointCut(){}

    @Around("pointCut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 获取类的所有接口
        Class<?>[] interfaces = pjp.getTarget().getClass().getInterfaces();
        // 判断是否是Retry类
        if (Arrays.asList(interfaces).contains(Retry.class)){
            // 生成一个唯一key扔到内存中（要求有过期时间）
            Object[] args = pjp.getArgs();
            //手动在编译时开启-parameters
            Retry target = (Retry)pjp.getTarget();
            Integer retryTime = target.getRetryTime();
            String key = "用args生成的key";
            int time = DATA_MAP.get(key).getAndAdd(1);
            if (time <= retryTime){
             log.info("第 {} 次重试",time);
             return pjp.proceed();
            }
            log.error("重试次数达到上限，入参{}",args);
        }else {
            return pjp.proceed();
        }

        return null;
    }

}
