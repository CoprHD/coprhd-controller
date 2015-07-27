/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.scaleio.api;

import com.iwave.ext.command.CommandException;

import java.util.List;

public class ScaleIORemoveConsistencyGroupSnapshotsCommand
        extends AbstractScaleIOQueryCommand<ScaleIORemoveConsistencyGroupSnapshotsResult>{

    /*
    Consistency Group 5a05c11200000003 removed successfully.
        2 snapshots were removed
    */

    private static final String REMOVE_CG_SNAPSHOT_SUCCESS = "RemoveCGSnapshotSuccess";

    private static final String REMOVED_SNAPSHOT_COUNT = "RemovedSnapshotCount";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("Consistency Group (\\w+) removed successfully.", REMOVE_CG_SNAPSHOT_SUCCESS),
            new ParsePattern("\\s+(\\d+) snapshots were removed", REMOVED_SNAPSHOT_COUNT)
    };

    public ScaleIORemoveConsistencyGroupSnapshotsCommand(ScaleIOCommandSemantics commandSemantics, String consistencyGroupId) {
        addArgument("--remove_consistency_group_snapshots");
        addArgument(String.format("--consistency_group_id %s", consistencyGroupId));
        if (commandSemantics != ScaleIOCommandSemantics.SIO1_2X) {
            // SIO 1.30+ prompts the user for certain operations; this argument gets past the prompt
            addArgument("--i_am_sure");
        }
        results = new ScaleIORemoveConsistencyGroupSnapshotsResult();
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); //No need to check not null condition here
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
            case REMOVE_CG_SNAPSHOT_SUCCESS:
                results.setIsSuccess(true);
                break;
            case REMOVED_SNAPSHOT_COUNT:
                results.setCount(Integer.parseInt(capturedStrings.get(0)));
                break;
        }
    }
}
