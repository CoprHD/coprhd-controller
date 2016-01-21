/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.healthmonitor;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.systemservices.impl.healthmonitor.models.Status;
import com.emc.vipr.model.sys.healthmonitor.ServiceHealth;
import com.emc.vipr.model.sys.healthmonitor.ServiceStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that calculates node and its services health.
 * Node health status:
 * GOOD - when node is reachable and all its services are GOOD
 * UNAVAILABLE - when node is not reachable
 * DEGRADED - when node is reachable and any of its service is UNAVAILABLE/DEGRADED
 * <p/>
 * Service health status: GOOD - when a service is up and running UNAVAILABLE - when a service is not running but is registered in
 * coordinator RESTARTED - when service is restarting
 */
public class NodeHealthExtractor implements StatConstants {

    private static final Logger _log = LoggerFactory.getLogger(NodeHealthExtractor.class);

    /**
     * Method that populates list of services with its health.
     * List of available services are retrieved from coordinator while list of active
     * services are taken from service statistics.
     * 
     * @return Overall service status. This is used to calculate node status.
     *         GOOD if all services are GOOD.
     *         DEGRADED if any of the service is UNAVAILABLE.
     * @throws Exception
     */
    public static List<ServiceHealth> getServiceHealth(
            List<ServiceStats> serviceStatsList, CoordinatorClient coordinator, String nodeId) {
        // Get running services from stats service
        if (serviceStatsList != null) {
            List<ServiceHealth> serviceHealthList = new ArrayList<ServiceHealth>();
            for (ServiceStats serviceStats : serviceStatsList) {
                String sName = serviceStats.getServiceName();
                if (sName == null || sName.isEmpty() || MONITOR_SVCNAME.equals(sName)) {
                    continue;
                }
                serviceHealthList.add(new ServiceHealth(sName,
                        getServiceStatusFromStats(serviceStats, coordinator, nodeId).toString()));
            }
            return serviceHealthList;
        } else {
            return null;
        }
    }

    /**
     * Calculates service status from the service statistics.
     * UNAVAILABLE if the service process cannot be found.
     * RESTARTED if service uptime is less than or equal to 0.
     * DEGRADED if the service process is fine by corresponding service beacon is not found in ZK.
     * GOOD otherwise
     * 
     * @param serviceStats service statistics
     * @return service status
     */
    private static Status getServiceStatusFromStats(ServiceStats serviceStats, CoordinatorClient coordinator, String nodeId) {
        String svcName = serviceStats.getServiceName();
        // If service status is not available..
        if (serviceStats.getProcessStatus() == null || serviceStats.getProcessStatus()
                .getStartTime() == 0 || serviceStats.getProcessStatus()
                .getUpTime() == 0) {
            return Status.UNAVAILABLE;
        }
        else if (Constants.DBSVC_NAME.equals(svcName) || Constants.GEODBSVC_NAME.equals(svcName)) {
            // Check service beacon to know service status. make it work for dbsvc/geodbsvc first, then rest of other
            // after we unified beacon format
            String svcVersion = coordinator.getTargetDbSchemaVersion();
            List<Service> svcs;
            try {
                svcs = coordinator.locateAllServices(svcName, svcVersion, null, null);
            } catch (RetryableCoordinatorException ex) {
                _log.warn("Failed to look for Beacon for service {}, treat as status DEGRADED", svcName, ex);
                return Status.DEGRADED;
            }

            // In case we cannot find the service in ZK, the service is DEGRADED. It's not UNAVAILABLE
            // because we still see its process as GOOD
            Status status = Status.DEGRADED;
            if (nodeId != null) {
                for (Service svc : svcs) {
                    if (nodeId.equals(svc.getNodeId())) {
                        status = Status.GOOD;
                    }
                }
            }
            return status;
        }
        else {
            // Check service status based on process id
            return serviceStats.getProcessStatus().getUpTime() <= 0 ? Status.RESTARTED : Status.GOOD;
        }

    }
}
