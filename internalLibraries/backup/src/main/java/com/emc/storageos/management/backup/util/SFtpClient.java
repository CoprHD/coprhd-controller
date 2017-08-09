package com.emc.storageos.management.backup.util;

import com.jcraft.jsch.*;
import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

public class SFtpClient implements BackupClient {


    private static final Logger log = LoggerFactory.getLogger(SFtpClient.class);

    private static final String PROTOCOL_TYPE_SFTP = "sftp";

    private static final String ERROR_MSG_CREATE_CHANNEL_FAIL = "Create Channel failed.";

    /**
     * host, something like 10.75.81.21
     **/
    private final String host;
    private final String username;
    private final String password;

    private Session session;
    private ChannelSftp channel;


    public SFtpClient(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }


    private Session createSession() throws JSchException {
        JSch jsch = new JSch();
        this.session = jsch.getSession(this.username, this.host);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        this.session.setPassword(this.password);
        this.session.connect();
        return this.session;
    }

    public ChannelSftp openSftpChannel() throws JSchException {
        if (this.channel == null || this.channel.isClosed() || !this.channel.isConnected()) {
            if (this.session == null || !this.session.isConnected()) {
                this.createSession();
            }
            this.channel = (ChannelSftp) this.session.openChannel(PROTOCOL_TYPE_SFTP);
            this.channel.connect();
        }
        return this.channel;
    }

    public void closeSession() {
        if (this.channel != null) {
            this.channel.disconnect();
            this.channel = null;
        }
        if (this.session != null) {
            this.session.disconnect();
            this.session = null;
        }
    }

    public boolean doesExist(ChannelSftp channel, String remotePath) throws Exception {
        InputStream is;
        try {
            is = channel.get(remotePath);
            if (is != null)
                return is.available() > 0;
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public OutputStream upload(String fileName, long offset) throws Exception {

        if (fileName == null || fileName.startsWith(File.separator)) {
            log.error("Invalid argument fileName. we only support relative path.");
            throw new IllegalArgumentException("fileName");
        }
        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        int index = fileName.lastIndexOf(File.separator);
        if (index > -1) {
            String directory = fileName.substring(0, index);
            SftpATTRS dirstat = channel.stat(directory);
            if (dirstat == null) {
                throw new IOException("Can't get stat of  directory:" + directory);
            } else {
                if (!dirstat.isDir()) {
                    throw new IOException(directory + " is not a directory");
                }
            }
        }

        if(offset>0){
            return new SkipOutputStream(channel.put(fileName,ChannelSftp.APPEND),offset);
        }else{
            return channel.put(fileName,ChannelSftp.OVERWRITE);
        }
    }

    @Override
    public List<String> listFiles(String prefix) throws Exception {

        if (prefix == null) {
            log.error("Invalid argument prefix");
            throw new IllegalArgumentException("prefix");
        }
        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        List<String> childNames = new ArrayList();
        List v = channel.ls(prefix + ".*");
        ChannelSftp.LsEntry entry;
        for (int i = 0; i < v.size(); i++) {
            entry = (ChannelSftp.LsEntry) v.get(i);
            childNames.add(entry.getFilename());
        }
        return childNames;
    }

    @Override
    public List<String> listAllFiles() throws Exception {

        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        List<String> childNames = new ArrayList();
        List v = channel.ls("*");
        ChannelSftp.LsEntry entry;
        for (int i = 0; i < v.size(); i++) {
            entry = (ChannelSftp.LsEntry) v.get(i);
            childNames.add(entry.getFilename());
        }
        return childNames;
    }

    @Override
    public InputStream download(String backupFileName) throws Exception {

        if (backupFileName == null) {
            log.error("Invalid argument backupFileName");
            throw new IllegalArgumentException("backupFileName");
        }
        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        if (!doesExist(channel, backupFileName)) {
            log.error("{} does not exist.",backupFileName);
            throw new IOException(backupFileName + " does not exist.");
        }
        return channel.get(backupFileName);
    }

    @Override
    public void rename(String sourceFileName, String destFileName) throws Exception {
        if (sourceFileName == null || destFileName == null) {
            log.error("Invalid argument.");
            throw new IllegalArgumentException("Invalid arguments.");
        }

        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        if (!doesExist(channel, sourceFileName)) {
            log.error("{} does not exist.",sourceFileName);
            throw new IOException(sourceFileName + " does not exist.");
        }

        this.channel.rename(sourceFileName, destFileName);
    }

    @Override
    public long getFileSize(String fileName) throws Exception {

        if (fileName == null) {
            log.error("Invalid argument fileName");
            throw new IllegalArgumentException("Invalid argument fileName");
        }

        if (this.openSftpChannel() == null) {
            log.error(ERROR_MSG_CREATE_CHANNEL_FAIL);
            throw new IOException(ERROR_MSG_CREATE_CHANNEL_FAIL);
        }

        SftpATTRS attrs = channel.stat(fileName);
        if (attrs == null) {
            log.error("Cannot get file attributes for file {}" , fileName);
            throw new IOException("Cannot get file attributes for file " + fileName);
        }
        return attrs.getSize();
    }

    @Override
    public String getUri() {
        return this.host;
    }

    @Override
    public void validate() throws AuthenticationException, ConnectException {

        try {
            this.createSession();
        } catch (JSchException e) {
            log.error("username or host are invalid.");
            throw new AuthenticationException("username or host are invalid.");
        } finally {
            this.closeSession();
        }
    }

}
