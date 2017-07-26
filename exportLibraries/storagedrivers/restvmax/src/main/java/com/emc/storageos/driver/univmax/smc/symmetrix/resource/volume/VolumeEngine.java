/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.univmax.smc.basetype.DefaultResEngine;
import com.emc.storageos.driver.univmax.smc.basetype.ResponseWrapper;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.GenericResultImplType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.IteratorType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume.model.GetVolumeResultType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume.model.VolumeListResultType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.volume.model.VolumeType;
import com.google.gson.reflect.TypeToken;

public class VolumeEngine extends DefaultResEngine {
    private static final Logger LOG = LoggerFactory.getLogger(VolumeEngine.class);

    static class EndPointHolder {
        public final static String LIST_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume";
        public final static String GET_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume/%s";
        public final static String REMOVE_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume/%s";
    }

    /**
     * @param authenticationInfo
     */
    public VolumeEngine(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

    /**
     * List all volumes with filters.
     * 
     * @param filters
     * @return
     */
    public IteratorType<VolumeListResultType> listVolumes(Map<String, String> filters) {
        return listVolumes(genUrlFillers(), filters);
    }

    /**
     * List all volumes with filters.
     * 
     * @param urlFillers
     * @param filters
     * @return
     */
    public IteratorType<VolumeListResultType> listVolumes(List<String> urlFillers,
            Map<String, String> filters) {
        String url = urlGenerator.genUrl(EndPointHolder.LIST_VOLUME_URL, urlFillers, filters);
        Type responseClazzType = new TypeToken<IteratorType<VolumeListResultType>>() {
        }.getType();
        ResponseWrapper<VolumeListResultType> responseWrapper = engine.list(url, responseClazzType);
        IteratorType<VolumeListResultType> responseBeanIterator = responseWrapper.getResponseBeanIterator();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during listing volumes:{}", responseWrapper.getException());
            responseBeanIterator = new IteratorType<VolumeListResultType>();
            appendExceptionMessage(responseBeanIterator, "Exception happened during listing volumes:%s",
                    responseWrapper.getException());
            return responseBeanIterator;
        }

        if (!responseBeanIterator.isSuccessfulStatus()) {
            LOG.error("{}: Failed to list volumes with error: {}", responseBeanIterator.getHttpStatusCode(),
                    responseBeanIterator.getCustMessage());
        }
        LOG.debug("Output response bean as : {}", responseBeanIterator);
        return responseBeanIterator;
    }

    /**
     * Find volume ids with filters.
     * 
     * @param filters
     * @return List<String> volumeIds
     */
    public List<String> findValidVolumes(Map<String, String> filters) {
        return findValidVolumes(genUrlFillers(), filters);
    }

    /**
     * Find valid volumes with filters.
     * 
     * @param urlFillers
     * @param filters
     * @return
     */
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

    /**
     * Fetch one volume with volumeId.
     * 
     * @param volumeId
     * @return VolumeType
     */
    public VolumeType fetchVolume(String volumeId) {
        return fetchVolume(volumeId, genUrlFillers(volumeId));
    }

    /**
     * Fetch volume with id.
     * 
     * @param volumeId
     * @param urlFillers
     * @return
     */
    public VolumeType fetchVolume(String volumeId, List<String> urlFillers) {
        String url = urlGenerator.genUrl(EndPointHolder.GET_VOLUME_URL, urlFillers, null);
        ResponseWrapper<GetVolumeResultType> responseWrapper = engine.get(url, GetVolumeResultType.class);
        GetVolumeResultType responseBean = responseWrapper.getResponseBean();
        VolumeType volumeBean = new VolumeType();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during fetching volume {}:{}", volumeId, responseWrapper.getException());
            appendExceptionMessage(volumeBean, "Exception happened during fetching volume %s:%s",
                    volumeId,
                    responseWrapper.getException());
            volumeBean.setHttpStatusCode(responseBean.getHttpStatusCode());
            return volumeBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to fetch volume {} with error: {}", volumeId, responseBean.getHttpStatusCode(),
                    responseBean.getCustMessage());
            volumeBean.setHttpStatusCode(responseBean.getHttpStatusCode());
            volumeBean.setCustMessage(responseBean.getCustMessage());
            return volumeBean;
        }

        volumeBean = responseBean.getVolume()[0];
        volumeBean.setHttpStatusCode(responseBean.getHttpStatusCode());

        LOG.debug("Output response bean as : {}", volumeBean);
        return volumeBean;
    }

    /**
     * Remove standard volume (volume without local or remote replication).
     * 
     * @param volumeId
     * @return
     */
    public GenericResultImplType removeStandardVolume(String volumeId) {
        String url = urlGenerator.genUrl(EndPointHolder.REMOVE_VOLUME_URL, genUrlFillers(volumeId), null);
        ResponseWrapper<GenericResultImplType> responseWrapper = engine.delete(url, GenericResultImplType.class);
        GenericResultImplType responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during removing volume {}:{}", volumeId, responseWrapper.getException());
            responseBean = new GenericResultImplType();
            appendExceptionMessage(responseBean, "Exception happened during removing volume %s:%s",
                    volumeId,
                    responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to remove volume {} with error: {}", responseBean.getHttpStatusCode(),
                    volumeId,
                    responseBean.getCustMessage() + responseBean.getMessage());
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }
}
