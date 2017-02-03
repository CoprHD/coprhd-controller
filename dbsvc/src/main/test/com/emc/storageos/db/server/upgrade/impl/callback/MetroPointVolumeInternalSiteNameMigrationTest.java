/*
 * Copyright (c) 2017 Dell EMC Corporation
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
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.MetroPointVolumeInternalSiteNameMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test that invalid internalSiteName values are overwritten with correct values for
 * MetroPoint VPlex source volumes.
 *
 * This class tests the following migration callback classes:
 * - MetroPointVolumeInternalSiteNameMigration
 */
public class MetroPointVolumeInternalSiteNameMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(MetroPointVolumeInternalSiteNameMigrationTest.class);

    private static final String SOURCE_INTERNAL_SITE = "0x690acf1a08ae6e1e";
    private static final String STANDBY_INTERNAL_SITE = "0x3e29b09d4f02ef61";
    private static final String SOURCE_RP_COPY_NAME = "ACTIVE_PROD_COPY";
    private static final String STANDBY_RP_COPY_NAME = "STANDBY_PROD_COPY";
    private static URI sourceVirtualArrayURI = URIUtil.createId(VirtualArray.class);
    private static URI standbyVirtualArrayURI = URIUtil.createId(VirtualArray.class);

    private static List<URI> validVolumeURIs = new ArrayList<URI>();
    private static List<URI> invalidVolumeURIs = new ArrayList<URI>();
    private static URI nonMetroPointRPVplexVolumeURI;
    private static URI nonRPVolumeURI;
    private static URI nullInternalSiteNamesVolume;
    private static URI blockConsistencyGroupURI;
    private static URI protectionSetURI;

    @SuppressWarnings("serial")
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.5", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new MetroPointVolumeInternalSiteNameMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected void prepareData() throws Exception {
        blockConsistencyGroupURI = createBlockConsistencyGroup();
        protectionSetURI = createProtectionSet();

        // Prepare a mixture of "valid" VPlex MetroPoint volumes where 10 have the correct source site name and 10 have the
        // incorrect source site name.
        List<Volume> validVolumesValidInternalSite = createRPVolumeData("valid-site-vol", 10, SOURCE_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        List<Volume> validVolumesInvalidInternalSite = createRPVolumeData("invalid-site-vol", 10, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        List<Volume> validVolumes = new ArrayList<Volume>();
        validVolumes.addAll(validVolumesValidInternalSite);
        validVolumes.addAll(validVolumesInvalidInternalSite);
        for (Volume vol : validVolumes) {
            createBackingVolumes(vol, 2, sourceVirtualArrayURI, SOURCE_INTERNAL_SITE);
            _dbClient.createObject(vol);
            validVolumeURIs.add(vol.getId());
        }

        // Create a VPlex MetroPoint source volume with a null internal site name. Migration should set the
        // internal site name correctly on the source VPlex volume here.
        List<Volume> invalidSiteNameVolumes = createRPVolumeData("invalid-inernalsitename", 1, NullColumnValueGetter.getNullStr(),
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        Volume invalidSiteNameVolume = invalidSiteNameVolumes.get(0);
        createBackingVolumes(invalidSiteNameVolume, 2, sourceVirtualArrayURI, SOURCE_INTERNAL_SITE);
        _dbClient.createObject(invalidSiteNameVolume);
        validVolumeURIs.add(invalidSiteNameVolume.getId());

        // Create a VPlex MetroPoint source volume with a null RP copy name
        List<Volume> invalidCopyNameVolumes = createRPVolumeData("invalid-rpcopy", 1, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(),
                NullColumnValueGetter.getNullStr(), sourceVirtualArrayURI, true);
        Volume invalidCopyNameVolume = invalidCopyNameVolumes.get(0);
        createBackingVolumes(invalidCopyNameVolume, 2, sourceVirtualArrayURI, SOURCE_INTERNAL_SITE);
        _dbClient.createObject(invalidCopyNameVolume);
        invalidVolumeURIs.add(invalidCopyNameVolume.getId());

        // Create a VPlex MetroPoint source volume with a null virtual array
        List<Volume> invalidVirtualArrayVolumes = createRPVolumeData("invalid-varray", 1, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, NullColumnValueGetter.getNullURI(), true);
        Volume invalidVirtualArrayVolume = invalidVirtualArrayVolumes.get(0);
        createBackingVolumes(invalidVirtualArrayVolume, 2, sourceVirtualArrayURI, SOURCE_INTERNAL_SITE);
        _dbClient.createObject(invalidVirtualArrayVolume);
        invalidVolumeURIs.add(invalidVirtualArrayVolume.getId());

        // The volumes created below all have the standby internal site name set on the VPlex MetroPoint source
        // volume. The volumes are setup with invalid data preventing the migration to properly take place. We
        // want to verify that the internal site name was unable to be changed.

        // Create a VPlex MetroPoint source volume with only 1 backing volume (invalid)
        List<Volume> invalidAssociatedVolumes = createRPVolumeData("invalid-associatedvols", 1, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        Volume invalidAssociatedVolume = invalidAssociatedVolumes.get(0);
        createBackingVolumes(invalidAssociatedVolume, 1, sourceVirtualArrayURI, SOURCE_INTERNAL_SITE);
        _dbClient.createObject(invalidAssociatedVolume);
        invalidVolumeURIs.add(invalidAssociatedVolume.getId());

        // Create a VPlex MetroPoint source volume where the source side backing volume has a null virtual array
        List<Volume> invalidBackingVarrayVolumes = createRPVolumeData("invalid-backing-varray", 1, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        Volume invalidBackingVarrayVolume = invalidBackingVarrayVolumes.get(0);
        createBackingVolumes(invalidBackingVarrayVolume, 2, NullColumnValueGetter.getNullURI(), SOURCE_INTERNAL_SITE);
        _dbClient.createObject(invalidBackingVarrayVolume);
        invalidVolumeURIs.add(invalidBackingVarrayVolume.getId());

        // Create a VPlex MetroPoint source volume where the source side backing volume has a null internal site name
        List<Volume> invalidBackingSiteNameVolumes = createRPVolumeData("invalid-backing-backing-site", 1, STANDBY_INTERNAL_SITE,
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        Volume invalidBackingSiteNameVolume = invalidBackingSiteNameVolumes.get(0);
        createBackingVolumes(invalidBackingSiteNameVolume, 2, sourceVirtualArrayURI, NullColumnValueGetter.getNullStr());
        _dbClient.createObject(invalidBackingSiteNameVolume);
        invalidVolumeURIs.add(invalidBackingSiteNameVolume.getId());

        // Create a VPlex MetroPoint source volume where the parent and source side backing volumes have a null internal site name
        List<Volume> invalidSourceAndBackingSiteNameVolumes = createRPVolumeData("invalid-source-and-backing-site", 1,
                NullColumnValueGetter.getNullStr(),
                PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, true);
        Volume invalidSourceAndBackingSiteNameVolume = invalidSourceAndBackingSiteNameVolumes.get(0);
        createBackingVolumes(invalidSourceAndBackingSiteNameVolume, 2, sourceVirtualArrayURI, NullColumnValueGetter.getNullStr());
        _dbClient.createObject(invalidSourceAndBackingSiteNameVolume);
        nullInternalSiteNamesVolume = invalidSourceAndBackingSiteNameVolume.getId();

        // Create non-MetroPoint RPVPlex volume
        List<Volume> rpVplexNonMetroPointVolumes = createRPVolumeData("non-metropoint-rpvplex", 1,
                STANDBY_INTERNAL_SITE, PersonalityTypes.SOURCE.name(), SOURCE_RP_COPY_NAME, sourceVirtualArrayURI, false);
        Volume rpVplexNonMetroPointVolume = rpVplexNonMetroPointVolumes.get(0);
        createBackingVolumes(rpVplexNonMetroPointVolume, 2, sourceVirtualArrayURI, NullColumnValueGetter.getNullStr());
        _dbClient.createObject(rpVplexNonMetroPointVolume);
        nonMetroPointRPVplexVolumeURI = rpVplexNonMetroPointVolume.getId();

        // Create non-RP volume
        List<Volume> nonRpVolumes = createVolumeData("non-rp", 1, sourceVirtualArrayURI);
        Volume nonRpVolume = nonRpVolumes.get(0);
        _dbClient.createObject(nonRpVolume);
        nonRPVolumeURI = nonRpVolume.getId();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyVolumeResults();
    }

    /**
     * Creates RecoverPoint Volume objects.
     *
     * @param name the name of the volume
     * @param numVolumes the number of volumes to create
     * @param internalSiteName the internal site name of the volume(s)
     * @param personality the personality of the volume(s)
     * @param copyName the RP copy name of the volume(s)
     * @param virtualArray the virtual array of the volume(s)
     * @return a List of Volumes
     */
    private List<Volume> createRPVolumeData(String name, int numVolumes, String internalSiteName, String personality, String copyName,
            URI virtualArray, boolean isMetroPoint) {
        List<Volume> volumes = new ArrayList<Volume>();
        for (int i = 1; i <= numVolumes; i++) {
            Volume volume = new Volume();
            URI volumeURI = URIUtil.createId(Volume.class);

            String volName = name + i;

            volume.setId(volumeURI);
            volume.setLabel(name + i);
            volume.setPersonality(personality);
            volume.setRpCopyName(copyName);
            volume.setVirtualArray(virtualArray);
            volume.setInternalSiteName(internalSiteName);
            volume.setVirtualPool(createRPVirtualPool(volName + "target-varray", volName + "-vpool", isMetroPoint));
            volume.setConsistencyGroup(blockConsistencyGroupURI);
            volume.setProtectionSet(new NamedURI(protectionSetURI, name + i));

            volumes.add(volume);
        }

        return volumes;
    }

    private URI createBlockConsistencyGroup() {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        URI cgURI = URIUtil.createId(BlockConsistencyGroup.class);
        cg.setId(cgURI);
        cg.setLabel("consistencyGroup");

        _dbClient.createObject(cg);

        return cgURI;
    }

    private URI createProtectionSet() {
        ProtectionSet ps = new ProtectionSet();
        URI psURI = URIUtil.createId(ProtectionSet.class);
        ps.setId(psURI);
        ps.setLabel("protectionSet");

        _dbClient.createObject(ps);

        return psURI;
    }

    /**
     * Creates Volume objects.
     *
     * @param name the name of the volume
     * @param numVolumes the number of volumes to create
     * @param virtualArray the virtual array of the volume(s)
     * @return a List of Volumes
     */
    private List<Volume> createVolumeData(String name, int numVolumes, URI virtualArray) {
        List<Volume> volumes = new ArrayList<Volume>();
        for (int i = 1; i <= numVolumes; i++) {
            Volume volume = new Volume();
            URI volumeURI = URIUtil.createId(Volume.class);

            String volName = name + i;

            volume.setId(volumeURI);
            volume.setLabel(name + i);
            volume.setVirtualArray(virtualArray);
            volume.setVirtualPool(createVirtualPool(volName + "-vpool"));

            volumes.add(volume);
        }

        return volumes;
    }

    /**
     * Creates backing volumes for the given volume.
     *
     * @param volume the parent volume
     * @param numVolumes the number of backing volumes
     * @param sourceVirtualArray the source side virtual array
     * @param sourceInternalSiteName the source side internal site name
     */
    private void createBackingVolumes(Volume volume, int numVolumes, URI sourceVirtualArray, String sourceInternalSiteName) {
        String volumeName = volume.getLabel();

        Volume backingVolume1 = new Volume();
        URI backingVolumeURI1 = URIUtil.createId(Volume.class);
        backingVolume1.setId(backingVolumeURI1);
        backingVolume1.setLabel(volumeName + "-source");
        backingVolume1.setVirtualArray(sourceVirtualArray);
        backingVolume1.setInternalSiteName(sourceInternalSiteName);
        backingVolume1.setRpCopyName(SOURCE_RP_COPY_NAME);
        backingVolume1.setVirtualPool(createVirtualPool(volumeName + "-source-vpool"));
        _dbClient.createObject(backingVolume1);

        Volume backingVolume2 = new Volume();
        URI backingVolumeURI2 = URIUtil.createId(Volume.class);
        backingVolume2.setId(backingVolumeURI2);
        backingVolume2.setLabel(volumeName + "-standby");
        backingVolume2.setVirtualArray(standbyVirtualArrayURI);
        backingVolume2.setInternalSiteName(STANDBY_INTERNAL_SITE);
        backingVolume2.setRpCopyName(STANDBY_RP_COPY_NAME);
        backingVolume2.setVirtualPool(createVirtualPool(volumeName + "-standby-vpool"));
        _dbClient.createObject(backingVolume2);

        StringSet associatedVols = new StringSet();
        associatedVols.add(backingVolumeURI1.toString());

        // Only create the standby associated volume if requested to create 2 backing volumes
        if (numVolumes == 2) {
            associatedVols.add(backingVolumeURI2.toString());
        }

        volume.setAssociatedVolumes(associatedVols);
    }

    private URI createVirtualPool(String vpoolName) {
        VirtualPool virtualPool = new VirtualPool();
        URI virtualPoolURI = URIUtil.createId(VirtualPool.class);
        virtualPool.setId(virtualPoolURI);
        virtualPool.setLabel(vpoolName);

        _dbClient.createObject(virtualPool);

        return virtualPool.getId();
    }

    private URI createRPVirtualPool(String protectionVarrayName, String vpoolName, boolean metroPoint) {
        VirtualArray virtualArray = new VirtualArray();
        URI virtualArrayURI = URIUtil.createId(VirtualArray.class);
        virtualArray.setId(virtualArrayURI);
        virtualArray.setLabel(protectionVarrayName);
        _dbClient.createObject(virtualArray);

        VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
        URI protectionSettingsURI = URIUtil.createId(VpoolProtectionVarraySettings.class);
        protectionSettings.setId(protectionSettingsURI);
        protectionSettings.setJournalSize("min");
        _dbClient.createObject(protectionSettings);

        VirtualPool virtualPool = new VirtualPool();
        URI virtualPoolURI = URIUtil.createId(VirtualPool.class);
        virtualPool.setId(virtualPoolURI);
        virtualPool.setLabel(vpoolName);
        StringMap protectionVarraySettings = new StringMap();
        protectionVarraySettings.put(virtualArrayURI.toString(), protectionSettingsURI.toString());
        virtualPool.setProtectionVarraySettings(protectionVarraySettings);

        if (metroPoint) {
            virtualPool.setHighAvailability(VirtualPool.HighAvailabilityType.vplex_distributed.name());
            virtualPool.setMetroPoint(Boolean.TRUE);
        }

        _dbClient.createObject(virtualPool);

        return virtualPool.getId();
    }

    /**
     * Verify the migration has successfully completed.
     *
     * @throws Exception
     */
    private void verifyVolumeResults() throws Exception {
        log.info("Verifying migration of Volume.internalSiteName for MetroPoint source volumes.");

        // get the volumes
        Iterator<Volume> validVolumeItr =
                _dbClient.queryIterativeObjects(Volume.class, validVolumeURIs);
        // Get the block snapshots
        Iterator<Volume> invalidVolumeItr =
                _dbClient.queryIterativeObjects(Volume.class, invalidVolumeURIs);

        while (validVolumeItr.hasNext()) {
            Volume validVolume = validVolumeItr.next();
            Assert.assertTrue(
                    String.format("Volume %s internalSiteName field should be set to %s.", validVolume.getId(), SOURCE_INTERNAL_SITE),
                    SOURCE_INTERNAL_SITE.equals(validVolume.getInternalSiteName()));
        }

        while (invalidVolumeItr.hasNext()) {
            Volume invalidVolume = invalidVolumeItr.next();
            Assert.assertTrue(String.format("Volume %s internalSiteName field should not have been changed and should still be set to %s.",
                    invalidVolume.getId(), STANDBY_INTERNAL_SITE),
                    STANDBY_INTERNAL_SITE.equals(invalidVolume.getInternalSiteName()));
        }

        if (nullInternalSiteNamesVolume != null) {
            Volume nullInternalSiteNamesVol = _dbClient.queryObject(Volume.class, nullInternalSiteNamesVolume);
            Assert.assertTrue(String.format(
                    "Migration for Volume %s should not have happened.  The internalSiteName field should still be set to null.",
                    nullInternalSiteNamesVol.getId()),
                    NullColumnValueGetter.isNullValue(nullInternalSiteNamesVol.getInternalSiteName()));
        }

        if (nonMetroPointRPVplexVolumeURI != null) {
            Volume nonMetroPointRPVplexVolume = _dbClient.queryObject(Volume.class, nonMetroPointRPVplexVolumeURI);
            Assert.assertTrue(
                    String.format(
                            "Volume %s is not a MetroPoint volume and its internalSiteName field should not have been changed and should still be set to %s.",
                            nonMetroPointRPVplexVolume.getId(), STANDBY_INTERNAL_SITE),
                    STANDBY_INTERNAL_SITE.equals(nonMetroPointRPVplexVolume.getInternalSiteName()));
        }

        if (nonRPVolumeURI != null) {
            Volume nonRPVolume = _dbClient.queryObject(Volume.class, nonRPVolumeURI);
            Assert.assertTrue(
                    String.format(
                            "Volume %s is not a MetroPoint volume and its internalSiteName field should not have been changed and should still be set to %s.",
                            nonRPVolume.getId(), NullColumnValueGetter.getNullStr()),
                    NullColumnValueGetter.isNullValue(nonRPVolume.getInternalSiteName()));
        }
    }
}
