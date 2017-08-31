/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;


/**
 * Class representing the isilon group object
 * member names should match the key names in json object
 *
 * @author sauraa
 *
 */
public class IsilonGroup {

    private String dn;
    private String dns_domain;
    private String domain;
    private Boolean generated_gid;
    private IsilonIdentity gid;
    private String id;
    private String member_of;
    private String name;
    private String provider;
    private String sam_account_name;
    private IsilonIdentity sid;
    private String type;

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



    public IsilonIdentity getGid() {
        return gid;
    }

    public void setGid(IsilonIdentity gid) {
        this.gid = gid;
    }



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
