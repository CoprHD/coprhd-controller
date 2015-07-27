/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.storageos.model.DataObjectRestRep;
import com.google.common.collect.Maps;

public class TagUtils {

    public static String ORDER_ID_TAG = "vipr:orderId";
    public static String ORDER_NUMBER_TAG = "vipr:orderNumber";
    
    private static Pattern MACHINE_TAG_REGEX = Pattern.compile("([^W]*\\:[^W]*)=(.*)");

    public static Map<String, String> parseTags(Collection<String> tags) {
        Map<String, String> machineTags = Maps.newHashMap();
        for (String tag : tags) {
            Matcher matcher = MACHINE_TAG_REGEX.matcher(tag);
            if (matcher.matches()) {
                machineTags.put(matcher.group(1), matcher.group(2));
            }
        }
        return machineTags;
    }    
    
    public static String getTagValue(DataObjectRestRep dataObject, String tagName) {
        if (dataObject == null || (dataObject.getTags() == null)) {
            return null;
        }

        Map<String, String> currentMachineTags = parseTags(dataObject.getTags());
        return currentMachineTags.get(tagName);
    }    
    
    public static String getOrderIdTagValue(DataObjectRestRep dataObject) {
        return getTagValue(dataObject, ORDER_ID_TAG);
    }
    
    public static String getOrderNumberTagValue(DataObjectRestRep dataObject) {
        return getTagValue(dataObject, ORDER_NUMBER_TAG);
    }    
    
    public static String createOrderIdTag(String orderId) {
        return String.format("%s=%s", ORDER_ID_TAG, orderId);
    }
    
}
