/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "diagutils_param")
public class DiagutilParam {
    private String logEnable="";
    private List<String> options;
    private UploadParam uploadParam;
    private List<String> nodeIds;
    private List<String> nodeNames;
    private List<String> logNames;
    private int severity;
    private String startTimeStr;
    private String endTimeStr;
    private String msgRegex;
    private int maxCount;

    public DiagutilParam() {}
    @XmlElement(name = "options")
    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    @XmlElement(name = "upload_param")
    public UploadParam getUploadParam() {
        return uploadParam;
    }

    public void setUploadParam(UploadParam uploadParam) {
        this.uploadParam = uploadParam;
    }

    @XmlElement(name = "log_enable")
    public String getLogEnable() {
        return logEnable;
    }

    public void setLogEnable(String enable) {
        logEnable = enable;
    }
    @XmlElement(name = "node_id")
    public List<String> getNodeIds() {
        return nodeIds;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
    }

    @XmlElement(name = "node_name")
    public List<String> getNodeNames() {
        return nodeNames;
    }

    public void setNodeNames(List<String> nodeNames) {
        this.nodeNames = nodeNames;
    }

    @XmlElement(name = "log_name")
    public List<String> getLogNames() {
        return logNames;
    }

    public void setLogNames(List<String> logNames) {
        this.logNames = logNames;
    }

    @XmlElement(name = "severity")
    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    @XmlElement(name = "maxcount")
    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    @XmlElement(name = "start")
    public String getStartTimeStr() {
        return startTimeStr;
    }

    public void setStartTimeStr(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }

    @XmlElement(name = "end")
    public String getEndTimeStr() {
        return endTimeStr;
    }

    public void setEndTimeStr(String endTimeStr) {
        this.endTimeStr = endTimeStr;
    }

    @XmlElement(name = "msg_regex")
    public String getMsgRegex() {
        return msgRegex;
    }

    public void setMsgRegex(String msgRegex) {
        this.msgRegex = msgRegex;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("options: ");
        sb.append(options);
        sb.append(",");
        sb.append("upload_param: ");
        sb.append(uploadParam);

        return sb.toString();
    }
}
