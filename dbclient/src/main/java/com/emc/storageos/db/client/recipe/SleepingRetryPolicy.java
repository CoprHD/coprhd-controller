/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.recipe;

import java.util.Random;

/**
 * Base sleeping retry policy with optional count limit. The sleep time
 * is delegated to the subclass.
 */
public abstract class SleepingRetryPolicy {
    private final int maxAttempts;
    private int attempts;

    public SleepingRetryPolicy() {
        this.maxAttempts = 0;
    }

    public SleepingRetryPolicy(int max) {
        this.maxAttempts = max;
        this.attempts = 0;
    }

    public boolean allowRetry() {
        if (maxAttempts == -1 || attempts < maxAttempts) {
            try {
                Thread.sleep(getSleepTimeMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            attempts++;
            return true;
        }
        return false;
    }

    public int getAttemptCount() {
        return attempts;
    }

    public int getMaxAttemptCount() {
        return maxAttempts;
    }

    public abstract SleepingRetryPolicy duplicate();

    protected abstract long getSleepTimeMs();

    /**
     * A simple RunOnce Policy which doesn't allow to retry
     */
    public static class RunOnce extends SleepingRetryPolicy {
        public static RunOnce instance = new RunOnce();

        public static RunOnce get() {
            return instance;
        }

        @Override
        public boolean allowRetry() {
            return false;
        }

        @Override
        public SleepingRetryPolicy duplicate() {
            return RunOnce.get();
        }

        @Override
        protected long getSleepTimeMs() {
            return 0;
        }
    }

    /**
     * Bounded exponential backoff that will wait for no more than a provided max amount of time.
     * The following examples show the maximum wait time for each attempt
     *
     * BoundedExponentialBackoff(250, 5000, 10)
     * 250 500 1000 2000 4000 5000 5000 5000 5000 5000
     *
     */
    public static class BoundedExponentialBackoff extends SleepingRetryPolicy {
        private final int MAX_SHIFT = 30;

        private final Random random = new Random();
        private final long baseSleepTimeMs;
        private final long maxSleepTimeMs;

        public BoundedExponentialBackoff(long baseSleepTimeMs, long maxSleepTimeMs, int max) {
            super(max);
            this.baseSleepTimeMs = baseSleepTimeMs;
            this.maxSleepTimeMs = maxSleepTimeMs;
        }

        @Override
        public long getSleepTimeMs() {
            int attempt = this.getAttemptCount() + 1;
            if (attempt > MAX_SHIFT) {
                attempt = MAX_SHIFT;
            }
            long sleepTimeMs = baseSleepTimeMs * Math.max(1, random.nextInt(1 << attempt));
            return Math.min(maxSleepTimeMs, sleepTimeMs);
        }

        public SleepingRetryPolicy duplicate() {
            return new BoundedExponentialBackoff(baseSleepTimeMs, maxSleepTimeMs, getMaxAttemptCount());
        }

    }
}
