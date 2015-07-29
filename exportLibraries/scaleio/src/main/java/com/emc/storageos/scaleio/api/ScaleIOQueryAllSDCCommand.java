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

public class ScaleIOQueryAllSDCCommand extends AbstractScaleIOQueryCommand<ScaleIOQueryAllSDCResult> {

    // Query all SDC returned 3 SDC nodes.
    // SDC ID: 5509377100000000 IP: 10.247.78.47 State: Connected GUID: 2BCC6A51-C001-4F97-A721-0E10C69A82E7
    // SDC ID: 5509377200000001 IP: 10.247.78.48 State: Connected GUID: 9CDD874D-CE99-4C0D-9560-6219E017A0C1
    // SDC ID: 5509377300000002 IP: 10.247.78.49 State: Connected GUID: 6E58E38F-9CC8-4CA8-BAD8-0B99107B98F1

    private static final String CLIENT = "Client";
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("SDC ID:\\s+(\\w+).*?IP:\\s+(.*?)\\s+State:\\s+(\\w+)\\s+GUID:\\s+(.*)", CLIENT)
    };

    public ScaleIOQueryAllSDCCommand() {
        addArgument("--query_all_sdc");
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); // No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOQueryAllSDCResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        String sdcId = capturedStrings.get(0);
        String sdcIP = capturedStrings.get(1);
        String sdcState = capturedStrings.get(2);
        String sdcGUID = capturedStrings.get(3);
        results.addClient(sdcId, sdcIP, sdcState, sdcGUID);
    }
}
