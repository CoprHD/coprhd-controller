/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 *
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

public class ScaleIOVersionCommand extends AbstractScaleIOQueryCommand<ScaleIOVersionResult> {

    public static final String VERSION_STRING = "VersionString";
    // Examples:
    //   EMC ScaleIO Version: R1_30.17.1
    //   ScaleIO ECS Version: R1_21.0.20
    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[]{
            new ParsePattern(".*?\\s+Version:\\s+\\w{1}(.*)", VERSION_STRING),
    };

    public ScaleIOVersionCommand() {
        addArgument("--version");
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG.clone(); //No need to check not null condition here
    }

    @Override
    void beforeProcessing() {
        results = new ScaleIOVersionResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        results.setVersion(capturedStrings.get(0));
    }

}
