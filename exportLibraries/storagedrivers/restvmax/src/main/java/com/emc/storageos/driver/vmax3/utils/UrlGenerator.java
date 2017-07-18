/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.driver.vmax3.smc.SymConstants;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

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

    public static void main(String[] args) {
        String LIST_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume";
        String[] arr = { "11", "33", "44", "55" };
        List<String> list = Arrays.asList(arr);
        Map<String, String> params = new HashMap<String, String>();
        params.put("aaa", "vaaa");
        params.put("bbb", "vbbb");
        AuthenticationInfo authenticationInfo = new AuthenticationInfo("https", "1.2.3.4", 1234, "user", "ppp");
        UrlGenerator gen = new UrlGenerator(authenticationInfo);
        System.out.println(gen.genUrl(LIST_VOLUME_URL, list, params));
    }
}
