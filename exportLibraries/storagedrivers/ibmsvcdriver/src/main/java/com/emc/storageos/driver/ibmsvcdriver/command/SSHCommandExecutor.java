package com.emc.storageos.driver.ibmsvcdriver.command;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;
import com.emc.storageos.driver.ibmsvcdriver.connection.StreamConsumer;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.CommandException;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.SSHException;
import com.emc.storageos.driver.ibmsvcdriver.exceptions.SSHTimeoutException;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHCommandExecutor implements CommandExecutor {

    private static final Logger _log = LoggerFactory.getLogger(SSHCommandExecutor.class);

    private static final String SUDO_COMMAND = "sudo -S -p '' sh -c ";

    private static final long MILLIS_PER_SECOND = 1000;

    private static final long SLEEP_TIME = 100;

    private SSHConnection connection;

    private int commandTimeout;

    private boolean autoDisconnect = true;

    private Session clientSession = null;

    public SSHCommandExecutor() {
        super();
    }

    public SSHCommandExecutor(SSHConnection connection) {
        this.connection = connection;
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

    @Override
    public CommandOutput executeCommand(Command command) throws CommandException {

        clientSession = connection.getClientSession();
        
        _log.info("Executing the command on the host {}", connection.getHostname());
        CommandOutput cmdOutput = null;
        
        if (command != null) {

            try {
                if (!clientSession.isConnected()) {
                    clientSession.connect();
                }
                ChannelExec channel = (ChannelExec) clientSession.openChannel("exec");
                try {
                    StreamConsumer stdout = new StreamConsumer(channel.getInputStream());
                    StreamConsumer stderr = new StreamConsumer(channel.getErrStream());

                    connect(command, channel);
                    waitForDone(channel);

                    int exitCode = channel.getExitStatus();

                    cmdOutput = new CommandOutput(stdout.toString(), stderr.toString(), exitCode);

                    stdout.close();
                    stderr.close();
                    
                } finally {
                    channel.disconnect();
                }
            } catch (JSchException | IOException | InterruptedException | SSHException e) {
                _log.error(String.format("SSH '%s' command failed: ", command.getCommand()), e);
                throw new CommandException(e);
            } finally {
                if (isAutoDisconnect()) {
                    // session.disconnect();
                }
            }
        }
        _log.info("Executed the command on the host {}", connection.getHostname());
        
        return cmdOutput;
    }

    protected void connect(Command command, ChannelExec channel) throws JSchException, IOException {
        String commandLine = command.getCommandLine();

        _log.info("Command to be executed - {}", commandLine);
        boolean isRootUser = StringUtils.equals("root", connection.getUsername());

        InputStream stdin = null;
        if (command.isRunAsRoot() && !isRootUser) {
            commandLine = SUDO_COMMAND + "\"" + escapeCommandLine(commandLine) + "\"";
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
                _log.error("SSH received no response within " + commandTimeout + " seconds", commandTimeout);
                throw new SSHTimeoutException("SSH received no response within " + commandTimeout + " seconds", commandTimeout);
            }
            Thread.sleep(SLEEP_TIME);
            remainingTime -= SLEEP_TIME;
        }
    }
}
