/**
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ScaleIOQueryAllSDCResult {
    public static final String SDC_ID = "SDC_ID";
    public static final String SDC_IP = "SDC_IP";
    public static final String SDC_STATE = "SDC_STATE";
    public static final String SDC_GUID = "SDC_GUID";

    private static final ScaleIOAttributes EMPTY_PROPERTY = new ScaleIOAttributes();
    private Map<String, ScaleIOAttributes> sdcMap = new HashMap<String, ScaleIOAttributes>();

    public void addClient(String sdcId, String sdcIP, String sdcState, String sdcGUID) {
        ScaleIOAttributes properties = sdcMap.get(sdcId);
        if (properties == null) {
            properties = new ScaleIOAttributes();
            sdcMap.put(sdcId, properties);
        }
        properties.put(SDC_ID, sdcId);
        properties.put(SDC_IP, sdcIP);
        properties.put(SDC_STATE, sdcState);
        properties.put(SDC_GUID, sdcGUID);
    }

    public ScaleIOAttributes getClientInfoById(String sdcId) {
        ScaleIOAttributes properties = sdcMap.get(sdcId);
        if (properties == null) {
            properties = EMPTY_PROPERTY;
        }
        return properties;
    }

    public ScaleIOAttributes getClientInfoByIP(String sdcIP) {
        ScaleIOAttributes properties = EMPTY_PROPERTY;
        for (ScaleIOAttributes it : sdcMap.values()) {
            String value = it.get(SDC_IP);
            if (value != null && value.equals(sdcIP)) {
                properties = it;
                break;
            }
        }
        return properties;
    }

    public Collection<String> getSDCIds() {
        return sdcMap.keySet();
    }
}
