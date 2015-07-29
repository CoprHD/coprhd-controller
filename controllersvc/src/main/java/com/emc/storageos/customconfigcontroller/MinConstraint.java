/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.customconfigcontroller;

import java.util.Map;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;

public class MinConstraint extends CustomConfigConstraint {

    private static final long serialVersionUID = -6356570983262033063L;
    private Map<String, Double> minVals = null;

    @Override
    public String applyConstraint(String dataField, String systemType) {
        Double val = Double.parseDouble(dataField);
        Double minVal = getMinVal(systemType);
        if (val < minVal) {
            return minVal.toString();
        }
        return dataField;
    }

    @Override
    public void validate(String dataField, String systemType) {
        Double minVal = getMinVal(systemType);
        Double val = Double.parseDouble(dataField);
        if (val < minVal) {
            throw CustomConfigControllerException.exceptions.minConstraintViolated(
                    systemType, val, minVal);
        }
    }

    private Double getMinVal(String systemType) {
        if (minVals.containsKey(systemType)) {
            return minVals.get(systemType);
        }
        return minVals.get(CustomConfigConstants.DEFAULT_KEY);
    }

    public Map<String, Double> getMinVals() {
        return minVals;
    }

    public void setMinVals(Map<String, Double> minVals) {
        this.minVals = minVals;
    }

}
