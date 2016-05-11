/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

public class ConnectionInfo {
    // management interface IP address
    private String ipAddress;

    // management port number
    private Integer portNumber;

    // management interface user
    private String username;

    // management interface password
    private String password;

    // Whether or not SSL is used for connection
    private boolean useSSL;

    public ConnectionInfo(String ipAddress, Integer portNumber, String username, String password) {
        super();
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.username = username;
        this.password = password;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(Integer portNumber) {
        this.portNumber = portNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }  
}
