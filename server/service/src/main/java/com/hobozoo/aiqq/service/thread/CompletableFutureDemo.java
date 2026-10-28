package com.hobozoo.aiqq.service.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author hbz
 */
@Slf4j
public class CompletableFutureDemo {

    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(10, 10, 0, TimeUnit.MINUTES, queue);

    /**
     * 核心数
      */
    private int coreNum = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws Exception{

        CompletableFutureDemo completableFutureDemo = new CompletableFutureDemo();
        completableFutureDemo.m1();
    }

    private class ZuHe{
        private String string;
        private Boolean bol;
        private Integer integer;

        public String getString() {
            return string;
        }

        public void setString(String string) {
            this.string = string;
        }

        public Boolean getBol() {
            return bol;
        }

        public void setBol(Boolean bol) {
            this.bol = bol;
        }

        public Integer getInteger() {
            return integer;
        }

        public void setInteger(Integer integer) {
            this.integer = integer;
        }
    }

    public void m1()throws Exception{

        Random random = new Random();

        CompletableFuture<Void> result1 = CompletableFuture.supplyAsync(() -> {
            if (random.nextInt(20)%2 == 0){//模拟异常发生
                throw new RuntimeException("嘿嘿");
            }
            return 1;
        }, pool).exceptionally(throwable -> {
            log.error("出现异常", throwable);
            return null;//如果异常返回一个特殊值，可以看业务决定
        }).thenAccept(integer -> {
            // 处理返回结果
            System.out.println(integer);
        });


        CompletableFuture<Void> result2 = CompletableFuture.supplyAsync(() -> {
            if (random.nextInt(20) % 2 == 0) {//模拟异常发生
                throw new RuntimeException("嘿嘿");
            }
            return 2;
        }, pool).exceptionally(throwable -> {
            log.error("出现异常", throwable);
            return null;//如果异常返回一个特殊值，可以看业务决定
        }).thenAccept(integer -> {
            // 处理返回结果
            System.out.println(integer);
        });

        //注意，此时产生了两个CompletableFuture，所以要等两个都完成后。当前线程才继续。
        CompletableFuture.allOf(result1,result2).join();


        CompletableFuture<String> bResult = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(7);
                log.info("调用B系统的当前线程：{}",Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "B系统返回";
        }, pool);

        CompletableFuture<Boolean> cResult = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                log.info("调用C系统的当前线程：{}",Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }, pool);

        // 写法1：
        ZuHe zuHe = new ZuHe();
       // zuHe.setInteger(result.get());
        zuHe.setString(bResult.get());
        zuHe.setBol(cResult.get());

        // 写法2：
    //    CompletableFuture<Void> abcResult = CompletableFuture.allOf(aResult, bResult, cResult);
//        CompletableFuture<ZuHe> zuheResult = abcResult.thenApply(aVoid -> {
//            // todo 如何拼接？
//
//            return zuHe;
//        });



//        LinkedList<CompletableFuture<String>> list = new LinkedList<>();
//        list.add(aResult);
//        list.add(bResult);
//        list.add(cResult);
//        CompletableFuture<List<String>> sequence = sequence(list);

        /**
         * todo
         * 不对，这里你用get的话肯定是要阻塞的
         * 最好的方法是 设置一个回调函数，该回调中：
         * 1.处理，组合结果
         * 2.返回
         */
//        List<String> strings = sequence.get();
//        System.out.println(sequence);

        /**
         * 肯定不能这么用，因为我们的这个场景中返回的东西是不一样的
         */
    }

    /**
     * 看了下，这种才是正确用法，返回的结果都是相同的
     * 所以放在一个List <T>中
     * 我的需求是异步的返回结果是不同的对象，然后组装成一个最终的返回对象
     * @param futures
     * @param <T>
     * @return
     */
    public static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return allDoneFuture.thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }

    /**
     * 原始写法
     * @param productId
     * @return
     */
    public Future<Integer> getPriceAsync_original(String productId) {

        CompletableFuture<Integer> futurePrice = new CompletableFuture<>();
        pool.submit(() -> {
            // hashCode模拟产品价格
            Integer price = 0;
            try {
                price = productId.hashCode()%2;
                futurePrice.complete(price);
            } catch (Exception e) {
                // 处理异常
                futurePrice.completeExceptionally(e);
            }
        });
        return futurePrice;
    }

    /**
     * 使用提供的 静态工厂写法
     * 这个写法同上面的写法一毛一样
     * @param productId
     * @return
     */
    public Future<Integer> getPriceAsync_factory(String productId) {
        return CompletableFuture.supplyAsync(() -> productId.hashCode()%2,pool);
    }


    /**
     * 如果此时多家店铺，需要查询多次
     * 流+CompletableFuture 的方式
     *
     * 还有一种并行流的方式：缺点很明显，不能对线程池进行优化配置
     * @param productId
     * @return
     */
    public List<Integer> getPriceAsync_stream(String productId) {

        List<String> shops = Arrays.asList("shop1", "shop2", "shop3");

        // 第一个流
        List<CompletableFuture<Integer>> collect = shops.stream().map(shop -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    TimeUnit.SECONDS.sleep(Long.getLong(shop.charAt(5)+""));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return Integer.parseInt(shop.charAt(5)+""+productId.charAt(0));
            }, pool);
        }).collect(Collectors.toList());

        // 第二个流，join方法和Futrue的get相同
        List<Integer> result = collect.stream().map(CompletableFuture::join).collect(Collectors.toList());

        // todo 这和我的使用场景还不太一样，我面对的场景是并行访问不同的接口，即入参不同，返回不同，所以就不能构建一个List。就没办法使用StreamAPI

        return result;
    }


}
