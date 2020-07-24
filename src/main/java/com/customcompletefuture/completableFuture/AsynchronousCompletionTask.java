package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * @author 0003066
 * @date 2020/7/24
 */
public interface AsynchronousCompletionTask {
    boolean useCommonPool = (ForkJoinPool.getCommonPoolParallelism() > 1);

    Executor asyncPool = useCommonPool ? ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    static Executor screenExecutor(Executor e) {
        if (!useCommonPool && e == ForkJoinPool.commonPool()) {
            return asyncPool;
        }
        if (e == null) {
            throw new NullPointerException();
        }
        return e;
    }
}
