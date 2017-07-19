/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.SymConstants;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.basetype.DefaultManager;
import com.emc.storageos.driver.vmax3.smc.basetype.EmptyResponse;
import com.emc.storageos.driver.vmax3.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.CreateStorageGroupParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.EditStorageGroupParameter;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.model.StorageGroupResponse;

public class StorageGroupManager extends DefaultManager {
    /**
     * 
     */
    static class IgnoredErrorMessage {
        public static final String NO_CHANGES_REQUIRED = "No changes required";
        public static final String NO_IO_SET = "No bandwidth, IO value or Dynamic Distribution Flag values have been set";

    }

    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupManager.class);

    static class EndPointHolder {
        public final static String LIST_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String CREATE_SG_URL = "/84/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String EDIT_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup/%s";
        public final static String create_vol_in_SG_URL = "/84/sloprovisioning/symmetrix/%s/storagegroup/%s";

    }

    static enum SgPropertyType {
        SLO,
        WORK_LOAD,
        HOST_IO_LIMIT,
        CREATE_VOLUME
    }

    /**
     * @param authenticationInfo
     */
    public StorageGroupManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

    public StorageGroupResponse createEmptySg(CreateStorageGroupParameter param) {
        return createEmptySg(param, genUrlFillers());
    }

    private StorageGroupResponse createEmptySg(CreateStorageGroupParameter param, List<String> urlFillers) {
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

    public EmptyResponse editSgWithSlo(String sgId, EditStorageGroupParameter param) {
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.SLO);
    }

    public EmptyResponse editSgWithWorkload(String sgId, EditStorageGroupParameter param) {
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.WORK_LOAD);
    }

    public EmptyResponse editSgWithHostIoLimit(String sgId, EditStorageGroupParameter param) {
        return editProperty4Sg(sgId, param, genUrlFillers(sgId), SgPropertyType.HOST_IO_LIMIT);
    }

    public StorageGroupResponse createNewVolInSg(String sgId, EditStorageGroupParameter param) {
        return createNewVolInSg(sgId, param, genUrlFillers(sgId));
    }

    private StorageGroupResponse createNewVolInSg(String sgId, EditStorageGroupParameter param, List<String> urlFillers) {
        String url = urlGenerator.genUrl(EndPointHolder.create_vol_in_SG_URL, urlFillers, null);
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

    private EmptyResponse editProperty4Sg(String sgId, EditStorageGroupParameter param, List<String> urlFillers,
            SgPropertyType sgPropertyType) {
        String url = urlGenerator.genUrl(EndPointHolder.EDIT_SG_URL, urlFillers, null);
        ResponseWrapper<EmptyResponse> responseWrapper = engine.put(url, param, EmptyResponse.class);
        EmptyResponse responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during editing storageGroup for {} :{}", sgPropertyType, responseWrapper.getException());
            responseBean = new EmptyResponse();
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
