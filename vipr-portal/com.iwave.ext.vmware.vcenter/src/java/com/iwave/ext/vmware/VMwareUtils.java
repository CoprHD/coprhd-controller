/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.vmware;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.vmware.vim25.AlreadyExists;
import com.vmware.vim25.DatastoreHostMount;
import com.vmware.vim25.HostNasVolume;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.LocalizableMessage;
import com.vmware.vim25.MethodFault;
import com.vmware.vim25.NasDatastoreInfo;
import com.vmware.vim25.PlatformConfigFault;
import com.vmware.vim25.ScsiLunState;
import com.vmware.vim25.VmfsDatastoreInfo;
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

    /**
     * Returns true if the datastore is mounted on the host
     * 
     * @param datastore the datastore
     * @param host the host
     * @return true if the datastore is mounted on the host, otherwise returns false
     */
    public static boolean isDatastoreMountedOnHost(Datastore datastore, HostSystem host) {
        if (host != null && datastore != null) {
            DatastoreHostMount[] hostMounts = datastore.getHost();
            if (hostMounts != null) {
                for (DatastoreHostMount hostMount : hostMounts) {
                    if (hostMount.getKey().equals(host.getMOR()) && hostMount.mountInfo != null && hostMount.mountInfo.mounted) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the disk operational state is 'off'
     * 
     * @param disk the scsi disk
     * @return true if the disk operational state is 'off', otherwise returns false
     */
    public static boolean isDiskOff(HostScsiDisk disk) {
        String[] state = disk.getOperationalState();
        if (state == null || state.length == 0) {
            return false;
        }
        String primaryState = state[0];
        return StringUtils.equals(primaryState, ScsiLunState.off.name());
    }

}
