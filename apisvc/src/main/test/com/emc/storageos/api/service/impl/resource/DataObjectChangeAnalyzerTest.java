/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.emc.storageos.api.service.impl.resource.utils.DataObjectChangeAnalyzer;
import com.emc.storageos.api.service.impl.resource.utils.DataObjectChangeAnalyzer.Change;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class DataObjectChangeAnalyzerTest {
    
    private static void printChanges(Map<String, Change> changes) {
        System.out.println("**************** Changes: ***********************");
        for (String key : changes.keySet()) {
            Change change = changes.get(key);
            if (change._left == null) change._left = "<null>";
            if (change._right == null) change._right = "<null>";
            System.out.println(String.format("key = %s, left = %s, right = %s", 
               key, change._left.toString(), change._right.toString()));
        }
    }
    
    @Test
    public void analyzerTest() {
        VirtualPool cosa = new VirtualPool();
        VirtualPool cosb = new VirtualPool();
        cosa.setDriveType("drive-a");
        cosb.setDriveType("drive-b");
        cosa.setNumPaths(1);
        cosb.setNumPaths(2);
        Map<String, Change> changes = 
                DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[]{}, new String[] {}, new String[] {});
        printChanges(changes);
        assertEquals("drive-a", changes.get("driveType")._left);
        assertEquals("drive-b", changes.get("driveType")._right);
        assertEquals(new Integer(1), changes.get("numPaths")._left);
        assertEquals(new Integer(2), changes.get("numPaths")._right);
       
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] { "driveType"}, null, null);
        printChanges(changes);
        assertEquals("drive-a", changes.get("driveType")._left);
        assertEquals("drive-b", changes.get("driveType")._right);
        assertNull(changes.get("numPaths"));
       
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] { "driveType", "numPaths"}, null, null);
        printChanges(changes);
        assertEquals("drive-a", changes.get("driveType")._left);
        assertEquals("drive-b", changes.get("driveType")._right);
        assertEquals(new Integer(1), changes.get("numPaths")._left);
        assertEquals(new Integer(2), changes.get("numPaths")._right);
       
        cosb.setNumPaths(1);
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, null, new String[] { "driveType"}, null);
        assertTrue(changes.isEmpty());
        printChanges(changes);
        
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] { "numPaths"}, null, null);
        assertTrue(changes.isEmpty());
        printChanges(changes);
        
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] { "driveType"}, null, null);
        assertNotNull(changes.get("driveType"));
        printChanges(changes);
        
        Set<String> nha = new HashSet<String>();
        nha.add("nha");
        cosa.addVirtualArrays(nha);
        StringSetMap ssMapa = new StringSetMap();
        ssMapa.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, "systemType");
        cosa.addArrayInfoDetails(ssMapa);
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] {}, new String[] { "driveType"}, null);
        assertEquals("nha", changes.get("virtualArrays.nha")._left);
        assertNull(changes.get("virtualArrays.nha")._right);
        printChanges(changes);
        
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] {"virtualArrays"}, null, null);
        assertNull(changes.get("driveType"));
        assertEquals("nha", changes.get("virtualArrays.nha")._left);
        assertNull(changes.get("virtualArrays.nha")._right);
        printChanges(changes);
        
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, new String[] {"virtualArrays", "driveType"}, null, null);
        assertEquals("drive-a", changes.get("driveType")._left);
        assertEquals("drive-b", changes.get("driveType")._right);
        assertEquals("nha", changes.get("virtualArrays.nha")._left);
        assertNull(changes.get("virtualArrays.nha")._right);
        printChanges(changes);
       
        cosb.addVirtualArrays(nha);
        StringSetMap ssMapb = new StringSetMap();
        ssMapb.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, "systemType");
        cosb.addArrayInfoDetails(ssMapb);
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, null, new String[] { "driveType"}, null);
        assertTrue(changes.isEmpty());
        printChanges(changes);
        
        cosa.setArrayInfo(null);
        Set<String> nhb = new HashSet<String>();
        nhb.add("nhb");
        cosb.addVirtualArrays(nhb);
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, null, new String[] { "driveType"}, null);
        assertEquals("nhb", changes.get("virtualArrays.nhb")._right);
        assertEquals("[systemType]", changes.get("arrayInfo.system_type")._right.toString());
        printChanges(changes);
        nha.add("nhb");
        cosa.addVirtualArrays(nha);
        cosb.setArrayInfo(null);
        changes = DataObjectChangeAnalyzer.analyzeChanges(cosa, cosb, null, new String[] { "driveType"}, null);
        assertTrue(changes.isEmpty());
        printChanges(changes);
    }
   
}
