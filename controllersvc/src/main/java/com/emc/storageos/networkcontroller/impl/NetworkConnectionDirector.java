package com.emc.storageos.networkcontroller.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.recipes.locks.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedSemaphore;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.volumecontroller.impl.Dispatcher.DeviceInfo;

/**
 * Questions: 
 * what happens if a thread dies before releasing the lease ?
 * what happens if a node dies ?
 * Should I build the map around the thread or the task?
 *
 */
public class NetworkConnectionDirector {
    private static final Logger _log = LoggerFactory.getLogger(NetworkConnectionDirector.class);
    private Map<String, Map<URI, Lease>> threadsLeases = new HashMap<String, Map<URI, Lease>>();
    private static final int maxRetryCount = 3;
    private Dispatcher dispatcher;
    
    public synchronized void acquireLease(NetworkSystem networkSystem) {
        DistributedSemaphore _deviceSemaphore = null;
        Lease lease = null;
        int retryCount = 0;
        String name = Thread.currentThread().getName();
        try {
            // stop the same thread from acquiring a lease on the same resource
            lease = getFromLeasesMap(name, networkSystem.getId());
            // if this is the first lease for this thread and resource combination, try to get a lease
            if (lease == null) {
                _log.debug("Getting lease for thread {} and network system {}", name, networkSystem.getLabel());
                DeviceInfo info = new DeviceInfo(networkSystem.getId(), networkSystem.getSystemType(), true);
                _deviceSemaphore = dispatcher.getSemaphore(info);
                if(_deviceSemaphore == null) {
                    // this network system did not specify maxConnections. This should mean no lease is required
                    _log.info("Cannot get lease for network system {} because it did not specify maxConnections.", networkSystem.getLabel());
                } else {
                    // loop as many as maxRetryCount 
                    while (lease == null ) { 
                        lease = _deviceSemaphore.acquireLease(dispatcher._acquireLeaseWaitTimeSeconds, TimeUnit.SECONDS);
                        if(lease != null) {
                            addToLeasesMap(name, networkSystem.getId(), lease);
                        } else {
                            if (retryCount < maxRetryCount ) {
                                // Could not get a lease. Retry.
                                _log.debug("Could not get lease for thread {} and network system {}. Retrying", name, networkSystem.getLabel());
                                retryCount++;
                                try {
                                    Thread.sleep(dispatcher._acquireLeaseRetryWaitTimeSeconds * 1000);
                                } catch (InterruptedException e) {
                                    _log.error("Thread waiting to acquire lease was interrupted and "
                                            + "will be resumed");
                                }
                            } else {
                                // Exceeded maxRetryCount, time to quit
                                _log.error("Could not get lease for thread {} and network system {}", name, networkSystem.getLabel());
                                throw NetworkDeviceControllerException.exceptions.couldNotAcquireLease(networkSystem.getLabel());
                            }
                        }
                    }
                }
            } else {
                // The thread already has the lease and should not be asking for another
                _log.error("Thread {} already has a lease to network system {}", name, networkSystem.getLabel());
                throw NetworkDeviceControllerException.exceptions.leaseAlreadyAcquired(networkSystem.getLabel());
            }
        } catch(Exception ex) {
            _log.error("Problem aquiring lease for network system: , Error message: ", networkSystem.getLabel(), ex);
            try {
                if(_deviceSemaphore != null && lease != null) {
                    _deviceSemaphore.returnLease(lease);
                }
            } catch(Exception e) {
                _log.warn("Problem returning lease for  network system: , Error message: ", networkSystem.getLabel(), e);
            }
        }
    }

    public synchronized void returnLease(NetworkSystem networkSystem) {
        DistributedSemaphore _deviceSemaphore = null;
        Lease lease = null;
        String name = Thread.currentThread().getName();
        try {
            // find the lease
            lease = getFromLeasesMap(name, networkSystem.getId());
            // if we have the lease, return it
            if (lease != null) {
                _log.debug("Returning lease for thread {} and network system {}", name, networkSystem.getId());
                DeviceInfo info = new DeviceInfo(networkSystem.getId(), networkSystem.getSystemType(), true);
                _deviceSemaphore = dispatcher.getSemaphore(info);
                if(_deviceSemaphore != null) {
                    _deviceSemaphore.returnLease(lease);
                    removeFromLeasesMap(name, networkSystem.getId());
                    // this network system did not specify maxConnections.
                } else {
                    _log.debug(
                            "Cannot return lease for network system {} because this network system did not specify maxConnections.", networkSystem.getId());
                }
            } else {
                _log.warn("A lease was not acquired by thread {} for network system {}.", name, networkSystem.getId());
            }
        } catch(Exception ex) {
            _log.warn("Problem aquiring lock for thread {} : ", ex);
            try {
                if(_deviceSemaphore != null && lease != null) {
                    _deviceSemaphore.returnLease(lease);
                }
            } catch(Exception e) {
                _log.warn("Problem releasing aquired lock for task.", e);
            }
        }
    }
    
    private void addToLeasesMap (String threadName, URI resourceId, Lease lease) {
        Map<URI, Lease> threadLeases =  threadsLeases.get(threadName);
        if (threadLeases == null) {
            threadLeases = new HashMap<URI, Lease>();
            threadsLeases.put(threadName, threadLeases);
        }
        threadLeases.put(resourceId, lease);
    }
    
    private Lease getFromLeasesMap (String threadName, URI resourceId) {
        Map<URI, Lease> threadLeases =  threadsLeases.get(threadName);
        if (threadLeases == null) {
            return null;
        }
        return threadLeases.get(resourceId);
    }
    
    private void removeFromLeasesMap (String threadName, URI resourceId) {
        Map<URI, Lease> threadLeases =  threadsLeases.get(threadName);
        if (threadLeases != null) {
            threadLeases.remove(resourceId);
            if (threadLeases.isEmpty()) {
                // make sure the map does not grow indefinitely
                threadsLeases.remove(threadName);
            }
        }
    }

    public void setDispatcher(Dispatcher _dispatcher) {
        this.dispatcher = _dispatcher;
    }
}
