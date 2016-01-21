/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.google.common.collect.Lists;

public final class TestBlockStorageUtils {

    static final List<BlockObjectRestRep> newGetBlockResources(ViPRCoreClient client, Collection<URI> uris) {
        List<BlockObjectRestRep> blockResources = Lists.newArrayList();
        List<URI> blockVolumes = new ArrayList<URI>();
        List<URI> blockSnapshots = new ArrayList<URI>();
        for (URI resourceId : uris) {
            ResourceType volumeType = ResourceType.fromResourceId(resourceId.toString());
            switch (volumeType) {
                case VOLUME:
                    blockVolumes.add(resourceId);
                    break;
                case BLOCK_SNAPSHOT:
                    blockSnapshots.add(resourceId);
                    break;
                default:
                    break;
            }
        }
        blockResources.addAll(client.blockVolumes().getByIds(blockVolumes));
        blockResources.addAll(client.blockSnapshots().getByIds(blockSnapshots));
        return blockResources;
    }

    static final List<BlockObjectRestRep> originalGetBlockResources(ViPRCoreClient client, Collection<URI> uris) {
        List<BlockObjectRestRep> returnList = new ArrayList<>();
        for (URI uri : uris) {
            BlockObjectRestRep s = getSingle(client, uri);
            if (s != null) {
                returnList.add(s);
            }
            returnList.add(getSingle(client, uri));
        }
        return returnList;
    }

    static final BlockObjectRestRep getSingle(ViPRCoreClient client, URI resourceId) {

        ResourceType volumeType = ResourceType.fromResourceId(resourceId.toString());
        switch (volumeType) {
            case VOLUME:
                VolumeRestRep volume = client.blockVolumes().get(resourceId);
                if (volume != null) {
                    return volume;
                }
                break;
            case BLOCK_SNAPSHOT:
                BlockSnapshotRestRep snapshot = client.blockSnapshots().get(resourceId);
                if (snapshot != null) {
                    return snapshot;
                }
                break;
            default:
                break;
        }
        return null;
    }

    static List<URI> prepare(ViPRCoreClient client) throws URISyntaxException {

        List<URI> returnList = new ArrayList<>();

        ProjectRestRep project = client.projects().get(new URI("urn:storageos:Project:3e1ef32b-3091-4a83-9fac-4a2a3bc30413:global"));

        for (BlockSnapshotRestRep r : client.blockSnapshots().findByProject(project)) {
            returnList.add(r.getId());
        }

        for (VolumeRestRep r : client.blockVolumes().findByProject(project)) {
            returnList.add(r.getId());
        }
        return returnList;
    }

    @Test
    public void testGetBlockResources() throws URISyntaxException {
        Logger.getRootLogger().setLevel(Level.OFF);
        ViPRCoreClient client =
                new ViPRCoreClient("host", true).withLogin("root", "password");
        try {
            List<URI> uris = prepare(client);
            StopWatch w = new StopWatch();
            w.start();
            List<BlockObjectRestRep> original = originalGetBlockResources(client, uris);
            w.stop();
            float firstTime = w.getTime();
            System.out.println(String.format("Original query time with %s volumes : %s ms", original.size(), firstTime));

            w.reset();
            w.start();
            List<BlockObjectRestRep> fixed = newGetBlockResources(client, uris);
            w.stop();
            float secondTime = w.getTime();
            System.out.println(String.format("Updated query time with %s volumes : %s ms", fixed.size(), w.getTime()));
            System.out.println(String.format("Improvement : %s %%", firstTime / secondTime * 100.0f));

            Assert.assertTrue(firstTime > secondTime);

        } finally {
            client.auth().logout();
        }
    }
}
