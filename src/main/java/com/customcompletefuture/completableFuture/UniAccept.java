package com.customcompletefuture.completableFuture;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @author 0003066
 * @date 2020/7/29
 */
final class UniAccept<T> extends UniCompletion<T, Void> {
    Consumer<? super T> fn;

    UniAccept(Executor executor, CompletableFuture<Void> dep,
              CompletableFuture<T> src, Consumer<? super T> fn) {
        super(executor, dep, src);
        this.fn = fn;
    }

    final CompletableFuture<Void> tryFire(int mode) {
        CompletableFuture<Void> d;
        CompletableFuture<T> a;
        if ((d = dep) == null ||
                !d.uniAccept(a = src, fn, mode > 0 ? null : this))
            return null;
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
