/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api;

import com.google.common.base.Joiner;
import com.iwave.ext.command.CommandException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScaleIOSnapshotMultiVolumeCommand extends AbstractScaleIOQueryCommand<ScaleIOSnapshotMultiVolumeResult> {

    /*
     * Snapshots on 3 volumes created successfully.
     * Consistency group ID: 5a05c11200000003
     * Source volume with ID e9e516b400000001 => e9e516bd00000004
     * Source volume with ID e9e516b500000002 => e9e516be00000005
     * Source volume with ID e9e516b600000003 => e9e516bf00000006
     */

    private static final String SNAPSHOT_VOLUME_SUCCESS = "SnapshotVolumeSuccess";

    private static final String SNAPSHOT_MULTI_VOLUME_SUCCESS = "SnapshotMultiVolumeSuccess";

    private static final String SNAPSHOT_CONSISTENCY_GROUP = "SnapshotConsistencyGroup";

    private static final String SNAPSHOT_ID = "SnapshotId";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("Snapshot created successfully", SNAPSHOT_VOLUME_SUCCESS),
            new ParsePattern("Snapshots on \\d+ volumes created successfully.", SNAPSHOT_MULTI_VOLUME_SUCCESS),
            new ParsePattern("Consistency group ID: (\\w+)", SNAPSHOT_CONSISTENCY_GROUP),
            new ParsePattern("\\s+Source volume with ID (\\w+)\\s+=>\\s+(\\w+)\\s+(\\S+)", SNAPSHOT_ID)
    };

    public ScaleIOSnapshotMultiVolumeCommand(Map<String, String> id2snapshot) {
        Set<Map.Entry<String, String>> entries = id2snapshot.entrySet();

        // NOTE: volIds and snapNames elements MUST align correctly
        List<String> volIds = new ArrayList<>();
        List<String> snapNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries) {
            volIds.add(entry.getKey());
            snapNames.add(entry.getValue());
        }

        // We want to snapshot a list of volumes
        addArgument("--snapshot_volume");

        // Ordered list of volume ids
        String joined = Joiner.on(',').join(volIds);
        addArgument(String.format("--volume_id %s", joined));

        // Ordered list of snapshot names, in same order as volume ids
        String names = Joiner.on(',').skipNulls().join(snapNames);
        addArgument(String.format("--snapshot_name %s", names));

        results = new ScaleIOSnapshotMultiVolumeResult();
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    void beforeProcessing() {

    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {
            case SNAPSHOT_VOLUME_SUCCESS:
            case SNAPSHOT_MULTI_VOLUME_SUCCESS:
                results.setIsSuccess(true);
                break;
            case SNAPSHOT_CONSISTENCY_GROUP:
                results.setConsistencyGroupId(capturedStrings.get(0));
                break;
            case SNAPSHOT_ID:
                ScaleIOSnapshotVolumeResult line = new ScaleIOSnapshotVolumeResult();
                line.setSourceId(capturedStrings.get(0));
                line.setId(capturedStrings.get(1));
                line.setName(capturedStrings.get(2));
                results.addResult(line);
                break;
        }
    }
}
