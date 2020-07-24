package com.customcompletefuture;


import com.customcompletefuture.completableFuture.CompletableFuture;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class MyTest2 {
    private String a = "test";
    private String b = "test2";
    private String c = "test2";

    public void completableFutureTest() {
        CompletableFuture<String> uCompletableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println(a);
            return a + "finish";
        });
    }

}
