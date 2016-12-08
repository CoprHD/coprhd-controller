/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.CLUSTER;
import static com.emc.sa.service.ServiceParams.COMPUTE_IMAGE;
import static com.emc.sa.service.ServiceParams.COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.DATACENTER;
import static com.emc.sa.service.ServiceParams.DNS_SERVERS;
import static com.emc.sa.service.ServiceParams.GATEWAY;
import static com.emc.sa.service.ServiceParams.HOST_PASSWORD;
import static com.emc.sa.service.ServiceParams.MANAGEMENT_NETWORK;
import static com.emc.sa.service.ServiceParams.NETMASK;
import static com.emc.sa.service.ServiceParams.NTP_SERVER;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VCENTER;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;
import static com.emc.sa.util.ArrayUtil.safeArrayCopy;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.compute.ComputeUtils.FqdnToIpTable;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.google.common.collect.Lists;

@Service("AddHostToCluster")
public class AddHostToClusterService extends ViPRService {

    @Param(CLUSTER)
    protected URI clusterId;

    @Param(PROJECT)
    protected URI project;

    @Param(VIRTUAL_ARRAY)
    protected URI virtualArray;

    @Param(COMPUTE_IMAGE)
    protected URI computeImage;

    @Param(NETMASK)
    protected String netmask;

    @Param(GATEWAY)
    protected String gateway;

    @Param(NTP_SERVER)
    protected String ntpServer;

    @Param(MANAGEMENT_NETWORK)
    protected String managementNetwork;

    @Param(DNS_SERVERS)
    protected String dnsServers;

    @Param(VIRTUAL_POOL)
    protected URI virtualPool;

    @Param(COMPUTE_VIRTUAL_POOL)
    protected URI computeVirtualPool;

    @Param(SIZE_IN_GB)
    protected Double size;

    @Param(HOST_PASSWORD)
    protected String rootPassword;

    @Bindable(itemType = FqdnToIpTable.class)
    protected FqdnToIpTable[] fqdnToIps;

    @Param(value = VCENTER, required = false)
    protected URI vcenterId;

    @Param(value = DATACENTER, required = false)
    protected URI datacenterId;

    private Cluster cluster;
    private List<String> hostNames = null;
    private List<String> hostIps = null;

    @Override
    public void precheck() throws Exception {

        StringBuilder preCheckErrors = new StringBuilder();
        hostNames = ComputeUtils.getHostNamesFromFqdnToIps(fqdnToIps);

        hostIps = ComputeUtils.getIpsFromFqdnToIps(fqdnToIps);

        List<String> existingHostNames = ComputeUtils.getHostNamesByName(getClient(), hostNames);
        cluster = BlockStorageUtils.getCluster(clusterId);
        List<String> hostNamesInCluster = ComputeUtils.findHostNamesInCluster(cluster);

        if (cluster == null) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.no.cluster.exists"));
        }

        if (hostNames == null || hostNames.isEmpty() || hostIps == null || hostIps.isEmpty()) {
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

        for (String hostIp : hostIps) {
            if (!ComputeUtils.isValidIpAddress(hostIp)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.ip.invalid", hostIp) + "  ");
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

        if (!ComputeUtils.isValidIpAddress(netmask)) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.invalid.netmask") + "  ");
        }

        if (!ComputeUtils.isValidHostIdentifier(gateway)) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.invalid.gateway") + "  ");

        }
        if (ntpServer != null && ntpServer.trim().length() == 0) {
            ntpServer = null;
        }
        else if (ntpServer != null && ntpServer.trim().length() > 0) {
            // allowing user to specify comma separated list - use only use the first valid one
            String[] ntpServerList = ntpServer.split(",");
            String validServer = null;
            for (String ntpServerx : ntpServerList) {
                if (ComputeUtils.isValidHostIdentifier(ntpServerx.trim())) {
                    validServer = ntpServerx.trim();
                }
                break;
            }
            if (validServer == null) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.invalid.ntp") + "  ");
            }
            else {
                ntpServer = validServer;
            }
        }

        if (dnsServers != null && dnsServers.trim().length() == 0) {
            dnsServers = null;
        }
        else if (dnsServers != null && dnsServers.trim().length() > 0) {
            String[] dnsServerList = dnsServers.split(",");
            for (String dnsServer : dnsServerList) {
                if (!ComputeUtils.isValidIpAddress(dnsServer.trim()) && !ComputeUtils.isValidHostIdentifier(dnsServer.trim())) {
                    preCheckErrors.append(
                            ExecutionUtils.getMessage("compute.cluster.invalid.dns") + "  ");
                }
            }
        }

        for (String existingHostName : existingHostNames) {
            if (!hostNamesInCluster.contains(existingHostName)) {
                preCheckErrors.append(
                        ExecutionUtils.getMessage("compute.cluster.hosts.exists.elsewhere",
                                existingHostName) + "  ");
            }
        }

        if (vcenterId != null && datacenterId == null) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.datacenter.id.null") + "  ");
        }

        ComputeVirtualPoolRestRep cvp = ComputeUtils.getComputeVirtualPool(getClient(), computeVirtualPool);
        if (cvp.getServiceProfileTemplates().isEmpty()) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.service.profile.templates.null", cvp.getName()) + "  ");
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString());
        }
    }

    @Override
    public void execute() throws Exception {

        Map<String, String> hostToIPs = new HashMap<String, String>();

        if (hostNames.size() != hostIps.size()) {
            throw new IllegalStateException(ExecutionUtils.getMessage("compute.cluster.host.ip.mismatch"));
        }

        int index = 0;
        for (String hostname : hostNames) {
            if (hostname != null) {
                hostToIPs.put(hostname, hostIps.get(index));
                index++;
            }
        }

        hostNames = ComputeUtils.removeExistingHosts(hostNames, cluster);

        List<Host> hosts = ComputeUtils.createHosts(cluster.getId(), computeVirtualPool, hostNames, virtualArray);
        logInfo("compute.cluster.hosts.created", ComputeUtils.nonNull(hosts).size());
        // Below step to update the shared export group to the cluster (the
        // newly added hosts will be taken care of this update cluster
        // method and in a synchronized way)
        URI updatedClusterURI = ComputeUtils.updateClusterSharedExports(cluster.getId(), cluster.getLabel());

        // Make sure the all hosts that are being created belong to the all
        // of the exportGroups of the cluster, else fail the order.
        // Not do so and continuing to create bootvolumes to the host we
        // might end up in "consistent lun violation" issue.
        if (!ComputeUtils.nonNull(hosts).isEmpty() && null == updatedClusterURI) {
            logInfo("compute.cluster.sharedexports.update.failed.rollback.started", cluster.getLabel());
            ComputeUtils.deactivateHosts(hosts);
            // When the hosts are deactivated, update the cluster shared export groups to reflect the same.
            ComputeUtils.updateClusterSharedExports(cluster.getId(), cluster.getLabel());
            logInfo("compute.cluster.sharedexports.update.failed.rollback.completed", cluster.getLabel());
            throw new IllegalStateException(
                    ExecutionUtils.getMessage("compute.cluster.sharedexports.update.failed", cluster.getLabel()));
        } else {
            logInfo("compute.cluster.sharedexports.updated", cluster.getLabel());
            List<URI> bootVolumeIds = ComputeUtils.makeBootVolumes(project, virtualArray, virtualPool, size, hosts,
                    getClient());
            logInfo("compute.cluster.boot.volumes.created", ComputeUtils.nonNull(bootVolumeIds).size());
            hosts = ComputeUtils.deactivateHostsWithNoBootVolume(hosts, bootVolumeIds, cluster);

            List<URI> exportIds = ComputeUtils.exportBootVols(bootVolumeIds, hosts, project, virtualArray);
            logInfo("compute.cluster.exports.created", ComputeUtils.nonNull(exportIds).size());
            hosts = ComputeUtils.deactivateHostsWithNoExport(hosts, exportIds, bootVolumeIds, cluster);

            logInfo("compute.cluster.exports.installing.os");
            List<HostRestRep> hostsWithOs = installOSForHosts(hostToIPs, ComputeUtils.getHostNameBootVolume(hosts));
            logInfo("compute.cluster.exports.installed.os", ComputeUtils.nonNull(hostsWithOs).size());

            pushToVcenter();
        }
        String orderErrors = ComputeUtils.getOrderErrors(cluster, hostNames, computeImage, vcenterId);
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

    public String getRootPassword() {
        return rootPassword;
    }

    public void setRootPassword(String rootPassword) {
        this.rootPassword = rootPassword;
    }

    public FqdnToIpTable[] getFqdnToIps() {
        return safeArrayCopy(fqdnToIps);
    }

    public void setFqdnToIps(FqdnToIpTable[] fqdnToIps) {
        this.fqdnToIps = safeArrayCopy(fqdnToIps);
    }

    private List<HostRestRep> installOSForHosts(Map<String, String> hostToIps, Map<String, URI> hostNameToBootVolumeMap) {
        List<HostRestRep> hosts = ComputeUtils.getHostsInCluster(cluster.getId());

        List<OsInstallParam> osInstallParams = Lists.newArrayList();

        // Filter out everyting except the hosts that were created and
        // added to the cluster from the current order.
        List<HostRestRep> newHosts = Lists.newArrayList();
        for (HostRestRep host : hosts) {
            if (hostToIps.get(host.getHostName()) != null) {
                newHosts.add(host);
            }
        }

        for (HostRestRep host : newHosts) {
            if ((host != null) && (
                    (host.getType() == null) ||
                            host.getType().isEmpty() ||
                    host.getType().equals(Host.HostType.No_OS.name())
                    )) {
                OsInstallParam param = new OsInstallParam();
                String hostIp = hostToIps.get(host.getHostName());
                param.setComputeImage(computeImage);
                param.setHostName(host.getHostName());
                param.setDnsServers(dnsServers);
                param.setGateway(gateway);
                param.setNetmask(netmask);
                param.setHostIp(hostIp);
                param.setVolume(hostNameToBootVolumeMap.get(host.getHostName()));
                param.setManagementNetwork(managementNetwork);
                param.setNtpServer(ntpServer);
                param.setRootPassword(rootPassword);
                osInstallParams.add(param);
            }
            else {
                osInstallParams.add(null);
            }
        }
        List<HostRestRep> installedHosts = Lists.newArrayList();
        try {
            // Attempt an OS Install only on the list of hosts that are a part of the order
            // This does check if the hosts already have a OS before attempting the install
            installedHosts = ComputeUtils.installOsOnHosts(newHosts, osInstallParams);
        } catch (Exception e) {
            logError(e.getMessage());
        }
        return installedHosts;
    }

    private void pushToVcenter() {
        if (vcenterId != null) {
            // If the cluster already has a datacenter associated with it,
            // it needs to be updated, else create.
            try {
                URI existingDatacenterId = cluster.getVcenterDataCenter();
                if (existingDatacenterId == null) {
                    logInfo("compute.cluster.create.vcenter.cluster", vcenterId, datacenterId);
                    ComputeUtils.createVcenterCluster(cluster, datacenterId);
                } else {
                    logInfo("vcenter.cluster.update", cluster.getLabel());
                    ComputeUtils.updateVcenterCluster(cluster, datacenterId);
                }
            } catch (Exception e) {
                logError("compute.cluster.vcenter.push.failed", e.getMessage());
            }
        }
    }

    public URI getClusterId() {
        return clusterId;
    }

    public void setClusterId(URI clusterId) {
        this.clusterId = clusterId;
    }

    public URI getProject() {
        return project;
    }

    public void setProject(URI project) {
        this.project = project;
    }

    public URI getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    public URI getComputeImage() {
        return computeImage;
    }

    public void setComputeImage(URI computeImage) {
        this.computeImage = computeImage;
    }

    public String getNetmask() {
        return netmask;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getNtpServer() {
        return ntpServer;
    }

    public void setNtpServer(String ntpServer) {
        this.ntpServer = ntpServer;
    }

    public String getManagementNetwork() {
        return managementNetwork;
    }

    public void setManagementNetwork(String managementNetwork) {
        this.managementNetwork = managementNetwork;
    }

    public String getDnsServers() {
        return dnsServers;
    }

    public void setDnsServers(String dnsServers) {
        this.dnsServers = dnsServers;
    }

    public URI getVirtualPool() {
        return virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        this.virtualPool = virtualPool;
    }

    public URI getComputeVirtualPool() {
        return computeVirtualPool;
    }

    public void setComputeVirtualPool(URI computeVirtualPool) {
        this.computeVirtualPool = computeVirtualPool;
    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    /**
     * @return the cluster
     */
    public Cluster getCluster() {
        return cluster;
    }

    /**
     * @param cluster the cluster to set
     */
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    /**
     * @return the hostNames
     */
    public List<String> getHostNames() {
        return hostNames;
    }

    /**
     * @param hostNames the hostNames to set
     */
    public void setHostNames(List<String> hostNames) {
        this.hostNames = hostNames;
    }

    /**
     * @return the hostIps
     */
    public List<String> getHostIps() {
        return hostIps;
    }

    /**
     * @param hostIps the hostIps to set
     */
    public void setHostIps(List<String> hostIps) {
        this.hostIps = hostIps;
    }
}