/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHSession {
    JSch jsch;
    Session session;
    Channel channel;
    InputStream ins;
    OutputStream outs;

    private static final Logger _log = LoggerFactory.getLogger(SSHSession.class);
    static final Integer timeout = 15000;           // in milliseconds
    static final Integer connectTimeout = 10000;    // in milliseconds

    public SSHSession() {
    }

    public void connect(String hostname, Integer port, String username, String password)
            throws Exception {
        jsch = new JSch();
        session = jsch.getSession(username, hostname, port);
        session.setPassword(password);
        Hashtable<String, String> config = new Hashtable<String, String>();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(timeout);

        channel = session.openChannel("shell");
        ((ChannelShell) channel).setPtyType("vt102");
        // channel.setInputStream(System.in);
        // channel.setOutputStream(System.out);
        ins = channel.getInputStream();
        outs = channel.getOutputStream();
        channel.connect(connectTimeout);
    }

    public void setTimeout(int timeout) {
        try {
            session.setTimeout(timeout);
        } catch (JSchException ex) {
            _log.error("Couldn't set timeout: " + ex.getLocalizedMessage());
        }
    }

    public boolean isConnected() {
        return channel.isConnected();
    }

    public void disconnect() {
        channel.disconnect();
        session.disconnect();
    }

    public JSch getJsch() {
        return jsch;
    }

    public Session getSession() {
        return session;
    }

    public Channel getChannel() {
        return channel;
    }

    public InputStream getIns() {
        return ins;
    }

    public OutputStream getOuts() {
        return outs;
    }

}
