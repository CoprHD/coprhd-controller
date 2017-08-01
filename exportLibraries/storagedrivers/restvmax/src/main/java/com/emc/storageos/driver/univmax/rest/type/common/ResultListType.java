/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.univmax.rest.type.common;

import java.util.List;

public class ResultListType<T> extends GenericResultImplType {
    private Integer from;
    private Integer to;
    // min/max occurs: 0/unbounded
    private List<T> result;

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    /**
     * @return the result
     */
    public List<T> getResult() {
        return result;
    }

    /**
     * @param result the result to set
     */
    public void setResult(List<T> result) {
        this.result = result;
    }

}
