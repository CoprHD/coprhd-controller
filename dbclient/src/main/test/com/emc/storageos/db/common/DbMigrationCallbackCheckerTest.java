/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.common.DbMigrationCallbackChecker.MigrationCallbackDiff;

public class DbMigrationCallbackCheckerTest {
	
	private Map<String, List<String>> baselineVersionedCallbacks;
	private Map<String, List<String>> curentVersionedCallbacks;
	private static final String version1_0Callback1 = "MigrationCallback1.0.1";
	private static final String version1_0Callback2 = "MigrationCallback1.0.2";
	private static final String version1_0Callback3 = "MigrationCallback1.0.3";
	private static final String version2_0Callback1 = "MigrationCallback2.0.1";
	private static final String version2_0Callback2 = "MigrationCallback2.0.2";
	private static final String version2_0Callback3 = "MigrationCallback2.0.3";
	private static final String version1_0 = "1.0";
	private static final String version2_0 = "2.0";
	
	@Before
	public void setup(){
		this.baselineVersionedCallbacks = new HashMap<String, List<String>>();
		List<String> version1_0Callbacks = new ArrayList<String>();
		version1_0Callbacks.add(version1_0Callback1);
		version1_0Callbacks.add(version1_0Callback2);
		version1_0Callbacks.add(version1_0Callback3);
		
		List<String> version2_0Callbacks = new ArrayList<String>();
		version2_0Callbacks.add(version2_0Callback1);
		version2_0Callbacks.add(version2_0Callback2);
		this.baselineVersionedCallbacks.put(version1_0, version1_0Callbacks);
		this.baselineVersionedCallbacks.put(version2_0, version2_0Callbacks);
		
		this.curentVersionedCallbacks = deepCloneVersionedCallbacks(this.baselineVersionedCallbacks);
	}

	@Test
	public void shouldNoDiffWithSameBaselineAndCurrentCallbacks(){
		DbMigrationCallbackChecker callbackChecker = new DbMigrationCallbackChecker(this.baselineVersionedCallbacks, this.curentVersionedCallbacks);
		Assert.assertFalse(callbackChecker.hasDiff());
	}
	
	@Test
	public void shouldNoDiffWithDifferentCallbackOrder(){
		Map<String, List<String>> current = deepCloneVersionedCallbacks(this.curentVersionedCallbacks);
		current.get(version1_0).remove(version1_0Callback1);
		current.get(version1_0).add(version1_0Callback1);
		DbMigrationCallbackChecker callbackChecker = new DbMigrationCallbackChecker(this.baselineVersionedCallbacks, current);
		Assert.assertFalse(callbackChecker.hasDiff());
	}
	
	@Test
	public void shouldHasDiffWithCurrentAddNewCallbacks(){
		String newCallback = version2_0Callback3;
		Map<String, List<String>> current = deepCloneVersionedCallbacks(this.curentVersionedCallbacks);
		current.get(version2_0).add(newCallback);
		DbMigrationCallbackChecker callbackChecker = new DbMigrationCallbackChecker(this.baselineVersionedCallbacks, current);
		Assert.assertTrue(callbackChecker.hasDiff());
		
		Map<String, List<MigrationCallbackDiff>> versionedDiffs = callbackChecker.getDiff();
		Assert.assertTrue(versionedDiffs.containsKey(version2_0));
		List<MigrationCallbackDiff> diffs = versionedDiffs.get(version2_0);
		Assert.assertEquals(1, diffs.size());
		Assert.assertEquals("add", diffs.get(0).getAction());
		Assert.assertEquals(newCallback, diffs.get(0).getCallback());
	}
	
	@Test
	public void shouldHasDiffWithCurrentRemoveNewCallbacks(){
		String newCallback = version2_0Callback2;
		Map<String, List<String>> current = deepCloneVersionedCallbacks(this.curentVersionedCallbacks);
		current.get(version2_0).remove(newCallback);
		DbMigrationCallbackChecker callbackChecker = new DbMigrationCallbackChecker(this.baselineVersionedCallbacks, current);
		Assert.assertTrue(callbackChecker.hasDiff());
		
		Map<String, List<MigrationCallbackDiff>> versionedDiffs = callbackChecker.getDiff();
		Assert.assertTrue(versionedDiffs.containsKey(version2_0));
		List<MigrationCallbackDiff> diffs = versionedDiffs.get(version2_0);
		Assert.assertEquals(1, diffs.size());
		Assert.assertEquals("remove", diffs.get(0).getAction());
		Assert.assertEquals(newCallback, diffs.get(0).getCallback());
	}
	
	private Map<String, List<String>> deepCloneVersionedCallbacks(final Map<String, List<String>> versionedCallbacks){
		Map<String, List<String>> newVersionedCallbacks = new HashMap<String, List<String>>();
		
		for(Map.Entry<String, List<String>> callbackEntry : versionedCallbacks.entrySet()){
			List<String> cs = new ArrayList<String>();
			for(String callback : callbackEntry.getValue()){
				cs.add(callback);
			}
			newVersionedCallbacks.put(callbackEntry.getKey(), cs);
		}
		
		return newVersionedCallbacks;
	}
	
}
