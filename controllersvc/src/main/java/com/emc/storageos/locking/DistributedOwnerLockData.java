/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.locking;

import java.io.Serializable;

public class DistributedOwnerLockData implements Serializable {
    /**
     * This lock data is persisted in Zookeeper.
     */
    String owner;			// the lock's owner
    Long timeAcquired;		// the time (msec) the lock was acquired

    DistributedOwnerLockData(String owner, Long time) {
        this.owner = owner;
        this.timeAcquired = time;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Long getTimeAcquired() {
        return timeAcquired;
    }

    public void setTimeAcquired(Long timeAcquired) {
        this.timeAcquired = timeAcquired;
    }
}
