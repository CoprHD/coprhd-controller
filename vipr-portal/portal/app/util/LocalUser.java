/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import org.apache.commons.lang.StringUtils;

public enum LocalUser {

    ROOT, SVCUSER, PROXYUSER, SYSMONITOR;

    public static boolean isLocalUser(String username) {
        if (StringUtils.isNotBlank(username)) {
            for (LocalUser localUser : LocalUser.values()) {
                if (StringUtils.endsWithIgnoreCase(username, localUser.name())) {
                    return true;
                }
            }
        }
        return false;
    }

}
