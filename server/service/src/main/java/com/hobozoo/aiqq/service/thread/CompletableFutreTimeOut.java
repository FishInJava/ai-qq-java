package com.hobozoo.aiqq.service.thread;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * @author hbz
 */
@Slf4j
public class CompletableFutreTimeOut {

    /**
     * 版本1：
     * 用的是CompletableFuture，但是写法是原始的Futrue写法
     * 异常处理很麻烦，如果需要step1，step2，step3每个步骤的异常分开处理，
     * 就需要写3个try-catch
     */
    class Version1{


        public <T,R> void getResult1(){
        }
        public <T,R> void getResult2(Function<T,R> function){
        }
        public <T,R> T getResult3(Function<T,R> function){
            return null;
        }
        public <T,R> R getResult4(Function<T,R> function){
            return null;
        }
        public <T,R> List<T> getResult5(Function<T,R> function){
            return null;
        }


        /**
         * 对外暴漏的接口
         */
        public void serve() {
            try {
                //step 1：异步操作
                CompletableFuture<Response> responseFuture = asyncCode();
                //step 2：获取异步操作结果（阻塞）
                Response response = responseFuture.get(1, TimeUnit.SECONDS);
                //step 3：对结果处理
                send(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 版本2：
     * 链式编程处理异常
     * 链式编程处理超时
     */
    class Version2{
        /**
         * 对外暴漏的接口
         */
        public void serve() {
            //step 1：异步操作
            CompletableFuture<Response> responseFuture = asyncCode();
            //step 2：处理step1 产生的异常
            responseFuture.exceptionally(throwable -> {
                log.error("发现异常",throwable);
                //返回一个默认值，防止后续逻辑错误
                return new Response();
            });
            // 超时设置
            responseFuture.orTimeout(5,TimeUnit.SECONDS);
            //step 3:对结果的处理
            responseFuture.thenAccept(CompletableFutreTimeOut.this::send);
            //step 4：处理step2 产生的异常，不知道这么写是否OK
            CompletableFuture<Response> exceptionally = responseFuture.exceptionally(throwable -> {
                log.error("发现异常", throwable);
                // doSomeRecord() 做一些记录逻辑，或者回退逻辑
                return new Response();
            });

        }
    }

    /**
     * 版本3：
     * J8及以下版本（没有orTimeout这个API）
     * 超时使用ScheduledExecutorService来控制
     */
    class Version3{

    }

    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(10, 10, 0, TimeUnit.MINUTES, queue);

    /**
     * 如何设置超时
     * 1.J9中有专门的API:public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit)
     * 2.J8中可以设置一个线程池，
     */
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    public static <T> CompletableFuture<T> failAfter(Duration duration) {
        final CompletableFuture<T> promise = new CompletableFuture<>();
        scheduler.schedule(() -> {
            final TimeoutException ex = new TimeoutException("Timeout after " + duration);
            return promise.completeExceptionally(ex);
        }, duration.toMillis(), TimeUnit.SECONDS);
        return promise;
    }

    /**
     * 响应
     */
    class Response{ }
    class Request{
        Random random = new Random();
        Response doRequest(){
            if (random.nextInt(20)%2 == 0){
                throw new RuntimeException("嘿嘿");
            }
            return new Response();
        }
    }

    /**
     * 一个异步操作
     * @return
     */
    CompletableFuture<Response> asyncCode(){
        return CompletableFuture.supplyAsync(() -> new Request().doRequest(),pool);
    }

    /**
     * 对结果的处理
     * @param response
     */
    private void send(Response response) {
        //...
    }




}
