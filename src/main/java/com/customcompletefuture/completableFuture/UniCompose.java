package com.customcompletefuture.completableFuture;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * @author 0003066
 * @date 2020/7/29
 */
@SuppressWarnings("serial")
final class UniCompose<T, V> extends UniCompletion<T, V> {
    Function<? super T, ? extends CompletionStage<V>> fn;

    UniCompose(Executor executor, CompletableFuture<V> dep,
               CompletableFuture<T> src,
               Function<? super T, ? extends CompletionStage<V>> fn) {
        super(executor, dep, src);
        this.fn = fn;
    }

    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<V> d;
        CompletableFuture<T> a;
        if ((d = dep) == null ||
                !d.uniCompose(a = src, fn, mode > 0 ? null : this))
            return null;
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
