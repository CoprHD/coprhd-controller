/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.scaleio.api;

import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScaleIOQueryAllSDSResult {
    public static final String SDS_ID = "SDS_ID";
    public static final String SDS_NAME = "SDS_NAME";
    public static final String SDS_IP = "SDS_IP";
    public static final String SDS_PORT = "SDS_PORT";

    private final static String EMPTY_STRING = "";

    private Map<String, String> idMap = new HashMap<>();
    private Map<String, String> nameMap = new HashMap<>();
    private Map<String, List<ScaleIOAttributes>> sdsMap = new HashMap<>();

    public void addProtectionDomain(String protectionDomainId, String protectionDomainName) {
        if (!sdsMap.containsKey(protectionDomainId)) {
            sdsMap.put(protectionDomainId, new ArrayList<ScaleIOAttributes>());
            nameMap.put(protectionDomainId, protectionDomainName);
            idMap.put(protectionDomainName, protectionDomainId);
        }
    }

    public String getProtectionDomainName(String id) {
        String name = nameMap.get(id);
        return (Strings.isNullOrEmpty(name)) ? EMPTY_STRING : name;
    }

    public String getProtectionDomainId(String protectionDomainName) {
        String id = idMap.get(protectionDomainName);
        return (Strings.isNullOrEmpty(id)) ? EMPTY_STRING : id;
    }

    public void addSDS(String protectionDomainId, String sdsId, String sdsName, String sdsIP, String sdsPort) {
        List<ScaleIOAttributes> sdsScaleIOAttributes = sdsMap.get(protectionDomainId);
        ScaleIOAttributes properties = new ScaleIOAttributes();
        properties.put(SDS_ID, sdsId);
        properties.put(SDS_NAME, sdsName);
        properties.put(SDS_IP, sdsIP);
        properties.put(SDS_PORT, sdsPort);
        sdsScaleIOAttributes.add(properties);
    }

    public List<ScaleIOAttributes> getSDSForProtectionDomain(String id) {
        List<ScaleIOAttributes> properties = sdsMap.get(id);
        return (properties != null) ? properties : Collections.EMPTY_LIST;
    }

    public Collection<String> getProtectionDomainIds() {
        return sdsMap.keySet();
    }

}
