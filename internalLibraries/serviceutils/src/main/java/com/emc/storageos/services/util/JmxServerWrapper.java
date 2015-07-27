/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import org.apache.log4j.Logger;
import java.io.IOException;

import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;


public class JmxServerWrapper {
    private static Logger _log = Logger.getLogger(JmxServerWrapper.class);
    private int _jmxRemotePort;
    private int _jmxRemoteExportPort;
    private String _jmxHost;
    private String _jmxFmtUrl;
    private boolean _jmxEnabled;
    private JmxServerWrapper _jmxServer;
    private JMXConnectorServer _jmxRemoteServer;


    public JmxServerWrapper() {
    }

    /**
     * JMX enabled or not
     */
    public void setEnabled(boolean jmxEnabled) {
        _jmxEnabled = jmxEnabled;
    }

    public boolean getEnabled() {
        return _jmxEnabled;
    }

    /**
     * JMX service url format
     */
    public void setServiceUrl(String serviceUrl) {
        _jmxFmtUrl = serviceUrl;
    }

    public String getServiceUrl() {
        return _jmxFmtUrl;
    }

    /**
     * JMX remote port
     */
    public void setPort(int jmxRemotePort) {
        _jmxRemotePort = jmxRemotePort;
    }

    public int getPort() {
        return _jmxRemotePort;
    }

    /**
     * JMX Export Port for Remote Objects (RMIServer and RMIConnection etc.)
     */
    public void setExportPort(int jmxRemoteExportPort) {
        _jmxRemoteExportPort = jmxRemoteExportPort;
    }

    public int getExportPort() {
        return _jmxRemoteExportPort;
    }

    /**
     * JMX remote host
     */
    public void setHost(String jmxHost) {
        _jmxHost = jmxHost;
    }

    public String getHost() {
        return _jmxHost;
    }

    public void start()  throws Exception {
        _log.debug("JMX server wrapper: jmx enabled = " + _jmxEnabled);
        if (_jmxEnabled) {

            try {

                LocateRegistry.createRegistry(_jmxRemotePort);
                _log.info("start bind JMX to " + _jmxHost + ":" + _jmxRemotePort + " serviceurl: " + _jmxFmtUrl);

                JMXServiceURL jmxUrl = new JMXServiceURL(String.format(_jmxFmtUrl, _jmxRemoteExportPort, _jmxHost, _jmxRemotePort));
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                _jmxRemoteServer=JMXConnectorServerFactory.newJMXConnectorServer(jmxUrl, null, mbs);
                _jmxRemoteServer.start();
            } catch (Exception e) {
                _log.error("JMX server startup failed", e);
                throw e;
            }
        }
    }

    public void stop() {
        if (_jmxEnabled) {
            try {
                _jmxRemoteServer.stop();
            } catch (IOException e) {
                _log.error("Exception happens when stop JMX server", e);
            }
        }
    }
}
