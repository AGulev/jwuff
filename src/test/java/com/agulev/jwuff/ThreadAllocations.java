package com.agulev.jwuff;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

final class ThreadAllocations {
    private ThreadAllocations() {}

    static long measureAllocatedBytesForCurrentThread(Runnable op) {
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemorySupported()) {
            return -1L;
        }
        if (!mx.isThreadAllocatedMemoryEnabled()) {
            mx.setThreadAllocatedMemoryEnabled(true);
        }
        long tid = Thread.currentThread().threadId();
        long before = mx.getThreadAllocatedBytes(tid);
        op.run();
        long after = mx.getThreadAllocatedBytes(tid);
        return Math.max(0L, after - before);
    }

    static long measureAllocatedBytesForNewThread(Runnable op) throws Exception {
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemorySupported()) {
            return -1L;
        }
        if (!mx.isThreadAllocatedMemoryEnabled()) {
            mx.setThreadAllocatedMemoryEnabled(true);
        }

        String threadName = "jwuff-alloc-" + System.nanoTime();
        long[] delta = new long[1];
        Throwable[] thrown = new Throwable[1];

        Thread t = new Thread(() -> {
            Thread.currentThread().setName(threadName);
            long before = mx.getThreadAllocatedBytes(Thread.currentThread().threadId());
            try {
                op.run();
            } catch (Throwable th) {
                thrown[0] = th;
            } finally {
                long after = mx.getThreadAllocatedBytes(Thread.currentThread().threadId());
                delta[0] = Math.max(0L, after - before);
            }
        });

        t.start();
        t.join();

        if (thrown[0] != null) {
            if (thrown[0] instanceof RuntimeException re) throw re;
            throw new RuntimeException(thrown[0]);
        }
        return delta[0];
    }
}
