/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.volumecontroller.RPProtectionRecommendation;
import com.emc.storageos.volumecontroller.RPRecommendation;

public class RecoverPointSchedulerTest extends Assert {

    protected static StoragePool pool1 = null;
    protected static StoragePool pool2 = null;
    protected static StoragePool pool3 = null;
    protected static StoragePool pool4 = null;
    protected static StoragePool poolA = null;
    protected static StoragePool poolB = null;
    protected static StoragePool poolC = null;
    protected static StoragePool poolD = null;
    protected static VirtualArray nh1 = null;
    protected static VirtualArray nh2 = null;
    private static final Logger logger = LoggerFactory
            .getLogger(RecoverPointSchedulerTest.class);

    @BeforeClass
    public static synchronized void setup() {
        if (pool1 == null) {
            pool1 = new StoragePool();
            pool1.setId(URI.create("pool1"));
            pool1.setLabel("Pool1");
        }

        if (pool2 == null) {
            pool2 = new StoragePool();
            pool2.setId(URI.create("pool2"));
            pool2.setLabel("Pool2");
        }

        if (pool3 == null) {
            pool3 = new StoragePool();
            pool3.setId(URI.create("pool3"));
            pool3.setLabel("Pool3");
        }

        if (pool4 == null) {
            pool4 = new StoragePool();
            pool4.setId(URI.create("pool4"));
            pool4.setLabel("Pool4");
        }

        if (poolA == null) {
            poolA = new StoragePool();
            poolA.setId(URI.create("poolA"));
            poolA.setLabel("PoolA");
        }

        if (poolB == null) {
            poolB = new StoragePool();
            poolB.setId(URI.create("poolB"));
            poolB.setLabel("PoolB");
        }

        if (poolC == null) {
            poolC = new StoragePool();
            poolC.setId(URI.create("poolC"));
            poolC.setLabel("PoolC");
        }

        if (poolD == null) {
            poolD = new StoragePool();
            poolD.setId(URI.create("poolD"));
            poolD.setLabel("PoolD");
        }

        if (nh1 == null) {
            nh1 = new VirtualArray();
            nh1.setId(URI.create("NH1URI"));
            nh1.setLabel("nh1");
        }

        if (nh2 == null) {
            nh2 = new VirtualArray();
            nh2.setId(URI.create("NH2URI"));
            nh2.setLabel("nh2");
        }
    }

    private ProtectionSystem buildProtectionSystemWithCapacity() {
        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URI.create("PSURI_CAPACITY"));

        // set the stats
        ps.setCgCapacity(312L);
        ps.setCgCount(12L);
        ps.setLabel("rp_protection_system_capacity");

        Map<String, String> siteVolCapacity = new HashMap<String, String>();
        siteVolCapacity.put("1", "512");
        siteVolCapacity.put("2", "512");

        Map<String, String> siteVolCount = new HashMap<String, String>();
        siteVolCount.put("1", "100");
        siteVolCount.put("2", "100");

        ps.setSiteVolumeCapacity(new StringMap(siteVolCapacity));
        ps.setSiteVolumeCount(new StringMap(siteVolCount));

        return ps;
    }

    private ProtectionSystem buildProtectionSystemWithNoCapacitySite1() {
        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URI.create("PSURI_NOCAPACITYSITE1"));

        // set the stats
        ps.setCgCapacity(312L);
        ps.setCgCount(12L);
        ps.setLabel("rp_protection_system_no_capacity_site1");

        Map<String, String> siteVolCapacity = new HashMap<String, String>();
        siteVolCapacity.put("1", "512");
        siteVolCapacity.put("2", "512");

        Map<String, String> siteVolCount = new HashMap<String, String>();
        siteVolCount.put("1", "512");
        siteVolCount.put("2", "100");

        ps.setSiteVolumeCapacity(new StringMap(siteVolCapacity));
        ps.setSiteVolumeCount(new StringMap(siteVolCount));

        return ps;
    }

    private ProtectionSystem buildProtectionSystemWithNoCapacitySite2() {
        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URI.create("PSURI_NOCAPACITYSITE2"));

        // set the stats
        ps.setCgCapacity(312L);
        ps.setCgCount(12L);
        ps.setLabel("rp_protection_system_no_capacity_site2");

        Map<String, String> siteVolCapacity = new HashMap<String, String>();
        siteVolCapacity.put("1", "512");
        siteVolCapacity.put("2", "512");

        Map<String, String> siteVolCount = new HashMap<String, String>();
        siteVolCount.put("1", "100");
        siteVolCount.put("2", "512");

        ps.setSiteVolumeCapacity(new StringMap(siteVolCapacity));
        ps.setSiteVolumeCount(new StringMap(siteVolCount));

        return ps;
    }

    private ProtectionSystem buildProtectionSystemWithNoCapacity() {
        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URI.create("PSURI_NOCAPACITY"));

        // set the stats
        ps.setCgCapacity(312L);
        ps.setCgCount(12L);
        ps.setLabel("rp_protection_system_no_capacity");

        Map<String, String> siteVolCapacity = new HashMap<String, String>();
        siteVolCapacity.put("1", "512");
        siteVolCapacity.put("2", "512");

        Map<String, String> siteVolCount = new HashMap<String, String>();
        siteVolCount.put("1", "512");
        siteVolCount.put("2", "512");

        ps.setSiteVolumeCapacity(new StringMap(siteVolCapacity));
        ps.setSiteVolumeCount(new StringMap(siteVolCount));

        return ps;
    }

    private ProtectionSystem buildProtectionSystemNoCGAvailability() {
        ProtectionSystem ps = new ProtectionSystem();
        ps.setId(URI.create("PSURI_NOCG"));

        // set the stats
        ps.setCgCapacity(312L);
        ps.setCgCount(312L);
        ps.setLabel("rp_protection_system_no_cg_capacity");

        Map<String, String> siteVolCapacity = new HashMap<String, String>();
        siteVolCapacity.put("1", "512");
        siteVolCapacity.put("2", "512");

		Map<String, String> siteVolCount = new HashMap<String, String>();
		siteVolCount.put("1", "512");
		siteVolCount.put("2", "512");
		
		ps.setSiteVolumeCapacity(new StringMap(siteVolCapacity));
		ps.setSiteVolumeCount(new StringMap(siteVolCount));
		
		return ps;
	}
	
	public void fillRecommendationObject(RPProtectionRecommendation rec, ProtectionSystem ps, String sourceInternalSiteName, String destInternalSiteName, 
					VirtualArray sourceVarray, VirtualArray destVarray, StoragePool sourceStoragePool, StoragePool destStoragePool, int resourceCount) {
		rec.setProtectionDevice(ps.getId());
		
		//fill the source
		RPRecommendation sourceRec = new RPRecommendation();
		sourceRec.setInternalSiteName(sourceInternalSiteName);
		sourceRec.setSourceStoragePool(sourceStoragePool.getId());
        sourceRec.setResourceCount(resourceCount);
		rec.setResourceCount(resourceCount);
		
		//fill source journal
		RPRecommendation sourceJournalRec = new RPRecommendation();
		sourceJournalRec.setSourceStoragePool(sourceStoragePool.getId());
		sourceJournalRec.setInternalSiteName(sourceInternalSiteName);
		sourceJournalRec.setResourceCount(1);
		
		//fill target
		RPRecommendation targetRec = new RPRecommendation();
		targetRec.setInternalSiteName(destInternalSiteName);		
		targetRec.setSourceStoragePool(sourceStoragePool.getId());
		targetRec.setResourceCount(resourceCount);
		sourceRec.setTargetRecommendations(new ArrayList<RPRecommendation>());
		sourceRec.getTargetRecommendations().add(targetRec);
		
		//fill targetJournal
		RPRecommendation targetJournalRec = new RPRecommendation();
		targetJournalRec.setSourceStoragePool(destStoragePool.getId());
		targetJournalRec.setInternalSiteName(destInternalSiteName);
		targetJournalRec.setResourceCount(1);
				
		//populate the protection recommendation object with all the recommendation
		rec.setSourceRecommendations(new ArrayList<RPRecommendation>());
		rec.getSourceRecommendations().add(sourceRec);
		rec.setSourceJournalRecommendation(sourceJournalRec);
		rec.setTargetJournalRecommendations(new ArrayList<RPRecommendation>());
		rec.getTargetJournalRecommendations().add(targetJournalRec);			
	}

    @Test
    public void fireProtectionPlacementRulesCDPValidTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "1", nh1, nh1, pool1, pool1, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "1", nh1, nh1, pool1, pool2, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "1", nh1, nh1, pool2, pool1, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "1", nh1, nh1, pool2, pool2, 1);

        assertTrue(invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCDPNoCGAvailability() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = this.buildProtectionSystemNoCGAvailability();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "1", nh1, nh1, pool1, pool1, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "1", nh1, nh1, pool1, pool2, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "1", nh1, nh1, pool2, pool1, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "1", nh1, nh1, pool2, pool2, 1);

        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCDPMixedProtectionSystems() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps1 = this.buildProtectionSystemWithCapacity();
        ProtectionSystem ps2 = this.buildProtectionSystemNoCGAvailability();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps1, "1", "1", nh1, nh1, pool1, pool1, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps2, "1", "1", nh2, nh2, poolA, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps2, "1", "1", nh2, nh2, poolA, poolA, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps2, "1", "1", nh2, nh2, poolB, poolB, 1);

        assertTrue(invokeFireProtectionPlacementRules(ps1, ppm1, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps2, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps2, ppm3, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps2, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCDPNoCapacity() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = this.buildProtectionSystemWithNoCapacity();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "1", nh1, nh1, pool1, pool1, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "1", nh1, nh1, pool1, pool2, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "1", nh1, nh1, pool2, pool1, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "1", nh1, nh1, pool2, pool2, 1);

        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCRRValidCapacityTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 1);

        assertTrue(invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCRRSite2NoCapacityTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        // set the remote site vol capacity to 1 less than full.
        ps.getSiteVolumeCount().remove("2");
        ps.getSiteVolumeCount().put("2", "511");

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 1);

        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCRRSite1NoCapacityTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        // set the remote site vol capacity to 1 less than full.
        ps.getSiteVolumeCount().remove("1");
        ps.getSiteVolumeCount().put("1", "512");

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 1);

        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCLRValidTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 1);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 1);

        assertTrue(invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm3, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm4, 1));
    }

    @Test
    public void fireProtectionPlacementRulesCLRSite1NoCapacityTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        // set the remote site vol capacity to 1 less than full.
        ps.getSiteVolumeCount().remove("1");
        ps.getSiteVolumeCount().put("1", "510");

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 2);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 4);

        assertTrue(invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 2));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 4));
    }

    @Test
    public void fireProtectionPlacementRulesCLRSite1NoCapacityMultiVolumeTest() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, SecurityException, NoSuchMethodException {
        ProtectionSystem ps = buildProtectionSystemWithCapacity();

        // set the remote site vol capacity to 1 less than full.
        ps.getSiteVolumeCount().remove("1");
        ps.getSiteVolumeCount().put("1", "510");

        RPProtectionRecommendation ppm1 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm1, ps, "1", "2", nh1, nh2, pool1, poolA, 1);

        RPProtectionRecommendation ppm2 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm2, ps, "1", "2", nh1, nh2, pool1, poolB, 1);

        RPProtectionRecommendation ppm3 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm3, ps, "1", "2", nh1, nh2, pool2, poolA, 2);

        RPProtectionRecommendation ppm4 = new RPProtectionRecommendation();
        fillRecommendationObject(ppm4, ps, "1", "2", nh1, nh2, pool3, poolB, 4);

        assertTrue(invokeFireProtectionPlacementRules(ps, ppm1, 1));
        assertTrue(invokeFireProtectionPlacementRules(ps, ppm2, 1));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm3, 2));
        assertTrue(!invokeFireProtectionPlacementRules(ps, ppm4, 4));
    }

    private boolean invokeFireProtectionPlacementRules(
            ProtectionSystem protectionSystem, RPProtectionRecommendation rec, Integer resourceCount) {
        RecoverPointScheduler scheduler = new RecoverPointScheduler();
        boolean toRet = false;
        try {
            Class c = Class.forName("com.emc.storageos.api.service.impl.placement.RecoverPointScheduler");
            Method method = c.getDeclaredMethod("fireProtectionPlacementRules", new Class[] { ProtectionSystem.class,
                    RPProtectionRecommendation.class, Integer.class });
            method.setAccessible(true);
            Object ret = method.invoke(scheduler, new Object[] { protectionSystem, rec, resourceCount });
            Boolean returnBool = (Boolean) ret;
            toRet = returnBool.booleanValue();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        return toRet;
    }
}
