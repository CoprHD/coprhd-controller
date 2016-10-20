package com.emc.storageos.driver.ibmsvcdriver.api;/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.utils.ParsePattern;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.Initiator;

import java.util.ArrayList;
import java.util.List;

public class IBMSVCQueryVolumeHostMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCQueryVolumeHostMappingResult>{

    public static final String HOST_PARAMS_INFO = "HostsInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", HOST_PARAMS_INFO)
    };

    public IBMSVCQueryVolumeHostMappingCommand(String vdiskId) {
        addArgument("svcinfo lsvdiskhostmap -delim : -nohdr");
        addArgument(vdiskId);
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
        results = new IBMSVCQueryVolumeHostMappingResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {

            case HOST_PARAMS_INFO:

                String[] vdiskHostMapData = capturedStrings.get(0).split(":");

                IBMSVCHost host = new IBMSVCHost();
                host.setHostId(vdiskHostMapData[3]);
                host.setHostName(vdiskHostMapData[4]);
                results.addHostList(host);
                results.setSuccess(true);
                break;
        }
    }
}
