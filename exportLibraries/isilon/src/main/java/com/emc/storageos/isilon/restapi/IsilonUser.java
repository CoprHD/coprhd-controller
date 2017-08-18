/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;


/**
 * Class representing the isilon user object
 * member names should match the key names in json object
 *
 * @author sauraa
 *
 */
public class IsilonUser {

    private String dn;
    private String dns_domain;
    private String domain;
    private Boolean generated_gid;
    private Boolean generated_uid;
    private Boolean generated_upn;
    private IsilonIdentity gid;
    private String home_directory;
    private String id;
    private Boolean locked;
    private String max_password_age;
    private String member_of;
    private String name;
    private IsilonIdentity on_disk_group_identity;
    private IsilonIdentity on_disk_user_identity;
    private Boolean password_expired;
    private Boolean password_expires;
    private String password_expiry;
    private String password_last_set;
    private IsilonIdentity primary_group_sid;
    private Boolean prompt_password_change;
    private String provider;
    private String sam_account_name;
    private String shell;
    private IsilonIdentity sid;
    private String type;
    private IsilonIdentity uid;
    private String upn;
    private Boolean user_can_change_password;


    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getDns_domain() {
        return dns_domain;
    }

    public void setDns_domain(String dns_domain) {
        this.dns_domain = dns_domain;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Boolean getGenerated_gid() {
        return generated_gid;
    }

    public void setGenerated_gid(Boolean generated_gid) {
        this.generated_gid = generated_gid;
    }

    public Boolean getGenerated_uid() {
        return generated_uid;
    }

    public void setGenerated_uid(Boolean generated_uid) {
        this.generated_uid = generated_uid;
    }

    public Boolean getGenerated_upn() {
        return generated_upn;
    }

    public void setGenerated_upn(Boolean generated_upn) {
        this.generated_upn = generated_upn;
    }

    public IsilonIdentity getGid() {
        return gid;
    }

    public void setGid(IsilonIdentity gid) {
        this.gid = gid;
    }

    public String getHome_directory() {
        return home_directory;
    }

    public void setHome_directory(String home_directory) {
        this.home_directory = home_directory;
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

    public String getMax_password_age() {
        return max_password_age;
    }

    public void setMax_password_age(String max_password_age) {
        this.max_password_age = max_password_age;
    }

    public String getMember_of() {
        return member_of;
    }

    public void setMember_of(String member_of) {
        this.member_of = member_of;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IsilonIdentity getOn_disk_group_identity() {
        return on_disk_group_identity;
    }

    public void setOn_disk_group_identity(IsilonIdentity on_disk_group_identity) {
        this.on_disk_group_identity = on_disk_group_identity;
    }

    public IsilonIdentity getOn_disk_user_identity() {
        return on_disk_user_identity;
    }

    public void setOn_disk_user_identity(IsilonIdentity on_disk_user_identity) {
        this.on_disk_user_identity = on_disk_user_identity;
    }

    public Boolean getPassword_expired() {
        return password_expired;
    }

    public void setPassword_expired(Boolean password_expired) {
        this.password_expired = password_expired;
    }

    public Boolean getPassword_expires() {
        return password_expires;
    }

    public void setPassword_expires(Boolean password_expires) {
        this.password_expires = password_expires;
    }

    public String getPassword_expiry() {
        return password_expiry;
    }

    public void setPassword_expiry(String password_expiry) {
        this.password_expiry = password_expiry;
    }

    public String getPassword_last_set() {
        return password_last_set;
    }

    public void setPassword_last_set(String password_last_set) {
        this.password_last_set = password_last_set;
    }

    public IsilonIdentity getPrimary_group_sid() {
        return primary_group_sid;
    }

    public void setPrimary_group_sid(IsilonIdentity primary_group_sid) {
        this.primary_group_sid = primary_group_sid;
    }

    public Boolean getPrompt_password_change() {
        return prompt_password_change;
    }

    public void setPrompt_password_change(Boolean prompt_password_change) {
        this.prompt_password_change = prompt_password_change;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSam_account_name() {
        return sam_account_name;
    }

    public void setSam_account_name(String sam_account_name) {
        this.sam_account_name = sam_account_name;
    }

    public String getShell() {
        return shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public IsilonIdentity getSid() {
        return sid;
    }

    public void setSid(IsilonIdentity sid) {
        this.sid = sid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public IsilonIdentity getUid() {
        return uid;
    }

    public void setUid(IsilonIdentity uid) {
        this.uid = uid;
    }

    public String getUpn() {
        return upn;
    }

    public void setUpn(String upn) {
        this.upn = upn;
    }

    public Boolean getUser_can_change_password() {
        return user_can_change_password;
    }

    public void setUser_can_change_password(Boolean user_can_change_password) {
        this.user_can_change_password = user_can_change_password;
    }

    @Override
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



}
