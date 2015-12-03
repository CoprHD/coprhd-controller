/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iwave.ext.command.Command;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.command.CommandExecutor;
import com.iwave.ext.command.CommandOutput;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHCommandExecutor implements CommandExecutor {
    static final Logger log = LoggerFactory.getLogger(SSHCommandExecutor.class);
    private static final String DEFAULT_SUDO_COMMAND = "sudo -S -p '' sh -c ";
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long SLEEP_TIME = 100;

    private String sudoCommand = DEFAULT_SUDO_COMMAND;
    private SSHConnection connection;
    private Session session;
    private int connectTimeout;
    private int readTimeout;
    private int commandTimeout;
    private boolean autoDisconnect = true;

    public SSHCommandExecutor(SSHConnection connection) {
        this.connection = connection;
    }

    public SSHCommandExecutor(String host, String username, String password) {
        this(host, 22, username, password);
    }

    public SSHCommandExecutor(String host, int port, String username, String password) {
        this.connection = new SSHConnection(host, port, username, password);
    }

    public void setSudoPrefix(String sudoPrefix) {
        this.sudoCommand = sudoPrefix;
    }

    public SSHConnection getConnection() {
        return connection;
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

    public int getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(int commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    public boolean isAutoDisconnect() {
        return autoDisconnect;
    }

    public void setAutoDisconnect(boolean autoDisconnect) {
        this.autoDisconnect = autoDisconnect;
    }

    protected JSch createClient() throws SSHException {
        return new JSch();
    }

    public boolean isConnected() {
        return (session != null) && session.isConnected();
    }

    public void connect() throws SSHException {
        try {
            session = createClient().getSession(connection.getUsername(), connection.getHost(), connection.getPort());
            session.setPassword(connection.getPassword());
            session.setUserInfo(new SSHUserInfo(connection.getPassword()));
            if (connectTimeout > 0) {
                session.connect(connectTimeout);
            }
            else {
                session.connect();
            }

            if (readTimeout > 0) {
                session.setTimeout(readTimeout);
            }
        } catch (JSchException e) {
            throw new SSHException(e);
        }
    }

    public void disconnect() {
        if (isConnected()) {
            session.disconnect();
            session = null;
        }
    }

    @Override
    public CommandOutput executeCommand(Command command) throws CommandException {
        try {
            if (!isConnected()) {
                connect();
            }
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            try {
                StreamConsumer stdout = new StreamConsumer(channel.getInputStream());
                StreamConsumer stderr = new StreamConsumer(channel.getErrStream());

                connect(command, channel);
                waitForDone(channel);

                stdout.close();
                stderr.close();
                int exitCode = channel.getExitStatus();
                return new CommandOutput(stdout.toString(), stderr.toString(), exitCode);
            } finally {
                channel.disconnect();
            }
        } catch (JSchException | IOException | InterruptedException | SSHException e) {
            log.error(String.format("SSH '%s' command failed: ", command.getCommand()), e);
            throw new CommandException(e);
        } finally {
            if (isAutoDisconnect()) {
                disconnect();
            }
        }
    }

    protected void connect(Command command, ChannelExec channel) throws JSchException, IOException {
        String commandLine = command.getCommandLine();
        boolean isRootUser = StringUtils.equals("root", connection.getUsername());

        InputStream stdin = null;
        if (command.isRunAsRoot() && !isRootUser) {
            commandLine = sudoCommand + "\"" + escapeCommandLine(commandLine) + "\"";
            stdin = new ByteArrayInputStream((connection.getPassword() + "\n").getBytes("US-ASCII"));
        }

        channel.setCommand(commandLine);
        channel.setInputStream(stdin);
        channel.connect();
    }

    protected String escapeCommandLine(String commandLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            switch (ch) {
                case '\\':
                case '"':
                case '$':
                    sb.append('\\');
                    break;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    protected void waitForDone(ChannelExec channel) throws InterruptedException {
        boolean hasTimeout = commandTimeout > 0;
        long remainingTime = commandTimeout * MILLIS_PER_SECOND;
        while (!channel.isClosed()) {
            if (hasTimeout && remainingTime <= 0) {
                throw new SSHTimeoutException("SSH received no response within " + commandTimeout + " seconds", commandTimeout);
            }
            Thread.sleep(SLEEP_TIME);
            remainingTime -= SLEEP_TIME;
        }
    }
}
