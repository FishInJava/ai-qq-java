package com.hobozoo.aiqq.mq.aop;

/**
 * @author hbz
 */
public interface Retry {

    // 是否可以给一个默认属性，实现方法看着不是很优雅

    /**
     * 获取重试次数
     * @return
     */
    Integer getRetryTime();

}
