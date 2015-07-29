/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware;

import static com.emc.sa.service.ServiceParams.CONSISTENCY_GROUP;
import static com.emc.sa.service.ServiceParams.DATASTORE_NAME;
import static com.emc.sa.service.ServiceParams.HLU;
import static com.emc.sa.service.ServiceParams.HOST;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.NUMBER_OF_VOLUMES;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VIRTUAL_ARRAY;
import static com.emc.sa.service.ServiceParams.VIRTUAL_POOL;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.vmware.vim25.*;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.bind.Param;
import com.google.common.collect.Lists;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedEntity;

public class VMwareUtils {
    public static final String CANONICAL_NAME_PREFIX = "naa.";
    public static final String ALTERNATE_CANONICAL_NAME_PREFIX = "eui.";
    public static final String CANONICAL_NAME_PREFIX_REGEX = "naa\\.";
    public static final String ALTERNATE_CANONICAL_NAME_PREFIX_REGEX = "eui\\.";
    private static final String PERMISSION_DENIED_MESSAGE_KEY = "vob.vmfs.nfs.mount.error.perm.denied";

    public static boolean isPlatformConfigFault(Exception e) {
        return e.getCause() instanceof PlatformConfigFault;
    }

    public static boolean isAlreadyExists(Exception e) {
        return e.getCause() instanceof AlreadyExists;
    }

    public static boolean isPermissionDenied(VMWareException e) {
        if (isPlatformConfigFault(e)) {
            LocalizableMessage[] messages = ((PlatformConfigFault) e.getCause()).getFaultMessage();
            if (messages != null && messages.length > 0) {
                String faultMessageKey = messages[0].getKey();
                return PERMISSION_DENIED_MESSAGE_KEY.equals(faultMessageKey);
            }
        }
        return false;
    }

    public static String getFaultMessage(MethodFault fault) {
        return VMWareException.getDetailMessage(fault);
    }

    /**
     * Gets the hosts attached to the specified datastore.
     * 
     * @param vcenter
     *            the vcenter API.
     * @param datastore
     *            the datastore
     * @return the list of hosts attached to the datastore.
     */
    public static List<HostSystem> getHostsForDatastore(VCenterAPI vcenter, Datastore datastore) {
        List<HostSystem> hosts = Lists.newArrayList();
        DatastoreHostMount[] mounts = datastore.getHost();
        if (mounts != null && mounts.length > 0) {
            for (DatastoreHostMount mount : mounts) {
                HostSystem host = vcenter.lookupManagedEntity(mount.getKey());
                if (host != null) {
                    hosts.add(host);
                }
            }
        }
        return hosts;
    }

    /**
     * Gets all the hosts in the same cluster as this host. If the host is not in a cluster, it will be the only host in
     * the list.
     * 
     * @param host
     *            the host.
     * @return the list of hosts in the same cluster.
     */
    public static List<HostSystem> getHostsInCluster(HostSystem host) {
        List<HostSystem> hosts = Lists.newArrayList();
        if (host.getParent() instanceof ClusterComputeResource) {
            HostSystem[] clusterHosts = ((ClusterComputeResource) host.getParent()).getHosts();
            if (clusterHosts != null) {
                for (HostSystem clusterHost : clusterHosts) {
                    hosts.add(clusterHost);
                }
            }
        }
        else {
            hosts.add(host);
        }
        return hosts;
    }

    /**
     * Gets the HostVmfsVolume information about a datastore, if that datastore is a VMFS datastore.
     * 
     * @param datastore
     *            the datastore.
     * @return the HostVmfsVolume, or null if the datastore is null or not a VMFS datastore.
     */
    public static HostVmfsVolume getHostVmfsVolume(Datastore datastore) {
        if (datastore != null && datastore.getInfo() instanceof VmfsDatastoreInfo) {
            return ((VmfsDatastoreInfo) datastore.getInfo()).getVmfs();
        }
        return null;
    }

    /**
     * Gets the HostNasVolume information about a datastore, if that datastore is a NAS datastore.
     * 
     * @param datastore
     *            the datastore.
     * @return the HostNasVolume, or null if the datastore is null or not a NAS datastore.
     */
    public static HostNasVolume getHostNasVolume(Datastore datastore) {
        if (datastore != null && datastore.getInfo() instanceof NasDatastoreInfo) {
            return ((NasDatastoreInfo) datastore.getInfo()).getNas();
        }
        return null;
    }

    public static String getPath(ManagedEntity entity) {
        LinkedList<String> path = Lists.newLinkedList();
        while (entity != null) {
            path.addFirst(entity.getName());
            entity = entity.getParent();
        }
        return StringUtils.join(path, "/");
    }

    /**
     * Gets the connection state of the given host system.
     * 
     * @param host
     *            the host system.
     * @return the host system connection state, or null if it cannot be determined.
     */
    public static HostSystemConnectionState getConnectionState(HostSystem host) {
        HostRuntimeInfo runtime = host.getRuntime();
        HostSystemConnectionState connectionState = (runtime != null) ? runtime.getConnectionState() : null;
        return connectionState;
    }

    public static String getDiskWwn(HostScsiDisk disk) {
        if (StringUtils.startsWith(disk.getCanonicalName(), CANONICAL_NAME_PREFIX)) {
            return disk.getCanonicalName().replaceFirst(CANONICAL_NAME_PREFIX_REGEX, "");
        } else if (StringUtils.startsWith(disk.getCanonicalName(), ALTERNATE_CANONICAL_NAME_PREFIX)) {
            return disk.getCanonicalName().replaceFirst(ALTERNATE_CANONICAL_NAME_PREFIX_REGEX, "");
        } else {
            return null;
        }
    }

    /**
     * Checks if the multipath policy is a valid policy
     * 
     * @param multipathPolicy policy name to check
     * @return true if policy is valid, otherwise false
     */
    public static boolean isValidMultipathPolicy(String multipathPolicy) {
        for (String validPolicy : HostStorageAPI.MULTIPATH_POLICY_TYPES) {
            if (StringUtils.equalsIgnoreCase(validPolicy, multipathPolicy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Class that holds all params for volume creation. These params will be added
     * to the createBlockVolumeHelper for each pair of Datastore / volumes. This class
     * is needed since all params listed are single instance on the form while the
     * Datastore / Volume can have multiple.
     * 
     * @author cormij4
     * 
     */
    public static class DatastoreToVolumeParams {
        @Param(VIRTUAL_POOL)
        protected URI virtualPool;
        @Param(VIRTUAL_ARRAY)
        protected URI virtualArray;
        @Param(PROJECT)
        protected URI project;
        @Param(HOST)
        protected URI hostId;
        @Param(value = NUMBER_OF_VOLUMES, required = false)
        protected Integer count;
        @Param(value = CONSISTENCY_GROUP, required = false)
        protected URI consistencyGroup;
        @Param(value = HLU, required = false)
        protected Integer hlu;

        public String toString() {
            return "Virtual Pool=" + virtualPool + ", Virtual Array=" + virtualArray + ", Project=" + project
                    + ", Host Id=" + hostId + ", Volume Count=" + count + ", Consistency Group=" + consistencyGroup
                    + ", HLU=" + hlu;
        }

        public Map<String, Object> getParams() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(VIRTUAL_POOL, virtualPool);
            map.put(VIRTUAL_ARRAY, virtualArray);
            map.put(PROJECT, project);
            map.put(HOST, hostId);
            map.put(NUMBER_OF_VOLUMES, count);
            map.put(CONSISTENCY_GROUP, consistencyGroup);
            map.put(HLU, hlu);
            return map;
        }
    }

    /**
     * Class to hold params of all pair of Datastore / Volume.
     * 
     * @author cormij4
     * 
     */
    public static class DatastoreToVolumeTable {
        @Param(DATASTORE_NAME)
        protected String datastoreName;
        @Param(NAME)
        protected String nameParam;
        @Param(SIZE_IN_GB)
        protected Double sizeInGb;

        public String toString() {
            return "Datastore Name=" + datastoreName + ", Volume=" + nameParam + ", size=" + sizeInGb;
        }

        public Map<String, Object> getParams() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(DATASTORE_NAME, datastoreName);
            map.put(NAME, nameParam);
            map.put(SIZE_IN_GB, sizeInGb);
            return map;
        }
    }

    /**
     * Helper method for creating a list of all the params for the createBlockVolumesHelper.
     * 
     * @param table of Datastore to Volumes
     * @param params for volume creation
     * @return Map of all params
     */
    public static Map<String, Object> createDatastoreVolumeParam(DatastoreToVolumeTable table, DatastoreToVolumeParams params) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.putAll(table.getParams());
        map.putAll(params.getParams());
        return map;
    }

    /**
     * Loop through all the Datastore / Volumes pair and return all the Datastore names.
     * 
     * @param datastoreToVolume list
     * @return list of datastore names
     */
    public static List<String> getDatastoreNamesFromDatastoreToVolume(DatastoreToVolumeTable[] datastoreToVolume) {
        List<String> dataStoreNames = new ArrayList<String>();
        for (DatastoreToVolumeTable value : datastoreToVolume) {
            dataStoreNames.add(value.datastoreName);
        }
        return dataStoreNames;
    }

    /**
     * Loop through all the Datastore / Volumes pair and return all the Volume names.
     * 
     * @param datastoreToVolume list
     * @return list of volume names
     */
    public static List<String> getVolumeNamesFromDatastoreToVolume(DatastoreToVolumeTable[] datastoreToVolume) {
        List<String> volumeNames = new ArrayList<String>();
        for (DatastoreToVolumeTable value : datastoreToVolume) {
            volumeNames.add(value.nameParam);
        }
        return volumeNames;
    }

    /**
     * Determine whether all entries are unique within a given list
     * 
     * @param names list
     * @return true or false if the list contain unique names
     */
    public static boolean isUniqueNames(List<String> names) {
        List<String> unique = Lists.newArrayList();

        for (String n : names) {
            if (unique.contains(n)) {
                return false;
            }
            unique.add(n);
        }

        return true;
    }

}
