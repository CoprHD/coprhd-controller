/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.addAffectedResource;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addInjectedValue;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.addRollback;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.execute;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logError;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logWarn;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionException;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.machinetags.KnownMachineTags;
import com.emc.sa.service.vipr.block.tasks.GetBlockVolumeByWWN;
import com.emc.sa.service.vipr.block.tasks.RemoveBlockVolumeMachineTag;
import com.emc.sa.service.vipr.block.tasks.SetBlockVolumeMachineTag;
import com.emc.sa.service.vipr.file.tasks.FindFilesystemWithDatastore;
import com.emc.sa.service.vipr.tasks.GetCluster;
import com.emc.sa.service.vipr.tasks.GetHost;
import com.emc.sa.service.vmware.block.tasks.CreateVVolDatastore;
import com.emc.sa.service.vmware.block.tasks.CreateVmfsDatastore;
import com.emc.sa.service.vmware.block.tasks.DetachLunsFromHost;
import com.emc.sa.service.vmware.block.tasks.ExpandVmfsDatastore;
import com.emc.sa.service.vmware.block.tasks.ExtendVmfsDatastore;
import com.emc.sa.service.vmware.block.tasks.FindHostScsiDiskForLun;
import com.emc.sa.service.vmware.block.tasks.FindLunsBackingDatastore;
import com.emc.sa.service.vmware.block.tasks.RefreshStorage;
import com.emc.sa.service.vmware.block.tasks.SetMultipathPolicy;
import com.emc.sa.service.vmware.block.tasks.SetStorageIOControl;
import com.emc.sa.service.vmware.block.tasks.UnmountVmfsDatastore;
import com.emc.sa.service.vmware.block.tasks.VerifyDatastoreHostMounts;
import com.emc.sa.service.vmware.file.tasks.CreateNfsDatastore;
import com.emc.sa.service.vmware.file.tasks.GetEndpoints;
import com.emc.sa.service.vmware.file.tasks.TagDatastoreOnFilesystem;
import com.emc.sa.service.vmware.file.tasks.UntagDatastoreOnFilesystem;
import com.emc.sa.service.vmware.tasks.ConnectToVCenter;
import com.emc.sa.service.vmware.tasks.DeleteDatastore;
import com.emc.sa.service.vmware.tasks.EnterMaintenanceMode;
import com.emc.sa.service.vmware.tasks.FindCluster;
import com.emc.sa.service.vmware.tasks.FindDatastore;
import com.emc.sa.service.vmware.tasks.FindESXHost;
import com.emc.sa.service.vmware.tasks.GetVcenter;
import com.emc.sa.service.vmware.tasks.GetVcenterDataCenter;
import com.emc.sa.service.vmware.tasks.VerifyDatastoreDoesNotExist;
import com.emc.sa.service.vmware.tasks.VerifyDatastoreForRemoval;
import com.emc.sa.service.vmware.tasks.VerifySupportedMultipathPolicy;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExportParam;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.DatastoreSummaryMaintenanceModeState;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class VMwareSupport {
    private URI vcenterId;
    private VCenterAPI vcenterAPI;

    public void connect(URI vcenterId) {
        this.vcenterId = vcenterId;
        Vcenter vcenter = getVcenter(vcenterId);
        vcenterAPI = execute(new ConnectToVCenter(vcenter));
        addInjectedValue(VCenterAPI.class, vcenterAPI);
    }

    public void disconnect() {
        if (vcenterAPI != null) {
            vcenterAPI.logout();
            vcenterAPI = null;
        }
    }

    public Vcenter getVcenter(URI vcenterId) {
        return execute(new GetVcenter(vcenterId));
    }

    public VcenterDataCenter getDatacenter(URI datacenterId) {
        return execute(new GetVcenterDataCenter(datacenterId));
    }

    public Host getESXHost(URI hostId) {
        return execute(new GetHost(hostId));
    }

    public Cluster getCluster(URI clusterId) {
        if (NullColumnValueGetter.isNullURI(clusterId)) {
            return null;
        }
        return execute(new GetCluster(clusterId));
    }

    public HostSystem getHostSystem(String datacenterName, String esxHostName) {
        return execute(new FindESXHost(datacenterName, esxHostName));
    }

    public ClusterComputeResource getCluster(String datacenterName, String clusterName) {
        return execute(new FindCluster(datacenterName, clusterName));
    }

    public Datastore getDatastore(String datacenterName, String datastoreName) {
        return execute(new FindDatastore(datacenterName, datastoreName));
    }

    public Set<String> getEndpoints(HostSystem host, ClusterComputeResource cluster) {
        return execute(new GetEndpoints(host, cluster));
    }

    /**
     * Verify that the datastore does not exist.
     * 
     * @param datacenterName
     *            the datacenter name.
     * @param datastoreName
     *            the datastore name.
     */
    public void verifyDatastoreDoesNotExist(String datacenterName, String datastoreName) {
        execute(new VerifyDatastoreDoesNotExist(datacenterName, datastoreName));
    }

    /**
     * Verify that the multipath policy is supported on a host.
     * 
     * @param host
     *            the host.
     * @param multipathPolicy
     *            the multipath policy.
     */
    public void verifySupportedMultipathPolicy(HostSystem host, String multipathPolicy) {
        if (VMwareUtils.isValidMultipathPolicy(multipathPolicy)) {
            execute(new VerifySupportedMultipathPolicy(host, multipathPolicy));
        }
    }

    /**
     * Performs various checks to see if the datastore should be able to be removed.
     * 
     * @param datastore
     *            the datastore.
     */
    public void verifyDatastoreForRemoval(Datastore datastore) {
        execute(new VerifyDatastoreForRemoval(datastore));
    }

    /**
     * Creates a VMFS datastore.
     * 
     * @param host
     *            the host to which the volume is assigned.
     * @param cluster
     *            the cluster to which the volume is associated (may be null if the storage is exclusive to the host)
     * @param volume
     *            the volume to create the datastore on.
     * @param datastoreName
     *            the name of the datastore to create.
     * @return datastore
     */
    public Datastore createVmfsDatastore(HostSystem host, ClusterComputeResource cluster, URI hostOrClusterId,
            BlockObjectRestRep volume, String datastoreName) {
        HostScsiDisk disk = findScsiDisk(host, cluster, volume);
        Datastore datastore = execute(new CreateVmfsDatastore(host, disk, datastoreName));
        addAffectedResource(volume);
        addVmfsDatastoreTag(volume, hostOrClusterId, datastoreName);
        ExecutionUtils.clearRollback();

        return datastore;
    }

    
    public Datastore createVVOLDatastore(HostSystem host, ClusterComputeResource cluster, 
            BlockObjectRestRep volume, String datastoreName) {
        Datastore datastore = execute(new CreateVVolDatastore(host,datastoreName));
        ExecutionUtils.clearRollback();

        return datastore;
    }
    /**
     * Sets the multipath policy on the disks for the given host/cluster
     * 
     * @param host
     *            the host to which the volume is assigned.
     * @param cluster
     *            the cluster to which the volume is associated (may be null if the storage is exclusive to the host)
     * @param multipathPolicy
     *            the multipath policy to use.
     * @param volume
     *            the volume with the created datastore.
     */
    public void setMultipathPolicy(HostSystem host, ClusterComputeResource cluster, String multipathPolicy,
            BlockObjectRestRep volume) {
        if (VMwareUtils.isValidMultipathPolicy(multipathPolicy)) {
            Map<HostSystem, HostScsiDisk> hostDisks = Maps.newHashMap();
            if (cluster != null) {
                List<HostSystem> clusterHosts = Lists.newArrayList(cluster.getHosts());
                for (HostSystem clusterHost : clusterHosts) {
                    HostScsiDisk disk = execute(new FindHostScsiDiskForLun(clusterHost, volume));
                    hostDisks.put(clusterHost, disk);
                }
            } else if (host != null) {
                HostScsiDisk disk = execute(new FindHostScsiDiskForLun(host, volume));
                hostDisks.put(host, disk);
            }

            if (hostDisks.size() > 0) {
                execute(new SetMultipathPolicy(hostDisks, multipathPolicy));
            }
        }

    }

    public void detachLuns(HostSystem host, List<HostScsiDisk> disks) {
        execute(new DetachLunsFromHost(host, disks));
    }

    public void unmountVmfsDatastore(HostSystem host, Datastore datastore) {
        execute(new UnmountVmfsDatastore(host, datastore));
    }

    /**
     * Sets the storage IO control for the given datastore
     * 
     * @param datastore
     *            the datastore to set the storage io control on
     * @param enabled
     *            true to enable storage io control or false to disable storage io control
     */
    public void setStorageIOControl(Datastore datastore, Boolean enabled) {
        if (enabled != null) {
            if (datastore.getCapability().storageIORMSupported) {
                execute(new SetStorageIOControl(datastore, enabled));
            } else {
                logWarn("vmware.support.storage.io.control.not.supported", datastore.getName());
            }
        }
    }

    /**
     * Extends a VMFS datastore.
     * 
     * @param host
     *            the host to which the volume is assigned.
     * @param cluster
     *            the cluster to which the volume is associated (may be null if the storage is exclusive to the host)
     * @param volume
     *            the volume to extend the datastore with.
     * @param datastore
     *            the datastore to extend.
     */
    public void extendVmfsDatastore(HostSystem host, ClusterComputeResource cluster, URI hostOrClusterId,
            BlockObjectRestRep volume, Datastore datastore) {
        HostScsiDisk disk = findScsiDisk(host, cluster, volume);
        execute(new ExtendVmfsDatastore(host, disk, datastore));
        addAffectedResource(volume);
        addVmfsDatastoreTag(volume, hostOrClusterId, datastore.getName());
        ExecutionUtils.clearRollback();
    }

    /**
     * Expand a VMFS datastore.
     * 
     * @param host
     *            the host to which the volume is assigned.
     * @param cluster
     *            the cluster to which the volume is associated (may be null if the storage is exclusive to the host)
     * @param volume
     *            the volume that was expanded.
     * @param datastore
     *            the datastore to expand.
     */
    public void expandVmfsDatastore(HostSystem host, ClusterComputeResource cluster, URI hostOrClusterId,
            BlockObjectRestRep volume, Datastore datastore) {
        HostScsiDisk disk = findScsiDisk(host, cluster, volume);
        execute(new ExpandVmfsDatastore(host, disk, datastore));
        addAffectedResource(volume);
        addVmfsDatastoreTag(volume, hostOrClusterId, datastore.getName());
        ExecutionUtils.clearRollback();
    }

    /**
     * Verifies that the datastore host configuration matches the host/cluster configuration.
     * 
     * @param host
     *            the host
     * @param cluster
     *            the cluster, will be null when using exclusive storage
     * @param datastore
     *            the datastore to verify.
     */
    public void verifyDatastoreMounts(HostSystem host, ClusterComputeResource cluster, Datastore datastore) {
        execute(new VerifyDatastoreHostMounts(host, cluster, datastore));
    }

    /**
     * Puts the datastore into maintenance mode, if required.
     * 
     * @param datastore
     *            the datastore.
     */
    public void enterMaintenanceMode(Datastore datastore) {
        String maintenanceMode = datastore.getSummary().getMaintenanceMode();
        // Only attempt to enter maintenance mode if the state is unknown (null) or normal
        if ((maintenanceMode == null) || maintenanceMode.equals(DatastoreSummaryMaintenanceModeState.normal.name())) {
            execute(new EnterMaintenanceMode(datastore));
        }
    }

    /**
     * Deletes a VMFS datastore. Because VMFS datastores are shared across hosts, it only needs to be deleted from a
     * single host for it to be deleted.
     * 
     * @param volumes
     *            the volumes backing the datastore.
     * @param hostOrClusterId
     *            the id of the host or cluster where the datastore is mounted.
     * @param datastore
     *            the datastore to delete
     * @param detachLuns
     *            if true, detach the luns from each host
     */
    public void deleteVmfsDatastore(Collection<VolumeRestRep> volumes, URI hostOrClusterId, final Datastore datastore, boolean detachLuns) {
        List<HostSystem> hosts = getHostsForDatastore(datastore);
        if (hosts.isEmpty()) {
            throw new IllegalStateException("Datastore is not mounted by any hosts");
        }

        enterMaintenanceMode(datastore);
        setStorageIOControl(datastore, false);

        executeOnHosts(hosts, new HostSystemCallback() {
            @Override
            public void exec(HostSystem host) {
                unmountVmfsDatastore(host, datastore);
            }
        });

        final Map<HostSystem, List<HostScsiDisk>> hostDisks = buildHostDiskMap(hosts, datastore);

        execute(new DeleteDatastore(hosts.get(0), datastore));

        if (detachLuns) {
            executeOnHosts(hosts, new HostSystemCallback() {
                @Override
                public void exec(HostSystem host) {
                    List<HostScsiDisk> disks = hostDisks.get(host);
                    detachLuns(host, disks);
                }
            });
        }
        removeVmfsDatastoreTag(volumes, hostOrClusterId);
    }

    private Map<HostSystem, List<HostScsiDisk>> buildHostDiskMap(List<HostSystem> hosts, Datastore datastore) {

        Map<HostSystem, List<HostScsiDisk>> hostDisks = new HashMap<>();

        for (HostSystem host : hosts) {
            List<HostScsiDisk> disks = new HostStorageAPI(host).listDisks(datastore);
            hostDisks.put(host, disks);
        }
        return hostDisks;
    }

    private void executeOnHosts(List<HostSystem> hosts, HostSystemCallback callback) {
        for (HostSystem host : hosts) {
            callback.exec(host);
        }
    }

    private interface HostSystemCallback {
        public void exec(HostSystem host);
    }

    /**
     * Creates an NFS datastore for the hosts in the cluster
     * 
     * @param cluster
     *            the cluster.
     * @param fileSystem
     *            the file system.
     * @param export
     *            the export.
     * @param datacenterId
     *            the datacenter ID.
     * @param datastoreName
     *            the name of the datastore to create.
     * @return datastores
     */
    public List<Datastore> createNfsDatastore(ClusterComputeResource cluster, FileShareRestRep fileSystem,
            FileSystemExportParam export, URI datacenterId, String datastoreName) {
        addNfsDatastoreTag(fileSystem, export, datacenterId, datastoreName);
        List<Datastore> datastores = Lists.newArrayList();

        String fileServer = StringUtils.substringBefore(export.getMountPoint(), ":");
        String mountPath = StringUtils.substringAfter(export.getMountPoint(), ":");
        for (HostSystem host : cluster.getHosts()) {
            datastores.add(execute(new CreateNfsDatastore(host, fileServer, mountPath, datastoreName)));
            addAffectedResource(fileSystem);
            ExecutionUtils.clearRollback();
        }
        return datastores;
    }

    /**
     * Creates an NFS datastore for a host.
     * 
     * @param host
     *            the host.
     * @param fileSystem
     *            the file system.
     * @param export
     *            the export.
     * @param datacenterId
     *            the datacenter ID.
     * @param datastoreName
     *            the name of the datastore to create.
     * @return datastore
     */
    public Datastore createNfsDatastore(HostSystem host, FileShareRestRep fileSystem, FileSystemExportParam export,
            URI datacenterId, String datastoreName) {
        addNfsDatastoreTag(fileSystem, export, datacenterId, datastoreName);

        String fileServer = StringUtils.substringBefore(export.getMountPoint(), ":");
        String mountPath = StringUtils.substringAfter(export.getMountPoint(), ":");
        Datastore datastore = execute(new CreateNfsDatastore(host, fileServer, mountPath, datastoreName));
        addAffectedResource(fileSystem);
        ExecutionUtils.clearRollback();
        return datastore;
    }

    /**
     * Deletes an NFS datastore. The datastore will be removed from all hosts.
     * 
     * @param datastore
     *            the NFS datastore.
     */
    public void deleteNfsDatastore(FileShareRestRep fileSystem, URI datacenterId, Datastore datastore) {
        String datastoreName = datastore.getName();
        List<HostSystem> hosts = getHostsForDatastore(datastore);
        if (hosts.isEmpty()) {
            throw new IllegalStateException("Datastore is not mounted by any hosts");
        }
        enterMaintenanceMode(datastore);
        setStorageIOControl(datastore, false);
        for (HostSystem host : hosts) {
            execute(new DeleteDatastore(host, datastore));
        }
        removeNfsDatastoreTag(fileSystem, datacenterId, datastoreName);
    }

    /**
     * Gets the hosts for the given datastore.
     * 
     * @param datastore
     *            the datastore.
     * @return the hosts.
     */
    public List<HostSystem> getHostsForDatastore(Datastore datastore) {
        List<HostSystem> hosts = Lists.newArrayList();
        DatastoreHostMount[] hostMounts = datastore.getHost();
        if (hostMounts != null) {
            for (DatastoreHostMount hostMount : hostMounts) {
                HostSystem host = vcenterAPI.lookupManagedEntity(hostMount.key);
                if (host != null) {
                    hosts.add(host);
                }
            }
        }
        return hosts;
    }

    /**
     * Finds the file system containing the datastore.
     * 
     * @param project
     *            the project.
     * @param datacenterId
     *            the datacenter name.
     * @param datastoreName
     *            the datastore name.
     * @return the file system containing the datastore.
     */
    public FileShareRestRep findFileSystemWithDatastore(URI project, URI datacenterId, String datastoreName) {
        return execute(new FindFilesystemWithDatastore(project, vcenterId, datacenterId, datastoreName));
    }

    /**
     * Refreshes storage for the cluster or host.
     * 
     * @param host
     *            the host to refresh.
     * @param cluster
     *            the cluster (may be null).
     */
    public void refreshStorage(HostSystem host, ClusterComputeResource cluster) {
        if (cluster != null) {
            refreshStorage(cluster);
        }
        else {
            refreshStorage(host);
        }
    }

    /**
     * Refreshes the storage for all hosts in the cluster.
     * 
     * @param cluster
     *            the cluster to refresh.
     */
    public void refreshStorage(ClusterComputeResource cluster) {
        List<HostSystem> hosts = Lists.newArrayList(cluster.getHosts());
        refreshStorage(hosts);
    }

    /**
     * Refreshes the storage for the given host.
     * 
     * @param host
     *            the host to refresh.
     */
    public void refreshStorage(HostSystem host) {
        List<HostSystem> hosts = Lists.newArrayList(host);
        refreshStorage(hosts);
    }

    /**
     * Refreshes the storage for a given list of hosts.
     * 
     * @param hosts
     *            the hosts to refresh;
     */
    public void refreshStorage(List<HostSystem> hosts) {
        if (!hosts.isEmpty()) {
            execute(new RefreshStorage(hosts));
        }
    }

    /**
     * Refreshes the storage for all hosts associated with the datastore.
     * 
     * @param datastore
     *            the datastore.
     */
    public void refreshStorage(Datastore datastore) {
        List<HostSystem> hosts = VMwareUtils.getHostsForDatastore(vcenterAPI, datastore);
        refreshStorage(hosts);
    }

    /**
     * Finds the SCSI disk on the host system that matches the volume.
     * 
     * @param host
     *            the host system.
     * @param volume
     *            the volume to find.
     * @return the disk for the volume.
     */
    public HostScsiDisk findScsiDisk(HostSystem host, ClusterComputeResource cluster, BlockObjectRestRep volume) {
        // Ensure that the volume has a WWN set or we won't be able to find the disk
        if (StringUtils.isBlank(volume.getWwn())) {
            String volumeId = ResourceUtils.stringId(volume);
            String volumeName = ResourceUtils.name(volume);
            ExecutionUtils.fail("failTask.VMwareSupport.findLun", new Object[] { volumeId }, new Object[] { volumeName });
        }
        HostScsiDisk disk = execute(new FindHostScsiDiskForLun(host, volume));

        // Find the volume on all other hosts in the cluster
        if (cluster != null) {
            HostSystem[] hosts = cluster.getHosts();
            if (hosts == null) {
                throw new IllegalStateException("Cluster '" + cluster.getName() + "' contains no hosts");
            }
            Map<HostSystem, HostScsiDisk> disks = Maps.newHashMap();
            disks.put(host, disk);
            for (HostSystem otherHost : hosts) {
                if (StringUtils.equals(host.getName(), otherHost.getName())) {
                    continue;
                }
                HostScsiDisk otherDisk = execute(new FindHostScsiDiskForLun(otherHost, volume));
                disks.put(otherHost, otherDisk);
            }
        }

        return disk;
    }

    /**
     * Adds a tag associating the volumes to the datastore.
     * 
     * @param volumes
     *            the volumes.
     * @param datastoreName
     *            the datastore name.
     */
    public void addVmfsDatastoreTag(Collection<BlockObjectRestRep> volumes, URI hostOrClusterId, String datastoreName) {
        for (BlockObjectRestRep volume : volumes) {
            addVmfsDatastoreTag(volume, hostOrClusterId, datastoreName);
        }

    }

    /**
     * Adds a tag to the volume associating it with a datastore.
     * 
     * @param volume
     *            the volume to tag.
     * @param datastoreName
     *            the name of the datastore to associate.
     */
    public void addVmfsDatastoreTag(BlockObjectRestRep volume, URI hostOrClusterId, String datastoreName) {
        execute(new SetBlockVolumeMachineTag(volume.getId(), KnownMachineTags.getVMFSDatastoreTagName(hostOrClusterId),
                datastoreName));
        addRollback(new RemoveBlockVolumeMachineTag(volume.getId(),
                KnownMachineTags.getVMFSDatastoreTagName(hostOrClusterId)));
        addAffectedResource(volume);
    }

    /**
     * Removes the VMFS datastore tag from the volumes.
     * 
     * @param volumes
     *            the volumes to remove the tag from.
     */
    public void removeVmfsDatastoreTag(Collection<? extends BlockObjectRestRep> volumes, URI hostOrClusterId) {
        for (BlockObjectRestRep volume : volumes) {
            removeVmfsDatastoreTag(volume, hostOrClusterId);
        }
    }

    /**
     * Removes a datastore tag from the given volume.
     * 
     * @param volume
     *            the volume to remove the tag from.
     */
    public void removeVmfsDatastoreTag(BlockObjectRestRep volume, URI hostOrClusterId) {
        execute(new RemoveBlockVolumeMachineTag(volume.getId(),
                KnownMachineTags.getVMFSDatastoreTagName(hostOrClusterId)));
        addAffectedResource(volume);
    }

    /**
     * Tags the fileshare with the NFS datastore information.
     * 
     * @param fileSystem
     * @param export
     * @param datacenterId
     * @param datastoreName
     */
    public void addNfsDatastoreTag(FileShareRestRep fileSystem, FileSystemExportParam export, URI datacenterId,
            String datastoreName) {
        execute(new TagDatastoreOnFilesystem(fileSystem.getId(), vcenterId, datacenterId, datastoreName,
                export.getMountPoint()));
        addRollback(new UntagDatastoreOnFilesystem(fileSystem.getId(), vcenterId, datacenterId, datastoreName));
        addAffectedResource(fileSystem);
    }

    /**
     * Removes the NFS datastore tag from the filesystem.
     * 
     * @param fileSystem
     * @param datacenterId
     * @param datastoreName
     */
    public void removeNfsDatastoreTag(FileShareRestRep fileSystem, URI datacenterId, String datastoreName) {
        execute(new UntagDatastoreOnFilesystem(fileSystem.getId(), vcenterId, datacenterId, datastoreName));
        addAffectedResource(fileSystem);
    }

    /**
     * Finds the volumes backing the datastore.
     * 
     * @param host
     *            the actual host system
     * @param datastore
     *            the datastore.
     * @return the volumes backing the host system.
     */
    public List<VolumeRestRep> findVolumesBackingDatastore(HostSystem host, Datastore datastore) {
        Set<String> luns = execute(new FindLunsBackingDatastore(host, datastore));
        List<VolumeRestRep> volumes = Lists.newArrayList();
        for (String lun : luns) {
            VolumeRestRep volume = execute(new GetBlockVolumeByWWN(lun));
            if (volume != null) {
                volumes.add(volume);
            }
        }
        return volumes;
    }

    /**
     * Checks the exception cause for VMware specific faults.
     * 
     * @param e
     *            the execution exception.
     * @return an execution exception to be rethrown.
     */
    public ExecutionException handleError(ExecutionException e) {
        // find the root cause of this exception
        Throwable cause = e.getCause();
        if (cause instanceof VMWareException) {
            cause = ((VMWareException) cause).getCause();
        }

        // log the error and return a new exception that uses the fault
        // message from the VMWare MethodFault as the main message.
        if (cause instanceof MethodFault) {
            String faultMessage = VMwareUtils.getFaultMessage((MethodFault) cause);
            logError(cause, "vmware.support.method.fault", cause.getClass(), faultMessage);
            return new ExecutionException(cause);
        }

        return e;
    }

    /**
     * Unmount the datastore from the host or hosts in the cluster
     * 
     * @param host host to unmount the datastore from. if null, use cluster's hosts
     * @param cluster cluster to unmount the datastore from
     * @param datastore the datastore to unmount
     */
    public void unmountVmfsDatastore(HostSystem host, ClusterComputeResource cluster,
            final Datastore datastore) {
        enterMaintenanceMode(datastore);
        setStorageIOControl(datastore, false);
        List<HostSystem> hosts = host != null ? Lists.newArrayList(host) : Lists.newArrayList(cluster.getHosts());

        executeOnHosts(hosts, new HostSystemCallback() {
            @Override
            public void exec(HostSystem host) {
                unmountVmfsDatastore(host, datastore);
            }
        });
    }

    /**
     * Detach the volume from the host or hosts in the cluster
     * 
     * @param host host to detach the volume. if null, use cluster's hosts
     * @param cluster cluster to detach the volume
     * @param volume the volume to detach
     */
    public void detachLuns(HostSystem host, ClusterComputeResource cluster, BlockObjectRestRep volume) {
        final HostScsiDisk disk = findScsiDisk(host, cluster, volume);
        List<HostSystem> hosts = host != null ? Lists.newArrayList(host) : Lists.newArrayList(cluster.getHosts());

        executeOnHosts(hosts, new HostSystemCallback() {
            @Override
            public void exec(HostSystem host) {
                detachLuns(host, Collections.singletonList(disk));
            }
        });
    }
}
