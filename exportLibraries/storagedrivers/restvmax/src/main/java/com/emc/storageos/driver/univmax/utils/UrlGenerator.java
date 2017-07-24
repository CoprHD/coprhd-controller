/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.utils;

import java.util.List;
import java.util.Map;

import com.emc.storageos.driver.univmax.SymConstants;
import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;

final public class UrlGenerator {
    private AuthenticationInfo authenticationInfo;

    /**
     * @param authenticationInfo
     */
    public UrlGenerator(AuthenticationInfo authenticationInfo) {
        super();
        this.authenticationInfo = authenticationInfo;
    }

    /**
     * Generate the URL with the parameters input. It will add the host information and BASE_URL at the front of the input url.
     * 
     * @param url
     * @param urlFillers
     * @param urlParams
     * @return formated URL
     */
    public String genUrl(String url, List<String> urlFillers, Map<String, String> urlParams) {
        return new StringBuilder(formatHostUrl())
                .append(SymConstants.BASE_URL)
                .append(formatCustUrl(url, urlFillers))
                .append(formatUrlParams(urlParams))
                .toString();

    }

    private String formatHostUrl() {
        return String.format(SymConstants.HOST_FORMAT, authenticationInfo.getProtocol(), authenticationInfo.getHost(),
                authenticationInfo.getPort());
    }

    private String formatCustUrl(String url, List<String> urlFillers) {
        return String.format(url, urlFillers.toArray());
    }

    private String formatUrlParams(Map<String, String> urlParams) {
        if (CollectionUtils.isNullOrEmptyMap(urlParams)) {
            return "";
        }

        StringBuilder sb = new StringBuilder(SymConstants.MarkHolder.QUESTION_MARK);
        for (Map.Entry<String, String> entry : urlParams.entrySet()) {
            sb.append(entry.getKey())
                    .append(SymConstants.MarkHolder.EQUAL_MARK)
                    .append(entry.getValue())
                    .append(SymConstants.MarkHolder.AND_MARK);
        }
        return sb.toString();
    }

}
