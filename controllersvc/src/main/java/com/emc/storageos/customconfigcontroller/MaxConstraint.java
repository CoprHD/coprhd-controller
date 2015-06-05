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

public class MaxConstraint extends CustomConfigConstraint {
    
    private Map<String, Double> maxVals = null;
    
	@Override
	public String applyConstraint(String dataField, String systemType) {
        Double val = Double.parseDouble(dataField);
        Double maxVal = getMaxVal (systemType);
        if (val > maxVal) {
            return maxVal.toString();
        }
		return dataField;
	}

    @Override
    public void validate(String dataField, String systemType) {
        Double maxVal = getMaxVal (systemType);
        Double val = Double.parseDouble(dataField);
        if (val > maxVal) {
            throw CustomConfigControllerException.exceptions.maxConstraintViolated(
                    val, maxVal);
        }
    }
	
	private Double getMaxVal(String systemType) {
	    if (maxVals.containsKey(systemType)) {
	        return maxVals.get(systemType);
	    }
	    return maxVals.get(CustomConfigConstants.DEFAULT_KEY);
	}

    public Map<String, Double> getMaxVals() {
        return maxVals;
    }

    public void setMaxVals(Map<String, Double> maxVals) {
        this.maxVals = maxVals;
    }
	
	

}
