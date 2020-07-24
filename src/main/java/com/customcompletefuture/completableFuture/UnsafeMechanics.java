package com.customcompletefuture.completableFuture;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static com.customcompletefuture.completableFuture.AltResult.NIL;
import static com.customcompletefuture.completableFuture.AltResult.encodeThrowable;

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

    static void lazySetNext(Completion c, Completion next) {
        UNSAFE.putOrderedObject(c, NEXT, next);
    }

    /* ------------- Encoding and decoding outcomes -------------- */

    /**
     * Completes with a non-exceptional result, unless already completed.
     */
    final boolean completeValue(T t) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null, (t == null) ? NIL : t);
    }

    /**
     * Completes with an exceptional result, unless already completed.
     */
    final boolean completeThrowable(Throwable x) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null, encodeThrowable(x));
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
}
