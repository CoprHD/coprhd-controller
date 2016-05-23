/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

public class FlexVolume {
    private Logger log = Logger.getLogger(getClass());
    private String name = "";
    private NaServer server = null;

    public FlexVolume(NaServer server, String volumeName)
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
    public void setVolumeOffline(int delayInMinutes)
    {
        if (!isOnline()) {
            log.info("volume " + name + " is already offline.");
            return;
        }

        NaElement elem = new NaElement("volume-offline");
        elem.addNewChild("name", name);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to volume off line: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    boolean isOnline() {
        return "online".equals(getVolumeInfo().get("state"));
    }

    /**
     * Creates a new flexible volume. Only parameters for flexible volumes are provided.
     * Note the volume may not be operational immediately after this method returns. Use
     * getVolumeInfo() to query the status of the new volume.
     * 
     * @param containingAggrName - Optional. Name of the aggregate in which to create the volume. Must
     *            be used in conjunction with the size parameter.
     * @param path - Optional. The junction path where the volume is to be mounted.
     * @param size - Optional. Size (with unit) of the new volume. Ex: 10g, 2000m, 1t. Must be used
     *            in conjunction with containingAggrName.
     * @param spaceReserve - Optional. Type of volume guarantee new volume will use. Valid
     *            values are "none", "file", "volume".
     * @param permission - Optional. Unix permission bits in octal string format.
     * @return
     */
    public boolean createFlexibleVolume(String containingAggrName,
            String path, String size, String spaceReserve, String permission)
    {
        NaElement elem = new NaElement("volume-create");
        elem.addNewChild("volume", name);

        // Remaining params are optional
        if (containingAggrName != null && !containingAggrName.isEmpty()) {
            elem.addNewChild("containing-aggr-name", containingAggrName);
        }
        if (size != null && !size.isEmpty()) {
            elem.addNewChild("size", size);
        }
        if (path != null && !path.isEmpty()) {
            elem.addNewChild("junction-path", path);
        }
        if (spaceReserve != null && !spaceReserve.isEmpty()) {
            elem.addNewChild("space-reserve", spaceReserve);
        }
        if (permission != null && !permission.isEmpty()) {
            elem.addNewChild("unix-permissions", permission);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create new volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return true;
    }

    public boolean createFlexibleVolume(String containingAggrName,
            String path, String size, String spaceReserve, String permission, String state, String type, String langCode) {
        NaElement elem = new NaElement("volume-create");
        elem.addNewChild("volume", name);

        // Remaining params are optional
        if (containingAggrName != null && !containingAggrName.isEmpty()) {
            elem.addNewChild("containing-aggr-name", containingAggrName);
        }
        if (size != null && !size.isEmpty()) {
            elem.addNewChild("size", size);
        }
        if (path != null && !path.isEmpty()) {
            elem.addNewChild("junction-path", path);
        }
        if (spaceReserve != null && !spaceReserve.isEmpty()) {
            elem.addNewChild("space-reserve", spaceReserve);
        }
        if (permission != null && !permission.isEmpty()) {
            elem.addNewChild("unix-permissions", permission);
        }

        //
        if (state != null && !state.isEmpty()) {
            elem.addNewChild("volume-state", state);
        }

        // type of volume dp, ls & rw (default is rw)
        if (state != null && !state.isEmpty()) {
            elem.addNewChild("volume-type", type);
        }

        if (langCode != null && !langCode.isEmpty()) {
            elem.addNewChild("language-code", langCode);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create new Flexiable volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return true;

    }

    /**
     * Destroys a volume, releasing all storage blocks assigned to it.
     * 
     * @param force - force destroy. Set to false if unsure.
     * @return - true if successful, false if the operation failed
     */
    public boolean destroyVolume(boolean force)
    {
        NaElement elem = new NaElement("volume-destroy");
        elem.addNewChild("name", name);

        try {
            server.invokeElem(elem);
            return true;
        } catch (Exception e) {
            String msg = "Failed to destroy volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    /**
     * Unmounts a volume
     * 
     * @param force - force unmount. Set to false if unsure.
     * @return - true if successful, false if the operation failed
     */
    public boolean unmountVolume(boolean force)
    {
        NaElement elem = new NaElement("volume-unmount");
        elem.addNewChild("force", Boolean.toString(force));
        elem.addNewChild("volume-name", name);

        try {
            server.invokeElem(elem);
            return true;
        } catch (Exception e) {
            String msg = "Failed to unmount volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    /**
     * Returns a name-value pair map of the volume settings.
     * 
     * @param verbose - set to true for all settings
     * @return - map of volume setting name/value.
     */
    public Map<String, String> getVolumeInfo()
    {
        Map<String, String> result = null;
        NaElement elem = new NaElement("volume-get-iter");

        if (name != null && !name.isEmpty()) {
            NaElement volumeIdAttrs = new NaElement("volume-id-attributes");
            volumeIdAttrs.addNewChild("name", name);
            NaElement volumeAttrs = new NaElement("volume-attributes");
            volumeAttrs.addChildElem(volumeIdAttrs);
            NaElement query = new NaElement("query");
            query.addChildElem(volumeAttrs);
            elem.addChildElem(query);
        }

        NaElement resultElem = null;
        String tag = null;

        try {
            do {
                NaElement results = server.invokeElem(elem);
                tag = results.getChildContent("next-tag");
                resultElem = results.getChildByName("attributes-list");
                if (resultElem != null) {
                    for (NaElement volInfo : (List<NaElement>) resultElem.getChildren()) {
                        result = new HashMap<String, String>();
                        NaElement volIdAttrs = volInfo.getChildByName("volume-id-attributes");
                        if (volIdAttrs != null) {
                            for (NaElement info : (List<NaElement>) volIdAttrs.getChildren()) {
                                result.put(info.getName(), info.getContent());
                            }
                        }
                        NaElement volStateAttrs = volInfo.getChildByName("volume-state-attributes");
                        if (volStateAttrs != null) {
                            for (NaElement info : (List<NaElement>) volStateAttrs.getChildren()) {
                                result.put(info.getName(), info.getContent());
                            }
                        }
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("volume-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to get volume info: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
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
            throw new NetAppCException(msg, e);
        }
        return size;
    }

    /**
     * Sets a new size of a volume.
     * 
     * @param newSize - Size (with unit) of the new volume. Ex: 10g, 2000m, 1t.
     * @return - The new size of the volume.
     */
    public String setVolumeSize(String newSize)
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
            throw new NetAppCException(msg, e);
        }
        return size;
    }

    public List<String> listVolumes()
    {
        NaElement elem = new NaElement("volume-get-iter");
        NaElement resultElem = null;
        String tag = null;
        ArrayList<String> volumes = new ArrayList<String>();
        try {
            do {
                NaElement result = server.invokeElem(elem);
                tag = result.getChildContent("next-tag");
                resultElem = result.getChildByName("attributes-list");
                if (resultElem != null) {
                    for (NaElement volInfo : (List<NaElement>) resultElem.getChildren()) {
                        NaElement volAttrs = volInfo.getChildByName("volume-id-attributes");
                        if (volAttrs != null) {
                            volumes.add(volAttrs.getChildContent("name"));
                        }
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("volume-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to get list of Volumes.";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }

        return volumes;
    }

    public List<Map<String, String>> listVolumeInfo(Collection<String> attrs)
    {
        NaElement elem = new NaElement("volume-get-iter");
        ArrayList<Map<String, String>> volumes = new ArrayList<Map<String, String>>();

        if (name != null && !name.isEmpty()) {
            NaElement volumeIdAttrs = new NaElement("volume-id-attributes");
            volumeIdAttrs.addNewChild("name", name);
            NaElement volumeAttrs = new NaElement("volume-attributes");
            volumeAttrs.addChildElem(volumeIdAttrs);
            NaElement query = new NaElement("query");
            query.addChildElem(volumeAttrs);
            elem.addChildElem(query);
        }

        NaElement resultElem = null;
        String tag = null;

        try {
            do {
                NaElement result = server.invokeElem(elem);
                tag = result.getChildContent("next-tag");
                resultElem = result.getChildByName("attributes-list");

                if (resultElem != null) {
                    for (NaElement volInfo : (List<NaElement>) resultElem.getChildren()) {
                        Map<String, String> infos = new HashMap<String, String>();
                        NaElement volAttrs = volInfo.getChildByName("volume-id-attributes");
                        if (volAttrs != null) {
                            for (NaElement info : (List<NaElement>) volAttrs.getChildren()) {
                                String name = info.getName();
                                if (attrs == null || attrs.contains(name) || name.equals("name")) {
                                    infos.put(name, info.getContent());
                                }
                            }
                        }
                        NaElement volSpaceAttrs = volInfo.getChildByName("volume-space-attributes");
                        if (volSpaceAttrs != null) {
                            for (NaElement info : (List<NaElement>) volSpaceAttrs.getChildren()) {
                                String name = info.getName();
                                if (attrs == null || attrs.contains(name)) {
                                    infos.put(name, info.getContent());
                                }
                            }
                        }
                        NaElement volStateAttrs = volInfo.getChildByName("volume-state-attributes");
                        if (volStateAttrs != null) {
                            for (NaElement info : (List<NaElement>) volStateAttrs.getChildren()) {
                                String name = info.getName();
                                if (attrs == null || attrs.contains(name)) {
                                    infos.put(name, info.getContent());
                                }
                            }
                        }
                        volumes.add(infos);
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("volume-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to get list of Volumes.";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }

        return volumes;
    }

    /**
     * Creates a new snapshot of the volume.
     * 
     * @param snapshotName - Required. Name of the snapshot.
     * @param async - Optional. Default is false. True indicates created asynchronously.
     * @return - true/false if operation succeeded.
     */
    boolean createSnapshot(String snapshotName, boolean async)
    {
        NaElement elem = new NaElement("snapshot-create");
        elem.addNewChild("volume", name);
        elem.addNewChild("snapshot", snapshotName);
        elem.addNewChild("async", Boolean.toString(async));

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create snapshot on volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
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
            throw new NetAppCException(msg, e);
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
            throw new NetAppCException(msg, e);
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
        NaElement elem = new NaElement("snapshot-get-iter");
        NaElement resultElem = null;
        String tag = null;

        NaElement volumeElem = new NaElement("volume-get-iter");
        if (name != null && !name.isEmpty()) {
            NaElement volumeIdAttrs = new NaElement("volume-id-attributes");
            volumeIdAttrs.addNewChild("name", name);
            NaElement volumeAttrs = new NaElement("volume-attributes");
            volumeAttrs.addChildElem(volumeIdAttrs);
            NaElement query = new NaElement("query");
            query.addChildElem(volumeAttrs);
            volumeElem.addChildElem(query);
        }
        try {
            do {
                NaElement result = server.invokeElem(volumeElem);
                tag = result.getChildContent("next-tag");
                resultElem = result.getChildByName("attributes-list");
                if (resultElem != null) {
                    for (NaElement volInfo : (List<NaElement>) resultElem.getChildren()) {
                        NaElement volAttrs = volInfo.getChildByName("volume-id-attributes");
                        if (volAttrs != null) {
                            String volumeName = volAttrs.getChildContent("name");
                            if (volumeName.equalsIgnoreCase(name)) {
                                NaElement snapshotInfo = new NaElement("snapshot-info");
                                snapshotInfo.addNewChild("volume-provenance-uuid", volAttrs.getChildContent("provenance-uuid"));
                                NaElement query = new NaElement("query");
                                query.addChildElem(snapshotInfo);
                                elem.addChildElem(query);
                                break;
                            }
                        }
                    }
                }
                if (tag != null && !tag.isEmpty()) {
                    volumeElem = new NaElement("volume-get-iter");
                    volumeElem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to list volumes: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }

        ArrayList<String> snaps = new ArrayList<String>();
        try {
            do {
                resultElem = server.invokeElem(elem);
                tag = resultElem.getChildContent("next-tag");
                if (resultElem != null) {
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
                }
                if (tag != null && !tag.isEmpty()) {
                    elem = new NaElement("snapshot-get-iter");
                    elem.addNewChild("tag", tag);
                }
            } while (tag != null && !tag.isEmpty());
        } catch (Exception e) {
            String msg = "Failed to list snapshot for volume: " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return snaps;
    }
}
