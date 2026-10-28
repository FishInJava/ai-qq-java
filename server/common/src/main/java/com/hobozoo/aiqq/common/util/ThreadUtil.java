package com.hobozoo.aiqq.common.util;

import java.time.LocalDateTime;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author hbz
 */
public class ThreadUtil {

    /**
     * 不显示给定容量的话，就是Integer大小
     */
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(10, 10, 0, TimeUnit.MINUTES, queue);

    public static void main(String[] args) throws Exception{
        ThreadUtil threadUtil = new ThreadUtil();
        threadUtil.getBlock();
    }

    /**
     * CompletableFuture的使用
     * @throws Exception
     */
    public void j8CompletableFuture() throws Exception{

    }

//    /**
//     * guava提供的回调
//     * @throws Exception
//     */
//    public void guavaListenableFuture() throws Exception{
//
//        ListeningExecutorService service = MoreExecutors.listeningDecorator(pool);
//
//        ListenableFuture<String> future = service.submit(() -> {
//            return "A";
//        });
//
//        Futures.addCallback(future, new FutureCallback<String>() {
//
//            @Override
//            public void onSuccess(String result) {
//
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//
//            }
//        });
//
//    }

    /**
     * 线程池 + Futrue 的使用
     * @throws Exception
     */
    public void getBlock() throws Exception{

        Future<String> submit2 = pool.submit(() -> {
            // http请求数据
            TimeUnit.SECONDS.sleep(5);
            //countDownLatch.countDown();
            return "A";
        });

        Future<String> submit1 = pool.submit(() -> {

            // http请求数据
            TimeUnit.SECONDS.sleep(1);
            //countDownLatch.countDown();
            return "B";
        });

        Future<String> submit = pool.submit(() -> {

            // http请求数据
            TimeUnit.SECONDS.sleep(2);
            //countDownLatch.countDown();
            return "C";
        });

        LocalDateTime now = LocalDateTime.now();
        System.out.println(now);
        String s = submit2.get(1,TimeUnit.SECONDS);
        String s1 = submit1.get();
        String s3 = submit.get();
        System.out.println(s+s1+s3);
        LocalDateTime now1 = LocalDateTime.now();
        System.out.println(now1);

        //countDownLatch.await();

        // 组合数据并返回

    }



}
