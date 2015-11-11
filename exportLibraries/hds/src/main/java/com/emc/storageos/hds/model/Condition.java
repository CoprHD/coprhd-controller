/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class Condition {
    private String poolFunction;

    private String type;

    private String startElementNum;

    private String numOfElements;

    public Condition(String poolFunction) {
        this.poolFunction = poolFunction;
    }

    public Condition(String type, String startElementNum, String numOfElements) {
        this.type = type;
        this.startElementNum = startElementNum;
        this.numOfElements = numOfElements;
    }

    public Condition(String startElementNum, String numOfElements) {
        this.startElementNum = startElementNum;
        this.numOfElements = numOfElements;
    }

    public String getPoolFunction() {
        return poolFunction;
    }

    public void setPoolFunction(String poolFunction) {
        this.poolFunction = poolFunction;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setStartElementNum(String startElementNum) {
        this.startElementNum = startElementNum;
    }

    public void setNumOfElements(String numOfElements) {
        this.numOfElements = numOfElements;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.poolFunction) {
            xmlString.append(HDSConstants.SPACE_STR).append("poolFunction=")
                    .append(HDSConstants.QUOTATION_STR).append(this.poolFunction)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.type) {
            xmlString.append(HDSConstants.SPACE_STR).append("type=")
                    .append(HDSConstants.QUOTATION_STR).append(this.type)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.startElementNum) {
            xmlString.append(HDSConstants.SPACE_STR).append("startElementNum=")
                    .append(HDSConstants.QUOTATION_STR).append(this.startElementNum)
                    .append(HDSConstants.QUOTATION_STR);
        }
        if (null != this.numOfElements) {
            xmlString.append(HDSConstants.SPACE_STR).append("numOfElements=")
                    .append(HDSConstants.QUOTATION_STR).append(this.numOfElements)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
