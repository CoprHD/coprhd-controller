/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vmware.vim25.AlreadyExists;
import com.vmware.vim25.DuplicateName;
import com.vmware.vim25.HostConfigFault;
import com.vmware.vim25.HostDatastoreSystemVvolDatastoreSpec;
import com.vmware.vim25.HostFibreChannelHba;
import com.vmware.vim25.HostFibreChannelTargetTransport;
import com.vmware.vim25.HostFileSystemMountInfo;
import com.vmware.vim25.HostHostBusAdapter;
import com.vmware.vim25.HostInternetScsiHba;
import com.vmware.vim25.HostInternetScsiHbaSendTarget;
import com.vmware.vim25.HostInternetScsiTargetTransport;
import com.vmware.vim25.HostMultipathInfoFixedLogicalUnitPolicy;
import com.vmware.vim25.HostMultipathInfoLogicalUnitPolicy;
import com.vmware.vim25.HostNasVolumeSpec;
import com.vmware.vim25.HostPathSelectionPolicyOption;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.HostScsiDiskPartition;
import com.vmware.vim25.HostScsiTopology;
import com.vmware.vim25.HostScsiTopologyInterface;
import com.vmware.vim25.HostScsiTopologyLun;
import com.vmware.vim25.HostScsiTopologyTarget;
import com.vmware.vim25.HostStorageDeviceInfo;
import com.vmware.vim25.HostTargetTransport;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.NotFound;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.ScsiLun;
import com.vmware.vim25.VmfsDatastoreCreateSpec;
import com.vmware.vim25.VmfsDatastoreExpandSpec;
import com.vmware.vim25.VmfsDatastoreExtendSpec;
import com.vmware.vim25.VmfsDatastoreInfo;
import com.vmware.vim25.VmfsDatastoreOption;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostDatastoreSystem;
import com.vmware.vim25.mo.HostStorageSystem;
import com.vmware.vim25.mo.HostSystem;

/**
 * API for host storage operations using VMware.
 * 
 * @author jonnymiller
 */
public class HostStorageAPI {

    private static final String VMW_PSP_RR = "VMW_PSP_RR";
    private static final String VMW_PSP_MRU = "VMW_PSP_MRU";
    private static final String VMW_PSP_FIXED = "VMW_PSP_FIXED";
    public static final List<String> MULTIPATH_POLICY_TYPES = Arrays.asList(VMW_PSP_RR, VMW_PSP_MRU, VMW_PSP_FIXED);

    private HostSystem host;

    public HostStorageAPI(HostSystem host) {
        this.host = host;
    }

    public HostSystem getHostSystem() {
        return host;
    }

    /**
     * Gets the host storage system for the host.
     * 
     * @return the host storage system.
     */
    public HostStorageSystem getStorageSystem() {
        try {
            return host.getHostStorageSystem();
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets the host datastore system for the host.
     * 
     * @return the host datastore system.
     */
    public HostDatastoreSystem getDatastoreSystem() {
        try {
            return host.getHostDatastoreSystem();
        } catch (InvalidProperty e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets supported multipath policies
     * 
     * @return list of multipath policies
     */
    public HostPathSelectionPolicyOption[] getPathSelectionPolicyOptions() {
        try {
            HostStorageSystem storageSystem = getStorageSystem();
            return storageSystem.queryPathSelectionPolicyOptions();
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Sets the multipath policy on the given lun
     * 
     * @param lun the lun to set the policy on
     * @param multipathPolicy name of the multipath policy
     */
    public void setMultipathPolicy(HostScsiDisk lun, String multipathPolicy) {
        try {
            HostStorageSystem storageSystem = getStorageSystem();
            HostMultipathInfoLogicalUnitPolicy policy = createMultipathPolicy(multipathPolicy);
            storageSystem.setMultipathLunPolicy(lun.getUuid(), policy);
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Rescans the HBAs on the host.
     */
    public void rescanHBAs() {
        try {
            HostStorageSystem storageSystem = getStorageSystem();
            storageSystem.rescanAllHba();
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Refreshes the storage on the host.
     */
    public void refreshStorage() {
        try {
            HostStorageSystem storageSystem = getStorageSystem();
            storageSystem.rescanAllHba();
            storageSystem.rescanVmfs();
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets the host storage device info.
     * 
     * @return the host storage device info.
     */
    public HostStorageDeviceInfo getStorageDeviceInfo() {
        HostStorageSystem storageSystem = getStorageSystem();
        if (storageSystem != null) {
            return storageSystem.getStorageDeviceInfo();
        }
        else {
            return null;
        }
    }

    /**
     * Lists all SCSI disks on the host system.
     * 
     * @return the list of SCSI disks.
     */
    public List<HostScsiDisk> listScsiDisks() {
        List<HostScsiDisk> scsiDisks = Lists.newArrayList();
        HostStorageDeviceInfo storageDeviceInfo = getStorageDeviceInfo();
        if (storageDeviceInfo != null) {
            addItems(scsiDisks, storageDeviceInfo.getScsiLun(), HostScsiDisk.class);
        }
        return scsiDisks;
    }

    /**
     * Gets the mapping of SCSI disks, mapped by key.
     * 
     * @return the key to SCSI disk mapping.
     */
    public Map<String, HostScsiDisk> getScsiDisksByKey() {
        Map<String, HostScsiDisk> map = Maps.newLinkedHashMap();
        for (HostScsiDisk disk : listScsiDisks()) {
            map.put(disk.getKey(), disk);
        }
        return map;
    }

    /**
     * Gets teh mapping of SCSI disks, mapped by canonical name.
     * 
     * @return the name to SCSI disk mapping.
     */
    public Map<String, HostScsiDisk> getScsiDisksByCanonicalName() {
        Map<String, HostScsiDisk> map = Maps.newLinkedHashMap();
        for (HostScsiDisk disk : listScsiDisks()) {
            map.put(disk.getCanonicalName(), disk);
        }
        return map;
    }

    /**
     * Gets a SCSI disk by key.
     * 
     * @param key the disk key.
     * @return the SCSI disk.
     */
    public HostScsiDisk getScsiDiskByKey(String key) {
        HostStorageDeviceInfo storageDeviceInfo = getStorageDeviceInfo();
        if ((storageDeviceInfo != null) && (storageDeviceInfo.getScsiLun() != null)) {
            for (ScsiLun lun : storageDeviceInfo.getScsiLun()) {
                // Find the LUN with the matching key that is a HostScsiDisk
                if (StringUtils.equals(key, lun.getKey()) && (lun instanceof HostScsiDisk)) {
                    return (HostScsiDisk) lun;
                }
            }
        }
        return null;
    }

    /**
     * Finds the SCSI disk for the LUN that is associated with one or more targets, from the
     * specified sources. The sources and targets should be either port WWNs or iSCSI IQNs.
     * 
     * @param hlu the host LUN id.
     * @param sourceNames the source names (host HBAs) for the LUN.
     * @param targetNames the target names (array HBAs) for the LUN.
     * @return the SCSI disk.
     */
    public HostScsiDisk findLunDisk(int hlu, Collection<String> sourceNames,
            Collection<String> targetNames) {
        Map<String, HostHostBusAdapter> hbas = getHostBusAdapters();

        String diskKey = null;
        for (HostScsiTopologyInterface adapter : listScsiTopologyInterfaces()) {
            HostHostBusAdapter hba = hbas.get(adapter.getAdapter());
            String sourceName = getSourceName(hba);
            if (!sourceNames.contains(sourceName) || (adapter.getTarget() == null)) {
                continue;
            }

            for (HostScsiTopologyTarget target : adapter.getTarget()) {
                String targetName = getTargetName(target);
                if (!targetNames.contains(targetName)) {
                    continue;
                }

                HostScsiTopologyLun lun = findLun(target, hlu);
                if (lun != null) {
                    String key = lun.getScsiLun();
                    if (diskKey == null) {
                        diskKey = key;
                    }
                    // Make sure the LUNs all map to the same SCSI disk
                    else if (!StringUtils.equals(diskKey, key)) {
                        throw new IllegalArgumentException("LUN " + hlu
                                + " maps to different disks for " + targetNames);
                    }
                }
            }
        }

        if (diskKey == null) {
            return null;
        }
        HostScsiDisk disk = getScsiDiskByKey(diskKey);
        return disk;
    }

    /**
     * Lists the host bus adapters.
     * 
     * @return the list of host bus adapters.
     */
    public List<HostHostBusAdapter> listHostBusAdapters() {
        HostStorageDeviceInfo storageDeviceInfo = getStorageDeviceInfo();
        if (storageDeviceInfo != null) {
            return createList(storageDeviceInfo.getHostBusAdapter());
        }
        else {
            return Lists.newArrayList();
        }
    }

    /**
     * Gets a map of host bus adapters, mapped by key.
     * 
     * @return the key to host bus adapter mapping.
     */
    public Map<String, HostHostBusAdapter> getHostBusAdapters() {
        Map<String, HostHostBusAdapter> map = Maps.newLinkedHashMap();
        for (HostHostBusAdapter hba : listHostBusAdapters()) {
            map.put(hba.getKey(), hba);
        }
        return map;
    }

    /**
     * Finds the host bus adapter in the system with the specified key. This will query the host
     * each time, so if many host bus adapters need to be retrieved use {@link #getHostBusAdapters(HostSystem)} instead.
     * 
     * @param key the adapter key.
     * @return the host bus adapter.
     */
    public HostHostBusAdapter findHostBusAdapter(String key) {
        for (HostHostBusAdapter hba : listHostBusAdapters()) {
            if (StringUtils.equals(hba.getKey(), key)) {
                return hba;
            }
        }
        return null;
    }

    /**
     * Adds iSCSI send targets to the given host.
     * 
     * @param addresses
     */
    public void addInternetScsiSendTargets(HostInternetScsiHba hba, String... addresses) {
        addInternetScsiSendTargets(getStorageSystem(), hba, addresses);
    }

    /**
     * Adds iSCSI send targets to the host storage system.
     * 
     * @param storageSystem the storage system.
     * @param hba the iSCSI host bus adapter.
     * @param addresses the address of the targets.
     */
    public void addInternetScsiSendTargets(HostStorageSystem storageSystem,
            HostInternetScsiHba hba, String... addresses) {
        try {
            storageSystem.addInternetScsiSendTargets(hba.getDevice(),
                    createInternetScsiSendTargets(addresses));
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets the SCSI topology for the host.
     * 
     * @return the SCSI topology.
     */
    public HostScsiTopology getScsiTopology() {
        HostStorageDeviceInfo storageDeviceInfo = getStorageDeviceInfo();
        if (storageDeviceInfo != null) {
            return storageDeviceInfo.getScsiTopology();
        }
        else {
            return null;
        }
    }

    /**
     * Lists the SCSI topology interfaces for the given host.
     * 
     * @return the SCSI topology.
     */
    public List<HostScsiTopologyInterface> listScsiTopologyInterfaces() {
        HostScsiTopology scsiTopology = getScsiTopology();
        if (scsiTopology != null) {
            return createList(scsiTopology.getAdapter());
        }
        else {
            return Lists.newArrayList();
        }
    }

    /**
     * Lists the SCSI topology targets on the host system.
     * 
     * @return the list of SCSI topology targets.
     */
    public List<HostScsiTopologyTarget> listScsiTopologyTargets() {
        List<HostScsiTopologyTarget> targets = Lists.newArrayList();
        for (HostScsiTopologyInterface adapter : listScsiTopologyInterfaces()) {
            if (adapter.getTarget() == null) {
                continue;
            }
            addItems(targets, adapter.getTarget());
        }
        return targets;
    }

    /**
     * Creates a VMFS datastore.
     * 
     * @param disk the disk on which to create the datastore.
     * @param datastoreName the datastore name.
     */
    public Datastore createVmfsDatastore(HostScsiDisk disk, String datastoreName) {
        VmfsDatastoreCreateSpec createSpec = getVmfsDatastoreCreateSpec(disk, datastoreName);
        try {
            Datastore datastore = getDatastoreSystem().createVmfsDatastore(createSpec);
            return datastore;
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (DuplicateName e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }
    
    /**
     * to create vvol datastore
     * 
     * 
     */
    public Datastore createVVOLDatastore(String datastoreName) {
        try {
            HostDatastoreSystemVvolDatastoreSpec spec = new HostDatastoreSystemVvolDatastoreSpec();
            spec.setName("test");
            spec.setScId("scId");
			Datastore datastore = getDatastoreSystem().createVvolDatastore(spec);
            return datastore;
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (DuplicateName e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Extends a VMFS datastore.
     * 
     * @param disk the disk to use to extend the datastore.
     * @param datastore the datastore to extend.
     * @return the extended datastore.
     */
    public Datastore extendVmfsDatastore(HostScsiDisk disk, Datastore datastore) {
        VmfsDatastoreExtendSpec extendSpec = getVmfsDatastoreExtendSpec(disk, datastore);
        try {
            return getDatastoreSystem().extendVmfsDatastore(datastore, extendSpec);
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Expands a VMFS datastore on a given disk.
     * 
     * @param disk the disk on which the expansion should occur.
     * @param datastore the datastore.
     * @return the expanded datastore.
     */
    public Datastore expandVmfsDatastore(HostScsiDisk disk, Datastore datastore) {
        VmfsDatastoreExpandSpec expandSpec = getVmfsDatastoreExpandSpec(disk, datastore);
        try {
            return getDatastoreSystem().expandVmfsDatastore(datastore, expandSpec);
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Creates a NAS Datastore
     * 
     * @param datastoreName
     * @param remoteHost host name or IP of the NFS/CIFS server
     * @param remotePath path of the NAS storage. Ex. "/vol/mystorage"
     * @param accessMode
     * @param nasType Type of NAS storage CIFS or NFS
     * @param userName username for CIFS
     * @param password password for CIFS
     * @return
     */
    public Datastore createNASDatastore(String datastoreName, String remoteHost, String remotePath,
            final DataStoreAccessMode accessMode, final NasType nasType, final String userName,
            final String password) {
        HostNasVolumeSpec hnvs = getNASDatastoreCreateSpec(remoteHost, remotePath, datastoreName,
                accessMode, nasType, userName, password);

        try {
            return getDatastoreSystem().createNasDatastore(hnvs);
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (DuplicateName e) {
            throw new VMWareException(e);
        } catch (AlreadyExists e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Creates a read/write NFS datastore
     * 
     * @param datastoreName
     * @param remoteHost NFS server host name or IP
     * @param remotePath NFS server remote path
     * @return
     */
    public Datastore createNfsDatastore(String datastoreName, String remoteHost, String remotePath) {
        return createNASDatastore(datastoreName, remoteHost, remotePath,
                DataStoreAccessMode.readWrite, NasType.NFS, null, null);
    }

    /**
     * Deletes a datastore from the host.
     * 
     * @param datastore the VMFS datastore.
     */
    public void deleteDatastore(Datastore datastore) {
        try {
            getDatastoreSystem().removeDatastore(datastore);
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (ResourceInUse e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Query for the VMFS datastore create options.
     * 
     * @param disk the disk for the datastore.
     * @return the VMFS datastore create options.
     */
    public List<VmfsDatastoreOption> queryVmfsDatastoreCreateOptions(HostScsiDisk disk) {
        HostDatastoreSystem datastoreSystem = getDatastoreSystem();
        try {
            return createList(datastoreSystem.queryVmfsDatastoreCreateOptions(disk.getDevicePath()));
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets a VMFS datastore create spec for the given disk and datastore name.
     * 
     * @param disk the disk.
     * @param datastoreName the datastore name.
     * @return the VMFS datastore create spec.
     */
    public VmfsDatastoreCreateSpec getVmfsDatastoreCreateSpec(HostScsiDisk disk,
            String datastoreName) {
        List<VmfsDatastoreOption> createOptions = queryVmfsDatastoreCreateOptions(disk);
        VmfsDatastoreCreateSpec createSpec = pickBestCreateSpec(createOptions);
        if (createSpec == null) {
            throw new VMWareException("No VMFS datastore create spec. Volume may already contain a datastore.");
        }
        createSpec.getVmfs().setVolumeName(datastoreName);
        return createSpec;
    }

    /**
     * Picks the best create spec from the list of datastore options.
     * 
     * @param createOptions the list of create options.
     * @return the best datastore create spec.
     */
    public VmfsDatastoreCreateSpec pickBestCreateSpec(List<VmfsDatastoreOption> createOptions) {
        if ((createOptions == null) || createOptions.isEmpty()) {
            return null;
        }
        VmfsDatastoreCreateSpec bestSpec = (VmfsDatastoreCreateSpec) createOptions.get(0).getSpec();

        // Choose the create spec that uses the most recent VMFS version
        for (int i = 1; i < createOptions.size(); i++) {
            VmfsDatastoreOption createOption = createOptions.get(i);
            VmfsDatastoreCreateSpec currentSpec = (VmfsDatastoreCreateSpec) createOption.getSpec();
            if (currentSpec.getVmfs().getMajorVersion() > bestSpec.getVmfs().getMajorVersion()) {
                bestSpec = currentSpec;
            }
        }

        return bestSpec;
    }

    /**
     * Create a multipath policy based on the passed policy name
     * 
     * @param name policy name
     * @return multipath policy
     */
    public HostMultipathInfoLogicalUnitPolicy createMultipathPolicy(String name) {
        if (StringUtils.equalsIgnoreCase(name, VMW_PSP_FIXED)) {
            HostMultipathInfoFixedLogicalUnitPolicy policy = new HostMultipathInfoFixedLogicalUnitPolicy();
            policy.setPolicy(name);
            policy.setPrefer("");
            return policy;
        } else {
            HostMultipathInfoLogicalUnitPolicy policy = new HostMultipathInfoLogicalUnitPolicy();
            policy.setPolicy(name);
            return policy;
        }
    }

    /**
     * Query for the VMFS datastore extend options.
     * 
     * @param disk the disk for the datastore.
     * @param datastore the datastore.
     * @return the VMFS datastore extend options.
     */
    public List<VmfsDatastoreOption> queryVmfsDatastoreExtendOptions(HostScsiDisk disk,
            Datastore datastore) {
        HostDatastoreSystem datastoreSystem = getDatastoreSystem();
        try {
            return createList(datastoreSystem.queryVmfsDatastoreExtendOptions(datastore,
                    disk.getDevicePath(), true));
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets the first VMFS datastore extend spec.
     * 
     * @param disk the disk.
     * @param datastore the datastore.
     * @return the VMFS datastore extend spec.
     */
    public VmfsDatastoreExtendSpec getVmfsDatastoreExtendSpec(HostScsiDisk disk, Datastore datastore) {
        List<VmfsDatastoreOption> extendOptions = queryVmfsDatastoreExtendOptions(disk, datastore);
        VmfsDatastoreExtendSpec extendSpec = pickBestExtendSpec(extendOptions);
        if (extendSpec == null) {
            throw new VMWareException("No VMFS datastore extend spec");
        }
        return extendSpec;
    }

    /**
     * Picks the best extend spec. This just picks the first extend spec.
     * 
     * @param extendOptions the available extend options.
     * @return the extend spec.
     */
    public VmfsDatastoreExtendSpec pickBestExtendSpec(List<VmfsDatastoreOption> extendOptions) {
        if ((extendOptions == null) || extendOptions.isEmpty()) {
            return null;
        }
        VmfsDatastoreExtendSpec bestSpec = (VmfsDatastoreExtendSpec) extendOptions.get(0).getSpec();
        return bestSpec;
    }

    /**
     * Query for the VMFS datastore expand options.
     * 
     * @param datastore the datastore.
     * @return the VMFS datastore expand options.
     */
    public List<VmfsDatastoreOption> queryVmfsDatastoreExpandOptions(Datastore datastore) {
        HostDatastoreSystem datastoreSystem = getDatastoreSystem();
        try {
            return createList(datastoreSystem.queryVmfsDatastoreExpandOptions(datastore));
        } catch (HostConfigFault e) {
            throw new VMWareException(e);
        } catch (NotFound e) {
            throw new VMWareException(e);
        } catch (RuntimeFault e) {
            throw new VMWareException(e);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Gets the best VMFS datastore expand spec.
     * 
     * @param datastore the datastore.
     * @return the VMFS datastore expand spec.
     */
    public VmfsDatastoreExpandSpec getVmfsDatastoreExpandSpec(HostScsiDisk disk, Datastore datastore) {
        List<VmfsDatastoreOption> expandOptions = queryVmfsDatastoreExpandOptions(datastore);
        VmfsDatastoreExpandSpec extendSpec = pickBestExpandSpec(disk, expandOptions);
        if (extendSpec == null) {
            throw new VMWareException("No VMFS datastore expand spec");
        }
        return extendSpec;
    }

    /**
     * Picks the best expand spec. This picks an expand spec that uses the given disk.
     * 
     * @param disk the disk that was expanded.
     * @param expandOptions the available expand options.
     * @return the expand spec.
     */
    public VmfsDatastoreExpandSpec pickBestExpandSpec(HostScsiDisk disk, List<VmfsDatastoreOption> expandOptions) {
        if ((expandOptions == null) || expandOptions.isEmpty()) {
            return null;
        }
        for (VmfsDatastoreOption option : expandOptions) {
            VmfsDatastoreExpandSpec spec = (VmfsDatastoreExpandSpec) option.getSpec();
            String diskName = spec.getExtent().getDiskName();
            if (StringUtils.equals(diskName, disk.getCanonicalName())) {
                return spec;
            }
        }
        return null;
    }

    /**
     * Creates NAS volume spec required to create a NAS Datastore
     * 
     * @param remoteHost
     * @param remotePath
     * @param datastoreName
     * @param accessMode
     * @param nasType
     * @param userName
     * @param password
     * @return
     */
    public HostNasVolumeSpec getNASDatastoreCreateSpec(String remoteHost, String remotePath,
            String datastoreName, DataStoreAccessMode accessMode, NasType nasType, String userName,
            String password) {

        HostNasVolumeSpec hnvs = new HostNasVolumeSpec();
        hnvs.setRemoteHost(remoteHost);
        hnvs.setRemotePath(remotePath);
        hnvs.setLocalPath(datastoreName);
        hnvs.setType(nasType.toString());
        hnvs.setAccessMode(accessMode.toString()); // "readWrite" or "readOnly"
        hnvs.setUserName(userName);
        hnvs.setPassword(password);
        return hnvs;
    }

    /**
     * Lists the disks associated with the datastore on this host.
     * 
     * @param datastore the datastore.
     * @return the list of SCSI disks that make up the datastore.
     * 
     * @throws IllegalArgumentException if the datastore is not a VMFS datastore.
     */
    public List<HostScsiDisk> listDisks(Datastore datastore) {
        List<HostScsiDisk> disks = Lists.newArrayList();
        for (HostScsiDisk disk : getDisksByPartition(datastore).values()) {
            if (disk != null) {
                disks.add(disk);
            }
        }
        return disks;
    }

    /**
     * Detach all of the disks associated with the datastore on this host
     * 
     * @param datastore the datastore
     * @throws VMWareException
     */
    public void detachDatastore(Datastore datastore) {
        for (HostScsiDisk disk : listDisks(datastore)) {
            try {
                host.getHostStorageSystem().detachScsiLun(disk.getUuid());
            } catch (RemoteException e) {
                throw new VMWareException(e);
            }
        }
    }

    /**
     * Unmount Vmfs datastore from this host storage system
     * 
     * @param datastore the datastore
     */
    public void unmountVmfsDatastore(Datastore datastore) {
        try {
            String vmfsUuid = getVmfsVolumeUuid(datastore);
            host.getHostStorageSystem().unmountVmfsVolume(vmfsUuid);
        } catch (RemoteException e) {
            throw new VMWareException(e);
        }
    }

    /**
     * Get the Vmfs volume uuid from the datastore on this host
     * 
     * @param datastore the datastore
     * @return
     */
    private String getVmfsVolumeUuid(Datastore datastore) {
        String uuid = null;
        for (HostFileSystemMountInfo mount : new HostStorageAPI(host)
                .getStorageSystem().getFileSystemVolumeInfo().getMountInfo()) {

            if (mount.getVolume() instanceof HostVmfsVolume
                    && datastore.getName().equals(mount.getVolume().getName())) {
                HostVmfsVolume volume = (HostVmfsVolume) mount.getVolume();
                return volume.getUuid();
            }

        }
        return uuid;
    }

    /**
     * Gets the disks associated with the datastore mapped by partition.
     * 
     * @param datastore the datastore.
     * @return the disks mapped by partition.
     */
    public Map<HostScsiDiskPartition, HostScsiDisk> getDisksByPartition(Datastore datastore) {
        if (!(datastore.getInfo() instanceof VmfsDatastoreInfo)) {
            throw new IllegalArgumentException(datastore.getName() + " is not a VMFS datastore");
        }
        Map<HostScsiDiskPartition, HostScsiDisk> disks = Maps.newLinkedHashMap();

        Map<String, HostScsiDisk> disksByName = getScsiDisksByCanonicalName();
        HostVmfsVolume volume = ((VmfsDatastoreInfo) datastore.getInfo()).getVmfs();
        for (HostScsiDiskPartition partition : volume.getExtent()) {
            HostScsiDisk disk = disksByName.get(partition.getDiskName());
            disks.put(partition, disk);
        }
        return disks;
    }

    /**
     * Converts a WWN to a normalized form for ease of comparison.
     * 
     * @param wwn a world wide name.
     * @return the normalized WWN.
     */
    public static String normalizeWwn(String wwn) {
        wwn = StringUtils.lowerCase(wwn);
        wwn = StringUtils.replace(wwn, ":", "");
        wwn = StringUtils.leftPad(wwn, 16, '0');
        return wwn;
    }

    /**
     * Converts a WWN to a normalized form for ease of comparison.
     * 
     * @param wwn a world wide name.
     * @return the normalized WWN.
     */
    public static String normalizeWwn(long wwn) {
        return normalizeWwn(Long.toHexString(wwn));
    }

    /**
     * Gets the source name of the host bus adapter. This is either the WWN or IQN for FibreChannel
     * or iSCSI, respectively.
     * 
     * @param hba the host bus adapter.
     * @return the source name (WWN or IQN).
     */
    public static String getSourceName(HostHostBusAdapter hba) {
        if (hba instanceof HostFibreChannelHba) {
            long wwpn = ((HostFibreChannelHba) hba).getPortWorldWideName();
            return normalizeWwn(wwpn);
        }
        if (hba instanceof HostInternetScsiHba) {
            return ((HostInternetScsiHba) hba).getIScsiName();
        }
        return null;
    }

    /**
     * Gets the target name of the SCSI topology target. This is either the WWN or IQN for
     * FibreChannel or iSCSI, respectively.
     * 
     * @param target the SCSI topology target.
     * @return the target name (WWN or IQN).
     */
    public static String getTargetName(HostScsiTopologyTarget target) {
        HostTargetTransport transport = target.getTransport();
        if (transport instanceof HostFibreChannelTargetTransport) {
            long wwpn = ((HostFibreChannelTargetTransport) transport).getPortWorldWideName();
            return normalizeWwn(wwpn);
        }
        if (transport instanceof HostInternetScsiTargetTransport) {
            return ((HostInternetScsiTargetTransport) transport).getIScsiName();
        }
        return null;
    }

    /**
     * Finds a LUN by HLU within SCSI topology target.
     * 
     * @param target the SCSI topology target.
     * @param hlu the host LUN id.
     * @return the LUN.
     */
    public static HostScsiTopologyLun findLun(HostScsiTopologyTarget target, int hlu) {
        if ((target != null) && (target.getLun() != null)) {
            for (HostScsiTopologyLun lun : target.getLun()) {
                if (lun.getLun() == hlu) {
                    return lun;
                }
            }
        }
        return null;
    }

    /**
     * Finds the LUN by disk within the SCSI topology target.
     * 
     * @param target the SCSI topology target.
     * @param disk the disk.
     * @return the LUN.
     */
    public static HostScsiTopologyLun findLun(HostScsiTopologyTarget target, HostScsiDisk disk) {
        if ((target != null) && (target.getLun() != null)) {
            for (HostScsiTopologyLun lun : target.getLun()) {
                if (StringUtils.equals(disk.getKey(), lun.getScsiLun())) {
                    return lun;
                }
            }
        }
        return null;
    }

    /**
     * Finds a host bus adapter in the collection by iSCSI IQN.
     * 
     * @param hbas the collection of host bus adapters.
     * @param iqn the iSCSI IQN.
     * @return the iSCSI adapter, or null if no matching adapters.
     */
    public static HostInternetScsiHba findHostBusAdapterByIqn(
            Collection<? extends HostHostBusAdapter> hbas, String iqn) {
        for (HostHostBusAdapter hba : hbas) {
            if (hba instanceof HostInternetScsiHba) {
                HostInternetScsiHba iscsiHba = (HostInternetScsiHba) hba;

                if (StringUtils.equals(iqn, iscsiHba.getIScsiName())) {
                    return iscsiHba;
                }
            }
        }
        return null;
    }

    /**
     * Finds a host bus adapter in the collection by FibreChannel port WWN.
     * 
     * @param hbas the collection of host bus adapters.
     * @param portWwn the port WWN.
     * @return the FibreChannel adapter, or null if no matching adapters.
     */
    public static HostFibreChannelHba findHostBusAdapterByWwn(
            Collection<? extends HostHostBusAdapter> hbas, String portWwn) {
        portWwn = normalizeWwn(portWwn);
        for (HostHostBusAdapter hba : hbas) {
            if (hba instanceof HostFibreChannelHba) {
                HostFibreChannelHba fcHba = (HostFibreChannelHba) hba;

                String wwn = normalizeWwn(fcHba.getPortWorldWideName());
                if (StringUtils.equals(portWwn, wwn)) {
                    return fcHba;
                }
            }
        }
        return null;
    }

    /**
     * Creates an array of iSCSI send targets for the given addresses. The addresses may contains an
     * address only or an address and port (address:port).
     * 
     * @param addresses the addresses.
     * @return the iSCSI send targets.
     */
    public static HostInternetScsiHbaSendTarget[] createInternetScsiSendTargets(String... addresses) {
        HostInternetScsiHbaSendTarget[] targets = new HostInternetScsiHbaSendTarget[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            targets[i] = createInternetScsiSendTarget(addresses[i]);
        }
        return targets;
    }

    /**
     * Creates an iSCSI sent target for the given address.
     * 
     * @param address the address, may contain the port.
     * @return the iSCSI send target.
     */
    public static HostInternetScsiHbaSendTarget createInternetScsiSendTarget(String address) {
        HostInternetScsiHbaSendTarget target = new HostInternetScsiHbaSendTarget();
        if (StringUtils.contains(address, ':')) {
            target.setAddress(StringUtils.substringBefore(address, ":"));
            target.setPort(NumberUtils.toInt(StringUtils.substringAfter(address, ":")));
        }
        else {
            target.setAddress(address);
        }
        return target;
    }

    /**
     * Lists the partitions for the datastore. This will only return values for a VMFS datastore.
     * 
     * @param datastore the datastore.
     * @return the list of disk partitions for the VMFS datastore.
     */
    protected static List<HostScsiDiskPartition> listPartitions(Datastore datastore) {
        List<HostScsiDiskPartition> partitions = Lists.newArrayList();
        if (datastore.getInfo() instanceof VmfsDatastoreInfo) {
            HostVmfsVolume volume = ((VmfsDatastoreInfo) datastore.getInfo()).getVmfs();
            addItems(partitions, volume.getExtent());
        }
        return partitions;
    }

    /**
     * Creates a list from an array of elements (null-safe).
     * 
     * @param array the array, or null.
     * @return a list.
     */
    protected static <T> List<T> createList(T[] elements) {
        if (elements != null) {
            return Lists.newArrayList(elements);
        }
        else {
            return Lists.newArrayList();
        }
    }

    /**
     * Adds the items to a collection (null-safe).
     * 
     * @param collection the collection to add to.
     * @param elements the elements.
     */
    protected static <T> void addItems(Collection<T> collection, T[] elements) {
        if (elements != null) {
            for (T element : elements) {
                collection.add(element);
            }
        }
    }

    /**
     * Adds the items to the collection that are of the correct specific type only (null-safe).
     * 
     * @param collection the collection to add to.
     * @param elements the elements
     * @param c the specific class that elements must be in order to be added.
     */
    protected static <T, V extends T> void addItems(Collection<V> collection, T[] elements,
            Class<V> c) {
        if (elements != null) {
            for (T element : elements) {
                if (c.isInstance(element)) {
                    collection.add((V) element);
                }
            }
        }
    }
}
