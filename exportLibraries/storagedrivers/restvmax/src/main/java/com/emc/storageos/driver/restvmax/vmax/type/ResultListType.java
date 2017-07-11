/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.restvmax.vmax.type;

public class ResultListType<T> extends ResultType {
    private Integer from;
    private Integer to;
    private T[] result;

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

    public T[] getResult() {
        return result;
    }

    public void setResult(T[] result) {
        this.result = result;
    }
}
