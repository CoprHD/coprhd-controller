/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.geo;

import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.ResourceOperationTypeEnum;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

public class GeoServiceJob implements Serializable {

    public static final String LOCAL_VDC_ID = "LOCAL_VDC_ID";
    public static final String OPERATED_VDC_ID = "OPERATED_VDC_ID";
    public static final String VDC_NAME = "VDC_NAME";
    public static final String VDC_API_ENDPOINT = "VDC_ENDPOINT";
    public static final String VDC_SECRETE_KEY = "VDC_SECRETE_KEY";
    public static final String VDC_DESCRIPTION = "VDC_DESCRIPTION";
    public static final String VDC_GEOCOMMAND_ENDPOINT = "VDC_GEOCOMMAND_ENDPOINT";
    public static final String VDC_GEODATA_ENDPOINT = "VDC_GEODATA_ENDPOINT";
    public static final String VDC_CERTIFICATE_CHAIN = "VDC_CERTIFICATE_CHAIN";
    public static final String VDC_SHORT_ID = "VDC_SHORT_ID";

    public enum JobType {
        VDC_CONNECT_JOB(ResourceOperationTypeEnum.ADD_VDC),
        VDC_UPDATE_JOB(ResourceOperationTypeEnum.UPDATE_VDC),
        VDC_DISCONNECT_JOB (ResourceOperationTypeEnum.DISCONNECT_VDC),
        VDC_RECONNECT_JOB (ResourceOperationTypeEnum.RECONNECT_VDC),
        VDC_REMOVE_JOB (ResourceOperationTypeEnum.REMOVE_VDC);

        private final ResourceOperationTypeEnum rtype;

        JobType(ResourceOperationTypeEnum type) {
            rtype = type;
        }

        public ResourceOperationTypeEnum toResourceOperationType() {
            return rtype;
        }
    }

    private URI    _vdcId;
    private VirtualDataCenter _vdc;
    private String _task;
    private JobType _type;
    private List<Object> _params;

    public GeoServiceJob(VirtualDataCenter vdc, String task, JobType type, List<Object> params) {
        _vdc = vdc;
        _vdcId = vdc.getId();
        _task = task;
        _type = type;
        _params = params;
    }

    public void setVdcId(URI vdcId) {
        _vdcId = vdcId;
    }

    public URI getVdcId() {
        return _vdcId;
    } 

    public VirtualDataCenter getVdc() {
        return _vdc;
    }

    public void setVdc(VirtualDataCenter vdc) {
        this._vdc = vdc;
    }

    public void setTask(String task) {
        _task = task;
    }

    public String getTask() {
        return _task;
    }

    public void setType(JobType type) {
        _type = type;
    }

    public JobType getType() {
        return _type;
    }

    public void setParams(List<Object> params) {
        _params = params;
    }

    public List<Object> getParams() {
        return _params;
    }

}
