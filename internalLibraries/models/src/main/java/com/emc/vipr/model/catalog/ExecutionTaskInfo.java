/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlType;

@XmlType
public class ExecutionTaskInfo extends ExecutionLogInfo {

    /**
     * Details for this task
     */
    private String detail;

    /**
     * Length of time the task ran for
     */
    private Long elapsed;

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getElapsed() {
        return elapsed;
    }

    public void setElapsed(Long elapsed) {
        this.elapsed = elapsed;
    }
}
