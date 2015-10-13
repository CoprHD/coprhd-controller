package com.emc.storageos.volumecontroller.impl;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueTaskConsumerCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueTaskConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consume ControlRequest instances for the purpose of queueing them back onto the {@link Dispatcher}.
 *
 * @author Ian Bibby
 */
public class ControlRequestTaskConsumer extends DistributedLockQueueTaskConsumer<ControlRequest> {

    private static final Logger log = LoggerFactory.getLogger(ControlRequestTaskConsumer.class);
    private Dispatcher dispatcher;

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void consumeTask(ControlRequest task, final DistributedLockQueueTaskConsumerCallback callback) {
        try {
            log.info("Sending locked ControlRequest to the dispatcher");
            dispatcher.queue(task);
            callback.taskConsumed();
        } catch (Exception e) {
            log.error("Error occurred consuming task.", e);
        }
    }
}