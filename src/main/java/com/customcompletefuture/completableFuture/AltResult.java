package com.customcompletefuture.completableFuture;

import java.util.concurrent.CompletionException;

/**
 * @author 0003066
 * @date 2020/7/24
 */
final class AltResult {
    // null only for NIL
    final Throwable ex;

    AltResult(Throwable x) {
        this.ex = x;
    }

    /**
     * The encoding of the null value.
     */
    static final AltResult NIL = new AltResult(null);

    static AltResult encodeThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x :
                new CompletionException(x));
    }
}
