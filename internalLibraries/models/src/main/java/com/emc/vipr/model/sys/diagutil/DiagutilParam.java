/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * 
 * Model class for diagutils parameters.
 *
 */
@XmlRootElement(name = "diagutils_param")
public class DiagutilParam {
    private boolean logEnable;
    private LogParam logParam;
    private UploadParam uploadParam;


    public DiagutilParam() {
    }

    /**
     * Constructor for DiagutilParam
     * 
     * @param logEnable
     *           true or false
     * @param logParam
     *           LogParam object
     * @param uploadParam
     *           UploadParam object
     */
    public DiagutilParam(boolean logEnable, LogParam logParam, UploadParam uploadParam) {
        this.logEnable = logEnable;
        this.logParam = logParam;
        this.uploadParam = uploadParam;
    }

    @XmlElement(name = "log_enable")
    public boolean getLogEnable() {
        return logEnable;
    }

    public void setLogEnable(boolean enable) {
        logEnable = enable;
    }

    @XmlElement(name = "log_param")
    public LogParam getLogParam() {
        return logParam;
    }

    public void setLogParam(LogParam logParam) {
        this.logParam = logParam;
    }

    @XmlElement(name = "upload_param")
    public UploadParam getUploadParam() {
        return uploadParam;
    }

    public void setUploadParam(UploadParam uploadParam) {
        this.uploadParam = uploadParam;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("log_enable : ");
        sb.append(logEnable);
        if (logEnable) {
            sb.append(", log_param :");
            sb.append(logParam);
        }
        sb.append(", upload_param : ");
        sb.append(uploadParam);

        return sb.toString();
    }
}
