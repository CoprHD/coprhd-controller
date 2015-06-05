/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
