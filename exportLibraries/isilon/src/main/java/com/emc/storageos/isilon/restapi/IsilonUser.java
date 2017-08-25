/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing the isilon user object
 * member names should match the key names in json object
 *
 * @author sauraa
 *
 */
public class IsilonUser {

    private String dn;
    private String dnsDomain;
    private String domain;
    private String email;
    private Boolean enabled;
    private Boolean expired;
    private String expiry;
    private String gecos;
    private Boolean generatedGid;
    private Boolean generatedUid;
    private Boolean generatedUpn;
    private Identity gid;
    private String homeDirectory;
    private String id;
    private Boolean locked;
    private String maxPasswordAge;
    private String memberOf;
    private String name;
    private Identity onDiskGroupIdentity;
    private Identity onDiskUserIdentity;
    private Boolean passwordExpired;
    private Boolean passwordExpires;
    private String passwordExpiry;
    private String passwordLastSet;
    private Identity primaryGroupSid;
    private Boolean promptPasswordChange;
    private String provider;
    private String samAccountName;
    private String shell;
    private Identity sid;
    private String type;
    private Identity uid;
    private String upn;
    private Boolean userCanChangePassword;
    private Map<String, String> additionalProperties = new HashMap<String, String>();

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getDnsDomain() {
        return dnsDomain;
    }

    public void setDnsDomain(String dnsDomain) {
        this.dnsDomain = dnsDomain;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public String getGecos() {
        return gecos;
    }

    public void setGecos(String gecos) {
        this.gecos = gecos;
    }

    public Boolean getGeneratedGid() {
        return generatedGid;
    }

    public void setGeneratedGid(Boolean generatedGid) {
        this.generatedGid = generatedGid;
    }

    public Boolean getGeneratedUid() {
        return generatedUid;
    }

    public void setGeneratedUid(Boolean generatedUid) {
        this.generatedUid = generatedUid;
    }

    public Boolean getGeneratedUpn() {
        return generatedUpn;
    }

    public void setGeneratedUpn(Boolean generatedUpn) {
        this.generatedUpn = generatedUpn;
    }

    public Identity getGid() {
        return gid;
    }

    public void setGid(Identity gid) {
        this.gid = gid;
    }

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public void setHomeDirectory(String homeDirectory) {
        this.homeDirectory = homeDirectory;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getMaxPasswordAge() {
        return maxPasswordAge;
    }

    public void setMaxPasswordAge(String maxPasswordAge) {
        this.maxPasswordAge = maxPasswordAge;
    }

    public String getMemberOf() {
        return memberOf;
    }

    public void setMemberOf(String memberOf) {
        this.memberOf = memberOf;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Identity getOnDiskGroupIdentity() {
        return onDiskGroupIdentity;
    }

    public void setOnDiskGroupIdentity(Identity onDiskGroupIdentity) {
        this.onDiskGroupIdentity = onDiskGroupIdentity;
    }

    public Identity getOnDiskUserIdentity() {
        return onDiskUserIdentity;
    }

    public void setOnDiskUserIdentity(Identity onDiskUserIdentity) {
        this.onDiskUserIdentity = onDiskUserIdentity;
    }

    public Boolean getPasswordExpired() {
        return passwordExpired;
    }

    public void setPasswordExpired(Boolean passwordExpired) {
        this.passwordExpired = passwordExpired;
    }

    public Boolean getPasswordExpires() {
        return passwordExpires;
    }

    public void setPasswordExpires(Boolean passwordExpires) {
        this.passwordExpires = passwordExpires;
    }

    public String getPasswordExpiry() {
        return passwordExpiry;
    }

    public void setPasswordExpiry(String passwordExpiry) {
        this.passwordExpiry = passwordExpiry;
    }

    public String getPasswordLastSet() {
        return passwordLastSet;
    }

    public void setPasswordLastSet(String passwordLastSet) {
        this.passwordLastSet = passwordLastSet;
    }

    public Identity getPrimaryGroupSid() {
        return primaryGroupSid;
    }

    public void setPrimaryGroupSid(Identity primaryGroupSid) {
        this.primaryGroupSid = primaryGroupSid;
    }

    public Boolean getPromptPasswordChange() {
        return promptPasswordChange;
    }

    public void setPromptPasswordChange(Boolean promptPasswordChange) {
        this.promptPasswordChange = promptPasswordChange;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSamAccountName() {
        return samAccountName;
    }

    public void setSamAccountName(String samAccountName) {
        this.samAccountName = samAccountName;
    }

    public String getShell() {
        return shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public Identity getSid() {
        return sid;
    }

    public void setSid(Identity sid) {
        this.sid = sid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Identity getUid() {
        return uid;
    }

    public void setUid(Identity uid) {
        this.uid = uid;
    }

    public String getUpn() {
        return upn;
    }

    public void setUpn(String upn) {
        this.upn = upn;
    }

    public Boolean getUserCanChangePassword() {
        return userCanChangePassword;
    }

    public void setUserCanChangePassword(Boolean userCanChangePassword) {
        this.userCanChangePassword = userCanChangePassword;
    }

    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, String> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }


    public IsilonUser(String dn, String dnsDomain, String domain, String email, Boolean enabled, Boolean expired, String expiry, String gecos,
            Boolean generatedGid, Boolean generatedUid, Boolean generatedUpn, Identity gid, String homeDirectory, String id,
            Boolean locked, String maxPasswordAge, String memberOf, String name, Identity onDiskGroupIdentity, Identity onDiskUserIdentity,
            Boolean passwordExpired, Boolean passwordExpires, String passwordExpiry, String passwordLastSet, Identity primaryGroupSid,
            Boolean promptPasswordChange, String provider, String samAccountName, String shell, Identity sid, String type, Identity uid,
            String upn, Boolean userCanChangePassword, Map<String, String> additionalProperties) {
        super();
        this.dn = dn;
        this.dnsDomain = dnsDomain;
        this.domain = domain;
        this.email = email;
        this.enabled = enabled;
        this.expired = expired;
        this.expiry = expiry;
        this.gecos = gecos;
        this.generatedGid = generatedGid;
        this.generatedUid = generatedUid;
        this.generatedUpn = generatedUpn;
        this.gid = gid;
        this.homeDirectory = homeDirectory;
        this.id = id;
        this.locked = locked;
        this.maxPasswordAge = maxPasswordAge;
        this.memberOf = memberOf;
        this.name = name;
        this.onDiskGroupIdentity = onDiskGroupIdentity;
        this.onDiskUserIdentity = onDiskUserIdentity;
        this.passwordExpired = passwordExpired;
        this.passwordExpires = passwordExpires;
        this.passwordExpiry = passwordExpiry;
        this.passwordLastSet = passwordLastSet;
        this.primaryGroupSid = primaryGroupSid;
        this.promptPasswordChange = promptPasswordChange;
        this.provider = provider;
        this.samAccountName = samAccountName;
        this.shell = shell;
        this.sid = sid;
        this.type = type;
        this.uid = uid;
        this.upn = upn;
        this.userCanChangePassword = userCanChangePassword;
        this.additionalProperties = additionalProperties;
    }



    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("user ( id: " + id);
        str.append(", domain: " + domain);
        str.append(", type: " + type);
        str.append(", name: " + name);
        str.append(", gid: " + gid);
        str.append(", sid: " + sid);
        str.append(")");
        return str.toString();
    }

    public class Identity {

        private String id;
        private String name;
        private String type;
        private Map<String, String> additionalProperties = new HashMap<String, String>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, String> getAdditionalProperties() {
            return this.additionalProperties;
        }

        public void setAdditionalProperty(String name, String value) {
            this.additionalProperties.put(name, value);
        }

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("( Identity id: " + id);
            str.append(", type : " + type);
            str.append(",  name: " + name);
            str.append(",  additional properties: " + additionalProperties);
            str.append(")");
            return str.toString();
        }
    }

}