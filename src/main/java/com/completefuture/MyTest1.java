package com.completefuture;

import java.util.concurrent.CompletableFuture;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class MyTest1 {
    private String a = "test";
    private String b = "test2";
    private String c = "test3";
    private String result;

    public void completableFutureTest() {
        CompletableFuture<String> uCompletableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println(a);
            sleep();
            return result = a + "finish ";
        }).thenApplyAsync(s -> {
            System.out.println(b);
            sleep();
            return result = s + b + "finish";
        }).thenApplyAsync(s -> {
            System.out.println(c);
            sleep();
            return result = s + c + "finish";
        });
    }

    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
