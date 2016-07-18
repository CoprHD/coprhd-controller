/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;

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

    private static final List<String> FILE_TYPE_SYSTEMS = asList(VNX_FILE, ISILON, NETAPP, NETAPPC);
    private static final List<String> BLOCK_TYPE_SYSTEMS = asList(VMAX, VNX_BLOCK, VNXe, HITACHI, OPENSTACK, DATA_DOMAIN);
    private static final List<String> OBJECT_TYPE_SYSTEMS = asList(ECS);
    private static final List<String> FILE_TYPE_SYSTEM_PROVIDERS = asList(SCALEIOAPI);
    private static final List<String> BLOCK_TYPE_SYSTEM_PROVIDERS = asList(SMIS, HITACHI_PROVIDER, CINDER,
            DATA_DOMAIN_PROVIDER, VPLEX, SCALEIO, IBMXIV, XTREMIO, CEPH);

    private static final Map<String, Boolean> DEFAULT_SSL_ENABLE_MAP;
    private static final Map<String, Boolean> DEFAULT_MDM_ENABLE_MAP;
    private static final Map<String, Boolean> ONLY_MDM_MAP;
    private static final Map<String, Boolean> ELEMENT_MANAGER;
    private static final Map<String, Boolean> SECREAT_KEY_ENABLE_MAP;
    private static final Map<String, String> DISPLAY_NAME_MAP;
    private static final Map<String, String> SSL_PORT_MAP;
    private static final Map<String, String> NON_SSL_PORT_MAP;
    private static final Map<String, String> STORAGE_PROVIDER_MAP;

    static {
        // Initialize default SSL enable map
        DEFAULT_SSL_ENABLE_MAP = new HashMap<String, Boolean>();
        DEFAULT_SSL_ENABLE_MAP.put(VNX_BLOCK, true);
        DEFAULT_SSL_ENABLE_MAP.put(VMAX, true);
        DEFAULT_SSL_ENABLE_MAP.put(SMIS, true);
        DEFAULT_SSL_ENABLE_MAP.put(SCALEIOAPI, true);
        DEFAULT_SSL_ENABLE_MAP.put(VPLEX, true);
        DEFAULT_SSL_ENABLE_MAP.put(VNX_FILE, true);
        DEFAULT_SSL_ENABLE_MAP.put(VNXe, true);
        DEFAULT_SSL_ENABLE_MAP.put(IBMXIV, true);

        // Initialize secret key map
        SECREAT_KEY_ENABLE_MAP = new HashMap<String, Boolean>();
        SECREAT_KEY_ENABLE_MAP.put(CEPH, true);

        // Initialize default MDM enable map
        DEFAULT_MDM_ENABLE_MAP = new HashMap<String, Boolean>();
        DEFAULT_MDM_ENABLE_MAP.put(SCALEIO, true);
        DEFAULT_MDM_ENABLE_MAP.put(SCALEIOAPI, true);

        ONLY_MDM_MAP = new HashMap<String, Boolean>();
        ONLY_MDM_MAP.put(SCALEIOAPI, true);

        ELEMENT_MANAGER = new HashMap<String, Boolean>();
        ELEMENT_MANAGER.put(SCALEIO, true);

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

        DISPLAY_NAME_MAP.put(SMIS, "Storage Provider for EMC VMAX or VNX Block");
        DISPLAY_NAME_MAP.put(HITACHI_PROVIDER, "Storage Provider for Hitachi storage systems");
        DISPLAY_NAME_MAP.put(CINDER, "Storage Provider for Third-party block storage systems");
        DISPLAY_NAME_MAP.put(DATA_DOMAIN_PROVIDER, "Storage Provider for Data Domain Management Center");

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

        STORAGE_PROVIDER_MAP = new HashMap<String, String>();
        STORAGE_PROVIDER_MAP.put(VMAX, "Storage Provider for EMC VMAX, VNX Block");
        STORAGE_PROVIDER_MAP.put(SCALEIOAPI, "ScaleIO Gateway");
        STORAGE_PROVIDER_MAP.put(HITACHI, "Storage Provider for Hitachi storage systems");
        STORAGE_PROVIDER_MAP.put(VPLEX, "Storage Provider for EMC VPLEX");
        STORAGE_PROVIDER_MAP.put(OPENSTACK, "Storage Provider for Third-party block storage systems");
        STORAGE_PROVIDER_MAP.put(SCALEIO, "Block Storage Powered by ScaleIO");
        STORAGE_PROVIDER_MAP.put(DATA_DOMAIN, "Storage Provider for Data Domain Management Center");
        STORAGE_PROVIDER_MAP.put(IBMXIV, "Storage Provider for IBM XIV");
        STORAGE_PROVIDER_MAP.put(XTREMIO, "Storage Provider for EMC XtremIO");
        STORAGE_PROVIDER_MAP.put(CEPH, "Block Storage powered by Ceph");
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

    private void insertFileArrays() {
        for (String file : FILE_TYPE_SYSTEMS) {
            StorageSystemType type = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            type.setId(ssTyeUri);
            type.setStorageTypeId(ssTyeUri.toString());
            type.setStorageTypeName(file);
            type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(file));
            type.setMetaType(StorageSystemType.META_TYPE.FILE.toString().toLowerCase());
            type.setDriverClassName("file");
            type.setIsSmiProvider(false);

            if (DEFAULT_SSL_ENABLE_MAP.get(file) != null) {
                type.setIsDefaultSsl(true);
            }

            if (DEFAULT_MDM_ENABLE_MAP.get(file) != null) {
                type.setIsDefaultMDM(true);
            }
            if (ONLY_MDM_MAP.get(file) != null) {
                type.setIsOnlyMDM(true);
            }
            if (ELEMENT_MANAGER.get(file) != null) {
                type.setIsElementMgr(true);
            }
            if (SSL_PORT_MAP.get(file) != null) {
                type.setSslPort(SSL_PORT_MAP.get(file));
            }
            if (NON_SSL_PORT_MAP.get(file) != null) {
                type.setNonSslPort(NON_SSL_PORT_MAP.get(file));
            }

            if (alreadyExists(type)) {
                log.info("Meta data for {} already exist", type.getStorageTypeName());
                continue;
            }
            log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
            dbClient.createObject(type);
        }
    }

    private void insertFileProviders() {
        for (String provider : FILE_TYPE_SYSTEM_PROVIDERS) {
            StorageSystemType type = new StorageSystemType();
            URI ssTypeUri = URIUtil.createId(StorageSystemType.class);
            type.setId(ssTypeUri);
            type.setStorageTypeId(ssTypeUri.toString());
            type.setStorageTypeName(provider);
            type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(provider));
            type.setMetaType(StorageSystemType.META_TYPE.FILE.toString().toLowerCase());
            type.setDriverClassName("file");
            type.setIsSmiProvider(true);
            if (DEFAULT_SSL_ENABLE_MAP.get(provider) != null) {
                type.setIsDefaultSsl(true);
            }
            if (DEFAULT_MDM_ENABLE_MAP.get(provider) != null) {
                type.setIsDefaultMDM(true);
            }
            if (ONLY_MDM_MAP.get(provider) != null) {
                type.setIsOnlyMDM(true);
            }
            if (ELEMENT_MANAGER.get(provider) != null) {
                type.setIsElementMgr(true);
            }
            if (SSL_PORT_MAP.get(provider) != null) {
                type.setSslPort(SSL_PORT_MAP.get(provider));
            }
            if (NON_SSL_PORT_MAP.get(provider) != null) {
                type.setNonSslPort(NON_SSL_PORT_MAP.get(provider));
            }

            if (alreadyExists(type)) {
                log.info("Meta data for {} already exist", type.getStorageTypeName());
                continue;
            }
            log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
            dbClient.createObject(type);
        }
    }

    private void insertBlockArrays() {
        for (String block : BLOCK_TYPE_SYSTEMS) {
            StorageSystemType type = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            type.setId(ssTyeUri);
            type.setStorageTypeId(ssTyeUri.toString());
            type.setStorageTypeName(block);
            type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(block));
            type.setMetaType(StorageSystemType.META_TYPE.BLOCK.toString().toLowerCase());
            type.setDriverClassName("block");
            type.setIsSmiProvider(false);
            if (DEFAULT_SSL_ENABLE_MAP.get(block) != null) {
                type.setIsDefaultSsl(true);
            }
            if (DEFAULT_MDM_ENABLE_MAP.get(block) != null) {
                type.setIsDefaultMDM(true);
            }
            if (ONLY_MDM_MAP.get(block) != null) {
                type.setIsOnlyMDM(true);
            }
            if (ELEMENT_MANAGER.get(block) != null) {
                type.setIsElementMgr(true);
            }
            if (SSL_PORT_MAP.get(block) != null) {
                type.setSslPort(SSL_PORT_MAP.get(block));
            }
            if (NON_SSL_PORT_MAP.get(block) != null) {
                type.setNonSslPort(NON_SSL_PORT_MAP.get(block));
            }
            if (SECREAT_KEY_ENABLE_MAP.get(block) != null) {
                type.setIsSecretKey(SECREAT_KEY_ENABLE_MAP.get(block));
            }

            if (alreadyExists(type)) {
                log.info("Meta data for {} already exist", type.getStorageTypeName());
                continue;
            }
            log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
            dbClient.createObject(type);
        }
    }

    private void insertBlockProviders() {
        for (String provider : BLOCK_TYPE_SYSTEM_PROVIDERS) {
            StorageSystemType type = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            type.setId(ssTyeUri);
            type.setStorageTypeId(ssTyeUri.toString());
            type.setStorageTypeName(provider);
            type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(provider));
            type.setMetaType(StorageSystemType.META_TYPE.BLOCK.toString().toLowerCase());
            type.setDriverClassName("block");
            type.setIsSmiProvider(true);
            if (DEFAULT_SSL_ENABLE_MAP.get(provider) != null) {
                type.setIsDefaultSsl(true);
            }
            if (DEFAULT_MDM_ENABLE_MAP.get(provider) != null) {
                type.setIsDefaultMDM(true);
            }
            if (ONLY_MDM_MAP.get(provider) != null) {
                type.setIsOnlyMDM(true);
            }
            if (ELEMENT_MANAGER.get(provider) != null) {
                type.setIsElementMgr(true);
            }
            if (SSL_PORT_MAP.get(provider) != null) {
                type.setSslPort(SSL_PORT_MAP.get(provider));
            }
            if (NON_SSL_PORT_MAP.get(provider) != null) {
                type.setNonSslPort(NON_SSL_PORT_MAP.get(provider));
            }
            if (SECREAT_KEY_ENABLE_MAP.get(provider) != null) {
                type.setIsSecretKey(SECREAT_KEY_ENABLE_MAP.get(provider));
            }

            if (alreadyExists(type)) {
                log.info("Meta data for {} already exist", type.getStorageTypeName());
                continue;
            }
            log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
            dbClient.createObject(type);
        }
    }

    private void insertUnity() {
        StorageSystemType unity = new StorageSystemType();
        URI unityUri = URIUtil.createId(StorageSystemType.class);
        unity.setId(unityUri);
        unity.setStorageTypeId(unityUri.toString());
        unity.setStorageTypeName(UNITY);
        unity.setStorageTypeDispName(DISPLAY_NAME_MAP.get(UNITY));
        unity.setMetaType(StorageSystemType.META_TYPE.BLOCK_AND_FILE.toString().toLowerCase());
        unity.setIsSmiProvider(false);
        unity.setIsDefaultSsl(true);
        if (SSL_PORT_MAP.get(UNITY) != null) {
            unity.setSslPort(SSL_PORT_MAP.get(UNITY));
        }
        unity.setDriverClassName(StorageSystemType.META_TYPE.BLOCK_AND_FILE.toString().toLowerCase());

        if (alreadyExists(unity)) {
            log.info("Meta data for {} already exist", unity.getStorageTypeName());
            return;
        }
        log.info("Meta data for {} don't exist or have changed, update", unity.getStorageTypeName());
        dbClient.createObject(unity);
    }

    private void insertObjectArrays() {
        for (String object : OBJECT_TYPE_SYSTEMS) {
            StorageSystemType type = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            type.setId(ssTyeUri);
            type.setStorageTypeId(ssTyeUri.toString());
            type.setStorageTypeName(object);
            type.setStorageTypeDispName(DISPLAY_NAME_MAP.get(object));
            type.setMetaType(StorageSystemType.META_TYPE.OBJECT.toString().toLowerCase());
            type.setDriverClassName("object");
            type.setIsSmiProvider(false);
            if (DEFAULT_SSL_ENABLE_MAP.get(object) != null) {
                type.setIsDefaultSsl(true);
            }
            if (DEFAULT_MDM_ENABLE_MAP.get(object) != null) {
                type.setIsDefaultMDM(true);
            }
            if (ONLY_MDM_MAP.get(object) != null) {
                type.setIsOnlyMDM(true);
            }
            if (ELEMENT_MANAGER.get(object) != null) {
                type.setIsElementMgr(true);
            }
            if (SSL_PORT_MAP.get(object) != null) {
                type.setSslPort(SSL_PORT_MAP.get(object));
            }
            if (NON_SSL_PORT_MAP.get(object) != null) {
                type.setNonSslPort(NON_SSL_PORT_MAP.get(object));
            }

            if (alreadyExists(type)) {
                log.info("Meta data for {} already exist", type.getStorageTypeName());
                continue;
            }
            log.info("Meta data for {} don't exist or have changed, update", type.getStorageTypeName());
            dbClient.createObject(type);
        }
    }

    public static Map<String, String> getDisplayNames() {
        return DISPLAY_NAME_MAP;
    }

    public static Map<String, String> getProviderDsiplayNameMap() {
        return STORAGE_PROVIDER_MAP;
    }

    public void initializeStorageSystemTypes() {
        log.info("Intializing storage system type Column Family for default storage drivers");
        loadTypeMapFromDb();
        insertFileArrays();
        insertFileProviders();
        insertBlockArrays();
        insertBlockProviders();
        insertObjectArrays();
        insertUnity();
        log.info("Default drivers initialization done.");
    }
}
