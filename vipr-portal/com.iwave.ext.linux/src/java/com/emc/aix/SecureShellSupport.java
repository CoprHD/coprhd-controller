/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix;

import java.net.URI;

import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandOutput;

public abstract class SecureShellSupport {

    /** The SSH host address. */
    private String host;
    /** The SSH port (defaults to 22). */
    private int port = 22;
    /** The SSH username. */
    private String username;
    /** The SSH password. */
    private String password;
    /** The ID of the host to which this CLI connects */
    private URI hostId;
    public static int NO_TIMEOUT = 0;
    public static int SHORT_TIMEOUT = 60;

    public SecureShellSupport() {
    }

    public SecureShellSupport(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public SecureShellSupport(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public CommandOutput executeCommand(String commandString) {
        Command command = new Command();
        command.setCommand(commandString);
        executeCommand(command);
        return command.getOutput();
    }

    public abstract void executeCommand(Command command);

    public abstract void executeCommand(Command command, int timeout);

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

    public URI getHostId() {
        return hostId;
    }

    public void setHostId(URI hostId) {
        this.hostId = hostId;
    }
}
