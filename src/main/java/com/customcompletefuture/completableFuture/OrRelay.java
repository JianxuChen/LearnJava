package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/30
 */
@SuppressWarnings("serial")
final class OrRelay<T, U> extends BiCompletion<T, U, Object> { // for Or
    OrRelay(CompletableFuture<Object> dep, CompletableFuture<T> src,
            CompletableFuture<U> snd) {
        super(null, dep, src, snd);
    }

    final CompletableFuture<Object> tryFire(int mode) {
        CompletableFuture<Object> d;
        CompletableFuture<T> a;
        CompletableFuture<U> b;
        if ((d = dep) == null || !d.orRelay(a = src, b = snd))
            return null;
        src = null;
        snd = null;
        dep = null;
        return d.postFire(a, b, mode);
    }
}
