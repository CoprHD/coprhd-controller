package com.emc.sa.service.vipr.plugins.tasks;

import java.io.IOException;
import java.io.InputStream;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHCommandExecutor {

    Session session = null;

    public SSHCommandExecutor() {

    }

    public void executor(String host,String user, String password, String command) throws JSchException, IOException {

        SSHCommandExecutor executor = new SSHCommandExecutor();
        executor.connect(host,user,password);

        

        executor.executeCommand(command);

        executor.disconnect();
    }

    public void connect(String host,String user, String password) {

        try {
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            JSch jsch = new JSch();
            session = jsch.getSession(user,host, 22);
            session.setPassword(password);
            session.setConfig(config);
            session.connect();
            System.out.println("Connected");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void executeCommand(String script) throws JSchException, IOException {

        ChannelExec channel = (ChannelExec) session.openChannel("exec");

        ((ChannelExec) channel).setCommand(script);

        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(System.err);

        InputStream in = channel.getInputStream();
        channel.connect();
        byte[] tmp = new byte[1024];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0)
                    break;
                System.out.print(new String(tmp, 0, i));
            }
            if (channel.isClosed()) {
                System.out.println("exit-status: " + channel.getExitStatus());
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ee) {
            }
        }
        channel.disconnect();
        System.out.println("disconnect");
    }

    public void disconnect() {
        session.disconnect();
    }

}
