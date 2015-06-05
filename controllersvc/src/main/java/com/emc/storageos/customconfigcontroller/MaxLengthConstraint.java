/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.customconfigcontroller;

import java.util.Map;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

public class MaxLengthConstraint extends CustomConfigConstraint {

    private static final long serialVersionUID = 1061181519310789725L;
    private Map<String, Integer> maxLengthMap;
	private Integer defaultMaxLength;
	
	@Override
	public String applyConstraint(String dataField, String systemType) {
        // TODO - Should we handle null dataField?
		int endIndex = getMaxLength(systemType);
		if (dataField.length() > endIndex) {
		    return dataField.substring(0, endIndex);
		}
		return dataField;
	}
	
	@Override
    public void validate(String dataField, String systemType) {
	    // TODO - Should we handle null dataField?
	    int max = getMaxLength(systemType);
        if (dataField.length() > max) {        	
            throw CustomConfigControllerException.exceptions.maxLengthConstraintViolated(dataField, systemType, dataField.length(), max, dataField.substring(0, max));
        }
    }
	
	private int getMaxLength(String systemType) {
        int max = maxLengthMap.get(CustomConfigConstants.DEFAULT_KEY);
        if (maxLengthMap != null && maxLengthMap.containsKey(systemType)) {
            max = maxLengthMap.get(systemType);
        }
        return max;
	}

    public Map<String, Integer> getMaxLengthMap() {
		return maxLengthMap;
	}

    public void setMaxLengthMap(Map<String, Integer> maxLengthMap) {
        this.maxLengthMap = maxLengthMap;
    }

    public Integer getDefaultMaxLength() {
        return defaultMaxLength;
    }

    public void setDefaultMaxLength(Integer defaultMaxLength) {
        this.defaultMaxLength = defaultMaxLength;
    }
}
