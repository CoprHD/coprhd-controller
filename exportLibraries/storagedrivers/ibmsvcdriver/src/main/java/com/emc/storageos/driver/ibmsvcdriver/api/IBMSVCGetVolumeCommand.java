/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.api;

import java.util.List;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.google.common.base.Joiner;

public class IBMSVCGetVolumeCommand extends AbstractIBMSVCQueryCommand<IBMSVCGetVolumeResult> {

    public static final String VOLUME_ID = "VolumeId";
    public static final String VOLUME_NAME = "VolumeName";
    public static final String VOLUME_CAPACITY = "VolumeCapacity";
    public static final String VOLUME_WWN = "VolumeWWN";
    public static final String POOL_ID = "PoolId";
    public static final String POOL_NAME = "PoolName";
    public static final String PREFERRED_NODE_ID = "PreferredNodeId";
    public static final String FC_MAP_COUNT = "FCMapCount";
    public static final String COPY_COUNT = "CopyCount";
    public static final String SE_COPY_COUNT = "SECopyCount";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("id:(.*)", VOLUME_ID),
            new ParsePattern("name:(.*)", VOLUME_NAME),
            new ParsePattern("capacity:(.*)", VOLUME_CAPACITY),
            //new ParsePattern("capacity:(\\d+\\.\\d+)GB", VOLUME_CAPACITY),
            new ParsePattern("vdisk_UID:(.*)", VOLUME_WWN),
            new ParsePattern("mdisk_grp_id:(.*)", POOL_ID),
            new ParsePattern("mdisk_grp_name:(.*)", POOL_NAME),
            new ParsePattern("preferred_node_id:(.*)", PREFERRED_NODE_ID),
            new ParsePattern("fc_map_count:(.*)", FC_MAP_COUNT),
            new ParsePattern("copy_count:(.*)", COPY_COUNT),
            new ParsePattern("se_copy_count:(.*)", SE_COPY_COUNT)
    };

    public IBMSVCGetVolumeCommand(String volumeId) {
        addArgument("svcinfo lsvdisk -delim :");
        addArgument(String.format("%s", volumeId));
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCGetVolumeResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        results.setProperty(spec.getPropertyName(), Joiner.on(',').join(capturedStrings));
        results.setSuccess(true);
    }
}
