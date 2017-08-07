/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.ResponseWrapper;
import com.emc.storageos.driver.univmax.rest.UrlGenerator;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;
import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateMaskingViewParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreatePortGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetHostResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetMaskingViewResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetPortGroupResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.HostOrHostGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.HostType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.PortGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.StorageGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingHostGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingPortGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingStorageGroupParamType;
import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;
import com.google.gson.reflect.TypeToken;

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
        // if (!initializeRestClient(arrayId)) {
        // msg = String.format("Failed to fetch access information for array %s!", arrayId);
        // log.warn(msg);
        // task.setMessage(msg);
        // return task;
        // }

        exportVolumesToInitiatorFake(initiators, volumes,
                recommendedPorts,
                usedRecommendedPorts, selectedPorts, task);
        return task;
    }

    public void exportVolumesToInitiatorFake(List<Initiator> initiators, List<StorageVolume> volumes,
            List<StoragePort> recommendedPorts,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts, DriverTask task) {

        Random random = new Random(System.currentTimeMillis());
        int randomInt = random.nextInt(10000);
        String hostName = initiators.get(0).getHostName();
        // generate IG
        String hostId = genHostId(hostName, randomInt);
        List<String> initiatorList = new ArrayList<>();
        for (Initiator initiator : initiators) {
            initiatorList.add(transformInitiator(initiator));
        }

        CreateHostParamType igParam = new CreateHostParamType(hostId);
        igParam.setInitiatorId(initiatorList);
        GenericResultType host = createHost(igParam);
        if (!host.isSuccessfulStatus()) {
            log.error("Failed to create host (IG) with error :{}", host.getMessage());
            task.setMessage(host.getMessage());
            return;
        }

        // generate PG
        String pgId = genPgId(hostName, randomInt);
        usedRecommendedPorts.setValue(true);
        selectedPorts.addAll(recommendedPorts);
        CreatePortGroupParamType pgParam = new CreatePortGroupParamType(pgId);
        for (StoragePort port : recommendedPorts) {
            SymmetrixPortKeyType symPort = new SymmetrixPortKeyType(port.getPortGroup(), parsePortId(port.getPortName()));
            pgParam.addSymmetrixPortKey(symPort);
        }
        GenericResultType pg = createPortGroup(pgParam);
        if (!pg.isSuccessfulStatus()) {
            log.error("Failed to create portGroup (PG) with error :{}", pg.getMessage());
            task.setMessage(pg.getMessage());
            return;
        }

        // generate mv
        String sgId = volumes.get(0).getConsistencyGroup();
        String mvId = genMvId(hostName, randomInt);
        GenericResultType mv = createMaskingviewForHost(mvId, hostId, pgId, sgId);
        if (!mv.isSuccessfulStatus()) {
            log.error("Failed to create maskingview (MV) with error :{}", mv.getMessage());
            task.setMessage(mv.getMessage());
            return;
        }

        task.setStatus(TaskStatus.READY);
    }

    String parsePortId(String portName) {
        return portName.split(":")[1];
    }

    String transformInitiator(Initiator initiator) {
        return initiator.getPort().replaceAll(":", "");
    }

    String genHostId(String hostName, int randomInt) {
        return String.format("%s_IG_%s", hostName, randomInt);
    }

    String genPgId(String hostName, int randomInt) {
        return String.format("%s_PG_%s", hostName, randomInt);
    }

    String genMvId(String hostName, int randomInt) {
        return String.format("%s_MV_%s", hostName, randomInt);
    }

    // List<Host> findHostsWithInitiators(List<Initiator> initiators) {
    // Map<String, Host> hosts = new HashMap<>();
    // for (Initiator initiator : initiators) {
    // String hostName = initiator.getHostName();
    // Host host = hosts.get(hostName);
    // if (host == null) {
    // host = new Host(hostName);
    // hosts.put(hostName, host);
    // }
    // host.addNewInitiator(initiator.get);
    // }
    // }

    // String getInitiatorId(Initiator initiator) {
    // if (initiator.getProtocol().equals(Protocol.FC)) {
    //
    // }
    // if (initiator.getProtocol().equals(Protocol.iSCSI)) {
    //
    // }
    // }

    // Host findHostWithInitiators(List<Initiator> initiators) {
    //
    // }

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
     * @return GenericResultType
     */
    public GenericResultType createHost(CreateHostParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<HostType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper = post(endPoint, param, responseClazzType);

        GenericResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during creating Host(IG):", responseWrapper.getException());
            responseBean = new GenericResultType();
            appendExceptionMessage(responseBean, "Exception happened during creating Host(IG):%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch host with hostId
     * 
     * @param hostId
     * @return
     */
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

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Create portGroup.
     * 
     * @param param
     * @return
     */
    public GenericResultType createPortGroup(CreatePortGroupParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.PORTGROUP, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<GenericResultType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper = post(endPoint, param, responseClazzType);

        GenericResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during creating portgroup(PG):", responseWrapper.getException());
            responseBean = new GenericResultType();
            appendExceptionMessage(responseBean, "Exception happened during creating portgroup(PG):%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch portGroup with id.
     * 
     * @param pgId
     * @return GetPortGroupResultType
     */
    public GetPortGroupResultType fetchPortGroup(String pgId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.PORTGROUP_ID, genUrlFillersWithSn(pgId));
        Type responseClazzType = new TypeToken<GetPortGroupResultType>() {
        }.getType();
        ResponseWrapper<GetPortGroupResultType> responseWrapper = get(endPoint, responseClazzType);

        GetPortGroupResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during fetching PortGroup(PG):", responseWrapper.getException());
            responseBean = new GetPortGroupResultType();
            appendExceptionMessage(responseBean, "Exception happened during fetching PortGroup(PG):%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Create maskingview for host.
     * 
     * @param mvName
     * @param hostId
     * @param pgId
     * @param sgId
     * @return
     */
    public GenericResultType createMaskingviewForHost(String mvName, String hostId, String pgId, String sgId) {
        CreateMaskingViewParamType param = new CreateMaskingViewParamType(mvName);
        param.setHostOrHostGroupSelection(genHostOrHostGroupSelectionTypeUseExistingHost(hostId));
        param.setPortGroupSelection(genPortGroupSelectionTypeUseExistingPg(pgId));
        param.setStorageGroupSelection(genStorageGroupSelectionTypeUseExistingSg(sgId));
        return createMaskingview(param);
    }

    /**
     * Create maskingview for cluster(hostGroup).
     * 
     * @param mvName
     * @param hostGroupId
     * @param pgId
     * @param sgId
     * @return
     */
    public GenericResultType createMaskingviewForHostGroup(String mvName, String hostGroupId, String pgId, String sgId) {
        CreateMaskingViewParamType param = new CreateMaskingViewParamType(mvName);
        param.setHostOrHostGroupSelection(genHostOrHostGroupSelectionTypeUseExistingHostGroup(hostGroupId));
        param.setPortGroupSelection(genPortGroupSelectionTypeUseExistingPg(pgId));
        param.setStorageGroupSelection(genStorageGroupSelectionTypeUseExistingSg(sgId));
        return createMaskingview(param);
    }

    private HostOrHostGroupSelectionType genHostOrHostGroupSelectionTypeUseExistingHostGroup(String hostGroupId) {
        UseExistingHostGroupParamType useExistingHostGroupParam = new UseExistingHostGroupParamType(hostGroupId);
        HostOrHostGroupSelectionType hostOrHostGroupSelection = new HostOrHostGroupSelectionType();
        hostOrHostGroupSelection.setUseExistingHostGroupParam(useExistingHostGroupParam);
        return hostOrHostGroupSelection;
    }

    private HostOrHostGroupSelectionType genHostOrHostGroupSelectionTypeUseExistingHost(String hostId) {
        UseExistingHostParamType useExistingHostParam = new UseExistingHostParamType(hostId);
        HostOrHostGroupSelectionType hostOrHostGroupSelection = new HostOrHostGroupSelectionType();
        hostOrHostGroupSelection.setUseExistingHostParam(useExistingHostParam);
        return hostOrHostGroupSelection;
    }

    private PortGroupSelectionType genPortGroupSelectionTypeUseExistingPg(String pgId) {
        UseExistingPortGroupParamType useExistingPortGroupParam = new UseExistingPortGroupParamType(pgId);
        PortGroupSelectionType portGroupSelection = new PortGroupSelectionType();
        portGroupSelection.setUseExistingPortGroupParam(useExistingPortGroupParam);
        return portGroupSelection;
    }

    private StorageGroupSelectionType genStorageGroupSelectionTypeUseExistingSg(String sgId) {
        UseExistingStorageGroupParamType useExistingStorageGroupParam = new UseExistingStorageGroupParamType(sgId);
        StorageGroupSelectionType storageGroupSelection = new StorageGroupSelectionType();
        storageGroupSelection.setUseExistingStorageGroupParam(useExistingStorageGroupParam);
        return storageGroupSelection;
    }

    /**
     * Create maskingview.
     * 
     * @param param
     * @return
     */
    public GenericResultType createMaskingview(CreateMaskingViewParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.MASKINGVIEW, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<GenericResultType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper = post(endPoint, param, responseClazzType);

        GenericResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during creating maskingview(MV):", responseWrapper.getException());
            responseBean = new GenericResultType();
            appendExceptionMessage(responseBean, "Exception happened during creating maskingview(MV):%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch maskingview with id.
     * 
     * @param mvId
     * @return
     */
    public GetMaskingViewResultType fetchMaskingview(String mvId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.MASKINGVIEW_ID, genUrlFillersWithSn(mvId));
        Type responseClazzType = new TypeToken<GetMaskingViewResultType>() {
        }.getType();
        ResponseWrapper<GetMaskingViewResultType> responseWrapper = get(endPoint, responseClazzType);

        GetMaskingViewResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during fetching maskingview(MV):", responseWrapper.getException());
            responseBean = new GetMaskingViewResultType();
            appendExceptionMessage(responseBean, "Exception happened during fetching maskingview(MV):%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

}
