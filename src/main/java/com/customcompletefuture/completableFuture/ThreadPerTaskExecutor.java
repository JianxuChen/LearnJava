package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;

/**
 * @author 0003066
 * @date 2020/7/24
 */
 final class ThreadPerTaskExecutor implements Executor {
    @Override
    public void execute(Runnable r) { new Thread(r).start(); }
}
