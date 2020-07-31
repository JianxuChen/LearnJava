package com.customcompletefuture.completableFuture;

import java.util.concurrent.ForkJoinTask;
import java.util.function.Supplier;

/**
 * @author 0003066
 * @date 2020/7/24
 */
@SuppressWarnings("serial")
final class AsyncSupply<T> extends ForkJoinTask<Void>
        implements Runnable, AsynchronousCompletionTask {
    CompletableFuture<T> dep;
    Supplier<T> fn;

    AsyncSupply(CompletableFuture<T> dep, Supplier<T> fn) {
        this.dep = dep;
        this.fn = fn;
    }

    @Override
    public final Void getRawResult() {
        return null;
    }

    @Override
    public final void setRawResult(Void v) {
    }

    @Override
    public final boolean exec() {
        run();
        return true;
    }

    @Override
    public void run() {
        CompletableFuture<T> d;
        Supplier<T> f;
        d = dep;
        f = fn;
        if (d != null && f != null) {
            dep = null;
            fn = null;
            if (d.result == null) {
                try {
                    //把f执行的结果赋给d的result
                    d.completeValue(f.get());
                } catch (Throwable ex) {
                    //把f执行过程产生的异常赋给d的result
                    d.completeThrowable(ex);
                    System.out.println(d.stack);
                }
            }
            //完成后处理
            d.postComplete();
        }
    }
}
