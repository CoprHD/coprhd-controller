package com.emc.storageos.locking;

/**
 * An exception thrown when we could not acquire the required locks and
 * we want to schedule a retry.
 */
public class LockRetryException extends Error {
    private String lockPath;
    private Long remainingWaitTimeSeconds;

    public LockRetryException(final String lockPath, final Long remainingWaitTimeSeconds) {
        super(String.format("Could not acquired required lock %s, will retry for %d additional seconds", lockPath
                , remainingWaitTimeSeconds));
        this.lockPath = lockPath;
        this.remainingWaitTimeSeconds = remainingWaitTimeSeconds;
    }

    public String getLockPath() {
        return lockPath;
    }

    public String getLockIdentifier() {
        return lockPath.substring(lockPath.lastIndexOf('/') + 1, lockPath.length());
    }

    public Long getRemainingWaitTimeSeconds() {
        return remainingWaitTimeSeconds;
    }
}
