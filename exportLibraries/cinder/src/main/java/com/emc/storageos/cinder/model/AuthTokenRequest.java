/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

public class AuthTokenRequest {

    public Auth auth = new Auth();

    public class Auth {
        public PasswordCredentials passwordCredentials = new PasswordCredentials();
        public String tenantName;
    }

    public class PasswordCredentials {
        public String username;
        public String password;
    }

}
