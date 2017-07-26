/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.univmax.smc.basetype.DefaultResEngine;
import com.emc.storageos.driver.univmax.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.GenericResultImplType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.CreateStorageGroupParameter;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.DynamicDistributionType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.EditStorageGroupActionParam;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.EditStorageGroupParameter;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.EditStorageGroupSLOParam;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.EditStorageGroupWorkloadParam;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.RemoveVolumeParamType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.SetHostIOLimitsParam;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.sg.model.StorageGroupResponse;

public class StorageGroupEngine extends DefaultResEngine {
    /**
     * 
     */
    static class IgnoredErrorMessage {
        public static final String NO_CHANGES_REQUIRED = "No changes required";
        public static final String NO_IO_SET = "No bandwidth, IO value or Dynamic Distribution Flag values have been set";

    }

    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupEngine.class);

    static class EndPointHolder {
        public final static String LIST_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String CREATE_SG_URL = "/84/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String EDIT_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup/%s";
        public final static String CREATE_VOL_IN_SG_URL = "/84/sloprovisioning/symmetrix/%s/storagegroup/%s";
        public final static String REMOVE_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup/%s";

    }

    static enum SgPropertyType {
        SLO,
        WORK_LOAD,
        HOST_IO_LIMIT,
        CREATE_VOLUME,
        REMOVE_VOLUME
    }

    /**
     * @param authenticationInfo
     */
    public StorageGroupEngine(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

    /**
     * Create empty SG.
     * 
     * @param param
     * @return
     */
    public StorageGroupResponse createEmptySg(CreateStorageGroupParameter param) {
        return createEmptySg(param, genUrlFillers());
    }

    /**
     * Create empty SG.
     * 
     * @param param
     * @param urlFillers
     * @return
     */
    public StorageGroupResponse createEmptySg(CreateStorageGroupParameter param, List<String> urlFillers) {
        String url = urlGenerator.genUrl(EndPointHolder.CREATE_SG_URL, urlFillers, null);
        ResponseWrapper<StorageGroupResponse> responseWrapper = engine.post(url, param, StorageGroupResponse.class);
        StorageGroupResponse responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during creating empty storageGroup:{}", responseWrapper.getException());
            responseBean = new StorageGroupResponse();
            appendExceptionMessage(responseBean, "Exception happened during creating empty storageGroup:%s", responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to create empty storageGroup {} with error: {}", responseBean.getHttpStatusCode(),
                    param.getStorageGroupId(),
                    responseBean.getCustMessage());
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Edit SG for SLO property.
     * 
     * @param sgId
     * @param sloId
     * @return
     */
    public GenericResultImplType editSgWithSlo(String sgId, String sloId) {
        EditStorageGroupSLOParam sloParam = new EditStorageGroupSLOParam(sloId);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setEditStorageGroupSLOParam(sloParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.SLO);
    }

    /**
     * Edit SG for Workload property.
     * 
     * @param sgId
     * @param workload
     * @return
     */
    public GenericResultImplType editSgWithWorkload(String sgId, String workload) {
        EditStorageGroupWorkloadParam wlParam = new EditStorageGroupWorkloadParam(workload);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setEditStorageGroupWorkloadParam(wlParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.WORK_LOAD);
    }

    /**
     * Edit SG for host io limit properties.
     * 
     * @param sgId
     * @param hostIoLimitMbSec
     * @param hostIoLimitIoSec
     * @param dynamicDistribution
     * @return
     */
    public GenericResultImplType editSgWithHostIoLimit(String sgId, String hostIoLimitMbSec, String hostIoLimitIoSec,
            DynamicDistributionType dynamicDistribution) {
        SetHostIOLimitsParam setHostIOLimitsParam = new SetHostIOLimitsParam(hostIoLimitMbSec, hostIoLimitIoSec, dynamicDistribution);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setSetHostIOLimitsParam(setHostIOLimitsParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.HOST_IO_LIMIT);
    }

    /**
     * Remove volumes from SG.
     * 
     * @param sgId
     * @param volumeIds
     * @return
     */
    public GenericResultImplType removeVolumeFromSg(String sgId, List<String> volumeIds) {
        RemoveVolumeParamType removeVolumeParam = new RemoveVolumeParamType(volumeIds);
        EditStorageGroupActionParam actionParam = new EditStorageGroupActionParam();
        actionParam.setRemoveVolumeParam(removeVolumeParam);
        EditStorageGroupParameter param = new EditStorageGroupParameter();
        param.setEditStorageGroupActionParam(actionParam);
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.REMOVE_VOLUME);
    }

    /**
     * Create new volumes in SG.
     * 
     * @param sgId
     * @param param EditStorageGroupParameter
     * @return StorageGroupResponse
     */
    public StorageGroupResponse createNewVolInSg(String sgId, EditStorageGroupParameter param) {
        return createNewVolInSg(sgId, param, genUrlFillers(sgId));
    }

    /**
     * Remove empty SG.
     * 
     * @param sgId
     * @return
     */
    public GenericResultImplType removeEmptySg(String sgId) {
        String url = urlGenerator.genUrl(EndPointHolder.REMOVE_SG_URL, genUrlFillers(sgId), null);
        ResponseWrapper<GenericResultImplType> responseWrapper = engine.delete(url, GenericResultImplType.class);
        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during removing storageGroup {}:{}", sgId, responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during removing storageGroup %s:%s",
                    sgId,
                    responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to remove storageGroup {} with error: {}", responseBean.getHttpStatusCode(),
                    sgId,
                    responseBean.getCustMessage() + responseBean.getMessage());
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Create new volumes in SG.
     * 
     * @param sgId
     * @param param
     * @param urlFillers
     * @return
     */
    public StorageGroupResponse createNewVolInSg(String sgId, EditStorageGroupParameter param, List<String> urlFillers) {
        String url = urlGenerator.genUrl(EndPointHolder.CREATE_VOL_IN_SG_URL, urlFillers, null);
        ResponseWrapper<StorageGroupResponse> responseWrapper = engine.put(url, param, StorageGroupResponse.class);
        StorageGroupResponse responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during creating volume in storageGroup:{}", responseWrapper.getException());
            responseBean = new StorageGroupResponse();
            appendExceptionMessage(responseBean, "Exception happened during creating volume in storageGroup:%s",
                    responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to create volume in storageGroup {} with error: {}", responseBean.getHttpStatusCode(),
                    sgId,
                    responseBean.getCustMessage());
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Edit Sg.
     * 
     * @param sgId
     * @param param
     * @param urlFillers
     * @param sgPropertyType
     * @return
     */
    public GenericResultImplType editProperty4Sg(String sgId, EditStorageGroupParameter param, List<String> urlFillers,
            SgPropertyType sgPropertyType) {
        String url = urlGenerator.genUrl(EndPointHolder.EDIT_SG_URL, urlFillers, null);
        ResponseWrapper<GenericResultImplType> responseWrapper = engine.put(url, param, GenericResultImplType.class);
        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during editing storageGroup for {} :{}", sgPropertyType, responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during editing storageGroup for %s:%s",
                    sgPropertyType, responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to edit {} for storageGroup {} with error: {}", sgPropertyType, responseBean.getHttpStatusCode(),
                    sgId,
                    responseBean.getCustMessage());
            if (responseBean.getCustMessage().equals(IgnoredErrorMessage.NO_CHANGES_REQUIRED)) {
                LOG.info("This error should be ignored, for the resource has the properties already.");
                responseBean.setHttpStatusCode(SymConstants.StatusCode.OK);
            }
            if (sgPropertyType.equals(SgPropertyType.HOST_IO_LIMIT) && responseBean.getCustMessage().equals(IgnoredErrorMessage.NO_IO_SET)) {
                LOG.info("This error should be ignored, for the resource has the properties already.");
                responseBean.setHttpStatusCode(SymConstants.StatusCode.OK);
            }
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

}
