/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.google.common.collect.Lists;

public class StorageSystemTypesInitUtils {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemTypesInitUtils.class);

    private static final String ISILON = "isilon";
    private static final String VNX_BLOCK = "vnxblock";
    private static final String VNXe = "vnxe";
    private static final String VNX_FILE = "vnxfile";
    private static final String VMAX = "smis";
    private static final String NETAPP = "netapp";
    private static final String NETAPPC = "netappc";
    private static final String HITACHI = "hds";
    private static final String IBMXIV = "ibmxiv";
    private static final String VPLEX = "vplex";
    private static final String OPENSTACK = "openstack";
    private static final String SCALEIO = "scaleio";
    private static final String SCALEIOAPI = "scaleioapi";
    private static final String XTREMIO = "xtremio";
    private static final String DATA_DOMAIN = "datadomain";
    private static final String ECS = "ecs";

    // Default File arrays
    private static List<String> storageArrayFile = asList(VNX_FILE, ISILON, NETAPP, NETAPPC);

    // Default Provider for File
    private static List<String> storageProviderFile = asList(SCALEIOAPI);

    // Default block arrays
    private static List<String> storageArrayBlock = asList(VNX_BLOCK, VNXe);

    // Default Storage provider for Block
    private static List<String> storageProviderBlock = asList(VMAX, HITACHI, VPLEX, OPENSTACK, SCALEIO, DATA_DOMAIN,
            IBMXIV, XTREMIO);

    // Default object arrays
    private static List<String> storageArrayObject = asList(ECS);

    private StorageSystemTypesInitUtils() {
    }

    private static HashMap<String, Boolean> defaultSSL = null;

    private static HashMap<String, Boolean> defaultMDM = null;

    private static HashMap<String, Boolean> onlyMDM = null;

    private static HashMap<String, Boolean> elementManager = null;

    // Name of Array and its Display Name mapping
    private static HashMap<String, String> nameDisplayNameMap = null;

    private static HashMap<String, String> sslPortMap = null;

    private static HashMap<String, String> nonSslPortMap = null;

    private static HashMap<String, String> dbStorageTypeMap = null;

    private static void initializeDefaultSSL() {
        defaultSSL = new HashMap<String, Boolean>();
        defaultSSL.put(VNX_BLOCK, true);
        defaultSSL.put(VMAX, true);
        defaultSSL.put(SCALEIOAPI, true);
        defaultSSL.put(VPLEX, true);
        defaultSSL.put(VNX_FILE, true);
        defaultSSL.put(VNXe, true);
        defaultSSL.put(IBMXIV, true);
    }

    public static HashMap<String, String> initializeDisplayName() {
        HashMap<String, String> displayNameMap = new HashMap<String, String>();
        displayNameMap.put(VNX_FILE, "EMC VNX File");
        displayNameMap.put(ISILON, "EMC Isilon");
        displayNameMap.put(NETAPP, "NetApp 7-mode");
        displayNameMap.put(NETAPPC, "NetApp Cluster-mode");
        displayNameMap.put(SCALEIOAPI, "ScaleIO Gateway");
        displayNameMap.put(VNX_BLOCK, "EMC VNX Block");
        displayNameMap.put(VNXe, "EMC VNXe");
        displayNameMap.put(VMAX, "Storage Provider for EMC VMAX or VNX Block");
        displayNameMap.put(HITACHI, "Storage Provider for Hitachi storage systems");
        displayNameMap.put(VPLEX, "Storage Provider for EMC VPLEX");
        displayNameMap.put(OPENSTACK, "Storage Provider for Third-party block storage systems");
        displayNameMap.put(SCALEIO, "Block Storage Powered by ScaleIO");
        displayNameMap.put(DATA_DOMAIN, "Storage Provider for Data Domain Management Center");
        displayNameMap.put(IBMXIV, "Storage Provider for IBM XIV");
        displayNameMap.put(XTREMIO, "Storage Provider for EMC XtremIO");
        displayNameMap.put(ECS, "EMC Elastic Cloud Storage");
        return displayNameMap;
    }

    private static void initializeSSLPort() {
        sslPortMap = new HashMap<String, String>();
        sslPortMap.put(VNX_FILE, "5989");
        sslPortMap.put(SCALEIOAPI, "443");
        sslPortMap.put(VNX_BLOCK, "5989");
        sslPortMap.put(VMAX, "5989");
        sslPortMap.put(HITACHI, "2001");
        sslPortMap.put(VPLEX, "443");
        sslPortMap.put(OPENSTACK, "22");
        sslPortMap.put(SCALEIO, "22");
        sslPortMap.put(DATA_DOMAIN, "3009");
        sslPortMap.put(IBMXIV, "5989");
        sslPortMap.put(XTREMIO, "443");
        sslPortMap.put(ECS, "4443");
        sslPortMap.put(VNXe, "443");
        sslPortMap.put("vnxfile_smis", "5989");
    }

    private static void initializeNonSslPort() {
        nonSslPortMap = new HashMap<String, String>();
        nonSslPortMap.put("hicommand", "2001");
        nonSslPortMap.put("smis", "5988");
        nonSslPortMap.put("vplex", "443");
        nonSslPortMap.put("cinder", "22");
        nonSslPortMap.put("openstack", "22");
        nonSslPortMap.put("scaleio", "22");
        nonSslPortMap.put("scaleioapi", "80");
        nonSslPortMap.put("ddmc", "3009");
        nonSslPortMap.put("ibmxiv", "5989");
        nonSslPortMap.put("xtremio", "443");
        nonSslPortMap.put("vnxblock", "5988");
        nonSslPortMap.put("vmax", "5988");
        nonSslPortMap.put("ibmxiv", "5989");
        nonSslPortMap.put("isilon", "8080");
        nonSslPortMap.put("netapp", "443");
        nonSslPortMap.put("netappc", "443");
        nonSslPortMap.put("vplex", "443");
        nonSslPortMap.put("xtremio", "443");
        nonSslPortMap.put("vnxfile", "443");
        nonSslPortMap.put("vnxe", "443");
        nonSslPortMap.put("vnxfile_smis", "5988");
        nonSslPortMap.put("hds", "2001");
    }

    /**
     * Create a HashMap of existing storage system types, to avoid duplicate insertion
     * 
     */
    private static void createDbStorageTypeMap(DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(StorageSystemType.class, true);
        ArrayList<URI> uriList = Lists.newArrayList(ids.iterator());
        if (!uriList.isEmpty()) {
            Iterator<StorageSystemType> iter = dbClient.queryIterativeObjects(StorageSystemType.class, ids);
            dbStorageTypeMap = new HashMap<String, String>();
            while (iter.hasNext()) {
                StorageSystemType ssType = iter.next();
                dbStorageTypeMap.put(ssType.getStorageTypeName(), ssType.getStorageTypeName());
            }
        }
    }

    private static void insertFileArrays(DbClient dbClient) {
        for (String file : storageArrayFile) {
            if (dbStorageTypeMap != null && dbStorageTypeMap.get(file) != null) {
                //avoid duplicate entries
                continue;
            }
            StorageSystemType ssType = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            ssType.setId(ssTyeUri);
            ssType.setStorageTypeId(ssTyeUri.toString());
            ssType.setStorageTypeName(file);
            ssType.setStorageTypeDispName(nameDisplayNameMap.get(file));
            ssType.setStorageTypeType("file");
            ssType.setDriverClassName("file");
            ssType.setIsSmiProvider(false);

            if (defaultSSL.get(file) != null) {
                ssType.setIsDefaultSsl(true);
            }

            if (defaultMDM.get(file) != null) {
                ssType.setIsDefaultMDM(true);
            }
            if (onlyMDM.get(file) != null) {
                ssType.setIsOnlyMDM(true);
            }
            if (elementManager.get(file) != null) {
                ssType.setIsElementMgr(true);
            }
            if (sslPortMap.get(file) != null) {
                ssType.setSslPort(sslPortMap.get(file));
            }
            if (nonSslPortMap.get(file) != null) {
                ssType.setNonSslPort(nonSslPortMap.get(file));
            }

            dbClient.createObject(ssType);
        }
    }

    private static void insertFileProviders(DbClient dbClient) {
        for (String file : storageProviderFile) {
            if (dbStorageTypeMap != null && dbStorageTypeMap.get(file) != null) {
              //avoid duplicate entries
                continue;
            }
            StorageSystemType ssType = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            ssType.setId(ssTyeUri);
            ssType.setStorageTypeId(ssTyeUri.toString());
            ssType.setStorageTypeName(file);
            ssType.setStorageTypeDispName(nameDisplayNameMap.get(file));
            ssType.setStorageTypeType("file");
            ssType.setDriverClassName("file");
            ssType.setIsSmiProvider(true);
            if (defaultSSL.get(file) != null) {
                ssType.setIsDefaultSsl(true);
            }
            if (defaultMDM.get(file) != null) {
                ssType.setIsDefaultMDM(true);
            }
            if (onlyMDM.get(file) != null) {
                ssType.setIsOnlyMDM(true);
            }
            if (elementManager.get(file) != null) {
                ssType.setIsElementMgr(true);
            }
            if (sslPortMap.get(file) != null) {
                ssType.setSslPort(sslPortMap.get(file));
            }
            if (nonSslPortMap.get(file) != null) {
                ssType.setNonSslPort(nonSslPortMap.get(file));
            }
            dbClient.createObject(ssType);
        }
    }

    private static void insertBlockArrays(DbClient dbClient) {
        for (String block : storageArrayBlock) {
            if (dbStorageTypeMap != null && dbStorageTypeMap.get(block) != null) {
              //avoid duplicate entries
                continue;
            }
            StorageSystemType ssType = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            ssType.setId(ssTyeUri);
            ssType.setStorageTypeId(ssTyeUri.toString());
            ssType.setStorageTypeName(block);
            ssType.setStorageTypeDispName(nameDisplayNameMap.get(block));
            ssType.setStorageTypeType("block");
            ssType.setDriverClassName("block");
            ssType.setIsSmiProvider(false);
            if (defaultSSL.get(block) != null) {
                ssType.setIsDefaultSsl(true);
            }
            if (defaultMDM.get(block) != null) {
                ssType.setIsDefaultMDM(true);
            }
            if (onlyMDM.get(block) != null) {
                ssType.setIsOnlyMDM(true);
            }
            if (elementManager.get(block) != null) {
                ssType.setIsElementMgr(true);
            }
            if (sslPortMap.get(block) != null) {
                ssType.setSslPort(sslPortMap.get(block));
            }
            if (nonSslPortMap.get(block) != null) {
                ssType.setNonSslPort(nonSslPortMap.get(block));
            }

            dbClient.createObject(ssType);
        }
    }

    private static void insertBlockProviders(DbClient dbClient) {
        for (String block : storageProviderBlock) {
            if (dbStorageTypeMap != null && dbStorageTypeMap.get(block) != null) {
              //avoid duplicate entries
                continue;
            }
            StorageSystemType ssType = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            ssType.setId(ssTyeUri);
            ssType.setStorageTypeId(ssTyeUri.toString());
            ssType.setStorageTypeName(block);
            ssType.setStorageTypeDispName(nameDisplayNameMap.get(block));
            ssType.setStorageTypeType("block");
            ssType.setDriverClassName("block");
            ssType.setIsSmiProvider(true);
            if (defaultSSL.get(block) != null) {
                ssType.setIsDefaultSsl(true);
            }
            if (defaultMDM.get(block) != null) {
                ssType.setIsDefaultMDM(true);
            }
            if (onlyMDM.get(block) != null) {
                ssType.setIsOnlyMDM(true);
            }
            if (elementManager.get(block) != null) {
                ssType.setIsElementMgr(true);
            }
            if (sslPortMap.get(block) != null) {
                ssType.setSslPort(sslPortMap.get(block));
            }
            if (nonSslPortMap.get(block) != null) {
                ssType.setNonSslPort(nonSslPortMap.get(block));
            }

            dbClient.createObject(ssType);
        }
    }

    private static void insertObjectArrays(DbClient dbClient) {
        for (String object : storageArrayObject) {
            if (dbStorageTypeMap != null && dbStorageTypeMap.get(object) != null) {
              //avoid duplicate entries
                continue;
            }
            StorageSystemType ssType = new StorageSystemType();
            URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
            ssType.setId(ssTyeUri);
            ssType.setStorageTypeId(ssTyeUri.toString());
            ssType.setStorageTypeName(object);
            ssType.setStorageTypeDispName(nameDisplayNameMap.get(object));
            ssType.setStorageTypeType("object");
            ssType.setDriverClassName("object");
            ssType.setIsSmiProvider(false);
            if (defaultSSL.get(object) != null) {
                ssType.setIsDefaultSsl(true);
            }
            if (defaultMDM.get(object) != null) {
                ssType.setIsDefaultMDM(true);
            }
            if (onlyMDM.get(object) != null) {
                ssType.setIsOnlyMDM(true);
            }
            if (elementManager.get(object) != null) {
                ssType.setIsElementMgr(true);
            }
            if (sslPortMap.get(object) != null) {
                ssType.setSslPort(sslPortMap.get(object));
            }
            if (nonSslPortMap.get(object) != null) {
                ssType.setNonSslPort(nonSslPortMap.get(object));
            }

            dbClient.createObject(ssType);
        }
    }

    public static void initializeStorageSystemTypes(DbClient dbClient) {
        log.info("Intializing storage system type Column Family for default storage drivers");

        initializeDefaultSSL();
        // When db and default list are not in sync, re-insert is required, and make sure we avoid duplicate entry
        createDbStorageTypeMap(dbClient);

        defaultMDM = new HashMap<String, Boolean>();
        defaultMDM.put(SCALEIO, true);
        defaultMDM.put(SCALEIOAPI, true);

        onlyMDM = new HashMap<String, Boolean>();
        onlyMDM.put(SCALEIOAPI, true);

        elementManager = new HashMap<String, Boolean>();
        elementManager.put(SCALEIO, true);

        // Name of Array and its Display Name mapping
        nameDisplayNameMap = initializeDisplayName();
        // SSL port
        initializeSSLPort();
        // Storage Array/Provider port
        initializeNonSslPort();

        // Insert File Arrays
        insertFileArrays(dbClient);
        // Insert File Providers
        insertFileProviders(dbClient);

        // Insert Block Arrays
        insertBlockArrays(dbClient);
        // Insert Block Providers
        insertBlockProviders(dbClient);

        // Insert Object Arrays
        insertObjectArrays(dbClient);

        log.info("Default drivers initialization done....");
    }
}
