package com.customcompletefuture.completableFuture;

/**
 * @author 0003066
 * @date 2020/7/27
 */

import java.util.concurrent.Executor;

/**
 * A Completion with a source, dependent, and executor.
 */
@SuppressWarnings("serial")
abstract class UniCompletion<T, V> extends Completion {
    // executor to use (null if none)
    Executor executor;
    // the dependent to complete
    CompletableFuture<V> dep;
    // source for action
    CompletableFuture<T> src;

    UniCompletion(Executor executor, CompletableFuture<V> dep,
                  CompletableFuture<T> src) {
        this.executor = executor;
        this.dep = dep;
        this.src = src;
    }

    /**
     * Returns true if action can be run. Call only when known to be triggerable. Uses FJ tag bit to ensure that only one thread claims ownership.  If
     * async, starts as task -- a later call to tryFire will run action.
     */
    final boolean claim() {
        Executor e = executor;
        if (compareAndSetForkJoinTaskTag((short) 0, (short) 1)) {
            if (e == null) {
                return true;
            }
            executor = null; // disable
            e.execute(this);
        }
        return false;
    }

    @Override
    final boolean isLive() {
        return dep != null;
    }
}
