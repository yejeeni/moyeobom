package com.moyeobom.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * 예약 작업을 바로 실행하지 않고 쌓아 두었다가, 시계를 예약 시각으로 옮기며 순서대로 실행한다.
 */
public class FakeTaskScheduler implements TaskScheduler {

    private final MutableClock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<Entry> queue = new PriorityQueue<>(
            Comparator.comparing(Entry::at).thenComparingLong(Entry::seq));

    public FakeTaskScheduler(MutableClock clock) {
        this.clock = clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        FakeFuture future = new FakeFuture();
        queue.add(new Entry(startTime, sequence.incrementAndGet(), task, future));
        return future;
    }

    /** 다음 예약 하나를 실행한다. 실행할 것이 없으면 false. */
    public boolean runNext(Instant until) {
        while (!queue.isEmpty() && !queue.peek().at().isAfter(until)) {
            Entry entry = queue.poll();
            if (entry.future().isCancelled()) {
                continue;
            }
            clock.set(entry.at());
            entry.future().done = true;
            entry.task().run();
            return true;
        }
        return false;
    }

    public long pendingCount() {
        return queue.stream().filter(entry -> !entry.future().isCancelled()).count();
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw new UnsupportedOperationException();
    }

    private record Entry(Instant at, long seq, Runnable task, FakeFuture future) {
    }

    private static class FakeFuture implements ScheduledFuture<Object> {

        private volatile boolean cancelled;
        private volatile boolean done;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
