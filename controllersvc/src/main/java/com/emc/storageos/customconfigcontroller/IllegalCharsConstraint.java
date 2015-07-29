/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.Map;
import java.util.regex.Pattern;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

@SuppressWarnings("serial")
public class IllegalCharsConstraint extends CustomConfigConstraint {

    private Map<String, String> illegalCharsRegexMap;

    private Map<String, String> replacementMap;

    @Override
    public String applyConstraint(String dataField, String systemType) {
        String illegalCharsRegex = getValueFromMap(illegalCharsRegexMap, systemType);
        String replacement = getValueFromMap(replacementMap, systemType);
        return dataField.replaceAll(illegalCharsRegex, replacement);
    }

    @Override
    public void validate(String dataField, String systemType) {
        String illegalCharsRegex = getValueFromMap(illegalCharsRegexMap, systemType);
        Pattern pattern = Pattern.compile(illegalCharsRegex);
        if (pattern.matcher(dataField).find()) {
            throw CustomConfigControllerException.exceptions.illegalCharsConstraintViolated(dataField, systemType);
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

    public Map<String, String> getIllegalCharsRegexMap() {
        return illegalCharsRegexMap;
    }

    public void setIllegalCharsRegexMap(Map<String, String> illegalCharsRegexMap) {
        this.illegalCharsRegexMap = illegalCharsRegexMap;
    }

    public Map<String, String> getReplacementMap() {
        return replacementMap;
    }

    public void setReplacementMap(Map<String, String> replacementMap) {
        this.replacementMap = replacementMap;
    }

}
