/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

/**
 * @author sdorcas
 *         Not to be used directly by users or Orchestrator services. Use NetAppFacade.
 */
class Volume {
    private final Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;

    public Volume(NaServer server, String volumeName)
    {
        this.server = server;
        name = volumeName;
    }

    /**
     * Takes a volume offline
     * 
     * @param delayInMinutes - number of minutes to wait before the volume goes offline.
     *            Use zero to take offline immediately.
     */
    void setVolumeOffline(int delayInMinutes)
    {
        if (!isOnline()) {
            log.info("volume " + name + " is already offline.");
            return;
        }

        NaElement elem = new NaElement("volume-offline");
        elem.addNewChild("cifs-delay", Integer.toString(delayInMinutes));
        elem.addNewChild("name", name);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to volume off line: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    boolean isOnline() {
        return "online".equals(getVolumeInfo(false).get("state"));
    }

    /**
     * Creates a new flexible volume. Only parameters for flexible volumes are provided.
     * Note the volume may not be operational immediately after this method returns. Use
     * getVolumeInfo() to query the status of the new volume.
     * 
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
     * @return
     */
    boolean createFlexibleVolume(String containingAggrName, boolean isSnapLock,
            String remoteLocation, String size, String snaplockType, String spaceReserve)
    {
        NaElement elem = new NaElement("volume-create");
        elem.addNewChild("volume", name);

        if (isSnapLock) {
            elem.addNewChild("is-snaplock", Boolean.toString(isSnapLock));
        }

        // Remaining params are optional
        if (containingAggrName != null && !containingAggrName.isEmpty()) {
            elem.addNewChild("containing-aggr-name", containingAggrName);
        }
        if (remoteLocation != null && !remoteLocation.isEmpty()) {
            elem.addNewChild("remote-location", remoteLocation);
        }
        if (size != null && !size.isEmpty()) {
            elem.addNewChild("size", size);
        }
        if (snaplockType != null && !snaplockType.isEmpty()) {
            elem.addNewChild("snaplock-type", snaplockType);
        }
        if (spaceReserve != null && !spaceReserve.isEmpty()) {
            elem.addNewChild("space-reserve", spaceReserve);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create new volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Destroys a volume, releasing all storage blocks assigned to it.
     * 
     * @param force - force destroy. Set to false if unsure.
     * @return - true if successful, false if the operation failed
     */
    boolean destroyVolume(boolean force)
    {
        NaElement elem = new NaElement("volume-destroy");
        elem.addNewChild("force", Boolean.toString(force));
        elem.addNewChild("name", name);

        try {
            server.invokeElem(elem);
            return true;
        } catch (Exception e) {
            String msg = "Failed to destroy volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Disables SIS on a volume
     * 
     * @return - true if successful, false otherwise
     */
    boolean disableSis()
    {
        String volumePath = "/vol/" + name;
        NaElement elem = new NaElement("sis-disable");
        elem.addNewChild("path", volumePath);

        try {
            server.invokeElem(elem);
            return true;
        } catch (Exception e) {
            String msg = "Failed to disable SIS on volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Enables SIS and sets the schedule. Schedule format is ONTAP schedule format.
     * 
     * @param schedule - example "sat-sun[@0-23]"
     * @return
     */
    boolean enableSis(String schedule)
    {
        String volumePath = "/vol/" + name;

        NaElement elem = new NaElement("sis-enable");
        elem.addNewChild("path", volumePath);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            log.error("Failed to enable SIS on volume: " + name, e);
            return false; // Return now
        }
        // Now set the schedule
        if (schedule == null || schedule.isEmpty()) {
            schedule = "auto"; // default the schedule to auto
        }
        elem = new NaElement("sis-set-config");
        elem.addNewChild("path", volumePath);
        elem.addNewChild("schedule", schedule);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to set SIS schedule on volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Sets an option (volume-set-option) on a volume.
     * 
     * @param optionName - Name of the option to set
     * @param optionValue - Value of the option
     * @return - true/success, false/failed
     */
    boolean setVolumeOption(VolumeOptionType optionName, String optionValue)
    {
        NaElement elem = new NaElement("volume-set-option");
        elem.addNewChild("option-name", optionName.name());
        elem.addNewChild("option-value", optionValue);
        elem.addNewChild("volume", name);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append(optionName.name()).append("=").append(optionValue);
            String msg = "Failed to set volume option: " + sb.toString();
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Returns a name-value pair map of the volume settings.
     * 
     * @param verbose - set to true for all settings
     * @return - map of volume setting name/value.
     */
    Map<String, String> getVolumeInfo(boolean verbose)
    {
        Map<String, String> result = null;
        NaElement elem = new NaElement("volume-list-info");
        elem.addNewChild("verbose", Boolean.toString(verbose));
        elem.addNewChild("volume", name);
        try {
            NaElement resultElem = server.invokeElem(elem).getChildByName("volumes");
            resultElem = resultElem.getChildByName("volume-info");
            if (resultElem != null && resultElem.hasChildren()) {
                result = new HashMap<String, String>();
                List<NaElement> children = resultElem.getChildren();
                for (NaElement e : children) {
                    result.put(e.getName(), e.getContent());
                }
            }
        } catch (Exception e) {
            String msg = "Failed to get volume info: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return result;
    }

    /**
     * Returns the size of the volume
     * 
     * @return
     */
    String getVolumeSize()
    {
        String size = "";
        NaElement elem = new NaElement("volume-size");
        elem.addNewChild("volume", name);

        NaElement result = null;
        try {
            result = server.invokeElem(elem);
            size = result.getChildContent("volume-size");
        } catch (Exception e) {
            String msg = "Failed to retrieve size of volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return size;
    }

    /**
     * Sets a new size of a volume.
     * 
     * @param newSize - Size (with unit) of the new volume. Ex: 10g, 2000m, 1t.
     * @return - The new size of the volume.
     */
    String setVolumeSize(String newSize)
    {
        String size = "";

        NaElement elem = new NaElement("volume-size");
        elem.addNewChild("volume", name);
        elem.addNewChild("new-size", newSize);

        NaElement result = null;
        try {
            result = server.invokeElem(elem);
            size = result.getChildContent("volume-size");
        } catch (Exception e) {
            String msg = "Failed to set size of volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return size;
    }

    /*
     * List<String> listVolumes()
     * {
     * Map<String, String> result = null;
     * NaElement elem = new NaElement("volume-list-info");
     * elem.addNewChild("verbose", "false");
     * NaElement resultElem = null;
     * try {
     * resultElem = server.invokeElem(elem).getChildByName("volumes");
     * } catch (Exception e) {
     * String msg = "Failed to get list of Volumes.";
     * log.error(msg, e);
     * throw new NetAppException(msg, e);
     * }
     * ArrayList<String> volumes = new ArrayList<String>();
     * for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
     * volumes.add(e.getChildContent("name"));
     * }
     * return volumes;
     * }
     * 
     * List<Map<String, String>> listVolumeInfo(Collection<String> attrs)
     * {
     * NaElement elem = new NaElement("volume-list-info");
     * 
     * if (name != null && !name.isEmpty()) {
     * elem.addNewChild("volume", name);
     * }
     * 
     * elem.addNewChild("verbose", "true");
     * 
     * NaElement resultElem = null;
     * 
     * try {
     * resultElem = server.invokeElem(elem).getChildByName("volumes");
     * } catch (Exception e) {
     * String msg = "Failed to get list of Volumes.";
     * log.error(msg, e);
     * throw new NetAppException(msg, e);
     * }
     * 
     * ArrayList<Map<String, String>> volumes = new ArrayList<Map<String, String>>();
     * 
     * for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
     * Map<String, String> infos = new HashMap<String, String>();
     * for (NaElement info : (List<NaElement>) e.getChildren()) {
     * String name = info.getName();
     * if (attrs == null || attrs.contains(name) || name.equals("name")) {
     * infos.put(name, info.getContent());
     * }
     * }
     * volumes.add(infos);
     * }
     * 
     * return volumes;
     * }
     */

    List<String> listVolumes()
    {
        Map<String, String> result = null;
        Map<String, String> params = new HashMap<String, String>();
        String cmd = "volume-list-info";

        params.put("verbose", "false");

        NaElement elem = new NaElement("volume-list-info");
        elem.addNewChild("verbose", "false");
        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(elem).getChildByName("volumes");
        } catch (Exception e) {
            String msg = "Failed to get list of Volumes.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        ArrayList<String> volumes = new ArrayList<String>();
        for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
            volumes.add(e.getChildContent("name"));
        }
        return volumes;
    }

    List<Map<String, String>> listVolumeInfo(Collection<String> attrs)
    {
        NaElement elem = new NaElement("volume-list-info");

        if (name != null && !name.isEmpty()) {
            elem.addNewChild("volume", name);
        }

        elem.addNewChild("verbose", "true");

        NaElement resultElem = null;

        try {
            resultElem = server.invokeElem(elem).getChildByName("volumes");
        } catch (Exception e) {
            String msg = "Failed to get list of Volumes.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        ArrayList<Map<String, String>> volumes = new ArrayList<Map<String, String>>();

        for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
            Map<String, String> infos = new HashMap<String, String>();
            for (NaElement info : (List<NaElement>) e.getChildren()) {
                String name = info.getName();
                if (attrs == null || attrs.contains(name) || name.equals("name")) {
                    infos.put(name, info.getContent());
                }
            }
            volumes.add(infos);
        }

        return volumes;
    }

    /**
     * Creates a new snapshot of the volume.
     * 
     * @param snapshotName - Required. Name of the snapshot.
     * @param isValidLunCloneSnapshot - Optional. True indicates requested by snapvault.
     *            All snapshots for lun clones will be locked. Default is false.
     * @param async - Optional. Default is false. True indicates created asynchronously.
     * @return - true/false if operation succeeded.
     */
    boolean createSnapshot(String snapshotName, boolean isValidLunCloneSnapshot, boolean async)
    {
        NaElement elem = new NaElement("snapshot-create");
        elem.addNewChild("volume", name);
        elem.addNewChild("snapshot", snapshotName);
        elem.addNewChild("async", Boolean.toString(async));
        elem.addNewChild("is-valid-lun-clone-snapshot", Boolean.toString(isValidLunCloneSnapshot));

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create snapshot on volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Deletes a snapshot given the snapshot name.
     * 
     * @param snapshotName - name of the snapshot to delete.
     * @return - true/false if operation succeeded.
     */
    boolean deleteSnapshot(String snapshotName)
    {
        NaElement elem = new NaElement("snapshot-delete");
        elem.addNewChild("volume", name);
        elem.addNewChild("snapshot", snapshotName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete snapshot: " + snapshotName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Restores a volume to a specified snapshot.
     * 
     * @param snapshotName - Name of the snapshot to restore from.
     * @return - true/false if operation succeeded.
     */
    boolean restoreVolumeFromSnapshot(String snapshotName)
    {
        NaElement elem = new NaElement("snapshot-restore-volume");
        elem.addNewChild("volume", name);
        elem.addNewChild("snapshot", snapshotName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to restore volume to specified snapshot: " + snapshotName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Deletes a snapshot given the snapshot name.
     * 
     * @param snapshotName - name of the snapshot to delete.
     * @return - List<String> list of snapsshots for .
     */
    List<String> listSnapshots(Collection<String> attrs)
    {
        NaElement elem = new NaElement("snapshot-list-info");
        elem.addNewChild("volume", name);
        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to list snapshot for volume: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        ArrayList<String> snaps = new ArrayList<String>();

        for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
            for (NaElement info : (List<NaElement>) e.getChildren()) {
                for (NaElement info2 : ((List<NaElement>) info.getChildren())) {
                    String name = info2.getName();
                    if (attrs == null || attrs.contains(name) || name.equals("name")) {
                        snaps.add(info2.getContent());
                    }
                }

            }

        }
        return snaps;
    }

}
