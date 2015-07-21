/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder.model;

/**
 * Wrapper for all access related OpenStack structures
 * 
 */
public class AccessWrapper {
	public Access access;
    public class Access{
        public Token token;
        public Service[] serviceCatalog;
        public User user;
        public Metadata metadata;
    }

    public class Token{
        public String issued_at;
        public String expires;
        public Tenant tenant;
        public String id;
    }

    public class Tenant{
        public String description;
        public boolean enabled;
        public String name;
        public String id;
    }

    public class Service{
        public Endpoint[] endpoints;
        public String type;
        public String name;
        public String[] endpoint_links;
    }

    public class Endpoint{
        public String adminURL;
        public String internalURL;
        public String publicURL;
        public String region;
        public String id;
    }

    public class User{
        public String username;
        public String name;
        public String id;
        public Role[] roles;
        public String[] roles_links;
    }

    public class Role{
        public String name;
    }

    public class Metadata{
        public String[] roles;
        public int is_admin;
    }
}
