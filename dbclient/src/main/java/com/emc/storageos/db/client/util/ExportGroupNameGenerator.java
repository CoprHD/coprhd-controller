/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

/**
 * This generator will return the user specified label. The name will have
 * non-alphanumeric and whitespace characters removed. It will be truncated to the
 * maxLength if necessary.
 */
public class ExportGroupNameGenerator extends ResourceOnlyNameGenerator {

    @Override
    public String generate(String ignore, String resource, String ignore2,
                           char ignore3, int maxLength) {
       
        return removeSpecialCharsForName(resource, maxLength);
    }

    public static String removeSpecialCharsForName(String resource, int maxLength){
        String result = null;
        if(resource!=null){
            // TODO: FIX ME - it's possible for the user to pass in all whitespace
            // causing the result to be an empty string.
            // Also \\s+ is redundant in the regex
            String resourceName = resource.replaceAll("\\s+|[^a-zA-Z0-9]", "");
            result = resourceName;
            if (result.matches("^\\d+.*")) {
                result = String.format("V%s", result);
            }
            // If larger than the max size, truncate
            if (result.length() > maxLength) {
                result = resourceName.substring(0, maxLength);
            }
        }
        return result;
    }
}
