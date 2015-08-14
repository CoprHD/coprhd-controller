package com.emc.storageos.api.system.vplex;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.internal.MethodSorter;
import org.junit.runners.MethodSorters;
import org.springframework.context.annotation.DependsOn;

import com.emc.storageos.api.service.utils.ApisvcTestBase;
import com.emc.storageos.api.system.ApiSystemTestUtil;
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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VPlexIngestionTest extends ApisvcTestBase {
	Properties properties = new Properties();
	ApiSystemTestUtil util = null;
	private static Map<String, String> args = new HashMap<String, String>();
	
	private static final String PROJECT = "project";
	private static final String LOCAL_VPOOL = "localVpool";
	private static final String LOCAL_VARRAY = "localVarray";
	private static final String DIST_VPOOL = "distributedVpool";
	private static final String DIST_VARRAY = "distributedVarray";
	private static final String CONSISTENCY_GROUP = "consistencyGroup";
	private static final String VIPR_IP = "viprIP";
	private static final String USER_NAME = "userName";
	private static final String PASS_WORD = "passWord";
	private static final String ARRAY1_GUID = "array1GUID";
	private static final String ARRAY2_GUID = "array2GUID";
	private static final String VPLEX_GUID = "vplexGUID";
	private static final String CONFIG_FILE = "vplex-ingestion-test.properties";
	
	protected ViPRCoreClient client = null;
	private static boolean firstTime = true;
	
	@Before
	// This starts the apisvc. We assume all other required services are running except the apisvc.
	// Note the apisvc is only started once for the entire test run.
	public void setup() {
		if (firstTime) {
			firstTime = false;
			try {
				FileInputStream configFile = new FileInputStream(CONFIG_FILE);
				properties.load(configFile);
				configFile.close();
				printLog(properties.toString());

			} catch (FileNotFoundException ex) {
				Assert.assertTrue("Cannot find configuration file: " +  CONFIG_FILE, false);
			} catch (IOException ex) {
				Assert.assertTrue("IO exception configuration file: " +  CONFIG_FILE, false);
			}
		}
		
		startApisvc();

		client = getViprClient(properties.getProperty(VIPR_IP), 
				properties.getProperty(USER_NAME), properties.getProperty(PASS_WORD));
		util = new ApiSystemTestUtil(client, dbClient, log);
	}
	
	@After
	public void teardown() {
		if (client != null) {
			client.auth().logout();
		}
	}
	
	@Test
	// Test1 creates a vplex locl volume, inventory deletes it, 
	// discovers unmanaged resources, and ingests volume.
	// test1a creates unmanaged  vplex local volume create. Ingestion handled in test1b.
	public void prepare1() {
		// Create the volume to be ingested
		start();
		String timeInt = getTimeInt();
		String volumeName = "vpingest" + timeInt;
		printLog("Creating virtual volume: " + volumeName);
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(LOCAL_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(LOCAL_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		String cgName =  properties.getProperty(CONSISTENCY_GROUP) + timeInt;
		URI cg = null;
		if (cgName != null) {
			cg = util.createConsistencyGroup(cgName, project);
		}
		List<URI> volumeURIs = util.createVolume(volumeName, "1GB", 1,  vpool, varray, project, cg);
		// Look up the volume
		VolumeRestRep volume = client.blockVolumes().get(volumeURIs.get(0));
		String nativeId = volume.getNativeId();
		args.put("test1NativeId", nativeId);
		printLog("Virtual volume: " + nativeId);
		stop("Test 1 virtual volume creation: " + volumeName);
		
		// Inventory only delete it.
		util.deleteVolumes(volumeURIs, true);
		
	}
	
	@Test
	// Test2 creates a vplex distributed volume, inventory deletes it, 
	// discovers unmanaged resources, and ingests volume.
	// test2a creates unmanaged  distributed local volume create. Ingestion handled in test2b.
	public void prepare2() {
		// Create the volume to be ingested
		start();
		String timeInt = getTimeInt();
		String volumeName = "vpingest" + timeInt;
		printLog("Creating virtual volume: " + volumeName);
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(DIST_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(DIST_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		String cgName =  properties.getProperty(CONSISTENCY_GROUP) + timeInt;
		URI cg = null;
		if (cgName != null) {
			cg = util.createConsistencyGroup(cgName, project);
		}
		List<URI> volumeURIs = util.createVolume(volumeName, "1GB", 1,  vpool, varray, project, cg);
		// Look up the volume
		VolumeRestRep volume = client.blockVolumes().get(volumeURIs.get(0));
		String nativeId = volume.getNativeId();
		args.put("test2NativeId", nativeId);
		printLog("Virtual volume: " + nativeId);
		stop("Test 2 virtual volume creation: " + volumeName);
		
		// Inventory only delete it.
		util.deleteVolumes(volumeURIs, true);
		
	}
	
	@Test
	public void prepare999() {
		// Do discovery of unmanaged volumes / exports
				URI storageSystemURI;
				if (!properties.getProperty(ARRAY1_GUID).equals("null")) {
					printLog("Discovering " + properties.getProperty(ARRAY1_GUID));
					start();
					storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(ARRAY1_GUID));
					util.discoverStorageSystem(storageSystemURI, true);
					stop(String.format("Discovery of %s", properties.getProperty(ARRAY1_GUID)));
				}
				if (!properties.getProperty(ARRAY2_GUID).equals("null")) {
					printLog("Discovering " + properties.getProperty(ARRAY2_GUID));
					start();
					storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(ARRAY2_GUID));
					util.discoverStorageSystem(storageSystemURI, true);
					stop(String.format("Discovery of %s", properties.getProperty(ARRAY2_GUID)));
				}
				printLog("Discovering " + properties.getProperty(VPLEX_GUID));
				start();
				storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));
				util.discoverStorageSystem(storageSystemURI, true);
				stop(String.format("Discovery of %s", properties.getProperty(VPLEX_GUID)));
		
	}
	
	@Test
	public void test1() {
		printLog("test1");
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(LOCAL_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(LOCAL_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		URI cg = util.getURIFromLabel(BlockConsistencyGroup.class, properties.getProperty(CONSISTENCY_GROUP));
		URI vplexURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));

		// Look up the unmanaged volume by nativeId.
		List<UnManagedVolumeRestRep> uvols = client.unmanagedVolumes().getByStorageSystem(vplexURI);
		List<URI> uvolId = new ArrayList<URI>();
		for (UnManagedVolumeRestRep uvol : uvols) {
			if (uvol.getNativeGuid().equals(args.get("test1NativeId"))) {
				printLog("UnManagedVolume: " + uvol.getNativeGuid());
				uvolId.add(uvol.getId());
			}
		}
		Assert.assertFalse("Unmanaged volume id null", uvolId.isEmpty());

		// Do ingestion of virtual volume.
		start();
		List<String> nativeGuids = util.ingestUnManagedVolume(uvolId, project, varray, vpool);
		stop("Test1 ingestion of volume: " + uvolId);

		// Lookup the volumes in the database.
		List<Volume> volumes = util.findVolumesByNativeGuid(vplexURI, nativeGuids);
		for (Volume vvol : volumes) {
			printLog(String.format("Volume %s %s %s", vvol.getLabel(), vvol.getNativeGuid(), vvol.getId()));
			Assert.assertNotNull("No associated volumes", vvol.getAssociatedVolumes());
			Assert.assertFalse("Associated volumes empty", vvol.getAssociatedVolumes().isEmpty());
			for (String assocVolume : vvol.getAssociatedVolumes()) {
				Volume bvol = dbClient.queryObject(Volume.class, URI.create(assocVolume));
				printLog(String.format("  Backend Volume %s %s %s", bvol.getLabel(), bvol.getNativeGuid(), bvol.getId()));
			}

		}
	}
	
	@Test
	public void test2() {
		printLog("test2");
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(DIST_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(DIST_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		URI cg = util.getURIFromLabel(BlockConsistencyGroup.class, properties.getProperty(CONSISTENCY_GROUP));
		URI vplexURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));

		// Look up the unmanaged volume by nativeId.
		List<UnManagedVolumeRestRep> uvols = client.unmanagedVolumes().getByStorageSystem(vplexURI);
		List<URI> uvolId = new ArrayList<URI>();
		for (UnManagedVolumeRestRep uvol : uvols) {
			if (uvol.getNativeGuid().equals(args.get("test2NativeId"))) {
				printLog("UnManagedVolume: " + uvol.getNativeGuid());
				uvolId.add(uvol.getId());
			}
		}
		Assert.assertFalse("Unmanaged volume id null", uvolId.isEmpty());

		// Do ingestion of virtual volume.
		start();
		List<String> nativeGuids = util.ingestUnManagedVolume(uvolId, project, varray, vpool);
		stop("Test2 ingestion of virtual volume: " + uvolId);

		// Lookup the volumes in the database.
		List<Volume> volumes = util.findVolumesByNativeGuid(vplexURI, nativeGuids);
		for (Volume vvol : volumes) {
			printLog(String.format("Volume %s %s %s", vvol.getLabel(), vvol.getNativeGuid(), vvol.getId()));
			Assert.assertNotNull("No associated volumes", vvol.getAssociatedVolumes());
			Assert.assertFalse("Associated volumes empty", vvol.getAssociatedVolumes().isEmpty());
			for (String assocVolume : vvol.getAssociatedVolumes()) {
				Volume bvol = dbClient.queryObject(Volume.class, URI.create(assocVolume));
				printLog(String.format("  Backend Volume %s %s %s", bvol.getLabel(), bvol.getNativeGuid(), bvol.getId()));
			}
		}
	}
	
	
	@Test
	public void test999() {
		printLog("test999");
		try {
			Thread.sleep(3600000);
		} catch (InterruptedException ex) {
			log.info("Interrupted");;
		}
	}
	
	private static Long startTime = 0L;
	private void start() {
		startTime = System.currentTimeMillis();
	}
	private void stop(String message) {
		Long time = System.currentTimeMillis() - startTime;
		startTime = 0L;
		printLog(String.format("%s time (seconds): %d", message, time / 1000));
	}
	
	/**
	 * Returns an integer based on time within the last hour.
	 * Used to generate random volume names for example.
	 * @return Millisecond time within the last hour.
	 */
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
