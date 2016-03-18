/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectConnectionException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterObjectNotFoundException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterServerConnectionException;
import com.emc.storageos.vcentercontroller.exceptions.VcenterSystemException;
import com.vmware.vim25.ClusterConfigSpecEx;
import com.vmware.vim25.HostConfigFault;
import com.vmware.vim25.HostConnectSpec;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.HostStorageDeviceInfo;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.HostSystemPowerState;
import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.ScsiLun;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VmfsDatastoreCreateSpec;
import com.vmware.vim25.VmfsDatastoreInfo;
import com.vmware.vim25.VmfsDatastoreOption;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostDatastoreSystem;
import com.vmware.vim25.mo.HostStorageSystem;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Created with IntelliJ IDEA.
 * User: alaplante
 * Date: 9/16/14
 * Time: 11:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class VcenterApiClient {

    private final static Logger _log = LoggerFactory.getLogger(VcenterApiClient.class);

    private ServiceInstance serviceInstance;
    private VcenterClusterConfigurer clusterConfigurer = new VcenterClusterConfigurerPropertyInfoImpl();
    private PropertyInfo propertyInfo;

    private ServiceInstance createServiceInstance(String vcenterUrl, String vcenterUsername, String vcenterPassword)
            throws VcenterServerConnectionException {
        return connect(vcenterUrl, vcenterUsername, vcenterPassword);
    }

    public VcenterApiClient(PropertyInfo propertyInfo) {
        this.propertyInfo = propertyInfo;
    }

    private ServiceInstance connect(String url, String user, String password) throws VcenterServerConnectionException {
        try {
            return new ServiceInstance(new URL(url), user, password, true);
        } catch (InvalidLogin il) {
            _log.error("Invalid vCenter server credentials");
            throw new VcenterServerConnectionException("Invalid vCenter server credentials");
        } catch (Exception e) {
            _log.error("Error connecting to vCenter server");
            throw new VcenterServerConnectionException("Error connecting to vCenter server");
        }
    }

    public void setup(String vcenterIp, String vcenterUsername, String vcenterPassword) throws VcenterServerConnectionException {
        String vcenterUrl = "https://" + vcenterIp + ":443/sdk";
        serviceInstance = createServiceInstance(vcenterUrl, vcenterUsername, vcenterPassword);
        _log.info("Connected to vcenter " + vcenterIp + " API version " + serviceInstance.getAboutInfo().getApiVersion());
    }

    public void destroy() {
        if (serviceInstance != null) {
            serviceInstance.getServerConnection().logout();
            _log.info("Log out successful");
        }
    }

    private ClusterComputeResource searchClusterComputeResource(String datacenterName, String clusterNameOrMoRef)
            throws VcenterSystemException, VcenterObjectNotFoundException {
        if (serviceInstance == null) {
            _log.error("Invoke setup to open connection before using client");
            throw new VcenterSystemException("Invoke setup to open connection before using client");
        }

        try {
            // MoRef search first
            _log.info("Search cluster by MoRef " + clusterNameOrMoRef);
            ManagedEntity[] clusterComputeResources = new InventoryNavigator(serviceInstance.getRootFolder())
                    .searchManagedEntities("ClusterComputeResource");
            for (ManagedEntity managedEntity : clusterComputeResources) {
                if (managedEntity.getMOR().getVal().equals(clusterNameOrMoRef)) {
                    _log.info("Found cluster " + managedEntity.getName() + " by MoRef " + clusterNameOrMoRef);
                    return (ClusterComputeResource) managedEntity;
                }
            }

            // Then name
            _log.info("Search cluster by name " + clusterNameOrMoRef + " in datacenter " + datacenterName);
            Datacenter datacenter = (Datacenter) new InventoryNavigator(serviceInstance.getRootFolder()).searchManagedEntity("Datacenter",
                    datacenterName);
            if (datacenter == null) {
                _log.error("Datacenter " + datacenterName + " does not exist");
                throw new VcenterObjectNotFoundException("Datacenter " + datacenterName + " does not exist");
            }
            ClusterComputeResource clusterComputeResource = (ClusterComputeResource) new InventoryNavigator(datacenter)
                    .searchManagedEntity("ClusterComputeResource", clusterNameOrMoRef);
            if (clusterComputeResource == null) {
                _log.error("Cluster " + clusterNameOrMoRef + " does not exist");
                throw new VcenterObjectNotFoundException("Cluster " + clusterNameOrMoRef + " does not exist");
            }
            return clusterComputeResource;
        } catch (VcenterObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            _log.error("searchClusterComputeResources exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    /*
     * Provide names of the vCenter elements and method will locate the MangedEntity representation
     * Each search is done within context of previous entity thus there is a dependency
     * Cluster
     * Datacenter
     * Host
     * Parameters are optional (ie, leave host null to only search datacenter and cluster)
     * Must provide parent element name or child will not be searched
     * hostConnectedPoweredOn ensures host is operational and ready for calls
     */
    private Map<String, ManagedEntity> createManagedEntityMap(String datacenterName, String clusterNameOrMoRef, String hostname,
            boolean hostConnectedPoweredOn) throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        if (serviceInstance == null) {
            _log.error("Invoke setup to open connection before using client");
            throw new VcenterSystemException("Invoke setup to open connection before using client");
        }

        try {
            Map<String, ManagedEntity> vcenterManagedEntityMap = new HashMap<String, ManagedEntity>();

            if (datacenterName != null && !datacenterName.trim().equals("")) {
                Datacenter datacenter = (Datacenter) new InventoryNavigator(serviceInstance.getRootFolder()).searchManagedEntity(
                        "Datacenter", datacenterName);
                if (datacenter == null) {
                    _log.error("Datacenter " + datacenterName + " does not exist");
                    throw new VcenterObjectNotFoundException("Datacenter " + datacenterName + " does not exist");
                }
                vcenterManagedEntityMap.put("Datacenter", datacenter);

                if (clusterNameOrMoRef != null && !clusterNameOrMoRef.trim().equals("")) {
                    ClusterComputeResource clusterComputeResource = searchClusterComputeResource(datacenterName, clusterNameOrMoRef);
                    vcenterManagedEntityMap.put("ClusterComputeResource", clusterComputeResource);

                    if (hostname != null && !hostname.trim().equals("")) {
                        HostSystem hostSystem = findByHostname(clusterComputeResource, hostname);
                        if (hostSystem == null) {
                            _log.error("Host " + hostname + " does not exist");
                            throw new VcenterObjectNotFoundException("Host " + hostname + " does not exist");
                        }
                        if (hostConnectedPoweredOn) {
                            checkHostConnectedPoweredOn(hostSystem);
                        }
                        vcenterManagedEntityMap.put("HostSystem", hostSystem);
                    }
                }
            }
            return vcenterManagedEntityMap;
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("getVcenterObjects exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public String createCluster(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException {
        try {
            createManagedEntityMap(datacenterName, null, null, false).get("Datacenter"); // Ensures datacenter exists
            ClusterComputeResource clusterComputeResource = null;
            try {
                clusterComputeResource = searchClusterComputeResource(datacenterName, clusterNameOrMoRef);
            } catch (VcenterObjectNotFoundException e) {
                _log.info("Ignore VcenterObjectNotFoundException on cluster search since we are creating new cluster");
            }
            if (clusterComputeResource != null) {
                _log.error("Cluster " + clusterNameOrMoRef + " already exists");
                throw new VcenterSystemException("Cluster " + clusterNameOrMoRef + " already exists");
            }
            return createOrUpdateCluster(datacenterName, clusterNameOrMoRef);
        } catch (VcenterSystemException | VcenterObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            _log.error("createCluster exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public String updateCluster(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException {
        try {
            createManagedEntityMap(datacenterName, clusterNameOrMoRef, null, false); // Make call to check that cluster exists
            return createOrUpdateCluster(datacenterName, clusterNameOrMoRef);
        } catch (VcenterSystemException | VcenterObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            _log.error("updateCluster exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private String createOrUpdateCluster(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException {
        try {
            _log.info("Request to create or update cluster " + clusterNameOrMoRef + " in datacenter " + datacenterName);

            Map<String, ManagedEntity> managedEntityMap = createManagedEntityMap(datacenterName, null, null, false);
            Datacenter datacenter = (Datacenter) managedEntityMap.get("Datacenter"); // Ensure datacenter exists

            ClusterComputeResource clusterComputeResource = null;
            try {
                clusterComputeResource = searchClusterComputeResource(datacenterName, clusterNameOrMoRef);
                _log.info("Cluster " + clusterNameOrMoRef + " already exists so no work to do");
            } catch (VcenterObjectNotFoundException e) {
                _log.info("Cluster " + clusterNameOrMoRef + " does not exist and will be created");

                ClusterConfigSpecEx clusterConfigSpecEx = clusterConfigurer.configure(propertyInfo);
                clusterComputeResource = datacenter.getHostFolder().createClusterEx(clusterNameOrMoRef, clusterConfigSpecEx);
                _log.info("Cluster " + clusterNameOrMoRef + " created with key " + clusterComputeResource.getMOR().getVal());
            }

            return clusterComputeResource.getMOR().getVal();
        } catch (VcenterSystemException | VcenterObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            _log.error("Exception creating cluster: " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private HostSystem findByHostname(ClusterComputeResource clusterComputeResource, String hostname) throws VcenterSystemException {
        try {
            ManagedEntity[] hostSystems = new InventoryNavigator(clusterComputeResource).searchManagedEntities("HostSystem");

            _log.info("Search for host " + hostname + " by exact match");
            for (ManagedEntity hostManagedEntity : hostSystems) {
                HostSystem hostSystem = (HostSystem) hostManagedEntity;
                if (hostSystem.getName().equalsIgnoreCase(hostname)) {
                    _log.info("Found host by exact match based search " + hostSystem.getName());
                    return hostSystem;
                }
            }

            // Exact match failed so its qualified in one system but not in the other
            _log.info("Search for host " + hostname + " by FQDN and unqualified searches");
            Collection<HostSystem> hosts = new ArrayList<HostSystem>();
            if (hostname.contains(".")) { // FQDN
                for (ManagedEntity hostManagedEntity : hostSystems) {
                    HostSystem hostSystem = (HostSystem) hostManagedEntity;
                    if (hostSystem.getName().toLowerCase().equalsIgnoreCase(hostname.split("\\.")[0])) {
                        _log.info("Found host by FQDN based search " + hostSystem.getName());
                        hosts.add(hostSystem);
                    }
                }
            } else { // unqualified
                for (ManagedEntity hostManagedEntity : hostSystems) {
                    HostSystem hostSystem = (HostSystem) hostManagedEntity;
                    if (hostSystem.getName().toLowerCase().startsWith(hostname.toLowerCase())) {
                        _log.info("Found host by unqualified based search " + hostSystem.getName());
                        hosts.add(hostSystem);
                    }
                }
            }
            if (hosts.isEmpty()) {
                _log.info("Host " + hostname + " not found");
            } else if (hosts.size() > 1) {
                _log.info("Host " + hostname + " search returned ambiguous result set of many hosts");
            } else {
                return hosts.iterator().next();
            }
            return null;
        } catch (Exception e) {
            _log.error("findByHostname exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private String getHostCertificate(String hostname, String username, String password) throws VcenterSystemException,
            VcenterObjectConnectionException {
        try {
            Integer hostOperationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_host_operation_timeout"));
            Integer sslTimeout = hostOperationTimeout * 1000;
            Integer retryCount = 1;
            Long startTimeMillis = System.currentTimeMillis();
            Long cutoffTimeMillis = startTimeMillis + sslTimeout;

            VcenterHostCertificateGetter.HostConnectionStatus status = VcenterHostCertificateGetter.getInstance().getConnectionStatus(
                    hostname, 443, "/host/ssl_cert", username, password, 300);
            while ((System.currentTimeMillis() < cutoffTimeMillis)
                    && status == VcenterHostCertificateGetter.HostConnectionStatus.UNREACHABLE) {
                _log.info("Host " + hostname + " is unreachable after attempt " + retryCount + " - Retry SSL cert request");
                Thread.sleep(10000); // Retry is time based and if each retry executes very quickly (ie milliseconds) then we should
                                     // throttle and only retry every 10 seconds
                status = VcenterHostCertificateGetter.getInstance().getConnectionStatus(hostname, 443, "/host/ssl_cert", username,
                        password, 300);
                retryCount++;
            }

            String errorSuggestion = null;
            if (status == VcenterHostCertificateGetter.HostConnectionStatus.UNREACHABLE) {
                errorSuggestion = "Ensure host is powered on and responsive. Can be caused by intermittent or temporary connectivity issue thus retry recommended";
            } else if (status == VcenterHostCertificateGetter.HostConnectionStatus.UNKNOWN) {
                errorSuggestion = "Ensure hostname is correct and DNS resolvable";
            } else if (status == VcenterHostCertificateGetter.HostConnectionStatus.UNAUTHORIZED) {
                errorSuggestion = "Ensure host credentials are correct";
            }
            if (errorSuggestion != null) {
                _log.error("Host " + hostname + " not reachable in state " + status + " - " + errorSuggestion);
                throw new VcenterObjectConnectionException("Host " + hostname + " not reachable in state " + status + " - "
                        + errorSuggestion);
            }

            String thumbprint = null;
            try {
                thumbprint = VcenterHostCertificateGetter.getInstance().getSSLThumbprint(hostname, 443, "/host/ssl_cert", username,
                        password, sslTimeout);
            } catch (Exception e) {
                _log.info("Error getting host " + hostname + " SSL certificate " + e);
                thumbprint = VcenterHostCertificateGetter.getInstance().getSSLThumbprint(hostname, 443, "/host/ssl_cert", username,
                        password, sslTimeout);
            }
            if (thumbprint == null || thumbprint.equals("")) {
                _log.error("Could not retrieve host " + hostname + " SSL certificate");
                throw new VcenterSystemException("Could not retrieve host " + hostname + " SSL certificate");
            }
            return thumbprint;
        } catch (VcenterSystemException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("getHostCertificate exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private void trackHostTasks(HostSystem hostSystem, Integer timeout) throws VcenterSystemException {
        try {
            // Track and wait for other tasks, ie HA, initiated by the add before progressing
            String hostname = hostSystem.getName();
            int taskStartupWait = 5 * 1000;
            _log.info("Wait " + taskStartupWait + " seconds for any tasks to run against host before monitoring");
            Thread.sleep(taskStartupWait); // wait for post register tasks to begin

            Task[] tasks = hostSystem.getRecentTasks() == null ? new Task[0] : hostSystem.getRecentTasks();
            _log.info("Host " + hostname + " has " + tasks.length + " tasks currently running or queued to run");
            if (tasks.length > 0) {

                VcenterTaskMonitor taskMonitor = new VcenterTaskMonitor(timeout); // stateful since this instance used for ALL tasks -
                                                                                  // timeout is basically for ALL tasks to finish in
                for (Task task : tasks) {
                    TaskInfo taskInfo = task.getTaskInfo();
                    _log.info("Begin waiting on task " + taskInfo.getName() + " " + taskInfo.getDescriptionId());
                    taskMonitor.waitForTask(task); // call blocks until task reaches terminal state or timeout is met
                    _log.info("Quit waiting for task " + taskInfo.getName() + " " + taskInfo.getDescriptionId() + " in state "
                            + taskInfo.getState());
                }
                _log.info("Done waiting for tasks for host " + hostname);
                for (Task task : tasks) {
                    TaskInfo taskInfo = task.getTaskInfo();
                    _log.info("Host " + hostname + " task " + taskInfo.getName() + " " + taskInfo.getDescriptionId() + " in state "
                            + taskInfo.getState());
                }
            }
        } catch (Exception e) {
            _log.error("trackHostTasks exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private void reconnectHost(HostSystem hostSystem) throws VcenterSystemException {
        try {
            String hostname = hostSystem.getName();
            HostSystemConnectionState hostSystemConnectionState = hostSystem.getRuntime().getConnectionState();
            if (hostSystemConnectionState == HostSystemConnectionState.disconnected
                    || hostSystemConnectionState == HostSystemConnectionState.notResponding) {
                Integer operationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_operation_timeout"));
                _log.info("Host " + hostname + " is in a " + hostSystemConnectionState + "state - Attempt to reconnect");
                Task reconnectHostTask = hostSystem.reconnectHost_Task(null); // might need to provide conn info, no arg should use
                                                                              // 'defaults' guessing means what it was registered originally
                                                                              // with
                VcenterTaskMonitor.TaskStatus taskStatus = (new VcenterTaskMonitor(operationTimeout)).monitor(reconnectHostTask); // wait
                                                                                                                                  // configured
                                                                                                                                  // timeout
                _log.info("After running reconnect task for host " + hostname + " the task result is " + taskStatus
                        + " and host is in connection state " + hostSystem.getRuntime().getConnectionState());
            }
        } catch (Exception e) {
            _log.error("reconnectHost exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public void enterMaintenanceMode(String datacenterName, String clusterNameOrMoRef, String hostname) throws VcenterSystemException,
            VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to enter maintenance mode for host " + hostname + " in datacenter " + datacenterName + " cluster "
                    + clusterNameOrMoRef);
            enterMaintenanceModeHost((HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get(
                    "HostSystem"));
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("enterMaintenanceMode exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private void enterMaintenanceModeHost(HostSystem hostSystem) throws VcenterSystemException {
        try {
            String hostname = hostSystem.getName();
            if (!hostSystem.getRuntime().isInMaintenanceMode()) {
                Integer operationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_operation_timeout"));
                _log.info("Host " + hostname + " is not in maintenance mode - Attempt to enter");
                Task enterMaintenanceModeTask = hostSystem.enterMaintenanceMode(operationTimeout, true);
                VcenterTaskMonitor taskMonitor = new VcenterTaskMonitor(operationTimeout);
                VcenterTaskMonitor.TaskStatus taskStatus = taskMonitor.monitor(enterMaintenanceModeTask);
                _log.info("After running enter maintenance mode task for host " + hostname + " the task result is "
                        + taskStatus + " and host is in maintenance mode "
                        + hostSystem.getRuntime().isInMaintenanceMode());
                if (!hostSystem.getRuntime().isInMaintenanceMode()) {
                    _log.error("Host " + hostname + " failed to enter maintenance mode");
                    String message = "Host " + hostname + " failed to enter maintenance mode. "
                            + "Safety check for running virtual machines failed. "
                            + "Vcenter was either unable to migrate them or ViPR could not verify VMs are not running.";
                    if (taskStatus == VcenterTaskMonitor.TaskStatus.ERROR) {
                        throw new VcenterSystemException(message + " Vcenter reported: " + taskMonitor.errorDescription);
                    }
                    else if (taskStatus == VcenterTaskMonitor.TaskStatus.TIMED_OUT) {
                        throw new VcenterSystemException(message + " Task timed out.");
                    }
                    else { // task success but host did not enter maint mode - probably will not happen
                        throw new VcenterSystemException(message);
                    }
                }
            }
        } catch (VcenterSystemException e) {
            throw e;
        } catch (Exception e) {
            _log.error("enterMaintenanceMode exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public void exitMaintenanceMode(String datacenterName, String clusterNameOrMoRef, String hostname) throws VcenterSystemException,
            VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to exit maintenance mode for host " + hostname + " in datacenter " + datacenterName + " cluster "
                    + clusterNameOrMoRef);
            exitMaintenanceModeHost((HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get(
                    "HostSystem"));
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("exitMaintenanceMode exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private void exitMaintenanceModeHost(HostSystem hostSystem) throws VcenterSystemException {
        try {
            String hostname = hostSystem.getName();
            if (hostSystem.getRuntime().isInMaintenanceMode()) {
                Integer operationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_operation_timeout"));
                _log.info("Host " + hostname + " is in maintenance mode - Attempt to exit");
                Task exitMaintenanceModeTask = hostSystem.exitMaintenanceMode(operationTimeout);
                VcenterTaskMonitor.TaskStatus taskStatus = (new VcenterTaskMonitor(operationTimeout)).monitor(exitMaintenanceModeTask);
                _log.info("After running exit maintenance mode task for host " + hostname + " the task result is " + taskStatus
                        + " and host is in maintenance mode " + hostSystem.getRuntime().isInMaintenanceMode());
                if (hostSystem.getRuntime().isInMaintenanceMode()) {
                    _log.error("Host " + hostname + " failed to exit maintenance mode");
                    throw new VcenterSystemException("Host " + hostname + " failed to exit maintenance mode");
                }
            }
        } catch (VcenterSystemException e) {
            throw e;
        } catch (Exception e) {
            _log.error("exitMaintenanceMode exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public void removeHost(String datacenterName, String clusterNameOrMoRef, String hostname) throws VcenterSystemException,
            VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to remove host " + hostname + " to datacenter " + datacenterName + " cluster " + clusterNameOrMoRef);

            ClusterComputeResource clusterComputeResource = (ClusterComputeResource) createManagedEntityMap(datacenterName,
                    clusterNameOrMoRef, null, false).get("ClusterComputeResource");
            HostSystem hostSystem = findByHostname(clusterComputeResource, hostname);
            if (hostSystem == null) {
                _log.info("Host not found thus no delete necessary");
            } else {
                // Check that host can be removed (either in maintenance mode or in a !poweredOn or !connected state
                try {
                    checkHostConnectedPoweredOn(hostSystem);
                    _log.info("Host " + hostname + " connected and powered on so now check maintenance mode");
                    if (!hostSystem.getRuntime().isInMaintenanceMode()) {
                        _log.error("Host " + hostname + " must be in maintenance mode before deletion");
                        throw new VcenterSystemException("Host " + hostname + " must be in maintenance mode before deletion");
                    }
                } catch (VcenterObjectConnectionException e) {
                    _log.info("Host is not connected and/or powered on so go ahead and remove without maintenance mode check");
                }

                // Remove
                Integer hostOperationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_host_operation_timeout"));
                VcenterTaskMonitor taskMonitor = new VcenterTaskMonitor(hostOperationTimeout);
                Task deleteHostTask = hostSystem.destroy_Task();
                VcenterTaskMonitor.TaskStatus taskStatus = taskMonitor.monitor(deleteHostTask); // call blocks
                if (taskStatus == VcenterTaskMonitor.TaskStatus.SUCCESS) {
                    _log.info("Delete host " + hostname + " task succeeded");
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.ERROR) {
                    String errorMessage = "Delete host " + hostname + " task failed - " + taskMonitor.errorDescription;
                    _log.error(errorMessage);
                    throw new VcenterSystemException(errorMessage);
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.TIMED_OUT) {
                    _log.error("Delete host " + hostname + " task timed out at " + taskMonitor.progressPercent);
                    throw new VcenterSystemException("Delete host " + hostname + " task timed out at " + taskMonitor.progressPercent);
                } else { // Should not execute - Just here in case someone ever added a new state so we catch it
                    _log.error("Unknown task status encountered tracking delete host " + taskStatus);
                    throw new VcenterSystemException("Unknown task status encountered tracking delete host " + taskStatus);
                }
            }
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("Exception removing host: " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public String addHost(String datacenterName, String clusterNameOrMoRef, String hostname, String username, String password)
            throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to add host " + hostname + " to datacenter " + datacenterName + " cluster " + clusterNameOrMoRef);

            ClusterComputeResource clusterComputeResource = (ClusterComputeResource) createManagedEntityMap(datacenterName,
                    clusterNameOrMoRef, null, false).get("ClusterComputeResource");
            HostSystem hostSystem = findByHostname(clusterComputeResource, hostname);
            if (hostSystem == null) {
                _log.info("Host " + hostname + " does not exist and will be added");
                if (username == null || username.trim().equals("") || password == null || password.trim().equals("")) {
                    _log.error("Username and/or password missing - Both required to add host to cluster");
                    throw new VcenterSystemException("Username and/or password missing - Both required to add host to cluster");
                }
                HostConnectSpec hostConnectSpec = new HostConnectSpec();
                hostConnectSpec.setHostName(hostname);
                hostConnectSpec.setUserName(username);
                hostConnectSpec.setPassword(password);
                hostConnectSpec.setSslThumbprint(getHostCertificate(hostname, username, password)); // ie
                                                                                                    // 1D:0C:63:FC:58:58:1C:66:F0:5B:C4:0B:F3:84:0E:27:E9:59:83:F7

                _log.info("Attempt to add host " + hostname + " to " + datacenterName + "/" + clusterComputeResource.getName());
                Integer hostOperationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_host_operation_timeout"));
                Integer hostOperationTimeoutMillis = hostOperationTimeout * 1000;
                Integer retryCount = 1;
                Long startTimeMillis = System.currentTimeMillis();
                Long cutoffTimeMillis = startTimeMillis + hostOperationTimeoutMillis;

                VcenterTaskMonitor taskMonitor = new VcenterTaskMonitor(hostOperationTimeout);
                Task addHostTask = clusterComputeResource.addHost_Task(hostConnectSpec, true, null, null);
                VcenterTaskMonitor.TaskStatus taskStatus = taskMonitor.monitor(addHostTask); // call blocks
                while ((System.currentTimeMillis() < cutoffTimeMillis) && taskStatus == VcenterTaskMonitor.TaskStatus.ERROR) {
                    _log.info("Add host " + hostname + " retry error " + taskMonitor.errorDescription + " count " + retryCount);
                    Thread.sleep(60000); // Retry is time based and if each retry executes very quickly (ie milliseconds) then we should
                                         // throttle and only retry every 60 seconds
                    addHostTask = clusterComputeResource.addHost_Task(hostConnectSpec, true, null, null);
                    taskStatus = taskMonitor.monitor(addHostTask); // call blocks
                    retryCount++;
                }

                if (taskStatus == VcenterTaskMonitor.TaskStatus.SUCCESS) {
                    _log.info("Add host " + hostname + " task succeeded - Attempt to find host in cluster");
                    hostSystem = findByHostname(clusterComputeResource, hostname);
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.ERROR) {
                    String errorMessage = "Add host " + hostname + " task failed - " + taskMonitor.errorDescription;
                    if (taskMonitor.errorDescription.contains("already exists.")) {
                        errorMessage += " - Ensure adding host to correct cluster or remove host from its existing cluster";
                    }
                    _log.error(errorMessage);
                    throw new VcenterSystemException(errorMessage);
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.TIMED_OUT) {
                    _log.error("Add host " + hostname + " task timed out at " + taskMonitor.progressPercent);
                    throw new VcenterSystemException("Add host " + hostname + " task timed out at " + taskMonitor.progressPercent);
                } else { // Should not execute - Just here in case someone ever added a new state so we catch it
                    _log.error("Unknown task status encountered tracking add host " + taskStatus);
                    throw new VcenterSystemException("Unknown task status encountered tracking add host " + taskStatus);
                }

                trackHostTasks(hostSystem, hostOperationTimeout);
                // Only take host out of maintenance mode if it's being added. We don't want to exit maintenance mode on other hosts since
                // customer may have intentionally put it on that.
                exitMaintenanceModeHost(hostSystem);
            }

            // Some nice conveniences to reconnect and exit maintenance mode to ready the host for action
            reconnectHost(hostSystem);

            // Collect some details
            StringBuffer hostDetails = new StringBuffer();
            hostDetails.append("Host ").append(datacenterName).append("/").append(clusterComputeResource.getName()).append("/")
                    .append(hostname).append(" ");
            String os = hostSystem.getPropertyByPath("config.product.version").toString();
            hostDetails.append("OS ").append(os).append(" ");
            String key = hostSystem.getMOR().getVal();
            hostDetails.append("key ").append(key).append(" ");
            String connectionState = hostSystem.getRuntime().getConnectionState().toString();
            hostDetails.append("Connection State ").append(connectionState).append(" ");
            String powerState = hostSystem.getRuntime().getPowerState().toString();
            hostDetails.append("Power State ").append(powerState).append(" ");
            boolean maintenanceMode = hostSystem.getRuntime().isInMaintenanceMode();
            hostDetails.append("Maintenance Mode ").append(maintenanceMode).append(" ");
            _log.info(hostDetails.toString());

            return hostSystem.getMOR().getVal();
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("Exception adding host: " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private List<String> toString(List<VirtualMachine> virtualMachines) {
        List<String> virtualMachineNames = new ArrayList<String>();
        if (virtualMachines == null || virtualMachines.isEmpty()) {
            return virtualMachineNames;
        }
        for (VirtualMachine virtualMachine : virtualMachines) {
            virtualMachineNames.add(virtualMachine.getName());
        }
        return virtualMachineNames;
    }

    private List<String> getRunningVirtualMachines(String datacenterName, String clusterNameOrMoRef, String hostname, boolean runningOnly)
            throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to get virtual machines for host " + hostname + " in datacenter " + datacenterName + " cluster "
                    + clusterNameOrMoRef);
            return toString(getVirtualMachines(
                    (HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get("HostSystem"), runningOnly));
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("getVirtualMachines host exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public List<String> getRunningVirtualMachines(String datacenterName, String clusterNameOrMoRef, String hostname)
            throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        return getRunningVirtualMachines(datacenterName, clusterNameOrMoRef, hostname, true);
    }

    public List<String> getVirtualMachines(String datacenterName, String clusterNameOrMoRef, String hostname)
            throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        return getRunningVirtualMachines(datacenterName, clusterNameOrMoRef, hostname, false);
    }

    private List<String> getVirtualMachines(String datacenterName, String clusterNameOrMoRef, boolean runningOnly)
            throws VcenterSystemException, VcenterObjectNotFoundException {
        try {
            _log.info("Request to get virtual machines for cluster " + clusterNameOrMoRef + " in datacenter " + datacenterName);
            return toString(getVirtualMachines(
                    (ClusterComputeResource) createManagedEntityMap(datacenterName, clusterNameOrMoRef, null, false).get(
                            "ClusterComputeResource"), runningOnly));
        } catch (VcenterSystemException | VcenterObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            _log.error("getVirtualMachines cluster exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public List<String> getVirtualMachines(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException {
        return getVirtualMachines(datacenterName, clusterNameOrMoRef, false);
    }

    public List<String> getRunningVirtualMachines(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException {
        return getVirtualMachines(datacenterName, clusterNameOrMoRef, true);
    }

    private String toDetails(VirtualMachine virtualMachine) {
        // Collect some details
        StringBuffer vmDetails = new StringBuffer();
        String name = virtualMachine.getName();
        vmDetails.append("Name ").append(name);
        String key = virtualMachine.getMOR().getVal();
        vmDetails.append("Key ").append(key);
        String powerState = virtualMachine.getRuntime().getPowerState().toString();
        vmDetails.append("Power State ").append(powerState);
        boolean template = virtualMachine.getConfig().isTemplate();
        vmDetails.append("Template ").append(template);
        return vmDetails.toString();
    }

    private List<VirtualMachine> getVirtualMachines(ClusterComputeResource clusterComputeResource, boolean runningOnly)
            throws VcenterSystemException {
        try {
            List<VirtualMachine> virtualMachines = new ArrayList<VirtualMachine>();

            String clusterName = clusterComputeResource.getName();
            _log.info("Inspect cluster " + clusterName + " for virtual machines running only " + runningOnly);
            ResourcePool resourcePool = clusterComputeResource.getResourcePool();
            if (resourcePool != null) {
                _log.info("Inspect resource pool " + resourcePool.getName() + " for virtual machines");
                if (resourcePool.getVMs() != null) {
                    for (VirtualMachine virtualMachine : resourcePool.getVMs()) {
                        _log.info("Found virtual machine " + virtualMachine.getName() + " in resource pool " + resourcePool.getName());
                        _log.info(toDetails(virtualMachine));
                        if (runningOnly) { // Anything !poweredOff (poweredOn and suspended) will be considered running
                            if (!virtualMachine.getRuntime().getPowerState().equals(VirtualMachinePowerState.poweredOff)) {
                                virtualMachines.add(virtualMachine);
                            }
                        } else {
                            virtualMachines.add(virtualMachine);
                        }
                    }
                }
            }
            _log.info("Cluster " + clusterName + " has " + virtualMachines.size() + " virtual machines");
            return virtualMachines;
        } catch (Exception e) {
            _log.error("getVirtualMachines clusterComputeResource exception " + e);
            throw new VcenterSystemException("Error checking cluster for virtual machines");
        }
    }

    private List<VirtualMachine> getVirtualMachines(HostSystem hostSystem, boolean runningOnly) throws VcenterSystemException {
        try {
            String hostname = hostSystem.getName();
            _log.info("Inspect host " + hostname + " for virtual machines running only " + runningOnly);
            List<VirtualMachine> virtualMachines = new ArrayList<VirtualMachine>();
            VirtualMachine[] hostVirtualMachines = hostSystem.getVms();
            for (VirtualMachine virtualMachine : hostVirtualMachines) {
                _log.info("Found virtual machine " + virtualMachine.getName() + " on host " + hostname);
                _log.info(toDetails(virtualMachine));
                if (runningOnly) { // Anything !poweredOff (poweredOn and suspended) will be considered running
                    if (!virtualMachine.getRuntime().getPowerState().equals(VirtualMachinePowerState.poweredOff)) {
                        virtualMachines.add(virtualMachine);
                    }
                } else {
                    virtualMachines.add(virtualMachine);
                }
            }
            _log.info("Host " + hostname + " has " + virtualMachines.size() + " virtual machines");
            return virtualMachines;
        } catch (Exception e) {
            _log.error("getVirtualMachines hostSystem exception " + e);
            throw new VcenterSystemException("Error checking host for virtual machines");
        }
    }

    public void checkHostConnectedPoweredOn(String datacenterName, String clusterNameOrMoRef, String hostname)
            throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to check connected and powered on for host " + hostname + " in datacenter " + datacenterName + " cluster "
                    + clusterNameOrMoRef);
            HostSystem hostSystem = (HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get(
                    "HostSystem"); // checkHostConnectedPoweredOn is performed in this call
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("checkHostConnectedPoweredOn exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    private void checkHostConnectedPoweredOn(HostSystem hostSystem) throws VcenterObjectConnectionException {
        String hostname = hostSystem.getName();
        if (hostSystem.getRuntime().getConnectionState() != HostSystemConnectionState.connected
                && hostSystem.getRuntime().getPowerState() != HostSystemPowerState.poweredOn) {
            _log.error("Host " + hostname + " is not connected and powered on");
            throw new VcenterObjectConnectionException("Host " + hostname + " is not connected and powered on");
        }
    }

    public void refreshRescanHostStorage(String datacenterName, String clusterNameOrMoRef, String hostname) throws VcenterSystemException,
            VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to check connected and powered on for host " + hostname + " in datacenter " + datacenterName + " cluster "
                    + clusterNameOrMoRef);
            HostSystem hostSystem = (HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get(
                    "HostSystem");
            _log.info("Refresh and rescan storage before disk discovery on host " + hostname);
            hostSystem.getHostStorageSystem().rescanAllHba(); // expensive but needed to pickup any storage changes
            _log.info("Rescan HBAs complete");
            hostSystem.getHostStorageSystem().refreshStorageSystem();
            _log.info("Refresh storage system complete");
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("refreshRescanStorage exception " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public String
            createDatastore(String datacenterName, String clusterNameOrMoRef, String hostname, String volumeUuid, String datastoreName)
                    throws VcenterSystemException, VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to create datastore on volume " + volumeUuid + " host " + hostname + " to datacenter " + datacenterName
                    + " cluster " + clusterNameOrMoRef);

            HostSystem hostSystem = (HostSystem) createManagedEntityMap(datacenterName, clusterNameOrMoRef, hostname, true).get(
                    "HostSystem");

            if (volumeUuid == null || volumeUuid.trim().equals("")) {
                _log.error("Volume UUID not specified");
                throw new VcenterSystemException("Volume UUID not specified");
            }

            Datastore[] datastores = hostSystem.getDatastores();
            if (datastores != null && datastores.length > 0) {
                _log.info("Check host " + hostname + " for existing datastore on volume " + volumeUuid);

                String specifiedVolumeDevicePath = null;
                HostStorageSystem hostStorageSystem = hostSystem.getHostStorageSystem();
                HostStorageDeviceInfo hostStorageDeviceInfo = hostStorageSystem.getStorageDeviceInfo();
                ScsiLun[] hostScsiLuns = hostStorageDeviceInfo.getScsiLun();
                for (ScsiLun scsiLun : hostScsiLuns) {
                    if (scsiLun instanceof HostScsiDisk) {
                        HostScsiDisk hostScsiDisk = (HostScsiDisk) scsiLun;
                        if (hostScsiDisk.getUuid().toLowerCase().contains(volumeUuid.toLowerCase())) {
                            _log.info("Found disk " + hostScsiDisk.getUuid() + " on " + hostname + " for volume UUID " + volumeUuid);
                            specifiedVolumeDevicePath = hostScsiDisk.getDevicePath();
                            break;
                        }
                    }
                }

                // We found the device path for the volume specified by the UUID above. Now possibly map that to a datastore to check if
                // datastore already exists.
                if (specifiedVolumeDevicePath != null) {
                    for (Datastore datastore : datastores) {
                        if (datastore.getInfo() instanceof VmfsDatastoreInfo) {
                            VmfsDatastoreInfo vmfsDatastoreInfo = (VmfsDatastoreInfo) datastore.getInfo();
                            _log.info("Found datastore " + vmfsDatastoreInfo.getName() + " " + vmfsDatastoreInfo.getVmfs().getUuid());
                            String diskName = vmfsDatastoreInfo.getVmfs().getExtent()[0].getDiskName();
                            _log.info("Found datastore " + vmfsDatastoreInfo.getName() + " on disk " + diskName);
                            String devicePath = "/vmfs/devices/disks/" + diskName;
                            if (devicePath.equalsIgnoreCase(specifiedVolumeDevicePath)) {
                                _log.info("Datastore " + vmfsDatastoreInfo.getName() + " " + devicePath + " " + datastore.getMOR().getVal()
                                        + " already present");
                                return datastore.getMOR().getVal();
                            }
                        }
                    }
                }
            }

            _log.info("Search for candidate disk via host " + hostname);
            HostDatastoreSystem hostDatastoreSystem = hostSystem.getHostDatastoreSystem();
            HostScsiDisk[] hostScsiDisks = hostDatastoreSystem.queryAvailableDisksForVmfs(null);
            HostScsiDisk candidateHostScsiDisk = null;
            for (HostScsiDisk hostScsiDisk : hostScsiDisks) {
                _log.info("Found disk " + hostScsiDisk.getDevicePath() + " " + hostScsiDisk.getUuid());
                if (hostScsiDisk.getUuid().toLowerCase().contains(volumeUuid.toLowerCase())) {
                    candidateHostScsiDisk = hostScsiDisk;
                    break;
                }
            }
            if (candidateHostScsiDisk == null) {
                _log.error("Disk " + volumeUuid + " not found - Ensure underlying storage properly configured and disk accessible to host");
                throw new VcenterSystemException("Disk " + volumeUuid
                        + " not found - Ensure underlying storage properly configured and disk accessible to host");
            }

            String devicePath = candidateHostScsiDisk.getDevicePath();
            _log.info("Create datastore via host " + hostname + " on disk " + devicePath);
            VmfsDatastoreOption[] vmfsDatastoreOption = hostDatastoreSystem.queryVmfsDatastoreCreateOptions(devicePath);
            VmfsDatastoreCreateSpec vmfsDatastoreCreateSpec = (VmfsDatastoreCreateSpec) vmfsDatastoreOption[0].getSpec();
            vmfsDatastoreCreateSpec.getVmfs().setVolumeName(datastoreName);
            vmfsDatastoreCreateSpec.getVmfs().setBlockSizeMb(1); // TODO externalize
            Datastore datastore = null;
            try {
                datastore = hostDatastoreSystem.createVmfsDatastore(vmfsDatastoreCreateSpec);
            } catch (HostConfigFault hcf) {
                _log.info("HostConfigFault creating datastore on disk " + devicePath + " thus retry");
                if (hcf.getFaultMessage() != null && hcf.getFaultMessage().length > 0 && hcf.getFaultMessage()[0] != null) {
                    String errorMessage = hcf.getFaultMessage()[0].toString();
                    _log.error("HostConfigFault details are " + errorMessage);
                }
                datastore = hostDatastoreSystem.createVmfsDatastore(vmfsDatastoreCreateSpec);
            }
            if (datastore == null) { // Should not happen
                _log.error("Datastore null after create");
                throw new VcenterSystemException("Error creating datastore");
            }
            return datastore.getMOR().getVal();
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("Exception creating datastore: " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }

    public void removeCluster(String datacenterName, String clusterNameOrMoRef) throws VcenterSystemException,
            VcenterObjectNotFoundException, VcenterObjectConnectionException {
        try {
            _log.info("Request to remove cluster in datacenter " + datacenterName + " cluster " + clusterNameOrMoRef);

            try {
                ClusterComputeResource clusterComputeResource = (ClusterComputeResource) createManagedEntityMap(datacenterName,
                        clusterNameOrMoRef, null, false).get("ClusterComputeResource");
                String clusterName = clusterComputeResource.getName();
                _log.info("Attempt to delete cluster " + clusterName);
                // Remove
                Integer clusterOperationTimeout = Integer.parseInt(propertyInfo.getProperty("vcenter_operation_timeout"));
                VcenterTaskMonitor taskMonitor = new VcenterTaskMonitor(clusterOperationTimeout);
                Task deleteClusterTask = clusterComputeResource.destroy_Task();
                VcenterTaskMonitor.TaskStatus taskStatus = taskMonitor.monitor(deleteClusterTask); // call blocks
                if (taskStatus == VcenterTaskMonitor.TaskStatus.SUCCESS) {
                    _log.info("Delete cluster " + clusterName + " task succeeded");
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.ERROR) {
                    String errorMessage = "Delete cluster " + clusterName + " task failed - " + taskMonitor.errorDescription;
                    _log.error(errorMessage);
                    throw new VcenterSystemException(errorMessage);
                } else if (taskStatus == VcenterTaskMonitor.TaskStatus.TIMED_OUT) {
                    _log.error("Delete cluster " + clusterName + " task timed out at " + taskMonitor.progressPercent);
                    throw new VcenterSystemException("Delete cluster " + clusterName + " task timed out at " + taskMonitor.progressPercent);
                } else { // Should not execute - Just here in case someone ever added a new state so we catch it
                    _log.error("Unknown task status encountered tracking delete cluster " + taskStatus);
                    throw new VcenterSystemException("Unknown task status encountered tracking delete cluster " + taskStatus);
                }
            } catch (VcenterObjectNotFoundException e) {
                _log.info("Cluster not found thus no delete necessary");
            }
        } catch (VcenterSystemException | VcenterObjectNotFoundException | VcenterObjectConnectionException e) {
            throw e;
        } catch (Exception e) {
            _log.error("Exception removing cluster: " + e);
            throw new VcenterSystemException(e.getLocalizedMessage());
        }
    }
}
