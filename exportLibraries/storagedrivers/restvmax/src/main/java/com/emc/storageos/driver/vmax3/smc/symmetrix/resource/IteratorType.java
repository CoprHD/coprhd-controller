/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmax3.smc.symmetrix.resource;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultResponse;

public class IteratorType<T> extends DefaultResponse {

    public String getId() {
        return id;
    }

    private String id;
    private Integer count;
    private Long expirationTime;
    private Integer maxPageSize;
    private String warningMessage;
    private ResultListType<T> resultList;

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

    // TODO: how to tell all the resources is in this iterator or need another fetch from array
    public <T> List<T> fetchAllResults() {
        if (getCount() <= 0) {
            return new ArrayList<T>();
        }
        List<T> results = new ArrayList<T>();
        ResultListType<T> resultList = (ResultListType<T>) getResultList();
        for (T t : resultList.getResult()) {
            results.add(t);
        }
        return results;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "IteratorType [id=" + id + ", count=" + count + ", expirationTime=" + expirationTime + ", maxPageSize=" + maxPageSize
                + ", warningMessage=" + warningMessage + ", resultList=" + resultList + ", getMessage()=" + getCustMessage()
                + ", getHttpStatusCode()=" + getHttpStatusCode() + "]";
    }

}