package com.hobozoo.aiqq.mq.consumer;

/**
 * 消费者
 * @author hbz
 */
public interface Consumer {

    /**
     * 消费
     * @param topic 主题
     * @param tag 标签
     * @param key 唯一key
     * @param message 消息内容
     */
    void onMessage(String topic,String tag,String key,String message);

}
