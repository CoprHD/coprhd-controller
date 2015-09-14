package com.emc.storageos.coordinator.client.service.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedAroundHook;
import com.emc.storageos.coordinator.client.service.DistributedLockQueueManager;
import com.emc.storageos.coordinator.client.service.LeaderSelectorListenerForPeriodicTask;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Scheduled periodic task runner for a {@link DistributedLockQueueManager}, to be executed exclusively on a leader
 * using leader-election.
 *
 * @author Ian Bibby
 */
public class DistributedLockQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockQueueScheduler.class);
    private static final String LOCKQUEUE_LEADER_PATH = "lockqueueleader";
    private static final int INITIAL_DELAY = 300; // 5 minutes
    private static final int INTERVAL = 300;

    private CoordinatorClient coordinator;
    private DistributedLockQueueManager lockQueue;

    private LeaderSelector leaderSelector;
    private LeaderSelectorListenerForPeriodicTask listener;
    private DequeueValidator validator;

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void setLockQueue(DistributedLockQueueManager lockQueue) {
        this.lockQueue = lockQueue;
    }

    public void setValidator(DequeueValidator validator) {
        this.validator = validator;
    }

    public void start() {
        listener = new LeaderSelectorListenerForPeriodicTask(new LockQueuePeriodicTask(), INITIAL_DELAY, INTERVAL);
        leaderSelector = coordinator.getLeaderSelector(LOCKQUEUE_LEADER_PATH, listener);
        leaderSelector.autoRequeue();
        leaderSelector.start();
    }

    public void stop() {
        leaderSelector.close();
    }

    public DequeueValidator getValidator() {
        if (validator == null) {
            validator = new DequeueValidator();
        }
        return validator;
    }

    public static class DequeueValidator {
        public boolean validate(String lockKey) {
            return true;
        }
    }

    private class LockQueuePeriodicTask implements Runnable {

        @Override
        public void run() {
            try {
                log.info("Distributed lock queue scheduler is running");
                Set<String> lockKeys = lockQueue.getLockKeys();

                if (lockKeys == null || lockKeys.isEmpty()) {
                    log.info("Lock queue is empty");
                    return;
                }

                log.info("Number of locks to process: {}", lockKeys.size());
                for (final String lockKey : lockKeys) {
                    try {
                        log.info("Dequeueing HEAD from lock group: {}", lockKey);
                        DistributedAroundHook aroundHook = coordinator.getDistributedOwnerLockAroundHook();

                        aroundHook.run(new DistributedAroundHook.Action<Void>() {
                            @Override
                            public Void run() {
                                // Before this method runs, the globalLock will be acquired
                                if (getValidator().validate(lockKey)) {
                                    if (!lockQueue.dequeue(lockKey)) {
                                        // Nothing was de-queued (empty) so try and remove it
                                        lockQueue.removeLockKey(lockKey);
                                    }
                                } else {
                                    log.info("Skipping as lock is unavailable");
                                }
                                // After this method runs, the globalLock will be released
                                return null;
                            }
                        });

                    } catch (Exception e) {
                        log.error("Error occurred whilst processing locks", e);
                    }
                }
            } catch (Exception e) {
                log.error("Unexpected exception", e);
            }
        }
    }
}
