/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

public class SSHConnection {
    public static final int DEFAULT_PORT = 22;
    private String host;
    private int port = DEFAULT_PORT;
    private String username;
    private String password;

    public SSHConnection() {
    }

    public SSHConnection(String host, String username, String password) {
        this(host, DEFAULT_PORT, username, password);
    }

    public SSHConnection(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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
}
