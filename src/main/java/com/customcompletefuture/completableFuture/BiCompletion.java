package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/29
 */

import java.util.concurrent.Executor;

/**
 * A Completion for an action with two sources
 */
@SuppressWarnings("serial")
abstract class BiCompletion<T, U, V> extends UniCompletion<T, V> {
    CompletableFuture<U> snd; // second source for action

    BiCompletion(Executor executor, CompletableFuture<V> dep,
                 CompletableFuture<T> src, CompletableFuture<U> snd) {
        super(executor, dep, src);
        this.snd = snd;
    }
}
