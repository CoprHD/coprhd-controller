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

import com.emc.sa.engine.ExecutionException;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.ViPRExecutionUtils;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.tasks.AddHostToCluster;
import com.emc.sa.service.vipr.compute.tasks.CreateCluster;
import com.emc.sa.service.vipr.compute.tasks.CreateHosts;
import com.emc.sa.service.vipr.compute.tasks.CreateVcenterCluster;
import com.emc.sa.service.vipr.compute.tasks.DeactivateCluster;
import com.emc.sa.service.vipr.compute.tasks.DeactivateHost;
import com.emc.sa.service.vipr.compute.tasks.DeactivateHostNoWait;
import com.emc.sa.service.vipr.compute.tasks.FindCluster;
import com.emc.sa.service.vipr.compute.tasks.FindHostsInCluster;
import com.emc.sa.service.vipr.compute.tasks.InstallOs;
import com.emc.sa.service.vipr.compute.tasks.RemoveHostFromCluster;
import com.emc.sa.service.vipr.compute.tasks.SetBootVolume;
import com.emc.sa.service.vipr.compute.tasks.UpdateVcenterCluster;
import com.emc.sa.service.vipr.tasks.GetHost;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
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

public class ComputeUtils {

    public static final URI nullConsistencyGroup = null;

    public static List<Host> createHosts(URI cluster, URI vcp, List<String> hostNamesIn,
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
            tasks = execute(new CreateHosts(vcp, cluster, hostNames, varray));
        } catch (Exception e) {
            ExecutionUtils.currentContext().logError("computeutils.createhosts.failure",
                    e.getMessage());
        }
        // Some tasks could succeed while others could error out.
        List<HostRestRep> hostsInCluster = ComputeUtils.getHostsInCluster(cluster);
        List<URI> hostURIsToDeactivate = Lists.newArrayList();

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
                    hostURIsToDeactivate.add(hostRep.getId());
                }
            }
        }
        else { // If all the hosts failed, then the tasks are returned as null.
               // In this case we need to deactivate all the hosts that we wanted to create.
            for (HostRestRep hostRep : hostsInCluster) {
                if (hostNames.contains(hostRep.getName())) {
                    hostURIsToDeactivate.add(hostRep.getId());
                }
            }
        }
        for (URI hostToDeactivate : hostURIsToDeactivate) {
            execute(new DeactivateHost(hostToDeactivate, true));
        }
        return Arrays.asList(hosts);
    }

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
        List<HostRestRep> hostRestReps = execute(new FindHostsInCluster(cluster.getId()));
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

    public static List<URI> exportBootVols(List<URI> volumeIds, List<Host> hosts, URI project, URI virtualArray,
            boolean updateBootVolumeOnHost) {

        if ((hosts == null) || (volumeIds == null)) {
            return Collections.emptyList();
        }

        List<Task<ExportGroupRestRep>> tasks = new ArrayList<>();
        for (int x = 0; x < hosts.size(); x++) {
            if ((volumeIds.get(x) != null) && (hosts.get(x) != null)) {
                try {
                    /**
                     * Don't determine HLUs at all, even for the boot volumes. Let the system decide them for you. Hence passing -1
                     */
                    Task<ExportGroupRestRep> task = BlockStorageUtils.createHostExportNoWait(project,
                            virtualArray, Arrays.asList(volumeIds.get(x)), -1, hosts.get(x));
                    tasks.add(task);
                } catch (ExecutionException e) {
                    String errorMessage = e.getMessage() == null ? "" : e.getMessage();
                    ExecutionUtils.currentContext().logError("computeutils.exportbootvolumes.failure",
                            hosts.get(x).getHostName(), errorMessage);
                }
                ExecutionUtils.clearRollback(); // prevent exports from rolling back on exception
                /**
                 * The caller of this method expresses intent of whether the boot target should be updated on the host in question
                 * The OS Install API call already sets the boot volume and sets the boot targets, hence the following piece of code
                 * is executed only of updateBootVolumeOnHost and is typically set to true in the Bare Metal Case.
                 */
                if (updateBootVolumeOnHost) {
                    ViPRExecutionUtils.execute(new SetBootVolume(hosts.get(x), volumeIds.get(x)));
                }
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
        long freeCapacity = (long) Long.parseLong(size);
        double reqSize = sizeOfBootVolumesInGb * numVols;
        long reqCapacity = (long) reqSize;

        if ((reqSize - reqCapacity) > 0) { // round up
            reqCapacity++;
        }
        return reqCapacity > freeCapacity ? false : true;
    }

    public static List<Host> deactivateHostsWithNoBootVolume(List<Host> hosts,
            List<URI> bootVolumeIds) {
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
            }
        }
        if (!hostsToRemove.isEmpty()) {
            try {
                return deactivateHosts(hostsToRemove);
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        e.getMessage());
            }
        }
        return hosts;
    }

    public static List<Host> deactivateHostsWithNoExport(List<Host> hosts,
            List<URI> exportIds) {
        if (hosts == null) {
            return Lists.newArrayList();
        }
        List<Host> hostsToRemove = Lists.newArrayList();

        for (Host host : hosts) {
            if ((exportIds.get(hosts.indexOf(host)) == null) && (host != null)) {
                try {
                    execute(new RemoveHostFromCluster(host.getId()));
                } catch (Exception e) {
                    ExecutionUtils.currentContext().logError("computeutils.deactivatehost.noexport",
                            host.getHostName(), e.getMessage());
                }
                hostsToRemove.add(host);
            }
        }
        if (!hostsToRemove.isEmpty()) {
            try {
                return deactivateHosts(hostsToRemove);
            } catch (Exception e) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        e.getMessage());
            }
        }
        return hosts;
    }

    public static List<Host> deactivateHosts(List<Host> hosts) {
        List<URI> hostURIs = Lists.newArrayList();
        for (Host host : hosts) {
            hostURIs.add(host.getId());
        }
        List<URI> successfulHostURIs = deactivateHostURIs(hostURIs);

        ListIterator<Host> hostItr = nonNull(hosts).listIterator();
        while (hostItr.hasNext()) {
            if (!successfulHostURIs.contains(hostItr.next().getId())) {
                hostItr.set(null);
            }
        }
        return hosts;
    }

    public static List<URI> deactivateHostURIs(List<URI> hostURIs) {
        ArrayList<Task<HostRestRep>> tasks = new ArrayList<>();
        for (URI hostURI : hostURIs) {
            tasks.add(execute(new DeactivateHostNoWait(hostURI, true)));
        }
        ExecutionUtils.currentContext().logInfo("computeutils.deactivatehost.inprogress", hostURIs);
        // monitor tasks
        List<URI> successfulHostIds = Lists.newArrayList();
        while (!tasks.isEmpty()) {
            waitAndRefresh(tasks);
            for (Task<HostRestRep> successfulTask : getSuccessfulTasks(tasks)) {
                successfulHostIds.add(successfulTask.getResourceId());
                addAffectedResource(successfulTask.getResourceId());
                tasks.remove(successfulTask);
            }
            for (Task<HostRestRep> failedTask : getFailedTasks(tasks)) {
                ExecutionUtils.currentContext().logError("computeutils.deactivatehost.deactivate.failure",
                        failedTask.getResource().getName(), failedTask.getMessage());
                tasks.remove(failedTask);
            }
        }
        return successfulHostIds;
    }

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

    public static boolean isComputePoolCapacityAvailable(ViPRCoreClient client, URI vcp, int numHosts) {
        ComputeElementListRestRep resp = client.computeVpools().getMatchedComputeElements(vcp);
        int size = resp.getList().size();
        return size < numHosts ? false : true;
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
        StringBuffer orderErrors = new StringBuffer();

        List<HostRestRep> hosts = Lists.newArrayList();

        try {
            hosts = getHostsInCluster(cluster.getId());
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

        public String toString() {
            return "fqdns=" + fqdns + ", ips=" + ips;
        }
    }

    public static class FqdnTable {
        @Param
        protected String fqdns;

        public String toString() {
            return "fqdns=" + fqdns;
        }
    }

    public static void setHostBootVolumes(List<Host> hosts,
            List<URI> bootVolumeIds) {
        for (Host host : hosts) {
            if (host != null) {
                host.setBootVolumeId(bootVolumeIds.get(hosts.indexOf(host)));
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

}
