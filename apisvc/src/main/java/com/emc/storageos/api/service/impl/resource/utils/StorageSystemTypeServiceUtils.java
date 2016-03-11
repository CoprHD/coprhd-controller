package com.emc.storageos.api.service.impl.resource.utils;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystemType;

public class StorageSystemTypeServiceUtils {

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

	private static void initializeDefaultSSL(HashMap<String, Boolean> dsslMap) {
		dsslMap.put(VNX_BLOCK, true);
		dsslMap.put(VMAX, true);
		dsslMap.put(SCALEIOAPI, true);
		dsslMap.put(VPLEX, true);
		dsslMap.put(VNX_FILE, true);
		dsslMap.put(VNXe, true);
		dsslMap.put(IBMXIV, true);
		// return dsslMap;
	}

	private static void initializeDisplayName(HashMap<String, String> nameDisplayNameMap) {
		nameDisplayNameMap.put(VNX_FILE, "EMC VNX File");
		nameDisplayNameMap.put(ISILON, "EMC Isilon");
		nameDisplayNameMap.put(NETAPP, "NetApp 7-mode");
		nameDisplayNameMap.put(NETAPPC, "NetApp Cluster-mode");
		nameDisplayNameMap.put(SCALEIOAPI, "ScaleIO Gateway");
		nameDisplayNameMap.put(VNX_BLOCK, "EMC VNX Block");
		nameDisplayNameMap.put(VNXe, "EMC VNXe");
		nameDisplayNameMap.put(VMAX, "Storage Provider for EMC VMAX or VNX Block");
		nameDisplayNameMap.put(HITACHI, "Storage Provider for Hitachi storage systems");
		nameDisplayNameMap.put(VPLEX, "Storage Provider for EMC VPLEX");
		nameDisplayNameMap.put(OPENSTACK, "Storage Provider for Third-party block storage systems");
		nameDisplayNameMap.put(SCALEIO, "Block Storage Powered by ScaleIO");
		nameDisplayNameMap.put(DATA_DOMAIN, "Storage Provider for Data Domain Management Center");
		nameDisplayNameMap.put(IBMXIV, "Storage Provider for IBM XIV");
		nameDisplayNameMap.put(XTREMIO, "Storage Provider for EMC XtremIO");
		nameDisplayNameMap.put(ECS, "EMC Elastic Cloud Storage");
	}

	private static void initializeSSLPort(HashMap<String, String> sslPortmap) {
		sslPortmap.put(VNX_FILE, "5989");
		sslPortmap.put(SCALEIOAPI, "443");
		sslPortmap.put(VNX_BLOCK, "5989");
		sslPortmap.put(VMAX, "5989");
		sslPortmap.put(HITACHI, "2001");
		sslPortmap.put(VPLEX, "443");
		sslPortmap.put(OPENSTACK, "22");
		sslPortmap.put(SCALEIO, "22");
		sslPortmap.put(DATA_DOMAIN, "3009");
		sslPortmap.put(IBMXIV, "5989");
		sslPortmap.put(XTREMIO, "443");
		sslPortmap.put(ECS, "4443");
		sslPortmap.put("vnxfile_smis", "5989");
	}

	private static void initializeNonSslPort(HashMap<String, String> nonSslPortmap) {
		nonSslPortmap.put("hicommand", "2001");
		nonSslPortmap.put("smis", "5988");
		nonSslPortmap.put("vplex", "443");
		nonSslPortmap.put("cinder", "22");
		nonSslPortmap.put("scaleio", "22");
		nonSslPortmap.put("scaleioapi", "80");
		nonSslPortmap.put("ddmc", "3009");
		nonSslPortmap.put("ibmxiv", "5989");
		nonSslPortmap.put("xtremio", "443");
		nonSslPortmap.put("vnxblock", "5988");
		nonSslPortmap.put("vmax", "5988");
		nonSslPortmap.put("ibmxiv", "5989");
		nonSslPortmap.put("isilon", "8080");
		nonSslPortmap.put("netapp", "443");
		nonSslPortmap.put("netappc", "443");
		nonSslPortmap.put("vplex", "443");
		nonSslPortmap.put("xtremio", "443");
		nonSslPortmap.put("vnxfile", "443");
		nonSslPortmap.put("vnxe", "443");
		nonSslPortmap.put("vnxfile_smis", "5988");
		nonSslPortmap.put("hds", "2001");
	}

	public static void InitializeStorageSystemTypes(DbClient _dbClient) {
		// Default SSL providers
		HashMap<String, Boolean> defaultSSL = new HashMap<String, Boolean>();
		initializeDefaultSSL(defaultSSL);

		// MDM_DEFAULT_OPTIONS = StringOption.options(new String[] { SCALEIO,
		// SCALEIOAPI });
		HashMap<String, Boolean> defaultMDM = new HashMap<String, Boolean>();
		defaultMDM.put(SCALEIO, true);
		defaultMDM.put(SCALEIOAPI, true);

		// MDM_ONLY_OPTIONS = StringOption.options(new String[] {SCALEIOAPI});
		HashMap<String, Boolean> onlyMDM = new HashMap<String, Boolean>();
		onlyMDM.put(SCALEIOAPI, true);

		// ELEMENT_MANAGER_OPTIONS = StringOption.options(new String[] { SCALEIO
		// });
		HashMap<String, Boolean> elementManager = new HashMap<String, Boolean>();
		elementManager.put(SCALEIO, true);

		// Name of Array and its Display Name mapping
		HashMap<String, String> nameDisplayNameMap = new HashMap<String, String>();
		initializeDisplayName(nameDisplayNameMap);

		HashMap<String, String> sslPortMap = new HashMap<String, String>();
		initializeSSLPort(sslPortMap);

		HashMap<String, String> nonSslPortMap = new HashMap<String, String>();
		initializeNonSslPort(nonSslPortMap);

		for (String file : storageArrayFile) {
			StorageSystemType ssType = new StorageSystemType();
			URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
			ssType.setId(ssTyeUri);
			ssType.setStorageTypeId(ssTyeUri.toString());
			ssType.setStorageTypeName(file);
			ssType.setStorageTypeDispName(nameDisplayNameMap.get(file));
			ssType.setStorageTypeType("file");
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

			_dbClient.createObject(ssType);
		}

		for (String file : storageProviderFile) {
			StorageSystemType ssType = new StorageSystemType();
			URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
			ssType.setId(ssTyeUri);
			ssType.setStorageTypeId(ssTyeUri.toString());
			ssType.setStorageTypeName(file);
			ssType.setStorageTypeDispName(nameDisplayNameMap.get(file));
			ssType.setStorageTypeType("file");
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
			_dbClient.createObject(ssType);
		}

		for (String block : storageArrayBlock) {
			StorageSystemType ssType = new StorageSystemType();
			URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
			ssType.setId(ssTyeUri);
			ssType.setStorageTypeId(ssTyeUri.toString());
			ssType.setStorageTypeName(block);
			ssType.setStorageTypeDispName(nameDisplayNameMap.get(block));
			ssType.setStorageTypeType("block");
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

			_dbClient.createObject(ssType);
		}

		for (String block : storageProviderBlock) {
			StorageSystemType ssType = new StorageSystemType();
			URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
			ssType.setId(ssTyeUri);
			ssType.setStorageTypeId(ssTyeUri.toString());
			ssType.setStorageTypeName(block);
			ssType.setStorageTypeDispName(nameDisplayNameMap.get(block));
			ssType.setStorageTypeType("block");
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

			_dbClient.createObject(ssType);
		}

		for (String object : storageArrayObject) {
			StorageSystemType ssType = new StorageSystemType();
			URI ssTyeUri = URIUtil.createId(StorageSystemType.class);
			ssType.setId(ssTyeUri);
			ssType.setStorageTypeId(ssTyeUri.toString());
			ssType.setStorageTypeName(object);
			ssType.setStorageTypeDispName(nameDisplayNameMap.get(object));
			ssType.setStorageTypeType("object");
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

			_dbClient.createObject(ssType);
		}
	}

}
