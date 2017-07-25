/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;


import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

public class DiagutilJobStatus implements CoordinatorSerializable {
    private static final Logger log = LoggerFactory.getLogger(DiagutilJobStatus.class);
    private static final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

    private String startTime;
    private String nodeId;
    private DiagutilStatus status;
    private DiagutilStatusDesc description;
    private String errCode;
    private String location;

    public DiagutilJobStatus() {
    }

    public DiagutilJobStatus(String startTime, String nodeId, DiagutilStatus status, DiagutilStatusDesc desc, String err, String dir) {
        this.startTime = startTime;
        this.nodeId = nodeId;
        this.status = status;
        this.description = desc;
        this.errCode = err;
        this.location = dir;
    }
    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public DiagutilStatus getStatus() {
        return status;
    }

    public void setStatus(DiagutilStatus status) {
        this.status = status;
    }

    public DiagutilStatusDesc getDescription() {
        return description;
    }

    public void setDescription(DiagutilStatusDesc description) {
        this.description = description;
    }

    public String getErrCode() {
        return errCode;
    }

    public void setErrCode(String errCode) {
        this.errCode = errCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    @JsonIgnore
    public String encodeAsString() {
        return toString();
    }

    @Override
    @JsonIgnore
    public DiagutilJobStatus decodeFromString(String infoStr) throws FatalCoordinatorException {
        try {
            log.info("decodeFromString infoStr {}",infoStr);
            //mapper.readerForUpdating(this).readValues(infoStr);
            return mapper.readValue(infoStr, DiagutilJobStatus.class);
            /*log.info("this content {}",this);
            return this;*/
        }catch (IOException e) {
            log.error("Failed to decode data string", e);
            throw CoordinatorException.fatals.decodingError(e.getMessage());
        }
    }
   public static void main(String []args) {
       DiagutilJobStatus dj = new DiagutilJobStatus();
       String info =" {\"startTime\":\"1500885681541\",\"nodeId\":null,\"status\":\"INITIALIZE\",\"description\":null,\"errCode\":null,\"location\":null}";
       dj.decodeFromString(info);
       log.info("test dj is {}",dj);

   }
    @Override
    @JsonIgnore
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return null;
    }

    @Override
    @JsonIgnore
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            log.error("Failed to serialize this object", e);
        }
        return null;
    }
    @JsonIgnore
    public DiagutilInfo toDiagutilInfo() {
        DiagutilInfo diagutilInfo = new DiagutilInfo();
        diagutilInfo.setStatus(status);
        diagutilInfo.setStartTimeStr(startTime);
        diagutilInfo.setNodeId(nodeId);
        diagutilInfo.setDesc(description);

        return diagutilInfo;
    }
}
