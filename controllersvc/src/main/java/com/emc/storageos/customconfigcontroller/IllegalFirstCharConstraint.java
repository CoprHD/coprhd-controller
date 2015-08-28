/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.Map;
import java.util.regex.Pattern;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

@SuppressWarnings("serial")
public class IllegalFirstCharConstraint extends IllegalCharsConstraint {

    @Override
    public String applyConstraint(String dataField, String systemType) {
        String first = dataField.substring(0, 1);
        return dataField.replace(first, super.applyConstraint(first, systemType));
    }

    @Override
    public void validate(String dataField, String systemType) {
        String illegalCharsRegex = getValueFromMap(getIllegalCharsRegexMap(), systemType);
        Pattern pattern = Pattern.compile(illegalCharsRegex);
        String start = dataField.substring(0, 1);
        if (pattern.matcher(start).find()) {
            throw CustomConfigControllerException.exceptions.illegalFirstCharConstraintViolated(dataField, systemType, start);
        }
    }

    /**
     * At some point this should move into a base class
     * 
     * @param systemType
     * @return
     */
    private String getValueFromMap(Map<String, String> map, String systemType) {
        String chars = map.get(CustomConfigConstants.DEFAULT_KEY);
        if (map.containsKey(systemType)) {
            chars = map.get(systemType);
        }
        return chars;
    }

}
