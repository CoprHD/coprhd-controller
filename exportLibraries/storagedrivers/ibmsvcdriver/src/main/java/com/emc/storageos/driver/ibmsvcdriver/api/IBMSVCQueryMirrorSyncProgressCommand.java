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

public class IBMSVCQueryMirrorSyncProgressCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryMirrorSyncProgressResult> {

    public static final String VOLUME_ID = "VolumeId";
    public static final String VOLUME_NAME = "VolumeName";
    public static final String COPY_ID = "PoolId";
    public static final String SYNC_PROGRESS = "SyncProgress";
    public static final String EST_COMPLETION_TIME = "EstCompletionTime";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("vdisk_id:(.*)", VOLUME_ID),
            new ParsePattern("vdisk_name:(.*)", VOLUME_NAME),
            new ParsePattern("copy_id:(.*)", COPY_ID),
            new ParsePattern("progress:(.*)", SYNC_PROGRESS),
            new ParsePattern("estimated_completion_time:(.*)", EST_COMPLETION_TIME)
    };

    public IBMSVCQueryMirrorSyncProgressCommand(String volumeId) {
        addArgument("svcinfo lsvdisksyncprogress -delim :");
        addArgument(String.format("-copy 1"));
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
        results = new IBMSVCQueryMirrorSyncProgressResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        results.setProperty(spec.getPropertyName(), Joiner.on(',').join(capturedStrings));
        results.setSuccess(true);
    }
}
