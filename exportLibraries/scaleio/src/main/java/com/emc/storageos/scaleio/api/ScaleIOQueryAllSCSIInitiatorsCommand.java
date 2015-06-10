/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;


import java.util.List;
import java.util.Stack;

public class ScaleIOQueryAllSCSIInitiatorsCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryAllSCSIInitiatorsResult> {
    // Query all SCSI Initiators returned 1 initiators.
    // ID: 19be51df00000000 Name: lglw7144-vmhba32 State: Normal IQN: iqn.1998-01.com.vmware:lglw7144-37595fea  Not mapped to any volume
    //  OR
    //   Query all SCSI Initiators returned 1 initiators.
    // ID: 19be51df00000000 Name: lglw7144-vmhba32 State: Normal IQN: iqn.1998-01.com.vmware:lglw7144-37595fea  Mapped to the following volumes:
    //    Volume ID: e9e51f7c00000002 Name: iSCSI LUN: 0 ITL: ed3740a900000002
    private final static String SCSI_INIT_WITH_NAME = "ScsiInitiatorWithName";
    private final static String SCSI_INIT_NO_NAME = "ScsiInitiatorNoName";
    private final static String SCSI_INIT_WITH_NAME_1_30 = "ScsiInitiatorWithName1_30";
    private final static String SCSI_INIT_NO_NAME_1_30 = "ScsiInitiatorNoName1_30";
    private final static String MAPPED_VOLUME = "MappedVolume";
    private final static String MAPPED_VOLUME_1_30 = "MappedVolume1_30";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern("\\s+ID:\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+State:\\s+(\\w+)\\s+IQN:\\s+(.*?)\\s+.*", SCSI_INIT_WITH_NAME),
            new ParsePattern("\\s+ID:\\s+(\\w+)\\s+Name:\\s+State:\\s+(\\w+)\\s+IQN:\\s+(.*?)\\s+.*", SCSI_INIT_NO_NAME),
            new ParsePattern("\\s+ID:\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+IQN:\\s+(.*?)\\s+.*", SCSI_INIT_WITH_NAME_1_30),
            new ParsePattern("\\s+ID:\\s+(\\w+)\\s+Name:\\s+IQN:\\s+(.*?)\\s+.*", SCSI_INIT_NO_NAME_1_30),
            new ParsePattern("\\s+Volume ID:\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+LUN:\\s+(\\d+)\\s+ITL:\\s+(.*)", MAPPED_VOLUME),
            new ParsePattern("\\s+Volume ID:\\s+(\\w+)\\s+Name:\\s+(.*?)\\s+LUN:\\s+(\\d+)", MAPPED_VOLUME_1_30)
    };
    public static final String UNKNOWN_STATE = "Unknown";

    private Stack<String> initiatorStack;

    public ScaleIOQueryAllSCSIInitiatorsCommand() {
        addArgument("--query_all_scsi_initiators");
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryAllSCSIInitiatorsResult();
        initiatorStack = new Stack<>();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        String id;
        String name;
        String state;
        String iqn;
        String volId;
        String volName;
        String lun;
        String itl;
        String associatedInitiator;
        switch (spec.getPropertyName()) {
            case SCSI_INIT_WITH_NAME:
                id = capturedStrings.get(0);
                name = capturedStrings.get(1);
                state = capturedStrings.get(2);
                iqn = capturedStrings.get(3);
                results.addInitiator(id, name, state, iqn);
                initiatorStack.push(iqn);
                break;
            case SCSI_INIT_NO_NAME:
                id = capturedStrings.get(0);
                state = capturedStrings.get(1);
                iqn = capturedStrings.get(2);
                results.addInitiator(id, "", state, iqn);
                initiatorStack.push(iqn);
                break;
            case SCSI_INIT_WITH_NAME_1_30:
                id = capturedStrings.get(0);
                name = capturedStrings.get(1);
                iqn = capturedStrings.get(2);
                results.addInitiator(id, name, UNKNOWN_STATE, iqn);
                initiatorStack.push(iqn);
                break;
            case SCSI_INIT_NO_NAME_1_30:
                id = capturedStrings.get(0);
                iqn = capturedStrings.get(1);
                results.addInitiator(id, "", UNKNOWN_STATE, iqn);
                initiatorStack.push(iqn);
                break;
            case MAPPED_VOLUME:
                volId = capturedStrings.get(0);
                volName = capturedStrings.get(1);
                lun = capturedStrings.get(2);
                itl = capturedStrings.get(3);
                associatedInitiator = initiatorStack.peek();
                results.addMappedVolume(associatedInitiator, volId, volName, lun, itl);
                break;
            case MAPPED_VOLUME_1_30:
                volId = capturedStrings.get(0);
                volName = capturedStrings.get(1);
                lun = capturedStrings.get(2);
                itl = "";
                associatedInitiator = initiatorStack.peek();
                results.addMappedVolume(associatedInitiator, volId, volName, lun, itl);
                break;
        }
    }

}
