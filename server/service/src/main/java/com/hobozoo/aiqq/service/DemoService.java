package com.hobozoo.aiqq.service;

import java.util.concurrent.FutureTask;

/**
 * @author hbz
 */
public class DemoService {

    public static void main(String[] args) {
        /**do sth**/
        new Thread(new FutureTask<Integer>(() -> {
            return 1;
        })).start();

    }



}
