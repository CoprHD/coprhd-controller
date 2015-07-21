/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.util.UUID;

/**
 * This generator will return the user specified label plus the UUID. The label will
 * have non-alphanumeric and whitespace characters removed. The format is:
 *
 *   "[User-Specified-Name]-[UUID]"
 *
 * Example:
 *
 *   VOL456-3dc1d55f-b9cc-40d0-bb3f-90b928622cdd
 *
 * If the generated name is longer than maxLength, then the user specified label will
 * be truncated to fit.
 */
public class ResourceAndUUIDNameGenerator implements NameGenerator {

    @Override
    public String generate(String ignore, String resource, String resourceUrn,
                           char delimiter, int maxLength) {
        String resourceName = resource.replaceAll("\\s+|[^a-zA-Z0-9]", "");
        String resourceUUID = (resourceUrn == null) ? UUID.randomUUID().toString() :
                resourceUrn.split(":")[3];
        String result = resourceName + delimiter + resourceUUID;
        // If the resulting string is longer than the maxLength,
        // then truncate the tenant and/or the resource names,
        // so the result length is at most maxLength.
        if (result.length() > maxLength) {
            int whatsLeft = maxLength - resourceUUID.length() - 1;
            result = resourceName.substring(0, whatsLeft) + delimiter + resourceUUID;
        }

        return result;
    }
}
