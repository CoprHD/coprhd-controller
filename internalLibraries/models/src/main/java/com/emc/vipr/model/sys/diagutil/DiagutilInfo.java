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

    public enum DiagutilStatus{
        INITIALIZE,
        PRECHECK_IN_PROGRESS,
        PRECHECK_ERROR,
        PRECHECK_SUCCESS,
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

/*
    public enum DiagutilStatusDesc {
        PRECHECK_IN_PROGRESS,
        COLLECTING_ZK,
        COLLECTING_DB,
        COLLECTING_LOGS,
        COLLECTING_PROPERTIES,
        COLLECTING_HEALTH,
        COLLECTING_BACKUP,
        COLLECTING_ARCHIVE,
        COLLECT_COMPLETE,
        UPLOADING_INPROGRESS,
        UPLOAD_COMPLETE,
        DISK_FULL,
        COLLECTING_ZK_FAILURE,
        COLLECTING_DB_FAILURE,
        COLLECTING_LOGS_FAILURE,
        COLLECTING_PROPERTIES_FAILURE,
        COLLECTING_HEALTH_FAILURE,
        COLLECTING_BACKUP_FAILURE,
        UPLOAD_FAILURE
    }
*/
public enum DiagutilStatusDesc {
    PRECHECK_IN_PROGRESS,
    collecting_db,
    collecting_zk,
    collecting_logs,
    collecting_properties,
    collecting_health,
    collecting_backup,
    COLLECTING_ARCHIVE,
    COLLECT_COMPLETE,
    UPLOADING_INPROGRESS,
    UPLOAD_COMPLETE,
    DISK_FULL,
    COLLECTING_ZK_FAILURE,
    COLLECTING_DB_FAILURE,
    COLLECTING_LOGS_FAILURE,
    COLLECTING_PROPERTIES_FAILURE,
    COLLECTING_HEALTH_FAILURE,
    COLLECTING_BACKUP_FAILURE,
    UPLOAD_FAILURE
}


}
