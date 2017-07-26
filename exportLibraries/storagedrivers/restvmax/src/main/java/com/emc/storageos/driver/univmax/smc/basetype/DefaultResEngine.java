/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.basetype;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.restengine.RestHandler;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.IteratorType;
import com.emc.storageos.driver.univmax.smc.symmetrix.resource.ResultListType;
import com.emc.storageos.driver.univmax.utils.UrlGenerator;

public class DefaultResEngine {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultResEngine.class);
    protected AuthenticationInfo authenticationInfo;
    protected UrlGenerator urlGenerator;
    protected RestHandler engine;

    public static class EndPointHolder {
        public final static String LIST_RESOURCE_URL = "/common/Iterator/%s/page";

    }

    public final static String FROM_KEY = "from";
    public final static String TO_KEY = "to";

    /**
     * @param authenticationInfo
     */
    public DefaultResEngine(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.urlGenerator = new UrlGenerator(authenticationInfo);
        engine = new RestHandler(authenticationInfo);
    }

    /**
     * @return the authenticationInfo
     */
    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public void appendExceptionMessage(IResponse responseBean, String template, Object... params) {
        responseBean.setHttpStatusCode(SymConstants.StatusCode.EXCEPTION);
        responseBean.appendCustMessage(String.format(template, params));
    }

    public List<String> genUrlFillersWithSn(String... fillers) {
        List<String> urlFillers = genUrlFillers(fillers);
        urlFillers.add(0, authenticationInfo.getSn());
        return urlFillers;
    }

    public List<String> genUrlFillers(String... fillers) {
        List<String> urlFillers = new ArrayList<>();
        urlFillers.addAll(Arrays.asList(fillers));
        return urlFillers;
    }

    /**
     * Call iterator to list resources.
     * 
     * @param iteratorId
     * @param from
     * @param to
     * @return
     */
    public <T extends IResponse> ResultListType<T> getNextPageForIterator(String iteratorId, int from, int to, Type responseClazzType) {

        Map<String, String> filters = new HashMap<>();
        filters.put(FROM_KEY, String.valueOf(from));
        filters.put(TO_KEY, String.valueOf(to));
        String url = urlGenerator.genUrl(EndPointHolder.LIST_RESOURCE_URL, genUrlFillers(iteratorId), filters);

        ResponseWrapper<ResultListType> responseWrapper = engine.get(url, responseClazzType);
        ResultListType<T> responseBean = responseWrapper.getResponseBean();
        if (responseWrapper.getException() != null) {
            LOG.error("Exception happened during listing resources:{}", responseWrapper.getException());
            responseBean = new ResultListType<>();
            appendExceptionMessage(responseBean, "Exception happened during listing resources:%s",
                    responseWrapper.getException());
            return responseBean;
        }

        if (!responseBean.isSuccessfulStatus()) {
            LOG.error("{}: Failed to list resources with error: {}", responseBean.getHttpStatusCode(),
                    responseBean.getCustMessage());
        }
        LOG.debug("Output response bean as : {}", responseBean);
        return responseBean;
    }

    /**
     * Get the left resources of Iterator.
     * 
     * @param iterator
     * @param responseClassType
     * @return
     */
    public <T extends IResponse> List<T> getLeftAllResourcesOfIterator(IteratorType<T> iterator, Type responseClassType) {
        int total = iterator.getCount();
        int pageSize = iterator.getMaxPageSize();
        int left = total - pageSize;
        List<T> resourceList = new ArrayList<>();
        // get the left resources
        int from = pageSize + 1;
        int to = from - 1;
        while (left > 0) {
            if (left > pageSize) {
                to += pageSize;
                left = left - pageSize;
            } else {
                to += left;
                left = 0;
            }
            ResultListType<T> resultList = getNextPageForIterator(iterator.getId(), from, to, responseClassType);
            resourceList.addAll(processResultListType(resultList));

            from = to + 1;

        }

        return resourceList;
    }

    /**
     * Get all resources of iterator.
     * 
     * @param iterator
     * @param responseClassType
     * @return
     */
    public <T extends IResponse> List<T> getAllResourcesOfItatrator(IteratorType<T> iterator, Type responseClassType) {
        List<T> resourceList = new ArrayList<>();
        resourceList.addAll(iterator.fetchAllResults());
        resourceList.addAll(getLeftAllResourcesOfIterator(iterator, responseClassType));

        return resourceList;
    }

    private <T extends IResponse> List<T> processResultListType(ResultListType<T> resultList) {
        if (resultList.isSuccessfulStatus()) {
            return resultList.getResult();
        }
        LOG.error("Error happened:{}", resultList.getCustMessage());
        return new ArrayList<>();
    }
}
