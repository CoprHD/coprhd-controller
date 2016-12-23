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
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class IBMSVCFilterFCMappingCommand extends AbstractIBMSVCQueryCommand<IBMSVCFilterFCMappingResult> {

    public static final String FCMAP_PARAMS_INFO = "FCMapInfo";

    private final static ParsePattern[] PARSING_CONFIG = new ParsePattern[] {
            new ParsePattern("(.*)", FCMAP_PARAMS_INFO)
    };

    public IBMSVCFilterFCMappingCommand(String srcVolId, String tgtVolId) {
        addArgument("svcinfo lsfcmap -delim : -nohdr");
        List<String> filterParamters = new ArrayList<>();

        if(srcVolId != null){
            filterParamters.add(String.format("\"source_vdisk_id=%s\"", srcVolId));
        }

        if(tgtVolId != null){
            filterParamters.add(String.format("\"target_vdisk_id=%s\"", tgtVolId));
        }

        if(!filterParamters.isEmpty()){
            addArgument(String.format("-filtervalue %s", StringUtils.join(filterParamters, ":")));
        }

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
        results = new IBMSVCFilterFCMappingResult();
    }

    @Override
    void processMatch(ParsePattern spec, List<String> capturedStrings) {

        switch (spec.getPropertyName()) {

            case FCMAP_PARAMS_INFO:

                String[] fcMapData = capturedStrings.get(0).split(":");

                IBMSVCFCMap fcMap = new IBMSVCFCMap();
                fcMap.setfCMapID(fcMapData[0]);
                fcMap.setfCMapName(fcMapData[1]);
                fcMap.setSourceVdiskId(fcMapData[2]);
                fcMap.setSourceVdiskName(fcMapData[3]);
                fcMap.setTargetVdiskId(fcMapData[4]);
                fcMap.setTargetVdiskname(fcMapData[5]);
                fcMap.setFcMapStatus(fcMapData[6]);
                fcMap.setFcmapProgress(fcMapData[7]);
                results.addFCMapList(fcMap);
                results.setSuccess(true);
                break;
        }
    }

}
