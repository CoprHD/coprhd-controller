/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.SymConstants;
import com.emc.storageos.driver.vmax3.smc.basetype.AbstractManager;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.basetype.ResponseWrapper;

public class StorageGroupManager extends AbstractManager {
    private static final Logger LOG = LoggerFactory.getLogger(StorageGroupManager.class);

    static class EndPointHolder {
        public final static String LIST_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String CREATE_SG_URL = "/84/sloprovisioning/symmetrix/%s/storagegroup";
        public final static String EDIT_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup/%s";
        public final static String create_vol_in_SG_URL = "/sloprovisioning/symmetrix/%s/storagegroup/%s";

    }

    /**
     * @param authenticationInfo
     */
    public StorageGroupManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

    public StorageGroupResponse createEmptySg(CreateStorageGroupParameter param, String[] urlFillers) {
        String url = this.urlGenerator.genUrl(EndPointHolder.CREATE_SG_URL, urlFillers, null);
        ResponseWrapper<StorageGroupResponse> responseWrapper = engine.post(url, param, StorageGroupResponse.class);
        StorageGroupResponse responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during creating empty storageGroup:{}", responseWrapper.getException());
            responseBean = new StorageGroupResponse();
            responseBean.setMessage(String.format("Exception happened during creating empty storageGroup:%s",
                    responseWrapper.getException()));
            responseBean.setStatus(SymConstants.StatusCode.EXCEPTION);
            return responseBean;
        }

        if (!responseWrapper.isSuccessfulStatus()) {
            LOG.error("{}: Failed to create empty storageGroup {} with error: {}", responseBean.getStatus(),
                    param.getStorageGroupId(),
                    responseBean.getMessage());
            return null;
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    // public boolean editSgWithSlo(String slo) {
    //
    // }
    //
    // public boolean editSgWithWorkload(String workload) {
    //
    // }

}
