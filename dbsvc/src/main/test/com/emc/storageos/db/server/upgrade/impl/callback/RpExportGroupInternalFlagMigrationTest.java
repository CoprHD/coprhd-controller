/**
 *  Copyright (c) 2013-2014 EMC Corporation
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
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.RpExportGroupInternalFlagMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new DataObject.internalFlags field
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 *   of the database, using the DbSchemaScannerInterceptor
 *   you supply to hide your new field or column family
 *   when generating the "before" schema. 
 * - Your implementation of prepareData() is called, allowing
 *   you to use the internal _dbClient reference to create any 
 *   needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 *   the interceptor this time), so the full "after" schema
 *   is available.
 * - The dbsvc detects the diffs in the schema and executes the
 *   migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 *   allow you to confirm that the migration of your prepared
 *   data went as expected.
 * 
 */
public class RpExportGroupInternalFlagMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(RpExportGroupInternalFlagMigrationTest.class);
    
    // Used for migrations tests related to RP Export Groups.
    private static List<URI> rpTestExportGroupURIs = new ArrayList<URI>();
    
    // Used for migrations tests related to RP Initiators.
    private static List<URI> rpTestInitiatorURIs = new ArrayList<URI>();
    
    // Used for migrations tests related to RP Initiators and adding them to the Export Groups.
    private static List<Initiator> rpTestInitiators = new ArrayList<Initiator>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new RpExportGroupInternalFlagMigration());
        }});

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.2";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareInitiatorData();
        prepareExportGroupData();         
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyInitiatorResults();
        verifyExportGroupResults();        
    }
    
    /**
     * Prepares the data for volume tests.
     * 
     * @throws Exception When an error occurs preparing the volume data.
     */
    private void prepareInitiatorData() throws Exception {
        String currentLabel = "RPInitiator1";  

        Initiator initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHostName(currentLabel);
        initiator.setInitiatorPort("PORT");
        initiator.setInitiatorNode("NODE");
        initiator.setProtocol("FC");
        initiator.setIsManualCreation(false);
        _dbClient.createObject(initiator);
        rpTestInitiatorURIs.add(initiator.getId());
        rpTestInitiators.add(initiator);
        
        currentLabel = "RPInitiator2";
        
        initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHostName(currentLabel);
        initiator.setInitiatorPort("PORT");
        initiator.setInitiatorNode("NODE");
        initiator.setProtocol("FC");
        initiator.setIsManualCreation(false);
        _dbClient.createObject(initiator);
        rpTestInitiatorURIs.add(initiator.getId());
        rpTestInitiators.add(initiator);
       
        currentLabel = "RegularInitiator1";
        
        initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHostName(NullColumnValueGetter.getNullStr());
        initiator.setInitiatorPort("PORT");
        initiator.setInitiatorNode("NODE");
        initiator.setProtocol("FC");
        initiator.setIsManualCreation(false);
        _dbClient.createObject(initiator);
        rpTestInitiatorURIs.add(initiator.getId());
        rpTestInitiators.add(initiator);
        
        currentLabel = "RegularInitiator2";
        
        initiator = new Initiator();
        initiator.setId(URIUtil.createId(Initiator.class));
        initiator.setHostName(NullColumnValueGetter.getNullStr());
        initiator.setInitiatorPort("PORT");
        initiator.setInitiatorNode("NODE");
        initiator.setProtocol("FC");
        initiator.setIsManualCreation(false);
        _dbClient.createObject(initiator);
        rpTestInitiatorURIs.add(initiator.getId());
        rpTestInitiators.add(initiator);
        
        // Make sure our test data made it into the database as expected
        List<URI> initiatorURIs = _dbClient.queryByType(Initiator.class, false);
        int count = 0;       
        for (@SuppressWarnings("unused") URI ignore :initiatorURIs) {
            count++;
        }
        Assert.assertTrue("Expected 4 Initiators, found only " + count, count == 4);  
    }
    
    /**
     * Verifies the migration results for volumes.
     * 
     * @throws Exception When an error occurs verifying the volume migration
     *         results.
     */
    private void verifyInitiatorResults() throws Exception {
        log.info("Verifying migration of Initiators for Flag.RECOVERPOINT.");
        List<URI> initiatorURIs = _dbClient.queryByType(Initiator.class, false);
        int count = 0;
        Iterator<Initiator> initiatorsIter =
                _dbClient.queryIterativeObjects(Initiator.class, initiatorURIs);
        while (initiatorsIter.hasNext()) {
            Initiator initiator = initiatorsIter.next();
            count++;
            if (initiator.getHostName().contains("RP")) {                
                Assert.assertTrue("RECOVERPOINT flag should be set on rp initiator", 
                        initiator.checkInternalFlags(Flag.RECOVERPOINT));                               
            } else if (initiator.getHostName().contains("null")) {
                Assert.assertFalse("RECOVERPOINT flag should not be set on regular initiator", 
                        initiator.checkInternalFlags(Flag.RECOVERPOINT));                                                
            }
        }
        Assert.assertTrue("Should still have 4 initiators after migration, not " + count, count == 4);       
    }

    private void prepareExportGroupData() throws Exception {
        String currentLabel = "RPExport1";
        
        ExportGroup exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
        exportGroup.setLabel(currentLabel);        
        exportGroup.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));                                              
        exportGroup.setGeneratedName(currentLabel);
        exportGroup.addInitiator(rpTestInitiators.get(0));
        _dbClient.createObject(exportGroup);
        rpTestExportGroupURIs.add(exportGroup.getId());
        
        currentLabel = "RPExport2";
        
        exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
        exportGroup.setLabel(currentLabel);
        exportGroup.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));                                              
        exportGroup.setGeneratedName(currentLabel);
        exportGroup.addInitiator(rpTestInitiators.get(1));
        _dbClient.createObject(exportGroup);
        rpTestExportGroupURIs.add(exportGroup.getId());
        
        currentLabel = "RegularExport1";
        
        exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
        exportGroup.setLabel(currentLabel);
        exportGroup.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));                                              
        exportGroup.setGeneratedName(currentLabel);
        exportGroup.addInitiator(rpTestInitiators.get(2));
        _dbClient.createObject(exportGroup);
        rpTestExportGroupURIs.add(exportGroup.getId());
        
        currentLabel = "RegularExport2";
        
        exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE);
        exportGroup.setLabel(currentLabel);
        exportGroup.setProject(new NamedURI(URI.create("urn:" + currentLabel), currentLabel));                                              
        exportGroup.setGeneratedName(currentLabel);
        exportGroup.addInitiator(rpTestInitiators.get(3));
        _dbClient.createObject(exportGroup);
        rpTestExportGroupURIs.add(exportGroup.getId());
        
        // Make sure our test data made it into the database as expected
        List<URI> exportGroupURIs = _dbClient.queryByType(ExportGroup.class, false);
        int count = 0;       
        for (@SuppressWarnings("unused") URI ignore :exportGroupURIs) {
            count++;
        }
        Assert.assertTrue("Expected 4 Export Groups, found only " + count, count == 4);         
    }

    private void verifyExportGroupResults() throws Exception {
        log.info("Verifying migration of ExportGroups for Flag.RECOVERPOINT.");
        List<URI> exportGroupURIs = _dbClient.queryByType(ExportGroup.class, false);
        int count = 0;
        Iterator<ExportGroup> exportGroupsIter =
                _dbClient.queryIterativeObjects(ExportGroup.class, exportGroupURIs);
        while (exportGroupsIter.hasNext()) {
            ExportGroup exportGroup = exportGroupsIter.next();
            count++;
            if (exportGroup.getLabel().contains("RP")) {                
                Assert.assertTrue("RECOVERPOINT flag should be set on rp export group", 
                        exportGroup.checkInternalFlags(Flag.RECOVERPOINT));                               
            } else if (exportGroup.getLabel().contains("Regular")) {
                Assert.assertFalse("RECOVERPOINT flag should not be set on regular export group", 
                        exportGroup.checkInternalFlags(Flag.RECOVERPOINT));                                                
            }
        }
        Assert.assertTrue("Should still have 4 initiators after migration, not " + count, count == 4);     
    }
}
