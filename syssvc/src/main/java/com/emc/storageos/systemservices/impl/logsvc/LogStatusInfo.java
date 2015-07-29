/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogStatusInfo {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogStatusInfo.class);

    private List<String> status = new LinkedList<>();
    private static StringBuilder sb = new StringBuilder();

    public LogStatusInfo() {
    }

    public LogStatusInfo(List<String> list) {
        this.status = list;
    }

    public void appendInfo(String fileName, int lineNo) {
        sb.setLength(0); // clear StringBuilder
        sb.append("Failed to parse line ").append(lineNo).append(" of ").append(fileName);
        status.add(sb.toString());
    }

    public void append(LogStatusInfo status) {
        if (status == null || isEmpty()) {
            return;
        }
        this.status.addAll(status.getStatus());
    }

    public void appendErrFileName(String fileName) {
        sb.setLength(0); // clear StringBuffer
        sb.append(fileName).append(" can not be found");
        status.add(sb.toString());
    }

    public void append(String line) {
        this.status.add(line);
    }

    public List<String> getStatus() {
        return status;
    }

    public void write(DataOutputStream dos) throws IOException {
        logger.trace("write()");
        if (status.isEmpty()) {
            logger.info("status is empty");
            return;
        }
        for (String s : getStatus()) {
            int length = s.getBytes().length;
            dos.write(LogACKCode.ACK_STATUS);
            dos.writeInt(length);
            dos.write(s.getBytes());
        }
        status.clear();
    }

    public void readAndAppend(DataInputStream dis) throws IOException {
        logger.trace("read()");
        int lineLength = dis.readInt();
        byte[] line = new byte[lineLength];
        dis.readFully(line);
        append(new String(line));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (status.size() == 1) {
            sb.append(status.get(0));
        } else {
            for (String s : status) {
                sb.append(s);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public void clear() {
        status.clear();
    }

    public boolean isEmpty() {
        return status.isEmpty();
    }
}
