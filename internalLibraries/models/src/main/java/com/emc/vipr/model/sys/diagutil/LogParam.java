/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;



import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.List;
@XmlRootElement(name = "log_param")
public class LogParam implements Serializable{
    private List<String> nodeIds;
    private List<String> nodeNames;
    private List<String> logNames;
    private int severity;
    private String startTimeStr;
    private String endTimeStr;
    private String msgRegex;
    private int maxCount;

    public LogParam() {}

    public LogParam(List<String> nodeIds, List<String> nodeNames, List<String> logNames, int severity, String startTimeStr, String endTimeStr, String msgRegex) {
        this.nodeIds = nodeIds;
        this.nodeNames = nodeNames;
        this.logNames = logNames;
        this.severity = severity;
        this.startTimeStr = startTimeStr;
        this.endTimeStr = endTimeStr;
        this.msgRegex = msgRegex;
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
        sb.append("node_id:");
        sb.append(nodeIds);
        sb.append(", node_name:");
        sb.append(nodeNames);
        sb.append(", log_name:");
        sb.append(logNames);
        sb.append(", severity:");
        sb.append(severity);
        sb.append(", maxcount");
        sb.append(maxCount);
        sb.append(", start:");
        sb.append(startTimeStr);
        sb.append(", end:");
        sb.append(endTimeStr);
        sb.append(": msg_regex");
        sb.append(msgRegex);

        return sb.toString();
    }
}
