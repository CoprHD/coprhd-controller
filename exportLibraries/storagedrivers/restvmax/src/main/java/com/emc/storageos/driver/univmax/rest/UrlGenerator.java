/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;

import com.emc.storageos.driver.univmax.SymConstants;

final public class UrlGenerator {

    /**
     * Generate the URL with the parameters input.
     * 
     * @param url
     * @param urlFillers
     * @param urlParams
     * @return formated URL
     */
    public static String genUrl(String url, List<String> urlFillers, Map<String, String> urlParams) {
        return new StringBuilder()
                .append(formatCustUrl(url, urlFillers))
                .append(formatUrlParams(urlParams))
                .toString();

    }

    /**
     * Generate the URL with the parameters input.
     * 
     * @param url
     * @param urlFillers
     * @return
     */
    public static String genUrl(String url, List<String> urlFillers) {
        return genUrl(url, urlFillers, null);
    }

    private static String formatCustUrl(String url, List<String> urlFillers) {
        return String.format(url, urlFillers.toArray());
    }

    private static String formatUrlParams(Map<String, String> urlParams) {
        if (MapUtils.isEmpty(urlParams)) {
            return "";
        }

        StringBuilder sb = new StringBuilder(SymConstants.Mark.QUESTION);
        for (Map.Entry<String, String> entry : urlParams.entrySet()) {
            sb.append(entry.getKey())
                    .append(SymConstants.Mark.EQUAL)
                    .append(entry.getValue())
                    .append(SymConstants.Mark.AND);
        }
        return sb.toString();
    }

    public static List<String> genUrlFillers(String... fillers) {
        List<String> urlFillers = new ArrayList<>();
        urlFillers.addAll(Arrays.asList(fillers));
        return urlFillers;
    }

}
