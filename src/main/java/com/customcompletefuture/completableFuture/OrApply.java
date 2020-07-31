package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * @author 0003066
 * @date 2020/7/29
 */
 final class OrApply<T,U extends T,V> extends BiCompletion<T,U,V> {
    Function<? super T,? extends V> fn;
    OrApply(Executor executor, CompletableFuture<V> dep,
            CompletableFuture<T> src,
            CompletableFuture<U> snd,
            Function<? super T,? extends V> fn) {
        super(executor, dep, src, snd); this.fn = fn;
    }
    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<V> d;
        CompletableFuture<T> a;
        CompletableFuture<U> b;
        if ((d = dep) == null ||
                !d.orApply(a = src, b = snd, fn, mode > 0 ? null : this))
            return null;
        dep = null; src = null; snd = null; fn = null;
        return d.postFire(a, b, mode);
    }
}
