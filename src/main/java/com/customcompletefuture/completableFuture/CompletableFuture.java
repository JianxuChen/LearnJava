package com.customcompletefuture.completableFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.customcompletefuture.completableFuture.AltResult.NIL;
import static com.customcompletefuture.completableFuture.AsynchronousCompletionTask.asyncPool;
import static com.customcompletefuture.completableFuture.AsynchronousCompletionTask.screenExecutor;

/**
 * @author 0003066
 * @date 2020/7/24
 */
public class CompletableFuture<T> extends UnsafeMechanics implements Future<T>, CompletionStage<T> {

    // Modes for Completion.tryFire. Signedness matters.
    static final int SYNC = 0;
    static final int ASYNC = 1;
    static final int NESTED = -1;

    /**
     * Either the result or boxed AltResult
     */
    volatile Object result;
    /**
     * Top of Treiber stack of dependent actions
     */
    volatile Completion stack;

    /**
     * Reports result using Future.get conventions.
     *
     * 若未得到结果则上报中断，若结果有异常则上报异常
     */
    private static <T> T reportGet(Object r)
            throws InterruptedException, ExecutionException {
        if (r == null) { // by convention below, null means interrupted
            throw new InterruptedException();
        }
        if (r instanceof AltResult) {
            Throwable x, cause;
            if ((x = ((AltResult) r).ex) == null) {
                return null;
            }
            if (x instanceof CancellationException) {
                throw (CancellationException) x;
            }
            if ((x instanceof CompletionException) &&
                    (cause = x.getCause()) != null) {
                x = cause;
            }
            throw new ExecutionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /**
     * Decodes outcome to return result or throw unchecked exception.
     */
    private static <T> T reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult) r).ex) == null) {
                return null;
            }
            if (x instanceof CancellationException) {
                throw (CancellationException) x;
            }
            if (x instanceof CompletionException) {
                throw (CompletionException) x;
            }
            throw new CompletionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /**
     * Pops and tries to trigger all reachable dependents.  Call only when known to be done.
     */
    final void postComplete() {
        /*
         * On each step, variable f holds current dependents to pop
         * and run.  It is extended along only one path at a time,
         * pushing others to avoid unbounded recursion.
         */
        CompletableFuture<?> f = this;
        Completion h;
        while ((h = f.stack) != null || //f一开始是this是主线，后面可能会被赋为h的结果d，即支线，若f.stack有值则执行
                (f != this && (h = (f = this).stack) != null)) {//若f.stack无值时且f是支线，则回到主线的stack执行
            CompletableFuture<?> d;
            Completion t;
            if (f.casStack(h, t = h.next)) {//把h.next赋为f.stack，h从栈中拿出去执行，h.next在下次循环中作为f.stack执行
                if (t != null) {
                    if (f != this) {//若为支线，则把h作为主线的stack执行，原主线stack作为h的next
                        pushStack(h, stack);
                        continue;
                    }
                    h.next = null;    // detach
                }
                f = (d = h.tryFire(NESTED)) == null ? this : d;//执行若产生了CompletableFuture则作为支线
            }
        }
    }

    /**
     * Traverses stack and unlinks dead Completions.
     *
     * 把栈中isLive()为false的Completion释放
     */
    final void cleanStack() {
        for (Completion p = null, q = stack; q != null; ) {
            Completion s = q.next;
            if (q.isLive()) {//若stack可执行，则下次循环查看q.next状态，并把q保存到p
                p = q;
                q = s;
            } else if (p == null) {//若stack不可执行,则释放q
                casStack(q, s);
                q = stack;
            } else {
                p.next = s;//若p不为空且stack不可执行，则释放q
                if (p.isLive()) {
                    q = s;
                } else {
                    p = null;  // restart
                    q = stack;
                }
            }
        }
    }

    /**
     * Post-processing by dependent after successful UniCompletion tryFire.  Tries to clean stack of source a, and then either runs postComplete or
     * returns this to caller, depending on mode.
     */
    final CompletableFuture<T> postFire(CompletableFuture<?> a, int mode) {
        if (a != null && a.stack != null) {
            if (mode < 0 || a.result == null) {
                a.cleanStack();
            } else {
                a.postComplete();
            }
        }
        if (result != null && stack != null) {
            if (mode < 0) {
                return this;
            } else {
                postComplete();
            }
        }
        return null;
    }

    /**
     * Post-processing after successful BiCompletion tryFire.
     */
    final CompletableFuture<T> postFire(CompletableFuture<?> a,
                                        CompletableFuture<?> b, int mode) {
        if (b != null && b.stack != null) { // clean second source
            if (mode < 0 || b.result == null)
                b.cleanStack();
            else
                b.postComplete();
        }
        return postFire(a, mode);
    }

    /* ------------- One-input Completions -------------- */

    final <S> boolean uniApply(CompletableFuture<S> a,
                               Function<? super S, ? extends T> f,
                               UniApply<S, T> c) {
        Object r;
        Throwable x;
        //a.result为null说明上一个CompletableFuture还未执行完成
        if (a == null || (r = a.result) == null || f == null) {
            return false;
        }
        tryComplete:
        //result不为null说明f已经执行完成，直接返回true
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    //若上一个CompletableFuture的执行有异常则把异常传递给当前CompletableFuture
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                // 若c为空意味着mode>0,即已在异步线程中，若c不为空意味着未启动异步线程来执行该任务。
                // 若c非异步模式c.claim()返回true,若c为异步模式则在c.claim()中启动异步线程并返回false，不关心结果
                if (c != null && !c.claim()) {
                    return false;
                }
                //以上一个CompletableFuture的执行结果作为参数输入给f执行，执行结果赋值给当前CompletableFuture
                @SuppressWarnings("unchecked") S s = (S) r;
                completeValue(f.apply(s));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> CompletableFuture<V> uniApplyStage(
            Executor e, Function<? super T, ? extends V> f) {
        if (f == null) {
            throw new NullPointerException();
        }
        CompletableFuture<V> d = new CompletableFuture<V>();
        // thenApply时e为null，直接调uniApply()进行执行；
        // thenApplyAsync时e不为null，或uniApply()无结果，则放入栈中等待异步执行。
        // this为上一个CompletableFuture，d为thenApply执行产生的CompletableFuture
        if (e != null || !d.uniApply(this, f, null)) {
            UniApply<T, V> c = new UniApply<T, V>(e, d, this, f);
            //把未完成的f放入栈中
            push(c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final <S> boolean uniAccept(CompletableFuture<S> a,
                                Consumer<? super S> f, UniAccept<S> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null) {
            return false;
        }
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                f.accept(s);
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private CompletableFuture<Void> uniAcceptStage(Executor e,
                                                   Consumer<? super T> f) {
        if (f == null) {
            throw new NullPointerException();
        }
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (e != null || !d.uniAccept(this, f, null)) {
            UniAccept<T> c = new UniAccept<T>(e, d, this, f);
            push(c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final <R, S> boolean biApply(CompletableFuture<R> a,
                                 CompletableFuture<S> b,
                                 BiFunction<? super R, ? super S, ? extends T> f,
                                 BiApply<R, S, T> c) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null) {
            return false;
        }
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult) s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                completeValue(f.apply(rr, ss));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U, V> CompletableFuture<V> biApplyStage(
            Executor e, CompletionStage<U> o,
            BiFunction<? super T, ? super U, ? extends V> f) {
        CompletableFuture<U> b;
        if (f == null || (b = toCompletableFuture(o)) == null)
            throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        if (e != null || !d.biApply(this, b, f, null)) {
            BiApply<T, U, V> c = new BiApply<T, U, V>(e, d, this, b, f);
            bipush(b, c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final <R, S extends R> boolean orApply(CompletableFuture<R> a,
                                           CompletableFuture<S> b,
                                           Function<? super R, ? extends T> f,
                                           OrApply<R, S, T> c) {
        Object r;
        Throwable x;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        tryComplete:
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                @SuppressWarnings("unchecked") R rr = (R) r;
                completeValue(f.apply(rr));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U extends T, V> CompletableFuture<V> orApplyStage(
            Executor e, CompletionStage<U> o,
            Function<? super T, ? extends V> f) {
        CompletableFuture<U> b;
        if (f == null || (b = toCompletableFuture(o)) == null) {
            throw new NullPointerException();
        }
        CompletableFuture<V> d = new CompletableFuture<V>();
        if (e != null || !d.orApply(this, b, f, null)) {
            OrApply<T, U, V> c = new OrApply<T, U, V>(e, d, this, b, f);
            orpush(b, c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final boolean uniRelay(CompletableFuture<T> a) {
        Object r;
        if (a == null || (r = a.result) == null)
            return false;
        if (result == null) // no need to claim
            completeRelay(r);
        return true;
    }

    final <S> boolean uniCompose(
            CompletableFuture<S> a,
            Function<? super S, ? extends CompletionStage<T>> f,
            UniCompose<S, T> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                CompletableFuture<T> g = toCompletableFuture(f.apply(s));
                if (g.result == null || !uniRelay(g)) {
                    UniRelay<T> copy = new UniRelay<T>(this, g);
                    g.push(copy, result, stack);
                    copy.tryFire(SYNC);
                    if (result == null)
                        return false;
                }
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> CompletableFuture<V> uniComposeStage(
            Executor e, Function<? super T, ? extends CompletionStage<V>> f) {
        if (f == null) throw new NullPointerException();
        Object r;
        Throwable x;
        if (e == null && (r = result) != null) {
            // try to return function result directly
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    return new CompletableFuture<V>(encodeThrowable(x, r));
                }
                r = null;
            }
            try {
                @SuppressWarnings("unchecked") T t = (T) r;
                CompletableFuture<V> g = toCompletableFuture(f.apply(t));
                Object s = g.result;
                if (s != null)
                    return new CompletableFuture<V>(encodeRelay(s));
                CompletableFuture<V> d = new CompletableFuture<V>();
                UniRelay<V> copy = new UniRelay<V>(d, g);
                g.push(copy, result, stack);
                copy.tryFire(SYNC);
                return d;
            } catch (Throwable ex) {
                return new CompletableFuture<V>(encodeThrowable(ex));
            }
        }
        CompletableFuture<V> d = new CompletableFuture<V>();
        UniCompose<T, V> c = new UniCompose<T, V>(e, d, this, f);
        push(c, result, stack);
        c.tryFire(SYNC);
        return d;
    }

    final boolean uniWhenComplete(CompletableFuture<T> a,
                                  BiConsumer<? super T, ? super Throwable> f,
                                  UniWhenComplete<T> c) {
        Object r;
        T t;
        Throwable x = null;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    t = null;
                } else {
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                f.accept(t, x);
                if (x == null) {
                    internalComplete(r);
                    return true;
                }
            } catch (Throwable ex) {
                if (x == null)
                    x = ex;
            }
            completeThrowable(x, r);
        }
        return true;
    }

    private CompletableFuture<T> uniWhenCompleteStage(
            Executor e, BiConsumer<? super T, ? super Throwable> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        if (e != null || !d.uniWhenComplete(this, f, null)) {
            UniWhenComplete<T> c = new UniWhenComplete<T>(e, d, this, f);
            push(c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final <S> boolean uniHandle(CompletableFuture<S> a,
                                BiFunction<? super S, Throwable, ? extends T> f,
                                UniHandle<S, T> c) {
        Object r;
        S s;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    s = null;
                } else {
                    x = null;
                    @SuppressWarnings("unchecked") S ss = (S) r;
                    s = ss;
                }
                completeValue(f.apply(s, x));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> CompletableFuture<V> uniHandleStage(
            Executor e, BiFunction<? super T, Throwable, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        if (e != null || !d.uniHandle(this, f, null)) {
            UniHandle<T, V> c = new UniHandle<T, V>(e, d, this, f);
            push(c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    final boolean uniExceptionally(CompletableFuture<T> a,
                                   Function<? super Throwable, ? extends T> f,
                                   UniExceptionally<T> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (r instanceof AltResult && (x = ((AltResult) r).ex) != null) {//若有异常则执行异常块方法
                    if (c != null && !c.claim())
                        return false;
                    completeValue(f.apply(x));
                } else
                    internalComplete(r);//若无异常则返回上个CompletableFuture的结果
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private CompletableFuture<T> uniExceptionallyStage(
            Function<Throwable, ? extends T> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        if (!d.uniExceptionally(this, f, null)) {
            UniExceptionally<T> c = new UniExceptionally<T>(d, this, f);
            push(c, result, stack);
            c.tryFire(SYNC);
        }
        return d;
    }

    boolean biRelay(CompletableFuture<?> a, CompletableFuture<?> b) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                completeThrowable(x, r);
            else if (s instanceof AltResult && (x = ((AltResult) s).ex) != null)
                completeThrowable(x, s);
            else
                completeNull();
        }
        return true;
    }

    final boolean orRelay(CompletableFuture<?> a, CompletableFuture<?> b) {
        Object r;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null))
            return false;
        if (result == null)
            completeRelay(r);
        return true;
    }

    /**
     * Recursively constructs a tree of completions.
     */
    static CompletableFuture<Void> andTree(CompletableFuture<?>[] cfs,
                                           int lo, int hi) {
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (lo > hi) // empty
            d.result = NIL;
        else {
            CompletableFuture<?> a, b;
            int mid = (lo + hi) >>> 1;

            if (lo == mid) {//lo=0,hi=1,mid=0,数组有2个元素
                a = cfs[lo];
            } else {//左半边递归
                a = andTree(cfs, lo, mid);
            }
            if (lo == hi) {//数组有1个元素
                b = a;
            } else {
                if (hi == mid + 1) {//lo=0,hi=2,mid=1,数组有3个元素,左半边递归,右半边结果为cfs[hi]
                    b = cfs[hi];
                } else {//右半边递归
                    b = andTree(cfs, mid + 1, hi);
                }
            }

            if (a == null || b == null)
                throw new NullPointerException();

            if (!d.biRelay(a, b)) {//获取ab的结果，缺一个即为false,若false则放入a和b的栈中
                BiRelay<?, ?> c = new BiRelay<>(d, a, b);
                a.bipush(b, c, a.result, a.stack);
                c.tryFire(SYNC);
            }
        }
        return d;
    }

    /**
     * Recursively constructs a tree of completions.
     */
    static CompletableFuture<Object> orTree(CompletableFuture<?>[] cfs,
                                            int lo, int hi) {
        CompletableFuture<Object> d = new CompletableFuture<Object>();
        if (lo <= hi) {
            CompletableFuture<?> a, b;
            int mid = (lo + hi) >>> 1;

            if (lo == mid) {//lo=0,hi=1,mid=0,数组有2个元素
                a = cfs[lo];
            } else {//左半边递归
                a = orTree(cfs, lo, mid);
            }
            if (lo == hi) {//数组有1个元素
                b = a;
            } else {
                if (hi == mid + 1) {//lo=0,hi=2,mid=1,数组有3个元素,左半边递归,右半边结果为cfs[hi]
                    b = cfs[hi];
                } else {//右半边递归
                    b = orTree(cfs, mid + 1, hi);
                }
            }

            if (a == null || b == null)
                throw new NullPointerException();

            if (!d.orRelay(a, b)) {
                OrRelay<?, ?> c = new OrRelay<>(d, a, b);
                a.orpush(b, c, a.result, a.stack);
                c.tryFire(SYNC);
            }
        }
        return d;
    }

    /* ------------- Zero-input Async forms -------------- */

    /**
     * 用e去异步执行f，结果存放在新建的CompletableFuture
     */
    static <U> CompletableFuture<U> asyncSupplyStage(Executor e, Supplier<U> f) {
        if (f == null) {
            throw new NullPointerException();
        }
        CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }

    /* ------------- Signallers -------------- */

    /**
     * Returns raw result after waiting, or null if interruptible and interrupted.
     */
    private Object waitingGet(boolean interruptible) {
        Signaller q = null;
        boolean queued = false;
        int spins = -1;
        Object r;
        while ((r = result) == null) {
            if (spins < 0) {
                //设置自旋
                spins = (Runtime.getRuntime().availableProcessors() > 1) ?
                        1 << 8 : 0;
            } else if (spins > 0) {
                //进行自旋
                if (nextSecondarySeed() >= 0) {
                    --spins;
                }
            } else if (q == null) {
                //自旋结束，仍无结果则创建Signaller
                q = new Signaller(interruptible, 0L, 0L);
            } else if (!queued) {
                //把Signaller放入栈中
                queued = tryPushStack(q, stack);
            } else if (interruptible && q.interruptControl < 0) {
                //已中断状态,返回
                q.thread = null;
                cleanStack();
                return null;
            } else if (q.thread != null && result == null) {
                //自旋结束仍无结果，则阻塞
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        //得到了结果，清理Signaller为异步执行申请的资源
        if (q != null) {
            q.thread = null;
            //上报中断
            if (q.interruptControl < 0) {
                //get方法和join方法上报中断的方式区别
                if (interruptible) {
                    r = null; // report interruption
                } else {
                    Thread.currentThread().interrupt();
                }
            }
        }
        postComplete();
        return r;
    }

    /**
     * Returns raw result after waiting, or null if interrupted, or throws TimeoutException on timeout.
     */
    private Object timedGet(long nanos) throws TimeoutException {
        if (Thread.interrupted()) {
            return null;
        }
        if (nanos <= 0L) {
            throw new TimeoutException();
        }
        long d = System.nanoTime() + nanos;
        Signaller q = new Signaller(true, nanos, d == 0L ? 1L : d); // avoid 0
        boolean queued = false;
        Object r;
        // We intentionally don't spin here (as waitingGet does) because
        // the call to nanoTime() above acts much like a spin.
        while ((r = result) == null) {
            if (!queued) {
                queued = tryPushStack(q, stack);
            } else if (q.interruptControl < 0 || q.nanos <= 0L) {
                //中断或超时
                q.thread = null;
                cleanStack();
                if (q.interruptControl < 0) {
                    return null;
                }
                throw new TimeoutException();
            } else if (q.thread != null && result == null) {
                //阻塞
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        if (q.interruptControl < 0) {
            //上报中断
            r = null;
        }
        q.thread = null;
        postComplete();
        return r;
    }

    /* ------------- public methods -------------- */

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Creates a new complete CompletableFuture with given encoded result.
     */
    private CompletableFuture(Object r) {
        this.result = r;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the {@link ForkJoinPool#commonPool()} with the value
     * obtained by calling the given Supplier.
     *
     * 返回一个新创建的CompletableFuture，该CompletableFuture存放了supplier在ForkJoinPool中异步执行完成后的结果
     *
     * @param supplier a function returning the value to be used to complete the returned CompletableFuture
     * @param <U>      the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(asyncPool, supplier);
    }

    /*
     * CompletionStage implemented methods
     */

    @Override
    public <U> CompletableFuture<U> thenApply(
            Function<? super T, ? extends U> fn) {
        return uniApplyStage(null, fn);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(
            Function<? super T, ? extends U> fn) {
        return uniApplyStage(asyncPool, fn);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(
            Function<? super T, ? extends U> fn, Executor executor) {
        return uniApplyStage(screenExecutor(executor), fn);
    }

    @Override
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniAcceptStage(null, action);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return null;
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return null;
    }

    @Override
    public CompletionStage<Void> thenRun(Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return null;
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(null, other, fn);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(asyncPool, other, fn);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn,
                                                      Executor executor) {
        return null;
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return null;
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return null;
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action,
                                                         Executor executor) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return null;
    }

    @Override
    public <U> CompletableFuture<U> applyToEither(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(null, other, fn);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(asyncPool, other, fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return null;
    }

    @Override
    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return null;
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return null;
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return null;
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return null;
    }

    @Override
    public <U> CompletableFuture<U> thenCompose(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(null, fn);
    }

    @Override
    public <U> CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(asyncPool, fn);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return null;
    }

    @Override
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(fn);
    }

    @Override
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(null, action);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return null;
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return null;
    }

    @Override
    public <U> CompletableFuture<U> handle(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(null, fn);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return null;
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return null;
    }

    /* ------------- Arbitrary-arity constructions -------------- */

    /**
     * Returns a new CompletableFuture that is completed when all of the given CompletableFutures complete.  If any of the given CompletableFutures
     * complete exceptionally, then the returned CompletableFuture also does so, with a CompletionException holding this exception as its cause.
     * Otherwise, the results, if any, of the given CompletableFutures are not reflected in the returned CompletableFuture, but may be obtained by
     * inspecting them individually. If no CompletableFutures are provided, returns a CompletableFuture completed with the value {@code null}.
     *
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a program, as in: {@code CompletableFuture.allOf(c1, c2, c3).join();}.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are {@code null}
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }

    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        return orTree(cfs, 0, cfs.length - 1);
    }

    @Override
    public java.util.concurrent.CompletableFuture toCompletableFuture() {
        return null;
    }

    public static CompletableFuture toCompletableFuture(CompletionStage completionStage) {
        return (CompletableFuture) completionStage;
    }

    /*
     * Future implemented methods
     */

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
                internalComplete(new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }

    @Override
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
                (((AltResult) r).ex instanceof CancellationException);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    /**
     * Waits if necessary for this future to complete, and then returns its result.
     *
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException    if this future completed exceptionally
     * @throws InterruptedException  if the current thread was interrupted while waiting
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        return reportGet((r = result) == null ? waitingGet(true) : r);
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Object r;
        long nanos = unit.toNanos(timeout);
        return reportGet((r = result) == null ? timedGet(nanos) : r);
    }

    public T join() {
        Object r;
        return reportJoin((r = result) == null ? waitingGet(false) : r);
    }

    public int getNumberOfDependents() {
        int count = 0;
        for (Completion p = stack; p != null; p = p.next)
            ++count;
        return count;
    }

}
