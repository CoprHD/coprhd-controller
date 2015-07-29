/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.plugins.metering.vnxfile;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VNXFileProtocol {

    public static Logger _logger = LoggerFactory
            .getLogger(VNXFileProtocol.class);

    /**
     * Protocol type. http or https
     */
    private static volatile String _protocolType;

    private static volatile ProtocolSocketFactory _socketFactory;

    private static int _port;

    private static String _portNumber;

    public VNXFileProtocol() {
        _logger.info("call method..");
    }

    public VNXFileProtocol(String protocolType, ProtocolSocketFactory protocolSocketFactory, Object portNumber) {
    }

    public static void setProtocolType(String protocolType) {
        _protocolType = protocolType;
    }

    public static String getProtocolType() {
        return _protocolType;
    }

    public static void setProtocolSocketFactory(ProtocolSocketFactory protocolSocketFactory) {
        _socketFactory = protocolSocketFactory;
    }

    public static ProtocolSocketFactory getProtocolSocketFactory() {
        return _socketFactory;
    }

    public static int getPortNumber() {
        return _port;
    }

    public static void setPortNumber(int portNumber) {
        _port = portNumber;
    }

    public Protocol getProtocol() {
        return new Protocol(getProtocolType(), getProtocolSocketFactory(), getPortNumber());
    }

    public Protocol getProtocol(int portNumber) {
        return new Protocol(getProtocolType(), getProtocolSocketFactory(), portNumber);
    }

}
