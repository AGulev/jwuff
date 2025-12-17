package com.agulev.jwuff;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class JfrAllocations {
    private JfrAllocations() {}

    static long measureAllocatedBytesForCurrentThread(Runnable op) throws Exception {
        String threadName = "jwuff-jfr-" + System.nanoTime();
        long[] allocated = new long[1];
        long[] matchedEvents = new long[1];

        Thread t = new Thread(() -> {
            Thread.currentThread().setName(threadName);
            op.run();
        });

        Path jfr = Files.createTempFile("jwuff-", ".jfr");
        jfr.toFile().deleteOnExit();

        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationInNewTLAB").withThreshold(Duration.ZERO);
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withThreshold(Duration.ZERO);
            recording.start();

            t.start();
            t.join();

            recording.stop();
            recording.dump(jfr);
        }

        try (RecordingFile rf = new RecordingFile(jfr)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent ev = rf.readEvent();
                String name = ev.getEventType().getName();
                if (!name.equals("jdk.ObjectAllocationInNewTLAB") && !name.equals("jdk.ObjectAllocationOutsideTLAB")) {
                    continue;
                }
                String evThread = threadJavaName(ev);
                if (evThread != null && !threadName.equals(evThread)) continue;
                matchedEvents[0]++;
                allocated[0] += ev.getLong("allocationSize");
            }
        }

        try {
            Files.deleteIfExists(jfr);
        } catch (Exception ignored) {
        }
        if (matchedEvents[0] == 0) {
            // Either JFR didn't record allocation events (some runtimes/configs), or the thread field
            // name differs; return 0 and let callers fall back to heap usage info.
            return 0L;
        }
        return allocated[0];
    }

    private static String threadJavaName(RecordedEvent ev) {
        try {
            var t = ev.getThread("eventThread");
            return t == null ? null : t.getJavaName();
        } catch (IllegalArgumentException ignored) {
        }
        try {
            var t = ev.getThread("thread");
            return t == null ? null : t.getJavaName();
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }
}
