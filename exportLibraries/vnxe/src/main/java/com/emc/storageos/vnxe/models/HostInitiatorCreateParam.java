/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class HostInitiatorCreateParam extends ParamBase {
    private String initiatorWWNorIqn;
    private VNXeBase host;
    private int initiatorType;
    private String chapUser;
    private String chapSecret;
    private Integer chapSecretType;
    private Boolean isIgnored;

    public String getInitiatorWWNorIqn() {
        return initiatorWWNorIqn;
    }

    public void setInitiatorWWNorIqn(String initiatorWWNorIqn) {
        this.initiatorWWNorIqn = initiatorWWNorIqn;
    }

    public VNXeBase getHost() {
        return host;
    }

    public void setHost(VNXeBase host) {
        this.host = host;
    }

    public int getInitiatorType() {
        return initiatorType;
    }

    public void setInitiatorType(int initiatorType) {
        this.initiatorType = initiatorType;
    }

    public String getChapUser() {
        return chapUser;
    }

    public void setChapUser(String chapUser) {
        this.chapUser = chapUser;
    }

    public String getChapSecret() {
        return chapSecret;
    }

    public void setChapSecret(String chapSecret) {
        this.chapSecret = chapSecret;
    }

    public Integer getChapSecretType() {
        return chapSecretType;
    }

    public void setChapSecretType(Integer chapSecretType) {
        this.chapSecretType = chapSecretType;
    }

    public Boolean getIsIgnored() {
        return isIgnored;
    }

    public void setIsIgnored(Boolean isIgnored) {
        this.isIgnored = isIgnored;
    }

}
