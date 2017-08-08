/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
@SuppressWarnings("serial")
public class BlockPerformancePolicyChangeTaskCompleter extends VolumeWorkflowCompleter {

    // Used to restore old policy information in the event of an error.
    private Map<URI, URI> oldVolumeToPerfPolicyMap;

    // Reference to a logger.
    private static final Logger logger = LoggerFactory.getLogger(BlockPerformancePolicyChangeTaskCompleter.class);

    /**
     * 
     * @param volumeURIs
     * @param oldVolumeToPerfPolicyMap
     * @param taskId
     */
    public BlockPerformancePolicyChangeTaskCompleter(List<URI> volumeURIs,
            Map<URI, URI> oldVolumeToPerfPolicyMap, String taskId) {
        super(volumeURIs, taskId);
        this.oldVolumeToPerfPolicyMap = oldVolumeToPerfPolicyMap;
    }
}
