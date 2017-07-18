/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

public class AuthenticationInfo {

    private String protocol = "https";
    private String host;
    private Integer port;
    private String userName;
    private String password;
    private String sn;

    /**
     * @param host
     * @param port
     * @param userName
     * @param password
     */
    public AuthenticationInfo(String protocol, String host, Integer port, String userName, String password) {
        super();
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the port
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * @return the userName
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the sn
     */
    public String getSn() {
        return sn;
    }

    /**
     * @param sn the sn to set
     */
    public void setSn(String sn) {
        this.sn = sn;
    }

    /**
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @param protocol the protocol to set
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "AuthenticationInfo [protocol=" + protocol + ", host=" + host + ", port=" + port + ", userName=" + userName + ", password="
                + password + ", sn=" + sn + "]";
    }

}
