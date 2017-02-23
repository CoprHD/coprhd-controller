/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.getOrderTenant;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.sa.engine.ExecutionException;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.tasks.GetBlockResource;
import com.emc.sa.service.vipr.compute.tasks.AddHostToCluster;
import com.emc.sa.service.vipr.compute.tasks.CreateCluster;
import com.emc.sa.service.vipr.compute.tasks.CreateHosts;
import com.emc.sa.service.vipr.compute.tasks.CreateVcenterCluster;
import com.emc.sa.service.vipr.compute.tasks.DeactivateCluster;
import com.emc.sa.service.vipr.compute.tasks.DeactivateHost;
import com.emc.sa.service.vipr.compute.tasks.DeactivateHostNoWait;
import com.emc.sa.service.vipr.compute.tasks.FindCluster;
import com.emc.sa.service.vipr.compute.tasks.FindHostsInCluster;
import com.emc.sa.service.vipr.compute.tasks.FindVblockHostsInCluster;
import com.emc.sa.service.vipr.compute.tasks.InstallOs;
import com.emc.sa.service.vipr.compute.tasks.RemoveHostFromCluster;
import com.emc.sa.service.vipr.compute.tasks.SetBootVolume;
import com.emc.sa.service.vipr.compute.tasks.UpdateCluster;
import com.emc.sa.service.vipr.compute.tasks.UpdateVcenterCluster;
import com.emc.sa.service.vipr.tasks.GetHost;
import com.emc.sa.service.vmware.VMwareSupport;
import com.emc.sa.service.vmware.tasks.GetVcenter;
import com.emc.sa.service.vmware.tasks.GetVcenterDataCenter;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.NameIgnoreCaseFilter;
import com.emc.vipr.client.exceptions.TimeoutException;
import com.google.common.collect.Lists;
import com.vmware.vim25.mo.HostSystem;

// VBDU TODO COP-28437: In general, this module needs javadoc.  Many methods are using List objects and returning List objects that correspond to the incoming list that
// require the indexing in the list to be retained or use of "indexOf()" to find the right map entry, both of which is poor programming practice, so that needs 
// to be fixed as well.  It does not need to be fixed if the calling object literally doesn't care about the mapping between the incoming arg and the return object, 
// so each case needs to be investigated separately.  Good hints are when you see use of "indexOf()" that Maps should've been used.
//
// Then Javadoc all of the methods so future readers know what the intentions are.  Be clear about return types.
public class ComputeUtils {

    public static final URI nullConsistencyGroup = null;

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hostNamesIn->return List is poor programming practice.
    public static List<Host> createHosts(Cluster cluster, URI vcp, List<String> hostNamesIn,
            URI varray) throws Exception {

        // new hosts will be created with lower case hostNames. force it here so we can find host afterwards
        List<String> hostNames = Lists.newArrayList();
        for (String hostNameIn : hostNamesIn) {
            hostNames.add(hostNameIn != null ? hostNameIn.toLowerCase() : null);
        }

        Host[] hosts = new Host[hostNames.size()];
        Tasks<HostRestRep> tasks = null;
        List<String> hostsToDeactivate = Lists.newArrayList();
        try {
            tasks = execute(new CreateHosts(vcp, cluster.getId(), hostNames, varray));
        } catch (Exception e) {
            ExecutionUtils.currentContext().logError("computeutils.createhosts.failure",
                    e.getMessage());
        }
        // Some tasks could succeed while others could error out.
        // VBDU TODO: COP-28453, We will not be adding the host to the cluster until after the host is booted. The line
        // below will need to be removed and we should only rely on the returned task ids to determine which hosts were
        // successful.

        // VBDU TODO: COP-28453, We should only rely on the task resource id and not base it on the hostname. We should
        // not delete a host just based on the hostname in case there are duplicates.
        List<HostRestRep> hostsInCluster = ComputeUtils.getHostsInCluster(cluster.getId(), cluster.getLabel());
        Map<URI,String> hostDeactivateMap = new HashMap<URI, String>();
        List<String> succeededHosts = Lists.newArrayList();
        if ((tasks != null) && (tasks.getTasks() != null)) {
            for (Task<HostRestRep> task : tasks.getTasks()) {
                URI hostUri = task.getResourceId();
                addAffectedResource(hostUri);
                Host host = execute(new GetHost(hostUri));
                int hostIndex = hostNames.indexOf(host.getHostName());
                succeededHosts.add(host.getHostName());
                hosts[hostIndex] = host;
            }
            for (String hostName : hostNames) {
                if (!succeededHosts.contains(hostName)) {
                    hostsToDeactivate.add(hostName);
                }
            }

            for (HostRestRep hostRep : hostsInCluster) {
                if (hostsToDeactivate.contains(hostRep.getName())) {
                    hostDeactivateMap.put(hostRep.getId(), hostRep.getName());
                }
            }
        }
        else { // If all the hosts failed, then the tasks are returned as null.
            // In this case we need to deactivate all the hosts that we wanted to create.
            for (HostRestRep hostRep : hostsInCluster) {
                if (hostNames.contains(hostRep.getName())) {
                    hostDeactivateMap.put(hostRep.getId(), hostRep.getName());
                }
            }
        }

        for (Entry<URI, String> hostEntry : hostDeactivateMap.entrySet()){
            execute(new DeactivateHost(hostEntry.getKey(), hostEntry.getValue(), true));
        }
        return Arrays.asList(hosts);
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // names->return List is poor programming practice.
    public static List<String> getHostNamesByName(ViPRCoreClient client,
            List<String> names) {
        List<String> hostNames = Lists.newArrayList();
        if (names == null) {
            return Collections.emptyList();
        }
        for (String hostName : names) {
            NameIgnoreCaseFilter<HostRestRep> filter = new NameIgnoreCaseFilter<HostRestRep>(
                    hostName);
            List<HostRestRep> resp = client.hosts().getByTenant(
                    getOrderTenant(), filter);
            for (HostRestRep hostRestRep : resp) {
                hostNames.add(hostRestRep.getHostName());
            }
        }
        return hostNames;
    }

    public static Cluster createCluster(String clusterName) {
        ClusterRestRep clusterRestRep = execute(new CreateCluster(clusterName));
        return (clusterRestRep == null) ? null : BlockStorageUtils
                .getCluster(clusterRestRep.getId());
    }

    public static Cluster getCluster(String clusterName) {
        List<ClusterRestRep> clusters = execute(new FindCluster(clusterName));
        if ((clusters == null) || (clusters.isEmpty())) {
            return null;
        }
        if (clusters.size() > 1) {
            throw new IllegalStateException(new Throwable(
                    "Error.  More than one cluster for this user/tenant named : "
                            + clusterName));
        }
        return BlockStorageUtils.getCluster(clusters.get(0).getId());
    }

    public static List<String> findHostNamesInCluster(Cluster cluster) {
        if (cluster == null) {
            return Collections.emptyList();
        }
        List<HostRestRep> hostRestReps = execute(new FindHostsInCluster(cluster.getId(), cluster.getLabel()));
        List<String> hostNames = Lists.newArrayList();
        if (hostRestReps != null) {
            for (HostRestRep hostRestRep : hostRestReps) {
                hostNames.add(hostRestRep.getHostName());
            }
        }
        return hostNames;
    }

    public static Cluster addHostsToCluster(List<Host> hosts, Cluster cluster) {
        if ((hosts != null) && (cluster != null)) {
            for (Host host : hosts) {
                if (host != null) {
                    execute(new AddHostToCluster(host.getId(), cluster.getId()));
                }
            }
        }
        return cluster;
    }

    public static List<Host> removeHostsFromCluster(List<Host> hosts) {
        if (hosts != null) {
            for (Host host : hosts) {
                execute(new RemoveHostFromCluster(host.getId()));
            }
        }
        return hosts;
    }

    public static boolean deactivateCluster(Cluster cluster) {
        if (cluster != null) {
            try {
                execute(new DeactivateCluster(cluster));
                return true;
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatecluster.failure",
                        e.getMessage());
            }
        }
        return false;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hosts->return List is poor programming practice.
    public static List<URI> makeBootVolumes(URI project,
            URI virtualArray, URI virtualPool, Double size,
            List<Host> hosts, ViPRCoreClient client) {

        if (hosts == null) {
            return Lists.newArrayList();
        }
        ArrayList<Task<VolumeRestRep>> tasks = new ArrayList<>();
        ArrayList<String> volumeNames = new ArrayList<>();
        for (Host host : hosts) {
            if (host == null) {
                volumeNames.add(null);
                continue;
            }
            String volumeName = host.getHostName().replaceAll("[^A-Za-z0-9_]", "_").concat("_boot");
            while (!BlockStorageUtils.getVolumeByName(volumeName).isEmpty()) { // vol name used?
                if (volumeName.matches(".*_\\d+$")) { // incr suffix number
                    int volNumber = Integer.parseInt(volumeName.substring(volumeName.lastIndexOf("_") + 1));
                    volumeName = volumeName.replaceAll("_\\d+$", "_" + ++volNumber);
                }
                else {
                    volumeName = volumeName.concat("_0"); // add suffix number
                }
            }
            try {
                tasks.add(BlockStorageUtils.createVolumesByName(project, virtualArray,
                        virtualPool, size, nullConsistencyGroup, volumeName));  // does not wait for task
                volumeNames.add(volumeName);
            } catch (ExecutionException e) {
                String errorMessage = e.getMessage() == null ? "" : e.getMessage();
                ExecutionUtils.currentContext().logError("computeutils.makebootvolumes.failure",
                        host.getHostName(), errorMessage);
            }
        }

        // monitor tasks
        URI[] volumeIds = new URI[hosts.size()];
        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<VolumeRestRep> successfulTask : getSuccessfulTasks(tasks)) {
                URI volumeId = successfulTask.getResourceId();
                String taskResourceName = successfulTask.getResource().getName();
                int volNameIndex = volumeNames.indexOf(taskResourceName);
                volumeIds[volNameIndex] = volumeId;
                hosts.get(volNameIndex).setBootVolumeId(volumeId);
                addAffectedResource(volumeId);
                tasks.remove(successfulTask);
            }
            for (Task<VolumeRestRep> failedTask : getFailedTasks(tasks)) {
                String volumeName = failedTask.getResource().getName();
                String errorMessage = failedTask.getMessage() == null ? "" : failedTask.getMessage();
                ExecutionUtils.currentContext().logError("computeutils.makebootvolumes.createvolume.failure",
                        volumeName, errorMessage);
                tasks.remove(failedTask);
            }
        }
        return Arrays.asList(volumeIds);
    }

    public static <T> List<Task<T>> getSuccessfulTasks(List<Task<T>> tasks) {
        List<Task<T>> successfulTasks = new ArrayList<>();
        for (Task<T> task : tasks) {
            if (task.isComplete() && !task.isError()) {
                successfulTasks.add(task);
            }
        }
        return successfulTasks;
    }

    public static <T> List<Task<T>> getFailedTasks(List<Task<T>> tasks) {
        List<Task<T>> failedTasks = new ArrayList<>();
        for (Task<T> task : tasks) {
            if (task.isComplete() && task.isError()) {
                failedTasks.add(task);
            }
        }
        return failedTasks;
    }

    private static <T> void waitAndRefresh(List<Task<T>> tasks) {
        long t = 100;  // >0 to keep waitFor(t) from waiting until task completes
        for (Task<T> task : tasks) {
            try {
                task.waitFor(t); // internal polling interval overrides (typically ~10 secs)
            } catch (TimeoutException te) {
                // ignore timeout after polling interval
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.task.exception", e.getMessage());
            }
        }
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // volumeIds,hosts->return List is poor programming practice.
    public static List<URI> exportBootVols(List<URI> volumeIds, List<Host> hosts, URI project, URI virtualArray, Integer hlu) {

        if ((hosts == null) || (volumeIds == null)) {
            return Collections.emptyList();
        }

        List<Task<ExportGroupRestRep>> tasks = new ArrayList<>();
        for (int x = 0; x < hosts.size(); x++) {
            if ((volumeIds.get(x) != null) && (hosts.get(x) != null) && !(hosts.get(x).getInactive())) {
                try {
                    /**
                     * Don't determine HLUs at all, even for the boot volumes. Let the system decide them for you. Hence passing -1
                     */
                    Task<ExportGroupRestRep> task = BlockStorageUtils.createHostExportNoWait(project,
                            virtualArray, Arrays.asList(volumeIds.get(x)), hlu, hosts.get(x));
                    tasks.add(task);
                } catch (ExecutionException e) {
                    String errorMessage = e.getMessage() == null ? "" : e.getMessage();
                    ExecutionUtils.currentContext().logError("computeutils.exportbootvolumes.failure",
                            hosts.get(x).getHostName(), errorMessage);
                }
                ExecutionUtils.clearRollback(); // prevent exports from rolling back on exception
            }
        }

        // monitor tasks
        List<String> hostNames = Lists.newArrayList();
        for (Host host : hosts) {
            if (host != null) {
                hostNames.add(host.getHostName());
            }
            else {
                hostNames.add(null);
            }
        }

        URI[] exportIds = new URI[hosts.size()];
        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<ExportGroupRestRep> successfulTask : getSuccessfulTasks(tasks)) {
                URI exportId = successfulTask.getResourceId();
                addAffectedResource(exportId);
                String exportName = successfulTask.getResource().getName();
                int hostNameIndex = hostNames.indexOf(exportName); // export named after host
                exportIds[hostNameIndex] = exportId;
                tasks.remove(successfulTask);
            }
            for (Task<ExportGroupRestRep> failedTask : getFailedTasks(tasks)) {
                String errorMessage = failedTask.getMessage() == null ? "" : failedTask.getMessage();
                ExecutionUtils.currentContext().logError("computeutils.exportbootvolumes.failure",
                        failedTask.getResource().getName(), errorMessage);
                tasks.remove(failedTask);
            }
        }
        return Arrays.asList(exportIds);
    }

    protected static boolean isCapacityAvailable(ViPRCoreClient client,
            URI virtualPool, URI virtualArray, Double sizeOfBootVolumesInGb,
            Integer numVols) {
        // Check for pool capacity
        CapacityResponse capacityResponse = client.blockVpools()
                .getCapacityOnVirtualArray(virtualPool, virtualArray);
        String size = capacityResponse.getFreeGb();
        long freeCapacity = Long.parseLong(size);
        double reqSize = sizeOfBootVolumesInGb * numVols;
        long reqCapacity = (long) reqSize;

        if ((reqSize - reqCapacity) > 0) { // round up
            reqCapacity++;
        }
        return reqCapacity > freeCapacity ? false : true;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hosts->return List is poor programming practice.
    public static List<Host> deactivateHostsWithNoBootVolume(List<Host> hosts,
            List<URI> bootVolumeIds, Cluster cluster) {
        if (hosts == null) {
            return Lists.newArrayList();
        }
        List<Host> hostsToRemove = Lists.newArrayList();
        for (Host host : hosts) {
            if ((host != null) && (bootVolumeIds.get(hosts.indexOf(host)) == null)) {
                try {
                    execute(new RemoveHostFromCluster(host.getId()));
                } catch (Exception e) {
                    ExecutionUtils.currentContext().logError("computeutils.deactivatehost.failure",
                            host.getHostName(), e.getMessage());
                }
                hostsToRemove.add(host);
                host.setInactive(true);
            }
        }
        if (!hostsToRemove.isEmpty()) {
            try {
                deactivateHosts(hostsToRemove);
                for (Host hostRemoved : hostsToRemove) {
                    for (Host hostCreated : hosts) {
                        if(hostCreated.getId().equals(hostRemoved.getId())) {
                            hosts.set(hosts.indexOf(hostCreated), null);
                        }
                    }
                }
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        e.getMessage());
            }
        }
        return hosts;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hosts/exports->return List is poor programming practice.
    public static List<Host> deactivateHostsWithNoExport(List<Host> hosts,
            List<URI> exportIds, List<URI> bootVolumeIds, Cluster cluster) {
        if (hosts == null) {
            return Lists.newArrayList();
        }
        List<Host> hostsToRemove = Lists.newArrayList();
        List<URI> bootVolsToRemove = Lists.newArrayList();

        for (Host host : hosts) {
            if ((exportIds.get(hosts.indexOf(host)) == null) && (host != null)) {
                try {
                    execute(new RemoveHostFromCluster(host.getId()));
                } catch (Exception e) {
                    ExecutionUtils.currentContext().logError("computeutils.deactivatehost.noexport",
                            host.getHostName(), e.getMessage());
                }
                hostsToRemove.add(host);
                host.setInactive(true);
                // remove the corresponding boot volume of the host too, if the
                // boot volume export failed.
                bootVolsToRemove.add(bootVolumeIds.get(hosts.indexOf(host)));
            }
        }
        if (!hostsToRemove.isEmpty()) {
            try {
                deactivateHosts(hostsToRemove);
                for (Host hostRemoved : hostsToRemove) {
                    for (Host hostCreated : hosts) {
                        if(hostCreated.getId().equals(hostRemoved.getId())) {
                            hosts.set(hosts.indexOf(hostCreated), null);
                        }
                    }
                }
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        e.getMessage());
            }
        }
        // Cleanup all bootvolumes of the deactivated host so that we do not leave any unsed boot volumes.
        if (!bootVolsToRemove.isEmpty()) {
            try {
                BlockStorageUtils.deactivateVolumes(bootVolsToRemove, VolumeDeleteTypeEnum.FULL);
            }catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.bootvolume.deactivate.failure",
                        e.getMessage());
            }
        }
        return hosts;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hosts->return List is poor programming practice.
    public static List<Host> deactivateHosts(List<Host> hosts) {
        Map<URI, String> hostURIMap = new HashMap<URI, String>();
        for (Host host : hosts) {
            hostURIMap.put(host.getId(), host.getLabel());
        }
        List<URI> successfulHostURIs = deactivateHostURIs(hostURIMap);

        ListIterator<Host> hostItr = nonNull(hosts).listIterator();
        while (hostItr.hasNext()) {
            if (!successfulHostURIs.contains(hostItr.next().getId())) {
                hostItr.set(null);
            }
        }
        return hosts;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hostURIs->return List is poor programming practice.
    public static List<URI> deactivateHostURIs(Map<URI,String> hostURIs) {
        ArrayList<Task<HostRestRep>> tasks = new ArrayList<>();
        ExecutionUtils.currentContext().logInfo("computeutils.deactivatehost.inprogress", hostURIs.values());
        // monitor tasks
        List<URI> successfulHostIds = Lists.newArrayList();
        for (Entry<URI, String> hostentry : hostURIs.entrySet()) {
            tasks.add(execute(new DeactivateHostNoWait(hostentry.getKey(), hostentry.getValue(), true)));
        }
        List<String> removedHosts = Lists.newArrayList();
        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<HostRestRep> successfulTask : getSuccessfulTasks(tasks)) {
                successfulHostIds.add(successfulTask.getResourceId());
                addAffectedResource(successfulTask.getResourceId());
                removedHosts.add(hostURIs.get(successfulTask.getResourceId()));
                tasks.remove(successfulTask);
            }
            for (Task<HostRestRep> failedTask : getFailedTasks(tasks)) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        failedTask.getResource().getName(), failedTask.getMessage());
                // VBDU TODO: COP-28454 Why are we manipulating the task list here?
                tasks.remove(failedTask);
            }
        }
        ExecutionUtils.currentContext().logInfo("computeutils.deactivatehost.completed", removedHosts);
        return successfulHostIds;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hosts->osInstallParams and hosts/osInstallParams->return List is poor programming practice.
    public static List<HostRestRep> installOsOnHosts(List<HostRestRep> hosts, List<OsInstallParam> osInstallParams) {

        if ((hosts == null) || hosts.isEmpty()) {
            return Collections.emptyList();
        }

        // execute all tasks (no waiting)
        List<Task<HostRestRep>> tasks = Lists.newArrayList();
        for (HostRestRep host : hosts) {
            if (host != null) {
                int hostIndex = hosts.indexOf(host);
                if (hostIndex > (osInstallParams.size() - 1)) {
                    continue;
                }
                if (osInstallParams.get(hostIndex) == null) {
                    continue;
                }
                try {
                    tasks.add(execute(new InstallOs(host, osInstallParams.get(hostIndex))));
                } catch (Exception e) {
                    ExecutionUtils.currentContext().logError("computeutils.installOs.failure",
                            host.getId() + "  " + e.getMessage());
                }
            }
        }
        // monitor tasks
        List<URI> successfulHostIds = Lists.newArrayList();
        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<HostRestRep> successfulTask : getSuccessfulTasks(tasks)) {
                tasks.remove(successfulTask);
                URI hostId = successfulTask.getResource().getId();
                Host newHost = execute(new GetHost(hostId));
                if (newHost == null) {
                    ExecutionUtils.currentContext().logError("computeutils.installOs.installing.failure",
                            successfulTask.getResource().getName());
                }
                else {
                    ExecutionUtils.currentContext().logInfo("computeutils.installOs.success",
                            newHost.getHostName());
                    addAffectedResource(hostId);
                    successfulHostIds.add(hostId);
                }
            }
            for (Task<HostRestRep> failedTask : getFailedTasks(tasks)) {
                tasks.remove(failedTask);
                String errorMessage = failedTask.getMessage() == null ? "" : failedTask.getMessage();
                ExecutionUtils.currentContext().logError("computeutils.installOs.installing.failure.task",
                        failedTask.getResource().getName(), errorMessage);
            }
        }
        // remove failed hosts
        for (ListIterator<HostRestRep> itr = hosts.listIterator(); itr.hasNext();) {
            HostRestRep host = itr.next();
            // VBDU TODO: COP-28437, Removing list elements from incoming argument is poor programming practice.
            // Please clone list and return a new list of successfully installed host objects.
            if ((host != null) && !successfulHostIds.contains(host.getId())) {
                itr.set(null);
            }
        }

        return hosts;
    }

    public static List<URI> getHostURIsByCluster(ViPRCoreClient client, URI clusterId) {
        List<HostRestRep> resp = client.hosts().getByCluster(clusterId);
        List<URI> hostURIs = Lists.newArrayList();
        for (HostRestRep r : resp) {
            hostURIs.add(r.getId());
        }
        return hostURIs;
    }

    public static List<HostRestRep> getHostsInCluster(URI clusterId) {
        return execute(new FindHostsInCluster(clusterId));
    }

    /**
     * get hosts for a given cluster
     * @param clusterId cluster uri
     * @param clustername name of cluster
     * @return
     */
    public static List<HostRestRep> getHostsInCluster(URI clusterId, String clustername) {
        return execute(new FindHostsInCluster(clusterId, clustername));
    }

    static <T> List<T> nonNull(List<T> objectList) {
        List<T> objectListToReturn = new ArrayList<>();
        if (objectList != null) {
            for (T object : objectList) {
                if (object != null) {
                    objectListToReturn.add(object);
                }
            }
        }
        return objectListToReturn;
    }

    // VBDU TODO: COP-28437, These methods need to be rewritten to use maps. Assuming stable indexing of
    // hostNames->return List is poor programming practice.
    static List<String> removeExistingHosts(List<String> hostNames, Cluster cluster) {
        for (String hostNameFound : ComputeUtils.findHostNamesInCluster(cluster)) {
            for (int i = 0; i < hostNames.size(); i++) {
                String hostName = hostNames.get(i);
                if (hostNameFound.equals(hostName)) {
                    ExecutionUtils.currentContext().logWarn("computeutils.removeexistinghosts.warn", hostName);
                    hostNames.set(i, null);
                }
            }
        }
        return hostNames;
    }

    public static boolean createVcenterCluster(Cluster cluster, URI datacenter) {
        if ((cluster != null) && (datacenter != null)) {
            try {
                execute(new CreateVcenterCluster(cluster.getId(), datacenter));
            } catch (Exception e) {
                ExecutionUtils.getMessage("compute.cluster.vcenter.sync.failed", e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Method to invoke create vcenter cluster
     * @param cluster cluster object
     * @param datacenter datacenter object
     * @return
     */
    public static boolean createVcenterCluster(Cluster cluster, VcenterDataCenter datacenter) {
        if ((cluster != null) && (datacenter != null)) {
            try {
                execute(new CreateVcenterCluster(cluster, datacenter));
            } catch (Exception e) {
                ExecutionUtils.getMessage("compute.cluster.vcenter.sync.failed", e.getMessage());
                return false;
            }
        }
        return true;
    }

    public static boolean updateVcenterCluster(Cluster cluster, URI datacenter) {
        if ((cluster != null) && (datacenter != null)) {
            try {
                execute(new UpdateVcenterCluster(cluster.getId(), datacenter));
            } catch (Exception e) {
                ExecutionUtils.getMessage("compute.cluster.vcenter.sync.failed", e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Method to invoke update vcenter cluster
     * @param cluster cluster object
     * @param datacenter datacenter object
     * @return
     */
    public static boolean updateVcenterCluster(Cluster cluster, VcenterDataCenter datacenter) {
        if ((cluster != null) && (datacenter != null)) {
            try {
                execute(new UpdateVcenterCluster(cluster, datacenter));
            } catch (Exception e) {
                ExecutionUtils.getMessage("compute.cluster.vcenter.sync.failed", e.getMessage());
                return false;
            }
        }
        return true;
    }

    public static boolean isComputePoolCapacityAvailable(ViPRCoreClient client, URI poolURI, int numHosts) {
        ComputeVirtualPoolRestRep resp = client.computeVpools().getComputeVirtualPool(poolURI);
        int numAvailableBlades = resp.getAvailableMatchedComputeElements().size();
        return numAvailableBlades < numHosts ? false : true;
    }

    public static boolean isValidIpAddress(String ipAddress) {
        return EndpointUtility.isValidIpV4Address(ipAddress) || EndpointUtility.isValidIpV6Address(ipAddress);
    }

    public static boolean isValidHostIdentifier(String input) {
        return EndpointUtility.isValidHostName(input) || isValidIpAddress(input);
    }

    public static List<String> getHostNamesFromFqdnToIps(FqdnToIpTable[] fqdnToIps) {
        List<String> hostNames = new ArrayList<String>();
        for (FqdnToIpTable value : fqdnToIps) {
            hostNames.add(value.fqdns.toLowerCase()); // host controller will force lower case names anyway
        }
        return hostNames;
    }

    public static List<String> getIpsFromFqdnToIps(FqdnToIpTable[] fqdnToIps) {
        List<String> ips = new ArrayList<String>();
        for (FqdnToIpTable value : fqdnToIps) {
            ips.add(value.ips);
        }
        return ips;
    }

    public static String getOrderErrors(Cluster cluster,
            List<String> hostNames, URI computeImage, URI vcenterId) {
        StringBuilder orderErrors = new StringBuilder();

        List<HostRestRep> hosts = Lists.newArrayList();

        try {
            hosts = getHostsInCluster(cluster.getId(), cluster.getLabel());
        } catch (Exception e) {
            // catches if cluster was removed & marked for delete
            ExecutionUtils.currentContext().logError("compute.cluster.get.hosts.failed", e.getMessage());
        }

        List<String> hostNamesInCluster = Lists.newArrayList();
        for (HostRestRep host : hosts) {
            hostNamesInCluster.add(host.getName());
        }

        int numberOfFailedHosts = 0;
        for (String hostName : hostNames) {
            if (hostName != null && !hostNamesInCluster.contains(hostName)) {
                numberOfFailedHosts++;
            }
        }
        if (numberOfFailedHosts > 0) {
            orderErrors.append(ExecutionUtils.getMessage("compute.cluster.hosts.failed",
                    numberOfFailedHosts + "  "));
        }

        for (HostRestRep host : hosts) {
            if (vcenterId != null && (host.getvCenterDataCenter() == null)) {
                orderErrors.append(ExecutionUtils.getMessage("compute.cluster.vcenter.push.failed",
                        host.getHostName()) + "  ");
            }
        }

        // Check if the OS installed on the new hosts that were created by the order
        if (computeImage != null) {
            List<HostRestRep> newHosts = Lists.newArrayList();
            for (HostRestRep host : hosts) {
                if (hostNames.contains(host.getHostName())) {
                    newHosts.add(host);
                }
            }
            for (HostRestRep host : newHosts) {
                if ((host.getType() == null) || host.getType().isEmpty() ||
                        host.getType().equals(Host.HostType.No_OS.name())) {
                    orderErrors.append(ExecutionUtils.getMessage("computeutils.installOs.failure",
                            host.getHostName()) + "  ");
                }
            }
        }
        return orderErrors.toString();
    }

    public static List<String> getHostNamesFromFqdn(FqdnTable[] fqdnValues) {
        List<String> hostNames = new ArrayList<String>();
        for (FqdnTable value : fqdnValues) {
            hostNames.add(value.fqdns.toLowerCase()); // host controller will force lower case names anyway
        }
        return hostNames;
    }

    public static class FqdnToIpTable {
        @Param
        protected String fqdns;
        @Param
        protected String ips;

        @Override
        public String toString() {
            return "fqdns=" + fqdns + ", ips=" + ips;
        }
    }

    public static class FqdnTable {
        @Param
        protected String fqdns;

        @Override
        public String toString() {
            return "fqdns=" + fqdns;
        }
    }

    public static void setHostBootVolumes(List<Host> hosts,
            List<URI> bootVolumeIds) {
        for (Host host : hosts) {
            if (host != null && !host.getInactive()) {
                host.setBootVolumeId(bootVolumeIds.get(hosts.indexOf(host)));
                ViPRExecutionUtils.execute(new SetBootVolume(host, bootVolumeIds.get(hosts.indexOf(host))));
            }
        }
    }

    public static Map<String, URI> getHostNameBootVolume(List<Host> hosts) {

        if (hosts == null || hosts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, URI> hostMap = new HashMap<String, URI>();

        for (Host host : hosts) {
            if (host != null) {
                hostMap.put(host.getHostName(), host.getBootVolumeId());
            }
        }
        return hostMap;
    }

    public static ComputeVirtualPoolRestRep getComputeVirtualPool(ViPRCoreClient client, URI cvp) {
        return client.computeVpools().getComputeVirtualPool(cvp);
    }

    public static Cluster updateCluster(URI clusterID, String clusterName) {
        ClusterRestRep clusterRestRep = execute(new UpdateCluster(clusterID, clusterName));
        return (clusterRestRep == null) ? null : BlockStorageUtils
                .getCluster(clusterRestRep.getId());
    }

    /**
     * This method fetches all vblock hosts for the given cluster
     * @param clusterId cluster id URI
     * @return
     */
    public static Map<URI, String> getVblockHostURIsByCluster(URI clusterId) {
        List<HostRestRep> resp = getVblockHostsInCluster(clusterId);
        List<URI> provisionedHostURIs = Lists.newArrayList();
        Map<URI, String> provisionedHostMap = new HashMap<URI,String>(resp.size());
        for (HostRestRep r : resp) {
            provisionedHostURIs.add(r.getId());
            provisionedHostMap.put(r.getId(), r.getName());
        }
        return provisionedHostMap;
    }

    /**
     * get list of vblock hosts
     * @param clusterId cluster id URI
     * @return
     */
    public static List<HostRestRep> getVblockHostsInCluster(URI clusterId) {
        return execute(new FindVblockHostsInCluster(clusterId));
    }

    /**
     * Get Vcenter
     * @param vcenterId vcenter id
     * @return
     */
    public static Vcenter getVcenter(URI vcenterId) {
        return execute(new GetVcenter(vcenterId));
    }

    /**
     * Get vcenter data center
     * @param datacenterId datacenter id
     * @return
     */
    public static VcenterDataCenter getVcenterDataCenter(URI datacenterId) {
        return execute(new GetVcenterDataCenter(datacenterId));
    }

    /**
     * Validate that the boot volume for this host is still on the server.
     * This prevents us from deleting a re-purposed volume that was originally
     * a boot volume.
     * 
     * @return true if the volumes are valid, or the volumes are not able to be validated, so we can go ahead anyway.
     */
    public static boolean validateBootVolumes(Cluster cluster, List<HostRestRep> hostsToValidate) {
        // If the cluster isn't returned properly, not found in DB, do not delete the boot volume until
        // the references are fixed.
        if (cluster == null || cluster.getInactive()) {
            ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.cluster");
            return false;
        }

        // If this cluster is not part of a virtual center/datacenter, then we cannot perform validation,
        // so return that the boot volume is valid due to lack of technical ability to dig any deeper.
        if (NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
            ExecutionUtils.currentContext().logInfo("computeutils.removebootvolumes.validation.skipped", cluster.forDisplay());
            return true;
        }

        VcenterDataCenter dataCenter = execute(new GetVcenterDataCenter(cluster.getVcenterDataCenter()));

        // If the datacenter isn't returned properly, not found in DB, but the cluster has a reference to
        // it, there's an issue with the sync of the DB object. Do not allow the validation to pass
        // until that's fixed.
        if (dataCenter == null || dataCenter.getInactive() || NullColumnValueGetter.isNullURI(dataCenter.getVcenter())) {
            ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.datacenter", cluster.forDisplay());
            return false;
        }

        Vcenter vcenter = execute(new GetVcenter(dataCenter.getVcenter()));

        // If the vcenter isn't returned properly, not found in DB, but the cluster has a reference to
        // it, there's an issue with the sync of the DB object. Do not allow the validation to pass
        // until that's fixed.
        if (vcenter == null || vcenter.getInactive()) {
            ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.vcenter", cluster.forDisplay());
            return false;
        }

        VMwareSupport vmware = null;
        try {
            vmware = new VMwareSupport();
            vmware.connect(vcenter.getId());

            for (HostRestRep clusterHost : hostsToValidate) {
                Host host = BlockStorageUtils.getHost(clusterHost.getId());

                // Do not validate a host no longer in our database
                if (host == null || host.getInactive()) {
                    ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.host", "N/A",
                            "host not found or inactive");
                    return false;
                }

                // If there's no vcenter associated with the host, then this host is in the ViPR cluster, but is not
                // in the vCenter cluster, and therefore we can not perform a deep validation.
                if (NullColumnValueGetter.isNullURI(host.getVcenterDataCenter())) {
                    ExecutionUtils.currentContext().logInfo("computeutils.removebootvolumes.validation.skipped.hostnotinvcenter",
                            host.getHostName());
                    continue;
                }

                // Validate the boot volume exists. If it doesn't, there's nothing that will get deleted anyway. Don't
                // flag it as an issue.
                if (clusterHost.getBootVolume() == null || NullColumnValueGetter.isNullURI(clusterHost.getBootVolume().getId())) {
                    ExecutionUtils.currentContext().logWarn("computeutils.removebootvolumes.failure.host", host.getHostName(),
                            "no boot volume associated with host");
                    continue;
                }

                BlockObjectRestRep bootVolume = execute(new GetBlockResource(clusterHost.getBootVolume().getId()));

                // Do not validate an old/non-existent boot volume representation
                if (bootVolume == null || bootVolume.getInactive()) {
                    ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.host", host.getHostName(),
                            "boot volume not found or inactive");
                    return false;
                }

                HostSystem hostSystem = vmware.getHostSystem(dataCenter.getLabel(), clusterHost.getName());

                // Make sure the host system is still part of the cluster. If it isn't, hostSystem will be null and
                // we can fail the validation based on principle alone.
                if (hostSystem == null) {
                    ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.host", host.getHostName(),
                            "host not part of cluster/datacenter.");
                    return false;
                }

                if (vmware.findScsiDisk(hostSystem, null, bootVolume, false, false) == null) {
                    // fail, host can't see its boot volume
                    ExecutionUtils.currentContext().logError("computeutils.removebootvolumes.failure.bootvolume",
                            bootVolume.getDeviceLabel(), bootVolume.getWwn());
                    return false;
                } else {
                    ExecutionUtils.currentContext().logInfo("computeutils.removebootvolumes.validated", host.getHostName(),
                            bootVolume.getDeviceLabel());
                }
            }
        } finally {
            if (vmware != null) {
                vmware.disconnect();
            }
        }

        return true;
    }
}
