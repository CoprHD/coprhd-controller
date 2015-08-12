package com.emc.storageos.api.system.vplex;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.api.service.utils.ApisvcTestBase;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.UnManagedVolumeRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.search.SearchBuilder;

public class VPlexIngestionTest extends ApisvcTestBase {
	
	Properties properties = new Properties();
	VPlexTestUtil util = null;
	
	private static final String PROJECT = "project";
	private static final String LOCAL_VPOOL = "localVpool";
	private static final String LOCAL_VARRAY = "localVarray";
	private static final String CONSISTENCY_GROUP = "cg";
	private static final String VIPR_IP = "viprIP";
	private static final String USER_NAME = "userName";
	private static final String PASS_WORD = "password!";
	private static final String ARRAY1_GUID = "array1GUID";
	private static final String ARRAY2_GUID = "array2GUID";
	private static final String VPLEX_GUID = "vplexGUID";
	
	protected ViPRCoreClient client = null;
	@Before
	// This starts the apisvc. We assume all other required services are running except the apisvc.
	// Note the apisvc is only started once for the entire test run.
	public void setup() {
		startApisvc();
		// These properties should be externalized.
		properties.setProperty(PROJECT, "vplexIngest");
		// simulator
		properties.setProperty(LOCAL_VPOOL, "sim11Distributed");
		properties.setProperty(LOCAL_VARRAY,  "site11");
		properties.setProperty(CONSISTENCY_GROUP, "null");
		properties.setProperty(ARRAY1_GUID, "SYMMETRIX+999595867618");
		properties.setProperty(ARRAY2_GUID, "SYMMETRIX+999334388821");
		properties.setProperty(VPLEX_GUID, "VPLEX+FNM00130900300:FNM00130900301");
		// hardware
//		properties.setProperty(LOCAL_VPOOL, "vplex1L");
//		properties.setProperty(LOCAL_VARRAY,  "site1");
//		properties.setProperty(CONSISTENCY_GROUP, "null");
//		properties.setProperty(ARRAY1_GUID, "CLARIION+APM00140844981");
//		properties.setProperty(ARRAY2_GUID, "SYMMETRIX+000195701573");
//		properties.setProperty(ARRAY2_GUID, "null");
//		properties.setProperty(VPLEX_GUID, "VVPLEX+FNM00114300288:FNM00114600001");
		properties.setProperty(VIPR_IP, "richa031.lss.emc.com");
		properties.setProperty(USER_NAME, "root");
		properties.setProperty(PASS_WORD, "ChangeMe1!");
		client = getViprClient(properties.getProperty(VIPR_IP), 
				properties.getProperty(USER_NAME), properties.getProperty(PASS_WORD));
		util = new VPlexTestUtil(client, dbClient, log);
	}
	
	@After
	public void teardown() {
		client.auth().logout();
	}
	
	@Test
	// This tests a vplex local volume create, followed by an ingestion.
	public void test1() {
		// Create the volume to be ingested
		String volumeName = "vpingest" + getTimeInt();
		printLog("Creating virtual volume: " + volumeName);
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(LOCAL_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(LOCAL_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		URI cg = util.getURIFromLabel(BlockConsistencyGroup.class, properties.getProperty(CONSISTENCY_GROUP));
		List<URI> volumeURIs = util.createVolume(volumeName, "1GB", 1,  vpool, varray, project, cg);
		// Look up the volume
		VolumeRestRep volume = client.blockVolumes().get(volumeURIs.get(0));
		String nativeId = volume.getNativeId();
		printLog("Virtual volume: " + nativeId);
		
		// Inventory only delete it.
		util.deleteVolumes(volumeURIs, true);
		
		// Do discovery of unmanaged volumes / exports
		printLog("Discovering " + properties.getProperty(ARRAY1_GUID));
		URI storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(ARRAY1_GUID));
		util.discoverStorageSystem(storageSystemURI, true);
		if (!properties.getProperty(ARRAY2_GUID).equals("null")) {
			printLog("Discovering " + properties.getProperty(ARRAY2_GUID));
			storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(ARRAY2_GUID));
			util.discoverStorageSystem(storageSystemURI, true);
		}
		printLog("Discovering " + properties.getProperty(VPLEX_GUID));
		storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));
		util.discoverStorageSystem(storageSystemURI, true);
		
		// Look up the unmanaged volume by nativeId.
		List<UnManagedVolumeRestRep> uvols = client.unmanagedVolumes().getByStorageSystem(storageSystemURI);
		List<URI> uvolId = new ArrayList<URI>();
		for (UnManagedVolumeRestRep uvol : uvols) {
			if (uvol.getNativeGuid().equals(nativeId)) {
				printLog("UnManagedVolume: " + uvol.getNativeGuid());
				uvolId.add(uvol.getId());
			}
		}
		Assert.assertFalse("Unmanaged volume id null", uvolId.isEmpty());
		
		// Do ingestion of virtual volume.
		util.ingestUnManagedVolume(uvolId, project, varray, vpool);
	}
	
	public void test2a() {
		printLog("test2a");
	}
	
	public void test3a() {
		printLog("test3a");
	}
	
	public void test3b() {
		printLog("test3b");
		try {
			Thread.sleep(3600000);
		} catch (InterruptedException ex) {
			log.info("Interrupted");;
		}
	}
	
	private String getTimeInt() {
		Long time = System.currentTimeMillis();
		time = time % 3600000;   // get time within the current hour
		return Long.toHexString(time);
	}
	
	private void printLog(String s) {
		System.out.println(s);
		log.info(s);;
	}

}
