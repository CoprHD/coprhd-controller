/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import org.junit.Assert;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ExportGroupTypeConverter;
import com.emc.storageos.db.common.DbSchemaScannerInterceptor;
import com.emc.storageos.db.server.upgrade.DbMigrationTestBase;
import com.emc.storageos.db.server.DbsvcTestBase;

/**
 * Test proper conversion of the type field for ExportGroup
 */
public class ExportGroupTypeMigrationTest extends DbSimpleMigrationTestBase {
	private static final String OLD_TYPE_VALUE = "Exclusive";

	@BeforeClass
	public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new ExportGroupTypeConverter());
        }});

        DbsvcTestBase.setup();
	}

	@Override
	protected String getSourceVersion() {
		return "1.0";
	}

	@Override
	protected String getTargetVersion() {
		return "1.1";
	}

	@Override
	protected void prepareData() throws Exception {
		// make sure no existing export groups, so we can use label 
		deleteExportGroups();	
		
		// key - export group type string to be tested, value - expected
		// ExportGroupType
		Map<String, ExportGroupType> typeMap = new HashMap<String, ExportGroupType>();
		typeMap.put(OLD_TYPE_VALUE, ExportGroupType.Initiator);
		for (ExportGroupType type : ExportGroupType.values()) {
			typeMap.put(type.name(), type);
		}

		for (Entry<String, ExportGroupType> entry : typeMap.entrySet()) {
			ExportGroup exportGroup = new ExportGroup();
			exportGroup.setId(URIUtil.createId(ExportGroup.class));
			String type = entry.getKey();
			exportGroup.setType(type);
			exportGroup.setLabel(entry.getValue().name());
			System.out.println("creating ExportGroup " + exportGroup.getId().toString());
			_dbClient.createObject(exportGroup);
		}		
	}

	@Override
	protected void verifyResults() throws Exception {
		List<URI> exportGroupURIs = _dbClient.queryByType(ExportGroup.class,
				false);
		Iterator<ExportGroup> exportGroupsIter = _dbClient
				.queryIterativeObjects(ExportGroup.class, exportGroupURIs);
		while (exportGroupsIter.hasNext()) {
			ExportGroup exportGroup = exportGroupsIter.next();
			System.out.println("verifying ExportGroup " + exportGroup.getId().toString());
			Assert.assertEquals(exportGroup.getLabel(), exportGroup.getType());
		}
		
		deleteExportGroups();
	}
	
	private void deleteExportGroups() {
		List<URI> exportGroupURIs = _dbClient.queryByType(ExportGroup.class,
				false);
		Iterator<ExportGroup> exportGroupsIter = _dbClient.queryIterativeObjects(ExportGroup.class,
				exportGroupURIs);
		List<ExportGroup> exportGroups = new ArrayList<ExportGroup>();
		while (exportGroupsIter.hasNext()) {
			// add to list to be deleted
			exportGroups.add(exportGroupsIter.next());
		}

		// delete all objects in the list
		_dbClient.removeObject(exportGroups
				.toArray(new ExportGroup[exportGroups.size()]));
	}
	
    // set dbClient from outside, so the class can be used in stand alone test
    public void setDbClient(DbClientImpl dbClient) {
            _dbClient = dbClient;
    }	
}
