/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.exceptions.SSHException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHConnection implements Connection {

    // Logger
    private static final Logger _log = LoggerFactory.getLogger(SSHConnection.class);

    // The host for the SSH connection.
    private String hostname = "";

    // The port for the SSH connection.
    private int port;

    // The user for the SSH connection.
    private String username = "";

    // The password for the SSH connection.
    private String password = "";

    // The default port for SSH
    private static final int DEFAULT_PORT = 22;

    // The connection timeout for the SSH connection
    private int connectTimeout;

    // The read timeout for the SSH connection
    private int readTimeout;

    // The session for the SSH connection
    private Session clientSession;

    public SSHConnection() {
        super();
    }

    public SSHConnection(String hostname, String username, String password) {
        this(hostname, DEFAULT_PORT, username, password);
    }

    public SSHConnection(String hostname, int port, String username, String password) {
        super();
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public SSHConnection(ConnectionInfo connectionInfo) {
        this.hostname = connectionInfo.get_hostname();
        this.port = connectionInfo.get_port();
        this.username = connectionInfo.get_username();
        this.password = connectionInfo.get_password();
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Session getClientSession() {
        return clientSession;
    }

    public void setClientSession(Session clientSession) {
        this.clientSession = clientSession;
    }

    public boolean isConnected() {
        return (clientSession != null) && clientSession.isConnected();
    }

    @Override
    public void connect() {
        _log.info("Connecting to the SSH host", getHostname());
        try {
            clientSession = new JSch().getSession(getUsername(), getHostname(), getPort());
            clientSession.setPassword(getPassword());
            clientSession.setUserInfo(new SSHUserInfo(getPassword()));
            if (connectTimeout > 0) {
                clientSession.connect(connectTimeout);
            } else {
                clientSession.connect();
            }

            if (readTimeout > 0) {
                clientSession.setTimeout(readTimeout);
            }
            setClientSession(clientSession);

        } catch (JSchException e) {
            throw new SSHException(e);
        }
        _log.info("Connected to the SSH host...: ", getHostname());
        System.out.println("Connected to the SSH host..." + getHostname());
    }

    @Override
    public void disconnect() {
        _log.info("Disconnecting the SSH connection to the host...: ", getHostname());
        if (isConnected()) {
            clientSession.disconnect();
            clientSession = null;
        }
        _log.info("Disconnected the SSH connection to the host...: ", getHostname());
        System.out.println("Disconnected the SSH connection to the host..." + getHostname());
    }

}
