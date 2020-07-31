package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * @author 0003066
 * @date 2020/7/29
 */
@SuppressWarnings("serial")
final class UniWhenComplete<T> extends UniCompletion<T, T> {
    BiConsumer<? super T, ? super Throwable> fn;

    UniWhenComplete(Executor executor, CompletableFuture<T> dep,
                    CompletableFuture<T> src,
                    BiConsumer<? super T, ? super Throwable> fn) {
        super(executor, dep, src);
        this.fn = fn;
    }

    final CompletableFuture<T> tryFire(int mode) {
        CompletableFuture<T> d;
        CompletableFuture<T> a;
        if ((d = dep) == null ||
                !d.uniWhenComplete(a = src, fn, mode > 0 ? null : this))
            return null;
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
