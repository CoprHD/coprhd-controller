/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.PORT_GROUP;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportBlockVolumeHelper;
import com.emc.sa.service.vipr.compute.ComputeUtils.FqdnTable;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.google.common.collect.ImmutableList;

@Service("AddBareMetalHostToCluster")
public class AddBareMetalHostToClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(PROJECT)
    protected URI project;

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

    @Bindable(itemType = FqdnTable.class)
    protected FqdnTable[] fqdnValues;

    private Cluster cluster;
    private List<String> hostNames = null;
    private List<String> copyOfHostNames = null;

    @Override
    public void precheck() throws Exception {

        StringBuilder preCheckErrors = new StringBuilder();
        hostNames = ComputeUtils.getHostNamesFromFqdn(fqdnValues);
        copyOfHostNames = ImmutableList.copyOf(hostNames);

        List<String> existingHostNames = ComputeUtils.getHostNamesByName(getClient(), hostNames);
        cluster = BlockStorageUtils.getCluster(clusterId);
        List<String> hostNamesInCluster = ComputeUtils.findHostNamesInCluster(cluster);

        if (cluster == null) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.no.cluster.exists"));
        }
        acquireClusterLock(cluster);

        if (hostNames == null || hostNames.isEmpty()) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.osinstall.host.required") + "  ");
        }

        // Check for validity of host names and host Ips
        for (String hostName : hostNames) {
            if (!ComputeUtils.isValidHostIdentifier(hostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hostname.invalid", hostName) + "  ");
            }
        }

        if (hostNamesInCluster != null && !hostNamesInCluster.isEmpty() && !existingHostNames.isEmpty()
                && hostNamesInCluster.containsAll(existingHostNames)) {
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
                virtualArray, size, (hostNames.size() - existingHostNames.size()))) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.storage.capacity") + "  ");
        }

        if (!ComputeUtils.isComputePoolCapacityAvailable(getClient(), computeVirtualPool,
                (hostNames.size() - existingHostNames.size()))) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.compute.capacity") + "  ");
        }

        for (String existingHostName : existingHostNames) {
            if (!hostNamesInCluster.contains(existingHostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hosts.exists.elsewhere",
                                existingHostName) + "  ");
            }
        }

        ComputeVirtualPoolRestRep cvp = ComputeUtils.getComputeVirtualPool(getClient(), computeVirtualPool);
        if (cvp.getServiceProfileTemplates().isEmpty()) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.service.profile.templates.null", cvp.getName()) + "  ");
        }
        //TODO COP-28922 Can we add a check to see if the blades and the templates match?
        // e.g. the blades can be from multiple UCS clusters

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString() + 
                    ComputeUtils.getContextErrors(getModelClient()));
        }
        
        ExportBlockVolumeHelper.precheckPortGroupParameter(virtualPool, clusterId, project, virtualArray, portGroup, getClient());
    }

    @Override
    public void execute() throws Exception {

        hostNames = ComputeUtils.removeExistingHosts(hostNames, cluster);

        List<Host> hosts = ComputeUtils.createHosts(cluster, computeVirtualPool, hostNames, virtualArray);
        for (Host host : hosts) {
            acquireHostLock(host, cluster);
        }
        logInfo("compute.cluster.hosts.created", ComputeUtils.nonNull(hosts).size());

        Map<Host, URI> hostToBootVolumeIdMap = ComputeUtils.makeBootVolumes(project, virtualArray, virtualPool, size, hosts,
                getClient());
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
}
