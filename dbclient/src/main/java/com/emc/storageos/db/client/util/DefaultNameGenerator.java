/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.util.UUID;

public class DefaultNameGenerator implements NameGenerator {

    @Override
    public String generate(String tenant, String resource, String resourceUrn,
                           char delimiter, int maxLength) {
        String tenantName = tenant.replaceAll("\\s+|[^a-zA-Z0-9]", "");
        String resourceName = resource.replaceAll("\\s+|[^a-zA-Z0-9]", "");
        String resourceUUID = (resourceUrn == null) ? UUID.randomUUID().toString() :
                resourceUrn.split(":")[3];
        String result = tenantName + delimiter +
                resourceName + delimiter + resourceUUID;
        // If the resulting string is longer than the maxLength,
        // then truncate the tenant and/or the resource names,
        // so the result length is at most maxLength.
        if (result.length() > maxLength) {
            int tenantNameLength = tenantName.length();
            int resourceNameLength = resourceName.length();
            int whatsLeft = maxLength - resourceUUID.length() - 2;
            int whatsExtra = (tenantNameLength + resourceNameLength) - whatsLeft;
            // Truncate the name that is longer
            if ((whatsExtra > whatsLeft) ||
                    (tenantNameLength == resourceNameLength)) {
                int halfOfLeft = whatsLeft/2;
                String adjustedTenant = (halfOfLeft > tenantNameLength) ? tenantName : tenantName.substring(0, halfOfLeft);
                String adjustedResource = (halfOfLeft > resourceNameLength) ? resourceName : resourceName.substring(0, halfOfLeft);
                result = adjustedTenant + delimiter + adjustedResource + delimiter + resourceUUID;
            } else if (resourceNameLength > tenantNameLength) {
                result = tenantName + delimiter + resourceName.substring(0,
                        resourceNameLength - whatsExtra) + delimiter + resourceUUID;
            } else if (tenantNameLength > resourceNameLength) {
                result = tenantName.substring(0, tenantNameLength - whatsExtra) +
                        delimiter + resourceName + delimiter + resourceUUID;
            }
        }

        return result;
    }
}
