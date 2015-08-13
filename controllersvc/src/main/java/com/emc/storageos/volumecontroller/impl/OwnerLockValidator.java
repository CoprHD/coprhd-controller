package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueScheduler;
import com.emc.storageos.locking.DistributedOwnerLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends the default
 * {@link com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueScheduler.DequeueValidator} in order
 * to check the availability of an owner lock at the time #validate is called.
 *
 * @author Ian Bibby
 */
public class OwnerLockValidator extends DistributedLockQueueScheduler.DequeueValidator {

    private static final Logger log = LoggerFactory.getLogger(OwnerLockValidator.class);

    private DistributedOwnerLockService ownerLockService;

    public OwnerLockValidator(DistributedOwnerLockService ownerLockService) {
        this.ownerLockService = ownerLockService;
    }

    @Override
    public boolean validate(String lockKey) {
        try {
            log.info("Checking availability of lock: {}", lockKey);
            return ownerLockService.isLockAvailable(lockKey);
        } catch (Exception e) {
            log.warn("Caught exception whilst validating owner lock availability: {}", lockKey, e);
        }
        return false;
    }
}
