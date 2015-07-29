/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.VirtualPool;

@SuppressWarnings("serial")
public class VPlexProtection extends Protection {
    // Target (for protection only)
    private URI targetVplexDevice;
    // The target virtual array for the recommendation.
    private URI targetVarray;
    // The target vpool for the recommendation
    private VirtualPool targetVpool;
    private List<Recommendation> targetVPlexHaRecommendations;

    public URI getTargetVplexDevice() {
        return targetVplexDevice;
    }

    public void setTargetVplexDevice(URI targetVplexDevice) {
        this.targetVplexDevice = targetVplexDevice;
    }

    public URI getTargetVarray() {
        return targetVarray;
    }

    public void setTargetVarray(URI targetVarray) {
        this.targetVarray = targetVarray;
    }

    public VirtualPool getTargetVpool() {
        return targetVpool;
    }

    public void setTargetVpool(VirtualPool targetVpool) {
        this.targetVpool = targetVpool;
    }

    public List<Recommendation> getTargetVPlexHaRecommendations() {
        return targetVPlexHaRecommendations;
    }

    public void setTargetVPlexHaRecommendations(
            List<Recommendation> targetVPlexHaRecommendations) {
        this.targetVPlexHaRecommendations = targetVPlexHaRecommendations;
    }
}
