/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

import java.util.ArrayList;
import java.util.List;

public class IteratorType<T> {

    private String id;
    private Integer count;
    private Long expirationTime;
    private Integer maxPageSize;
    private String warningMessage;
    // min/max occurs: 1/1
    private ResultListType<T> resultList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Integer getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(Integer maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public ResultListType<T> getResultList() {
        return resultList;
    }

    public void setResultList(ResultListType<T> resultList) {
        this.resultList = resultList;
    }

    public List<T> fetchAllResults() {
        if (getCount() <= 0) {
            return new ArrayList<T>();
        }
        return getResultList().getResult();
    }
}
