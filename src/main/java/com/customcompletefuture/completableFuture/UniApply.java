package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * @author 0003066
 * @date 2020/7/27
 */
final class UniApply<T, V> extends UniCompletion<T, V> {
    Function<? super T, ? extends V> fn;

    UniApply(Executor executor, CompletableFuture<V> dep,
             CompletableFuture<T> src,
             Function<? super T, ? extends V> fn) {
        super(executor, dep, src);
        this.fn = fn;
    }

    @Override
    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<V> d;
        CompletableFuture<T> a;
        if ((d = dep) == null ||
                !d.uniApply(a = src, fn, mode > 0 ? null : this)) {
            return null;
        }
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
