/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cinder;

import java.io.Serializable;

/**
 * Bean for Cinder Endpoint Information. Use this bean for
 * keeping cinder details required to access the cinder service.
 * 
 */
public class CinderEndPointInfo implements Serializable {

    private static final long serialVersionUID = -1311609353266088041L;
    private String cinderHostName = null;
    private String cinderRESTuserName = null;
    private String cinderRESTPassword = null;
    private String cinderRESTPort = null;
    private String cinderToken = null;
    private String cinderTenantId = null;
    private String cinderBaseUriHttp = null;
    private String cinderBaseUriHttps = null;
    private String cinderTenantName = null;
    private String baseUri = null;

    public CinderEndPointInfo(String hostName, String userName,
            String password, String tenantName) {

        this.cinderHostName = hostName;
        this.cinderRESTuserName = userName;
        this.cinderRESTPassword = password;
        this.cinderTenantName = tenantName;
        this.cinderRESTPort = CinderConstants.CINDER_REST_PORT;
    }

    public String getCinderTenantName() {
        return cinderTenantName;
    }

    public void setCinderTenantName(String cinderTenantName) {
        this.cinderTenantName = cinderTenantName;
    }

    public String getCinderHostName() {
        return cinderHostName;
    }

    public void setCinderHostName(String cinderHostName) {
        this.cinderHostName = cinderHostName;
    }

    public String getCinderRESTuserName() {
        return cinderRESTuserName;
    }

    public void setCinderRESTuserName(String cinderRESTuserName) {
        this.cinderRESTuserName = cinderRESTuserName;
    }

    public String getCinderRESTPassword() {
        return cinderRESTPassword;
    }

    public void setCinderRESTPassword(String cinderRESTPassword) {
        this.cinderRESTPassword = cinderRESTPassword;
    }

    public String getCinderRESTPort() {
        return cinderRESTPort;
    }

    public void setCinderRESTPort(String cinderRESTPort) {
        this.cinderRESTPort = cinderRESTPort;
    }

    public String getCinderToken() {
        return cinderToken;
    }

    public void setCinderToken(String cinderToken) {
        this.cinderToken = cinderToken;
    }

    public String getCinderTenantId() {
        return cinderTenantId;
    }

    public void setCinderTenantId(String cinderTenantId) {
        this.cinderTenantId = cinderTenantId;
    }

    private String getCinderBaseUriHttp() {
        return cinderBaseUriHttp;
    }

    public void setCinderBaseUriHttp(String cinderBaseUriHttp) {
        this.cinderBaseUriHttp = cinderBaseUriHttp;
    }

    private String getCinderBaseUriHttps() {
        return cinderBaseUriHttps;
    }

    public void setCinderBaseUriHttps(String cinderBaseUriHttps) {
        this.cinderBaseUriHttps = cinderBaseUriHttps;
    }

    public String getBaseUri() {

        if (null == baseUri)
        {
            String endPointBaseUri = getCinderBaseUriHttp();
            if (null == endPointBaseUri) {
                endPointBaseUri = getCinderBaseUriHttps();
            }
            baseUri = endPointBaseUri.replace("5000/v2.0", CinderConstants.CINDER_REST_PORT);
        }

        return baseUri;
    }

}
