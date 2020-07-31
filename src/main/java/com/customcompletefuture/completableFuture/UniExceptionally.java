package com.customcompletefuture.completableFuture;

import java.util.function.Function;

/**
 * @author 0003066
 * @date 2020/7/29
 */
@SuppressWarnings("serial")
final class UniExceptionally<T> extends UniCompletion<T, T> {
    Function<? super Throwable, ? extends T> fn;

    UniExceptionally(CompletableFuture<T> dep, CompletableFuture<T> src,
                     Function<? super Throwable, ? extends T> fn) {
        super(null, dep, src);
        this.fn = fn;
        System.out.println("init " + this);
    }

    final CompletableFuture<T> tryFire(int mode) { // never ASYNC
        // assert mode != ASYNC;
        CompletableFuture<T> d;
        CompletableFuture<T> a;
        if ((d = dep) == null || !d.uniExceptionally(a = src, fn, this))
            return null;
        dep = null;
        src = null;
        fn = null;
        return d.postFire(a, mode);
    }
}
