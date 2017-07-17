/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractManager;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.IteratorType;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.bean.VolumeListResultType;
import com.google.gson.reflect.TypeToken;

public class VolumeManager extends AbstractManager {
    private static final Logger LOG = LoggerFactory.getLogger(VolumeManager.class);

    static class EndPointHolder {
        public final static String LIST_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume";
        public final static String GET_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume/%s";
    }

    /**
     * @param authenticationInfo
     */
    public VolumeManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

    public IteratorType<VolumeListResultType> listVolumes(List<String> urlFillers,
            Map<String, String> urlParams) {
        String url = urlGenerator.genUrl(EndPointHolder.LIST_VOLUME_URL, urlFillers, urlParams);
        Type responseClazzType = new TypeToken<IteratorType<VolumeListResultType>>() {
        }.getType();
        ResponseWrapper<VolumeListResultType> responseWrapper = engine.list(url, VolumeListResultType.class, responseClazzType);
        IteratorType<VolumeListResultType> responseBeanIterator = responseWrapper.getResponseBeanIterator();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during listing volumes:{}", responseWrapper.getException());
            responseBeanIterator = new IteratorType<VolumeListResultType>();
            appendExceptionMessage(responseBeanIterator, "Exception happened during listing volumes:%s",
                    responseWrapper.getException());
            return responseBeanIterator;
        }

        if (!responseBeanIterator.isSuccessfulStatus()) {
            LOG.error("{}: Failed to list volumes with error: {}", responseBeanIterator.getStatus(),
                    responseBeanIterator.getMessage());
        }
        LOG.debug("Output response bean as : {}", responseBeanIterator);
        return responseBeanIterator;
    }

    public List<String> findValidVolumes(List<String> urlFillers, Map<String, String> filters) {
        List<String> volIds = new ArrayList<String>();
        IteratorType<VolumeListResultType> iterator = listVolumes(urlFillers, filters);
        if (!iterator.isSuccessfulStatus() && iterator.getCount() <= 0) {
            LOG.warn("Cannot find valid volumes with filters {}", filters);
            return volIds;
        }

        List<VolumeListResultType> volumeList = iterator.fetchAllResults();
        for (VolumeListResultType volume : volumeList) {
            volIds.add(volume.getVolumeId());
        }

        return volIds;
    }

}
