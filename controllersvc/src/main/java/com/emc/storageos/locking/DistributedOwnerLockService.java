/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.locking;

import java.util.List;

public interface DistributedOwnerLockService {
    public final Long POLL_TIME = 0L;        // do not block

    /**
     * Acquires multiples DistributedOwner locks according to the names in the lockNames list.
     * These locks are integrated with Workflows, and normally accessed through the WorkflowService calls.
     * 
     * @param lockNames -- List of lockNames; acquired in the supplied order
     * @param owner -- Normally the workflow id.
     * @param Seconds -- seconds to try wait. 0 = check once only
     *            If some locks could not be acquired, any acquired locks are released.
     * @return true if locks are all acquired, false if no locks are acquired
     */
    public boolean acquireLocks(List<String> lockNames, String owner, long Seconds);

	/**
	 * Acquires multiples DistributedOwner locks according to the names in the lockNames list.
	 * These locks are integrated with Workflows, and normally accessed through the WorkflowService calls.
	 * @param lockNames -- List of lockNames; acquired in the supplied order
	 * @param owner -- Normally the workflow id.
	 * @param lockingStartedTimeSeconds -- the time (from System.millis()) expressed in seconds
	 * when we started trying to acquire the locks
	 * @param maxWaitTimeSeconds -- maximum wait time seconds to acquire all locks
	 * If some locks could not be acquired, any acquired locks are released.
	 * @return true if locks are all acquired, false if no locks are acquired
	 * @throws LockRetryException to cause the Dispatcher to retry the operation later
	 */
	public boolean acquireLocks(List<String> lockNames, String owner,
								long lockingStartedTimeSeconds, long maxWaitTimeSeconds) throws LockRetryException;

    /**
     * Releases the specified DistributedOwner locks.
     * 
     * @param lockNames -- List of lockNames to be released.
     * @param owner -- Normally the workflow id.
     * @return -- true if locks are all released, false otherwise.
     */
    public boolean releaseLocks(List<String> lockNames, String owner);

    /**
     * Releases all locks for an owner.
     * 
     * @param owner -- Normally the workflow id or step id.
     * @return -- true if locks are all released, false otherwise.
     */
    public boolean releaseLocks(String owner);

    /**
     * returns a the list of locks currently owned by owner
     * 
     * @param owner -- Normally the workflow id or step.
     * @return -- list of locks id's for the owner
     */
    public List<String> getLocksForOwner(String owner);

    /**
     * Acquire the lock.
     * 
     * @param lockKey
     * @param owner
     * @param maxWaitSeconds
     * @return
     */
    public abstract boolean acquireLock(String lockKey, String owner,
            long maxWaitSeconds);

    /**
     * Release the lock.
     * 
     * @param lockName
     * @return true if lock released
     */
    public abstract boolean releaseLock(String lockName, String owner);

	/**
	 * Checks if an owner lock is available for acquirement at the time of calling.
	 *
	 * @param lockName
	 * @return true, if the lock is available.
	 * @throws Exception
	 */
	boolean isLockAvailable(String lockName) throws Exception;

}