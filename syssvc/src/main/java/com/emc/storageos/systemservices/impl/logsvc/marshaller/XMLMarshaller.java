/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.IOException;
import java.io.OutputStream;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;
import com.emc.storageos.systemservices.impl.logsvc.util.LogUtil;

public class XMLMarshaller extends Marshaller {

    XMLMarshaller(OutputStream outputStream) {
        super(outputStream);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void head() throws IOException {
        outputStream.write("<log_list>\n".getBytes());
    }

    @Override
    public void tail() throws IOException {
        outputStream.write("</log_list>\n".getBytes());
    }

    @Override
    public void marshall(LogMessage logMessage) throws IOException {
        outputStream.write("<log_info>\n\t<class>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getFileName()));
        outputStream.write("</class>\n\t<line>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getLineNumber()));
        outputStream.write("</line>\n\t<node>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getNodeId()));
        outputStream.write("</node>\n\t<node_name>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getNodeName()));
        outputStream.write("</node_name>\n\t<message>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getLogContent()));
        outputStream.write("</message>\n\t<severity>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getLevel()));
        outputStream.write("</severity>\n\t<service>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getService()));
        outputStream.write("</service>\n\t<thread>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getThreadName()));
        outputStream.write("</thread>\n\t<time>".getBytes());
        outputStream.write(LogUtil.escapeXml(logMessage.getTimeBytes()));
        outputStream.write("</time>\n\t<time_ms>".getBytes());
        outputStream.write(String.valueOf(logMessage.getTime()).getBytes());
        outputStream.write("</time_ms>\n</log_info>\n".getBytes());
    }

    @Override
    public void marshall(String msg, LogMessage prevMsg) throws IOException {
        outputStream.write("<log_info>\n\t<message>".getBytes());
        outputStream.write(LogUtil.escapeXml(msg.getBytes()));
        outputStream.write("</message>\n\t<severity>ERROR</severity>\n\t<service>internal</service>\n\t<time>".getBytes());
        outputStream.write(prevMsg == null ? "--".getBytes() : LogUtil.escapeXml(prevMsg.getTimeBytes()));
        outputStream.write("</time>\n\t<time_ms>".getBytes());
        outputStream.write(prevMsg == null ? "--".getBytes() : String.valueOf(prevMsg.getTime()).getBytes());
        outputStream.write("</time_ms>\n</log_info>\n".getBytes());
    }
}
