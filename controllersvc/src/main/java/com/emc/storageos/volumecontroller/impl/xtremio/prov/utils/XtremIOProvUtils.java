/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.xtremio.prov.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.XtremIOConstants.XTREMIO_ENTITY_TYPE;
import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiatorGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMap;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOLunMapFull;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOSystem;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOTag;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumes;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumesFull;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;

public class XtremIOProvUtils {

    private static final Logger _log = LoggerFactory.getLogger(XtremIOProvUtils.class);

    private static final int SLEEP_TIME = 10000; // 10 seconds

    private static final String DOT_OPERATOR = "\\.";
    private static final Integer XIO_MIN_4X_VERSION = 4;
    private static final Integer XIO_4_0_2_VERSION = 402;

    private static final Set<String> SUPPORTED_HOST_OS_SET = new HashSet<String>();

    private XtremIOProvUtils() {
    };

    static {
        SUPPORTED_HOST_OS_SET.add(Host.HostType.Windows.name());
        SUPPORTED_HOST_OS_SET.add(Host.HostType.Linux.name());
        SUPPORTED_HOST_OS_SET.add(Host.HostType.AIX.name());
        SUPPORTED_HOST_OS_SET.add(Host.HostType.Esx.name());
        SUPPORTED_HOST_OS_SET.add(Host.HostType.HPUX.name());
        SUPPORTED_HOST_OS_SET.add(Host.HostType.Other.name());
    }

    public static void updateStoragePoolCapacity(XtremIOClient client, DbClient dbClient, StoragePool storagePool) {
        try {
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storagePool.getStorageDevice());
            _log.info(String.format("Old storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
            List<XtremIOSystem> systems = client.getXtremIOSystemInfo();
            for (XtremIOSystem system : systems) {
                if (system.getSerialNumber().equalsIgnoreCase(storageSystem.getSerialNumber())) {
                    storagePool.setFreeCapacity(system.getTotalCapacity() - system.getUsedCapacity());
                    storagePool.setSubscribedCapacity(system.getSubscribedCapacity());
                    dbClient.updateObject(storagePool);
                    break;
                }
            }
            _log.info(String.format("New storage pool capacity data for %n  pool %s/%s --- %n  free capacity: %s; subscribed capacity: %s",
                    storageSystem.getId(), storagePool.getId(),
                    storagePool.calculateFreeCapacityWithoutReservations(),
                    storagePool.getSubscribedCapacity()));
        } catch (Exception e) {
            _log.warn("Problem when updating pool capacity for pool {}", storagePool.getNativeGuid());
        }
    }

    /**
     * Gets the storage pool for the given storage system.
     *
     * @param systemId
     *            the system id
     * @param dbClient
     *            the db client
     * @return the xtremio storage pool
     */
    public static StoragePool getXtremIOStoragePool(URI systemId, DbClient dbClient) {
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePoolConstraint(systemId),
                storagePoolURIs);
        Iterator<URI> poolsItr = storagePoolURIs.iterator();
        while (poolsItr.hasNext()) {
            URI storagePoolURI = poolsItr.next();
            StoragePool pool = dbClient.queryObject(StoragePool.class, storagePoolURI);
            if (pool != null && !pool.getInactive()) {
                return pool;
            }
        }
        return null;
    }

    /**
     * Check if there is a volume with the given name
     * If found, return the volume
     *
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO volume if found else null
     */
    public static XtremIOVolume isVolumeAvailableInArray(XtremIOClient client, String label, String clusterName) throws Exception {
        XtremIOVolume volume = null;
        try {
            volume = client.getVolumeDetails(label, clusterName);
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                throw e;
            } else {
                _log.warn("Volume {} not found on cluster {}", label, clusterName);
            }
        }
        return volume;
    }

    /**
     * Check if there is a snapshot with the given name
     * If found, return the snapshot
     *
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO snapshot if found else null
     */
    public static XtremIOVolume isSnapAvailableInArray(XtremIOClient client, String label, String clusterName) throws Exception {
        XtremIOVolume volume = null;
        try {
            volume = client.getSnapShotDetails(label, clusterName);
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                throw e;
            } else {
                _log.info("Snapshot {} not available in Array.", label);
            }
        }
        return volume;
    }

    /**
     * Check if there is a consistency group with the given name
     * If found, return the consistency group
     *
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO consistency group if found else null
     */
    public static XtremIOConsistencyGroup isCGAvailableInArray(XtremIOClient client, String label, String clusterName) throws Exception {
        XtremIOConsistencyGroup cg = null;
        try {
            cg = client.getConsistencyGroupDetails(label, clusterName);
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                throw e;
            } else {
                _log.info("Consistency group {} not available in Array.", label);
            }
        }

        return cg;
    }

    /**
     * Check if there is a tag with the given name
     * If found, return the tag
     *
     * @param client
     * @param tagName
     * @param tagEntityType
     * @param clusterName
     * @return XtrmIO tag if found else null
     */
    public static XtremIOTag isTagAvailableInArray(XtremIOClient client, String tagName, String tagEntityType, String clusterName)
            throws Exception {
        XtremIOTag tag = null;

        try {
            tag = client.getTagDetails(tagName, tagEntityType, clusterName);
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                throw e;
            } else {
                _log.info("Tag {} not available in Array.", tagName);
            }
        }

        return tag;
    }

    /**
     * Check if there is a snapset with the given name
     * If found, return the snapset
     *
     * @param client
     * @param label
     * @param clusterName
     * @return XtremIO snapset if found else null
     */
    public static XtremIOConsistencyGroup isSnapsetAvailableInArray(XtremIOClient client, String label, String clusterName)
            throws Exception {
        XtremIOConsistencyGroup cg = null;
        try {
            cg = client.getSnapshotSetDetails(label, clusterName);
        } catch (Exception e) {
            if (null != e.getMessage() && !e.getMessage().contains(XtremIOConstants.OBJECT_NOT_FOUND)) {
                throw e;
            } else {
                _log.info("Snapshot Set {} not available in Array.", label);
            }
        }

        return cg;
    }

    /**
     * Checks if there is folder with the given name and sub folders for volume and snapshots.
     * If not found, create them.
     *
     * @param client
     * @param rootVolumeFolderName
     * @return map of volume folder name and snapshot folder name
     * @throws Exception
     */
    public static Map<String, String> createFoldersForVolumeAndSnaps(XtremIOClient client, String rootVolumeFolderName)
            throws Exception {

        List<String> folderNames = client.getVolumeFolderNames();
        _log.info("Volume folder Names found on Array : {}", Joiner.on("; ").join(folderNames));
        Map<String, String> folderNamesMap = new HashMap<String, String>();
        String rootFolderName = XtremIOConstants.V1_ROOT_FOLDER.concat(rootVolumeFolderName);
        _log.info("rootVolumeFolderName: {}", rootFolderName);
        String volumesFolderName = rootFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
        String snapshotsFolderName = rootFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
        folderNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesFolderName);
        folderNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsFolderName);
        if (!folderNames.contains(rootFolderName)) {
            _log.info("Sending create root folder request {}", rootFolderName);
            client.createTag(rootVolumeFolderName, "/", XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        } else {
            _log.info("Found {} folder on the Array.", rootFolderName);
        }
        long waitTime = 30000; // 30 sec
        int count = 0;
        // @TODO this is a temporary workaround to retry volume folder verification.
        // Actually we should create workflow steps for this.
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the volume folders", count);
            if (!folderNames.contains(volumesFolderName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                folderNames = client.getVolumeFolderNames();
            } else {
                _log.info("Found {} folder on the Array.", volumesFolderName);
                break;
            }
        }
        if (!folderNames.contains(volumesFolderName)) {
            _log.info("Sending create volume folder request {}", volumesFolderName);
            client.createTag("volumes", rootFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        }

        if (!folderNames.contains(snapshotsFolderName)) {
            _log.info("Sending create snapshot folder request {}", snapshotsFolderName);
            client.createTag("snapshots", rootFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), null);
        } else {
            _log.info("Found {} folder on the Array.", snapshotsFolderName);
        }

        return folderNamesMap;
    }

    /**
     * Checks if there are tags with the given name for volume and snapshots.
     * If not found, create them.
     *
     * @param client
     * @param rootTagName
     * @param clusterName
     * @return map of volume tag name and snapshot tag name
     * @throws Exception
     */
    public static Map<String, String> createTagsForVolumeAndSnaps(XtremIOClient client, String rootTagName, String clusterName)
            throws Exception {
        List<String> tagNames = client.getTagNames(clusterName);
        _log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        Map<String, String> tagNamesMap = new HashMap<String, String>();
        String volumesTagName = XtremIOConstants.V2_VOLUME_ROOT_FOLDER.concat(rootTagName);
        String snapshotsTagName = XtremIOConstants.V2_SNAPSHOT_ROOT_FOLDER.concat(rootTagName);
        tagNamesMap.put(XtremIOConstants.VOLUME_KEY, volumesTagName);
        tagNamesMap.put(XtremIOConstants.SNAPSHOT_KEY, snapshotsTagName);
        long waitTime = 30000; // 30 sec
        int count = 0;
        // @TODO this is a temporary workaround to retry volume tag verification.
        // Actually we should create workflow steps for this.
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the volume tag", count);
            if (!tagNames.contains(volumesTagName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                tagNames = client.getTagNames(clusterName);
            } else {
                _log.info("Found {} tag on the Array.", volumesTagName);
                break;
            }

        }
        if (!tagNames.contains(volumesTagName)) {
            _log.info("Sending create volume tag request {}", volumesTagName);
            client.createTag(volumesTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), clusterName);
        }

        if (!tagNames.contains(snapshotsTagName)) {
            _log.info("Sending create snapshot tag request {}", snapshotsTagName);
            client.createTag(snapshotsTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.SnapshotSet.name(), clusterName);
        } else {
            _log.info("Found {} tag on the Array.", snapshotsTagName);
        }

        return tagNamesMap;

    }

    /**
     * Checks if there are tags with the given name for consistency group.
     * If not found, create them.
     *
     * @param client
     * @param rootTagName
     * @param clusterName
     * @return string
     * @throws Exception
     */
    public static String createTagsForConsistencyGroup(XtremIOClient client, String rootTagName, String clusterName)
            throws Exception {
        List<String> tagNames = client.getTagNames(clusterName);
        _log.info("Tag Names found on Array : {}", Joiner.on("; ").join(tagNames));
        String cgTagName = XtremIOConstants.V2_CONSISTENCY_GROUP_ROOT_FOLDER.concat(rootTagName);

        long waitTime = 30000; // 30 sec
        int count = 0;
        while (waitTime > 0) {
            count++;
            _log.debug("Retrying {} time to find the cg tag", count);
            if (!tagNames.contains(cgTagName)) {
                _log.debug("sleeping time {} remaining time: {}", SLEEP_TIME, (waitTime - SLEEP_TIME));
                Thread.sleep(SLEEP_TIME);
                waitTime = waitTime - SLEEP_TIME;
                tagNames = client.getTagNames(clusterName);
            } else {
                _log.info("Found cg tag: {} on the Array.", cgTagName);
                break;
            }

        }
        if (!tagNames.contains(cgTagName)) {
            _log.info("Sending create cg tag request {}", cgTagName);
            client.createTag(cgTagName, null, XtremIOConstants.XTREMIO_ENTITY_TYPE.ConsistencyGroup.name(), clusterName);
        } else {
            _log.info("Found {} cg tag on the Array.", cgTagName);
        }

        return cgTagName;

    }

    /**
     * Check the number of volumes under the tag/volume folder.
     * If zero, delete the tag/folder
     *
     * @param client
     * @param xioClusterName
     * @param volumeFolderName
     * @param storageSystem
     * @throws Exception
     */
    public static void cleanupVolumeFoldersIfNeeded(XtremIOClient client, String xioClusterName, String volumeFolderName,
            StorageSystem storageSystem) throws Exception {
        try {
            boolean isVersion2 = client.isVersion2();
            // Find the # volumes in folder, if the Volume folder is empty,
            // then delete the folder too
            XtremIOTag tag = client.getTagDetails(volumeFolderName, XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
            if (tag == null) {
                _log.info("Tag {} not found on the array", volumeFolderName);
                return;
            }
            _log.info("Got back tag details {}", tag.toString());
            String numOfVols = isVersion2 ? tag.getNumberOfDirectObjs() : tag.getNumberOfVolumes();
            int numberOfVolumes = Integer.parseInt(numOfVols);
            if (numberOfVolumes == 0) {
                if (isVersion2) {
                    client.deleteTag(volumeFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                } else {
                    String volumesFolderName = volumeFolderName.concat(XtremIOConstants.VOLUMES_SUBFOLDER);
                    String snapshotsFolderName = volumeFolderName.concat(XtremIOConstants.SNAPSHOTS_SUBFOLDER);
                    _log.info("Deleting Volumes Folder ...");
                    client.deleteTag(volumesFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                    _log.info("Deleting Snapshots Folder ...");
                    client.deleteTag(snapshotsFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                    _log.info("Deleting Root Folder ...");
                    client.deleteTag(volumeFolderName, XtremIOConstants.XTREMIO_ENTITY_TYPE.Volume.name(), xioClusterName);
                }
            }
        } catch (Exception e) {
            _log.warn("Deleting root folder {} failed", volumeFolderName, e);
        }
    }

    /**
     * Get the XtremIO client for making requests to the system based
     * on the passed profile.
     *
     * @param dbClient
     *            the db client
     * @param system
     *            the system
     * @param xtremioRestClientFactory
     *            xtremioclientFactory.
     * @return A reference to the xtremio client.
     */
    public static XtremIOClient getXtremIOClient(DbClient dbClient, StorageSystem system, XtremIOClientFactory xtremioRestClientFactory) {
        if (null == system.getSmisProviderIP() || null == system.getSmisPortNumber()) {
            _log.error("There is no active XtremIO Provider managing the system {}.", system.getSerialNumber());
            throw XtremIOApiException.exceptions.noMgmtConnectionFound(system.getSerialNumber());
        }
        XtremIOClient client = (XtremIOClient) xtremioRestClientFactory
                .getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(system.getSmisProviderIP(),
                                system.getSmisPortNumber())),
                        system.getSmisUserName(), system.getSmisPassword(), getXtremIOVersion(dbClient, system), true);
        return client;
    }

    /**
     * Gets the XtrmeIO model.
     *
     * @param dbClient
     *            the db client
     * @param system
     *            the system
     * @return the XtrmeIO model
     */
    public static String getXtremIOVersion(DbClient dbClient, StorageSystem system) {
        String version = system.getFirmwareVersion();
        // get model info from storage provider as it will have the latest model updated after scan process
        if (!NullColumnValueGetter.isNullURI(system.getActiveProviderURI())) {
            StorageProvider provider = dbClient.queryObject(StorageProvider.class, system.getActiveProviderURI());
            version = provider.getVersionString();
        }
        return version;
    }

    /**
     * Refresh the XIO Providers & its client connections.
     *
     * @param xioProviderList
     *            the XIO provider list
     * @param dbClient
     *            the db client
     * @return the list of active providers
     */
    public static List<URI> refreshXtremeIOConnections(final List<StorageProvider> xioProviderList,
            DbClient dbClient, XtremIOClientFactory xtremioRestClientFactory) {
        List<URI> activeProviders = new ArrayList<URI>();
        for (StorageProvider provider : xioProviderList) {
            try {
                // For providers without version/model, let it try connecting V1 client to update connection status
                XtremIOClient xioClient = (XtremIOClient) xtremioRestClientFactory.getRESTClient(
                        URI.create(XtremIOConstants.getXIOBaseURI(provider.getIPAddress(), provider.getPortNumber())),
                        provider.getUserName(), provider.getPassword(), provider.getVersionString(), true);
                if (null != xioClient.getXtremIOXMSVersion()) {
                    // Now update provider status based on connection live check.
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.CONNECTED
                            .toString());
                    activeProviders.add(provider.getId());
                } else {
                    _log.info("XIO Connection is not active {}", provider.getProviderID());
                    provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                            .toString());
                }
            } catch (Exception ex) {
                _log.error("Exception occurred while validating XIO client for {}", provider.getProviderID(), ex);
                provider.setConnectionStatus(StorageProvider.ConnectionStatus.NOTCONNECTED
                        .toString());
            } finally {
                dbClient.updateObject(provider);
            }
        }
        return activeProviders;
    }

    public static boolean is4xXtremIOModel(String model) {
        return (NullColumnValueGetter.isNotNullValue(model) && Integer.valueOf(model.split(DOT_OPERATOR)[0]) >= XIO_MIN_4X_VERSION);
    }

    /**
     * Check if the version is greater than or equal to 4.0.2
     *
     * @param version
     *            XIO storage system firmware version
     * @return true if the version is 4.0.2 or greater
     */
    public static boolean isXtremIOVersion402OrGreater(String version) {
        // the version will be in the format: 4.0.2-80_ndu. Extract the third number between dot and dash
        // and verify that the numerical value is greater than 2
        Pattern pattern = Pattern.compile("([0-9]*?\\.[0-9]*?)\\.([0-9]*?)-");
        Matcher matcher = pattern.matcher(version);
        while (matcher.find()) {
            float xioVersion = Float.parseFloat(matcher.group(1));
            boolean isVersion4 = xioVersion == 4.0;
            boolean isVersionGreater = xioVersion > 4.0;
            return isVersionGreater || (isVersion4 && (Integer.valueOf(matcher.group(2)) >= 2));
        }

        return false;
    }

	public static boolean isBulkAPISupported(String firmwareVersion,XtremIOClient client) throws Exception {
        String xmsVersion =client.getXtremIOXMSVersion();
		return xmsVersion.compareTo(XtremIOConstants.XTREMIO_BULK_XMS_MINVERSION) >=0 && firmwareVersion.compareTo(XtremIOConstants.XTREMIO_BULK_API_MINVERSION) >= 0;
        
    }

    /**
     * Returns the XtremIO supported OS based on the initiator Host OS type.
     *
     * From API Doc: solaris, aix, windows, esx, other, linux, hpux
     *
     * @param hostURI
     *            - Host URI of the Initiator.
     * @return operatingSystem type.
     */
    public static String getInitiatorHostOS(Host host) {
        String osType = null;
        if (SUPPORTED_HOST_OS_SET.contains(host.getType())) {
            osType = host.getType().toLowerCase();
        }
        return osType;
    }

    /**
     *
     * @param igName
     * @param clusterName
     * @param client
     * @return
     * @throws Exception
     */
    public static List<XtremIOVolume> getInitiatorGroupVolumes(String igName, String clusterName, XtremIOClient client) throws Exception {
        List<XtremIOVolume> igVolumes = new ArrayList<XtremIOVolume>();
        List<XtremIOObjectInfo> igLunMaps = new ArrayList<XtremIOObjectInfo>();
        if (client.isVersion2()) {
            igLunMaps = client.getLunMapsForInitiatorGroup(igName, clusterName);
        } else {
            XtremIOInitiatorGroup ig = client.getInitiatorGroup(igName, clusterName);
            if (ig == null) {
                return igVolumes;
            }
            List<XtremIOObjectInfo> lunMaps = client.getLunMaps(clusterName);
            String igIndex = ig.getIndex();
            for (XtremIOObjectInfo lunMap : lunMaps) {
                String[] lunInfo = lunMap.getName().split(XtremIOConstants.UNDERSCORE);
                if (igIndex.equals(lunInfo[1])) {
                    igLunMaps.add(lunMap);
                }
            }
        }

        for (XtremIOObjectInfo igLunMap : igLunMaps) {
            String[] igLunInfo = igLunMap.getName().split(XtremIOConstants.UNDERSCORE);
            igVolumes.add(client.getVolumeByIndex(igLunInfo[0], clusterName));
        }
        return igVolumes;
    }


	public static Map<String, List<XtremIOVolume>> getLunMapAndVolumes(Set<String> igNameSet, String clusterName,
            XtremIOClient client, Map<String, List<XtremIOVolume>> igNameToVolMap) throws Exception {
        List<XtremIOLunMapFull> igLunMaps = new ArrayList<>();
        long starttime =0l;
        starttime=System.nanoTime();
        igLunMaps = client.getLunMapsForAllInitiatorGroups(igNameSet, clusterName);
        _log.debug("Time taken for All Lun API Call : " + "total time = "
                + String.format("%2.6f", (System.nanoTime() - starttime) / 1000000000.0) + " seconds"  );
        HashMap<String, List<String>> map = new HashMap<>();
        HashMap<String, XtremIOVolume> indexInfoMap = new HashMap<>();

        StringBuilder volumeURL = new StringBuilder();
        String indexFilter = "index:eq:";
        int indexSize =0;
       long starttime1 = System.nanoTime();
        for (XtremIOLunMapFull lunMapFull : igLunMaps) {
            for (XtremIOLunMap lunmap : lunMapFull.getContent()) {
                if (map.containsKey(lunmap.getIgName())) {
                    map.get(lunmap.getIgName()).add(lunmap.getVolumeIndex());
                    map.put(lunmap.getIgName(), map.get(lunmap.getIgName()));
                    volumeURL = volumeURL.append(indexFilter).append(lunmap.getVolumeIndex()).append(",");
                    indexSize++;
                  } else {
                    List<String> volumeIndexList = new ArrayList<>();
                    volumeIndexList.add(lunmap.getVolumeIndex());
                    map.put(lunmap.getIgName(), volumeIndexList);
                   volumeURL = volumeURL.append(indexFilter).append(lunmap.getVolumeIndex()).append(",");
                   indexSize++;
                }

                if (indexSize >= XtremIOConstants.XTREMIO_MAX_Filters) {
                     starttime = System.nanoTime();
                    XtremIOVolumesFull volumes = client.getVolumesForAllInitiatorGroups(clusterName, volumeURL);
                    _log.debug("Time taken for Volume API Call : " + "total time = "
                            + String.format("%2.6f", (System.nanoTime() - starttime) / 1000000000.0) + " seconds , numFilters = " + indexSize );
                    for (XtremIOVolume volume : volumes.getContent()) {
                        indexInfoMap.put(volume.getVolInfo().get(2), volume);
                    }
                    volumeURL = new StringBuilder();
                    indexSize = 0;
                }

            }
        }
        starttime = System.nanoTime();
        XtremIOVolumesFull volumes = client.getVolumesForAllInitiatorGroups(clusterName, volumeURL);
        _log.debug("Time taken for Volume API Call : " + "total time = "
                + String.format("%2.6f", (System.nanoTime() - starttime) / 1000000000.0) + " seconds , numFilters = " + indexSize );
        _log.debug("Time taken for All Volume API Call : " + "total time = "
                + String.format("%2.6f", (System.nanoTime() - starttime1) / 1000000000.0) + " seconds ");
        for (XtremIOVolume volume : volumes.getContent()) {
            indexInfoMap.put(volume.getVolInfo().get(2), volume);
        }

        for (Map.Entry<String, List<String>> entry1 : map.entrySet()) {
            List<XtremIOVolume> discoveredVolumes = new ArrayList<>();
            for (Map.Entry<String, XtremIOVolume> entry2 : indexInfoMap.entrySet()) {
                if (entry1.getValue().contains(entry2.getKey())) {

                    discoveredVolumes.add(entry2.getValue());
                }

            }
            igNameToVolMap.put(entry1.getKey(), discoveredVolumes);
        }

        return igNameToVolMap;
    }


    /**
     * Gets the lun maps for the initiator group.
     *
     * @param igName
     *            the ig name
     * @param clusterName
     *            the cluster name
     * @param client
     *            the xtremio client
     * @return the initiator group lun maps
     * @throws Exception
     */
    public static List<XtremIOObjectInfo> getInitiatorGroupLunMaps(String igName, String clusterName, XtremIOClient client)
            throws Exception {
        List<XtremIOObjectInfo> igLunMaps = new ArrayList<XtremIOObjectInfo>();
        if (client.isVersion2()) {
            igLunMaps = client.getLunMapsForInitiatorGroup(igName, clusterName);
        } else {
            XtremIOInitiatorGroup ig = client.getInitiatorGroup(igName, clusterName);
            if (ig == null) {
                return igLunMaps;
            }
            List<XtremIOObjectInfo> lunMaps = client.getLunMaps(clusterName);
            String igIndex = ig.getIndex();
            for (XtremIOObjectInfo lunMap : lunMaps) {
                String[] lunInfo = lunMap.getName().split(XtremIOConstants.UNDERSCORE);
                if (igIndex.equals(lunInfo[1])) {
                    igLunMaps.add(lunMap);
                }
            }
        }
        return igLunMaps;
    }

    /**
     *
     * @param storageSerialNumber
     * @param initiators
     * @param initiatorsNotFound
     * @param xioClusterName
     * @param client
     * @return
     * @throws Exception
     */
    public static ArrayListMultimap<String, Initiator> mapInitiatorToInitiatorGroup(String storageSerialNumber,
            Collection<Initiator> initiators, List<Initiator> initiatorsNotFound, String xioClusterName, XtremIOClient client)
            throws Exception {
        ArrayListMultimap<String, Initiator> initiatorToIGMap = ArrayListMultimap.create();
        for (Initiator initiator : initiators) {
            String igName = getIGNameForInitiator(initiator, storageSerialNumber, client, xioClusterName);
            if (igName == null || igName.isEmpty()) {
                _log.info("initiator {} - no IG found.", initiator.getLabel(), igName);
                if (initiatorsNotFound != null) {
                    initiatorsNotFound.add(initiator);
                }
            } else {
                initiatorToIGMap.put(igName, initiator);
            }
        }
        _log.info("Found {} existing IGs: {} after running selection process",
                initiatorToIGMap.size(), Joiner.on(",").join(initiatorToIGMap.asMap().entrySet()));
        return initiatorToIGMap;
    }

    /**
     *
     * @param initiator
     * @param storageSerialNumber
     * @param client
     * @param xioClusterName
     * @return
     * @throws Exception
     */
    public static String getIGNameForInitiator(Initiator initiator, String storageSerialNumber, XtremIOClient client, String xioClusterName)
            throws Exception {
        String igName = null;
        String initiatorName = initiator.getMappedInitiatorName(storageSerialNumber);
        if (null != initiatorName) {
            // Get initiator by Name and find IG Group
            XtremIOInitiator initiatorObj = client.getInitiator(initiatorName, xioClusterName);
            if (null != initiatorObj) {
                igName = initiatorObj.getInitiatorGroup().get(1);
            }
        }

        return igName;
    }
}
