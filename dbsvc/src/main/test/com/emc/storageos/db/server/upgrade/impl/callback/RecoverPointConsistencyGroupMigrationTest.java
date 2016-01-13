/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByConsistencyGroup;

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
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ProtectionSetToBlockConsistencyGroupMigration;
import com.emc.storageos.db.client.upgrade.callbacks.RpBlockSnapshotConsistencyGroupMigration;
import com.emc.storageos.db.client.upgrade.callbacks.VolumeRpJournalMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new DataObject.internalFlags field
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 * of the database, using the DbSchemaScannerInterceptor
 * you supply to hide your new field or column family
 * when generating the "before" schema.
 * - Your implementation of prepareData() is called, allowing
 * you to use the internal _dbClient reference to create any
 * needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 * the interceptor this time), so the full "after" schema
 * is available.
 * - The dbsvc detects the diffs in the schema and executes the
 * migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 * allow you to confirm that the migration of your prepared
 * data went as expected.
 * 
 * This class tests the following migration callback classes:
 * - BlockSnapshotConsistencyGroupMigration
 * - ProtectionSetToBlockConsistencyGroupMigration
 * - VolumeRpJournalMigration
 */
public class RecoverPointConsistencyGroupMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(RecoverPointConsistencyGroupMigrationTest.class);

    // Used for migrations tests related to RP BlockSnapshots.
    private static List<URI> rpTestBlockSnapshotURIs = new ArrayList<URI>();

    // Used for migrations tests related to RP ProtectionSets.
    private static List<URI> rpTestProtectionSetURIs = new ArrayList<URI>();

    // Used for migrations tests related to RP volumes.
    private static List<URI> rpTestVolumeURIs = new ArrayList<URI>();

    private static String RP_SRC_JOURNAL_APPEND = "-journal-prod";
    private static String RP_TGT_JOURNAL_APPEND = "-target-journal-";
    private static String RP_TGT_APPEND = "-target-";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new ProtectionSetToBlockConsistencyGroupMigration());
                add(new VolumeRpJournalMigration());
                add(new RpBlockSnapshotConsistencyGroupMigration());
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
        prepareRPConsistencyGroupData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyBlockConsistencyGroupResults();
        verifyBlockSnapshotResults();
        verifyRpVolumeResults();
    }

    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareRPConsistencyGroupData() throws Exception {
        log.info("Preparing RecoverPoint consistency group data for RecoverPointConsistencyGroupMigration");
        TenantOrg tenantOrg = new TenantOrg();
        URI tenantOrgURI = URIUtil.createId(TenantOrg.class);
        tenantOrg.setId(tenantOrgURI);
        _dbClient.createObject(tenantOrg);

        Project proj = new Project();
        URI projectURI = URIUtil.createId(Project.class);
        String projectLabel = "project";
        proj.setId(projectURI);
        proj.setLabel(projectLabel);
        proj.setTenantOrg(new NamedURI(tenantOrgURI, projectLabel));
        _dbClient.createObject(proj);

        // Create CG 1 volumes
        ProtectionSet cg1ps = createProtectionSetData("cg1", projectURI);
        List<Volume> cg1Volumes = createRpVolumes("cg1volume1", 1, cg1ps);
        cg1Volumes.addAll(createRpVolumes("cg1volume2", 1, cg1ps));
        addVolumesToProtectionSet(cg1ps.getId(), cg1Volumes);
        createBlockSnapshotData("cg1Snap", cg1Volumes);

        // Create CG 2 volumes
        ProtectionSet cg2ps = createProtectionSetData("cg2", projectURI);
        List<Volume> cg2Volumes = createRpVolumes("cg2volume1", 2, cg2ps);
        cg2Volumes.addAll(createRpVolumes("cg2volume2", 2, cg2ps));
        addVolumesToProtectionSet(cg2ps.getId(), cg2Volumes);
        createBlockSnapshotData("cg2Snap", cg2Volumes);

        // Create CG 3 volumes
        ProtectionSet cg3ps = createProtectionSetData("cg3", projectURI);
        List<Volume> cg3Volumes = createRpVolumes("cg3volume1", 3, cg3ps);
        addVolumesToProtectionSet(cg3ps.getId(), cg3Volumes);
        createBlockSnapshotData("cg3Snap", cg3Volumes);

        // Verify the rp volume data exists in the database.
        for (URI volumeURI : rpTestVolumeURIs) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            Assert.assertNotNull(String.format("RecoverPoint test volume %s not found", volumeURI), volume);
        }
    }

    private void addVolumesToProtectionSet(URI protectionSetURI, List<Volume> volumes) {
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetURI);
        StringSet vols = new StringSet();
        for (Volume volume : volumes) {
            vols.add(volume.getId().toString());
        }
        protectionSet.setVolumes(vols);
        _dbClient.persistObject(protectionSet);
    }

    /**
     * Creates the RP source volume/journal and the specified number of
     * target/journal volumes.
     * 
     * @param volumeName
     * @param numTargets
     */
    private List<Volume> createRpVolumes(String volumeName, int numTargets, ProtectionSet protectionSet) {
        List<Volume> volumes = new ArrayList<Volume>();
        String rsetName = "RSet-" + volumeName;

        Volume sourceVolume = new Volume();
        URI sourceVolumeURI = URIUtil.createId(Volume.class);
        rpTestVolumeURIs.add(sourceVolumeURI);
        volumes.add(sourceVolume);
        sourceVolume.setId(sourceVolumeURI);
        sourceVolume.setLabel(volumeName);
        sourceVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
        sourceVolume.setRSetName(rsetName);
        sourceVolume.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
        _dbClient.createObject(sourceVolume);

        Volume sourceVolumeJournal = new Volume();
        URI sourceVolumeJournalURI = URIUtil.createId(Volume.class);
        rpTestVolumeURIs.add(sourceVolumeJournalURI);
        volumes.add(sourceVolumeJournal);
        sourceVolumeJournal.setId(sourceVolumeJournalURI);
        sourceVolumeJournal.setLabel(volumeName + RP_SRC_JOURNAL_APPEND);
        sourceVolumeJournal.setPersonality(Volume.PersonalityTypes.METADATA.toString());
        sourceVolumeJournal.setProtectionSet(new NamedURI(protectionSet.getId(), sourceVolumeJournal.getLabel()));
        _dbClient.createObject(sourceVolumeJournal);

        for (int i = 1; i <= numTargets; i++) {
            Volume sourceVolumeTarget = new Volume();
            URI sourceVolumeTargetURI = URIUtil.createId(Volume.class);
            rpTestVolumeURIs.add(sourceVolumeTargetURI);
            volumes.add(sourceVolumeTarget);
            sourceVolumeTarget.setId(sourceVolumeTargetURI);
            sourceVolumeTarget.setLabel(volumeName + RP_TGT_APPEND + "vArray" + i);
            sourceVolumeTarget.setPersonality(Volume.PersonalityTypes.TARGET.toString());
            sourceVolumeTarget.setRSetName(rsetName);
            sourceVolumeTarget.setProtectionSet(new NamedURI(protectionSet.getId(), sourceVolumeTarget.getLabel()));
            _dbClient.createObject(sourceVolumeTarget);

            Volume sourceVolumeTargetJournal = new Volume();
            URI sourceVolumeTargetJournalURI = URIUtil.createId(Volume.class);
            rpTestVolumeURIs.add(sourceVolumeTargetJournalURI);
            volumes.add(sourceVolumeTargetJournal);
            sourceVolumeTargetJournal.setId(sourceVolumeTargetJournalURI);
            sourceVolumeTargetJournal.setLabel(volumeName + RP_TGT_JOURNAL_APPEND + "vArray" + i);
            sourceVolumeTargetJournal.setPersonality(Volume.PersonalityTypes.METADATA.toString());
            sourceVolumeTargetJournal.setProtectionSet(new NamedURI(protectionSet.getId(), sourceVolumeTargetJournal.getLabel()));
            _dbClient.createObject(sourceVolumeTargetJournal);
        }

        return volumes;
    }

    private ProtectionSet createProtectionSetData(String cgName, URI projectURI) throws Exception {
        ProtectionSet protectionSet = new ProtectionSet();
        URI protectionSetURI = URIUtil.createId(ProtectionSet.class);
        rpTestProtectionSetURIs.add(protectionSetURI);
        protectionSet.setId(protectionSetURI);
        protectionSet.setLabel("ViPR-" + cgName);
        protectionSet.setProtectionId("790520997");
        protectionSet.setProtectionStatus("ENABLED");
        protectionSet.setProject(projectURI);
        _dbClient.createObject(protectionSet);

        return protectionSet;
    }

    private void createBlockSnapshotData(String name, List<Volume> volumes) throws Exception {
        for (Volume volume : volumes) {
            BlockSnapshot blockSnapshot = new BlockSnapshot();
            URI blockSnapshotURI = URIUtil.createId(BlockSnapshot.class);
            rpTestBlockSnapshotURIs.add(blockSnapshotURI);
            blockSnapshot.setId(blockSnapshotURI);
            blockSnapshot.setLabel(name);
            blockSnapshot.setEmName(name);
            blockSnapshot.setSnapsetLabel(name);
            blockSnapshot.setEmBookmarkTime("1395408080019");
            blockSnapshot.setEmInternalSiteName("0x5fd991b1295c0901");
            blockSnapshot.setParent(new NamedURI(volume.getId(), name));
            _dbClient.createObject(blockSnapshot);
        }
    }

    /**
     * Verifies the migration results for BlockConsistencyGroups.
     * 
     * @throws Exception When an error occurs verifying the BlockConsistencyGroup
     *             migration results.
     */
    private void verifyBlockConsistencyGroupResults() throws Exception {
        log.info("Verifying created BlockConsistencyGroups for RecoverPointConsistencyGroupMigration.");

        List<URI> cgURIs = _dbClient.queryByType(BlockConsistencyGroup.class, false);

        Iterator<BlockConsistencyGroup> cgs =
                _dbClient.queryIterativeObjects(BlockConsistencyGroup.class, cgURIs);
        List<BlockConsistencyGroup> consistencyGroups = new ArrayList<BlockConsistencyGroup>();
        while (cgs.hasNext()) {
            consistencyGroups.add(cgs.next());
        }

        for (URI protectionSetURI : rpTestProtectionSetURIs) {
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetURI);
            Assert.assertNotNull(String.format("RecoverPoint test ProtectionSet %s not found", protectionSetURI), protectionSet);

            // A BlockConsistencyGroup should have been created for each ProtectionSet
            BlockConsistencyGroup cg = null;

            for (BlockConsistencyGroup consistencyGroup : consistencyGroups) {
                cg = consistencyGroup;
                if (cg.getLabel().equals(protectionSet.getLabel())) {
                    break;
                }
            }

            Project project = _dbClient.queryObject(Project.class, protectionSet.getProject());

            Assert.assertTrue(String.format("Field deviceName is not properly set for consistency group %s", cg.getId().toString()),
                    cg.getDeviceName().equals(protectionSet.getLabel()));
            Assert.assertTrue(String.format("Field type should be set to RP for consistency group %s", cg.getId().toString()),
                    cg.getType().equals(BlockConsistencyGroup.Types.RP.toString()));
            Assert.assertTrue(String.format("Field project does not match the corresponding field for protection set %s", protectionSet
                    .getId().toString()),
                    project.getId().equals(protectionSet.getProject()));
            Assert.assertTrue(String.format("Field tenant does not match the corresponding field for protection set %s", protectionSet
                    .getId().toString()),
                    cg.getTenant().getURI().equals(project.getTenantOrg().getURI()));

            // Verify the consistency group volumes match the protection set volumes
            // Find all volumes assigned to the group
            final URIQueryResultList cgVolumesResults = new URIQueryResultList();
            _dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId().toString()),
                    cgVolumesResults);

            List<Volume> cgVolumes = new ArrayList<Volume>();
            Iterator<URI> cgVolumeURIs = cgVolumesResults.iterator();
            while (cgVolumeURIs.hasNext()) {
                Volume cgVol = _dbClient.queryObject(Volume.class, cgVolumeURIs.next());
                cgVolumes.add(cgVol);
            }

            for (Volume cgVolume : cgVolumes) {
                log.info(String.format("CG (%s) volume (%s) found.", cg.getLabel(), cgVolume.getLabel()));
            }

            Iterator<String> protectionSetVolumeURIs = protectionSet.getVolumes().iterator();

            while (protectionSetVolumeURIs.hasNext()) {
                boolean found = false;
                String protectionSetVolumeURI = protectionSetVolumeURIs.next();

                for (Volume cgVolume : cgVolumes) {

                    if (cgVolume.getId().toString().equals(protectionSetVolumeURI)) {
                        found = true;
                        break;
                    }
                }

                Assert.assertTrue(
                        String.format("All ProtectionSet volumes MUST be part of the BlockConsistencyGroup.  " +
                                "ProtectionSet volume %s was not found in BlockConsistencyGroup %s.",
                                protectionSetVolumeURI, cg.getId().toString()), found);
            }
        }
    }

    /**
     * Verifies the migration results for BlockSnapshot.
     * 
     * @throws Exception When an error occurs verifying the BlockSnapshot
     *             migration results.
     */
    private void verifyBlockSnapshotResults() throws Exception {
        log.info("Verifying updated BlockSnapshot results for RecoverPointConsistencyGroupMigration.");
        for (URI blockSnapshotURI : rpTestBlockSnapshotURIs) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, blockSnapshotURI);

            // Make sure the consistency group has been set to match the parent volume's
            // consistency group.
            Volume parentVolume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
            URI rpCgUri = parentVolume.fetchConsistencyGroupUriByType(_dbClient, Types.RP);
            Assert.assertTrue("The block snapshot consistency group MUST match the parent volume's consistency group.",
                    snapshot.fetchConsistencyGroup().equals(rpCgUri));
        }
    }

    /**
     * Verifies the migration results for RP source/target volume journal references.
     * 
     * @throws Exception When an error occurs verifying the BlockSnapshot
     *             migration results.
     */
    private void verifyRpVolumeResults() throws Exception {
        log.info("Verifying updated RecoverPoint source/target Volume results for RecoverPointConsistencyGroupMigration.");
        for (URI rpVolumeURI : rpTestVolumeURIs) {
            Volume rpVolume = _dbClient.queryObject(Volume.class, rpVolumeURI);

            // Ensure that the source and target volumes have been assigned journal volume reference
            if (rpVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString()) ||
                    rpVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
                Assert.assertNotNull("RecoverPoint source/target volumes MUST have a journal volume reference.",
                        rpVolume.getRpJournalVolume());
            } else {
                Assert.assertNull("RecoverPoint journal volumes should NOT have a journal volume reference.", rpVolume.getRpJournalVolume());
            }
        }
    }
}
