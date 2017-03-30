/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockObjectMultipleConsistencyGroupsMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new BlockConsistencyGroup fields and collapsing
 * multiple BlockConsistencyGroup references to a single references in BlockObject.
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
 * - BlockObjectMultipleConsistencyGroupsMigration
 * 
 * ****************************************************************************
 * 
 * NOTE: to run this multiple time, delete the folder "dbtest" under dbsvc
 * 
 * ****************************************************************************
 * 
 */
public class BlockObjectMultipleConsistencyGroupsMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(BlockObjectMultipleConsistencyGroupsMigrationTest.class);

    // Empty CG
    private static volatile URI emptyCgURI = null;

    // Used for RP+VPlex result verification
    private static volatile URI rpVplexPrimaryConsistencyGroupURI = null;
    private static HashMap<URI, URI> rpVplexVolumeToCgMapping = new HashMap<URI, URI>();

    // Used for VPlex result verification
    private static volatile URI vplexConsistencyGroupURI = null;
    private static List<URI> vplexVolumeURIs = new ArrayList<URI>();

    // Used for RP result verification
    private static volatile URI rpConsistencyGroupURI = null;
    private static volatile URI rpConsistencyGroupURI2 = null;
    private static volatile URI rpConsistencyGroupURI3 = null;
    private static List<URI> rpVolumeURIs = new ArrayList<URI>();

    // Used for Local Array result verification
    private static volatile URI localArrayConsistencyGroupURI = null;
    private static List<URI> blockVolumeURIs = new ArrayList<URI>();

    // Used for BlockSnapshot result verification
    private static List<URI> blockSnapshotURIs = new ArrayList<URI>();

    // Used for BlockMirror result verification
    private static List<URI> blockMirrorURIs = new ArrayList<URI>();

    private static String RP_SRC_JOURNAL_APPEND = "-journal-prod";
    private static String RP_TGT_JOURNAL_APPEND = "-target-journal-";
    private static String RP_TGT_APPEND = "-target-";

    private URI projectURI = null;
    private URI protectionSystemURI = null;

    private URI staleProtectionSetURI = null;
    private URI staleProtectionSetURI2 = null;
    private StringSet staleProtectionSetVolumeURIs = new StringSet();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new BlockObjectMultipleConsistencyGroupsMigration());
            }
        });
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
        prepareProjectData();
        createBareBlockConsistencyGroup("emptyCg");
        prepareProtectionSystemData();
        prepareRPVplexConsistencyGroupData();
        prepareRPConsistencyGroupData();
        prepareVPlexConsistencyGroupData();
        prepareLocalArrayConsistencyGroupData();
        prepareBlockSnapshotData("blockSnapshot", 10);
        prepareBlockMirrorData("blockMirror", 10);
        prepareProtectionSetWithAllStaleVolumes();
        prepareRPConsistencyGroupDataWithDuplicates();
        prepareRPConsistencyGroupDataWithStaleVolumes();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyRpVplexConsistencyGroupMigration();
        verifyVplexConsistencyGroupMigration();
        verifyRpConsistencyGroupMigration();
        verifyLocalArrayConsistencyGroupMigration();
        verifyBlockSnapshotMigration();
        verifyBlockMirrorMigration();
        verifyEmptyConsistencyGroupMigration();
        verifyStaleProtectionSet();
        verifyProtectionSetWith2StaleVolumes();
        verifyRpConsistencyGroupWithDuplicatesMigration();
    }

    /**
     * Prepares and persists the Project/Tenant data.
     * 
     * @throws Exception
     */
    private void prepareProjectData() throws Exception {
        TenantOrg tenantOrg = new TenantOrg();
        URI tenantOrgURI = URIUtil.createId(TenantOrg.class);
        tenantOrg.setId(tenantOrgURI);
        _dbClient.createObject(tenantOrg);

        Project proj = new Project();
        projectURI = URIUtil.createId(Project.class);
        String projectLabel = "project";
        proj.setId(projectURI);
        proj.setLabel(projectLabel);
        proj.setTenantOrg(new NamedURI(tenantOrgURI, projectLabel));
        _dbClient.createObject(proj);
    }

    /**
     * Prepares the ProtectionSystem data.
     * 
     * @throws Exception
     */
    private void prepareProtectionSystemData() throws Exception {
        ProtectionSystem protectionSystem = new ProtectionSystem();
        protectionSystemURI = URIUtil.createId(ProtectionSystem.class);
        protectionSystem.setId(protectionSystemURI);
        _dbClient.createObject(protectionSystem);
    }

    /**
     * Prepares the RP + VPlex consistency group test data.
     * 
     * @throws Exception
     */
    private void prepareRPVplexConsistencyGroupData() throws Exception {
        String cg1Name = "rpVplexCg";
        // Create the primary RecoverPoint BlockConsistencyGroup that will be shared by all the
        // RP+VPlex volumes.
        BlockConsistencyGroup rpVplexCg = createBlockConsistencyGroup(cg1Name, null, Types.RP.name(), true);
        // Save the CG references for migration verification.
        rpVplexPrimaryConsistencyGroupURI = rpVplexCg.getId();

        // Create the ProtectionSet that the RP + VPlex volumes will belong to.
        ProtectionSet rpVplexProtectionSet = createProtectionSet(cg1Name, projectURI);

        // Create all the RP+VPlex volumes
        List<Volume> rpVplexVolumes = createRpVolumes("rpVplexCgVolume1", 1, rpVplexProtectionSet, true);
        rpVplexVolumes.addAll(createRpVolumes("rpVplexCgVolume2", 1, rpVplexProtectionSet, true));

        // Add the RP + VPlex volumes to the RP consistency group
        addVolumesToBlockConsistencyGroup(rpVplexCg.getId(), rpVplexVolumes);
        // Add the RP+VPlex volumes to the protection set.
        addVolumesToProtectionSet(rpVplexProtectionSet.getId(), rpVplexVolumes);
    }

    /**
     * Prepares a ProtectionSet with volume references to volumes that do not
     * exist in the DB.
     * 
     * @throws Exception
     */
    private void prepareProtectionSetWithAllStaleVolumes() throws Exception {
        String cgName = "staleVolumeTest";
        ProtectionSet protectionSet = createProtectionSet(cgName, projectURI);
        staleProtectionSetURI = protectionSet.getId();

        URI volumeURI1 = URIUtil.createId(Volume.class);
        URI volumeURI2 = URIUtil.createId(Volume.class);
        URI volumeURI3 = URIUtil.createId(Volume.class);

        StringSet volumes = new StringSet();
        volumes.add(volumeURI1.toString());
        volumes.add(volumeURI2.toString());
        volumes.add(volumeURI3.toString());

        protectionSet.setVolumes(volumes);
        _dbClient.persistObject(protectionSet);
    }

    /**
     * Prepare the RecoverPoint only volumes and associated consistency group data.
     * 
     * @throws Exception
     */
    private void prepareRPConsistencyGroupData() throws Exception {
        String cg2Name = "rpCg";
        // Create the RecoverPoint BlockConsistencyGroup that will be shared by all the
        // RP volumes.
        BlockConsistencyGroup rpCg = createBlockConsistencyGroup(cg2Name, null, Types.RP.name(), true);
        // Save the CG references for migration verification.
        rpConsistencyGroupURI = rpCg.getId();

        // create a protection set and volumes and add them to the CG
        addProtectionSetAndVolumes(rpCg, "rpCg", 0);

    }

    /**
     * Prepare the RecoverPoint only volumes and associated consistency group data.
     * 
     * @throws Exception
     */
    private void prepareRPConsistencyGroupDataWithDuplicates() throws Exception {
        String cg2Name = "rpCg3";
        // Create the RecoverPoint BlockConsistencyGroup that will be shared by all the
        // RP volumes.
        BlockConsistencyGroup rpCg = createBlockConsistencyGroup(cg2Name, null, Types.RP.name(), true);
        // Save the CG references for migration verification.
        rpConsistencyGroupURI3 = rpCg.getId();

        // create a protection set and volumes and add them to the CG
        addProtectionSetAndVolumes(rpCg, "ps-dup", 3);

        // create a protection set and volumes and add them to the CG
        addProtectionSetAndVolumes(rpCg, "ps-dup", 3);

        // same volume in two protection sets
        // Create the ProtectionSet that the RP volumes will belong to.
        String prefix = "ps-dup2-";
        ProtectionSet cg2ps1 = createProtectionSet(prefix + "ProtectionSet", projectURI);
        ProtectionSet cg2ps2 = createProtectionSet(prefix + "ProtectionSet", projectURI);

        // Create all the RP volumes
        List<Volume> rpCgVolumes = createRpVolumes(prefix + "VolumeA", 1, cg2ps1, false);

        // Add the RP volumes to the RP consistency group
        addVolumesToBlockConsistencyGroup(rpCg.getId(), rpCgVolumes);
        // Add the RP volumes to the protection set
        addVolumesToProtectionSet(cg2ps1.getId(), rpCgVolumes);
        addVolumesToProtectionSet(cg2ps2.getId(), rpCgVolumes);

    }

    /**
     * creates snapshot objects
     * 
     * @param numSnapshots
     * @param volume
     * @param name snapshot name
     */
    public void addSnapshots(int numSnapshots, Volume volume, BlockConsistencyGroup cg, ProtectionSet ps, String name) {
        for (int i = 1; i <= numSnapshots; i++) {
            BlockSnapshot blockSnapshot = new BlockSnapshot();
            URI blockSnapshotURI = URIUtil.createId(BlockSnapshot.class);
            blockSnapshotURIs.add(blockSnapshotURI);
            blockSnapshot.setId(blockSnapshotURI);
            blockSnapshot.setLabel(name + i);
            blockSnapshot.setSnapsetLabel(name + i);
            blockSnapshot.setParent(new NamedURI(volume.getId(), name + i));
            blockSnapshot.addConsistencyGroup(cg.getId().toString());
            blockSnapshot.setProtectionSet(ps.getId());
            _dbClient.createObject(blockSnapshot);
        }

    }

    /**
     * adds new protection set with volumes to a CG; it's intentional that the protection set name is
     * not unique
     * 
     * @param rpCg
     * @param prefix
     * @throws Exception
     */
    private void addProtectionSetAndVolumes(BlockConsistencyGroup rpCg, String prefix, int numberOfSnaps) throws Exception {
        // Create the ProtectionSet that the RP volumes will belong to.
        ProtectionSet cg2ps = createProtectionSet(prefix + "ProtectionSet", projectURI);

        // Create all the RP volumes
        List<Volume> rpCgVolumes = createRpVolumes(prefix + "VolumeA", 2, cg2ps, false);
        rpCgVolumes.addAll(createRpVolumes(prefix + "VolumeB", 2, cg2ps, false));

        // Add the RP volumes to the RP consistency group
        addVolumesToBlockConsistencyGroup(rpCg.getId(), rpCgVolumes);
        // Add the RP volumes to the protection set
        addVolumesToProtectionSet(cg2ps.getId(), rpCgVolumes);

        // add some snapshots
        addSnapshots(numberOfSnaps, rpCgVolumes.iterator().next(), rpCg, cg2ps, prefix + "SnapVolmeA");

    }

    /**
     * Prepare the RecoverPoint only volumes and associated consistency group data.
     * 
     * @throws Exception
     */
    private void prepareRPConsistencyGroupDataWithStaleVolumes() throws Exception {
        String cg2Name = "rpCg2";
        // Create the RecoverPoint BlockConsistencyGroup that will be shared by all the
        // RP volumes.
        BlockConsistencyGroup rpCg = createBlockConsistencyGroup(cg2Name, null, Types.RP.name(), true);
        // Save the CG references for migration verification.
        rpConsistencyGroupURI2 = rpCg.getId();

        // Create the ProtectionSet that the RP volumes will belong to.
        ProtectionSet cg2ps = createProtectionSet("rpCg2ProtectionSet", projectURI);

        // Create all the RP volumes
        List<Volume> rpCg2Volumes = createRpVolumes("rpCg2VolumeA", 2, cg2ps, false);
        rpCg2Volumes.addAll(createRpVolumes("rpCg2VolumeB", 2, cg2ps, false));

        // Add the RP volumes to the RP consistency group
        addVolumesToBlockConsistencyGroup(rpCg.getId(), rpCg2Volumes);
        // Add the RP volumes to the protection set
        addVolumesToProtectionSet(cg2ps.getId(), rpCg2Volumes);

        // Add stale volume references
        URI staleVolumeURI1 = URIUtil.createId(Volume.class);
        URI staleVolumeURI2 = URIUtil.createId(Volume.class);

        ProtectionSet ps = _dbClient.queryObject(ProtectionSet.class, cg2ps.getId());
        StringSet vols = ps.getVolumes();
        vols.add(staleVolumeURI1.toString());
        vols.add(staleVolumeURI2.toString());

        staleProtectionSetURI2 = cg2ps.getId();
        staleProtectionSetVolumeURIs.add(staleVolumeURI1.toString());
        staleProtectionSetVolumeURIs.add(staleVolumeURI2.toString());

        _dbClient.persistObject(ps);
    }

    /**
     * Prepare the VPlex volumes and associated consistency group data.
     * 
     * @throws Exception
     */
    private void prepareVPlexConsistencyGroupData() throws Exception {
        // Create a VPlex storage system
        StorageSystem storageSystem = createStorageSystem(true);

        // Create the VPlex volumes and add them to the VPlex consistency group
        List<Volume> vplexVolumes = createVPlexVolumes("vplexVolume", 3, storageSystem.getId());
        // Prior to 2.2, VPlex only CGs (nothing to do with RP) did not set the CG type of VPLEX. So we pass false here.
        BlockConsistencyGroup vplexCg = createBlockConsistencyGroup("vplexCg", storageSystem.getId(), Types.VPLEX.name(), false);
        // Save a references to the cg for migration verification
        vplexConsistencyGroupURI = vplexCg.getId();
        // Add the VPlex volumes to the VPlex consistency group
        addVolumesToBlockConsistencyGroup(vplexCg.getId(), vplexVolumes);
    }

    /**
     * Create block volumes and associated local array consistency group.
     * 
     * @throws Exception
     */
    private void prepareLocalArrayConsistencyGroupData() throws Exception {
        // Create a non-VPlex storage system
        StorageSystem storageSystem = createStorageSystem(false);
        // Create the block volumes that will be part of the cg
        List<Volume> blockVolumes = createBlockVolumes("blockVolume", 3, storageSystem.getId());
        // Create the consistency group and add the block volumes
        BlockConsistencyGroup localArrayCg = createBlockConsistencyGroup("localArrayCg", storageSystem.getId(), Types.LOCAL.name(), true);
        localArrayConsistencyGroupURI = localArrayCg.getId();
        addVolumesToBlockConsistencyGroup(localArrayCg.getId(), blockVolumes);
    }

    /**
     * Creates the BlockObject BlockSnapshot data.
     * 
     * @param name
     * @param numSnapshots
     * @throws Exception
     */
    private void prepareBlockSnapshotData(String name, int numSnapshots) throws Exception {
        // Create the volume for the snapshots
        Volume volume = new Volume();
        URI volumeURI = URIUtil.createId(Volume.class);

        StorageSystem storageSystem = createStorageSystem(false);
        volume.setId(volumeURI);
        volume.setStorageController(storageSystem.getId());
        String volName = "blockSnapshotVolume";
        volume.setLabel(volName);
        BlockConsistencyGroup cg =
                createBlockConsistencyGroup("blockSnapshotConsistencyGroup", storageSystem.getId(), Types.LOCAL.name(), true);
        volume.setConsistencyGroup(cg.getId());
        _dbClient.createObject(volume);

        for (int i = 1; i <= numSnapshots; i++) {
            BlockSnapshot blockSnapshot = new BlockSnapshot();
            URI blockSnapshotURI = URIUtil.createId(BlockSnapshot.class);
            blockSnapshotURIs.add(blockSnapshotURI);
            blockSnapshot.setId(blockSnapshotURI);
            blockSnapshot.setLabel(name + i);
            blockSnapshot.setSnapsetLabel(name + i);
            blockSnapshot.setParent(new NamedURI(volume.getId(), name + i));
            blockSnapshot.addConsistencyGroup(cg.getId().toString());
            _dbClient.createObject(blockSnapshot);
        }
    }

    /**
     * Creates the BlockObject BlockMirror data.
     * 
     * @param name
     * @param numBlockMirrors
     * @throws Exception
     */
    private void prepareBlockMirrorData(String name, int numBlockMirrors) throws Exception {
        BlockConsistencyGroup cg =
                createBlockConsistencyGroup("blockMirrorConsistencyGroup", null, Types.LOCAL.name(), true);

        for (int i = 1; i <= numBlockMirrors; i++) {
            BlockMirror blockMirror = new BlockMirror();
            URI blockMirrorURI = URIUtil.createId(BlockMirror.class);
            blockMirrorURIs.add(blockMirrorURI);
            blockMirror.setId(blockMirrorURI);
            blockMirror.setLabel(name + i);
            // Set the 'old' field value so it can be migrated
            blockMirror.addConsistencyGroup(cg.getId().toString());
            _dbClient.createObject(blockMirror);
        }
    }

    /**
     * Convenience method to create a ProtectionSet.
     * 
     * @param cgName
     * @param projectURI
     * @return
     * @throws Exception
     */
    private ProtectionSet createProtectionSet(String cgName, URI projectURI) throws Exception {
        ProtectionSet protectionSet = new ProtectionSet();
        URI protectionSetURI = URIUtil.createId(ProtectionSet.class);
        protectionSet.setId(protectionSetURI);
        protectionSet.setLabel("ViPR-" + cgName);
        protectionSet.setProtectionId("790520997");
        protectionSet.setProtectionStatus("ENABLED");
        protectionSet.setProject(projectURI);
        protectionSet.setProtectionSystem(protectionSystemURI);
        _dbClient.createObject(protectionSet);

        return protectionSet;
    }

    /**
     * Convenience method that creates a StorageSystem.
     * 
     * @param isVplex true if a VPlex storage system is to be created, false otherwise.
     * @return
     */
    private StorageSystem createStorageSystem(boolean isVplex) {
        StorageSystem ss = new StorageSystem();
        URI storageSystemId = URIUtil.createId(StorageSystem.class);
        ss.setId(storageSystemId);
        if (isVplex) {
            ss.setLabel("VPLEX+FNM00114300288:FNM00114600001");
            ss.setNativeGuid("VPLEX+FNM00114300288:FNM00114600001");
            ss.setSystemType("vplex");
        } else {
            ss.setLabel("SYMMETRIX+000195701573");
            ss.setNativeGuid("SYMMETRIX+000195701573");
            ss.setSystemType("vmax");
        }

        _dbClient.createObject(ss);

        return ss;
    }

    /**
     * Creates the consistency group used by the BlockObjects.
     * 
     * @param name
     * @return
     */
    private BlockConsistencyGroup createBareBlockConsistencyGroup(String name) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        URI cgURI = URIUtil.createId(BlockConsistencyGroup.class);
        emptyCgURI = cgURI;
        cg.setId(cgURI);
        cg.setLabel(name);
        _dbClient.createObject(cg);
        return cg;
    }

    /**
     * Creates the consistency group used by the BlockObjects.
     * 
     * @param name
     * @return
     */
    private BlockConsistencyGroup createBlockConsistencyGroup(String name, URI storageSystem, String type, boolean setType) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        URI cgURI = URIUtil.createId(BlockConsistencyGroup.class);
        cg.setId(cgURI);
        cg.setLabel(name);
        cg.setStorageController(storageSystem);

        if (type.equals(Types.LOCAL.name())) {
            // Set the 'old' field value so it can be migrated
            cg.setDeviceName("localArrayDeviceName");
        } else if (type.equals(Types.VPLEX.name())) {
            // Set the 'old' field value so it can be migrated
            cg.setDeviceName("vplexDeviceName");
        } else if (type.equals(Types.RP.name())) {
            // Set the 'old' field value so it can be migrated.
            cg.setDeviceName("rpDeviceName");
        } else if (type.equals(Types.SRDF.name())) {
            // Set the 'old' field value so it can be migrated
            cg.setDeviceName("srdfDeviceName");
        }

        if (setType) {
            cg.setType(type);
        }

        // Set the 'old' field value so it can be migrated
        _dbClient.createObject(cg);
        return cg;
    }

    /**
     * Convenience method that adds volumes to a protection set.
     * 
     * @param protectionSetURI
     * @param volumes
     */
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
     * Convenience method that creates the BlockObject Volume data.
     * 
     * @param volumeName
     * @param numTargets
     */
    private List<Volume> createBlockVolumes(String volumeName, int numVolumes, URI storageSystem) {
        List<Volume> volumes = new ArrayList<Volume>();
        for (int i = 1; i <= numVolumes; i++) {
            Volume volume = new Volume();
            URI volumeURI = URIUtil.createId(Volume.class);
            blockVolumeURIs.add(volumeURI);
            volume.setId(volumeURI);
            volume.setLabel(volumeName + i);
            volume.setStorageController(storageSystem);
            _dbClient.createObject(volume);
            volumes.add(volume);
        }

        return volumes;
    }

    /**
     * Convenience method that creates VPlex volumes.
     * 
     * @param name
     * @param numberOfVols
     * @param storageSystem
     * @return
     */
    private List<Volume> createVPlexVolumes(String name, int numberOfVols, URI storageSystem) {
        List<Volume> volumes = new ArrayList<Volume>();

        for (int i = 1; i <= numberOfVols; i++) {
            Volume vplexVolume = new Volume();
            URI vplexVolumeUri = URIUtil.createId(Volume.class);
            vplexVolumeURIs.add(vplexVolumeUri);
            vplexVolume.setId(vplexVolumeUri);
            vplexVolume.setLabel(name + i);
            vplexVolume.setNativeId("/clusters/cluster-1/virtual-volumes/device_V000195701573-01E7F_vol" + i);
            vplexVolume.setStorageController(storageSystem);

            StringSet associatedVolumes = new StringSet();
            associatedVolumes.add("associatedVol1");
            vplexVolume.setAssociatedVolumes(associatedVolumes);

            volumes.add(vplexVolume);
        }

        return volumes;
    }

    /**
     * Creates the RP source volume/journal and the specified number of
     * target/journal volumes.
     * 
     * @param volumeName
     * @param numTargets
     */
    private List<Volume> createRpVolumes(String volumeName, int numTargets, ProtectionSet protectionSet, boolean isRpVPlex) {
        List<Volume> volumes = new ArrayList<Volume>();

        StringSet associatedVolumes = new StringSet();
        associatedVolumes.add("associatedVol1");

        StorageSystem storageSystem = null;
        if (isRpVPlex) {
            storageSystem = createStorageSystem(true);
        } else {
            storageSystem = createStorageSystem(false);
        }

        String rsetName = "RSet-" + volumeName;

        Volume sourceVolume = new Volume();
        URI sourceVolumeURI = URIUtil.createId(Volume.class);
        volumes.add(sourceVolume);
        sourceVolume.setId(sourceVolumeURI);
        sourceVolume.setLabel(volumeName);
        sourceVolume.setPersonality(Volume.PersonalityTypes.SOURCE.toString());
        sourceVolume.setRSetName(rsetName);
        sourceVolume.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
        sourceVolume.setStorageController(storageSystem.getId());
        if (isRpVPlex) {
            sourceVolume.setAssociatedVolumes(associatedVolumes);
            sourceVolume.setNativeId("/clusters/cluster-1/virtual-volumes/device_V000195701573-01E7A_vol");
            // Create a VPLEX ViPR BlockConsistencyGroup for the source volume
            BlockConsistencyGroup sourceVolumeCg =
                    createBlockConsistencyGroup(sourceVolume.getLabel() + "-CG", storageSystem.getId(), Types.VPLEX.name(), true);
            addVolumeToBlockConsistencyGroup(sourceVolumeCg.getId(), sourceVolume);
            rpVplexVolumeToCgMapping.put(sourceVolumeURI, sourceVolumeCg.getId());
        } else {
            rpVolumeURIs.add(sourceVolumeURI);
        }
        _dbClient.createObject(sourceVolume);

        Volume sourceVolumeJournal = new Volume();
        URI sourceVolumeJournalURI = URIUtil.createId(Volume.class);
        volumes.add(sourceVolumeJournal);
        sourceVolumeJournal.setId(sourceVolumeJournalURI);
        sourceVolumeJournal.setLabel(volumeName + RP_SRC_JOURNAL_APPEND);
        sourceVolumeJournal.setPersonality(Volume.PersonalityTypes.METADATA.toString());
        sourceVolumeJournal.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
        sourceVolumeJournal.setStorageController(storageSystem.getId());
        if (isRpVPlex) {
            sourceVolumeJournal.setAssociatedVolumes(associatedVolumes);
            sourceVolumeJournal.setNativeId("/clusters/cluster-1/virtual-volumes/device_V000195701573-01E7B_vol");
            // Create a VPLEX ViPR BlockConsistencyGroup for the source journal volume
            BlockConsistencyGroup sourceVolumeJournalCg =
                    createBlockConsistencyGroup(sourceVolumeJournal.getLabel() + "-CG", storageSystem.getId(), Types.VPLEX.name(), true);
            addVolumeToBlockConsistencyGroup(sourceVolumeJournalCg.getId(), sourceVolumeJournal);
            rpVplexVolumeToCgMapping.put(sourceVolumeJournalURI, sourceVolumeJournalCg.getId());
        } else {
            rpVolumeURIs.add(sourceVolumeJournalURI);
        }
        _dbClient.createObject(sourceVolumeJournal);

        for (int i = 1; i <= numTargets; i++) {
            Volume sourceVolumeTarget = new Volume();
            URI sourceVolumeTargetURI = URIUtil.createId(Volume.class);
            volumes.add(sourceVolumeTarget);
            sourceVolumeTarget.setId(sourceVolumeTargetURI);
            sourceVolumeTarget.setLabel(volumeName + RP_TGT_APPEND + "vArray" + i);
            sourceVolumeTarget.setPersonality(Volume.PersonalityTypes.TARGET.toString());
            sourceVolumeTarget.setRSetName(rsetName);
            sourceVolumeTarget.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
            sourceVolumeTarget.setStorageController(storageSystem.getId());
            if (isRpVPlex) {
                sourceVolumeTarget.setAssociatedVolumes(associatedVolumes);
                sourceVolumeTarget.setNativeId("/clusters/cluster-2/virtual-volumes/device_V000195701573-01E7C_vol" + i);
                // Create a VPLEX ViPR BlockConsistencyGroup for the target volume
                BlockConsistencyGroup sourceVolumeTargetCg =
                        createBlockConsistencyGroup(sourceVolumeTarget.getLabel() + "-CG", storageSystem.getId(), Types.VPLEX.name(), true);
                addVolumeToBlockConsistencyGroup(sourceVolumeTargetCg.getId(), sourceVolumeTarget);
                rpVplexVolumeToCgMapping.put(sourceVolumeTargetURI, sourceVolumeTargetCg.getId());
            } else {
                rpVolumeURIs.add(sourceVolumeTargetURI);
            }

            _dbClient.createObject(sourceVolumeTarget);

            Volume sourceVolumeTargetJournal = new Volume();
            URI sourceVolumeTargetJournalURI = URIUtil.createId(Volume.class);
            volumes.add(sourceVolumeTargetJournal);
            sourceVolumeTargetJournal.setId(sourceVolumeTargetJournalURI);
            sourceVolumeTargetJournal.setLabel(volumeName + RP_TGT_JOURNAL_APPEND + "vArray" + i);
            sourceVolumeTargetJournal.setPersonality(Volume.PersonalityTypes.METADATA.toString());
            sourceVolumeTargetJournal.setProtectionSet(new NamedURI(protectionSet.getId(), protectionSet.getLabel()));
            sourceVolumeTargetJournal.setStorageController(storageSystem.getId());
            if (isRpVPlex) {
                sourceVolumeTargetJournal.setAssociatedVolumes(associatedVolumes);
                sourceVolumeTargetJournal.setNativeId("/clusters/cluster-2/virtual-volumes/device_V000195701573-01ED_vol" + i);
                // Create a VPLEX ViPR BlockConsistencyGroup for the source target journal volume
                BlockConsistencyGroup sourceVolumeTargetJournalCg =
                        createBlockConsistencyGroup(sourceVolumeTargetJournal.getLabel() + "-CG", storageSystem.getId(),
                                Types.VPLEX.name(), true);
                addVolumeToBlockConsistencyGroup(sourceVolumeTargetJournalCg.getId(), sourceVolumeTargetJournal);
                rpVplexVolumeToCgMapping.put(sourceVolumeTargetJournalURI, sourceVolumeTargetJournalCg.getId());
            } else {
                rpVolumeURIs.add(sourceVolumeTargetJournalURI);
            }
            _dbClient.createObject(sourceVolumeTargetJournal);
        }

        return volumes;
    }

    /**
     * Associates a list of volumes with a given BlockConsistencyGroup URI.
     * 
     * @param cgUri
     * @param volumes
     */
    private void addVolumesToBlockConsistencyGroup(URI cgUri, List<Volume> volumes) {
        for (Volume volume : volumes) {
            addVolumeToBlockConsistencyGroup(cgUri, volume);
        }
    }

    /**
     * Associates a volume with a given BlockConsistencyGroup URI.
     * 
     * @param cgUri
     * @param volume
     */
    private void addVolumeToBlockConsistencyGroup(URI cgUri, Volume volume) {
        // Set the 'old' field value so it can be migrated
        volume.addConsistencyGroup(cgUri.toString());
        _dbClient.persistObject(volume);
    }

    private void verifyEmptyConsistencyGroupMigration() throws Exception {
        log.info("Verifying empty/unused BlockConsistencyGroup.");
        BlockConsistencyGroup emptyCg =
                _dbClient.queryObject(BlockConsistencyGroup.class, emptyCgURI);

        // Verify that the primary CG now has the VPlex type added to its types list is null
        Assert.assertTrue("The empty BlockConsistencyGroup.type field should be null.",
                emptyCg.getType().equals(NullColumnValueGetter.getNullStr()));

        Assert.assertTrue("The BlockConsistencyGroup.types field should be null.", emptyCg.getTypes().isEmpty());
    }

    /**
     * Verify the RP+VPlex consistency group and its volumes have been properly migrated.
     * 
     * @throws Exception
     */
    private void verifyRpVplexConsistencyGroupMigration() throws Exception {
        log.info("Verifying RP+VPlex BlockConsistencyGroup and associated volume migration.");
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        BlockConsistencyGroup rpVplexPrimaryCg =
                _dbClient.queryObject(BlockConsistencyGroup.class, rpVplexPrimaryConsistencyGroupURI);

        // Verify the RP+VPLEX consistency group was properly migrated
        verifyConsistencyGroupMigration(rpVplexPrimaryCg, Types.RP.name(), Types.VPLEX.name());

        Assert.assertNotNull("The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should be populated.",
                rpVplexPrimaryCg.getSystemConsistencyGroups());
        Assert.assertNotNull("The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should contain an entry for "
                + protectionSystemURI.toString(),
                rpVplexPrimaryCg.getSystemConsistencyGroups().get(protectionSystemURI.toString()));
        Assert.assertTrue(
                "The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should contain a mapping for "
                        + protectionSystemURI.toString() + "-> ViPR-" + rpVplexPrimaryCg.getLabel(),
                rpVplexPrimaryCg.getSystemConsistencyGroups().get(protectionSystemURI.toString())
                        .contains("ViPR-" + rpVplexPrimaryCg.getLabel()));

        // Verify that primary CG has a mapping reference for each of the VPlex storage system/cg name.
        for (URI rpVplexVolumeId : rpVplexVolumeToCgMapping.keySet()) {
            Volume rpVplexVolume = _dbClient.queryObject(Volume.class, rpVplexVolumeId);
            blockObjects.add(rpVplexVolume);

            // Get the VPlex consistency group
            URI cgUri = rpVplexVolumeToCgMapping.get(rpVplexVolumeId);
            BlockConsistencyGroup vplexCg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);

            String cgName = vplexCg.getLabel();
            String clusterName = getVPlexClusterFromVolume(rpVplexVolume);
            String storageSystem = rpVplexVolume.getStorageController().toString();
            String clusterCgName = BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName);

            // Verify that primary CG contains the correct mapping
            Assert.assertTrue("The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should contain a mapping for "
                    + storageSystem + "->" + clusterCgName,
                    rpVplexPrimaryCg.getSystemConsistencyGroups().get(storageSystem).contains(clusterCgName));

            // Verify that the VPlex CG has been marked for deletion
            Assert.assertTrue("The VPlex BlockConsistencyGroup " + vplexCg.getLabel() + "should be inactive.", vplexCg.getInactive());
        }

        // Verify the volume migration took place correctly
        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the VPlex consistency group and its volumes have been properly migrated.
     * 
     * @throws Exception
     */
    private void verifyVplexConsistencyGroupMigration() throws Exception {
        log.info("Verifying VPlex BlockConsistencyGroup and associated volume migration.");

        BlockConsistencyGroup vplexCg = _dbClient.queryObject(BlockConsistencyGroup.class, vplexConsistencyGroupURI);
        Iterator<Volume> vplexVolumeItr =
                _dbClient.queryIterativeObjects(Volume.class, vplexVolumeURIs);

        // Verify the VPLEX consistency group was properly migrated
        verifyConsistencyGroupMigration(vplexCg, Types.VPLEX.name());

        while (vplexVolumeItr.hasNext()) {
            Volume vplexVolume = vplexVolumeItr.next();
            // Get the VPlex consistency group
            String cgName = vplexCg.getLabel();
            String clusterName = getVPlexClusterFromVolume(vplexVolume);
            String storageSystem = vplexVolume.getStorageController().toString();
            String clusterCgName = BlockConsistencyGroupUtils.buildClusterCgName(clusterName, cgName);

            // Verify that primary CG contains the correct mapping
            Assert.assertNotNull("The VPlex BlockConsistencyGroup.vplexStorageSystemToCg field should be populated.",
                    vplexCg.getSystemConsistencyGroups());
            Assert.assertTrue("The VPlex BlockConsistencyGroup.vplexStorageSystemToCg should contain a key for storage system "
                    + storageSystem,
                    vplexCg.getSystemConsistencyGroups().containsKey(storageSystem));
            Assert.assertTrue("The VPlex BlockConsistencyGroup.vplexStorageSystemToCg field should contain a mapping for " + storageSystem
                    + "->" + clusterCgName,
                    vplexCg.getSystemConsistencyGroups().get(storageSystem).contains(clusterCgName));
        }

        // Verify the volume migration took place correctly
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        while (vplexVolumeItr.hasNext()) {
            blockObjects.add(vplexVolumeItr.next());
        }

        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the RP consistency group and its volumes have been properly migrated.
     * 
     * @throws Exception
     */
    private void verifyRpConsistencyGroupMigration() throws Exception {
        log.info("Verifying RP BlockConsistencyGroup and associated volume migration.");

        BlockConsistencyGroup rpCg = _dbClient.queryObject(BlockConsistencyGroup.class, rpConsistencyGroupURI);

        Assert.assertNotNull("The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should be populated.",
                rpCg.getSystemConsistencyGroups());
        Assert.assertTrue("The RP+VPlex BlockConsistencyGroup.systemConsistencyGroups field should contain a mapping for "
                + protectionSystemURI.toString() + "-> ViPR-" + rpCg.getLabel(),
                rpCg.getSystemConsistencyGroups().get(protectionSystemURI.toString()).contains("ViPR-" + rpCg.getLabel()));

        Iterator<Volume> rpVolumeItr =
                _dbClient.queryIterativeObjects(Volume.class, rpVolumeURIs);

        // Verify the RP consistency group was properly migrated
        verifyConsistencyGroupMigration(rpCg, Types.RP.name());

        // Verify the volume migration took place correctly
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        while (rpVolumeItr.hasNext()) {
            blockObjects.add(rpVolumeItr.next());
        }

        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the RP consistency group and its volumes have been properly migrated.
     * 
     * @throws Exception
     */
    private void verifyRpConsistencyGroupWithDuplicatesMigration() throws Exception {
        log.info("Verifying duplicate protection sets were cleaned up.");

        List<URI> protSetIds = _dbClient.queryByType(ProtectionSet.class, true);

        List<String> protSetNames = new ArrayList<String>();
        for (URI id : protSetIds) {
            ProtectionSet protSet = _dbClient.queryObject(ProtectionSet.class, id);
            if (protSet == null || protSet.getInactive()) {
                continue;
            }
            if (protSetNames.contains(protSet.getLabel())) {
                // fail duplicate protection set
                Assert.fail("Duplicate protection sets exist after migration " + protSet.getLabel());
            } else {
                protSetNames.add(protSet.getLabel());

                // verify that there are no duplicates or stale volumes on the volume list
                List<String> volumeIds = new ArrayList<String>();
                for (String volId : protSet.getVolumes()) {
                    Volume vol = _dbClient.queryObject(Volume.class, URI.create(volId));
                    if (vol == null || vol.getInactive()) {
                        // fail stale volume on protection set
                        Assert.fail(String.format("Stale volume %s exists on protection set %s exist after migration", volId,
                                protSet.getLabel()));
                    } else if (volumeIds.contains(volId)) {
                        // fail duplicate volume on protection set
                        Assert.fail(String.format("Duplicate volume %s exists on protection set %s exist after migration", volId,
                                protSet.getLabel()));
                    } else if (vol.getProtectionSet() == null || vol.getProtectionSet().getURI() == null
                            || !vol.getProtectionSet().getURI().equals(protSet.getId())) {
                        // fail volume does not point back to protection set
                        Assert.fail(String.format("Volume %s does not point back to protection set %s exist after migration", volId,
                                protSet.getLabel()));
                    } else {
                        volumeIds.add(volId);
                    }
                }
            }
        }

        // make sure there are no dangling protection sets on volumes
        List<URI> volumes = _dbClient.queryByType(Volume.class, true);
        for (URI id : volumes) {
            Volume vol = _dbClient.queryObject(Volume.class, id);
            if (vol.getProtectionSet() != null && vol.getProtectionSet().getURI() != null) {
                ProtectionSet protSet = _dbClient.queryObject(ProtectionSet.class, vol.getProtectionSet().getURI());
                if (protSet == null || protSet.getInactive()) {
                    // fail dangling protection set
                    Assert.fail(String.format("Stale protection set %s on volume %s exists after migration", vol.getProtectionSet()
                            .getURI(), id));
                }
            }
        }

        // make sure there are no dangling protection sets on snapshots
        List<URI> snapshots = _dbClient.queryByType(BlockSnapshot.class, true);
        for (URI id : snapshots) {
            BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, id);
            if (snapshot.getProtectionSet() != null) {
                ProtectionSet protSet = _dbClient.queryObject(ProtectionSet.class, snapshot.getProtectionSet());
                if (protSet == null || protSet.getInactive()) {
                    // fail dangling protection set
                    Assert.fail(String.format("Stale protection set %s on snapshot %s exists after migration", snapshot.getProtectionSet(),
                            id));
                }
            }
        }
    }

    /**
     * Verify the local array consistency group and its volumes have been properly
     * migrated.
     * 
     * @throws Exception
     */
    private void verifyLocalArrayConsistencyGroupMigration() throws Exception {
        log.info("Verifying local array BlockConsistencyGroup and associated volume migration.");

        BlockConsistencyGroup localArrayCg = _dbClient.queryObject(BlockConsistencyGroup.class, localArrayConsistencyGroupURI);
        Iterator<Volume> blockVolumeItr =
                _dbClient.queryIterativeObjects(Volume.class, blockVolumeURIs);

        // Verify the LOCAL consistency group was properly migrated
        verifyConsistencyGroupMigration(localArrayCg, Types.LOCAL.name());

        // Verify the volume migration took place correctly
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        while (blockVolumeItr.hasNext()) {
            blockObjects.add(blockVolumeItr.next());
        }

        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the BlockMirror objects have been migrated correctly.
     * 
     * @throws Exception
     */
    private void verifyBlockSnapshotMigration() throws Exception {
        log.info("Verifying BlockSnapshot migration.");
        // Get the block snapshots
        Iterator<BlockSnapshot> blockSnapshotItr =
                _dbClient.queryIterativeObjects(BlockSnapshot.class, blockSnapshotURIs);

        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        while (blockSnapshotItr.hasNext()) {
            blockObjects.add(blockSnapshotItr.next());
        }

        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the BlockMirror objects have been migrated correctly.
     * 
     * @throws Exception
     */
    private void verifyBlockMirrorMigration() throws Exception {
        log.info("Verifying BlockMirror migration.");
        // Get the block snapshots
        Iterator<BlockMirror> blockMirrorItr =
                _dbClient.queryIterativeObjects(BlockMirror.class, blockMirrorURIs);

        List<BlockObject> blockObjects = new ArrayList<BlockObject>();

        while (blockMirrorItr.hasNext()) {
            blockObjects.add(blockMirrorItr.next());
        }

        verifyBlockObjects(blockObjects);
    }

    /**
     * Verify the migration of BlockConsistencyGroups. Ensure the type and deviceName
     * fields have been migrated to the types and deviceNames fields.
     * 
     * @param consistencyGroup
     * @param types The types that should have been properly migrated.
     * @throws Exception
     */
    private void verifyConsistencyGroupMigration(BlockConsistencyGroup consistencyGroup, String... types) throws Exception {
        log.info("Verifying BlockConsistencyGroup migration for " + consistencyGroup.getLabel());

        // Verify that the primary CG now has the VPlex type added to its types list is and that
        // the type field null
        Assert.assertTrue("The BlockConsistencyGroup.type field should be null.",
                consistencyGroup.getType().equals(NullColumnValueGetter.getNullStr()));

        for (String type : types) {
            Assert.assertNotNull("The " + type + " BlockConsistencyGroup.types field should be populated.", consistencyGroup.getTypes());
            Assert.assertTrue("The BlockConsistencyGroup.types field for " + consistencyGroup.getLabel() + " should contain " + type,
                    consistencyGroup.getTypes().contains(type));

            // Verify that the primary CG now has the RP and VPlex device names added to its list
            Assert.assertTrue("The local array BlockConsistencyGroup.deviceName field should be null.",
                    consistencyGroup.getDeviceName().equals(NullColumnValueGetter.getNullStr()));
        }
    }

    /**
     * Verify the migration for BlockObjects. Ensure the consistencyGroups field has
     * been collapsed into the consistencyGroup field.
     * 
     * @param blockObjects
     */
    private void verifyBlockObjects(List<BlockObject> blockObjects) {
        for (BlockObject blockObject : blockObjects) {
            log.info("Verifying BlockObject migration for " + blockObject.getLabel());

            // For RP+VPlex migrations, the BlockObjects will have a null consistencyGroups reference.
            // For non-RP+VPlex migrations, the BlockObjects will have an empty null consistencyGroups reference.
            // Both conditions indicate the field is no longer being used
            Assert.assertTrue("BlockObject.consistencyGroups field should be empty.",
                    blockObject.getConsistencyGroups() == null || blockObject.getConsistencyGroups().isEmpty());
            Assert.assertNotNull("BlockObject.consistencyGroup field should not be null.",
                    blockObject.getConsistencyGroup());
        }
    }

    /**
     * Verifies that ProtectionSets referencing all stale volumes get removed from the DB.
     */
    private void verifyStaleProtectionSet() {
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, staleProtectionSetURI);
        Assert.assertTrue("ProtectionSet  " + staleProtectionSetURI + " is stale and should have been removed.",
                (protectionSet == null || protectionSet.getInactive()));
    }

    /**
     * Verifies that stale volumes have been removed from the ProtectionSet.
     */
    private void verifyProtectionSetWith2StaleVolumes() {
        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, staleProtectionSetURI2);
        StringSet psVolumes = protectionSet.getVolumes();
        for (String staleVolume : staleProtectionSetVolumeURIs) {
            // verify the stale volumes were removed
            Assert.assertFalse("ProtectionSet " + staleProtectionSetURI2 + " should not contain stale volume " + staleVolume,
                    psVolumes.contains(staleVolume));
        }
    }

    /**
     * Gets the VPlex cluster Id for a given VPlex virtual volume.
     * 
     * @param virtualVolume
     * @return
     */
    private String getVPlexClusterFromVolume(Volume virtualVolume) {
        String clusterId = null;

        if (virtualVolume != null && virtualVolume.getNativeId() != null) {
            String[] nativeIdSplit = virtualVolume.getNativeId().split("/");
            clusterId = nativeIdSplit[2];
        }

        return clusterId;
    }
}
