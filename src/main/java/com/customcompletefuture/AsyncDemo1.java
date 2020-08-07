package com.customcompletefuture;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class AsyncDemo1 {
    private String a1 = "testa1";
    private String b1 = "testb1";
    private String a2 = "testa2";
    private String b2 = "testb2";


    void completableFutureTest() {
        CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
            String supplyAsyncResult = "Hello world!";
            System.out.println(supplyAsyncResult);
            return supplyAsyncResult;
        }).thenApply(r -> {
            String thenApplyResult = r + " thenApply!";
            System.out.println(thenApplyResult);
            return thenApplyResult;
        });

        try {
            System.out.println(completableFuture.get() + " finish!");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void sleep(int time) {
        try {

            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
