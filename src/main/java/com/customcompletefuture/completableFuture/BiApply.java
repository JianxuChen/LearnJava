package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * @author 0003066
 * @date 2020/7/29
 */
final class BiApply<T, U, V> extends BiCompletion<T, U, V> {
    BiFunction<? super T, ? super U, ? extends V> fn;

    BiApply(Executor executor, CompletableFuture<V> dep,
            CompletableFuture<T> src, CompletableFuture<U> snd,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        super(executor, dep, src, snd);
        this.fn = fn;
    }

    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<V> d;
        CompletableFuture<T> a;
        CompletableFuture<U> b;
        if ((d = dep) == null ||
                !d.biApply(a = src, b = snd, fn, mode > 0 ? null : this)) {
            return null;
        }
        dep = null;
        src = null;
        snd = null;
        fn = null;
        return d.postFire(a, b, mode);
    }
}
