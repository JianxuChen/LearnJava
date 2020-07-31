package com.customcompletefuture;

/**
 * @author 0003066
 * @date 2020/7/20
 */
public class CompleteFutureDemo2 {
    public static void main(String[] args) {
//        MyTest2 myTest2 = new MyTest2();
//        myTest2.completableFutureTest();
//        MyTest3 myTest3 = new MyTest3();
//        myTest3.completableFutureTest();
        MyTest4 myTest4 = new MyTest4();
        myTest4.completableFutureTest();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
