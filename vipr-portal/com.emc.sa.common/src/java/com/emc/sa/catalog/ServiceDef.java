/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import org.apache.commons.lang.StringUtils;

import java.util.Map;

public class ServiceDef {
    public String label;
    public String title;
    public String image;
    public String description;
    public String baseService;
    public Map<String,String> lockFields;

    public String getName() {
        return StringUtils.defaultIfBlank(this.label, StringUtils.replace(title, " ", ""));
    }
}
