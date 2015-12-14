/*

 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.locking.LockRetryException;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zookeeper.KeeperException;
import com.emc.storageos.Controller;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.workflow.WorkflowService;
import org.apache.curator.framework.recipes.locks.Lease;

/**
 * Main API for queueing / dispatching calls to device specific controller implementations.
 */
public class Dispatcher extends DistributedQueueConsumer<ControlRequest> {
    private static final Logger _log = LoggerFactory.getLogger(Dispatcher.class);
    // todo separate queue per build?
    private static final String QUEUE_NAME = "controller";
    private static final int DEFAULT_MAX_THREADS = 10;
    private static final long DEFAULT_MAX_WAIT_STOP = 60 * 1000;
    private static final int DEFAULT_METHOD_EXECUTOR_POOL_SIZE = 100;
    private static final int ACQUIRE_LEASE_WAIT_TIME_SECONDS = 10;
    private static final int ACQUIRE_LEASE_RETRY_WAIT_TIME__SECONDS = 10;
    private static final int LOCK_RETRY_WAIT_TIME_SECONDS = 60;
    private static final int DEFAULT_CONTROLLER_MAX_ITEM = 1000;
    private static final int MAX_WORKFLOW_STEPS = 10000;
    private static final long STALE_ITEM_THRESHOLD = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Define the Queues used by the Dispatcher.
    // To add a new Queue, add it's name to the QueueName enum, and then add a constructor
    // in the DispatcherQueue[] _queues below.
    public static enum QueueName {
        controller, workflow_outer, workflow_inner;
    };

    private class DispatcherQueue {
        final QueueName _queue_name;
        final Integer _method_executor_pool_size;
        // _queue_max_item is not set for all queues hence its not a final variable
        Integer _queue_max_item;
        DistributedQueue<ControlRequest> _queue;
        ScheduledThreadPoolExecutor _methodPoolExecutor;

        DispatcherQueue(QueueName name, Integer poolSize, Integer maxItem) {
            _queue_name = name;
            _method_executor_pool_size = poolSize;
            _queue_max_item = maxItem;
        }

        DispatcherQueue(QueueName name, Integer poolSize) {
            _queue_name = name;
            _method_executor_pool_size = poolSize;
        }

        public DistributedQueue<ControlRequest> getQueue() {
            return _queue;
        }

        public void setQueue(DistributedQueue<ControlRequest> _queue) {
            this._queue = _queue;
        }

        public QueueName getQueueName() {
            return _queue_name;
        }

        public Integer getMethodExecutorPoolSize() {
            return _method_executor_pool_size;
        }

        public Integer getQueueMaxItem() {
            return _queue_max_item;
        }

        public ScheduledThreadPoolExecutor getMethodPoolExecutor() {
            return _methodPoolExecutor;
        }

        public void setMethodPoolExecutor(ScheduledThreadPoolExecutor executor) {
            _methodPoolExecutor = executor;
        }
    }

    DispatcherQueue[] _queues = {
            new DispatcherQueue(QueueName.controller, DEFAULT_METHOD_EXECUTOR_POOL_SIZE, DEFAULT_CONTROLLER_MAX_ITEM),
            new DispatcherQueue(QueueName.workflow_outer, DEFAULT_METHOD_EXECUTOR_POOL_SIZE / 5),
            new DispatcherQueue(QueueName.workflow_inner, DEFAULT_METHOD_EXECUTOR_POOL_SIZE)
    };

    // Methods to return the queues or a specific queue
    private DispatcherQueue[] getQueues() {
        return _queues;
    }

    private DispatcherQueue getDefaultQueue() {
        return _queues[0];
    }

    private DispatcherQueue getQueue(QueueName name) {
        for (DispatcherQueue q : getQueues()) {
            if (q.getQueueName() == name) {
                return q;
            }
        }
        return getDefaultQueue();
    }

    private DispatcherQueue getQueue(String queueName) {
        if (queueName == null) {
            return getDefaultQueue();
        }
        return getQueue(QueueName.valueOf(queueName));
    }

    private CoordinatorClient _coordinator;
    private Map<String, Controller> _controller;
    private Map<Controller, Map<String, Method>> _methodMap;
    private Map<String, Integer> _deviceMaxConnectionMap;
    private final ConcurrentMap<URI, DistributedSemaphore> _deviceSemaphoreMap = new ConcurrentHashMap<URI, DistributedSemaphore>();
    private int _acquireLeaseWaitTimeSeconds = ACQUIRE_LEASE_WAIT_TIME_SECONDS;
    private int _acquireLeaseRetryWaitTimeSeconds = ACQUIRE_LEASE_RETRY_WAIT_TIME__SECONDS;

    private DistributedLockQueueManager<ControlRequest> _lockQueueManager;

    /**
     * This method implements the logic for acquiring a device-specific semaphore.
     * To get a semaphore, the device-specific configuration must include setting up maxConnections
     * in _deviceMaxConnectionMap.
     * 
     * @param info Light wrapper of needed device properties (e.g., URI, DeviceType)
     * @return DistributedSemaphore instance, if maxConnections exists.
     *         null, otherwise.
     */
    private DistributedSemaphore getSemaphore(DeviceInfo info) {
        if (!info.getNeedsLock()) {
            return null;
        }
        DistributedSemaphore deviceSemaphore = _deviceSemaphoreMap.get(info.getURI());
        if (deviceSemaphore == null && _deviceMaxConnectionMap != null) {
            Integer maxConnections = _deviceMaxConnectionMap.get(info.getType());
            if (maxConnections != null) {
                synchronized (this) {
                    try {
                        deviceSemaphore = _deviceSemaphoreMap.get(info.getURI());
                        if (deviceSemaphore == null) {
                            deviceSemaphore = _coordinator.getSemaphore(info.getURI().toString(), maxConnections.intValue());
                        }
                        _deviceSemaphoreMap.put(info.getURI(), deviceSemaphore);
                    } catch (Exception e) {
                        _log.error("Error getting deviceSemaphore for device: {}", info.getURI().toString(), e);
                    }
                }
            }
        }
        return deviceSemaphore;
    }

    /**
     * A Runnable to enable periodic retries, in the event that the semaphore's leases are exhausted.
     * The item is processed and removed from the distributed queue when:
     * a) A semaphore cannot be acquired
     * b) A semaphore is acquired, and a lease is acquired
     */
    private class DeviceMethodInvoker implements Runnable {
        private final ControlRequest _item;
        private final DispatcherQueue _queue;
        private final Controller _innerController;
        private final Method _method;
        private final DistributedQueueItemProcessedCallback _callback;
        private final DistributedSemaphore _deviceSemaphore;
        private final Object[] _args;

        public DeviceMethodInvoker(ControlRequest item,
                DistributedQueueItemProcessedCallback callback) throws DeviceControllerException {
            _item = item;
            _queue = getQueue(item.getQueueName());
            final String targetClassName = item.getTargetClassName();
            _innerController = _controller.get(targetClassName);
            if (_innerController == null) {
                _log.info("Failed Getting target: " + targetClassName);
                throw DeviceControllerException.exceptions.unableToDispatchToController(targetClassName);
            }
            _method = _methodMap.get(_innerController).get(item.getMethodName());
            _args = item.getArg();
            _deviceSemaphore = getSemaphore(item.getDeviceInfo());
            _callback = callback;
        }

        @Override
        public void run() {
            Lease lease = null;
            boolean bRetryLease = false;
            boolean bInvocationProblem = false;
            boolean bRetryLock = false;
            boolean isStale = false;
            try {
                // Reset the thread name temporarily so that the log lines don't
                // reference a thread name that may have already completed its work.
                // Any log lines above this line will have a thread name that may
                // reference work that may have already been completed.
                String defaultName = String.format("%s-thread-%d", Thread.currentThread().getThreadGroup().getName(), Thread
                        .currentThread().getId());
                Thread.currentThread().setName(defaultName);
                _log.info("Invoking {}: {}", _method.getName(), _args);
                String opId = "";
                URI resourceId = new URI("");
                if (_args.length > 1) {
                    if (_args.length > 2
                            && _args[_args.length - 2] != null
                            && _args[_args.length - 2].getClass().equals(URI.class)) {
                        resourceId = (URI) _args[_args.length - 2];
                    }
                    opId = (String) _args[_args.length - 1];
                    List<String> stringArgs = new ArrayList<String>();
                    StringBuilder threadNameBuilder = new StringBuilder();
                    threadNameBuilder.append(Thread.currentThread().getId()).append('|').
                            append(_method.getName()).append('|');
                    for (Object arg : _args) {
                        if (arg instanceof String) {
                            stringArgs.add((String) arg);
                        }
                    }
                    threadNameBuilder.append(Joiner.on('|').join(stringArgs));
                    Thread.currentThread().setName(threadNameBuilder.toString());
                }
                ControllerUtils.setThreadLocalLogData(resourceId, opId);
                long now = System.currentTimeMillis();
                long timeSinceItemCreation = now - _item.getTimestamp();
                if (timeSinceItemCreation < STALE_ITEM_THRESHOLD) {
                    if (_deviceSemaphore == null) {
                        // this device did not specify maxConnections.
                        _log.info("Dispatching task {}: {}", _method.getName(), _args);
                        _method.invoke(_innerController, _args);
                    } else {
                        lease = _deviceSemaphore.acquireLease(_acquireLeaseWaitTimeSeconds, TimeUnit.SECONDS);
                        if (lease != null) {
                            _log.info("Dispatching task {}: {}", _method.getName(), _args);
                            _method.invoke(_innerController, _args);
                        } else {
                            // Could not get a lease. Retry.
                            _log.info("Rescheduling task {}: {}", _method.getName(), _args);
                            _queue.getMethodPoolExecutor().schedule(this, _acquireLeaseRetryWaitTimeSeconds, TimeUnit.SECONDS);
                            bRetryLease = true;
                        }
                    }
                } else {
                    _log.info(String.format("Task %s is stale and will not be executed. Timestamp for request was %d (%s), now = %d",
                            _method.getName(), _item.getTimestamp(), new Date(_item.getTimestamp()).toString(), now));
                    isStale = true;
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof LockRetryException) {
                    LockRetryException lockEx = (LockRetryException) cause;
                    _item.setLockGroup(lockEx.getLockIdentifier());
                    if (!addRequestToLockQueue(lockEx, _item)) {
                        _log.warn("Rescheduling task {}: {}", _method.getName(), _args);
                        _queue.getMethodPoolExecutor().schedule(this, LOCK_RETRY_WAIT_TIME_SECONDS, TimeUnit.SECONDS);
                        bRetryLock = true;
                    }
                } else {
                    _log.warn("Problem executing task: " + _method.getName() + "; {}", _args, e);
                    bInvocationProblem = true;
                }
            } catch (Exception e) {
                _log.warn("Problem executing task: " + _method.getName() + "; {}", _args, e);
                bInvocationProblem = true;
            } finally {
                try {
                    if (_deviceSemaphore != null && lease != null) {
                        _deviceSemaphore.returnLease(lease);
                    }
                    if ((!bRetryLease && !bInvocationProblem && !bRetryLock) || isStale) {
                        // The method was invoked. Cleanup.
                        _callback.itemProcessed();
                        _log.info("Done with task {}: {}", _method.getName(), _args);
                    }
                } catch (Exception e) {
                    _log.warn("Problem removing task from queue: " + _method.getName() + ", {}", _args, e);
                }
            }
        }
    }

    /**
     * Attempt to push an item onto the lock queue.
     *
     * @param lockEx LockRetryException instance
     * @param item Item to queue
     * @return true if the lock is not available and queueing was successful
     */
    private boolean addRequestToLockQueue(final LockRetryException lockEx, final ControlRequest item) {
        try {
            _log.info(String.format("Dispatcher processing LockRetryException key %s remaining time %s",
                    lockEx.getLockPath(), lockEx.getRemainingWaitTimeSeconds()));

            DistributedAroundHook<Boolean> aroundHook = _coordinator.getDistributedOwnerLockAroundHook();
            Boolean result = aroundHook.run(new DistributedAroundHook.Action<Boolean>() {

                @Override
                public Boolean run() {
                    // Before this method runs, the globalLock will be acquired
                    try {
                        return !_coordinator.isDistributedOwnerLockAvailable(lockEx.getLockPath()) &&
                                _lockQueueManager.queue(lockEx.getLockIdentifier(), item);
                    } catch (Exception e) {
                        return false;
                    }
                    // After this method runs, the globalLock will be released
                }
            });

            return result.booleanValue();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sets coordinator
     * 
     * @param coordinator
     */
    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Sets device specific controller implementations
     * 
     * @param controller
     */
    public void setController(Set<Controller> controller) {
        _controller = new HashMap<String, Controller>();
        _methodMap = new HashMap<Controller, Map<String, Method>>();
        Iterator<Controller> it = controller.iterator();
        while (it.hasNext()) {
            Controller c = it.next();
            _controller.put(c.getClass().getName(), c);
            Map<String, Method> methodMap = new HashMap<String, Method>();
            Method[] methods = c.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                methodMap.put(methods[i].getName(), methods[i]);
            }
            _methodMap.put(c, methodMap);
        }
    }

    /**
     * Sets _deviceMaxConnectionMap <DeviceType, MaxConnections>
     * 
     * @param deviceMaxConnectionMap
     */
    public void setDeviceMaxConnectionMap(Map<String, Integer> deviceMaxConnectionMap) {
        _deviceMaxConnectionMap = deviceMaxConnectionMap;
    }

    /**
     * Sets _methodExecutorPoolSize, if configured.
     * 
     * @param corePoolSize Specified size of the _methodExecutorPool
     */
    public void setMethodExecutorPoolSize(int corePoolSize) {
        getDefaultQueue().getMethodPoolExecutor().setMaximumPoolSize(corePoolSize);
    }

    /**
     * Sets _acquireLeaseWaitTimeSeconds if configured.
     * 
     * @param waitTime Specified blocking wait time on acquireLease requests
     */
    public void setAcquireLeaseWaitTimeSeconds(int waitTime) {
        _acquireLeaseWaitTimeSeconds = waitTime;
    }

    /**
     * Sets _acquireLeaseRetryWaitTimeSeconds if configured.
     * 
     * @param retryWaitTime Specified retry wait time after limited-blocking acquireLease call returns no lease.
     */
    public void setAcquireLeaseRetryWaitTimeSeconds(int retryWaitTime) {
        _acquireLeaseRetryWaitTimeSeconds = retryWaitTime;
    }

    /**
     * Creates _methodPoolExecutor for each Queue
     */
    public void build() {
        for (DispatcherQueue q : getQueues()) {
            q.setMethodPoolExecutor(
                    new ScheduledThreadPoolExecutor(q.getMethodExecutorPoolSize()) {
                        @Override
                        protected void afterExecute(Runnable r, Throwable t) {
                            // After executing the runnable, clear the thread local log data
                            // that was set by the provisioning method invoked by the runnable.
                            ControllerUtils.clearThreadLocalLogData();
                        }
                    }
                    );
        }
    }

    /**
     * Queues a task to the default "controller" queue. This is the original behavior.
     * 
     * @param deviceURI
     * @param deviceType
     * @param target
     * @param method
     * @param args
     * @throws ControllerException
     */
    public void queue(final URI deviceURI, final String deviceType, Object target, String method,
            Object... args) throws ControllerException {
        queue(getDefaultQueue().getQueueName(), deviceURI, deviceType, true, target, method, args);
    }

    /**
     * Queues a method call against device specific controller
     * 
     * @param queueName of enum QueueName identifies the Dispatcher queue to be
     *            used
     * @param deviceURI
     * @param deviceType
     * @param target
     * @param method
     * @param args
     * 
     * @throws ControllerException
     */
    public void queue(final QueueName queueName, final URI deviceURI, final String deviceType,
            Object target, String method, Object... args) throws ControllerException {
        queue(queueName, deviceURI, deviceType, true, target, method, args);
    }

    /**
     * Queues a method call against device specific controller
     * 
     * @param queueName of enum QueueName identifies the Dispatcher queue to be
     *            used
     * @param deviceURI
     * @param deviceType
     * @param lockDevice indicates whether a semaphore should be acquired for the device
     * @param target
     * @param method
     * @param args
     * 
     * @throws ControllerException
     */
    public void queue(final QueueName queueName, final URI deviceURI, final String deviceType, boolean lockDevice,
            Object target, String method, Object... args) throws ControllerException {
        ControlRequest req = new ControlRequest(queueName.name(),
                new DeviceInfo(deviceURI, deviceType, lockDevice), target, method, args);
        try {
            if (QueueName.controller.equals(queueName)) {
                checkZkStepToWorkflowSize();
            }
            getQueue(queueName).getQueue().put(req);
        } catch (final CoordinatorException e) {
            throw ClientControllerException.retryables.queueToBusy();
        } catch (final ClientControllerException e) {
            throw ClientControllerException.retryables.queueToBusy();
        } catch (final KeeperException e) {
            _log.error("Exception occurred while queueing item", e);
            throw ClientControllerException.fatals.unableToQueueJob(deviceURI);
        } catch (final Exception e) {
            throw ClientControllerException.fatals.unableToQueueJob(deviceURI, e);
        }
        _log.info("Queued task {}: {} ", method, args);
    }

    /**
     * Place back onto the Dispatcher an existing ControlRequest instance that would
     * have been held in a lock queue.
     *
     * @See {@link DistributedLockQueueManager}
     *
     * @param item          An existing ControlRequest.
     * @throws Exception
     */
    public void queue(ControlRequest item) throws Exception {
        try {
            if (QueueName.controller.toString().equalsIgnoreCase(item.getQueueName())) {
                checkZkStepToWorkflowSize();
            }
            getQueue(item.getQueueName()).getQueue().put(item);
        } catch (final CoordinatorException e) {
            throw ClientControllerException.retryables.queueToBusy();
        } catch (final ClientControllerException e) {
            throw ClientControllerException.retryables.queueToBusy();
        } catch (final KeeperException e) {
            throw ClientControllerException.fatals.unableToQueueJob(item.getDeviceInfo().getURI());
        } catch (final Exception e) {
            throw ClientControllerException.fatals.unableToQueueJob(item.getDeviceInfo().getURI(), e);
        }
        _log.info("Queued existing task {}: {} ", item.getMethodName(), item.getArg());
    }

    /**
     * This method checks the size of the total number of steps across all the running
     * workflows in zoo keeper if it reaches the default limit then it throws
     * QueueTooBusyException
     * 
     * @throws Exception
     */
    private void checkZkStepToWorkflowSize() throws Exception {
        int zkStep2WorkflowSize = WorkflowService.getZkStep2WorkflowSize();
        if (zkStep2WorkflowSize > MAX_WORKFLOW_STEPS) {
            _log.error("Queue is too busy. More than " + MAX_WORKFLOW_STEPS + " zookeeper step2workflow found.");
            throw ClientControllerException.retryables.queueToBusy();
        }
    }

    /**
     * Starts dispatcher
     * 
     * @throws Exception
     */
    public void start() throws Exception {
        _coordinator.start();
        // todo make max threads configurable
        // _queue = _coordinator.getQueue(QUEUE_NAME, this, new ControlRequestSerializer(), DEFAULT_MAX_THREADS);
        for (DispatcherQueue q : getQueues()) {
            if (q.getQueueMaxItem() != null) {
                q.setQueue(_coordinator.getQueue(q.getQueueName().name(), this,
                        new ControlRequestSerializer(), DEFAULT_MAX_THREADS,
                        q.getQueueMaxItem()));
            } else {
                q.setQueue(_coordinator.getQueue(q.getQueueName().name(), this,
                        new ControlRequestSerializer(), DEFAULT_MAX_THREADS));
            }
        }
    }

    /**
     * Stops dispatcher
     * 
     * @throws IOException
     */
    public void stop() throws IOException {
        // todo make wait time configurable
        for (DispatcherQueue q : getQueues()) {
            q.getQueue().stop(DEFAULT_MAX_WAIT_STOP);
        }
    }

    @Override
    public void consumeItem(ControlRequest item, DistributedQueueItemProcessedCallback callback) throws Exception {
        DispatcherQueue queue = getQueue(item.getQueueName());
        queue.getMethodPoolExecutor().execute(new DeviceMethodInvoker(item, callback));
    }

    /**
     * Container class from device properties that we like to give to ControlRequest
     */
    public static class DeviceInfo implements Serializable {

        private URI uri;
        private String type;
        private boolean needsLock;

        public DeviceInfo() {
        }

        public DeviceInfo(URI deviceURI, String deviceType, boolean lockDevice) {
            uri = deviceURI;
            type = deviceType;
            needsLock = lockDevice;
        }

        public void setURI(URI deviceURI) {
            uri = deviceURI;
        }

        public void setType(String deviceType) {
            type = deviceType;
        }

        public URI getURI() {
            return uri;
        }

        public String getType() {
            return type;
        }

        /**
         * @return the lock
         */
        public boolean getNeedsLock() {
            return needsLock;
        }

        /**
         * @param lock the lock to set
         */
        public void setNeedsLock(boolean lock) {
            this.needsLock = lock;
        }
    }

    public Map<String, Controller> getControllerMap() {
        return _controller;
    }

    @Override
    public boolean isBusy(String queue) {
        // For the provioning operations, basically all the nodes (with large thread pool)
        // would get similar load naturally. More nodes and longer running, more evenly.
        // If Dispatcher needs better load balance, it could enhance it from here.
        return false;
    }

    public void setLockQueueManager(DistributedLockQueueManager lockQueueManager) {
        _lockQueueManager = lockQueueManager;
    }
}
