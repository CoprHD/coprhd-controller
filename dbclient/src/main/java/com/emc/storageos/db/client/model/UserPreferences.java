/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

@Cf("UserPreferences")
public class UserPreferences extends ModelObject {

    public static final String NOTIFY_BY_EMAIL = "notifyByEmail";
    public static final String EMAIL = "email";
    public static final String USER_ID = "userId";

    private String userId;
    private Boolean notifyByEmail;
    private String email;

    @AlternateId("UserToPreferences")
    @Name(USER_ID)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        setChanged(USER_ID);
    }

    @Name(NOTIFY_BY_EMAIL)
    public Boolean getNotifyByEmail() {
        return notifyByEmail;
    }

    public void setNotifyByEmail(Boolean notifyByEmail) {
        this.notifyByEmail = notifyByEmail;
        setChanged(NOTIFY_BY_EMAIL);
    }

    @Name(EMAIL)
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        setChanged(EMAIL);
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getUserId(), getNotifyByEmail(), getEmail(), getId() };
    }

}
