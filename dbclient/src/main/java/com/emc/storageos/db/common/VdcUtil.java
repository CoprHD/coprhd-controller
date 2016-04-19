/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VdcVersion;
import com.emc.storageos.db.client.model.GeoVisibleResource;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.util.KeyspaceUtil;

/**
 * Utility class for determining the id of the local VDC,
 * and for performing related operations
 */
public class VdcUtil {
    private static final Logger log = LoggerFactory.getLogger(VdcUtil.class);
    private static DbClient dbClient;
    private static volatile VirtualDataCenter localVdc;
    /**
     * the short id of first vdc in the geo-federation
     * Any object URL with a missing vdc id will be assumed to belong to this vdc
     * 
     * If this vdc was upgraded from a pre-geo-fedration supported ViPR version,
     * there will be object URL's missing the vdc id. This is the only vdc where it's
     * possible to have missing vdc ids in object URL's because all other vdc's added
     * to the federation are required to have no local resources.
     */
    private static final String FIRST_VDC_ID = "vdc1";
    /**
     * Cache the short VDC id to VDC URN mapping for performance
     */
    private static final Map<String, URI> vdcIdMap = new HashMap<String, URI>();
    private static volatile boolean rebuildVdcIdMap = true;

    private VdcUtil() {
        // no instances
    }

    public static void setDbClient(DbClient dbclient) {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // only called once when spring initialization, so it's safe to ignore sonar violation
        dbClient = dbclient; // NOSONAR (squid:S2444)
    }

    public static String getFirstVdcId() {
        return FIRST_VDC_ID;
    }

    public static String getLocalShortVdcId() {
        buildUrnMap();
        if (localVdc == null) {
            return FIRST_VDC_ID;
        }
        return localVdc.getShortId();
    }

    public static VirtualDataCenter getLocalVdc() {
        return dbClient.queryObject(VirtualDataCenter.class, getVdcUrn(getLocalShortVdcId()));
    }

    /**
     * Determine if an object is "remote" to this VDC, meaning it is
     * geo-visible and originated in the DB on a remote VDC
     * 
     * @param o the object to test
     * @return true if the object originated remotely
     */
    public static boolean isRemoteObject(DataObject o) {

        if ((o instanceof GeoVisibleResource) == false) {
            return false;
        }

        buildUrnMap();
        if (localVdc == null) {
            throw new IllegalStateException("No local VirtualDataCenter object found");
        }

        String objectVdc = URIUtil.parseVdcIdFromURI(o.getId());
        objectVdc = StringUtils.isNotBlank(objectVdc) ? objectVdc : FIRST_VDC_ID;
        return !localVdc.getShortId().toString().equals(objectVdc);
    }

    public static URI getVdcUrn(String shortVdcId) {
        buildUrnMap();
        if (localVdc == null) {
            throw new IllegalStateException("No local VirtualDataCenter object found");
        }
        synchronized (vdcIdMap) {
            return vdcIdMap.get(shortVdcId);
        }
    }

    public static void invalidateVdcUrnCache() {
        rebuildVdcIdMap = true;
    }

    public static URI getVdcId(Class<? extends DataObject> clazz, URI uri) {
        if (KeyspaceUtil.isGlobal(clazz)) {
            return URI.create(VdcUtil.getLocalShortVdcId());
        }
        String vdcFromUri = URIUtil.parseVdcIdFromURI(uri);
        vdcFromUri = StringUtils.isNotBlank(vdcFromUri) ? vdcFromUri : FIRST_VDC_ID;
        return URI.create(vdcFromUri);
    }

    /**
     * if there is any active vdc which is a remote vdc, return false.
     * 
     * @return
     */
    public static boolean isLocalVdcSingleSite() {

        List<URI> ids = dbClient.queryByType(VirtualDataCenter.class, true);
        for (URI vdcId : ids) {
            VirtualDataCenter vdc = dbClient.queryObject(VirtualDataCenter.class, vdcId);
            if (!vdc.getLocal()) {
                if ((vdc.getConnectionStatus() == VirtualDataCenter.ConnectionStatus.ISOLATED)
                        || vdc.getRepStatus() == VirtualDataCenter.GeoReplicationStatus.REP_NONE) {
                    continue; // failed to add the remote vdc
                }

                return false;
            }
        }

        return true;
    }

    private static void buildUrnMap() {
        if (rebuildVdcIdMap) {
            // When running unit test, prevents NPEs when creating URIs
            if (dbClient == null) {
                return;
            }
            synchronized (vdcIdMap) {
                if (rebuildVdcIdMap) {
                    log.info("Rebuilding the vdcIdMap from the database");
                    List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
                    Iterator<VirtualDataCenter> vdcIter = dbClient.queryIterativeObjects(VirtualDataCenter.class, vdcIds);
                    localVdc = null;
                    vdcIdMap.clear();
                    while (vdcIter.hasNext()) {
                        VirtualDataCenter vdc = vdcIter.next();
                        vdcIdMap.put(vdc.getShortId(), vdc.getId());
                        if (Boolean.TRUE.equals(vdc.getLocal())) {
                            localVdc = vdc;
                        }
                    }
                    if (!vdcIdMap.isEmpty()) {
                        rebuildVdcIdMap = false;
                    }
                }
            }
        }
    }

    public static String getMinimalVdcVersion() {
        List<URI> vdcIds = getVdcIds();
        List<URI> geoVerIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> geoVersions = dbClient.queryObject(VdcVersion.class, geoVerIds);

        if (!hasAnyGeoVersion(geoVersions)) {
            log.info("GeoVersion doesn't exist, return default version");
            return DbConfigConstants.DEFAULT_VDC_DB_VERSION;
        }

        if (missVersionFor(vdcIds, geoVersions)) {
            log.info("GeoVersion not exist for vdcs, return default version");
            return DbConfigConstants.DEFAULT_VDC_DB_VERSION;
        }

        String minimalVersion = null;
        for (VdcVersion geoVersion : geoVersions) {
            if ((minimalVersion == null) || (VdcVersionComparator.compare(minimalVersion, geoVersion.getVersion()) > 0)) {
                minimalVersion = geoVersion.getVersion();
            }
        }
        log.info("minimal Geo version {}", minimalVersion);
        return minimalVersion;
    }
    
    /**
     * Check if geo version of all other vdcs(excluding local vdc) are equal to or higher than target version
     *  
     * @param targetVersion
     * @return
     */
    public static boolean checkGeoCompatibleOfOtherVdcs(String targetVersion) {
        URI localVdcId = getLocalVdc().getId();
        List<URI> geoVerIds = dbClient.queryByType(VdcVersion.class, true);
        List<VdcVersion> geoVersions = dbClient.queryObject(VdcVersion.class, geoVerIds);
        for (VdcVersion geoVersion : geoVersions) {
            URI vdcId = geoVersion.getVdcId();
            if (vdcId.equals(localVdcId)) {
                continue; // skip current vdc
            }
            if (VdcVersionComparator.compare(geoVersion.getVersion(), targetVersion) < 0) {
                log.info("Vdc {} version is less than {}", new Object[]{vdcId, targetVersion});
                return false;
            }
        }
        return true;
    }
    
    private static boolean hasAnyGeoVersion(List<VdcVersion> geoVersions) {
        return geoVersions != null && geoVersions.iterator().hasNext();
    }

    private static boolean missVersionFor(final List<URI> vdcIds, final List<VdcVersion> geoVersions) {
        List<URI> geoVerVdcIds = new ArrayList<URI>();
        for (VdcVersion geoVersion : geoVersions) {
            geoVerVdcIds.add(geoVersion.getVdcId());
        }
        return !geoVerVdcIds.containsAll(vdcIds);
    }

    private static List<URI> getVdcIds() {
        List<URI> vdcIds = dbClient.queryByType(VirtualDataCenter.class, true);
        if (vdcIds == null || !vdcIds.iterator().hasNext()) {
            return new ArrayList<URI>();
        }
        return vdcIds;
    }

    public static class VdcVersionComparator {
        public static int compare(final String version1, final String version2) {
            if (version1.equals(version2)) {
                return 0;
            }
            String[] parts1 = StringUtils.split(version1, DbConfigConstants.VERSION_PART_SEPERATOR);
            String[] parts2 = StringUtils.split(version2, DbConfigConstants.VERSION_PART_SEPERATOR);

            int index = 0;
            while (index < parts1.length && index < parts2.length) {
                String part1 = parts1[index];
                String part2 = parts2[index];
                int result = 0;
                if (StringUtils.isNumeric(part1) && StringUtils.isNumeric(part2)) {
                    result = (Integer.valueOf(part1).compareTo(Integer.valueOf(part2)));
                } else {
                    result = part1.compareToIgnoreCase(part2);

                }

                if (result != 0) {
                    return result;
                }
                index++;
            }

            return parts1.length > parts2.length ? 1 : (parts1.length == parts2.length ? 0 : -1);
        }
    }

    public static String getDbSchemaVersion(String softwareVersion) {
        if (StringUtils.isBlank(softwareVersion)
                || !softwareVersion.contains(ProductName.getName())) {
            log.error("Unrecognized software version {}", softwareVersion);
            return null;
        }
        String prefix = ProductName.getName() + "-";
        String versionNumber = softwareVersion.substring(prefix.length());
        String numbers[] = versionNumber.split("\\.");
        if (numbers.length > 2) {
            return numbers[0] + "." + numbers[1];
        }
        log.error("Unrecognized software version {}", softwareVersion);
        // Unexpected software version number
        return null;
    }

}