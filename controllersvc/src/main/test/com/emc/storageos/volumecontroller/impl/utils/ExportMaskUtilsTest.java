/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy;

public class ExportMaskUtilsTest {

	@Test
	public void testExportMaskUtils() {
		//TODO add code to make this work 
		ExportGroup egp = new ExportGroup();
		ExportMask e1 = new ExportMask();
		e1.setLabel("e1");
		ExportMask e2 = new ExportMask();
		e2.setLabel("e2");
		ExportMask e3 = new ExportMask();
		e3.setLabel("e3");
		ExportMask e4 = new ExportMask();
		e4.setLabel("e4");
		ExportMask e5 = new ExportMask();
		e5.setLabel("e5");
		
		StringMap e1vols = new StringMap();
		e1vols.put("k1", "v1");
		e1vols.put("k2", "v2");
		e1vols.put("k3", "v3");
		e1.setExistingVolumes(e1vols);

		StringMap e2vols = new StringMap();
		e2vols.put("k1", "v1");
		e2.setExistingVolumes(e2vols);

		StringMap e3vols = new StringMap();
		e3vols.put("k1", "v1");
		e3vols.put("k2", "v2");
		e3.setExistingVolumes(e3vols);

		StringMap e4vols = new StringMap();
		e4.setExistingVolumes(e4vols);
		
		StringMap e5vols = new StringMap();
		e5vols.put("k1", "v1");
		e5vols.put("k2", "v2");
		e5vols.put("k3", "v3");
		e5vols.put("k4", "v1");
		e5vols.put("k5", "v2");
		e5vols.put("k6", "v3");
		e5.setExistingVolumes(e5vols);

		List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
		sortedMasks.add(e1);
		sortedMasks.add(e2);
		sortedMasks.add(e3);
		sortedMasks.add(e4);
		sortedMasks.add(e5);
		
		ExportMaskPolicy policy1 = new ExportMaskPolicy();
         
		Map<ExportMask, ExportMaskPolicy> maskPolicyMap = new HashMap<ExportMask, ExportMaskPolicy>();
		maskPolicyMap.put(e1,policy1);
		maskPolicyMap.put(e2,policy1);
		maskPolicyMap.put(e3,policy1);
		maskPolicyMap.put(e4,policy1);
		maskPolicyMap.put(e5,policy1);
		
		sortedMasks = ExportMaskUtils.sortMasksByEligibility(maskPolicyMap, egp);
		
		Assert.assertEquals(sortedMasks.get(0).getLabel(), "e4");
		Assert.assertEquals(sortedMasks.get(1).getLabel(), "e2");
		Assert.assertEquals(sortedMasks.get(2).getLabel(), "e3");
		Assert.assertEquals(sortedMasks.get(3).getLabel(), "e1");
		Assert.assertEquals(sortedMasks.get(4).getLabel(), "e5");
	}

	@Test
	public void testExportMaskUtilsCSG() {
		//TODO add code to make this work 
        ExportGroup egp = new ExportGroup();
		ExportMask e1 = new ExportMask();
		e1.setLabel("e1");
		ExportMask e2 = new ExportMask();
		e2.setLabel("e2");
		ExportMask e3 = new ExportMask();
		e3.setLabel("e3");
		ExportMask e4 = new ExportMask();
		e4.setLabel("e4");
		ExportMask e5 = new ExportMask();
		e5.setLabel("e5");
		
		StringMap e1vols = new StringMap();
		e1vols.put("k1", "v1");
		e1vols.put("k2", "v2");
		e1vols.put("k3", "v3");
		e1.setExistingVolumes(e1vols);

		StringMap e2vols = new StringMap();
		e2vols.put("k1", "v1");
		e2.setExistingVolumes(e2vols);

		StringMap e3vols = new StringMap();
		e3vols.put("k1", "v1");
		e3vols.put("k2", "v2");
		e3.setExistingVolumes(e3vols);

		StringMap e4vols = new StringMap();
		e4.setExistingVolumes(e4vols);
		
		StringMap e5vols = new StringMap();
		e5vols.put("k1", "v1");
		e5vols.put("k2", "v2");
		e5vols.put("k3", "v3");
		e5vols.put("k4", "v1");
		e5vols.put("k5", "v2");
		e5vols.put("k6", "v3");
		e5.setExistingVolumes(e5vols);

		List<ExportMask> sortedMasks = new ArrayList<ExportMask>();
		sortedMasks.add(e1);
		sortedMasks.add(e2);
		sortedMasks.add(e3);
		sortedMasks.add(e4);
		sortedMasks.add(e5);
		
		ExportMaskPolicy policy1simple = new ExportMaskPolicy();
		policy1simple.simpleMask = true;
		ExportMaskPolicy policy2notsimple = new ExportMaskPolicy();
		policy2notsimple.simpleMask = false;		

		Map<ExportMask, ExportMaskPolicy> maskPolicyMap = new HashMap<ExportMask, ExportMaskPolicy>();
		maskPolicyMap.put(e1,policy1simple);
		maskPolicyMap.put(e2,policy1simple);
		maskPolicyMap.put(e3,policy2notsimple);
		maskPolicyMap.put(e4,policy1simple);
		maskPolicyMap.put(e5,policy2notsimple);
		
		sortedMasks = ExportMaskUtils.sortMasksByEligibility(maskPolicyMap, egp);
		
		Assert.assertEquals(sortedMasks.get(0).getLabel(), "e3");
		Assert.assertEquals(sortedMasks.get(1).getLabel(), "e5");
		Assert.assertEquals(sortedMasks.get(2).getLabel(), "e4");
		Assert.assertEquals(sortedMasks.get(3).getLabel(), "e2");
		Assert.assertEquals(sortedMasks.get(4).getLabel(), "e1");
	}
}
