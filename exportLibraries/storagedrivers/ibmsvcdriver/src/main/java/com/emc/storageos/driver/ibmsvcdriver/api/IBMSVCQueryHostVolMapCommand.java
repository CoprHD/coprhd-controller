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

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IBMSVCQueryHostVolMapCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryHostVolMapResult> {

    public static final String HOST_VOLMAP_PARAMS_INFO = "HostVolMap";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", HOST_VOLMAP_PARAMS_INFO)
    };

    public IBMSVCQueryHostVolMapCommand(String hostId) {
        addArgument("svcinfo lshostvdiskmap -delim : -nohdr " + hostId);
    }

    @Override
    ParsePattern[] getOutputPatternSpecification() {
        return PARSING_CONFIG;
    }

    @Override
    protected void processError() throws CommandException {
        results.setErrorString(getErrorMessage());
        results.setSuccess(false);
    }

    @Override
    void beforeProcessing() {
        results = new IBMSVCQueryHostVolMapResult();
        Map<String, String> volHluMap = new HashMap<>();
        results.setVolHluMap(volHluMap);
        results.setVolCount(0);
        results.setSuccess(true);
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {
        switch (spec.getPropertyName()) {

            case HOST_VOLMAP_PARAMS_INFO:
                // not called if query returned empty.
                String[] hostVolData = capturedStrings.get(0).split(":");

                String volumeId = hostVolData[3];
                String hluId = hostVolData[2];

                results.setVolCount(results.getVolCount() + 1);
                results.getVolHluMap().put(volumeId, hluId);
                results.setSuccess(true);
                break;
        }
    }
}
