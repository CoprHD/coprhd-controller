package com.emc.storageos.api.system.recoverpoint;

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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

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

/**
 * NOTE: This test case is not complete. After talking to Tom, there's a couple of things that need
 * to be addressed:
 * 1. This test case requires all services to be running other than apisvc on the system running Eclipse.
 * Tom has been running Eclipse on Linux, where many of us run it on Windows/Mac.
 * 2. The test cases do not pre-create the vpools, varrays, projects, etc. So they are expected to already
 * be in the system. The test cases need create and delete these objects, much like the RP placement tests
 * do today.
 * 
 * This class runs tests on RecoverPoint ingestion.
 * 
 * See the superclass ApisvcTestBase for instructions on setting up the environment to run the test.
 * All these instructions should be followed. This will start the apisvc.
 * There is additional configuration for this test needed as follows:
 * 1. Copy the file rp-ingestion-test.properties from the directory containing this source
 * to the directory /opt/storageos/conf. Edit the version in /opt/storageos/conf to have appropriate
 * settings for your environment. Two environments are possible, the simulator based environment,
 * and a hardware based environment.
 * 2. To run this from Eclipse, right click on this source file, and select "Run As J-Unit". It won't run
 * until you modify the environmental settings as described in ApisvcTestBase, but then should start
 * the apisvc and run correctly. Note you must have the apisvc stopped when doing this, but all other
 * services should be running. This test effectively becomes part of the apisvc.
 * 
 * The general architecture of the tests are that there is a preparation phase for each test. (prepare1, prepare2, ...)
 * The preparation phase will create the artifacts (volumes, cgs, exports) on the managed resources (arrays, switches, RPs)
 * that will be ingested later. Then we delete them from the inventory so we have clean slate inside CoprHD.
 * 
 * Followed by a discovery of the unmanaged devices on all the arrays (prepare999),
 * followed by the actual ingestion tests (test1, test2, ...).
 * This is done because on real hardware, the discovery phase takes an inordinate amount of time (> 1 hour.)
 * Arguments are passed between the prepare steps and the test steps using the args static Map.
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RPIngestionTest extends ApisvcTestBase {
	Properties properties = new Properties();
	ApiSystemTestUtil util = null;
	private static Map<String, String> args = new HashMap<String, String>();
	
	// Fields that can appear in the configuration file.
	private static final String PROJECT = "project";
    private static final String RP_CDP_VPOOL = "rpCDPVpool";
	private static final String LOCAL_VARRAY = "localVarray";
    private static final String TARGET_VPOOL = "targetVpool";
	private static final String DIST_VPOOL = "distributedVpool";
	private static final String DIST_VARRAY = "distributedVarray";
	private static final String MIRROR_VPOOL = "mirrorVpool";
	private static final String MIRROR_VARRAY = "mirrorVarray";
	private static final String CONSISTENCY_GROUP = "consistencyGroup";
	private static final String VIPR_IP = "viprIP";
	private static final String USER_NAME = "userName";
	private static final String PASS_WORD = "passWord";
	private static final String ARRAY1_GUID = "array1GUID";
	private static final String ARRAY2_GUID = "array2GUID";
	private static final String ARRAY3_GUID = "array3GUID";
	private static final String VPLEX_GUID = "vplexGUID";
	// Name of the configuration file:
    private static final String CONFIG_FILE = "rp-ingestion-test.properties";
	
	protected ViPRCoreClient client = null;
	
	@Before
	// This starts the apisvc. We assume all other required services are running except the apisvc.
	// Note the apisvc is only started once for the entire test run.
	public void setup() {
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

		// This routine starts the apisvc the first time it is called.
		startApisvc();

		// A new client is setup each time a test runs.
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
	
    @Ignore
	@Test
    // Prepare1 creates a simple RP CDP volume set, then inventory deletes it.
    // SOURCE - 1gb local_varray, local_vpool
    // TARGET - 1gb local_varray, target_vpool
    // SOURCE_JOUNRAL - 10gb local_varray, local_vpool
    // TARGET_JOURNAL - 10gb local_varray, target_vpool
	public void prepare1() {
		// Create the volume to be ingested
		start();
		String timeInt = getTimeInt();
        String volumeName = "rpingest" + timeInt;
        printLog("Creating RP volume: " + volumeName);
        URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(RP_CDP_VPOOL));
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
        printLog("RP volume: " + nativeId);
        stop("Test 1 RP volume creation: " + volumeName);
		
		// Inventory only delete it.
		util.deleteVolumes(volumeURIs, true);
	}
	
	@Ignore
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
	
    @Ignore
	@Test
	// Test3 creates a vplex locl volume with mirror option, inventory deletes it, 
	// discovers unmanaged resources, and ingests volume, checking for mirror.
	// test3a creates unmanaged  vplex local volume create. Ingestion handled in test1b.
	public void prepare3() {
		// Create the volume to be ingested
		start();
		String timeInt = getTimeInt();
		String volumeName = "vpingest" + timeInt;
		printLog("Creating virtual volume: " + volumeName);
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(MIRROR_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(MIRROR_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		String cgName =  properties.getProperty(CONSISTENCY_GROUP) + timeInt;
		URI cg = null;
		if (cgName != null) {
			cg = util.createConsistencyGroup(cgName, project);
		}
		List<URI> volumeURIs = util.createVolume(volumeName, "1GB", 1,  vpool, varray, project, null);
		// Look up the volume
		VolumeRestRep volume = client.blockVolumes().get(volumeURIs.get(0));
		String nativeId = volume.getNativeId();
		args.put("test3NativeId", nativeId);
		printLog("Virtual volume: " + nativeId);
		stop("Test 3 virtual volume creation: " + volumeName);
		
		// Attach the mirror
		List<URI> mirrorURIs = util.attachContinuousCopy(volumeURIs.get(0), volumeName + "-mirror");
		printLog("Mirror volume: " + mirrorURIs.get(0).toString());
		
		// Inventory only delete it.
		// N.B. There is currently a problem... the VplexMirror object is not currently being deleted
		// by inventory delete.
		util.deleteVolumes(volumeURIs, true);
		
	}
	
    @Ignore
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
				if (!properties.getProperty(ARRAY3_GUID).equals("null")) {
					printLog("Discovering " + properties.getProperty(ARRAY3_GUID));
					start();
					storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(ARRAY3_GUID));
					util.discoverStorageSystem(storageSystemURI, true);
					stop(String.format("Discovery of %s", properties.getProperty(ARRAY3_GUID)));
				}
				printLog("Discovering " + properties.getProperty(VPLEX_GUID));
				start();
				storageSystemURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));
				util.discoverStorageSystem(storageSystemURI, true);
				stop(String.format("Discovery of %s", properties.getProperty(VPLEX_GUID)));
		
	}
	
	@Ignore
	@Test
	public void test1() {
		printLog("test1");
        URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(RP_CDP_VPOOL));
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
	
	@Ignore
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
	
    @Ignore
	@Test
	public void test3() {
		printLog("test3");
		URI vpool = util.getURIFromLabel(VirtualPool.class, properties.getProperty(MIRROR_VPOOL));
		URI varray = util.getURIFromLabel(VirtualArray.class, properties.getProperty(MIRROR_VARRAY));
		URI project = util.getURIFromLabel(Project.class, properties.getProperty(PROJECT));
		URI cg = util.getURIFromLabel(BlockConsistencyGroup.class, properties.getProperty(CONSISTENCY_GROUP));
		URI vplexURI = util.getURIFromLabel(StorageSystem.class, properties.getProperty(VPLEX_GUID));

		// Look up the unmanaged volume by nativeId.
		List<UnManagedVolumeRestRep> uvols = client.unmanagedVolumes().getByStorageSystem(vplexURI);
		List<URI> uvolId = new ArrayList<URI>();
		for (UnManagedVolumeRestRep uvol : uvols) {
			if (uvol.getNativeGuid().equals(args.get("test3NativeId"))) {
				printLog("UnManagedVolume: " + uvol.getNativeGuid());
				uvolId.add(uvol.getId());
			}
		}
		Assert.assertFalse("Unmanaged volume id null", uvolId.isEmpty());

		// Do ingestion of virtual volume.
		start();
		List<String> nativeGuids = util.ingestUnManagedVolume(uvolId, project, varray, vpool);
		stop("Test3 ingestion of volume: " + uvolId);

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
	
	@Ignore
	@Test
	/**
	 * Causes the apisvc to hang around for an hour after testing completes.
	 */
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
