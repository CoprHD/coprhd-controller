/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.PORT_GROUP;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SERVICE_PROFILE_TEMPLATE;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.util.ArrayUtil.safeArrayCopy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.compute.ComputeUtils.FqdnTable;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.google.common.collect.ImmutableList;

@Service("CreateBareMetalCluster")
public class CreateBareMetalClusterService extends ViPRService {

    @Param(PROJECT)
    protected URI project;

    @Param(NAME)
    protected String name;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(COMPUTE_VIRTUAL_POOL)
    protected URI computeVirtualPool;

    @Param(SIZE_IN_GB)
    protected Double size;

    @Param(value = HLU, required = false)
    protected Integer hlu;
    
    @Param(value = PORT_GROUP, required = false)
    protected URI portGroup;

    @Param(value = SERVICE_PROFILE_TEMPLATE, required = false)
    protected URI serviceProfileTemplate;

    @Bindable(itemType = FqdnTable.class)
    protected FqdnTable[] fqdnValues;

    private Cluster cluster = null;
    private List<String> hostNames = null;
    private List<String> copyOfHostNames = null;


    @Override
    public void precheck() throws Exception {

        StringBuilder preCheckErrors = new StringBuilder();
        hostNames = ComputeUtils.getHostNamesFromFqdn(fqdnValues);
        copyOfHostNames = ImmutableList.copyOf(hostNames);

        List<String> existingHostNames = ComputeUtils.getHostNamesByName(getClient(), hostNames);
        cluster = ComputeUtils.getCluster(name);
        List<String> hostNamesInCluster = ComputeUtils.findHostNamesInCluster(cluster);

        if ((cluster != null) && hostNamesInCluster.isEmpty()) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.empty.cluster.exists"));
        }

        if ((cluster != null) && !hostNames.containsAll(hostNamesInCluster)) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.unknown.host"));
        }

        if (hostNames == null || hostNames.isEmpty()) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.baremetal.hostname.required") + "  ");
        }

        // Check for validity of host names and host Ips
        for (String hostName : hostNames) {
            if (!ComputeUtils.isValidHostIdentifier(hostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hostname.invalid", hostName) + "  ");
            }
        }

        if (hostNames != null && !hostNames.isEmpty() && !existingHostNames.isEmpty() &&
                hostNamesInCluster.containsAll(existingHostNames) && (hostNames.size() == hostNamesInCluster.size())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.host.already.in.cluster") + "  ");
        }

        if (hostNamesInCluster != null && !hostNamesInCluster.isEmpty() && !existingHostNames.isEmpty()) {
             for (String hostName : hostNamesInCluster) {
                if (existingHostNames.contains(hostName)){
                    preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.hostname.already.in.cluster", hostName) + "  ");
                }
             }
        }

        if (!ComputeUtils.isCapacityAvailable(getClient(), virtualPool,
                virtualArray, size, hostNames.size() - existingHostNames.size())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.storage.capacity") + "  ");
        }

        preCheckErrors = ComputeUtils.verifyComputePoolCapacityAvailable(getClient(), computeVirtualPool,
                hostNames.size() - existingHostNames.size(),serviceProfileTemplate, virtualArray, preCheckErrors);

        for (String existingHostName : existingHostNames) {
            if (!hostNamesInCluster.contains(existingHostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hosts.exists.elsewhere",
                                existingHostName) + "  ");
            }
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString() + 
                    ComputeUtils.getContextErrors(getModelClient()));
        }
    }

    @Override
    public void execute() throws Exception {
        // acquire lock on compute system before start of provisioning.
        Map<URI, ComputeSystemRestRep> computeSystemMap = ComputeUtils.getComputeSystemsFromCVP(getClient(), computeVirtualPool);
        Map<URI, ComputeSystemRestRep> sortedMap = new TreeMap<URI, ComputeSystemRestRep>(computeSystemMap);
        Set<Entry<URI, ComputeSystemRestRep>> entrySet = sortedMap.entrySet();
        for (Entry<URI, ComputeSystemRestRep> entry : entrySet) {
            acquireComputeSystemLock(entry.getValue());
        }

        // Note: creates ordered lists of hosts, bootVolumes & exports
        // host[0] goes with bootVolume[0] and export[0], etc
        // elements are set to null if they fail

        if (cluster == null) {
            cluster = ComputeUtils.createCluster(name);
            logInfo("compute.cluster.created", name);
        } else {
            // If the hostName already exists, we remove it from the hostnames
            // list.
            hostNames = ComputeUtils.removeExistingHosts(hostNames, cluster);
        }
        acquireClusterLock(cluster);

        List<Host> hosts = ComputeUtils.createHosts(cluster, computeVirtualPool, hostNames, virtualArray, serviceProfileTemplate);
        for (Host host : hosts) {
            acquireHostLock(host, cluster);
        }

        logInfo("compute.cluster.hosts.created", ComputeUtils.nonNull(hosts).size());

        // release all locks on compute systems once host creation is done.
        for (Entry<URI, ComputeSystemRestRep> entry : entrySet) {
            releaseComputeSystemLock(entry.getValue());
        }

        Map<Host, URI> hostToBootVolumeIdMap = ComputeUtils.makeBootVolumes(project, virtualArray, virtualPool, size, hosts,
                getClient(), portGroup);
        logInfo("compute.cluster.boot.volumes.created", 
                hostToBootVolumeIdMap != null ? ComputeUtils.nonNull(hostToBootVolumeIdMap.values()).size() : 0);

        // Deactivate hosts with no boot volume, return list of hosts remaining.
        hostToBootVolumeIdMap = ComputeUtils.deactivateHostsWithNoBootVolume(hostToBootVolumeIdMap, cluster);

        // Export the boot volume, return a map of hosts and their EG IDs
        Map<Host, URI> hostToEgIdMap = ComputeUtils.exportBootVols(hostToBootVolumeIdMap, project, virtualArray, hlu, portGroup);
        logInfo("compute.cluster.exports.created", 
                hostToEgIdMap != null ? ComputeUtils.nonNull(hostToEgIdMap.values()).size(): 0);
        
        // Deactivate any hosts where the export failed, return list of hosts remaining
        hostToBootVolumeIdMap = ComputeUtils.deactivateHostsWithNoExport(hostToBootVolumeIdMap, hostToEgIdMap, cluster);
        
        // Set host boot volume ids and set san boot targets. 
        hosts = ComputeUtils.setHostBootVolumes(hostToBootVolumeIdMap, true);

        ComputeUtils.addHostsToCluster(hosts, cluster);
        hosts = ComputeUtils.deactivateHostsNotAddedToCluster(hosts, cluster);

        if (ComputeUtils.findHostNamesInCluster(cluster).isEmpty()) {
            logInfo("compute.cluster.removing.empty.cluster");
            ComputeUtils.deactivateCluster(cluster);
        }

        String orderErrors = ComputeUtils.getOrderErrors(cluster, copyOfHostNames, null, null);
        if (orderErrors.length() > 0) { // fail order so user can resubmit
            if (ComputeUtils.nonNull(hosts).isEmpty()) {
                throw new IllegalStateException(
                        ExecutionUtils.getMessage("compute.cluster.order.incomplete", orderErrors));
            } else {
                logError("compute.cluster.order.incomplete", orderErrors);
                setPartialSuccess();
            }
        }

    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    public URI getVirtualComputePool() {
        return computeVirtualPool;
    }

    public void setVirtualComputePool(URI computeVirtualPool) {
        this.computeVirtualPool = computeVirtualPool;
    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    public FqdnTable[] getFqdnValues() {
        return safeArrayCopy(fqdnValues);
    }

    public void setFqdnValues(FqdnTable[] fqdnValues) {
        this.fqdnValues = safeArrayCopy(fqdnValues);
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public List<String> getHostNames() {
        return hostNames;
    }

    public void setHostNames(List<String> hostNames) {
        this.hostNames = hostNames;
    }

    /**
     * @return the copyOfHostNames
     */
    public List<String> getCopyOfHostNames() {
        return copyOfHostNames;
    }

    /**
     * @param copyOfHostNames the copyOfHostNames to set
     */
    public void setCopyOfHostNames(List<String> copyOfHostNames) {
        this.copyOfHostNames = copyOfHostNames;
    }
}
