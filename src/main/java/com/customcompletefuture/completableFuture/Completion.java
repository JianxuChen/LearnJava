package com.customcompletefuture.completableFuture;
import java.util.concurrent.ForkJoinTask;

/**
 * @author 0003066
 * @date 2020/7/24
 */
@SuppressWarnings("serial")
abstract class Completion extends ForkJoinTask<Void>
        implements Runnable, AsynchronousCompletionTask {

    // Modes for Completion.tryFire. Signedness matters.

    static final int SYNC = 0;
    static final int ASYNC = 1;
    static final int NESTED = -1;

    // Treiber stack link
    volatile Completion next;

    /**
     * Performs completion action if triggered, returning a dependent that may need propagation, if one exists.
     *
     * @param mode SYNC, ASYNC, or NESTED
     */
    abstract CompletableFuture<?> tryFire(int mode);

    /**
     * Returns true if possibly still triggerable. Used by cleanStack.
     */
    abstract boolean isLive();

    @Override
    public final void run() {
        tryFire(ASYNC);
    }

    @Override
    public final boolean exec() {
        tryFire(ASYNC);
        return true;
    }

    @Override
    public final Void getRawResult() {
        return null;
    }

    @Override
    public final void setRawResult(Void v) {
    }
}
