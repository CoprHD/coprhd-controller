package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueItemNameGenerator;

/**
 * Implementation of {@link DistributedLockQueueItemNameGenerator} for ControlRequest instances, which uses
 * their timestamp property.
 *
 * @author Ian Bibby
 */
public class ControlRequestLockQueueItemName implements DistributedLockQueueItemNameGenerator<ControlRequest> {
    @Override
    public String generate(ControlRequest item) {
        if (item == null || item.getTimestamp() == null || item.getTimestamp() == 0L) {
            throw new IllegalArgumentException("ControlRequest instance must have non-null, non-zero timestamp");
        }
        return item.getTimestamp().toString();
    }
}
