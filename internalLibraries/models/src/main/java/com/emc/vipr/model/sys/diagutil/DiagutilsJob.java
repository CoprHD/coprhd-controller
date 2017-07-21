/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;

public class DiagutilsJob implements Serializable{
    private static final Logger log = LoggerFactory.getLogger(DiagutilsJob.class);
    private static final long serialVersionUID = -1323352738926395160L;

    private List<String> options ;
    private boolean logEnable;
    private UploadParam uploadParam;


    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public boolean getLogEnable() {
        return logEnable;
    }

    public void setLogEnable(boolean logEnable) {
        this.logEnable = logEnable;
    }

    public UploadParam getUploadParam() {
        return uploadParam;
    }

    public void setUploadParam(UploadParam uploadParam) {
        this.uploadParam = uploadParam;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("options:");
        for(String option : options) {
            sb.append(option);
            sb.append(",");
        }
        sb.append(logEnable);
        sb.append("upload param: ");
        sb.append(uploadParam.toString());
        return  sb.toString();
    }

}
