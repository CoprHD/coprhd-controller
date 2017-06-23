/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl.adapter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.service.impl.resource.ExportGroupService;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.computesystemcontroller.exceptions.CompatibilityException;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.computesystemcontroller.impl.DiscoveryStatusUtils;
import com.emc.storageos.computesystemcontroller.impl.HostToComputeElementMatcher;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.EventUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VcenterVersion;
import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.HostHardwareInfo;
import com.vmware.vim25.HostScsiDiskPartition;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.VmfsDatastoreInfo;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedObject;

/**
 * Discovery adapter for vCenters.
 * 
 * @author jonnymiller
 */
@Component
public class VcenterDiscoveryAdapter extends EsxHostDiscoveryAdapter {
    @Override
    public boolean isSupportedTarget(String targetId) {
        return URIUtil.isType(URI.create(targetId), Vcenter.class);
    }

    @Override
    public void discoveryFailure(DiscoveredSystemObject target, String compatibilityStatus, String errorMessage) {
        super.discoveryFailure(target, compatibilityStatus, errorMessage);
        Vcenter vcenter = getModelClient().vcenters().findById(target.getId());
        Iterable<VcenterDataCenter> dataCenters = getModelClient().datacenters().findByVCenter(vcenter.getId(), true);
        for (VcenterDataCenter dataCenter : dataCenters) {
            Iterable<Host> hosts = getModelClient().hosts().findByVcenterDatacenter(dataCenter.getId());
            for (Host host : hosts) {
                host.setDiscoveryStatus(DataCollectionJobStatus.ERROR.name());
                host.setLastDiscoveryRunTime(System.currentTimeMillis());
                host.setCompatibilityStatus(compatibilityStatus);
                host.setLastDiscoveryStatusMessage("vCenter Discovery Failed: " + errorMessage);
                save(host);
            }
        }
    }

    @Override
    public void discoverTarget(String targetId) {
        Vcenter vcenter = getModelClient().vcenters().findById(targetId);
        discoverVCenter(vcenter);
    }

    public void discoverVCenter(Vcenter vcenter) {
        DiscoveryProcessor processor = new DiscoveryProcessor(vcenter);
        VcenterVersion version = getVersion(vcenter);
        vcenter.setOsVersion(version.toString());

        if (getVersionValidator().isValidVcenterVersion(version)) {
            List<HostStateChange> changes = Lists.newArrayList();
            List<URI> deletedHosts = Lists.newArrayList();
            List<URI> deletedClusters = Lists.newArrayList();
            Set<URI> discoveredHosts = Sets.newHashSet();
            processor.discover(changes, deletedHosts, deletedClusters, discoveredHosts);
            matchHostsToComputeElements(discoveredHosts) ;
            deletedHosts.removeAll(discoveredHosts);
            processor.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
            // only update registration status of hosts if the vcenter is unregistered
            // to ensure newly discovered hosts are marked as unregistered
            if (vcenter.getRegistrationStatus().equals(RegistrationStatus.UNREGISTERED.toString())) {
                processor.setRegistrationStatus(vcenter.getRegistrationStatus());
            }
            save(vcenter);
            processHostChanges(changes, deletedHosts, deletedClusters, true);
            ingestDatastores(vcenter);
        } else {
            processor.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            save(vcenter);
            throw ComputeSystemControllerException.exceptions.incompatibleHostVersion(
                    "Vcenter", version.toString(), getVersionValidator().getVcenterMinimumVersion(false).toString());
        }
    }

    /**
     * Ingest datastores for the given vCenter
     * 
     * @param vcenter the vcenter for ingesting datastores
     */
    private void ingestDatastores(Vcenter vcenter) {
        VCenterAPI vcenterAPI = createVCenterAPI(vcenter);
        try {

            // Create map of MOR -> Host URI
            Map<String, URI> hostList = Maps.newHashMap();
            for (HostSystem esxHost : vcenterAPI.listAllHostSystems()) {
                String uuid = esxHost.getHardware().getSystemInfo().getUuid();
                Host host = findHostByUuid(uuid);
                if (host != null) {
                    hostList.put(esxHost.getMOR().getVal(), host.getId());
                }
            }

            for (Datastore datastore : vcenterAPI.listAllDatastores()) {
                if (datastore.getInfo() instanceof VmfsDatastoreInfo) {

                    HostVmfsVolume volume = ((VmfsDatastoreInfo) datastore.getInfo()).getVmfs();
                    for (HostScsiDiskPartition partition : volume.getExtent()) {
                        String wwn = StringUtils.substringAfter(partition.getDiskName(), "naa.");
                        SearchedResRepList resRepList = new SearchedResRepList(ResourceTypeEnum.VOLUME);
                        dbClient.queryByConstraint(
                                AlternateIdConstraint.Factory.getVolumeWwnConstraint(wwn.toUpperCase()),
                                resRepList);
                        if (resRepList.size() == 1) {
                            Volume blockVolume = dbClient.queryObject(Volume.class, resRepList.get(0).getId());
                            for (DatastoreHostMount dsh : datastore.getHost()) {
                                ManagedObject object = vcenterAPI.lookupManagedObject(dsh.getKey());

                                URI hostId = null;
                                if (object instanceof HostSystem) {
                                    HostSystem host = (HostSystem) object;
                                    hostId = hostList.get(host.getMOR().getVal());
                                }

                                // TODO determine if all hosts in clusters have access or just 1 host
                                String tagName = ExportGroupService.VMFS_DATASTORE + "-" + hostId;
                                info("Adding datastore tag " + tagName + " to volume " + blockVolume.getId());
                                ScopedLabel tagLabel = new ScopedLabel(blockVolume.getTenant().getURI().toString(), tagName);
                                blockVolume.getTag().add(tagLabel);
                                dbClient.updateObject(blockVolume);
                            }
                        } else {
                            info("Searching for WWN %s returned %d results", wwn, resRepList.size());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            error("Error occurred during datastore ingestion", ex);
        } finally {
            vcenterAPI.logout();
        }

    }

    private void matchHostsToComputeElements(Set<URI> hostIds) {
        HostToComputeElementMatcher.matchHostsToComputeElements(getDbClient(),hostIds);
    }

    @Override
    public String getErrorMessage(Throwable t) {
        Throwable rootCause = getRootCause(t);
        if (rootCause instanceof InvalidLogin) {
            return "Login failed, invalid username or password";
        }
        else if (rootCause instanceof RemoteException) {
            String message = rootCause.getMessage();
            if (StringUtils.contains(message, "java.net.UnknownHostException: ")) {
                return "Unknown host: " + StringUtils.substringAfter(message, "java.net.UnknownHostException: ");
            }
            else if (StringUtils.contains(message, "java.net.ConnectException: ")) {
                return "Error connecting: " + StringUtils.substringAfter(message, "java.net.ConnectException: ");
            }
            else if (StringUtils.contains(message, "java.net.NoRouteToHostException: ")) {
                return "No route to host: " + StringUtils.substringAfter(message, "java.net.NoRouteToHostException: ");
            }
            else if (StringUtils.startsWith(message, "VI SDK invoke exception:")) {
                return StringUtils.substringAfter(message, "VI SDK invoke exception:");
            }
        }
        return super.getErrorMessage(t);
    }

    public VcenterVersion getVersion(Vcenter vcenter) {
        VCenterAPI api = createVCenterAPI(vcenter);
        return api.getVcenterVersion();
    }

    private Iterable<VcenterDataCenter> getDatacenters(Vcenter vcenter) {
        return getModelClient().datacenters().findByVCenter(vcenter.getId(), true);
    }

    private VcenterDataCenter getOrCreateDataCenter(List<VcenterDataCenter> datacenters, String name) {
        return getOrCreate(VcenterDataCenter.class, datacenters, name);
    }

    private Iterable<Cluster> getClusters(VcenterDataCenter datacenter) {
        return getModelClient().clusters().findByDatacenter(datacenter.getId(), true);
    }

    private Cluster getOrCreateCluster(List<Cluster> clusters, String name) {
        return getOrCreate(Cluster.class, clusters, name);
    }

    private Iterable<Host> getHosts(VcenterDataCenter datacenter) {
        return getModelClient().esxHosts().findByDatacenter(datacenter.getId(), true);
    }

    private VcenterDataCenter findDatacenterByExternalId(List<VcenterDataCenter> dataCenters, String extId) {
        if (extId != null) {
            for (VcenterDataCenter dataCenter : dataCenters) {
                if (extId.equals(dataCenter.getExternalId())) {
                    return dataCenter;
                }
            }
        }
        return null;
    }

    private Cluster findClusterByExternalId(List<Cluster> clusters, String extId) {
        for (Cluster cluster : clusters) {
            if (extId.equals(cluster.getExternalId())) {
                return cluster;
            }
        }
        return null;
    }

    private Iterable<Host> getHosts(Cluster cluster) {
        return getModelClient().hosts().findByCluster(cluster.getId(), true);
    }

    private void deleteDatacenters(Iterable<VcenterDataCenter> datacenters, List<URI> deletedHosts, List<URI> deletedClusters) {
        for (VcenterDataCenter datacenter : datacenters) {
            boolean containsHosts = false;
            boolean clustersInUse = false;

            for (Cluster cluster : getClusters(datacenter)) {
                deletedClusters.add(cluster.getId());
            }

            for (Host host : getHosts(datacenter)) {
                deletedHosts.add(host.getId());
                containsHosts = true;
            }

            for (Cluster cluster : getClusters(datacenter)) {
                URI clusterId = cluster.getId();
                List<URI> hostUris = ComputeSystemHelper.getChildrenUris(dbClient, clusterId, Host.class, "cluster");
                if (hostUris.isEmpty() && !ComputeSystemHelper.isClusterInExport(dbClient, clusterId)
                        && EventUtils.findAffectedResourcePendingEvents(dbClient, clusterId).isEmpty()) {
                    info("Deactivating Cluster: " + clusterId);
                    ComputeSystemHelper.doDeactivateCluster(dbClient, cluster);
                } else {
                    info("Unable to delete cluster " + clusterId);
                    clustersInUse = true;
                }
            }

            // delete datacenters that don't contain any clusters or hosts, don't have any exports, and don't have any pending events
            if (!containsHosts && !clustersInUse
                    && !ComputeSystemHelper.isDataCenterInUse(dbClient, datacenter.getId())
                    && EventUtils.findAffectedResourcePendingEvents(dbClient, datacenter.getId()).isEmpty()) {
                info("Deactivating Datacenter: " + datacenter.getId());
                ComputeSystemHelper.doDeactivateVcenterDataCenter(dbClient, datacenter);
            } else {
                info("Unable to delete datacenter " + datacenter.getId());
            }
        }
    }

    public static VCenterAPI createVCenterAPI(Vcenter vcenter) {
        int port = vcenter.getPortNumber() != null ? vcenter.getPortNumber() : 443;

        URL url;
        try {
            url = new URL("https", vcenter.getIpAddress(), port, "/sdk");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage());
        }
        String username = vcenter.getUsername();
        String password = vcenter.getPassword();
        return new VCenterAPI(url, username, password);
    }

    /**
     * Discovery processor for VCenters.
     * 
     * @author jonnymiller
     */
    protected class DiscoveryProcessor {
        private Vcenter vcenter;
        private VCenterAPI vcenterAPI;

        public DiscoveryProcessor(Vcenter vcenter) {
            this.vcenter = vcenter;
        }

        public void discover(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters, Set<URI> discoveredHosts) {
            vcenterAPI = createVCenterAPI(vcenter);
            try {
                AboutInfo aboutInfo = vcenterAPI.getAboutInfo();
                if (!StringUtils.equals(aboutInfo.getApiType(), VCenterAPI.VCENTER_API_TYPE)) {
                    throw new CompatibilityException("Not a vCenter (Type: " + aboutInfo.getFullName() + ")");
                }
                checkDuplicateVcenter(vcenter, aboutInfo.getInstanceUuid());
                vcenter.setNativeGuid(aboutInfo.getInstanceUuid());
                discoverDatacenters(changes, deletedHosts, deletedClusters, discoveredHosts);
            } finally {
                vcenterAPI.logout();
            }
        }

        private VcenterDataCenter findOrCreateDataCenter(List<VcenterDataCenter> datacenters, Datacenter sourceDatacenter) {
            VcenterDataCenter dataCenter = findDatacenterByExternalId(datacenters, sourceDatacenter.getMOR().getVal());

            if (dataCenter != null) {
                datacenters.remove(dataCenter);
            } else {
                dataCenter = getOrCreateDataCenter(datacenters, sourceDatacenter.getName());
            }
            return dataCenter;
        }

        private void discoverDatacenters(List<HostStateChange> changes, List<URI> deletedHosts, List<URI> deletedClusters,
                Set<URI> discoveredHosts) {
            List<VcenterDataCenter> oldDatacenters = new ArrayList<VcenterDataCenter>();
            Iterables.addAll(oldDatacenters, getDatacenters(vcenter));

            for (Datacenter sourceDatacenter : vcenterAPI.listAllDatacenters()) {
                VcenterDataCenter targetDatacenter = findOrCreateDataCenter(oldDatacenters, sourceDatacenter);
                discoverDatacenter(sourceDatacenter, targetDatacenter, changes, deletedHosts, deletedClusters, discoveredHosts);
            }

            deleteDatacenters(oldDatacenters, deletedHosts, deletedClusters);
        }

        private Cluster findClusterHostUuid(ClusterComputeResource cluster) {
            String hostUuid = null;
            HostSystem[] hosts = cluster.getHosts();
            if (hosts != null) {
                for (HostSystem host : hosts) {
                    HostHardwareInfo hw = host.getHardware();
                    if (hw != null && hw.systemInfo != null && StringUtils.isNotBlank(hw.systemInfo.uuid)) {
                        hostUuid = hw.systemInfo.uuid;
                        break;
                    }
                }
            }
            if (hostUuid != null) {
                Host host = findHostByUuid(hostUuid);
                if (host != null && !NullColumnValueGetter.isNullURI(host.getCluster())) {
                    return getModelClient().clusters().findById(host.getCluster());
                }
            }
            return null;
        }

        private Cluster findClusterByName(URI tenant, String name) {
            for (Cluster cluster : getModelClient().clusters().findByLabel(tenant.toString(), name, true)) {
                // the above findByLabel() method actually uses findByPrefix() which can result in partial matches
                if (name.equals(cluster.getLabel())) {
                    return cluster;
                }
            }
            return null;
        }

        private void discoverDatacenter(Datacenter source, VcenterDataCenter target, List<HostStateChange> changes, List<URI> deletedHosts,
                List<URI> deletedClusters, Set<URI> discoveredHosts) {
            info("processing datacenter %s", source.getName());
            target.setVcenter(vcenter.getId());
            setVcenterDataCenterTenant(target);
            target.setExternalId(source.getMOR().getVal());
            target.setLabel(source.getName());
            save(target);

            List<Cluster> oldClusters = new ArrayList<Cluster>();
            Iterables.addAll(oldClusters, getClusters(target));
            List<Cluster> newClusters = Lists.newArrayList();
            reconcileClusters(source, target, oldClusters, newClusters);

            List<Host> oldHosts = new ArrayList<Host>();
            Iterables.addAll(oldHosts, getHosts(target));
            for (HostSystem sourceHost : vcenterAPI.listHostSystems(source)) {
                Host targetHost = null;
                HostHardwareInfo hw = sourceHost.getHardware();
                String uuid = null;
                if (hw != null && hw.systemInfo != null && StringUtils.isNotBlank(hw.systemInfo.uuid)) {
                    // try finding host by UUID
                    uuid = hw.systemInfo.uuid;
                    targetHost = findHostByUuid(uuid);
                }
                if (targetHost == null) {
                    if (findHostByLabel(oldHosts, sourceHost.getName()) != null) {
                        targetHost = getOrCreateHost(oldHosts, sourceHost.getName());
                    }
                    else {
                        Host existingHost = findExistingHost(sourceHost);
                        if (existingHost != null) {
                            targetHost = existingHost;
                        }
                        else {
                            targetHost = getOrCreateHost(oldHosts, sourceHost.getName());
                        }
                    }
                }
                else {
                    for (int i = 0; i < oldHosts.size(); i++) {
                        if (oldHosts.get(i).getId().equals(targetHost.getId())) {
                            oldHosts.remove(i);
                            break;
                        }
                    }
                }
                String bios = null;;
                if (hw != null && hw.biosInfo != null
                        && StringUtils.isNotBlank(hw.biosInfo.biosVersion)) {
                    bios = hw.biosInfo.biosVersion;
                }
                if (deletedHosts != null && deletedHosts.contains(target.getId())) {
                    deletedHosts.remove(target.getId());
                    info("Removing host " + target.getId() + " from deletedHosts. It may have been rediscovered in a different datacenter");
                }

                DiscoveryStatusUtils.markAsProcessing(getModelClient(), targetHost);
                try {
                    discoverHost(source, sourceHost, uuid, bios, target, targetHost, newClusters, changes);
                    discoveredHosts.add(targetHost.getId());
                    DiscoveryStatusUtils.markAsSucceeded(getModelClient(), targetHost);
                } catch (RuntimeException e) {
                    warn(e, "Problem discovering host %s", targetHost.getLabel());
                    DiscoveryStatusUtils.markAsFailed(getModelClient(), targetHost, e.getMessage(), e);
                }
            }
            for (Host oldHost : oldHosts) {
                if (!discoveredHosts.contains(oldHost.getId())) {
                    info("Unable to discover host %s. Marking as failed discovery.", oldHost.getId());
                    DiscoveryStatusUtils.markAsFailed(getModelClient(), oldHost, "Unable to discover host. Host may be disconnected.",
                            null);
                }
            }

            Collection<URI> oldClusterIds = Lists.newArrayList(Collections2.transform(oldClusters,
                    CommonTransformerFunctions.fctnDataObjectToID()));
            deletedClusters.addAll(oldClusterIds);

            Collection<URI> oldHostIds = Lists.newArrayList(Collections2.transform(oldHosts,
                    CommonTransformerFunctions.fctnDataObjectToID()));
            deletedHosts.addAll(oldHostIds);
        }

        /**
         * Get all clusters for the datacenter.
         * Sort them by host count - descending. The reason for this - if vcenter has more than one
         * cluster that matches Vipr cluster, then the one with most hosts will be matched first.
         * Try find the cluster in Vipr by first by vcenter cluster ID, then by name, then by host membership.
         * If not found create new cluster.
         */
        private void reconcileClusters(Datacenter source, VcenterDataCenter target, List<Cluster> oldClusters,
                List<Cluster> newClusters) {
            List<ClusterHolder> allClusters = new ArrayList<ClusterHolder>();
            // get all clusters
            List<ClusterComputeResource> vcClusters = vcenterAPI.listClusters(source);
            // put clusters in a sortable list
            for (ClusterComputeResource vcCluster : vcClusters) {
                allClusters.add(new ClusterHolder(vcCluster, vcCluster.getHosts().length));
            }
            // sort clusters so that those with most host will be processed first
            Collections.sort(allClusters);

            // process clusters - try finding them first; if can't, create new one
            for (ClusterHolder clusterHolder : allClusters) {
                ClusterComputeResource vcCluster = clusterHolder.cluster;
                String vcenterClusterId = vcCluster.getMOR().getVal();
                info("processing cluster %s %s", vcCluster.getName(), vcenterClusterId);

                // find this cluster
                Cluster targetCluster = findCluster(oldClusters, vcCluster, target.getId());

                if (targetCluster == null) {
                    // did not find it, have to create a new one
                    info("creating new cluster %s", vcCluster.getName());
                    targetCluster = getOrCreateCluster(oldClusters, vcCluster.getName());
                }
                targetCluster.setLabel(vcCluster.getName());
                targetCluster.setTenant(target.getTenant());
                targetCluster.setVcenterDataCenter(target.getId());
                targetCluster.setExternalId(vcenterClusterId);

                save(targetCluster);
                ComputeSystemHelper.updateInitiatorClusterName(dbClient, targetCluster.getId());
                newClusters.add(targetCluster);
            }
        }

        /**
         * This tries to find Cluster using this algorithm:
         * 1) Look among existing clusters using the vcenter cluster ID.
         * 2) Look among existing clusters by name.
         * 3) Look among clusters belonging to this tenant, that don't belong to any datacenter.
         * 4) Find by host UUID:
         * a) Take any host UUID.
         * b) Find matching host in Vipr.
         * c) Find Cluster that host is in.
         * d) Return that Cluster if its externalId is null - because otherwise it is related to another cluster.
         */
        private Cluster findCluster(List<Cluster> oldClusters, ClusterComputeResource vcCluster, URI vCenterDataCenterId) {
            // 1) find cluster by vcenter cluster id
            Cluster targetCluster = findClusterByExternalId(oldClusters, vcCluster.getMOR().getVal());
            info("find by vcenter cluster id %s", targetCluster == null ? "NULL" : targetCluster.getLabel());

            if (targetCluster == null) {
                // 2) try finding by name among the existing clusters
                targetCluster = findModelByLabel(oldClusters, vcCluster.getName());
                info("find by name in dc %s", targetCluster == null ? "NULL" : targetCluster.getLabel());
            }

            if (targetCluster != null) {
                // if found among old clusters, then remove it from the list
                oldClusters.remove(targetCluster);
            }

            if (targetCluster == null) {
                // 3) try finding by name in the tenant, must not belong to any datacenter
                targetCluster = getModelClient().clusters().findClusterByNameAndDatacenter(vCenterDataCenterId, vcCluster.getName(), true);
                info("find by name in tenant %s", targetCluster == null ? "NULL" : targetCluster.getLabel());
                if (targetCluster != null && !NullColumnValueGetter.isNullURI(targetCluster.getVcenterDataCenter())) {
                    // can't use this one
                    info("found, but can't use it");
                    targetCluster = null;
                }
            }

            if (targetCluster == null) {
                // 4) try finding by hosts
                targetCluster = findClusterHostUuid(vcCluster);
                info("find by host uuid %s", targetCluster == null ? "NULL" : targetCluster.getLabel());
                if (targetCluster != null && NullColumnValueGetter.isNotNullValue(targetCluster.getExternalId())) {
                    // can't use this one
                    info("found, but can't use it, it is related to another cluster");
                    targetCluster = null;
                }
            }
            return targetCluster;
        }

        private void discoverHost(Datacenter sourceDatacenter, HostSystem source, String uuid, String bios, VcenterDataCenter targetDatacenter,
                Host target, List<Cluster> clusters, List<HostStateChange> changes) {
            URI oldDatacenterURI = target.getVcenterDataCenter();
            URI newDatacenterURI = targetDatacenter.getId();
            boolean isDatacenterChanged = false;
            info("Discovering host " + target.getLabel() + " (" + target.getId() + ")");
            if (NullColumnValueGetter.isNullURI(oldDatacenterURI) || (!NullColumnValueGetter.isNullURI(newDatacenterURI)
                    && newDatacenterURI.toString().equalsIgnoreCase(oldDatacenterURI.toString()))) {
                info("setting vCenter datacenter to " + targetDatacenter.getLabel() + " (" + targetDatacenter.getId() + ") and tenant to "
                        + targetDatacenter.getTenant() + " for host " + target.getLabel());
                target.setVcenterDataCenter(targetDatacenter.getId());
                target.setTenant(targetDatacenter.getTenant());
            } else {
                isDatacenterChanged = true;
            }
            target.setDiscoverable(true);

            if (target.getId() == null) {
                target.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            }

            if (!target.getLabel().equals(source.getName())) {
                target.setLabel(source.getName());
            }

            // Configure the cluster
            String clusterName = getClusterName(source);
            URI oldClusterURI = target.getCluster();
            Cluster cluster = null;
            if (clusterName != null) {
                cluster = findModelByLabel(clusters, clusterName);
            }

            if (target.getType() == null ||
                    StringUtils.equalsIgnoreCase(target.getType(), HostType.Other.toString()) ||
                    StringUtils.equalsIgnoreCase(target.getType(), HostType.No_OS.toString())) {
                target.setType(Host.HostType.Esx.name());
            }
            target.setHostName(target.getLabel());
            target.setOsVersion(source.getConfig().getProduct().getVersion());

            if(bios != null) {
                target.setBios(bios);
            }
            if (uuid != null) {
                target.setUuid(uuid);
            }
            save(target);

            // Only attempt to update ip interfaces or initiators for connected hosts
            HostSystemConnectionState connectionState = getConnectionState(source);
            info("Connection status for host %s is %s", target.forDisplay(), connectionState);
            if (connectionState == HostSystemConnectionState.connected) {

                // discover initiators
                List<Initiator> oldInitiators = new ArrayList<Initiator>();
                List<Initiator> addedInitiators = new ArrayList<Initiator>();
                discoverConnectedHostInitiators(source, target, oldInitiators, addedInitiators);

                URI targetCluster = cluster != null ? cluster.getId() : NullColumnValueGetter.getNullURI();

                boolean isClusterChanged = NullColumnValueGetter.isNullURI(oldClusterURI) ? !NullColumnValueGetter.isNullURI(targetCluster)
                        : targetCluster != null && !oldClusterURI.toString().equals(targetCluster.toString());

                if (!oldInitiators.isEmpty() || !addedInitiators.isEmpty() || isClusterChanged || isDatacenterChanged) {
                    changes.add(new HostStateChange(target, oldClusterURI, targetCluster, oldInitiators, addedInitiators, oldDatacenterURI,
                            newDatacenterURI));
                }
            }
            else {
                if (connectionState == HostSystemConnectionState.disconnected) {
                    throw new IllegalStateException("Host is disconnected");
                }
                else if (connectionState == HostSystemConnectionState.notResponding) {
                    throw new IllegalStateException("Host is not responding");
                }
                else {
                    throw new IllegalStateException("Could not determine host connection state");
                }
            }
        }

        protected String getClusterName(HostSystem host) {
            if (host.getParent() instanceof ClusterComputeResource) {
                return host.getParent().getName();
            }
            else {
                return null;
            }
        }

        protected void setRegistrationStatus(String status) {
            Iterable<VcenterDataCenter> datacenters = getDatacenters(vcenter);
            for (VcenterDataCenter datacenter : datacenters) {
                setRegistrationStatus(datacenter, status);
            }
        }

        protected void setRegistrationStatus(VcenterDataCenter datacenter, String status) {
            Iterable<Host> hosts = getHosts(datacenter);
            for (Host host : hosts) {
                setRegistrationStatus(host, status);
            }
        }

        protected void setRegistrationStatus(Host host, String status) {
            host.setRegistrationStatus(status);
            save(host);
            setRegistrationStatus(getInitiators(host), status);
            setRegistrationStatus(getIpInterfaces(host), status);
        }

        protected void setRegistrationStatus(Iterable<? extends HostInterface> hostInterfaces, String status) {
            for (HostInterface hostInterface : hostInterfaces) {
                hostInterface.setRegistrationStatus(status);
                save(hostInterface);
            }
        }

        protected void setCompatibilityStatus(String status) {
            vcenter.setCompatibilityStatus(status);
            save(vcenter);

            Iterable<VcenterDataCenter> datacenters = getDatacenters(vcenter);
            for (VcenterDataCenter datacenter : datacenters) {
                setCompatibilityStatus(datacenter, status);
            }
        }

        protected void setCompatibilityStatus(VcenterDataCenter datacenter, String status) {
            Iterable<Host> hosts = getHosts(datacenter);
            for (Host host : hosts) {
                setCompatibilityStatus(host, status);
            }
        }

        protected void setCompatibilityStatus(Host host, String status) {
            host.setCompatibilityStatus(status);
            save(host);
        }

        /**
         * This just to allow sorting of clusters by host count.
         */
        class ClusterHolder implements Comparable<ClusterHolder> {
            public ClusterComputeResource cluster;
            public int hostCount;

            public ClusterHolder(ClusterComputeResource c, int num) {
                cluster = c;
                hostCount = num;
            }

            @Override
            public int compareTo(ClusterHolder arg) {
                return arg.hostCount - this.hostCount;
            }
        }

        /**
         * Sets the vCenterDataCenter's tenant based on the vCenter's tenant.
         * If the vCenter is created by the tenant admin and if vCenter is shared
         * with only one tenant and vCenterDataCenter does not belong to any tenant
         * already then set the vCenter's tenant to the vCenterDataCenter and set to
         * null if vCenter is shared with multiple tenants.
         * If the vCenterDataCenter is already assigned to a tenant but the vCenter
         * is shared with that tenant anymore then reset the vCenterDataCenters tenant
         * to null.
         *
         * @param target vCenterDataCenter to be updated with the new tenant information.
         */
        private void setVcenterDataCenterTenant(VcenterDataCenter target) {
            if (NullColumnValueGetter.isNullURI(target.getTenant())) {
                if (vcenter.getCascadeTenancy()) {
                    target.setTenant(BasePermissionsHelper.getTenant(vcenter.getAcls()));
                } else {
                    target.setTenant(NullColumnValueGetter.getNullURI());
                }
            } else {
                Set<URI> vCenterTenants = BasePermissionsHelper.getUsageURIsFromAcls(vcenter.getAcls());
                if (CollectionUtils.isEmpty(vCenterTenants) ||
                        !vCenterTenants.contains(target.getTenant())) {
                    target.setTenant(NullColumnValueGetter.getNullURI());
                }
            }
            getLog().debug("vCenterDataCenter {} is updated with tenant {}", target.getLabel(), target.getTenant());
        }
    }

    public void setDbCLient(DbClient dbClient) {
        super.setDbClient(dbClient);
    }

    protected void checkDuplicateVcenter(Vcenter vcenter, String nativeGuid) {
        if (nativeGuid != null && !nativeGuid.isEmpty()) {
            for (Vcenter existingVcenter : modelClient.vcenters().findByNativeGuid(nativeGuid, true)) {
                if (!existingVcenter.getId().equals(vcenter.getId())) {
                    ComputeSystemControllerException ex =
                            ComputeSystemControllerException.exceptions.duplicateSystem("vCenter", existingVcenter.getLabel());
                    DiscoveryStatusUtils.markAsFailed(modelClient, vcenter, ex.getMessage(), ex);
                    throw ex;
                }
            }
        }
    }

    /**
     * Sets properties pertaining to the {@link Initiator}
     * 
     * @param initiator {@link Initiator}
     * @param host {@link Host}
     */
    protected void setInitiatorHost(Initiator initiator, Host host) {
        setHostInterfaceRegistrationStatus(initiator, host);
        initiator.setHost(host.getId());
        initiator.setHostName(host.getHostName());
        Cluster cluster = getCluster(host);
        initiator.setClusterName(cluster != null ? cluster.getLabel() : "");
    }

    protected Cluster getCluster(Host host) {
        if (host.getCluster() != null) {
            return get(Cluster.class, host.getCluster());
        }
        else {
            return null;
        }
    }
}
