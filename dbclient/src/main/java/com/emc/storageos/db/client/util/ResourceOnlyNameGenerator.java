/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.db.client.util;

/**
 * This generator will return the user specified label. The name will have
 * non-alphanumeric and whitespace characters removed. It will be truncated to the
 * maxLength if necessary.
 */
public class ResourceOnlyNameGenerator implements NameGenerator {

    public static final String INVALID_CHARS_REGEX = "\\s+|[^a-zA-Z0-9_#@\\+\\-]";

    @Override
    public String generate(String ignore, String resource, String ignore2,
                           char ignore3, int maxLength) {
       
        return removeSpecialCharsForName(resource, maxLength);
    }
    
    
    public static String removeSpecialCharsForName(String resource, int maxLength){
        String result = null;
        if(resource!=null){
            String resourceName = resource.replaceAll(INVALID_CHARS_REGEX, "");
            result = resourceName;
            // If larger than the max size, truncate
            if (result.length() > maxLength) {
                result = resourceName.substring(0, maxLength);
            }
        }
        return result;
    }

    // This method allows you specify a String to replace invalid characters with
    public static String removeSpecialCharsForName(String resource,
                                                   String replace, int maxLength){
        String result = null;
        if(resource!=null){
            String fixedReplace = replace.replaceAll(INVALID_CHARS_REGEX, "");
            String resourceName = resource.replaceAll(INVALID_CHARS_REGEX, fixedReplace);
            result = resourceName;
            // If larger than the max size, truncate
            if (result.length() > maxLength) {
                result = resourceName.substring(0, maxLength);
            }
        }
        return result;
    }
}
