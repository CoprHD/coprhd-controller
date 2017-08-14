/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.sdkapi;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.rest.EndPoint;
import com.emc.storageos.driver.univmax.rest.ResponseWrapper;
import com.emc.storageos.driver.univmax.rest.UrlGenerator;
import com.emc.storageos.driver.univmax.rest.exception.FailedDeleteRestCallException;
import com.emc.storageos.driver.univmax.rest.exception.NoResourceFoundException;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;
import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;
import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateHostGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreateMaskingViewParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.CreatePortGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetHostResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetInitiatorResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetMaskingViewResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetPortGroupResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetStorageGroupResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.GetVolumeResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.HostOrHostGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.ListInitiatorResultType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.MaskingViewType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.PortGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.StorageGroupSelectionType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.StorageGroupType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingHostGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingHostParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingPortGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.UseExistingStorageGroupParamType;
import com.emc.storageos.driver.univmax.rest.type.sloprovisioning.VolumeType;
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

    private void exportVolumesToInitiatorFake(List<Initiator> initiators, List<StorageVolume> volumes,
            List<StoragePort> recommendedPorts,
            MutableBoolean usedRecommendedPorts, List<StoragePort> selectedPorts, DriverTask task) {

        Random random = new Random(System.currentTimeMillis());
        int randomInt = random.nextInt(10000000);
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
        return portName.split(SymConstants.Mark.COLON)[1];
    }

    String transformInitiator(Initiator initiator) {
        return initiator.getPort().replaceAll(SymConstants.Mark.COLON, SymConstants.Mark.EMPTY_STRING);
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
        // String arrayId = volumes.get(0).getStorageSystemId();
        // if (!initializeRestClient(arrayId)) {
        // msg = String.format("Failed to fetch access information for array %s!", arrayId);
        // log.warn(msg);
        // task.setMessage(msg);
        // return task;
        // }

        unexportVolumesToInitiatorFake(initiators, volumes, task);
        return task;
    }

    private void unexportVolumesToInitiatorFake(List<Initiator> initiators, List<StorageVolume> volumes, DriverTask task) {

        Set<String> mvIdsMatched = new HashSet<>();
        Map<String, List<String>> hostMvMap = new HashMap<>();
        try {
            // find IG with initiator->find host and mvs
            for (Initiator initiator : initiators) {
                hostMvMap.putAll(findHostMvMappingWithInitiator(initiator));
            }
            Set<String> mvIdsFromIgs = new HashSet<>();
            for (List<String> mvIds : hostMvMap.values()) {
                mvIdsFromIgs.addAll(mvIds);
            }
            // find SG with volumes -> find mvs
            Set<String> sgIds = new HashSet<>();
            for (StorageVolume volume : volumes) {
                sgIds.addAll(findSgIdsWithVolume(volume.getNativeId()));
            }

            Set<String> mvIdsFromSgs = new HashSet<>();
            for (String sgId : sgIds) {
                mvIdsFromSgs.addAll(findMvIdsWithSg(sgId));
            }
            mvIdsMatched.addAll(mvIdsFromSgs);
            log.debug("mvIdsFromSgs as {}", mvIdsFromSgs);
            log.debug("mvIdsFromIgs as {}", mvIdsFromIgs);
            mvIdsMatched.retainAll(mvIdsFromIgs);
            log.debug("Matched maskingviewIds as {}", mvIdsMatched);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setMessage(e.getMessage());
            return;
        }

        // find MV with IG and SG
        if (mvIdsMatched.isEmpty()) {
            task.setStatus(TaskStatus.FAILED);
            task.setMessage("Could not find valid maskingview.");
            return;
        }

        StringBuilder errorMsg = new StringBuilder();
        int failedNum = 0;
        for (String mvId : mvIdsMatched) {

            try {
                removeMvAndRelatedResourcs(mvId, hostMvMap);
            } catch (Exception e) {
                errorMsg.append(e.getMessage()).append(SymConstants.Mark.NEW_LINE);
                failedNum++;
            }
        }

        if (failedNum > 0) {
            task.setMessage(errorMsg.toString());
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
            if (failedNum == mvIdsMatched.size()) {
                task.setStatus(TaskStatus.FAILED);
            }
        } else {
            task.setStatus(TaskStatus.READY);
        }

    }

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that with business logic.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

    /**
     * Remove maskingview and IG, PG.
     * 
     * @param mvId
     * @param hostMvMap
     * @throws NoResourceFoundException
     * @throws FailedDeleteRestCallException
     */
    public void removeMvAndRelatedResourcs(String mvId, Map<String, List<String>> hostMvMap) throws NoResourceFoundException,
            FailedDeleteRestCallException {
        boolean removeMv = false;
        boolean removePg = false;
        // boolean removeIg = false;

        GetMaskingViewResultType mvResult = fetchMaskingview(mvId);
        if (!mvResult.isSuccessfulStatus() && !mvResult.getMessage().contains("Cannot find Masking View")) {
            log.error("Failed to fetch maskingview with id {} : {}", mvId, mvResult.getMessage());
            throw new NoResourceFoundException(mvResult.getMessage());
        } else if (mvResult.isSuccessfulStatus()) {
            removeMv = true;
        }

        MaskingViewType mv = mvResult.getMaskingView().get(0);
        // String cigId = mv.getHostGroupId();
        String igId = mv.getHostId();
        String pgId = mv.getPortGroupId();
        // String sgId = mv.getStorageGroupId();

        GetPortGroupResultType pgResult = fetchPortGroup(pgId);
        if (!pgResult.isSuccessfulStatus() && !pgResult.getMessage().contains("Cannot find Port Group")) {
            log.error("Failed to fetch portgroup with id {} : {}", pgId, pgResult.getMessage());
            throw new FailedDeleteRestCallException(pgResult.getMessage());
        } else if (pgResult.isSuccessfulStatus()) {
            List<String> mvIdsFromPg = pgResult.getPortGroup().get(0).getMaskingview();
            if (!CollectionUtils.isEmpty(mvIdsFromPg) && mvIdsFromPg.size() == 1) {
                if (mvIdsFromPg.contains(mvId)) {
                    removePg = true;
                }
            }
        }

        // remove Mv
        if (removeMv) {
            GenericResultImplType result = removeMv(mvId);
            if (!result.isSuccessfulStatus()) {
                log.error("Failed to delete maskingview with id {} : {}", mvId, mvResult.getMessage());
                throw new FailedDeleteRestCallException(mvResult.getMessage());
            }
        }
        // remove CIG or IG
        List<String> mvIds = hostMvMap.get(igId);
        if (!CollectionUtils.isEmpty(mvIds) && mvIds.size() == 1) {
            if (mvIds.contains(mvId)) {
                GenericResultImplType hostResult = removeHost(igId);
                if (!hostResult.isSuccessfulStatus() && !hostResult.getMessage().contains("Cannot find Host")) {
                    log.error("Failed to delete host with id {} : {}", igId, hostResult.getMessage());
                    throw new FailedDeleteRestCallException(hostResult.getMessage());
                }
            }
        }

        // remove PG
        if (removePg) {
            GenericResultImplType result = removePortGroup(pgId);
            if (!result.isSuccessfulStatus() && !result.getMessage().contains("Cannot find Port Group")) {
                log.error("Failed to delete portGroup with id {} : {}", pgId, result.getMessage());
                throw new FailedDeleteRestCallException(result.getMessage());
            }
        }
    }

    /**
     * Find maskingview id with sg.
     * 
     * @param sgId
     * @return List<String> maskingview ids that connected with this sg.
     * @throws NoResourceFoundException
     */
    public List<String> findMvIdsWithSg(String sgId) throws NoResourceFoundException {
        List<String> mvList = new ArrayList<>();
        GetStorageGroupResultType sgResult = fetchSg(sgId);
        if (!sgResult.isSuccessfulStatus()) {
            log.error("Failed to fetch storagegroup with id {} : {}", sgId, sgResult.getMessage());
            throw new NoResourceFoundException(sgResult.getMessage());
        }
        // Only one bean will return for one id.
        StorageGroupType sg = sgResult.getStorageGroup().get(0);
        String[] mvs = sg.getMaskingview();
        if (!ArrayUtils.isEmpty(mvs)) {
            mvList.addAll(Arrays.asList(mvs));
        }

        return mvList;
    }

    /**
     * Find sgIds with Volume.
     * 
     * @param volume
     * @return List<String> sgIds
     * @throws NoResourceFoundException
     */
    public List<String> findSgIdsWithVolume(String volumeId) throws NoResourceFoundException {
        List<String> sgIdList = new ArrayList<>();
        GetVolumeResultType volumeResult = fetchVolume(volumeId);
        VolumeType[] volumeBeans = volumeResult.getVolume();
        if (!volumeResult.isSuccessfulStatus() || ArrayUtils.isEmpty(volumeBeans)) {
            log.error("Failed to fetch volume with id {} : {}", volumeId, volumeResult.getMessage());
            throw new NoResourceFoundException(volumeResult.getMessage());
        }

        // Only one volumeBean should be returned for one nativeId.
        String[] sgIds = volumeBeans[0].getStorageGroupId();
        if (!ArrayUtils.isEmpty(sgIds)) {
            sgIdList.addAll(Arrays.asList(sgIds));
        }

        return sgIdList;
    }

    /**
     * Find host and maskingview mapping that connected to this initiator.
     * 
     * @param initiator
     * @return Map<String, List<String>> {hostId : [mvs]}
     * @throws NoResourceFoundException
     */
    private Map<String, List<String>> findHostMvMappingWithInitiator(Initiator initiator) throws NoResourceFoundException {
        Map<String, List<String>> hostMvMap = new HashMap<>();
        ListInitiatorResultType initiatorResult = listAllInitiatorsWithHba(initiator.getPort());
        if (!initiatorResult.isSuccessfulStatus() || initiatorResult.getNum_of_initiators() == 0) {
            log.error("Failed to list all initiators with hba {} : {}", initiator.getPort(), initiatorResult.getMessage());
            throw new NoResourceFoundException(initiatorResult.getMessage());
        }

        String initiatorId = initiatorResult.getInitiatorId().get(0);
        // use only one initiatorId will ok, because they are just the same hba mapping to different ports.
        GetInitiatorResultType initiatorBean = fetchInitiator(initiatorId);
        if (!initiatorBean.isSuccessfulStatus() || initiatorBean.getInitiator().isEmpty()) {
            log.error("Failed to find initiator with id {} : {}", initiatorId, initiatorBean.getMessage());
            throw new NoResourceFoundException(initiatorBean.getMessage());
        }
        String hostId = initiatorBean.getInitiator().get(0).getHost();
        List<String> mvs = initiatorBean.getInitiator().get(0).getMaskingview();
        if (!StringUtils.isEmpty(hostId)) {
            hostMvMap.put(hostId, mvs);
        }

        return hostMvMap;
    }

    /*
     * /////////////////////////////////////////////////////////////////////////////////////////
     * Functions that communicate with array through rest call.
     * 
     * /////////////////////////////////////////////////////////////////////////////////////////
     */

    public GenericResultImplType removePortGroup(String pgId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.PORTGROUP_ID, genUrlFillersWithSn(pgId));
        Type responseClazzType = new TypeToken<GenericResultImplType>() {
        }.getType();
        ResponseWrapper<GenericResultImplType> responseWrapper = delete(endPoint, responseClazzType);

        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during removing portGroup):", responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during removing portGroup:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Remove host with id.
     * 
     * @param hostId
     * @return GenericResultImplType
     */
    public GenericResultImplType removeHost(String hostId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST_ID, genUrlFillersWithSn(hostId));
        Type responseClazzType = new TypeToken<GenericResultImplType>() {
        }.getType();
        ResponseWrapper<GenericResultImplType> responseWrapper = delete(endPoint, responseClazzType);

        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during removing host):", responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during removing host:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Remove maskingview.
     * 
     * @param mvId
     * @return
     */
    public GenericResultImplType removeMv(String mvId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.MASKINGVIEW_ID, genUrlFillersWithSn(mvId));
        Type responseClazzType = new TypeToken<GenericResultImplType>() {
        }.getType();
        ResponseWrapper<GenericResultImplType> responseWrapper = delete(endPoint, responseClazzType);

        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during removing maskingview):", responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during removing maskingview:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch storageGroup with id.
     * 
     * @param sgId
     * @return
     */
    public GetStorageGroupResultType fetchSg(String sgId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.STORAGEGROUP_ID, genUrlFillersWithSn(sgId));
        Type responseClazzType = new TypeToken<GetStorageGroupResultType>() {
        }.getType();
        ResponseWrapper<GetStorageGroupResultType> responseWrapper = get(endPoint, responseClazzType);

        GetStorageGroupResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during fetching storagegroup):", responseWrapper.getException());
            responseBean = new GetStorageGroupResultType();
            appendExceptionMessage(responseBean, "Exception happened during fetching storagegroup:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch volume with id.
     * 
     * @param volumeId
     * @return GetVolumeResultType
     */
    public GetVolumeResultType fetchVolume(String volumeId) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.VOLUME_ID, genUrlFillersWithSn(volumeId));
        Type responseClazzType = new TypeToken<GetVolumeResultType>() {
        }.getType();
        ResponseWrapper<GetVolumeResultType> responseWrapper = get(endPoint, responseClazzType);

        GetVolumeResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during fetching volume):", responseWrapper.getException());
            responseBean = new GetVolumeResultType();
            appendExceptionMessage(responseBean, "Exception happened during fetching volume:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Fetch initiator with id.
     * 
     * @param id
     * @return GetInitiatorResultType
     */
    public GetInitiatorResultType fetchInitiator(String id) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.INITIATOR_ID, genUrlFillersWithSn(id));
        Type responseClazzType = new TypeToken<GetInitiatorResultType>() {
        }.getType();
        ResponseWrapper<GetInitiatorResultType> responseWrapper = get(endPoint, responseClazzType);

        GetInitiatorResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during listing Initiators):", responseWrapper.getException());
            responseBean = new GetInitiatorResultType();
            appendExceptionMessage(responseBean, "Exception happened during listing Initiators:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * List all initiators that match hba.
     * 
     * @param hba
     * @return
     */
    public ListInitiatorResultType listAllInitiatorsWithHba(String hba) {
        if (!hba.startsWith(SymConstants.IP_PORT_PREFIX) && hba.contains(SymConstants.Mark.COLON)) {
            hba = hba.replaceAll(SymConstants.Mark.COLON, SymConstants.Mark.EMPTY_STRING);
        }
        Map<String, String> filters = new HashMap<>();
        filters.put("initiator_hba", hba);

        return listAInitiators(filters);
    }

    /**
     * List all initiators with filters.
     * 
     * @param filters
     * @return
     */
    public ListInitiatorResultType listAInitiators(Map<String, String> filters) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.INITIATOR, genUrlFillersWithSn(), filters);
        Type responseClazzType = new TypeToken<ListInitiatorResultType>() {
        }.getType();
        ResponseWrapper<ListInitiatorResultType> responseWrapper = get(endPoint, responseClazzType);

        ListInitiatorResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during listing Initiators):", responseWrapper.getException());
            responseBean = new ListInitiatorResultType();
            appendExceptionMessage(responseBean, "Exception happened during listing Initiators:%s", responseWrapper.getException());
            return responseBean;
        }

        printErrorMessage(responseBean);
        log.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Create host (IG).
     * 
     * @param param (CreateHostParamType)
     * @return GenericResultType
     */
    public GenericResultType createHost(CreateHostParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOST, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<GenericResultType>() {
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

    public GenericResultType createCluster(CreateHostGroupParamType param) {
        String endPoint = UrlGenerator.genUrl(EndPoint.Export.HOSTGROUP, genUrlFillersWithSn());
        Type responseClazzType = new TypeToken<GenericResultType>() {
        }.getType();
        ResponseWrapper<GenericResultType> responseWrapper = post(endPoint, param, responseClazzType);

        GenericResultType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            log.error("Exception happened during creating HostGroup(CIG):", responseWrapper.getException());
            responseBean = new GenericResultType();
            appendExceptionMessage(responseBean, "Exception happened during creating HostGroup(CIG):%s", responseWrapper.getException());
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
            log.error("Exception happened during creating portgroup(PG): {}", responseWrapper.getException());
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
