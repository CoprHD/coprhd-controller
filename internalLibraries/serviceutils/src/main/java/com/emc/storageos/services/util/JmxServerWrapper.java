/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

public class JmxServerWrapper {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JmxServerWrapper.class);
    private int _jmxRemotePort;
    private int _jmxRemoteExportPort;
    private String _jmxHost;
    private String _jmxFmtUrl;
    private boolean _jmxEnabled;
    private JmxServerWrapper _jmxServer;
    private JMXConnectorServer _jmxRemoteServer;
    private JMXConnector jmxc;

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

    public void start() throws Exception {
        log.debug("JMX server wrapper: jmx enabled ={} ", _jmxEnabled);
        if (_jmxEnabled) {

            try {

                LocateRegistry.createRegistry(_jmxRemotePort);
                log.info("start bind JMX to {}:{}  serviceurl: {}", new Object[] { _jmxHost, _jmxRemotePort, _jmxFmtUrl});

                JMXServiceURL jmxUrl = new JMXServiceURL(String.format(_jmxFmtUrl, _jmxRemoteExportPort, _jmxHost, _jmxRemotePort));
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                _jmxRemoteServer = JMXConnectorServerFactory.newJMXConnectorServer(jmxUrl, null, mbs);
                _jmxRemoteServer.start();
                jmxc = _jmxRemoteServer.toJMXConnector(null);
            } catch (Exception e) {
                log.error("JMX server startup failed", e);
                throw e;
            }
        }
    }

    public void stop() {
        if (_jmxEnabled) {
            try {
                _jmxRemoteServer.stop();
            } catch (IOException e) {
                log.error("Exception happens when stop JMX server", e);
            }
        }
    }

    public void addConnectionNotificiationListener(NotificationListener listener,
                                                   NotificationFilter filter, Object handback) throws IOException {
        jmxc.addConnectionNotificationListener(listener, filter, handback);
    }

    public void removeConnectionNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException, IOException {
        jmxc.removeConnectionNotificationListener(listener);
    }
}
