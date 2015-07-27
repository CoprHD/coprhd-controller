/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.google.common.base.Strings;
import com.iwave.ext.command.CommandException;

import java.util.List;

public class ScaleIOSnapshotVolumeCommand extends AbstractScaleIOQueryCommand<ScaleIOSnapshotVolumeResult> {

//    Snapshot created successfully
//    Source volume with ID e9e5158e00000000 => e9e5158f00000001

    private static final String SNAPSHOT_VOLUME_SUCCESS = "SnapshotVolumeSuccess";

    private static final String SNAPSHOT_ID = "SnapshotId";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Snapshot created successfully", SNAPSHOT_VOLUME_SUCCESS),
            new ParsePattern("\\s+Source volume with ID (\\w+)\\s+=>\\s+(\\w+)\\s+(\\S+)", SNAPSHOT_ID)
    };

    public ScaleIOSnapshotVolumeCommand(String volumeId, String snapshotName) {
        addArgument("--snapshot_volume");
        addArgument(String.format("--volume_id %s", volumeId));
        if (!Strings.isNullOrEmpty(snapshotName)) {
            addArgument(String.format("--snapshot_name %s", snapshotName));
        }
        results = new ScaleIOSnapshotVolumeResult();
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); //No need to check not null condition here
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setIsSuccess(false);
    }

    @Override
    void beforeProcessing() {

    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case SNAPSHOT_VOLUME_SUCCESS:
                results.setIsSuccess(true);
                break;
            case SNAPSHOT_ID:
                results.setSourceId(capturedStrings.get(0));
                results.setId(capturedStrings.get(1));
                results.setName(capturedStrings.get(2));
                break;
        }
    }
}
