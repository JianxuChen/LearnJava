package com.customcompletefuture;


import com.customcompletefuture.completableFuture.CompletableFuture;

import java.util.Scanner;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class MyTest3 {
    private String a1 = "testa1";
    private String b1 = "testb1";
    private String a2 = "testa2";
    private String b2 = "testb2";


    public void completableFutureTest() {

        System.out.println("1" + Thread.currentThread());
        CompletableFuture<String> uCompletableFuture1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("2" + Thread.currentThread());
            sleep(1000);
//            System.out.println(a1);
            return a1;
        }).thenApplyAsync(s -> {
            System.out.println("3" + Thread.currentThread());
            sleep(1000);
//            System.out.println(s + b1);
            return s + b1;
        });

        CompletableFuture<String> uCompletableFuture2 = CompletableFuture.supplyAsync(() -> {
//            System.out.println(a2);
            System.out.println("4" + Thread.currentThread());
            sleep(1000);
            return a2;
        }).thenApplyAsync(s -> {
            System.out.println("5" + Thread.currentThread());
            sleep(1000);
//            System.out.println(s + b2);
            return s + b2;
        });

        CompletableFuture<String> uCompletableFutureAll = uCompletableFuture1.thenCombineAsync(uCompletableFuture2, (s1, s2) -> {
//            System.out.println(s1 + s2 + "final");
            return s1 + s2 + "final";
        });
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
