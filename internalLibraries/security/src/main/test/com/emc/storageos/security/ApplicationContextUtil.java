/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security;

import org.springframework.context.support.GenericXmlApplicationContext;

public class ApplicationContextUtil {

    public static String[] SECURITY_CONTEXTS = new String[] {
            "security-emc-conf.xml",
            "security-oss-conf.xml",
    };

    public static void initContext(String buildType, String ... contextFiles) {
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        ctx.getEnvironment().setActiveProfiles(buildType);
        ctx.load(contextFiles);
        ctx.refresh();
    }
}
