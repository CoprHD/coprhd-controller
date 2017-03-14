/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;

/**
 * Log message for execution step details.
 * 
 * @author jonnymiller
 */
@Cf("ExecutionTaskLog")
public class ExecutionTaskLog extends ExecutionLog {

    public static final String DETAIL = "detail";
    public static final String ELAPSED = "elapsed";

    private String detail;
    private Long elapsed;

    @Name(DETAIL)
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
        setChanged(DETAIL);
    }

    @Name(ELAPSED)
    public Long getElapsed() {
        return elapsed;
    }

    public void setElapsed(Long elapsed) {
        this.elapsed = elapsed;
        setChanged(ELAPSED);
    }

    @Override
    public String toString() {
        String s = super.toString();
        StringBuilder builder = new StringBuilder(s);
        builder.append(getPhase())
                .append("\nDetail:")
                .append(detail)
                .append("\nElapsed:")
                .append(elapsed)
                .append("\n");

        return builder.toString();
    }
}
