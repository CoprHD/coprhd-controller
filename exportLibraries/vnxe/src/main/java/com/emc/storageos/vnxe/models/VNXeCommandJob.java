/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
    private MessageOut messageOut;

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

    public MessageOut getMessageOut() {
        return messageOut;
    }

    public void setMessageOut(MessageOut messageOut) {
        this.messageOut = messageOut;
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
