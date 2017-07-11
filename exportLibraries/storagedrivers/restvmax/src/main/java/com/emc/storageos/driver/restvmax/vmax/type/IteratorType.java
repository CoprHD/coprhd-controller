/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.restvmax.vmax.type;

public class IteratorType<T> extends ResultType {

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

    public ResultListType getResultList() {
        return resultList;
    }

    public void setResultList(ResultListType resultList) {
        this.resultList = resultList;
    }
}