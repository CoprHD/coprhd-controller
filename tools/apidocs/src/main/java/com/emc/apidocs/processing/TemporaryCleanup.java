/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.processing;

import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;

/**
 * Not ideal, but we don't have time to build something generic in a configuration file.
 * 
 * This will clean up some of the bad stuff from comments :-(
 */
public class TemporaryCleanup {

    public static void applyCleanups(ApiService apiService) {
        if (apiService.packageName.contains(".fabric.")) {
            fixFabricPath(apiService);
        }
    }

    public static void applyCleanups(ApiMethod apiMethod) {
        if (apiMethod.getQualifiedName().equals("S3AccessModeOperation::initiateFileAccessModeTransition")) {
            fixPath(apiMethod);
        } else if (apiMethod.getQualifiedName().equals("S3AccessModeOperation::queryFileAccessModeProgress")) {
            fixPath(apiMethod);
        } else if (apiMethod.getQualifiedName().equals("CatalogService::invoke")) {
            fixComment(apiMethod);
        } else if (apiMethod.getQualifiedName().equals("CatalogService::invokeByPath")) {
            fixCommentLink(apiMethod);
        }

        if (apiMethod.apiService.packageName.startsWith("com.emc.storageos.fabric.")) {
            fixFabricRoles(apiMethod);
        }
    }

    public static void fixPath(ApiMethod apiMethod) {
        apiMethod.path = "/?accessmode";
        apiMethod.urlFormat = "Host Style: http://bucketname.ns1.emc.com/?accessmode\n" +
                "Path Style: http://ns1.emc.com/bucketname/?accessmode";
    }

    public static void fixComment(ApiMethod apiMethod) {
        apiMethod.description = apiMethod.description
                .replace(
                        "<p><a href= \"Retrieve_service_descriptor_api_services_{serviceId}.html\">Retrieve service descriptor</a> provides information on retrieving the parameters of the service required for the payload.</p>",
                        "");
    }

    public static void fixCommentLink(ApiMethod apiMethod) {
        apiMethod.description = apiMethod.description
                .replace(
                        "<p><a href= \"Retrieve_service_descriptor_api_services_{serviceId}.html\">Retrieve service descriptor</a> provides information on retrieving the parameters of the service required for the payload.</p>",
                        "");
    }

    public static void fixFabricPath(ApiService apiService) {
        apiService.path = apiService.path.replaceFirst("/external/", "/vdc/fabric/");
    }

    public static void fixFabricRoles(ApiMethod apiMethod) {
        apiMethod.roles.add("SYSTEM_ADMIN");
    }
}
