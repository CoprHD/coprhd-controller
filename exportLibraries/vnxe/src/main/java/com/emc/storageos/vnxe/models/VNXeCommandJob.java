/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeCommandJob extends VNXeBase {
    private int progressPct;
    private String methodName;
    private int state;
    private String name;
    private ParametersOut parametersOut;

    public int getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(int progressPct) {
        this.progressPct = progressPct;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParametersOut getParametersOut() {
        return parametersOut;
    }

    public void setParametersOut(ParametersOut parametersOut) {
        this.parametersOut = parametersOut;
    }

    public static enum JobStatusEnum {
        QUEUED(1),
        RUNNING(2),
        SUSPENDED(3),
        COMPLETED(4),
        FAILED(5),
        ROLLING_BACK(6),
        COMPLETED_WITH_PROBLEMS(7);

        private int value;

        private JobStatusEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

    }
}
