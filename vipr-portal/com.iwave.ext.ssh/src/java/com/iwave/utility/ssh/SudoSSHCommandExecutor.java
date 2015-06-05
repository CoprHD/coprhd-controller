/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.IOException;
import java.io.OutputStream;

import com.iwave.ext.command.Command;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;

public class SudoSSHCommandExecutor extends SSHCommandExecutor {

    public SudoSSHCommandExecutor(SSHConnection connection) {
        super(connection);
    }

    public SudoSSHCommandExecutor(String host, String username, String password) {
        super(host, username, password);
    }

    public SudoSSHCommandExecutor(String host, int port, String username, String password) {
        super(host, port, username, password);
    }

    @Override
    protected void connect(Command command, ChannelExec channel) throws JSchException, IOException {
        String commandLine = "sudo -S -p '' " + command.getCommandLine();
        OutputStream stdout = channel.getOutputStream();
        channel.setCommand(commandLine);
        channel.connect();

        String input = getConnection().getPassword() + "\n";
        stdout.write(input.getBytes("US-ASCII"));
        stdout.flush();
    }
}
