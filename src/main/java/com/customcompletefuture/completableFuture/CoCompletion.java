package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/29
 */

/**
 * A Completion delegating to a BiCompletion
 */
@SuppressWarnings("serial")
final class CoCompletion extends Completion {
    BiCompletion<?, ?, ?> base;

    CoCompletion(BiCompletion<?, ?, ?> base) {
        this.base = base;
    }

    final CompletableFuture<?> tryFire(int mode) {
        BiCompletion<?, ?, ?> c;
        CompletableFuture<?> d;
        if ((c = base) == null || (d = c.tryFire(mode)) == null)
            return null;
        base = null; // detach
        return d;
    }

    final boolean isLive() {
        BiCompletion<?, ?, ?> c;
        return (c = base) != null && c.dep != null;
    }
}
