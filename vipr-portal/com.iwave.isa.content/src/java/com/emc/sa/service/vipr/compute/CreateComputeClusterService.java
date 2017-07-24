/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.compute;

import static com.emc.sa.service.ServiceParams.COMPUTE_IMAGE;
import static com.emc.sa.service.ServiceParams.COMPUTE_VIRTUAL_POOL;
import static com.emc.sa.service.ServiceParams.DATACENTER;
import static com.emc.sa.service.ServiceParams.DNS_SERVERS;
import static com.emc.sa.service.ServiceParams.GATEWAY;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST_PASSWORD;
import static com.emc.sa.service.ServiceParams.MANAGEMENT_NETWORK;
import static com.emc.sa.service.ServiceParams.NAME;
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

import com.iwave.ext.windows.winrm.Pair;
import org.apache.commons.collections.CollectionUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.compute.ComputeUtils.FqdnToIpTable;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.google.common.collect.ImmutableList;

@Service("CreateComputeCluster")
public class CreateComputeClusterService extends ViPRService {

    @Param(PROJECT)
    protected URI project;

    @Param(NAME)
    protected String name;

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

    @Param(value = HLU, required = false)
    protected Integer hlu;

    @Param(HOST_PASSWORD)
    protected String rootPassword;

    @Bindable(itemType = FqdnToIpTable.class)
    protected FqdnToIpTable[] fqdnToIps;

    @Param(value = VCENTER, required = false)
    protected URI vcenterId;

    @Param(value = DATACENTER, required = false)
    protected URI datacenterId;

    private Cluster cluster = null;
    private List<String> hostNames = null;
    private List<String> hostIps = null;
    private List<String> copyOfHostNames = null;

    @Override
    public void precheck() throws Exception {

        StringBuilder preCheckErrors = new StringBuilder();
        hostNames = ComputeUtils.getHostNamesFromFqdnToIps(fqdnToIps);
        copyOfHostNames = ImmutableList.copyOf(hostNames);

        hostIps = ComputeUtils.getIpsFromFqdnToIps(fqdnToIps);

        List<String> existingHostNames = ComputeUtils.getHostNamesByName(getClient(), hostNames);
        cluster = ComputeUtils.getCluster(name);
        List<String> hostNamesInCluster = ComputeUtils.findHostNamesInCluster(cluster);

        if ((cluster != null) && hostNamesInCluster.isEmpty()) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.empty.cluster.exists"));
        }

        //TODO convert hostnames to lower case here before proceeding
        // and also inform it to the user
        if ((cluster != null) && !hostNames.containsAll(hostNamesInCluster)) {
            preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.unknown.host"));
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
                virtualArray, size, hostNames.size() - existingHostNames.size())) {
            preCheckErrors.append(
                    ExecutionUtils.getMessage("compute.cluster.insufficient.storage.capacity") + "  ");
        }

        if (!ComputeUtils.isComputePoolCapacityAvailable(getClient(), computeVirtualPool,
                hostNames.size() - existingHostNames.size())) {
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
        	// TODO why do we take only the first NTP server? Check
            String[] ntpServerList = ntpServer.split(",");
            String validServer = null;
            for (String ntpServerx : ntpServerList) {
                if (ComputeUtils.isValidHostIdentifier(ntpServerx.trim())) {
                    validServer = ntpServerx.trim();
                    //TODO the break should be moved inside the 'if'
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
        if (hostNamesInCluster != null && !hostNamesInCluster.isEmpty() && !existingHostNames.isEmpty()) {
             for (String hostName : hostNamesInCluster) {
                if (existingHostNames.contains(hostName)){
                    preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.hostname.already.in.cluster", hostName) + "  ");
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
        Pair<Boolean, String> status = new Pair<Boolean, String>();
        if (ComputeUtils.checkComputeSystemsHaveImageServer(getClient(), cvp, status).getFirstElement().equals(false)) {
        	preCheckErrors.append(ExecutionUtils.getMessage("compute.cluster.compute.system.image.server.null", status.getSecondElement()));
        }

        if (preCheckErrors.length() > 0) {
            throw new IllegalStateException(preCheckErrors.toString() + 
                    ComputeUtils.getContextErrors(getModelClient()));
        }
    }

    @Override
    public void execute() throws Exception {

        // Note: creates ordered lists of hosts, bootVolumes & exports
        // host[0] goes with bootVolume[0] and export[0], etc
        // elements are set to null if they fail

        Map<String, String> hostToIPs = new HashMap<String, String>();

        //TODO move this to the precheck section
        if (hostNames.size() != hostIps.size()) {
            throw new IllegalStateException(ExecutionUtils.getMessage("compute.cluster.host.ip.mismatch"));
        }

        int index = 0;
        for (String hostname : hostNames) {
            hostToIPs.put(hostname, hostIps.get(index));
            index++;
        }

        if (cluster == null) {
            cluster = ComputeUtils.createCluster(name);
            logInfo("compute.cluster.created", name);
        } else {
            // If the hostName already exists, we remove it from the hostnames list.
            hostNames = ComputeUtils.removeExistingHosts(hostNames, cluster);
        }
        acquireClusterLock(cluster);

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
        Map<Host, URI> hostToEgIdMap = ComputeUtils.exportBootVols(hostToBootVolumeIdMap, project, virtualArray, hlu);
        logInfo("compute.cluster.exports.created",
                hostToEgIdMap != null ? ComputeUtils.nonNull(hostToEgIdMap.values()).size(): 0);

        // Deactivate any hosts where the export failed, return list of hosts remaining
        hostToBootVolumeIdMap = ComputeUtils.deactivateHostsWithNoExport(hostToBootVolumeIdMap, hostToEgIdMap, cluster);

        // Set host boot volume ids, but do not set san boot targets. They will get set post os install.
        hosts = ComputeUtils.setHostBootVolumes(hostToBootVolumeIdMap, false);

        logInfo("compute.cluster.exports.installing.os");
        installOSForHosts(hostToIPs, ComputeUtils.getHostNameBootVolume(hosts), hosts);
        hosts = ComputeUtils.deactivateHostsWithNoOS(hosts);
        logInfo("compute.cluster.exports.installed.os", ComputeUtils.nonNull(hosts).size());

        ComputeUtils.addHostsToCluster(hosts, cluster);
        hosts = ComputeUtils.deactivateHostsNotAddedToCluster(hosts, cluster);

        // VBDU [DONE]: COP-28400, Potential DU if external host is added to vCenter cluster not under ViPR mgmt.
        // ClusterService has a precheck to verify the matching environments before deactivating
        try {
            if (ComputeUtils.findHostNamesInCluster(cluster).isEmpty()) {
                logInfo("compute.cluster.removing.empty.cluster");
                ComputeUtils.deactivateCluster(cluster);
            } else {
                if (!ComputeUtils.nonNull(hosts).isEmpty()) {
                    pushToVcenter(hosts);
                } else {
                    logWarn("compute.cluster.newly.provisioned.hosts.none");
                }
            }
        } catch (Exception ex) {
            logError(ex.getMessage());
            setPartialSuccess();
        }

        String orderErrors = ComputeUtils.getOrderErrors(cluster, copyOfHostNames, computeImage, vcenterId);
        if (orderErrors.length() > 0) { // fail order so user can resubmit
            if (ComputeUtils.nonNull(hosts).isEmpty()) {
                throw new IllegalStateException(
                        ExecutionUtils.getMessage("compute.cluster.order.incomplete", orderErrors));
            } else {
                logError("compute.cluster.order.incomplete", orderErrors);
                // VBDU TODO: COP-28433, change criteria for partial success
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

    private void installOSForHosts(Map<String, String> hostToIps, Map<String, URI> hostNameToBootVolumeMap, List<Host> createdHosts) {
        Map<Host,OsInstallParam> osInstallParamMap = new HashMap<Host, OsInstallParam>();
        for (Host host : createdHosts) {
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
                osInstallParamMap.put(host,param);
            }
            else {
                osInstallParamMap.put(host,null);
            }
        }

        try {
            ComputeUtils.installOsOnHosts(osInstallParamMap);
        } catch (Exception e) {
            logError(e.getMessage());
        }
    }

    private void pushToVcenter(List<Host> hosts) {
        if (vcenterId != null) {
            boolean isVCenterUpdate = false;
            List<ClusterRestRep> clusters = getClient().clusters().getByDataCenter(datacenterId);
            for (ClusterRestRep resp : clusters) {
                if (cluster.getLabel().equals(resp.getName())) {
                    isVCenterUpdate = true;
                    break;
                }
            }

            try {
                Vcenter vcenter = null;
                VcenterDataCenter dataCenter = null;
                vcenter = ComputeUtils.getVcenter(vcenterId);

                if (null != datacenterId) {
                    dataCenter = ComputeUtils.getVcenterDataCenter(datacenterId);
                }
                boolean status = true;
                if (isVCenterUpdate) {
                    logInfo("vcenter.cluster.update", cluster.getLabel());
                    if (dataCenter == null) {
                        status = ComputeUtils.updateVcenterCluster(cluster, datacenterId);
                    } else {
                        status = ComputeUtils.updateVcenterCluster(cluster, dataCenter);
                    }

                    if (!status) {
                        throw new IllegalStateException(
                                ExecutionUtils.getMessage("vcenter.cluster.update.failed", cluster.getLabel()));
                    }
                } else {
                    logInfo("compute.cluster.create.vcenter.cluster.datacenter",
                            (vcenter != null ? vcenter.getLabel() : vcenterId),
                            (dataCenter != null ? dataCenter.getLabel() : datacenterId));
                    if (dataCenter == null) {
                        status = ComputeUtils.createVcenterCluster(cluster, datacenterId);
                    } else {
                        status = ComputeUtils.createVcenterCluster(cluster, dataCenter);
                    }

                    if (!status) {
                        throw new IllegalStateException(
                                ExecutionUtils.getMessage("vcenter.cluster.create.failed", cluster.getLabel()));
                    }
                }
            } catch (Exception e) {
                logError("compute.cluster.vcenter.sync.failed.corrective.user.message", cluster.getLabel());
                logError("compute.cluster.vcenter.push.failed", e.getMessage());
                throw e;
            }
            ComputeUtils.discoverHosts(hosts);
        } else if(CollectionUtils.isNotEmpty(hosts)) {
            logInfo("compute.cluster.no.vcenter.manual.hostdiscover.message", hosts);
        }
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

    public String getHostPassword() {
        return rootPassword;
    }

    public void setHostPassword(String hostPassword) {
        this.rootPassword = hostPassword;
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

    public FqdnToIpTable[] getFqdnToIps() {
        return safeArrayCopy(fqdnToIps);
    }

    public void setFqdnToIps(FqdnToIpTable[] fqdnToIps) {
        this.fqdnToIps = safeArrayCopy(fqdnToIps);
    }

    public URI getVcenterId() {
        return vcenterId;
    }

    public void setVcenterId(URI vcenterId) {
        this.vcenterId = vcenterId;
    }

    public URI getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(URI datacenterId) {
        this.datacenterId = datacenterId;
    }

    public URI getProject() {
        return project;
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
