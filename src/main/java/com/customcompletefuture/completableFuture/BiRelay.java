package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/30
 */
@SuppressWarnings("serial")
final class BiRelay<T, U> extends BiCompletion<T, U, Void> { // for And
    BiRelay(CompletableFuture<Void> dep,
            CompletableFuture<T> src,
            CompletableFuture<U> snd) {
        super(null, dep, src, snd);
    }

    final CompletableFuture<Void> tryFire(int mode) {
        CompletableFuture<Void> d;
        CompletableFuture<T> a;
        CompletableFuture<U> b;
        if ((d = dep) == null || !d.biRelay(a = src, b = snd))
            return null;
        src = null;
        snd = null;
        dep = null;
        return d.postFire(a, b, mode);
    }
}
