/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import netapp.manage.NaElement;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.netapp.model.CifsAcl;
import com.iwave.ext.netapp.model.DiskDetailInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.NetAppDevice;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netapp.model.Quota;
import com.iwave.ext.netapp.utils.ExportRule;

@SuppressWarnings({ "findbugs:WMI_WRONG_MAP_ITERATOR" })
/**
 * @author sdorcas
 * All calls from iWO NetApp services must delegate to this class
 */
public class NetAppFacade {

    private Logger log = Logger.getLogger(getClass());
    Server server = null;
    String _vFilerName = null;

    public NetAppFacade(String host, int port, String username, String password, boolean useHTTPS) {
        this(host, port, username, password, useHTTPS, null);
    }

    public NetAppFacade(String host, int port, String username, String password, boolean useHTTPS, String vFilerName)
    {
        if (log.isDebugEnabled()) {
            String vFiler = (vFilerName != null ? vFilerName : "");
            log.debug("Connecting to NetApp server: " + host + ":" + port + ":" + vFiler);
        }
        _vFilerName = vFilerName;
        server = new Server(host, port, username, password, useHTTPS, vFilerName, false);
    }

    public static NetAppFacade create(Map<String, String> connectionParams) {
        return new NetAppFacade(connectionParams.get(NetAppDevice.IP_KEY),
                Integer.parseInt(connectionParams.get(NetAppDevice.PORT_KEY)),
                connectionParams.get(NetAppDevice.USR_KEY),
                connectionParams.get(NetAppDevice.PWD_KEY),
                Boolean.parseBoolean(connectionParams.get(NetAppDevice.SECURE_KEY)));
    }

    /***** Aggregate Ops ********/

    /**
     * Returns a list of aggregates.
     * 
     * @param name - Optional. If provided only the aggregate info for the
     *            the name aggregate is returned. If null or empty all aggregates are
     *            returned.
     * @return - list of aggregates.
     */
    public List<AggregateInfo> listAggregates(String name)
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing Aggregates. Params [name]: " + name);
        }

        Aggregate aggr = new Aggregate(server.getNaServer(), name);
        boolean listAll = false;
        if (name == null || name.isEmpty()) {
            listAll = true;
        }
        return aggr.listAllAggregates(listAll);
    }

    /***** Initiator Group operations *********/

    /**
     * Creates a new initiator group
     * 
     * @param name - Name of initiator group
     * @param type - One of "iscsi", "fcp"
     * @param type - OS type of initiator group
     * @throws NetAppException
     */
    public IGroupInfo createIGroup(String name, IGroupType type, LunOSType osType)
    {
        if (log.isDebugEnabled()) {
            log.debug("Creating new IGroup. Params [name,type,os-type]: " + name + "," + type + "," + osType);
        }

        IGroup igroup = new IGroup(server.getNaServer(), name);
        igroup.createIGroup(type, osType);

        IGroupInfo info = new IGroupInfo();
        info.setName(name);
        info.setType(type);
        info.setOsType(osType);
        return info;
    }

    /**
     * Destroys an initiator group. Best practice is to unmap all
     * LUNs prior to deletion.
     * 
     * @param name
     * @param forceDelete
     * @return success =true/false
     */
    public boolean destroyIGroup(String name, boolean forceDelete)
    {
        if (log.isDebugEnabled()) {
            log.debug("Destroying IGroup. Params [name,force]: " + name + "," + forceDelete);
        }
        IGroup igroup = new IGroup(server.getNaServer(), name);

        return igroup.destroyIGroup(forceDelete);
    }

    /**
     * Add initiator to group
     * 
     * @param groupName - Initiator Group name
     * @param initiatorName - Initiator WWN or iSCSI iqn
     * @throws NetAppException
     */
    public void addInitiatorToIGroup(String groupName, String initiator)
    {
        if (log.isDebugEnabled()) {
            log.debug("Adding initiator to IGroup. Params [groupName,initiator]: " + groupName + "," + initiator);
        }
        IGroup igroup = new IGroup(server.getNaServer(), groupName);

        igroup.addInitiatorToIGroup(initiator);
    }

    /**
     * Remove initiator from group
     * 
     * @param groupName - Initiator Group name
     * @param initiatorName - Initiator WWN or iSCSI iqn
     * @param force - force removal from groups mapped to LUNs
     * @throws NetAppException
     */
    public void removeInitiatorFromIGroup(String groupName, String initiator, boolean force)
    {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Removing initiator '%s' from group '%s' force=%s", initiator, groupName, force + ""));
        }

        IGroup igroup = new IGroup(server.getNaServer(), groupName);
        igroup.removeInitiatorFromIGroup(initiator, force);
    }

    /**
     * list initiator groups.
     * 
     * @param groupName - Initiator Group name
     * @return list of IGroupInfo
     */
    public List<IGroupInfo> listInitiatorGroups(String groupName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing initiator groups.");
        }
        IGroup igroup = new IGroup(server.getNaServer(), groupName);
        boolean listAll = false;
        if (groupName == null || groupName.isEmpty()) {
            listAll = true;
        }
        return igroup.listInitiatorGroups(listAll);
    }

    /**
     * find first group containing specified initiator.
     * 
     * @param initiator
     * @return
     */
    public IGroupInfo findIGroup(String initiator) {
        if (log.isDebugEnabled()) {
            log.debug("find igroup containing initiator: " + initiator);
        }

        IGroup igroup = new IGroup(server.getNaServer(), null);
        for (IGroupInfo info : igroup.listInitiatorGroups(true)) {
            if (info.getInitiators().contains(initiator)) {
                return info;
            }
        }

        return null;
    }

    /**
     * find first group containing exactly the specified initiators.
     * 
     * @param initiators
     * @return if not found, returned IGroupInfo.getName() is null and IGroupInfo.getOsType() is set if any of the initiators were found.
     */
    public IGroupInfo findExactGroup(List<String> initiators) {
        if (log.isDebugEnabled()) {
            log.debug("find igroup containing all initiators: " + initiators);
        }

        IGroupInfo notFound = new IGroupInfo();
        notFound.setName(null);

        IGroup igroup = new IGroup(server.getNaServer(), null);

        for (IGroupInfo info : igroup.listInitiatorGroups(true)) {
            ArrayList<String> list = new ArrayList<String>(initiators);

            for (String initiator : info.getInitiators()) {
                if (!list.remove(initiator)) {
                    list.add("fail");
                }
                else {
                    notFound.setOsType(info.getOsType());
                }
            }

            if (list.isEmpty()) {
                return info;
            }
        }

        return notFound;
    }

    /***** LUN ops ************/

    /**
     * Resizes a LUN first taking it offline, performing the resize, and taking in online.
     * 
     * NOTE: This is the same as resizeLun, however it does NOT change the online state of a Lun
     * (and thus could fail if the lun is still online)
     * 
     * @param lunPath - full path to the LUN
     * @param sizeInBytes - new size of the LUN
     * @param forceReduce - Forcibly reduce the LUN size. Must be true to reduce the LUN size.
     * @return size of the altered LUN. Returns -1 if the operation was unsuccessful.
     */
    public long resizeLunOnly(String lunPath, long sizeInBytes, boolean forceReduce) {
        long actualSize = -1;

        if (log.isDebugEnabled()) {
            log.debug("Re-sizing LUN. Params [lunPath,sizeInBytes]: " + lunPath + ", " + sizeInBytes);
        }

        Lun lun = new Lun(server.getNaServer(), lunPath);

        // Resize it
        actualSize = lun.resizeLun(sizeInBytes, forceReduce);

        if (log.isDebugEnabled()) {
            log.debug("LUN resized. New size in bytes = " + actualSize);
        }

        return actualSize;
    }

    /**
     * Resizes a LUN first taking it offline, performing the resize, and taking in online.
     * 
     * @param lunPath - full path to the LUN
     * @param sizeInBytes - new size of the LUN
     * @param forceReduce - Forcibly reduce the LUN size. Must be true to reduce the LUN size.
     * @return size of the altered LUN. Returns -1 if the operation was unsuccessful.
     */
    public long resizeLun(String lunPath, long sizeInBytes, boolean forceReduce)
    {
        long actualSize = -1;

        if (log.isDebugEnabled()) {
            log.debug("Re-sizing LUN. Params [lunPath,sizeInBytes]: " + lunPath + ", " + sizeInBytes);
        }

        Lun lun = new Lun(server.getNaServer(), lunPath);

        // Take LUN offline
        lun.setLunOnline(false, false);
        // Resize it
        actualSize = lun.resizeLun(sizeInBytes, forceReduce);
        // Take LUN online
        lun.setLunOnline(true, false);

        if (log.isDebugEnabled()) {
            log.debug("LUN resized. New size in bytes = " + actualSize);
        }

        return actualSize;
    }

    /**
     * Sets a LUN online
     * 
     * @param lunPath - full path to the LUN
     * @param forceOnline - force the LUN online, bypassing conflict checks
     * @return - true/false if operation was successful
     */
    public boolean setLunOnline(String lunPath, boolean forceOnline)
    {
        if (log.isDebugEnabled()) {
            log.debug("Setting LUN online. Params [lunPath,forceOnline]: " + lunPath + ", " + forceOnline);
        }

        Lun lun = new Lun(server.getNaServer(), lunPath);
        boolean result = lun.setLunOnline(true, forceOnline);

        return result;
    }

    /**
     * Sets a LUN offline
     * 
     * @param lunPath - full path to the LUN
     * @return - true/false if operation was successful
     */
    public boolean setLunOffline(String lunPath)
    {
        if (log.isDebugEnabled()) {
            log.debug("Setting LUN offline. Params [lunPath]: " + lunPath);
        }

        Lun lun = new Lun(server.getNaServer(), lunPath);
        boolean result = lun.setLunOnline(false, false);

        return result;
    }

    public Lun getLun(String lunPath) {
        return new Lun(server.getNaServer(), lunPath);
    }

    /**
     * Commissions a new LUN. Creates the LUN, sets a description, and maps the LUN
     * to a list of initiator groups
     * 
     * @param lunPath - full Path of the LUN
     * @param osType - the OS Type for the new LUN
     * @param sizeInBytes - Size in bytes of the new LUN
     * @param reserveSpace - true/false to reserve the space
     * @param description - Optional. Description of the LUN.
     * @param groupMap - Map of initiator groups to the id LUN will be mapped to.
     *            -1 means auto-assign. on return groupMap is set to the actual Lun ID assigned.
     * @return - The actual size of the newly created LUN. Value of -1 means it failed.
     */
    public long createLun(String lunPath, LunOSType osType, long sizeInBytes, boolean reserveSpace,
            String description, Map<String, Integer> groupMap)
    {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("Creating new LUN. Params [lunPath, " +
                    "osType, sizeInBytes, reserveSpace,description, groupMap]: ");
            sb.append(lunPath).append(",");
            sb.append(osType).append(",");
            sb.append(sizeInBytes).append(",");
            sb.append(reserveSpace).append(",");
            sb.append(description).append(",");
            sb.append(groupMap);
            log.debug(sb.toString());
        }

        // Create the LUN first
        Lun lun = new Lun(server.getNaServer(), lunPath);
        long createdSize = lun.createLunBySize(osType, sizeInBytes, reserveSpace);
        if (createdSize < 0) {
            log.error("Commisioning of LUN failed.");
            return createdSize; // Stop here as the remaing operations will fail.
        }
        // Set the description of the lun if provided
        if (description != null) {
            lun.setLunDescription(description);
        }
        // Now map the LUN to each initiator group in the list
        for (String group : groupMap.keySet()) {
            int lunId = groupMap.get(group);
            int result = lun.mapLun(false, group, lunId);
            if (result != -1) {
                groupMap.put(group, result);
            }
            else {
                log.warn("Mapping LUN to initiator group failed. Group: " + group + " id=" + lunId);
                log.warn("LUN will be deleted.");
                // Rollback the newly created LUN if it could not be mapped.
                lun.destroyLun(false);
            }
        }

        return createdSize;
    }

    /**
     * Maps the specified Lun to the InitiatorGroup
     * 
     * @param lunPath path of Lun to be mapped
     * @param initiatorGroup Name of initiator Group to be mapped to
     * @param lunId Lun ID (HLU) of the lun in the initiator group, a value of -1 means the Array will auto assign the lun id
     * @return The actual lun id that was assigned to this lun
     */
    public int mapLunToInitiatorGroup(String lunPath, String initiatorGroup, int lunId) {
        Lun lun = new Lun(server.getNaServer(), lunPath);
        return lun.mapLun(false, initiatorGroup, lunId);
    }

    /**
     * UnMaps the specified Lun from the InitiatorGroup
     * 
     * @param lunPath path of Lun to be unmapped
     * @param initiatorGroup Name of initiator Group lun currently mapped to
     */
    public void unmapLunFromInitiatorGroup(String lunPath, String initiatorGroup) {
        Lun lun = new Lun(server.getNaServer(), lunPath);
        lun.unmapLun(initiatorGroup);
    }

    /**
     * destroys the LUN.
     * 
     * @param lunPath - full path to the LUN
     * @param force - force delete the LUN, even it it is mapped.
     */
    public void deleteLun(String lunPath, boolean force)
    {
        if (log.isDebugEnabled()) {
            log.debug("Deleting LUN. Params [lunPath, force]: " + lunPath + ", " + force);
        }

        Lun lun = new Lun(server.getNaServer(), lunPath);

        lun.destroyLun(force);
    }

    /**
     * Retrieves the lun ID used to identify the mapping of LUN to an initiator group.
     * A returned value of -1 means an error occured.
     * 
     * @param lunPath
     * @param initGroupName
     * @return
     */
    public int getLunIdFromGroupMapping(String lunPath, String initGroupName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Getting LUN ID from group. Params [lunPath, initGroupName]: " + lunPath + "," + initGroupName);
        }
        Lun lun = new Lun(server.getNaServer(), lunPath);
        return lun.getLunIdForGroup(initGroupName);
    }

    /**
     * Retrieves the lun map used to identify the mapping of LUN to an initiator group.
     * 
     * @param lunPath
     * @return
     */
    public Map<String, Integer> getLunMap(String lunPath)
    {
        if (log.isDebugEnabled()) {
            log.debug("Getting LUN Map. Params [lunPath]: " + lunPath);
        }
        Lun lun = new Lun(server.getNaServer(), lunPath);
        return lun.getLunMap();
    }

    /**
     * List all Luns on the device. If a lun path is specified, only the specified LUN is
     * returned
     * 
     * @param lunPath - Optional. If specified only the specified LUN is returned
     * @return
     */
    public List<LunInfo> listLuns(String lunPath)
    {
        if (log.isDebugEnabled()) {
            log.debug("List LUNs.");
        }
        boolean listAll = false;
        if (lunPath == null || lunPath.isEmpty()) {
            listAll = true;
        }
        Lun lun = new Lun(server.getNaServer(), lunPath);
        return lun.listLUNs(listAll);
    }

    /**** Volume operations *****/

    /**
     * Returns the volumes size as string.
     * 
     * @param volumeName - Name of the volume.
     * @return - Size with unit. For example, "100g, 1t, 2048m".
     */
    public String getVolumeSize(String volumeName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving volume size. Volume: " + volumeName);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        String size = vol.getVolumeSize();
        return size;
    }

    /**
     * Takes a volume offline. Note this call does *not* wait for the specified
     * number of minutes. The delay occurs on the device.
     * 
     * @param volumeName - name of volume to offline
     * @param delayInMinutes - number of minutes to wait on the device before
     *            the volume is offline.
     */
    public void setVolumeOffline(String volumeName, int delayInMinutes)
    {
        if (log.isDebugEnabled()) {
            log.debug("Taking volume offline with params[name,delayInMinutes]: " +
                    volumeName + "," + delayInMinutes);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        vol.setVolumeOffline(delayInMinutes);
    }

    /**
     * Creates a new flexible volume, enables and configures SiS.
     * Only parameters for flexible volumes are provided.
     * Note the volume may not be operational immediately after this method returns. Use
     * getVolumeInfo() to query the status of the new volume.
     * 
     * @param volName - Required. Volume name.
     * @param containingAggrName - Optional. Name of the aggregate in which to create the volume. Must
     *            be used in conjunction with the size parameter.
     * @param isSnapLock - true/false to create a SnapLock volume
     * @param remoteLocation - Optional. Remote host and volume name for the origin of FlexCache.
     * @param size - Optional. Size (with unit) of the new volume. Ex: 10g, 2000m, 1t. Must be used
     *            in conjunction with containingAggrName.
     * @param snaplockType - Optional. Type of snaplock volume to be created.
     *            Valid values - "compliance" and "enterprise".
     * @param spaceReserve - Optional. Type of volume guarantee new volume will use. Valid
     *            values are "none", "file", "volume".
     * @param enableSis - enables SIS on the new volume.
     * @param sisSchedule - The SIS schedule in ONTAP format. Example "sat-sun[@0-23]"
     * @return
     */
    public boolean createFlexibleVolume(String volName, String containingAggrName, boolean isSnapLock,
            String remoteLocation, String size, String snaplockType, String spaceReserve,
            boolean enableSis, String sisSchedule)
    {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("Creating new flexible volume offline with params" +
                    "[volName,aggrName,isSnapLock,remoteLocation,size,snaplockType,spaceReserve," +
                    "enableSis, sisSchedule]:");
            sb.append(volName).append(", ");
            sb.append(containingAggrName).append(", ");
            sb.append(isSnapLock).append(", ");
            sb.append(remoteLocation).append(", ");
            sb.append(size).append(", ");
            sb.append(snaplockType).append(", ");
            sb.append(spaceReserve).append(", ");
            sb.append(enableSis).append(", ");
            sb.append(sisSchedule);
            log.debug(sb.toString());
        }
        // First create the volume
        Volume vol = new Volume(server.getNaServer(), volName);
        boolean result = vol.createFlexibleVolume(containingAggrName, isSnapLock, remoteLocation, size, snaplockType, spaceReserve);
        if (!result) {
            // If the create failed stop here. Subsequent operations will fail
            return result;
        }
        // Otherwise proceed with enabling and configuring SIS
        if (enableSis) {
            vol.enableSis(sisSchedule);
        }

        return result;
    }

    /**
     * Destroys a volume.
     * 
     * @param volumeName
     * @param force
     * @return
     */
    public boolean destroyVolume(String volumeName, boolean force)
    {
        if (log.isDebugEnabled()) {
            log.debug("Deleting volume with params[name,force]: " +
                    volumeName + "," + force);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        boolean result = vol.destroyVolume(force);

        return result;
    }

    /**
     * Sets a new size of a volume.
     * 
     * @param newSize - Size (with unit) of the new volume. Ex: 10g, 2000m, 1t.
     * 
     * @return - The new size of the volume.
     */
    public String setVolumeSize(String volumeName, String newSize) {

        Volume vol = new Volume(server.getNaServer(), volumeName);
        return vol.setVolumeSize(newSize);
    }

    /**
     * Takes a snapshot of the volume. This operation waits for the snapshot to complete on the device.
     * 
     * @param volumeName - Name of the volume
     * @param snapshotName - Name of the snapshot
     * @param isValidLunCloneSnapshot - Optional. True indicates requested by snapvault.
     *            All snapshots for lun clones will be locked. Default is false.
     * @return - true/false if successful
     */
    public boolean createVolumeSnapshot(String volumeName, String snapshotName, boolean isValidLunCloneSnapshot)
    {
        if (log.isDebugEnabled()) {
            log.debug("Creating snapshot on volume with params[volName,snapshotName,isValidLunCloneSnapshot]: " +
                    volumeName + "," + snapshotName + "," + isValidLunCloneSnapshot);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        // Take a snapshot (synchronous).
        boolean result = vol.createSnapshot(snapshotName, isValidLunCloneSnapshot, false);

        return result;
    }

    /**
     * Deletes a snapshot from a volume
     * 
     * @param volumeName - Name of the volume containing the snapshot
     * @param snapshotName - Name of the snapshot to be deleted
     * @return - true/false if the operation was successful.
     */
    public boolean deleteVolumeSnapshot(String volumeName, String snapshotName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Deleting snapshot on volume with params[volName,snapshotName]: " +
                    volumeName + "," + snapshotName);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        boolean result = vol.deleteSnapshot(snapshotName);
        return result;
    }

    /**
     * Restores a volume to a snapshot.
     * 
     * @param volumeName - Volume to be restored
     * @param snapshotName - Snapshot to restore from
     * @return - true/false if operation was successful
     */
    public boolean restoreVolumeFromSnapshot(String volumeName, String snapshotName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Restoring volume from snapshot with params[volName,snapshotName]: " +
                    volumeName + "," + snapshotName);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        boolean result = vol.restoreVolumeFromSnapshot(snapshotName);
        return result;
    }

    /**
     * Sets options on a specified volume
     * 
     * @param volumeName - Name of volume to set options.
     * @param options - Map(name,value) of options to set.
     */
    public void setVolumeOptions(String volumeName, Map<VolumeOptionType, String> options)
    {
        if (options == null) {
            return;
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        StringBuilder optionNV = null;
        for (Map.Entry<VolumeOptionType, String> e : options.entrySet()) {
            if (log.isDebugEnabled()) {
                optionNV = new StringBuilder();
                optionNV.append(e.getKey()).append("=").append(e.getValue());
                log.debug("Setting option on volume " + volumeName + ". " + optionNV.toString());
            }
            // Set the option
            vol.setVolumeOption(e.getKey(), e.getValue());
        }
    }

    /**
     * Returns the value of a specified volume info attribute
     * 
     * @param volumeName - Name of the volume
     * @param attrName - Name of the attribute whose value to return
     * @return - value of the attribute.
     */
    public String getVolumeInfoAttribute(String volumeName, String attrName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving volume info attribute[volName,attrName]: " +
                    volumeName + "," + attrName);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        Map<String, String> attrs = vol.getVolumeInfo(true);
        String attrValue = "";
        if (attrs.containsKey(attrName)) {
            attrValue = attrs.get(attrName);
        }
        return attrValue;
    }

    public Map<String, String> getVolumeInfoAttributes(String volumeName, boolean verbose)
    {
        Volume vol = new Volume(server.getNaServer(), volumeName);
        return vol.getVolumeInfo(verbose);
    }

    /**
     * Lists all volumes on a Filer.
     * 
     * @return - list of volumes
     */
    public List<String> listVolumes()
    {
        if (log.isDebugEnabled()) {
            log.debug("List all volumes");
        }
        Volume vol = new Volume(server.getNaServer(), ""); // Vol name is not needed for this
        List<String> volumes = vol.listVolumes();
        return volumes;
    }

    /**
     * Lists all volumes on a Filer.
     * 
     * @return - list of volumes
     */
    public List<Map<String, String>> listVolumeInfo(String volume, Collection<String> attrs)
    {
        if (log.isDebugEnabled()) {
            log.debug("List all volumes with attributes");
        }

        Volume vol = new Volume(server.getNaServer(), volume);
        return vol.listVolumeInfo(attrs);
    }

    /**
     * Creates a new CIFS share on the specified volume
     * 
     * @param mountPath - Path to mount as share
     * @param shareName - Name of new share
     * @param comment - Descriptive comment of share
     * @param maxusers - Optional. Maximum concurrent connections. Use -1 to set this to unlimited.
     * @param forcegroup - Optional. Name of group to which files created in share belong.
     * @return
     */
    public boolean addCIFSShare(String mountPath, String shareName, String comment, int maxusers, String forcegroup)
    {
        if (log.isDebugEnabled()) {
            log.debug("Add CIFS share to volume with params[volname,shareName,comment,maxusers]" +
                    mountPath + "," + shareName + "," + comment + "," + maxusers);
        }
        FileShare share = new FileShare(server.getNaServer(), mountPath);
        return share.addCIFSShare(shareName, comment, maxusers, forcegroup);
    }

    /**
     * Deletes a CIFS share, but not the underlying storage
     * 
     * @param shareName
     */
    public void deleteCIFSShare(String shareName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Delete CIFS share with params[shareName]" + shareName);
        }
        FileShare share = new FileShare(server.getNaServer(), "");
        share.deleteCIFSShare(shareName);
    }

    /**
     * Lists CIFS Shares.
     * Most keys in returned Maps are optional. For example, description and maxusers are only returned if they are set.
     * For exaample: {description=Testing, maxusers=5, share-name=demotest, mount-point=/vol/volscott}
     * 
     * @param shareName - can contain wildcard * or ?
     * @return
     */
    public List<Map<String, String>> listCIFSShares(String shareName) {
        FileShare share = new FileShare(server.getNaServer(), null);
        return share.listCIFSInfo(shareName);
    }

    public void changeCIFSShare(String shareName, Map<String, String> attrs) {
        FileShare share = new FileShare(server.getNaServer(), null);
        share.changeCIFSShare(shareName, attrs);
    }

    public void changeCIFSShare(String shareName, String attr, String value) {
        Map<String, String> attrs = Maps.newHashMap();
        attrs.put(attr, value);
        changeCIFSShare(shareName, attrs);
    }

    /**
     * Lists CIFS Access Control Lists.
     * 
     * @param shareName - can contain wildcard * or ?
     * @return
     */
    public List<CifsAcl> listCIFSAcls(String shareName) {
        FileShare share = new FileShare(server.getNaServer(), null);
        return share.listCIFSAcls(shareName);
    }

    /**
     * set CIFS Access Control List
     * 
     * @param acl
     */
    public void setCIFSAcl(CifsAcl acl) {
        FileShare share = new FileShare(server.getNaServer(), null);
        share.setCIFSAcl(acl);
    }

    /**
     * delete CIFS Access Control List
     * 
     * @param acl
     */
    public void deleteCIFSAcl(CifsAcl acl) {
        FileShare share = new FileShare(server.getNaServer(), null);
        share.deleteCIFSAcl(acl);
    }

    /**
     * Lists NFS Exports.
     * Keys in returned map are: actual-pathname, anon, nosuid, pathname, read-only, read-write, root, sec-flavor
     * 
     * NOTE : This method is deprecated as it can fail if an NFS Export has more than one rule {@link #listNFSExportRules(String)} should be
     * used instead
     * 
     * @param pathName - only return info about specified path.
     */
    @Deprecated
    public List<Map<String, String>> listNFSExports(String pathName) {
        FileShare share = new FileShare(server.getNaServer(), null);
        return share.listNFSInfo(pathName);
    }

    public List<ExportsRuleInfo> listNFSExportRules(String pathName)
    {
        FileShare share = new FileShare(server.getNaServer(), null);
        return share.listNFSExportRules(pathName);
    }

    /**
     * Adds a new NFS share.
     * 
     * @param mountPath - The mount path
     * @param exportPath - The exported path of the share.
     * @param anonymousUid - All hosts with this uid have root access to the directory. Use
     *            -1 to leave 'unspecified'
     * @param roHosts - List of hosts with read-only access
     * @param roAddAll - Give all hosts read-only access. If true, roHosts is ignored.
     * @param rwHosts - List of hosts with read-write access
     * @param rwAddAll - Give all hosts read-write access. If true, rwHosts is ignored.
     * @param rootHosts - List of hosts with root access
     * @param rootAddAll - Give all hosts root access. If true, rootHosts is ignored.
     * @param securityStyle - List of security styles this share supports.
     * @return - Returns
     */
    public List<String> addNFSShare(String mountPath, String exportPath, int anonymousUid,
            List<String> roHosts, boolean roAddAll, List<String> rwHosts, boolean rwAddAll,
            List<String> rootHosts, boolean rootAddAll, List<NFSSecurityStyle> securityStyle)
    {
        FileShare share = new FileShare(server.getNaServer(), mountPath);
        return share.addNFSShare(exportPath, anonymousUid, roHosts, roAddAll,
                rwHosts, rwAddAll, rootHosts, rootAddAll, securityStyle);
    }

    /**
     * Deletes a named NFS mounted share. As a convenience this function can also delete all
     * NFS shares.
     * 
     * @param mountPath - Path of share to delete
     * @param deleteAll - If true *ALL* mounted NFS shares will be removed. Be careful!
     * @return - Returns a list of deleted paths.
     */
    public List<String> deleteNFSShare(String mountPath, boolean deleteAll)
    {
        FileShare share = new FileShare(server.getNaServer(), mountPath);
        return share.deleteNFSShare(deleteAll);
    }

    /**
     * get list of FC WWNs .
     * 
     * @param verbose - if true, unused port names are also listed
     * @return
     */
    public List<Map<String, String>> listWWNs(boolean verbose) {
        NaElement elem = new NaElement("fcp-port-name-list-info");
        elem.addNewChild("verbose", Boolean.toString(verbose));

        try {
            List<Map<String, String>> list = Lists.newArrayList();
            NaElement result = server.getNaServer().invokeElem(elem);
            for (NaElement portElem : (List<NaElement>) result.getChildByName("fcp-port-names").getChildren()) {
                Map<String, String> port = Maps.newHashMap();
                for (NaElement portChild : (List<NaElement>) portElem.getChildren()) {
                    port.put(portChild.getName(), portChild.getContent());
                }
                list.add(port);
            }
            return list;
        } catch (Exception e) {
            String msg = "Failed to get fcp port names";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get list of iSCSI interfaces .
     * 
     * @return
     */
    public List<Map<String, String>> listIscsiInterfaceInfo(String interfaceName) {

        NaElement elem = new NaElement("iscsi-interface-list-info");
        if (StringUtils.isNotBlank(interfaceName)) {
            elem.addNewChild("interface-name", interfaceName);
        }

        try {
            NaElement result = server.getNaServer().invokeElem(elem);

            List<Map<String, String>> iscsiInterfaceInfos = Lists.newArrayList();
            for (NaElement entry : (List<NaElement>) result.getChildByName("iscsi-interface-list-entries").getChildren()) {
                Map<String, String> iscsiInterfaceInfo = Maps.newHashMap();
                for (NaElement child : (List<NaElement>) entry.getChildren()) {
                    String name = child.getName();
                    String value = child.getContent();
                    iscsiInterfaceInfo.put(name, value);
                }
                iscsiInterfaceInfos.add(iscsiInterfaceInfo);
            }

            return iscsiInterfaceInfos;
        } catch (Exception e) {
            String msg = "Failed to get iscsi interfaces";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    //
    /**
     * get list of iSCSI initiator
     * 
     * @return
     */
    public List<Map<String, String>> listIscsiInitiatorInfo() {

        NaElement elem = new NaElement("iscsi-initiator-list-info");

        try {
            NaElement result = server.getNaServer().invokeElem(elem);

            List<Map<String, String>> iscsiInterfaceInfos = Lists.newArrayList();
            for (NaElement entry : (List<NaElement>) result.getChildByName("iscsi-initiator-list-entries").getChildren()) {
                Map<String, String> iscsiInterfaceInfo = Maps.newHashMap();
                for (NaElement child : (List<NaElement>) entry.getChildren()) {
                    String name = child.getName();
                    String value = child.getContent();
                    iscsiInterfaceInfo.put(name, value);
                }
                iscsiInterfaceInfos.add(iscsiInterfaceInfo);
            }

            return iscsiInterfaceInfos;
        } catch (Exception e) {
            String msg = "Failed to get iscsi initiators";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get list of iSCSI initiator
     * 
     * @return
     */
    public List<Map<String, String>> listIscsiPortalInfo() {

        NaElement elem = new NaElement("iscsi-portal-list-info");

        try {
            NaElement result = server.getNaServer().invokeElem(elem);

            List<Map<String, String>> iscsiPortalInfos = Lists.newArrayList();
            for (NaElement entry : (List<NaElement>) result.getChildByName("iscsi-portal-list-entries").getChildren()) {
                Map<String, String> iscsiPortal = Maps.newHashMap();
                for (NaElement child : (List<NaElement>) entry.getChildren()) {
                    String name = child.getName();
                    String value = child.getContent();
                    iscsiPortal.put(name, value);
                }
                iscsiPortalInfos.add(iscsiPortal);
            }

            return iscsiPortalInfos;
        } catch (Exception e) {
            String msg = "Failed to get iscsi portals";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Get FC Node Name
     * 
     * @return Node Name
     */
    public String getFcWWNN() {
        String nodeName = null;

        NaElement result = server.invoke("fcp-node-get-name");
        if (result != null) {
            nodeName = result.getChildContent("node-name");
        }

        return nodeName;
    }

    /**
     *
     */
    public List<DiskDetailInfo> listDiskInfo(String disk, String ownershipType) {
        List<DiskDetailInfo> disks = new ArrayList<DiskDetailInfo>();

        NaElement input = new NaElement("disk-list-info");
        if (StringUtils.isNotBlank(disk)) {
            input.addNewChild("disk", disk);
        }
        if (StringUtils.isNotBlank(ownershipType)) {
            input.addNewChild("ownership-type", ownershipType);
        }

        NaElement result = server.invoke(input);

        if (result != null) {
            NaElement diskDetails = result.getChildByName("disk-details");
            if (diskDetails != null) {
                for (NaElement diskDetailElem : (List<NaElement>) diskDetails.getChildren()) {
                    if (diskDetailElem != null) {
                        DiskDetailInfo diskDetailInfo = new DiskDetailInfo();
                        diskDetailInfo.setAggregate(diskDetailElem.getChildContent("aggregate"));
                        diskDetailInfo.setDiskModel(diskDetailElem.getChildContent("disk-model"));
                        diskDetailInfo.setDiskType(diskDetailElem.getChildContent("disk-type"));
                        diskDetailInfo.setDiskUid(diskDetailElem.getChildContent("disk-uid"));
                        diskDetailInfo.setName(diskDetailElem.getChildContent("name"));
                        diskDetailInfo.setPool(diskDetailElem.getChildContent("pool"));
                        diskDetailInfo.setRaidGroup(diskDetailElem.getChildContent("raid-group"));
                        diskDetailInfo.setRaidState(diskDetailElem.getChildContent("raid-state"));
                        diskDetailInfo.setRaidType(diskDetailElem.getChildContent("raid-type"));
                        diskDetailInfo.setRpm((Integer) ConvertUtils.convert(diskDetailElem.getChildContent("rpm"), Integer.class));
                        diskDetailInfo.setPhysicalSpace((Long) ConvertUtils.convert(diskDetailElem.getChildContent("physical-space"),
                                Long.class));
                        disks.add(diskDetailInfo);
                    }
                }
            }
        }

        return disks;
    }

    /**
     * @return A list of specific CIFS configuration values
     */
    public Map<String, String> listCIFSConfig() {
        NaElement elem = new NaElement("cifs-list-config");

        Map<String, String> properties = Maps.newHashMap();
        try {
            NaElement result = server.getNaServer().invokeElem(elem);

            properties.put("NetBIOS-domainName", result.getChildContent("NetBIOS-domainname"));
            properties.put("DNS-domainName", result.getChildContent("DNS-domainname"));
            properties.put("NetBIOS-servername", result.getChildContent("NetBIOS-servername"));

            if (result.getChildByName("DC-connection") != null) {
                properties.put("DC-Address", result.getChildByName("DC-connection")
                        .getChildByName("connection-info")
                        .getChildByName("favored-address")
                        .getChildByName("address-info")
                        .getChildContent("ip-address"));
            }
            else if (result.getChildByName("LDAP-connection") != null) {
                properties.put("DC-Address", result.getChildByName("LDAP-connection")
                        .getChildByName("connection-info")
                        .getChildByName("favored-address")
                        .getChildByName("address-info")
                        .getChildContent("ip-address"));
            }

        } catch (Exception e) {
            String msg = "Failed to get array system info";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        return properties;
    }

    /**
     * get array system-info.
     * 
     * @return map of name/values as returned from system-get-info API
     */
    public Map<String, String> systemInfo() {
        HashMap<String, String> info = new HashMap<String, String>();
        NaElement elem = new NaElement("system-get-info");
        NaElement result = null;

        /*
         * For example: {partner-system-id=0142226671, board-speed=1666, system-name=iWaveTST0,
         * cpu-serial-number=8006251, system-serial-number=700000567389, memory-size=4096,
         * number-of-processors=2, system-id=0142223979, partner-system-name=iWaveTST1,
         * system-revision=B0, controller-address=A, vendor-id=NetApp, system-machine-type=FAS2040,
         * system-model=FAS2040, board-type=System Board XVII, cpu-processor-id=0x6ec, cpu-part-number=110-00133,
         * supports-raid-array=false, cpu-revision=A4, cpu-firmware-release=6.1, cpu-microcode-version=85}
         */

        try {
            result = server.getNaServer().invokeElem(elem).getChildByName("system-info");
            for (NaElement child : (List<NaElement>) result.getChildren()) {
                String name = child.getName();
                info.put(name, child.getContent());
            }
            return info;
        } catch (Exception e) {
            String msg = "Failed to get array system info";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get array system-info.
     * 
     * @return map of name/values as returned from system-get-info API
     */
    public Map<String, String> systemVersion() {
        HashMap<String, String> info = new HashMap<String, String>();
        NaElement elem = new NaElement("system-get-version");
        NaElement result = null;

        try {
            result = server.getNaServer().invokeElem(elem).getChildByName("version");
            if (result != null) {
                String name = result.getName();
                info.put(name, result.getContent());
            }
            result = server.getNaServer().invokeElem(elem).getChildByName("is-clustered");
            if (result != null) {
                String name = result.getName();
                info.put(name, result.getContent());
            }
            return info;
        } catch (Exception e) {
            String msg = "Failed to get array system version " + e.getMessage();
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get iSCSI target node name.
     * 
     * @return
     */
    public String getNodeName() {
        NaElement elem = new NaElement("iscsi-node-get-name");

        try {
            NaElement result = server.getNaServer().invokeElem(elem);
            return result.getChildContent("node-name");
        } catch (Exception e) {
            String msg = "Failed to get iSCSI node name";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get netbios aliases.
     * 
     * @return
     */
    public List<String> getNetbiosAliases() {
        NaElement elem = new NaElement("cifs-nbalias-names-get");
        List<String> names = Lists.newArrayList();

        try {
            NaElement result = server.getNaServer().invokeElem(elem).getChildByName("nbalias-names");
            if (result != null) {
                for (NaElement name : (List<NaElement>) result.getChildren()) {
                    names.add(name.getContent());
                }
            }
            return names;
        } catch (Exception e) {
            String msg = "Failed to get netbios aliases";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Sets the security style of a QTree
     * 
     * @param path full path of the QTree (i.e. /vol/MyVolume/MyTree)
     * @param type either ntfs, unix, mixed
     */
    public void setQTreeSecurityStyle(String path, String type) {
        String output = invokeCliCommand(new String[] { "qtree", "security", path, type });

        // Either blank, or a Quota warning message, means we're successful
        if (!StringUtils.isBlank(output) && !output.startsWith("qtree: Changing the security style")) {
            throw new NetAppException(output);
        }
    }

    public void setQTreeOplocks(String qtreePath, String oplocks) {
        String infoString = "NetAppFacade::setQTreeOplocks -> Trying to set oplocks = " + oplocks + " on qtree = " + qtreePath;
        log.info(infoString);
        NaElement elem = new NaElement("system-cli");
        NaElement argsarray = new NaElement("args");
        argsarray.addChildElem(new NaElement("arg", "qtree"));
        argsarray.addChildElem(new NaElement("arg", "oplocks"));
        argsarray.addChildElem(new NaElement("arg", qtreePath));
        argsarray.addChildElem(new NaElement("arg", oplocks));
        elem.addChildElem(argsarray);
        try {
            server.getNaServer().invokeElem(elem);
        } catch (Exception e) {
            String msg = "NetAppFacade::setQTreeOplocks -> Failed to invoke CLI command ";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Invokes a CLI command through the API
     * 
     * @param args
     * @return
     */
    public String invokeCliCommand(String args[]) {
        String cliResult;
        NaElement elem = new NaElement("system-cli");
        NaElement argsarray = new NaElement("args");
        for (int i = 0; i < args.length; i++) {
            argsarray.addNewChild("arg", args[i]);
        }
        elem.addChildElem(argsarray);
        // Call the NetApp API
        try {
            NaElement result = server.getNaServer().invokeElem(elem);
            cliResult = result.getChildContent("cli-output");
            return cliResult;
        } catch (Exception e) {
            String msg = "Failed to invoke CLI command ";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    public List<Quota> listQuotas()
    {
        return listQuotas(null, null);
    }

    public List<Quota> listQuotasByPath(String path)
    {
        return listQuotas(path, null);
    }

    public List<Quota> listQuotasByVolume(String volume) {
        return listQuotas(null, volume);
    }

    public List<Quota> listQuotas(String path, String volume)
    {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving quotas");
        }

        QuotaCommands quotaCommands = new QuotaCommands(this.server.getNaServer());
        return quotaCommands.quotaReport(path, volume);

    }

    public List<Qtree> listQtrees() {
        return listQtrees(null);
    }

    public List<Qtree> listQtrees(String volume) {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving qtrees");
        }

        QtreeCommands qtreeCommands = new QtreeCommands(this.server.getNaServer());
        return qtreeCommands.listQtree(volume);
    }

    /**
     * Creates a Qtree rooted in the specified volume.
     * 
     * @param qtree the Qtree name.
     * @param volume the volume name.
     */
    public void createQtree(String qtree, String volume) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Qtree " + qtree + " in volume " + volume);
        }
        QtreeCommands commands = new QtreeCommands(server.getNaServer());
        commands.createQtree(qtree, volume);
    }

    /**
     * Deletes the qtree at the given path (/vol/&lt;volume&gt;/&lt;qtree&gt;)
     * 
     * @param path the qtree path.
     * @param force whether to force deletion even if the qtree is not empty.
     */
    public void deleteQtree(String path, boolean force) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting Qtree " + path);
        }
        QtreeCommands commands = new QtreeCommands(server.getNaServer());
        commands.deleteQtree(path, force);
    }

    /**
     * Creates a Qtree rooted in the specified volume.
     * 
     * @param qtree the Qtree name.
     * @param volume the volume name.
     * @param mode the permissions on the qtree (similar to unix file permissions, 0755 for example)
     */
    public void createQtree(String qtree, String volume, String mode) {
        if (log.isDebugEnabled()) {
            log.debug("Creating Qtree " + qtree + " in volume " + volume + " [mode=" + mode + "]");
        }
        QtreeCommands commands = new QtreeCommands(server.getNaServer());
        commands.createQtree(qtree, volume, mode);
    }

    /**
     * Gets a tree quota for the given volume and path.
     * 
     * @param volume the volume name.
     * @param path the path for the quota.
     * @return the quota.
     */
    public Quota getQuota(String volume, String quotaTarget, String quotaType, String qtree) {
        if (log.isDebugEnabled()) {
            log.debug("Getting " + quotaType + " quota for volume " + volume + ", quotaTarget "
                    + quotaTarget);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        return commands.getQuota(volume, quotaTarget, quotaType, qtree);
    }

    /**
     * Gets a tree quota for the given volume and path.
     * 
     * @param volume the volume name or volume path.
     * @param path the path for the quota.
     * @return the quota.
     */
    public Quota getTreeQuota(String volume, String path) {
        if (log.isDebugEnabled()) {
            log.debug("Getting tree quota for volume " + volume + ", path " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        return commands.getTreeQuota(volume, path);
    }

    /**
     * Sets a disk-limit tree quota. If the tree quota doesn't already exist, it is created. The
     * volume can be specified as a name only, or as the volume path (/vol/<volume-name>). The disk
     * limit and threshold values are specified in KB.
     * 
     * @param volume the volume name or volume path containing the path to apply quota.
     * @param path the path which will have a disk limit applied.
     * @param diskLimitInKB the disk limit in KB.
     * @param thresholdInKB the threshold in KB after which a message will be logged. The threshold
     *            is only set if it is greater than 0.
     */
    public void setDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Setting disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.setDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    /**
     * Addss a disk-limit tree quota. The volume can be specified as a name only,
     * or as the volume path (/vol/<volume-name>). The disk limit and threshold values are specified
     * in KB.
     * 
     * @param volume the volume name or volume path containing the path to apply quota.
     * @param path the path which will have a disk limit applied.
     * @param diskLimitInKB the disk limit in KB.
     * @param thresholdInKB the threshold in KB after which a message will be logged. The threshold
     *            is only set if it is greater than 0.
     */
    public void addDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Setting disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.addDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    public void modifyDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        if (log.isDebugEnabled()) {
            log.debug("Modifying disk limit tree quota of " + diskLimitInKB + " KB to " + path);
        }

        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.modifyDiskLimitTreeQuota(volume, path, diskLimitInKB, thresholdInKB);
    }

    public void deleteQuota(String volume, String quotaTarget, String quotaType, String qtree) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting " + quotaType + " quota on " + quotaTarget);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.deleteQuota(volume, quotaTarget, quotaType, qtree);
    }

    public void deleteTreeQuota(String volume, String path) {
        if (log.isDebugEnabled()) {
            log.debug("Deleting tree quota for " + path);
        }
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.deleteTreeQuota(volume, path);
    }

    /**
     * Returns the current status of Quotas on the specified volume
     */
    public QuotaCommands.QuotaStatus getQuotaStatus(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        return commands.getQuotaStatus(volume);
    }

    /**
     * Starts a Resize Quota operation on the specified volume.
     * 
     * This only starts the resize operation and returns immediately, {@link #getQuotaStatus(String)} should be used
     * to find out when the current status of the operation
     */
    public void startQuotaResize(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.startResize(volume);
    }

    /**
     * Starts to turn quotas on for a volume. A successful return from this API does not mean that quotas are on,
     * merely that an attempt to start it has been triggered
     */
    public void turnQuotaOn(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.turnQuotaOn(volume);
    }

    /**
     * Turns the quota subsystem off for a volume
     */
    public void turnQuotaOff(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.turnQuotaOff(volume);
    }

    /**
     * Reinitialize quota subsystem off for a volume
     */
    public void reintializeQuota(String volume) {
        QuotaCommands commands = new QuotaCommands(server.getNaServer());
        commands.turnQuotaOff(volume);
        commands.turnQuotaOn(volume);
    }

    /**
     * List of snapshots for a volume
     * 
     * @param volumeName - Name of the volume containing the snapshots
     * @return - Collection containing snapshots .
     */
    public Collection<String> listSnapshots(String volumeName)
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing snapshots on volume with params[volName]: " +
                    volumeName);
        }
        Volume vol = new Volume(server.getNaServer(), volumeName);
        Collection<String> attrs = new ArrayList<String>();
        attrs.add("name");
        return vol.listSnapshots(attrs);
    }

    public List<VFilerInfo> listVFilers()
    {
        if (log.isDebugEnabled()) {
            log.debug("Listing vFilers");
        }

        VFiler vFiler = new VFiler(server.getNaServer(), null);
        return vFiler.listVFilers(true);
    }

    public boolean addStorage(String storagePath, String vFilerName) {
        if (log.isDebugEnabled()) {
            log.debug("Adding storage to vfiler");
        }

        VFiler vFiler = new VFiler(server.getNaServer(), null);
        return vFiler.addStorage(storagePath, vFilerName);
    }

    /**
     * Adds a new NFS share.
     * 
     * @param mountPath - The mount path
     * @param exportPath - The exported path of the share.
     * @param anonymousUid - All hosts with this uid have root access to the directory. Use
     *            -1 to leave 'unspecified'
     * @param roHosts - List of hosts with read-only access
     * @param roAddAll - Give all hosts read-only access. If true, roHosts is ignored.
     * @param rwHosts - List of hosts with read-write access
     * @param rwAddAll - Give all hosts read-write access. If true, rwHosts is ignored.
     * @param rootHosts - List of hosts with root access
     * @param rootAddAll - Give all hosts root access. If true, rootHosts is ignored.
     * @param securityStyle - List of security styles this share supports.
     * @return - Returns
     */
    public List<String> addNewNFSShare(String exportPath, List<ExportRule> exportRules)
    {
        FileShare share = new FileShare(server.getNaServer(), exportPath);
        return share.addNewNFSShare(exportPath, exportRules);
    }

    public List<String> modifyNFSShare(String exportPath, List<ExportRule> exportRules)
    {
        FileShare share = new FileShare(server.getNaServer(), exportPath);
        return share.modifyNFSShare(exportPath, exportRules);
    }

}
