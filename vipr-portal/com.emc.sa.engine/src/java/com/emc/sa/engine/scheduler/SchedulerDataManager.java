/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.scheduler;

import com.emc.sa.model.util.ScheduledTimeComparator;
import com.emc.storageos.db.client.model.uimodels.*;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.db.client.util.ExecutionWindowHelper;
import com.emc.storageos.db.client.model.NamedURI;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SchedulerDataManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerDataManager.class);

    /** The lock for accessing shared data. */
    private final Lock lock = new ReentrantLock();
    /** The condition for waiting on active windows. */
    private final Condition hasActiveWindows = lock.newCondition();

    /** Model access. */
    @Autowired
    private ModelClient models;
    /** The map of currently active windows. */
    private Map<URI, ExecutionWindow> activeWindows = Maps.newHashMap();
    /** The set of currently processing orders. */
    private Set<URI> activeOrders = Sets.newHashSet();

    private boolean enableInfiniteExecutionWindow = true;

    /**
     * Gets all execution windows from the database.
     * 
     * @return the list of all execution windows.
     */
    protected List<ExecutionWindow> getAllExecutionWindows() {
        List<URI> ids = models.findByType(ExecutionWindow.class);
        return models.findByIds(ExecutionWindow.class, ids);
    }

    /**
     * Updates the active windows.
     * 
     * @see #getAllExecutionWindows()
     */
    public void updateActiveWindows() {
        LOG.debug("Updating active windows");
        List<ExecutionWindow> allWindows = getAllExecutionWindows();
        Map<URI, ExecutionWindow> currentWindows = getCurrentActiveWindows();
        Calendar currentTime = Calendar.getInstance();
        for (ExecutionWindow window : allWindows) {
            boolean active = isActive(window, currentTime);
            if (active) {
                activateWindow(window);
            }
            else {
                deactivateWindow(window);
            }
            currentWindows.remove(window.getId());
        }

        // Deactivate any execution windows which aren't in the list of all windows
        for (ExecutionWindow window : currentWindows.values()) {
            deactivateWindow(window);
        }
    }

    /**
     * Waits for active windows to become available. This will not return until at least one window is active.
     * 
     * @return the mapping of active windows.
     * 
     * @throws InterruptedException
     */
    protected Map<URI, ExecutionWindow> waitForActiveWindows() throws InterruptedException {
        Map<URI, ExecutionWindow> windows = Maps.newHashMap();
        while (windows.isEmpty()) {
            lock.lock();
            try {
                if (activeWindows.isEmpty() && enableInfiniteExecutionWindow == false ) {
                    hasActiveWindows.await();
                }
                windows.putAll(activeWindows);
                if (enableInfiniteExecutionWindow == true) {
                    return windows;
                }
            } finally {
                lock.unlock();
            }
        }
        return windows;

    }

    /**
     * Determines if the window is active at the given time.
     * 
     * @param window
     *            the execution window.
     * @param time
     *            the time.
     * @return true if the window is active.
     */
    protected boolean isActive(ExecutionWindow window, Calendar time) {
        ExecutionWindowHelper helper = new ExecutionWindowHelper(window);
        return helper.isActive(time);
    }

    /**
     * Gets the execution windows that are currently active.
     * 
     * @return the currently active execution windows.
     */
    protected Map<URI, ExecutionWindow> getCurrentActiveWindows() {
        Map<URI, ExecutionWindow> currentWindows = Maps.newHashMap();
        lock.lock();
        try {
            currentWindows.putAll(activeWindows);
        } finally {
            lock.unlock();
        }
        return currentWindows;
    }

    /**
     * Activates an execution window, if required. If the window is not already active, it is added to the active
     * windows and all waiting objects are notified.
     * 
     * @param window
     *            the window to activate.
     */
    protected void activateWindow(ExecutionWindow window) {
        lock.lock();
        try {
            if (!activeWindows.containsKey(window.getId())) {
                LOG.info("Activate window: " + window.getLabel());
                activeWindows.put(window.getId(), window);
                hasActiveWindows.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deactivates an execution window, if required. If the window is currently active, it is removed from the active
     * windows and all waiting objects are notified.
     * 
     * @param window
     */
    protected void deactivateWindow(ExecutionWindow window) {
        lock.lock();
        try {
            if (activeWindows.containsKey(window.getId())) {
                LOG.info("Deactivate window: " + window.getLabel());
                activeWindows.remove(window.getId());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets and locks the next scheduled order that can be executed. This will wait until an order becomes available.
     * Once an order is returned from this method, the tenant will be locked until {@link #unlockOrder(Order)} is
     * called.
     * 
     * @return the next scheduled order.
     * 
     * @throws InterruptedException
     *             if the thread is interrupted.
     */
    public Order lockNextScheduledOrder() throws InterruptedException {
        Order order = null;
        while (order == null) {
            order = tryGetNextScheduledOrder();
            if (order == null) {
                Thread.sleep(15000);
            }
        }
        LOG.debug("Locked order " + order.getId());
        return order;
    }

    /**
     * Signals that the order is finished, releasing the tenant lock.
     * 
     * @param order
     *            the order.
     */
    public void unlockOrder(Order order) {
        LOG.debug("Unlocking order " + order.getId());
        doUnlockOrder(order);
    }

    /**
     * Checks for a scheduled order that can be run in any of the active windows. Any order returned from this method
     * will have its tenant locked until {@link #unlockOrder(Order)} is called.
     * 
     * @return the next order to be run, or null if none are available.
     * 
     * @throws InterruptedException
     *             if the thread is interrupted.
     */
    protected Order tryGetNextScheduledOrder() throws InterruptedException {
        // It would include normal active window and special INFINITE execution window
        Map<URI, ExecutionWindow> windows = waitForActiveWindows();

        // Pull all orders from the database and match orders to tenant and execution windows
        List<Order> orders = getAllScheduledOrders();
        lock.lock();
        try {
            Set<String> activeTenants = new HashSet<String>();
            if (windows.size() != 0) {
                // Get the tenants that are currently active within execution windows
                activeTenants = getTenants(windows.values());
            }

            Calendar currTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            for (Order order : orders) {
                LOG.debug("The order should be scheduled at {}", order.getScheduledTime());
                if (currTime.before(order.getScheduledTime())) {
                    LOG.debug("It is not time to invoke the earliest order yet.");
                    break;
                }

                if (order.getExecutionWindowId() == null) {
                    // order is not subjected to normal execution window but the special INFINITE window.

                    // check if the order is expired
                    if (isExpiredOrder(order, null)) {
                        order.setOrderStatus(OrderStatus.ERROR.name());
                        models.save(order);
                        continue;
                    }

                    // lock a order
                    if (!isLocked(order)) {
                        if (lockOrder(order)) {
                            return order;
                        }
                    }
                } else {
                    // order is subjected to normal execution window
                    if (windows.size() == 0) {
                        continue;
                    }

                    // lock a order if tenant is active and window is matched.
                    boolean matchesTenant = activeTenants.contains(order.getTenant());
                    if (matchesTenant && !isLocked(order) && canRunInWindow(order, windows)) {
                        if (lockOrder(order)) {
                            return order;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        // No matching orders
        return null;
    }

    /**
     * Determines if the order is locked.
     * 
     * @param order
     *            the order.
     * @return true if the order is locked.
     */
    private boolean isLocked(Order order) {
        lock.lock();
        try {
            return activeOrders.contains(order.getId());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts the lock the order. If the order is already active this returns false.
     * 
     * @param order
     *            the order.
     * @return true if the order is successfully locked.
     */
    private boolean lockOrder(Order order) {
        lock.lock();
        try {
            return activeOrders.add(order.getId());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unlocks the order.
     * 
     * @param order
     *            the order.
     */
    private void doUnlockOrder(Order order) {
        lock.lock();
        try {
            activeOrders.remove(order.getId());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the set of tenants from the execution windows.
     * 
     * @param windows
     *            the execution windows.
     * @return the set of tenants.
     */
    protected Set<String> getTenants(Collection<ExecutionWindow> windows) {
        Set<String> tenants = Sets.newHashSet();
        for (ExecutionWindow window : windows) {
            tenants.add(window.getTenant());
        }
        return tenants;
    }

    /**
     * Gets all scheduled orders from the database, ordered.
     * 
     * @return the list of all scheduled orders.
     */
    protected List<Order> getAllScheduledOrders() {
        List<Order> orders = models.orders().findByOrderStatus(OrderStatus.SCHEDULED);
        Collections.sort(orders, ScheduledTimeComparator.OLDEST);
        return orders;
    }

    /**
     * Determines if the order can run in any of the given execution windows.
     * 
     * @param order
     *            the order.
     * @param windows
     *            the execution windows.
     * @return true if the order can run in the windows.
     */
    protected boolean canRunInWindow(Order order, Map<URI, ExecutionWindow> windows) {
        NamedURI windowId = order.getExecutionWindowId();
        if (ExecutionWindow.isNextWindow(windowId)) {
            LOG.debug("Matches NEXT window");
            return true;
        }
        else if ((windowId != null) && windows.containsKey(windowId.getURI())) {
            LOG.debug("Matches '" + windowId.getName() + "' window");
            return true;
        }
        else {
            return false;
        }
    }

    private boolean isExpiredOrder(Order order, ExecutionWindow window) {
        ExecutionWindowHelper windowHelper = new ExecutionWindowHelper(window);
        if (windowHelper.isExpired(order.getScheduledTime())) {
            order.setOrderStatus(OrderStatus.ERROR.name());
            LOG.info("order {} has expired.", order.getId());
            models.save(order);
            return true;
        }
        return false;
    }

    /**
     * Gets all REOCCURRENCE scheduled events from the database, ordered.
     *
     * @return the list of all scheduled events
     */
    public List<ScheduledEvent> getAllReoccurrenceEvents() {
        List<ScheduledEvent> scheduledEvents = models.scheduledEvents().findByScheduledEventType(ScheduledEventType.REOCCURRENCE);
        return scheduledEvents;
    }


}
