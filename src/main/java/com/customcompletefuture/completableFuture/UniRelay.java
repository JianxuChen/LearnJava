package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/29
 */
@SuppressWarnings("serial")
final class UniRelay<T> extends UniCompletion<T,T> { // for Compose
    UniRelay(CompletableFuture<T> dep, CompletableFuture<T> src) {
        super(null, dep, src);
    }
    final CompletableFuture<T> tryFire(int mode) {
        CompletableFuture<T> d; CompletableFuture<T> a;
        if ((d = dep) == null || !d.uniRelay(a = src))
            return null;
        src = null; dep = null;
        return d.postFire(a, mode);
    }
}
