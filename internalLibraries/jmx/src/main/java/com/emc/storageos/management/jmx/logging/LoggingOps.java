/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.jmx.logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Scanner;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AgentInitializationException;

public class LoggingOps {
    private static final Logger log = LoggerFactory.getLogger(LoggingOps.class);
    private static final String CONNECTOR_ADDRESS =
            "com.sun.management.jmxremote.localConnectorAddress";
    private static final String PID_PATTERN = "/var/run/storageos/%s.pid";

    public static void setLevel(String logName, String level, int expirInMin, String scope)
            throws IllegalStateException {
        JMXConnector conn = initJMXConnector(logName);

        try {
            Object[] params = { level, expirInMin, scope };
            String[] sigs = { "java.lang.String", "int", "java.lang.String" };
            initMBeanServerConnection(conn).invoke(initObjectName(), LoggingMBean.OPERATION_SET,
                    params, sigs);
        } catch (IOException e) {
            throw new IllegalStateException("IOException", e);
        } catch (MBeanException e) {
            throw new IllegalStateException("MBeanException", e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalStateException("InstanceNotFoundException", e);
        } catch (ReflectionException e) {
            throw new IllegalStateException("ReflectionException", e);
        } finally {
            close(conn);
        }
    }

    public static String getLevel(String logName) throws IllegalStateException {
        JMXConnector conn = initJMXConnector(logName);

        try {
            return (String) initMBeanServerConnection(conn).getAttribute(initObjectName(),
                    LoggingMBean.ATTRIBUTE_NAME);
        } catch (IOException e) {
            throw new IllegalStateException("IOException", e);
        } catch (MBeanException e) {
            throw new IllegalStateException("MBeanException", e);
        } catch (AttributeNotFoundException e) {
            throw new IllegalStateException("AttributeNotFoundException", e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalStateException("InstanceNotFoundException", e);
        } catch (ReflectionException e) {
            throw new IllegalStateException("ReflectionException", e);
        } finally {
            close(conn);
        }
    }

    private static ObjectName initObjectName() {
        try {
            return new ObjectName(LoggingMBean.MBEAN_NAME);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Invalid object name", e);
        }
    }

    private static JMXConnector initJMXConnector(String logName) throws IllegalStateException {
        VirtualMachine vm = null;
        Scanner scanner = null;
        String logPidFileName = String.format(PID_PATTERN, logName);
        try {
            scanner = new Scanner(new File(logPidFileName));
            int pid = scanner.nextInt();
            log.debug("Got pid {} from pid file {}", pid, logPidFileName);

            vm = VirtualMachine.attach(String.valueOf(pid));
            String connectorAddress =
                    vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
            if (connectorAddress == null) {
                String agent = vm.getSystemProperties().getProperty("java.home") +
                        File.separator + "lib" + File.separator +
                        "management-agent.jar";
                vm.loadAgent(agent);

                connectorAddress =
                        vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
            }

            JMXServiceURL serviceURL = new JMXServiceURL(connectorAddress);
            return JMXConnectorFactory.connect(serviceURL);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Cannot find file " + logPidFileName, e);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("MalformedURLException:", e);
        } catch (IOException e) {
            throw new IllegalStateException("IOException when getting the MBean server"
                    + "connection:", e);
        } catch (AttachNotSupportedException e) {
            throw new IllegalStateException("Process cannot be attached:", e);
        } catch (AgentLoadException e) {
            throw new IllegalStateException("Failed to load agent:", e);
        } catch (AgentInitializationException e) {
            throw new IllegalStateException("Failed to initialize agent:", e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }

            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException e) {
                    throw new IllegalStateException("IOException when detaching vm:", e);
                }
            }
        }
    }

    private static MBeanServerConnection initMBeanServerConnection(JMXConnector conn) {
        if (conn == null) {
            throw new IllegalStateException("null JMXConnector");
        }

        try {
            MBeanServerConnection mbsc = conn.getMBeanServerConnection();
            if (mbsc == null) {
                throw new IllegalStateException("null MBeanServerConnection");
            }
            return mbsc;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get MBeanServerConnection:", e);
        }
    }

    private static void close(JMXConnector conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (IOException e) {
                log.error("IOException when closing JMX connector:", e);
            }
        }
    }

}
