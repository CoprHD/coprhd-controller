/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StorageSystemType.META_TYPE;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.services.util.PlatformUtils;
import com.emc.storageos.storagedriver.storagecapabilities.StorageProfile;

public class StorageSystemTypesInitUtils {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypesInitUtils.class);

    private static final String ISILON = "isilon";
    private static final String VNX_BLOCK = "vnxblock";
    private static final String VNXe = "vnxe";
    private static final String VNX_FILE = "vnxfile";
    private static final String VMAX = "vmax";
    private static final String SMIS = "smis";
    private static final String NETAPP = "netapp";
    private static final String NETAPPC = "netappc";
    private static final String HITACHI = "hds";
    private static final String HITACHI_PROVIDER = "hicommand";
    private static final String IBMXIV = "ibmxiv";
    private static final String VPLEX = "vplex";
    private static final String OPENSTACK = "openstack";
    private static final String CINDER = "cinder";
    private static final String SCALEIO = "scaleio";
    private static final String SCALEIOAPI = "scaleioapi";
    private static final String XTREMIO = "xtremio";
    private static final String DATA_DOMAIN = "datadomain";
    private static final String DATA_DOMAIN_PROVIDER = "ddmc";
    private static final String ECS = "ecs";
    private static final String CEPH = "ceph";
    private static final String UNITY = "unity";
    private static final String VNXFILE_SMIS = "vnxfile_smis";
    private static final String DELLSCSYSTEM = "dellscsystem";
    private static final String DELLSCPROVIDER = "dellscprovider";

    private static final Map<META_TYPE, List<String>> SYSTEMS_AND_PROVIDERS;

    private static final List<String> SSL_ENABLE_TYPE_LIST = asList(VNX_BLOCK, VMAX, SMIS, SCALEIOAPI, VPLEX,
            VNX_FILE, UNITY, VNXe, IBMXIV, DELLSCSYSTEM, DELLSCPROVIDER);
    private static final List<String> MDM_ENABLE_LIST = asList(SCALEIO, SCALEIOAPI);
    private static final List<String> ONLY_MDM_LIST = asList(SCALEIOAPI);
    private static final List<String> ELEMENT_MANAGER_LIST = asList(SCALEIO);
    private static final List<String> SECREAT_KEY_ENABLE_LIST = asList(CEPH);
    private static final Map<String, String> DISPLAY_NAME_MAP;
    private static final Map<String, String> SSL_PORT_MAP;
    private static final Map<String, String> NON_SSL_PORT_MAP;
    private static final Map<String, Set<String>> SUPPORTED_PROFILES_MAP;

    /*
     * Some storage systems should only be discovered by provider, not be added directly.
       For these storage systems, providers of them are shown on storage system adding page.
       This map is to store the mapping relation between storage system name and its storage
       provider display name.
     */
    private static final Map<String, String> STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP;

    static {
        SYSTEMS_AND_PROVIDERS = new HashMap<META_TYPE, List<String>>();
        SYSTEMS_AND_PROVIDERS.put(META_TYPE.BLOCK, asList(VMAX, VNX_BLOCK, HITACHI, OPENSTACK, DATA_DOMAIN,
                DELLSCSYSTEM));
        SYSTEMS_AND_PROVIDERS.put(META_TYPE.FILE, asList(VNX_FILE, ISILON, NETAPP, NETAPPC));
        SYSTEMS_AND_PROVIDERS.put(META_TYPE.OBJECT, asList(ECS));
        SYSTEMS_AND_PROVIDERS.put(META_TYPE.BLOCK_AND_FILE, asList(UNITY, VNXe));
        SYSTEMS_AND_PROVIDERS.put(META_TYPE.BLOCK_PROVIDER, asList(SMIS, HITACHI_PROVIDER, CINDER,
                DATA_DOMAIN_PROVIDER, VPLEX, SCALEIO, IBMXIV, XTREMIO, CEPH, SCALEIOAPI, DELLSCPROVIDER));

        DISPLAY_NAME_MAP = new HashMap<String, String>();
        DISPLAY_NAME_MAP.put(VMAX, "EMC VMAX");
        DISPLAY_NAME_MAP.put(VNX_BLOCK, "EMC VNX Block");
        DISPLAY_NAME_MAP.put(VNX_FILE, "EMC VNX File");
        DISPLAY_NAME_MAP.put(ISILON, "EMC Isilon");
        DISPLAY_NAME_MAP.put(NETAPP, "NetApp 7-mode");
        DISPLAY_NAME_MAP.put(NETAPPC, "NetApp Cluster-mode");
        DISPLAY_NAME_MAP.put(VNXe, "EMC VNXe");
        DISPLAY_NAME_MAP.put(VPLEX, "EMC VPLEX");
        DISPLAY_NAME_MAP.put(HITACHI, "Hitachi");
        DISPLAY_NAME_MAP.put(IBMXIV, "IBM XIV");
        DISPLAY_NAME_MAP.put(OPENSTACK, "Third-party block");
        DISPLAY_NAME_MAP.put(SCALEIO, "Block Storage Powered by ScaleIO");
        DISPLAY_NAME_MAP.put(SCALEIOAPI, "ScaleIO Gateway");
        DISPLAY_NAME_MAP.put(XTREMIO, "EMC XtremIO");
        DISPLAY_NAME_MAP.put(DATA_DOMAIN, "Data Domain");
        DISPLAY_NAME_MAP.put(ECS, "EMC Elastic Cloud Storage");
        DISPLAY_NAME_MAP.put(UNITY, "EMC Unity");
        DISPLAY_NAME_MAP.put(CEPH, "Block Storage powered by Ceph");
        DISPLAY_NAME_MAP.put(DELLSCSYSTEM, "Dell SC Storage");

        DISPLAY_NAME_MAP.put(SMIS, "Storage Provider for EMC VMAX or VNX Block");
        DISPLAY_NAME_MAP.put(HITACHI_PROVIDER, "Storage Provider for Hitachi storage systems");
        DISPLAY_NAME_MAP.put(CINDER, "Storage Provider for Third-party block storage systems");
        DISPLAY_NAME_MAP.put(DATA_DOMAIN_PROVIDER, "Storage Provider for Data Domain Management Center");
        DISPLAY_NAME_MAP.put(DELLSCPROVIDER, "Storage Provider for Dell SC Storage");

        SSL_PORT_MAP = new HashMap<String, String>();
        SSL_PORT_MAP.put(VNX_FILE, "5989");
        SSL_PORT_MAP.put(SCALEIOAPI, "443");
        SSL_PORT_MAP.put(VNX_BLOCK, "5989");
        SSL_PORT_MAP.put(VMAX, "5989");
        SSL_PORT_MAP.put(SMIS, "5989");
        SSL_PORT_MAP.put(HITACHI, "2001");
        SSL_PORT_MAP.put(HITACHI_PROVIDER, "2001");
        SSL_PORT_MAP.put(VPLEX, "443");
        SSL_PORT_MAP.put(OPENSTACK, "22");
        SSL_PORT_MAP.put(CINDER, "22");
        SSL_PORT_MAP.put(SCALEIO, "22");
        SSL_PORT_MAP.put(DATA_DOMAIN, "3009");
        SSL_PORT_MAP.put(DATA_DOMAIN_PROVIDER, "3009");
        SSL_PORT_MAP.put(IBMXIV, "5989");
        SSL_PORT_MAP.put(XTREMIO, "443");
        SSL_PORT_MAP.put(ECS, "4443");
        SSL_PORT_MAP.put(VNXe, "443");
        SSL_PORT_MAP.put(VNXFILE_SMIS, "5989");
        SSL_PORT_MAP.put(UNITY, "443");
        SSL_PORT_MAP.put(DELLSCSYSTEM, "3033");
        SSL_PORT_MAP.put(DELLSCPROVIDER, "3033");
        SSL_PORT_MAP.put(CEPH, "6789");

        NON_SSL_PORT_MAP = new HashMap<String, String>();
        NON_SSL_PORT_MAP.put(HITACHI_PROVIDER, "2001");
        NON_SSL_PORT_MAP.put(SMIS, "5988");
        NON_SSL_PORT_MAP.put(VPLEX, "443");
        NON_SSL_PORT_MAP.put(CINDER, "22");
        NON_SSL_PORT_MAP.put(OPENSTACK, "22");
        NON_SSL_PORT_MAP.put(SCALEIO, "22");
        NON_SSL_PORT_MAP.put(SCALEIOAPI, "80");
        NON_SSL_PORT_MAP.put(DATA_DOMAIN_PROVIDER, "3009");
        NON_SSL_PORT_MAP.put(DATA_DOMAIN, "3009");
        NON_SSL_PORT_MAP.put(IBMXIV, "5989");
        NON_SSL_PORT_MAP.put(XTREMIO, "443");
        NON_SSL_PORT_MAP.put(VNX_BLOCK, "5988");
        NON_SSL_PORT_MAP.put(VMAX, "5988");
        NON_SSL_PORT_MAP.put(ISILON, "8080");
        NON_SSL_PORT_MAP.put(NETAPP, "443");
        NON_SSL_PORT_MAP.put(NETAPPC, "443");
        NON_SSL_PORT_MAP.put(VPLEX, "443");
        NON_SSL_PORT_MAP.put(XTREMIO, "443");
        NON_SSL_PORT_MAP.put(VNX_FILE, "443");
        NON_SSL_PORT_MAP.put(VNXe, "443");
        NON_SSL_PORT_MAP.put(VNXFILE_SMIS, "5988");
        NON_SSL_PORT_MAP.put(HITACHI, "2001");
        NON_SSL_PORT_MAP.put(UNITY, "443");
        NON_SSL_PORT_MAP.put(CEPH, "6789");

        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP = new HashMap<String, String>();
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(VMAX, "Storage Provider for EMC VMAX, VNX Block");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(SCALEIOAPI, "ScaleIO Gateway");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(HITACHI, "Storage Provider for Hitachi storage systems");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(VPLEX, "Storage Provider for EMC VPLEX");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(OPENSTACK, "Storage Provider for Third-party block storage systems");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(SCALEIO, "Block Storage Powered by ScaleIO");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(DATA_DOMAIN, "Storage Provider for Data Domain Management Center");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(IBMXIV, "Storage Provider for IBM XIV");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(XTREMIO, "Storage Provider for EMC XtremIO");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(CEPH, "Block Storage powered by Ceph");
        STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP.put(DELLSCSYSTEM, "Storage Provider for Dell SC Storage");

        // Among native types, only vmax supports remote replication for block
        SUPPORTED_PROFILES_MAP = new HashMap<>();
        Set<String> supportedProfiles = new HashSet<>();
        supportedProfiles.add(StorageProfile.REMOTE_REPLICATION_FOR_BLOCK.toString());
        SUPPORTED_PROFILES_MAP.put(VMAX, supportedProfiles);
    }

    public StorageSystemTypesInitUtils(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    private DbClient dbClient;

    private Map<String, StorageSystemType> existingTypes;

    private void loadTypeMapFromDb() {
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> types = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        existingTypes = new HashMap<String, StorageSystemType>();
        while (types.hasNext()) {
            StorageSystemType type = types.next();
            existingTypes.put(type.getStorageTypeName(), type);
        }
    }

    /**
     * Return true only when all fields stored in DB are same with given type parameter
     */
    private boolean alreadyExists(StorageSystemType type) {
        if (existingTypes.containsKey(type.getStorageTypeName())) {
            StorageSystemType existingType = existingTypes.get(type.getStorageTypeName());
            if (existingType.equals(type)) {
                return true;
            } else {
                // If it exists but has changed, should remove and re-create
                dbClient.removeObject(existingType);
                existingTypes.remove(existingType.getStorageTypeName());
            }
        }
        return false;
    }

    public static Map<String, String> getDisplayNames() {
        return DISPLAY_NAME_MAP;
    }

    public static Map<String, String> getProviderDsiplayNameMap() {
        return STORAGE_SYSTEM_PROVIDER_DISP_NAME_MAP;
    }

    /**
     * Keep consistent with previous design so to avoid possible regression.
     * For block providers, use block as meta type.
     * For file providers, use file as meta type.
     */
    private String mapType(META_TYPE metaType) {
        if (metaType == META_TYPE.BLOCK_PROVIDER) {
            return META_TYPE.BLOCK.toString().toLowerCase();
        }
        if (metaType == META_TYPE.FILE_PROVIDER) {
            return META_TYPE.FILE.toString().toLowerCase();
        }
        return metaType.toString().toLowerCase();
    }

    private void insertStorageSystemTypes() {
        for (Map.Entry<META_TYPE, List<String>> entry : SYSTEMS_AND_PROVIDERS.entrySet()) {
            META_TYPE metaType = entry.getKey();
            List<String> systems = entry.getValue();
            for (String system : systems) {
                if (!PlatformUtils.isOssBuild() && system.equals(CEPH)) {
                    log.info("Skip inserting ceph meta data in non-oss build");
                    continue;
                }
                StorageSystemType type = new StorageSystemType();
                URI uri = URIUtil.createId(StorageSystemType.class);
                type.setId(uri);
                type.setStorageTypeId(uri.toString());
                type.setStorageTypeName(system);
                type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(system));
                type.setMetaType(mapType(metaType));
                type.setDriverClassName(metaType.toString().toLowerCase());
                type.setIsSmiProvider(metaType.isProvider());
                type.setIsDefaultSsl(SSL_ENABLE_TYPE_LIST.contains(system));
                type.setIsDefaultMDM(MDM_ENABLE_LIST.contains(system));
                type.setIsOnlyMDM(ONLY_MDM_LIST.contains(system));
                type.setIsElementMgr(ELEMENT_MANAGER_LIST.contains(system));
                type.setIsSecretKey(SECREAT_KEY_ENABLE_LIST.contains(system));
                type.setSslPort(SSL_PORT_MAP.get(system));
                type.setNonSslPort(NON_SSL_PORT_MAP.get(system));
                type.setIsNative(true);
                Set<String> supportedStorageProfiles = SUPPORTED_PROFILES_MAP.get(system);
                if (CollectionUtils.isNotEmpty(supportedStorageProfiles)) {
                    type.setSupportedStorageProfiles(new StringSet(supportedStorageProfiles));
                }
                if (alreadyExists(type)) {
                    log.info("Meta data for {} already exist", type.getStorageTypeName());
                    continue;
                }
                log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
                dbClient.createObject(type);
            }
        }
    }

    public void initializeStorageSystemTypes() {
        log.info("Intializing storage system type Column Family for default storage drivers");
        loadTypeMapFromDb();
        insertStorageSystemTypes();
        log.info("Default drivers initialization done.");
    }
}
