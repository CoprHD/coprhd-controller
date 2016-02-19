/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import static com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy.EXPORT_TYPE.REGULAR;
import static com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy.IG_TYPE.CASCADED;
import static com.emc.storageos.volumecontroller.impl.block.ExportMaskPolicy.IG_TYPE.SIMPLE;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.services.util.StorageDriverManager;

/**
 * Tester class for ExportMaskPlacementDescriptor usage.
 */
public class ExportMaskPlacementDescriptorTest {
    private static final Random RANDOMIZER = new Random(System.currentTimeMillis() % 997);
    private static final String URI_FORMAT = "%s:%s";

    private static List<String> ALL_SLOS = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        mockStorageDriverManager();
        ALL_SLOS.add("Bronze");
        ALL_SLOS.add("Silver");
        ALL_SLOS.add("Gold");
        ALL_SLOS.add("Platinum");
        ALL_SLOS.add("Diamond");
        ALL_SLOS.add("Unobtainium");
    }

    private static void mockStorageDriverManager() {
        StorageDriverManager storageDriverManager = new StorageDriverManager();
        storageDriverManager.setApplicationContext(new ClassPathXmlApplicationContext("driver-conf.xml"));
    }

    /**
     * ====================================================================================================================================
     * Test: validate that we can use the ExportMaskPlacementDescriptor place a set of volumes against a set of ExportMasks
     * ====================================================================================================================================
     */
    @Test
    public void testPlaceAllVolumes() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        // Place all the volumes for each mask
        Iterator<URI> iterator = masks.keySet().iterator();
        descriptor.placeVolumes(iterator.next(), descriptor.getVolumesToPlace());
        descriptor.placeVolumes(iterator.next(), descriptor.getVolumesToPlace());

        // ---- Test validation ----
        // After placing all the volumes to each ExportMask, check the values
        // returned by the descriptor.
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertTrue(descriptor.getPlacedMasks().containsAll(masks.keySet()));
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Placed all volumes:\n%s", descriptor);
    }

    /**
     * ====================================================================================================================================
     * Test: Randomized placement.
     * - Create a random set of Volumes
     * - Create a random set of ExportMasks
     * - Determine a random set of Volumes to place
     * - Determine a random set of ExportMasks to use
     * - Place all the volumes against the chosen set of ExportMasks
     * - Test the accuracy of placed and unplaced volumes
     * ====================================================================================================================================
     */
    @Test
    public void testPlaceVolumesRandomly() {
        // Create descriptor with random number of volumes between 2 and 10
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, RANDOMIZER.nextInt(8) + 2);
        // Create mock ExportMasks (between 2 and 10 of them)
        Map<URI, ExportMask> masks = mockExportMasks(RANDOMIZER.nextInt(8) + 2);
        descriptor.setMasks(masks);

        // Select the number of masks to use for placement
        int numMasksToUse = (RANDOMIZER.nextInt(masks.size()) / 2) + 1;

        // Create random assortment of volumes to place
        Map<URI, Volume> volumes = randomVolumeMap(descriptor);
        System.out.printf("We're picking %d volumes from the descriptor that has %d volumes\n", volumes.size(),
                descriptor.getVolumesToPlace().size());
        System.out.printf("We will be placing the volumes into %d out of %d masks\n", numMasksToUse, masks.size());

        // Place all the volumes
        Iterator<URI> iterator = masks.keySet().iterator();
        for (int i = 0; i < numMasksToUse; i++) {
            descriptor.placeVolumes(iterator.next(), volumes);
        }

        // ---- Test validation ----
        // After placing the volumes, check that the numbers returned by the descriptor.
        Map<URI, Volume> volumesToPlace = descriptor.getVolumesToPlace();
        Map<URI, Volume> unplacedVolumes = descriptor.getUnplacedVolumes();
        if (volumes.size() != volumesToPlace.size()) {
            Assert.assertTrue(descriptor.hasUnPlacedVolumes());
            Assert.assertTrue(unplacedVolumes.size() != 0);
            int unplacedVolumeCount = volumesToPlace.size() - volumes.size();
            Assert.assertEquals(unplacedVolumeCount, unplacedVolumes.size());
            System.out.printf("We placed %d volumes and the descriptor shows that there are %d unplaced volumes\n", volumes.size(),
                    unplacedVolumes.size());
        } else {
            Assert.assertFalse(descriptor.hasUnPlacedVolumes());
            Assert.assertTrue(unplacedVolumes.size() == 0);
            System.out.printf("All volumes were placed");
        }
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptor#getEquivalentExportMasks
     * - Create volumes and a set of ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Validate getEquivalentExportMasks results
     * ====================================================================================================================================
     */
    @Test
    public void testAddToEquivalentMasksSamePolicy() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);

        Set<URI> eqToMask1 = descriptor.getEquivalentExportMasks(xp.get(0).getId());
        Set<URI> eqToMask2 = descriptor.getEquivalentExportMasks(xp.get(1).getId());
        System.out.printf("ExportMasks %s and %s have been set as equivalent exports\n", xp.get(0).getMaskName(), xp.get(1).getMaskName());

        // ---- Test validation ----
        // Since we created a single ExportMaskPolicy and used that for each ExportMask,
        // there should be equivalent ExportMasks returned, but make sure that it does
        // not contain the ExportMask that your checking for equivalence.
        Assert.assertTrue(eqToMask1.contains(xp.get(1).getId()));
        Assert.assertFalse(eqToMask1.contains(xp.get(0).getId()));
        System.out.printf("Equivalent exports for %s is correct\n", xp.get(0).getMaskName());

        Assert.assertTrue(eqToMask2.contains(xp.get(0).getId()));
        Assert.assertFalse(eqToMask2.contains(xp.get(1).getId()));
        System.out.printf("Equivalent exports for %s is correct\n", xp.get(1).getMaskName());

        // Place all volumes in one mask
        Iterator<URI> iterator = masks.keySet().iterator();
        descriptor.placeVolumes(iterator.next(), descriptor.getVolumesToPlace());

        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.hasMasks());
        System.out.printf("Descriptor showed that we could get the correct set of equivalent exportMasks");
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptor#getEquivalentExportMasks
     * - Create volumes and a set of ExportMasks
     * - Create different ExportMaskPolicies
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate different ExportMaskPolicy
     * - Validate getEquivalentExportMasks results
     * ====================================================================================================================================
     */
    @Test
    public void testAddToEquivalentMasksDiffPolicy() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy1 = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);
        ExportMaskPolicy policy2 = mockExportMaskPolicy(REGULAR.name(), SIMPLE.name(), null, "mask2SgName", true, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy1);
        descriptor.addToEquivalentMasks(xp.get(1), policy2);

        Set<URI> eqToMask1 = descriptor.getEquivalentExportMasks(xp.get(0).getId());
        Set<URI> eqToMask2 = descriptor.getEquivalentExportMasks(xp.get(1).getId());

        // ---- Test validation ----
        // We have different policies for each ExportMask, so we shouldn't get any equivalent masks
        Assert.assertTrue(eqToMask1.isEmpty());
        System.out.printf("Equivalent exports for %s is correct\n", xp.get(0).getMaskName());
        Assert.assertTrue(eqToMask2.isEmpty());
        System.out.printf("Equivalent exports for %s is correct\n", xp.get(1).getMaskName());

        // Place all volumes in one mask
        Iterator<URI> iterator = masks.keySet().iterator();
        descriptor.placeVolumes(iterator.next(), descriptor.getVolumesToPlace());

        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.hasMasks());
        System.out.printf("Descriptor showed that equivalentExportMasks when queried");
    }

    /**
     * ====================================================================================================================================
     * Test: Randomized ExportMaskPlacementDescriptor#getEquivalentExportMasks
     * - Create volumes and a set of ExportMasks
     * - Create a randomized set of ExportMaskPolicies
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate to the policies created
     * - Validate getEquivalentExportMasks results
     * ====================================================================================================================================
     */
    @Test
    public void testAddToEquivalentMasksRandomPolicy() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(4);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        int size = RANDOMIZER.nextInt(ALL_SLOS.size());
        for (int i = 0; i < size; i++) {
            slos.add(ALL_SLOS.get(i));
        }

        List<ExportMask> xp = new ArrayList<>(masks.values());
        ExportMaskPolicy.EXPORT_TYPE[] exportTypes = ExportMaskPolicy.EXPORT_TYPE.values();
        ExportMaskPolicy.IG_TYPE[] exportIgTypes = ExportMaskPolicy.IG_TYPE.values();

        for (int j = 0; j < 4; j++) {
            String xpType = exportTypes[RANDOMIZER.nextInt(exportTypes.length)].name();
            String igType = exportIgTypes[RANDOMIZER.nextInt(exportIgTypes.length)].name();

            ExportMaskPolicy policy = mockExportMaskPolicy(xpType, igType, null, String.format("mask%dSg", j), RANDOMIZER.nextBoolean(),
                    slos, 0, 0);
            ExportMask mask = xp.get(j);
            descriptor.addToEquivalentMasks(mask, policy);
            System.out.println(String.format("ExportMask %s has policy %s", mask.getMaskName(), policy.toString()));
        }

        Set<URI> eqToMask1 = descriptor.getEquivalentExportMasks(xp.get(0).getId());
        Set<URI> eqToMask2 = descriptor.getEquivalentExportMasks(xp.get(1).getId());
        Set<URI> eqToMask3 = descriptor.getEquivalentExportMasks(xp.get(2).getId());
        Set<URI> eqToMask4 = descriptor.getEquivalentExportMasks(xp.get(3).getId());

        // ---- Test validation ----
        // We did a random creation of ExportMaskPolicy for each ExportMask.
        // Check that each ExportMask does not show up in its equivalence set.
        if (!eqToMask1.isEmpty()) {
            System.out.printf("There are equivalent masks for %s\n", xp.get(0).getMaskName());
            Assert.assertFalse(eqToMask1.contains(xp.get(0).getId()));
        } else {
            System.out.printf("There are no equivalent masks for %s\n", xp.get(0).getMaskName());
        }

        if (!eqToMask2.isEmpty()) {
            System.out.printf("There are equivalent masks for %s\n", xp.get(1).getMaskName());
            Assert.assertFalse(eqToMask2.contains(xp.get(1).getId()));
        } else {
            System.out.printf("There are no equivalent masks for %s\n", xp.get(1).getMaskName());
        }

        if (!eqToMask3.isEmpty()) {
            System.out.printf("There are equivalent masks for %s\n", xp.get(2).getMaskName());
            Assert.assertFalse(eqToMask3.contains(xp.get(2).getId()));
        } else {
            System.out.printf("There are no equivalent masks for %s\n", xp.get(2).getMaskName());
        }

        if (!eqToMask4.isEmpty()) {
            System.out.printf("There are equivalent masks for %s\n", xp.get(3).getMaskName());
            Assert.assertFalse(eqToMask4.contains(xp.get(3).getId()));
        } else {
            System.out.printf("There are no equivalent masks for %s\n", xp.get(3).getMaskName());
        }

        // Place all volumes in one mask
        Iterator<URI> iterator = masks.keySet().iterator();
        descriptor.placeVolumes(iterator.next(), descriptor.getVolumesToPlace());

        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.hasMasks());
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptor#invalidateExportMask
     * - Create volumes and a set of ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes to a single ExportMask
     * - Invalidate the ExportMask used for placement
     * - Validate that ExportMask shows unplaced volumes and other attributes related to the invalidation are correct.
     * ====================================================================================================================================
     */
    @Test
    public void testInvalidateExportMask1() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);

        // Place volumes into **one** ExportMask
        descriptor.placeVolumes(xp.get(0).getId(), descriptor.getVolumesToPlace());

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(2, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor before invalidation:\n%s\n", descriptor);

        // Invalidate the ExportMask
        descriptor.invalidateExportMask(xp.get(0).getId());

        // ---- Test validation ----
        // The volumes were placed against a single ExportMask, which we invalidated. We should
        // see that the volumes were unplaced afterwords.
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(1, descriptor.getMasks().size());
        Assert.assertFalse(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertTrue(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor after invalidation:\n%s", descriptor);
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptor#invalidateExportMask
     * - Create volumes and a set of ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes to *all* ExportMasks
     * - Invalidate the ExportMask used for placement
     * - Validate that ExportMask does not show unplaced volumes and other attributes related to that are correct
     * ====================================================================================================================================
     */
    @Test
    public void testInvalidateExportMask2() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);

        // Place volumes into **all** ExportMasks
        descriptor.placeVolumes(xp.get(0).getId(), descriptor.getVolumesToPlace());
        descriptor.placeVolumes(xp.get(1).getId(), descriptor.getVolumesToPlace());

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(2, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor before invalidation:\n%s\n", descriptor);

        // Invalidate the ExportMask
        descriptor.invalidateExportMask(xp.get(0).getId());

        // ---- Test validation ----
        // Volumes were placed against both ExportMasks. When we invalidated one of the ExportMasks,
        // the other ExportMask should still have a mapping for the volumes
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(1, descriptor.getMasks().size());
        Assert.assertFalse(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor after invalidation:\n%s", descriptor);
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptorHelper#getExportMaskWithLeastVolumes
     * - Create volumes
     * - Create 3 ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes into 1st ExportMask
     * - Add different volumes into 2nd ExportMask and 3rd ExportMask, making sure that 3rd ExportMask has more volumes than 2nd.
     * - Invalidate the 1st ExportMask used for placement
     * - Validate that ExportMask shows unplaced volumes and other attributes related to the invalidation are correct.
     * - Call ExportMaskPlacementDescriptorHelper#getExportMaskWithLeastVolumes
     * - Make sure that it shows that 2nd ExportMask is selected
     * ====================================================================================================================================
     */
    @Test
    public void testGetAlternateExportMasksForVolume1() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(3);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);
        descriptor.addToEquivalentMasks(xp.get(2), policy);

        int volumeCount1 = RANDOMIZER.nextInt(10) + 1;
        Map<URI, Volume> volumes1 = mockSetOfVolumes(descriptor.getBackendArray(), volumeCount1);
        Map<URI, Integer> volToHLU1 = mockVolumeToHLUs(volumes1);
        xp.get(1).addVolumes(volToHLU1);
        xp.get(1).addToUserCreatedVolumes(mockVolumesToBlockObjects(volumes1));

        // Make the 3rd ExportMask always have more volumes, that way xp.get(1) should be selected over xp.get(2)
        int volumeCount2 = volumeCount1 + RANDOMIZER.nextInt(5) + 1;
        Map<URI, Volume> volumes2 = mockSetOfVolumes(descriptor.getBackendArray(), volumeCount2);
        Map<URI, Integer> volToHLU2 = mockVolumeToHLUs(volumes2);
        xp.get(2).addVolumes(volToHLU2);
        xp.get(2).addToUserCreatedVolumes(mockVolumesToBlockObjects(volumes2));

        Map<URI, Volume> volumesToPlace = descriptor.getVolumesToPlace();

        // Place volumes into **one** ExportMask
        descriptor.placeVolumes(xp.get(0).getId(), volumesToPlace);

        // Add the other ExportMasks as alternates since they use the same ExportMaskPolicy
        for (URI volumeURI : volumesToPlace.keySet()) {
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(1).getId());
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(2).getId());
        }

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(3, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(1).getId()).isEmpty());
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(2).getId()).isEmpty());
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor before invalidation:\n%s\n", descriptor);

        // Invalidate the ExportMask that was placed
        descriptor.invalidateExportMask(xp.get(0).getId());

        // Validate that we have unplaced volumes
        Assert.assertTrue(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.getUnplacedVolumes().size() != 0);

        // Find the one with the least number of volumes
        Set<URI> equivalentMasks = descriptor.getEquivalentExportMasks(xp.get(0).getId());
        URI leastVolumeExportMaskURI = ExportMaskPlacementDescriptorHelper.getExportMaskWithLeastVolumes(descriptor, equivalentMasks);

        int xp1VolCount = xp.get(1).returnTotalVolumeCount();
        int xp2VolCount = xp.get(2).returnTotalVolumeCount();
        System.out.printf("Mask %s has %d volumes, Mask %s has %d volumes\n", xp.get(1).getMaskName(), xp1VolCount, xp.get(2).getMaskName(),
                xp2VolCount);

        // ---- Test validation ----
        // See which export got the least number of volumes
        Assert.assertNotNull(leastVolumeExportMaskURI);
        URI expected = (xp1VolCount < xp2VolCount) ? xp.get(1).getId() : xp.get(2).getId();
        Assert.assertEquals(expected, leastVolumeExportMaskURI);
        System.out.printf("Mask %s was correctly selected, since it has less volumes", xp.get(1).getMaskName());
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptorHelper#getExportMaskWithLeastVolumes
     * - Create volumes
     * - Create 3 ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes into 1st ExportMask
     * - Add the same number of volumes into the 2nd and 3rd ExportMasks
     * - Invalidate the 1st ExportMask used for placement
     * - Validate that ExportMask shows unplaced volumes and other attributes related to the invalidation are correct.
     * - Call ExportMaskPlacementDescriptorHelper#getExportMaskWithLeastVolumes
     * - Make sure that it chose one of the equivalent ExportMasks
     * ====================================================================================================================================
     */
    @Test
    public void testGetAlternateExportMasksForVolume2() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(3);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);
        descriptor.addToEquivalentMasks(xp.get(2), policy);

        int volumeCount = RANDOMIZER.nextInt(10) + 1;
        Map<URI, Volume> volumes1 = mockSetOfVolumes(descriptor.getBackendArray(), volumeCount);
        Map<URI, Integer> volToHLU1 = mockVolumeToHLUs(volumes1);
        xp.get(1).addVolumes(volToHLU1);
        xp.get(1).addToUserCreatedVolumes(mockVolumesToBlockObjects(volumes1));

        // Make the 3rd ExportMask have same number of volumes
        Map<URI, Volume> volumes2 = mockSetOfVolumes(descriptor.getBackendArray(), volumeCount);
        Map<URI, Integer> volToHLU2 = mockVolumeToHLUs(volumes2);
        xp.get(2).addVolumes(volToHLU2);
        xp.get(2).addToUserCreatedVolumes(mockVolumesToBlockObjects(volumes2));

        Map<URI, Volume> volumesToPlace = descriptor.getVolumesToPlace();

        // Place volumes into **one** ExportMask
        descriptor.placeVolumes(xp.get(0).getId(), volumesToPlace);

        // Add the other ExportMasks as alternates since they use the same ExportMaskPolicy
        for (URI volumeURI : volumesToPlace.keySet()) {
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(1).getId());
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(2).getId());
        }

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(3, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(1).getId()).isEmpty());
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(2).getId()).isEmpty());
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor before invalidation:\n%s\n", descriptor);

        // Invalidate the ExportMask that was placed
        descriptor.invalidateExportMask(xp.get(0).getId());

        // Validate that we have unplaced volumes
        Assert.assertTrue(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.getUnplacedVolumes().size() != 0);

        // Find the one with the least number of volumes
        Set<URI> equivalentMasks = descriptor.getEquivalentExportMasks(xp.get(0).getId());
        URI leastVolumeExportMaskURI = ExportMaskPlacementDescriptorHelper.getExportMaskWithLeastVolumes(descriptor, equivalentMasks);

        int xp1VolCount = xp.get(1).returnTotalVolumeCount();
        int xp2VolCount = xp.get(2).returnTotalVolumeCount();
        System.out.printf("Mask %s has %d volumes, Mask %s has %d volumes\n", xp.get(1).getMaskName(), xp1VolCount, xp.get(2).getMaskName(),
                xp2VolCount);

        // ---- Test validation ----
        // See which export got the least number of volumes
        Assert.assertNotNull(leastVolumeExportMaskURI);
        Assert.assertTrue(equivalentMasks.contains(leastVolumeExportMaskURI));
        System.out.printf("Mask selected was %s", leastVolumeExportMaskURI);
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptor#addAsAlternativeExportForVolume
     * - Create volumes
     * - Create 2 ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes into 1st ExportMask
     * - Call ExportMaskPlacementDescriptor#addAsAlternativeExportForVolume
     * - Validate that correct alternatives are shown for the given volumes
     * ====================================================================================================================================
     */
    @Test
    public void testGetAlternateExportMasksForVolume3() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);

        Map<URI, Volume> volumesToPlace = descriptor.getVolumesToPlace();
        // Place volumes into **one** ExportMask
        descriptor.placeVolumes(xp.get(0).getId(), volumesToPlace);

        // Provide a way for say that the volume could have equally been placed
        // in the other ExportMask
        for (URI volumeURI : volumesToPlace.keySet()) {
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(1).getId());
        }

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(2, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor:\n%s\n", descriptor);

        // Validate that we can find equivalent ExportMasks for each of the volumes
        for (URI volumeURI : volumesToPlace.keySet()) {
            Set<URI> alternates = descriptor.getAlternativeExportsForVolume(volumeURI);
            Assert.assertFalse(alternates.isEmpty());
            Assert.assertFalse(alternates.contains(xp.get(0).getId()));
            Assert.assertTrue(alternates.contains(xp.get(1).getId()));
        }
        System.out.printf("Descriptor showed that we got the expected results when querying the alternative exports for the volumes");
    }

    /**
     * ====================================================================================================================================
     * Test: ExportMaskPlacementDescriptorHelper#putUnplacedVolumesIntoAlternativeMask
     * - Create volumes
     * - Create 2 ExportMasks
     * - Create a single ExportMaskPolicy
     * - Use ExportMaskPlacementDescriptor#addToEquivalentMasks to associate the same policy for all the created ExportMasks
     * - Place all volumes into 1st ExportMask
     * - Call ExportMaskPlacementDescriptor#addAsAlternativeExportForVolume to associate 2nd ExportMask as an alternate
     * - Call putUnplacedVolumesIntoAlternativeMask
     * - Validate the descriptor shows that an an alternative ExportMask was used for volume placement
     * ====================================================================================================================================
     */
    @Test
    public void testGetAlternateExportMasksForVolume4() {
        // Create a simple descriptor with some ExportMasks to place
        ExportMaskPlacementDescriptor descriptor = createDescriptor(2, 2);
        Map<URI, ExportMask> masks = mockExportMasks(2);
        descriptor.setMasks(masks);

        Set<String> slos = new HashSet<>();
        slos.add("Gold");
        slos.add("Green");

        ExportMaskPolicy policy = mockExportMaskPolicy(REGULAR.name(), CASCADED.name(), null, "mask1SgName", false, slos, 0, 0);

        List<ExportMask> xp = new ArrayList<>(masks.values());
        descriptor.addToEquivalentMasks(xp.get(0), policy);
        descriptor.addToEquivalentMasks(xp.get(1), policy);

        Map<URI, Volume> volumesToPlace = descriptor.getVolumesToPlace();
        // Place volumes into **one** ExportMask
        descriptor.placeVolumes(xp.get(0).getId(), volumesToPlace);

        // Provide a way for say that the volume could have equally been placed
        // in the other ExportMask
        for (URI volumeURI : volumesToPlace.keySet()) {
            descriptor.addAsAlternativeExportForVolume(volumeURI, xp.get(1).getId());
        }

        // ---- Test validation ----
        // Preliminary validation of the placement
        Assert.assertTrue(descriptor.hasMasks());
        Assert.assertEquals(2, descriptor.getMasks().size());
        Assert.assertTrue(descriptor.getPlacedMasks().contains(xp.get(0).getId()));
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(1).getId()).isEmpty());
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        System.out.printf("Descriptor before invalidation:\n%s\n", descriptor);

        // Invalidate the ExportMask that was placed
        Set<URI> invalidMasks = new HashSet<>();
        invalidMasks.add(xp.get(0).getId());
        descriptor.invalidateExportMask(xp.get(0).getId());

        // Validate that we have unplaced volumes
        Assert.assertTrue(descriptor.hasUnPlacedVolumes());
        Assert.assertTrue(descriptor.getUnplacedVolumes().size() != 0);

        // Validate that we can find equivalent ExportMasks for each of the volumes
        for (URI volumeURI : volumesToPlace.keySet()) {
            Set<URI> alternates = descriptor.getAlternativeExportsForVolume(volumeURI);
            Assert.assertFalse(alternates.isEmpty());
            Assert.assertFalse(alternates.contains(xp.get(0).getId()));
            Assert.assertTrue(alternates.contains(xp.get(1).getId()));
        }

        // Run the helper method to replace volumes to alternative ExportMasks
        ExportMaskPlacementDescriptorHelper.putUnplacedVolumesIntoAlternativeMask(descriptor, invalidMasks);

        // Now we should have placed volumes
        Assert.assertFalse(descriptor.hasUnPlacedVolumes());
        Assert.assertFalse(descriptor.getUnplacedVolumes().size() != 0);
        Assert.assertFalse(descriptor.getPlacedVolumes(xp.get(1).getId()).isEmpty());
        Assert.assertTrue(descriptor.getPlacedVolumes(xp.get(0).getId()).isEmpty());

        Map<URI, Volume> volumeMapForAlternateXP = descriptor.getPlacedVolumes(xp.get(1).getId());
        Assert.assertTrue(volumesToPlace.keySet().containsAll(volumeMapForAlternateXP.keySet()));
        System.out.printf("Descriptor after putUnplacedVolumesIntoAlternativeMask:\n%s\n", descriptor);
    }

    // =================== HELPER & MOCK OBJECT CREATION METHODS ===================

    private Set<BlockObject> mockVolumesToBlockObjects(Map<URI, Volume> volumes) {
        Set<BlockObject> set = new HashSet<>();
        for (Volume volume : volumes.values()) {
            set.add(volume);
        }
        return set;
    }

    private Map<URI, Integer> mockVolumeToHLUs(Map<URI, Volume> volumeMap) {
        Map<URI, Integer> map = new HashMap<>();
        int i = 0;
        for (URI volumeURI : volumeMap.keySet()) {
            map.put(volumeURI, i++);
        }
        return map;
    }

    private Map<URI, Volume> randomVolumeMap(ExportMaskPlacementDescriptor descriptor) {
        Map<URI, Volume> map = new HashMap<>();
        Map<URI, Volume> volumes = descriptor.getVolumesToPlace();
        int count = RANDOMIZER.nextInt(volumes.size()) + 1;
        Iterator<URI> iterator = volumes.keySet().iterator();
        for (int i = 0; i < count; i++) {
            URI uri = iterator.next();
            map.put(uri, volumes.get(uri));
        }
        return map;
    }

    private URI mockURI(String type) {
        return URI.create(String.format(URI_FORMAT, type, UUID.randomUUID()));
    }

    private ExportMaskPlacementDescriptor createDescriptor(int numInitiators, int numVolumes) {
        URI tenant = mockURI("TenantOrg");
        URI project = mockURI("Project");
        URI virtualArray = mockURI("VirtualArray");

        Host host = mockHost("host.somewhere.com");
        StorageSystem vplex = mockStorageSystem("vplex");
        StorageSystem array = mockStorageSystem("backend-array");

        Set<Initiator> initiators = mockSetOfInitiators(host, numInitiators);
        Map<URI, Volume> volumes = mockSetOfVolumes(array, numVolumes);

        return ExportMaskPlacementDescriptor.create(tenant, project, vplex, array, virtualArray,
                volumes, initiators);
    }

    private StorageSystem mockStorageSystem(String label) {
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setId(mockURI("StorageSystem"));
        storageSystem.setLabel(label);
        return storageSystem;
    }

    private Volume mockVolume(StorageSystem storageSystem, String label) {
        Volume volume = new Volume();
        volume.setId(mockURI("Volume"));
        volume.setLabel(label);
        volume.setStorageController(storageSystem.getId());
        volume.setWWN(UUID.randomUUID().toString().toUpperCase());
        return volume;
    }

    private Host mockHost(String label) {
        Host host = new Host();
        host.setId(mockURI("Host"));
        host.setLabel(label);
        host.setHostName(label);
        return host;
    }

    private Initiator mockInitiator(Host host, String nwwn, String pwwn) {
        Initiator initiator = new Initiator();
        initiator.setId(mockURI("Initiator"));
        initiator.setHost(host.getId());
        initiator.setInitiatorNode(nwwn);
        initiator.setInitiatorPort(pwwn);
        return initiator;
    }

    private Map<URI, Volume> mockSetOfVolumes(StorageSystem storageSystem, int count) {
        Map<URI, Volume> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            Volume volume = mockVolume(storageSystem, String.format("vol%d", i));
            map.put(volume.getId(), volume);
        }
        return map;
    }

    private Set<Initiator> mockSetOfInitiators(Host host, int count) {
        Set<Initiator> set = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Initiator initiator = mockInitiator(host, "21:22:33:44:55:66:77:00", String.format("11:22:33:44:55:66:77:%02X", i));
            set.add(initiator);
        }
        return set;
    }

    private ExportMaskPolicy mockExportMaskPolicy(String exportType, String igType, String localTierPolicy, String sgName, boolean isSimple,
            Set<String> tierPolicies, int ioBWLimit, int iopsLimit) {
        ExportMaskPolicy policy = new ExportMaskPolicy();
        policy.setExportType(exportType);
        policy.setIgType(igType);
        policy.setLocalTierPolicy(localTierPolicy);
        policy.setSgName(sgName);
        policy.setSimpleMask(isSimple);
        policy.setTierPolicies(new StringSet(tierPolicies));
        policy.setHostIOLimitBandwidth(ioBWLimit);
        policy.setHostIOLimitIOPs(iopsLimit);
        policy.setMaxVolumesAllowed(4000);
        return policy;
    }

    private ExportMask mockExportMask(String maskName) {
        ExportMask mask = new ExportMask();
        mask.setId(mockURI("ExportMask"));
        mask.setMaskName(maskName);
        return mask;
    }

    private Map<URI, ExportMask> mockExportMasks(int number) {
        Map<URI, ExportMask> map = new HashMap<>();
        for (int i = 0; i < number; i++) {
            ExportMask mask = mockExportMask(String.format("mask%d", i));
            map.put(mask.getId(), mask);
        }
        return map;
    }
}
