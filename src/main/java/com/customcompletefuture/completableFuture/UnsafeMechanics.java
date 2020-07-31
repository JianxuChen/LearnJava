package com.customcompletefuture.completableFuture;

import sun.misc.Unsafe;
import sun.misc.VM;

import java.lang.reflect.Field;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.customcompletefuture.completableFuture.AltResult.NIL;

/**
 * @author 0003066
 * @date 2020/7/24
 */
public abstract class UnsafeMechanics<T> {
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long STACK;
    private static final long NEXT;

    static {
        try {
            final sun.misc.Unsafe u;
            UNSAFE = u = reflectGetUnsafe();
            Class<?> k = CompletableFuture.class;
            RESULT = u.objectFieldOffset(k.getDeclaredField("result"));
            STACK = u.objectFieldOffset(k.getDeclaredField("stack"));
            NEXT = u.objectFieldOffset
                    (Completion.class.getDeclaredField("next"));
        } catch (Exception x) {
            throw new Error(x);
        }
    }

    final boolean internalComplete(Object r) { // CAS from null to r
        return UNSAFE.compareAndSwapObject(this, RESULT, null, r);
    }

    final boolean casStack(Completion cmp, Completion val) {
        return UNSAFE.compareAndSwapObject(this, STACK, cmp, val);
    }

    /**
     * Returns true if successfully pushed c onto stack.
     */
    final boolean tryPushStack(Completion c, Completion stack) {
        lazySetNext(c, stack);
        return UNSAFE.compareAndSwapObject(this, STACK, stack, c);
    }

    /**
     * Unconditionally pushes c onto stack, retrying if necessary.
     */
    final void pushStack(Completion c, Completion stack) {
        do {
        } while (!tryPushStack(c, stack));
    }

    /**
     * Pushes the given completion (if it exists) unless done.
     */
    final void push(UniCompletion<?, ?> c, Object result, Completion stack) {
        if (c != null) {
            while (result == null && !tryPushStack(c, stack)) {
                // clear on failure，即回滚tryPushStack()中的lazySetNext()
                lazySetNext(c, null);
            }
        }
    }

    /**
     * Pushes completion to this and b unless both done.
     */
    final void bipush(CompletableFuture<?> b, BiCompletion<?, ?, ?> c, Object result, Completion stack) {
        if (c != null) {
            Object r;
            while ((r = result) == null && !tryPushStack(c, stack))
                lazySetNext(c, null); // clear on failure
            if (b != null && b != this && b.result == null) {
                Completion q = (r != null) ? c : new CoCompletion(c);
                while (b.result == null && !b.tryPushStack(q, b.stack))
                    lazySetNext(q, null); // clear on failure
            }
        }
    }

    /**
     * Pushes completion to this and b unless either done.
     */
    final void orpush(CompletableFuture<?> b, BiCompletion<?, ?, ?> c, Object result, Completion stack) {
        if (c != null) {
            while ((b == null || b.result == null) && result == null) {
                if (tryPushStack(c, stack)) {
                    if (b != null && b != this && b.result == null) {
                        Completion q = new CoCompletion(c);
                        while (result == null && b.result == null &&
                                !b.tryPushStack(q, b.stack))
                            lazySetNext(q, null); // clear on failure
                    }
                    break;
                }
                lazySetNext(c, null); // clear on failure
            }
        }
    }

    static void lazySetNext(Completion c, Completion next) {
        UNSAFE.putOrderedObject(c, NEXT, next);
    }

    /* ------------- Encoding and decoding outcomes -------------- */

    /**
     * Completes with the null value, unless already completed.
     */
    final boolean completeNull() {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                NIL);
    }

    /**
     * Completes with a non-exceptional result, unless already completed.
     */
    final boolean completeValue(T t) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null, (t == null) ? NIL : t);
    }

    /**
     * Returns the encoding of the given (non-null) exception as a wrapped CompletionException unless it is one already.
     */
    static AltResult encodeThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x :
                new CompletionException(x));
    }

    /**
     * Completes with an exceptional result, unless already completed.
     */
    final boolean completeThrowable(Throwable x) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null, encodeThrowable(x));
    }

    /**
     * Returns the encoding of the given (non-null) exception as a wrapped CompletionException unless it is one already.  May return the given Object
     * r (which must have been the result of a source future) if it is equivalent, i.e. if this is a simple relay of an existing CompletionException.
     */
    static Object encodeThrowable(Throwable x, Object r) {
        if (!(x instanceof CompletionException)) {
            x = new CompletionException(x);
        } else if (r instanceof AltResult && x == ((AltResult) r).ex) {
            return r;
        }
        return new AltResult(x);
    }

    /**
     * Completes with the given (non-null) exceptional result as a wrapped CompletionException unless it is one already, unless already completed. May
     * complete with the given Object r (which must have been the result of a source future) if it is equivalent, i.e. if this is a simple propagation
     * of an existing CompletionException.
     */
    final boolean completeThrowable(Throwable x, Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeThrowable(x, r));
    }

    /**
     * Returns the encoding of a copied outcome; if exceptional, rewraps as a CompletionException, else returns argument.
     */
    static Object encodeRelay(Object r) {
        Throwable x;
        return (((r instanceof AltResult) &&
                (x = ((AltResult) r).ex) != null &&
                !(x instanceof CompletionException)) ?
                new AltResult(new CompletionException(x)) : r);
    }

    /**
     * Completes with r or a copy of r, unless already completed. If exceptional, r is first coerced to a CompletionException.
     */
    final boolean completeRelay(Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeRelay(r));
    }

    private static Unsafe reflectGetUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Generates per-thread initialization/probe field
     */
    private static final AtomicInteger probeGenerator =
            new AtomicInteger();

    /**
     * The increment for generating probe values
     */
    private static final int PROBE_INCREMENT = 0x9e3779b9;

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * The increment of seeder per new instance
     */
    private static final long SEEDER_INCREMENT = 0xbb67ae8584caa73bL;

    /**
     * The next seed for default constructors.
     */
    private static final AtomicLong seeder = new AtomicLong(initialSeed());

    private static long initialSeed() {
        String sec = VM.getSavedProperty("java.util.secureRandomSeed");
        if (Boolean.parseBoolean(sec)) {
            byte[] seedBytes = java.security.SecureRandom.getSeed(8);
            long s = (long) (seedBytes[0]) & 0xffL;
            for (int i = 1; i < 8; ++i) {
                s = (s << 8) | ((long) (seedBytes[i]) & 0xffL);
            }
            return s;
        }
        return (mix64(System.currentTimeMillis()) ^
                mix64(System.nanoTime()));
    }

    static final void localInit() {
        int p = probeGenerator.addAndGet(PROBE_INCREMENT);
        int probe = (p == 0) ? 1 : p; // skip 0
        long seed = mix64(seeder.getAndAdd(SEEDER_INCREMENT));
        Thread t = Thread.currentThread();
        UNSAFE.putLong(t, SEED, seed);
        UNSAFE.putInt(t, PROBE, probe);
    }

    /**
     * Returns the pseudo-randomly initialized or updated secondary seed.
     */
    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        } else {
            localInit();
            if ((r = (int) UNSAFE.getLong(t, SEED)) == 0) {
                r = 1; // avoid zero
            }
        }
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }

    // Unsafe mechanics
    private static final long SEED;
    private static final long PROBE;
    private static final long SECONDARY;

    static {
        try {
            Class<?> tk = Thread.class;
            SEED = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSeed"));
            PROBE = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomProbe"));
            SECONDARY = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
