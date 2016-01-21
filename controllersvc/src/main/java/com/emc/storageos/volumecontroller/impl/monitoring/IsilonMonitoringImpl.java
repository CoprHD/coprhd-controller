/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonEvent;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.volumecontroller.impl.monitoring.isilon.RecordableIsilonEvent;

/**
 * Handles monitoring for Isilon devices
 * 
 */
public class IsilonMonitoringImpl implements IMonitoringStorageSystem {

    private final Logger _logger = LoggerFactory.getLogger(IsilonMonitoringImpl.class);
    private IsilonApiFactory _isilonApiFactory;
    private RecordableEventManager _recordableEventManager;
    // interval for events polling thread
    private final long _intervalSeconds = MonitoringJobConsumer.MONITORING_INTERVAL * 60;
    // Planned overlap for polling intervals between consecutive requests.
    private final long plannedIntervalOverlapSeconds = 3;

    /**
     * Holds IsilonDevice instance and its lock's callback instance
     */
    private final Map<MonitoredDevice, DistributedQueueItemProcessedCallback> ISILON_CACHE =
            new ConcurrentHashMap<MonitoredDevice, DistributedQueueItemProcessedCallback>();
    /**
     * Lock instance to handle static CACHE synchronization
     */
    private final Object cacheLock = new Object();

    private DbClient _dbClient;

    /**
     * Takes care monitoring for the given Isilon device after acquring lock from zooKeeper queue.
     */
    @Override
    public void startMonitoring(MonitoringJob monitoringJob,
            DistributedQueueItemProcessedCallback callback) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            String storageSystemURI = monitoringJob.getId().toString();
            _logger.info("storageSystemURI :{}", storageSystemURI);
            try {
                addIsilonDeviceIntoCache(storageSystemURI, callback);
            } catch (Exception e) {
                _logger.error(e.getMessage(), e);
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void scheduledMonitoring() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            stopMonitoringStaleSystem();
            for (MonitoredDevice device : ISILON_CACHE.keySet()) {
                device.collectMonitoringEvents();
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void stopMonitoringStaleSystem() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        Iterator<Map.Entry<MonitoredDevice, DistributedQueueItemProcessedCallback>> iter = ISILON_CACHE.entrySet().iterator();
        StorageSystem storageDeviceFromDB = null;
        while (iter.hasNext()) {
            Map.Entry<MonitoredDevice, DistributedQueueItemProcessedCallback> entry = iter.next();
            MonitoredDevice monitoredDevice = entry.getKey();
            URI isilonDeviceURI = monitoredDevice._storageSystemURI;
            _logger.debug("storageDeviceURI :{}", isilonDeviceURI);
            try {
                storageDeviceFromDB = _dbClient.queryObject(StorageSystem.class, isilonDeviceURI);
            } catch (DatabaseException e) {
                _logger.error(e.getMessage(), e);
            }

            if (null == storageDeviceFromDB || storageDeviceFromDB.getInactive()) {
                _logger.info("Stale isilon {} has been removed from monitoring", isilonDeviceURI);
                try {
                    entry.getValue().itemProcessed();// Removes monitorinJob token from queue
                } catch (Exception e) {
                    _logger.error("Exception occurred while removing monitoringJob token from ZooKeeper queue", e);
                } finally {
                    iter.remove(); // Removes item from CACHE
                }
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    /**
     * Get isilon device represented by the StorageDevice
     * 
     * @param device Isilon's StorageSystem instance
     * @return IsilonApi object
     * @throws IsilonException
     */
    private IsilonApi getIsilonDevice(StorageSystem device) throws IsilonException {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        IsilonApi isilonApi;
        URI deviceURI;
        try {
            deviceURI = new URI("https", null, device.getIpAddress(), device.getPortNumber(), "/", null, null);
        } catch (URISyntaxException ex) {
            throw IsilonException.exceptions.errorCreatingServerURL(device.getIpAddress(), device.getPortNumber(), ex);
        }
        // if no username, assume its the isilon simulator device
        if (device.getUsername() != null && !device.getUsername().isEmpty()) {
            isilonApi = _isilonApiFactory.getRESTClient(deviceURI,
                    device.getUsername(), device.getPassword());
        } else {
            isilonApi = _isilonApiFactory.getRESTClient(deviceURI);
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());

        return isilonApi;
    }

    /**
     * 
     * @param storageSystemURI {@link String} isilonDevice URI
     * @param callBack {@link DistributedQueueItemProcessedCallback} lock's callBack instance
     * @throws IOException
     */
    private void addIsilonDeviceIntoCache(String storageSystemURI, DistributedQueueItemProcessedCallback callBack) throws IOException {
        if (StringUtils.isNotEmpty(storageSystemURI)) {
            MonitoredDevice device = new MonitoredDevice(storageSystemURI);
            ISILON_CACHE.put(device, callBack);
        }
    }

    /**
     * Wrapper class for Isilon devices monitoring
     */
    public class MonitoredDevice {
        private final URI _storageSystemURI;
        private long _lastPolled;
        long _latestTimeThreshold;  // most recent timestamp of events received in one request
        long _mostRecentTimestampInPollingCycle;

        /**
         * Constructor, sets the device and work item
         * 
         * @param deviceURI
         */
        public MonitoredDevice(String deviceURI) {
            _storageSystemURI = URI.create(deviceURI);
            _lastPolled = (System.currentTimeMillis() - (_intervalSeconds * 1000)) / 1000;  // in seconds
            _latestTimeThreshold = 0;
            _mostRecentTimestampInPollingCycle = 0;
        }

        public long getLastPolled() {
            return _lastPolled;
        }

        /**
         * @return BiosCommandResult on error
         * @throws Exception
         */
        public void collectMonitoringEvents() {
            IsilonApi api = null;
            try {
                StorageSystem storagesystem = _dbClient.queryObject(StorageSystem.class, _storageSystemURI);

                _logger.info("Monitoring events for {} using ip {}", storagesystem.getId(), storagesystem.getIpAddress());
                if (CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storagesystem.getCompatibilityStatus()) ||
                        CompatibilityStatus.UNKNOWN.name().equalsIgnoreCase(storagesystem.getCompatibilityStatus())) {
                    api = getIsilonDevice(storagesystem);
                    List<IsilonEvent> events;
                    long curTime = System.currentTimeMillis() / 1000;

                    // Set start time to _lastPolled and subtract planned overlap to ensure we do not miss events on Isilon.
                    long startTime = _lastPolled - plannedIntervalOverlapSeconds; // absolute start time in seconds
                    long startTimeRelative = startTime - curTime;
                    // we use 0 value for end time to indicate relative current time on Isilon system.
                    // need to use relative time for remote host (absolute time may not match on remote host)
                    events = api.queryEvents(startTimeRelative, 0, storagesystem.getFirmwareVersion()).getList();

                    // Filter out events with timestamp less or equal to most recent timestamp of events in the previous request.
                    // This is required due to the fact that request intervals may overlap on the remote Isilon host.
                    List<IsilonEvent> filteredEvents = filterEvents(events);

                    RecordableIsilonEvent batch[] = new RecordableIsilonEvent[filteredEvents.size()];
                    int i = 0;
                    for (IsilonEvent event : filteredEvents) {
                        RecordableIsilonEvent e = new RecordableIsilonEvent(storagesystem, event);
                        batch[i++] = e;
                    }
                    if (null != _recordableEventManager) {
                        _recordableEventManager.recordEvents(batch);
                        _logger.info("Done monitoring device {} events {}, saved to database.", storagesystem.getId(),
                                filteredEvents.size());
                    }
                    else {
                        _logger.error("Unable to record Isilon Monitoring events because RecordableEventManager is not initialized");
                    }
                    _lastPolled = curTime;
                    _latestTimeThreshold = _mostRecentTimestampInPollingCycle;
                } else {
                    _logger.info("Monitoring will not happen for the incompatible isilon device :{}", storagesystem.getId());
                }

            } catch (Exception th) {
                _logger.error("Monitoring cycle failed for :{}", _storageSystemURI, th);
            }
        }

        /**
         * Filter out events with timestamp less or equal to most recent timestamp of events in the previous request.
         * This is required due to the fact that request intervals may overlap on the remote Isilon host.
         * 
         * @param events events to filter
         * @return list of filtered events
         */
        List<IsilonEvent> filterEvents(List<IsilonEvent> events) {
            long mostRecentTimestamp = _latestTimeThreshold;
            List<IsilonEvent> filteredEvents = new ArrayList<IsilonEvent>();
            for (IsilonEvent event : events) {
                long latestTime = event.getLatestTime();
                if (latestTime > _latestTimeThreshold) {
                    filteredEvents.add(event);
                    if (latestTime > mostRecentTimestamp) {
                        mostRecentTimestamp = latestTime;
                    }
                }
            }

            _mostRecentTimestampInPollingCycle = mostRecentTimestamp;
            return filteredEvents;
        }

        @Override
        public String toString() {
            return String.format("URI:%1$s, Device Type:isilon, _lastPolled:%2$tc", _storageSystemURI, new Date(_lastPolled * 1000));
        }
    }

    /**
     * Setter method for DbClient instance
     * 
     * @param _dbClient {@link DbClient}
     */
    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    /**
     * Sets RecordableEventManager
     * 
     * @param eventManager
     */
    public void setRecordableEventManager(RecordableEventManager eventManager) {
        _recordableEventManager = eventManager;
    }

    /**
     * Set Isilon API factory
     * 
     * @param factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _isilonApiFactory = factory;
    }

    @Override
    public void clearCache() {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        synchronized (cacheLock) {
            ISILON_CACHE.clear();
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

}
