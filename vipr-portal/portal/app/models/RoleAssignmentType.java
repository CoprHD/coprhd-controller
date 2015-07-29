/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import org.apache.commons.lang.StringUtils;

import util.MessagesUtils;

public enum RoleAssignmentType {
    USER,
    GROUP;

    public static boolean isUser(String type) {
        return USER == valueOf(type);
    }

    public static boolean isGroup(String type) {
        return GROUP == valueOf(type);
    }

    public String getDisplayName() {
        String displayName = name();
        String messageKey = "RoleAssignmentType." + name();
        String localized = MessagesUtils.get(messageKey);
        if (StringUtils.isNotBlank(localized) && StringUtils.equals(messageKey, localized) == false) {
            displayName = localized;
        }
        return displayName;
    }

}
