/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
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
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexExportMaskInitiatorsAndVolumesMigration;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Unit test for VplexExportMaskInitiatorsAndVolumesMigration procedure
 */
public class VplexExportMaskInitiatorsAndVolumesMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(VplexExportMaskInitiatorsAndVolumesMigrationTest.class);

    private final String ExportMaskCase1 = "ExportMaskCase1";
    private final String ExportMaskCase2 = "ExportMaskCase2";
    private final String ExportMaskCase3 = "ExportMaskCase3";
    private final String ExportMaskCase4 = "ExportMaskCase4";

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new VplexExportMaskInitiatorsAndVolumesMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareExportMaskVolumeAndInitiatorDataCase1();
        prepareExportMaskVolumeAndInitiatorDataCase2();
        prepareExportMaskInitiatorDataCase3();
        prepareExportMaskInitiatorDataCase4();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyExportMasksResults();
    }

    /*
     * Test Case 1:
     * Here ExportMask createdBySystem is true
     * 
     * 1.Adds volumes to userAddedVolumes when there are no volumes at
     * all in the user added volumes i:e userAddedVolumes == null
     * 
     * 2.Adds initiators to userAddedInitiators when there are no initiators
     * at all in the userAddedInitiators i:e userAddedInitiators == null
     */
    private void prepareExportMaskVolumeAndInitiatorDataCase1() throws Exception {

        StringMap volumes = new StringMap();
        try {
            Volume volume1 = new Volume();
            volume1.setId(URIUtil.createId(Volume.class));
            volume1.setLabel("volume1");
            volume1.setWWN("60000970000195701505533031343341");
            _dbClient.createObject(volume1);
            log.info("create VPLEX volume1 with URI: " + volume1.getId());
            volumes.put(volume1.getId().toString(), "0");

            Volume volume2 = new Volume();
            volume2.setId(URIUtil.createId(Volume.class));
            volume2.setLabel("volume2");
            volume2.setWWN("60000970000195701505533031343342");
            _dbClient.createObject(volume2);
            log.info("create VPLEX volume2 with URI: " + volume2.getId());
            volumes.put(volume2.getId().toString(), "1");

            List<URI> initiatorsURIs = new ArrayList<URI>();

            Initiator initiator1 = new Initiator();
            initiator1.setId(URIUtil.createId(Initiator.class));
            initiator1.setInitiatorPort("10:00:00:E0:7E:EE:EE:01");
            _dbClient.createObject(initiator1);
            log.info("create initiator1 with URI: " + initiator1.getId());
            initiatorsURIs.add(initiator1.getId());

            Initiator initiator2 = new Initiator();
            initiator2.setId(URIUtil.createId(Initiator.class));
            initiator2.setInitiatorPort("10:00:00:E0:7E:EE:EE:02");
            _dbClient.createObject(initiator2);
            log.info("create initiator1 with URI: " + initiator2.getId());
            initiatorsURIs.add(initiator2.getId());

            StorageSystem storageSystem = new StorageSystem();
            storageSystem.setId(URIUtil.createId(StorageSystem.class));
            storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
            storageSystem.setLabel("VplexStorageSystem");
            _dbClient.createObject(storageSystem);
            log.info("created VPLEX storageSystem with URI: " + storageSystem.getId());

            ExportMask exportMask = new ExportMask();
            exportMask.setId(URIUtil.createId(ExportMask.class));
            exportMask.setStorageDevice(storageSystem.getId());
            exportMask.setVolumes(volumes);
            exportMask.setInitiators(StringSetUtil.uriListToStringSet(initiatorsURIs));
            exportMask.setCreatedBySystem(true);
            exportMask.setMaskName(ExportMaskCase1);
            _dbClient.createObject(exportMask);
            log.info("created VPLEX exportmask ExportMaskCase1 with URI: " + exportMask.getId());
        } catch (Exception e) {
            log.error("Error is" + e);
            throw (e);
        }
    }

    /*
     * Test Case 2:
     * Here ExportMask createdBySystem is true
     * 
     * 1.Adds volumes to userAddedVolumes when there are some volumes
     * in the user added volumes but not all from the volumes list
     * i:e userAddedVolumes != null
     * 
     * 2.Adds initiators to userAddedInitiators when there are some initiators
     * in the userAddedInitiators but not all from the initiators list
     * i:e userAddedInitiators != null , it adds which are not already in the
     * userAddedInitiators
     */
    private void prepareExportMaskVolumeAndInitiatorDataCase2() throws Exception {

        StringMap volumes = new StringMap();
        try {
            Volume volume21 = new Volume();
            volume21.setId(URIUtil.createId(Volume.class));
            volume21.setLabel("volume21");
            volume21.setWWN("60000970000195701505533031343321");
            _dbClient.createObject(volume21);
            log.info("create VPLEX volume21 with URI: " + volume21.getId());
            volumes.put(volume21.getId().toString(), "0");

            Volume volume22 = new Volume();
            volume22.setId(URIUtil.createId(Volume.class));
            volume22.setLabel("volume22");
            // volume22.setWWN("60000970000195701505533031343322");
            _dbClient.createObject(volume22);
            log.info("create VPLEX volume22 with URI: " + volume22.getId());
            volumes.put(volume22.getId().toString(), "1");

            List<URI> initiatorsURIs = new ArrayList<URI>();

            Initiator initiator1 = new Initiator();
            initiator1.setId(URIUtil.createId(Initiator.class));
            initiator1.setInitiatorPort("10:00:00:E0:7E:EE:EE:21");
            _dbClient.createObject(initiator1);
            log.info("create initiator1 with URI: " + initiator1.getId());
            initiatorsURIs.add(initiator1.getId());

            Initiator initiator2 = new Initiator();
            initiator2.setId(URIUtil.createId(Initiator.class));
            initiator2.setInitiatorPort("10:00:00:E0:7E:EE:EE:22");
            _dbClient.createObject(initiator2);
            log.info("create initiator1 with URI: " + initiator2.getId());
            initiatorsURIs.add(initiator2.getId());

            StorageSystem storageSystem = new StorageSystem();
            storageSystem.setId(URIUtil.createId(StorageSystem.class));
            storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
            storageSystem.setLabel("VplexStorageSystem");
            _dbClient.createObject(storageSystem);
            log.info("created VPLEX storageSystem with URI: " + storageSystem.getId());

            ExportMask exportMask = new ExportMask();
            exportMask.setId(URIUtil.createId(ExportMask.class));
            exportMask.setStorageDevice(storageSystem.getId());
            exportMask.setVolumes(volumes);
            exportMask.setInitiators(StringSetUtil.uriListToStringSet(initiatorsURIs));
            exportMask.addToUserCreatedInitiators(initiator1);
            exportMask.addToUserCreatedVolumes(volume21);
            exportMask.setCreatedBySystem(true);
            exportMask.setMaskName(ExportMaskCase2);
            _dbClient.createObject(exportMask);
            log.info("created VPLEX exportmask ExportMaskCase2 with URI: " + exportMask.getId());
        } catch (Exception e) {
            log.error("Error is" + e);
            throw (e);
        }
    }

    /*
     * Test Case 3:
     * Here ExportMask createdBySystem is false
     * 
     * 1.First adds initiators to userAddedInitiators when there are no initiators
     * at all in the userAddedInitiators from the initiators list
     * i:e userAddedInitiators == null
     * 
     * 2.Adds exitingInitiators to the initiators list if we can find Initiator
     * object in database by the exitingInitiar wwpn.
     */
    private void prepareExportMaskInitiatorDataCase3() throws Exception {

        try {
            StorageSystem storageSystem = new StorageSystem();
            storageSystem.setId(URIUtil.createId(StorageSystem.class));
            storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
            storageSystem.setLabel("VplexStorageSystem");
            _dbClient.createObject(storageSystem);
            log.info("created VPLEX storageSystem with URI: " + storageSystem.getId());

            List<URI> initiatorsURIs = new ArrayList<URI>();
            StringSet existingInitiators = new StringSet();

            Initiator initiator1 = new Initiator();
            initiator1.setId(URIUtil.createId(Initiator.class));
            initiator1.setInitiatorPort("10:00:00:E0:7E:EE:EE:31");
            _dbClient.createObject(initiator1);
            log.info("create initiator1 with URI: " + initiator1.getId());
            initiatorsURIs.add(initiator1.getId());

            Initiator initiator2 = new Initiator();
            initiator2.setId(URIUtil.createId(Initiator.class));
            initiator2.setInitiatorPort("10:00:00:E0:7E:EE:EE:32");
            _dbClient.createObject(initiator2);
            log.info("create initiator2 with URI: " + initiator2.getId());
            initiatorsURIs.add(initiator2.getId());

            Initiator initiator3 = new Initiator();
            initiator3.setId(URIUtil.createId(Initiator.class));
            initiator3.setInitiatorPort("10:00:00:E0:7E:EE:EE:33");
            _dbClient.createObject(initiator3);
            log.info("create initiator3 with URI: " + initiator3.getId());
            existingInitiators.add("100000E07EEEEE33");
            ;
            Initiator initiator4 = new Initiator();
            initiator4.setId(URIUtil.createId(Initiator.class));
            initiator4.setInitiatorPort("10:00:00:E0:7E:EE:EE:34");
            _dbClient.createObject(initiator4);
            log.info("create initiator4 with URI: " + initiator4.getId());
            existingInitiators.add("100000E07EEEEE34");
            existingInitiators.add("100000E07EEEEE35");

            ExportMask exportMask = new ExportMask();
            exportMask.setId(URIUtil.createId(ExportMask.class));
            exportMask.setStorageDevice(storageSystem.getId());
            exportMask.setInitiators(StringSetUtil.uriListToStringSet(initiatorsURIs));
            exportMask.setExistingInitiators(existingInitiators);
            exportMask.setCreatedBySystem(false);
            exportMask.setMaskName(ExportMaskCase3);
            _dbClient.createObject(exportMask);
            log.info("created VPLEX exportmask ExportMaskCase3 with URI: " + exportMask.getId());
        } catch (Exception e) {
            log.error("Error is" + e);
            throw (e);
        }
    }

    /*
     * Test Case 4:
     * Here ExportMask createdBySystem is false
     * 
     * This is the case all the initiators are existing initiators
     * and initiators == null
     * Adds exitingInitiators to the initiators list if we can find Initiator
     * object in database by the exitingInitiator wwpn.
     */
    private void prepareExportMaskInitiatorDataCase4() throws Exception {

        try {
            StorageSystem storageSystem = new StorageSystem();
            storageSystem.setId(URIUtil.createId(StorageSystem.class));
            storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
            storageSystem.setLabel("VplexStorageSystem");
            _dbClient.createObject(storageSystem);
            log.info("created VPLEX storageSystem with URI: " + storageSystem.getId());

            StringSet existingInitiators = new StringSet();

            Initiator initiator3 = new Initiator();
            initiator3.setId(URIUtil.createId(Initiator.class));
            initiator3.setInitiatorPort("10:00:00:E0:7E:EE:EE:43");
            _dbClient.createObject(initiator3);
            log.info("created initiator3 with URI: " + initiator3.getId());
            existingInitiators.add("100000E07EEEEE43");
            ;
            Initiator initiator4 = new Initiator();
            initiator4.setId(URIUtil.createId(Initiator.class));
            initiator4.setInitiatorPort("10:00:00:E0:7E:EE:EE:44");
            _dbClient.createObject(initiator4);
            log.info("created initiator4 with URI: " + initiator4.getId());
            existingInitiators.add("100000E07EEEEE44");
            existingInitiators.add("100000E07EEEEE45");

            ExportMask exportMask = new ExportMask();
            exportMask.setId(URIUtil.createId(ExportMask.class));
            exportMask.setStorageDevice(storageSystem.getId());
            exportMask.setExistingInitiators(existingInitiators);
            exportMask.setCreatedBySystem(false);
            exportMask.setMaskName(ExportMaskCase4);
            _dbClient.createObject(exportMask);
            log.info("created VPLEX exportmask ExportMaskCase4 with URI: " + exportMask.getId());
        } catch (Exception e) {
            log.error("Error is" + e);
            throw (e);
        }
    }

    private void verifyExportMasksResults() throws Exception {
        log.info("Verifying results of VPLEX exportMask migration test.");
        List<URI> exportMaskUris = _dbClient.queryByType(ExportMask.class, true);
        log.info("exportMaskUris:" + exportMaskUris);
        Iterator<ExportMask> exportMasks = _dbClient.queryIterativeObjects(ExportMask.class, exportMaskUris, true);

        while (exportMasks.hasNext()) {
            ExportMask exportMask = exportMasks.next();

            if (exportMask.getMaskName().equals(ExportMaskCase1)) {

                log.info("looking a VPLEX export mask with id: " + exportMask.getId());

                Assert.assertEquals("Volumes and userAddedVolumes "
                        + "should be equal on VPLEX export mask.",
                        exportMask.getVolumes().size(), exportMask.getUserAddedVolumes().size());
                log.info("Everything looks good for ExportMaskCase1 : Volumes are {} and userAddedVolumes are {}",
                        exportMask.getVolumes(), exportMask.getUserAddedVolumes());

                Assert.assertEquals("Initiators and userAddedInitiators "
                        + "should be equal on VPLEX export mask.",
                        exportMask.getInitiators().size(), exportMask.getUserAddedInitiators().size());
                log.info("Everything looks good for ExportMaskCase1 : Initiators are {} and userAddedInitiators are {}",
                        exportMask.getInitiators(), exportMask.getUserAddedInitiators());
            }

            if (exportMask.getMaskName().equals(ExportMaskCase2)) {

                log.info("looking a VPLEX export mask with id: " + exportMask.getId());

                Assert.assertEquals("Volumes and userAddedVolumes "
                        + "should be equal on VPLEX export mask.",
                        exportMask.getVolumes().size(), exportMask.getUserAddedVolumes().size());
                log.info("Everything looks good for ExportMaskCase2 : Volumes are {} and userAddedVolumes are {}",
                        exportMask.getVolumes(), exportMask.getUserAddedVolumes());
                Assert.assertEquals("Initiators and userAddedInitiators "
                        + "should be equal on VPLEX export mask.",
                        exportMask.getInitiators().size(), exportMask.getUserAddedInitiators().size());
                log.info("Everything looks good for ExportMaskCase2 : Initiators are {} and userAddedInitiators are {}",
                        exportMask.getInitiators(), exportMask.getUserAddedInitiators());
            }

            if (exportMask.getMaskName().equals(ExportMaskCase3)) {

                log.info("looking a VPLEX export mask ExportMaskCase3 with id: " + exportMask.getId());

                Assert.assertEquals("Initiators should be 4",
                        exportMask.getInitiators().size(), 4);
                Assert.assertEquals("UserAddedInitiators should be 2",
                        exportMask.getUserAddedInitiators().size(), 2);
                log.info("Initiators size :" + exportMask.getInitiators().size());
                log.info("UserAddedInitiators size :" + exportMask.getUserAddedInitiators().size());
                log.info("Everything looks good for ExportMaskCase3 Initiators : Initiators are {} and userAddedInitiators are {}",
                        exportMask.getInitiators(), exportMask.getUserAddedInitiators());
            }

            if (exportMask.getMaskName().equals(ExportMaskCase4)) {

                log.info("looking a VPLEX export mask ExportMaskCase4 with id: " + exportMask.getId());

                Assert.assertEquals("Initiators should be 2",
                        exportMask.getInitiators().size(), 2);
                log.info("Initiators size :" + exportMask.getInitiators().size());
                log.info("Everything looks good for ExportMaskCase4 Initiators : Initiators are {} are ",
                        exportMask.getInitiators());
            }
        }
    }
}
