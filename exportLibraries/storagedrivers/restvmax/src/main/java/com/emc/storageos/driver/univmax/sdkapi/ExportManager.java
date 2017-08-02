/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.ResponseWrapper;
import com.emc.storageos.driver.univmax.rest.UrlGenerator;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetHostResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.HostType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.google.gson.reflect.TypeToken;

/**
 * @author fengs5
 *
 */
public class ExportManager extends DefaultManager {
    private static final Logger log = LoggerFactory.getLogger(ExportManager.class);

    /**
     * @param driverRegistry
     * @param lockManager
     */
    public ExportManager(Registry driverRegistry, LockManager lockManager) {
        super(driverRegistry, lockManager);
    }

    public DriverTask exportVolumesToInitiators(List<Initiator> initiators, List<StorageVolume> volumes,
            Map<String, String> volumeToHLUMap, List<StoragePort> recommendedPorts, List<StoragePort> availablePorts,
            StorageCapabilities capabilities, MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "exportVolumesToInitiators", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);
        String msg = null;
        String arrayId = volumes.get(0).getStorageSystemId();
        if (!initializeRestClient(arrayId)) {
            msg = String.format("Failed to fetch access information for array %s!", arrayId);
            log.warn(msg);
            task.setMessage(msg);
            return task;
        }

        log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    public DriverTask unexportVolumesFromInitiators(List<Initiator> initiators, List<StorageVolume> volumes) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "unexportVolumesFromInitiators", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        task.setStatus(DriverTask.TaskStatus.FAILED);

        String msg = null;
        String arrayId = volumes.get(0).getStorageSystemId();
        if (!initializeRestClient(arrayId)) {
            msg = String.format("Failed to fetch access information for array %s!", arrayId);
            log.warn(msg);
            task.setMessage(msg);
            return task;
        }

        log.warn(msg);
        task.setMessage(msg);
        return task;
    }

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that with business logic.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that communicate with array through rest call.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

    /**
     * Create host (IG).
     * 
     * @param param (CreateHostParamType)
     * @return HostType
     */
    public HostType createHost(CreateHostParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<HostType>() {
        }.getType();
        ResponseWrapper<HostType> responseWrapper = post(endPoint, param, responseClazzType);

        HostType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during creating Host(IG):", responseWrapper.getException());
            responseBean = new HostType();
            appendExceptionMessage(responseBean, "Exception happened during creating Host(IG):%s", responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            log.error("httpCode {}: Failed to create Host(IG) {} with error: {}", responseBean.getHttpCode(),
                    param,
                    responseBean.getMessage());
        }
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    public GetHostResultType fetchHost(String hostId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
        Type responseClazzType = new TypeToken<GetHostResultType>() {
        }.getType();
        ResponseWrapper<GetHostResultType> responseWrapper = get(endPoint, responseClazzType);

        GetHostResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during fetching Host(IG):", responseWrapper.getException());
            responseBean = new GetHostResultType();
            appendExceptionMessage(responseBean, "Exception happened during fetching Host(IG):%s", responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            log.error("httpCode {}: Failed to fetch Host(IG) {} with error: {}", responseBean.getHttpCode(),
                    hostId,
                    responseBean.getMessage());
        }
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

}
