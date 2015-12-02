/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.object.BucketACLUpdateParams.BucketACLOperationErrorType;

@XmlRootElement
public class BucketACE implements Serializable {

    private static final long serialVersionUID = -7903490453952137563L;

    /*
     * Payload attributes
     */
    private String user;
    private String group;
    private String customGroup;
    private String domain;
    private String permissions;
    private String bucketName;
    private String namespace;

    /*
     * Other attributes - not part of payload
     */
    private boolean proceedToNextStep;
    private BucketACLOperationErrorType errorType;

    public boolean canProceedToNextStep() {
        return proceedToNextStep;
    }

    public void proceedToNextStep() {
        this.proceedToNextStep = true;
    }

    public void cancelNextStep(BucketACLOperationErrorType errorType) {
        this.proceedToNextStep = false;
        this.errorType = errorType;
    }

    public BucketACLOperationErrorType getErrorType() {
        return errorType;
    }

    @XmlElement(name = "user")
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @XmlElement(name = "group")
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @XmlElement(name = "customgroup")
    public String getCustomGroup() {
        return customGroup;
    }

    public void setCustomGroup(String customGroup) {
        this.customGroup = customGroup;
    }

    @XmlElement(name = "domain")
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @XmlElement(name = "permissions")
    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    @XmlElement(name = "bucket")
    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    @XmlElement(name = "namespace")
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

}
