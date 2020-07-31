package com.customcompletefuture;


import com.customcompletefuture.completableFuture.CompletableFuture;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class MyTest2 {
    private String a = "test";
    private String b = "test2";
    private String c = "test3";

    int sign;
    Thread threadSleep;
    Thread threadMain;
    Scanner scan = new Scanner(System.in);

    public void completableFutureTest() {
        threadMain = Thread.currentThread();
        System.out.println(threadMain);
        Thread threadControl = new Thread(() -> {
            while (true) {
                sign = scan.nextInt();
                if (sign == 1) {
                    threadSleep.interrupt();
                } else if (sign == 2) {
                    threadMain.interrupt();
                } else if (sign == 0) {
                    break;
                }

            }
        });
        threadControl.start();
        System.out.println(threadControl);

        CompletableFuture<String> uCompletableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println(a);
            return a;
        }).thenApplyAsync(s -> {
            System.out.println(s + b);
            return s + b;
        }).thenApplyAsync(s -> {
            threadSleep = Thread.currentThread();
            System.out.println(threadSleep);
            System.out.println(s + c);
//            sleep(Integer.MAX_VALUE);
            sleep(10000);
            return s + c;
        });
        System.out.println(uCompletableFuture.join());
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
