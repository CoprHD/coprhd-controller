/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.diagutil;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "diagutil_info")
public class DiagutilInfo {
    private String nodeId;
    private String startTimeStr;
    private DiagutilStatus status;
    private DiagutilStatusDesc desc;
    private String location;
    private String errcode;

    public DiagutilInfo() {

    }

    @XmlElement(name = "node_id")
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @XmlElement(name = "start_time")
    public String getStartTimeStr() {
        return startTimeStr;
    }

    public void setStartTimeStr(String startTimeStr) {
        this.startTimeStr = startTimeStr;
    }

    @XmlElement(name = "status")
    public DiagutilStatus getStatus() {
        return status;
    }

    public void setStatus(DiagutilStatus status) {
        this.status = status;
    }

    @XmlElement(name = "description")
    public DiagutilStatusDesc getDesc() {
        return desc;
    }

    public void setDesc(DiagutilStatusDesc desc) {
        this.desc = desc;
    }

    @XmlElement(name = "data_location")
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @XmlElement(name = "error_code")
    public String getErrcode() {
        return errcode;
    }

    public void setErrcode(String errcode) {
        this.errcode = errcode;
    }

    public enum DiagutilStatus {
        INITIALIZE,
        PRECHECK_IN_PROGRESS,
        PRECHECK_ERROR,
        COLLECTING_IN_PROGRESS,
        COLLECTING_ERROR,
        COLLECTING_SUCCESS,
        UPLOADING_IN_PROGRESS,
        UPLOADING_ERROR,
        DOWNLOADING_IN_PROGRESS,
        DOWNLOAD_ERROR,
        COMPLETE,
        UNEXPECTED_ERROR,
    }

    public enum DiagutilStatusDesc {
        precheck_in_progress,
        disk_full,
        collecting_db,
        collecting_zk,
        collecting_logs,
        collecting_properties,
        collecting_health,
        collecting_backup,
        collecting_archive,
        collect_complete,
        downloading_in_progress,
        downloading_failure,
        downloading_complete,
        uploading_in_progress,
        upload_complete,
        collecting_zk_failure,
        collecting_db_failure,
        collecting_logs_failure,
        collecting_properties_failure,
        collecting_health_failure,
        collecting_backup_failure,
        upload_failure
    }


}
