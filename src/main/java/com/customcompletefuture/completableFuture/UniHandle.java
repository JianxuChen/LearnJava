package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * @author 0003066
 * @date 2020/7/29
 */
@SuppressWarnings("serial")
final class UniHandle<T, V> extends UniCompletion<T, V> {
    BiFunction<? super T, Throwable, ? extends V> fn;

    UniHandle(Executor executor, CompletableFuture<V> dep,
              CompletableFuture<T> src,
              BiFunction<? super T, Throwable, ? extends V> fn) {
        super(executor, dep, src);
        this.fn = fn;
    }

    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<V> d;
        CompletableFuture<T> a;
        if ((d = dep) == null ||
                !d.uniHandle(a = src, fn, mode > 0 ? null : this))
            return null;
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
