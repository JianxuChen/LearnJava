package com.customcompletefuture;


import com.customcompletefuture.completableFuture.CompletableFuture;

import java.util.Arrays;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class MyTest4 {
    private String a1 = "testa1";
    private String b1 = "testb1";
    private String a2 = "testa2";
    private String b2 = "testb2";


    public void completableFutureTest() {

        CompletableFuture<String> uCompletableFuture1 = CompletableFuture.supplyAsync(() -> {
            System.out.println(Thread.currentThread() + " uCompletableFuture1");
            System.out.println(a1);
            sleep(1000);
//            throw new RuntimeException();
            return a1;
//        }).exceptionally(s -> {
//            System.out.println(Thread.currentThread() + " exceptionally");

//            System.out.println(s);
//            return s + b1;
        });

        CompletableFuture<String> uCompletableFuture2 = CompletableFuture.supplyAsync(() -> {
            System.out.println(a2);
            sleep(2000);
            return a2;
        });

        CompletableFuture<String> uCompletableFuture3 = uCompletableFuture1.applyToEither(uCompletableFuture2, s -> s);
//        CompletableFuture.allOf(uCompletableFuture1, uCompletableFuture2);
        System.out.println(uCompletableFuture3.getNumberOfDependents());


        System.out.println(Thread.currentThread() + " main");
        System.out.println(uCompletableFuture1.join());
        System.out.println("finish");
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
