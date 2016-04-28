/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.jmx.recovery;

import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.emc.storageos.services.util.PlatformUtils;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;

public class DbManagerOps implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DbManagerOps.class);
    private static final Integer DB_REPAIR_MAX_RETRY_COUNT = 3;
    private static final String JMX_URL_PATTERN = "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi";
    private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";
    public final static String MBEAN_NAME = "com.emc.storageos.db.server.impl:name=DbManager";

    private JMXConnector conn;
    private DbManagerMBean mbean;

    /**
     * Create an DbManagerOps object that connects to specified service on localhost.
     * 
     * @param svcName The name of the service, which should have pid file as /var/run/svcName.pid
     */
    public DbManagerOps(String svcName) {
        try {
            this.conn = initJMXConnector(svcName);
            initMbean(this.conn.getMBeanServerConnection());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create an DbManagerOps object using given MBeanServerConnection. The connection is built outside
     * of this object's control.
     * 
     * @param mbsc The MBeanServerConnection caller has made.
     * @throws IOException
     * @throws MalformedObjectNameException
     */
    public DbManagerOps(MBeanServerConnection mbsc) throws IOException, MalformedObjectNameException {
        initMbean(mbsc);
    }

    private void initMbean(MBeanServerConnection mbsc) throws IOException, MalformedObjectNameException {
        this.mbean = JMX.newMBeanProxy(mbsc, new ObjectName(MBEAN_NAME), DbManagerMBean.class);
    }

    private JMXConnector initJMXConnector(String svcName) throws IOException, AttachNotSupportedException, AgentLoadException,
            AgentInitializationException {
        int pid = PlatformUtils.getServicePid(svcName);
        log.info("Connecting to JMX of {} service with pid {}", svcName, pid);

        VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
        try {
            String connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
            if (connectorAddress == null) {
                String javaHome = vm.getSystemProperties().getProperty("java.home");
                String agent = StringUtils.join(new String[] {javaHome, "lib", "management-agent.jar"}, File.separator);
                vm.loadAgent(agent);

                connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
            }

            JMXServiceURL serviceURL = new JMXServiceURL(connectorAddress);
            return JMXConnectorFactory.connect(serviceURL);
        } finally {
            vm.detach();
        }
    }

    /**
     * Get a map from node ID to their state.
     * 
     * @return Map from node ID to state, true means up, false means down.
     */
    public Map<String, Boolean> getNodeStates() {
        return this.mbean.getNodeStates();
    }

    /**
     * Remove a node from cluster.
     * 
     * @param nodeId The ID of vipr node, e.g. vipr1, vipr2, etc.
     */
    public void removeNode(String nodeId) throws IOException, MalformedObjectNameException {
        this.mbean.removeNode(nodeId);
    }

    /**
     * Trigger node repair for specified keyspace
     * 
     * @param canResume
     */
    public void startNodeRepair(boolean canResume, boolean crossVdc) throws Exception {
        this.mbean.startNodeRepair(canResume, crossVdc);
    }

    /**
     * Get status of last repair, can be either running, failed, or succeeded.
     * 
     * @param forCurrentNodesOnly If true, this method will only return repairs for current node set.
     *            If false, all historical repairs for any node set can be returned.
     * @return The object describing the status. null if no repair started yet.
     */
    public DbRepairStatus getLastRepairStatus(boolean forCurrentNodesOnly) {
        return this.mbean.getLastRepairStatus(forCurrentNodesOnly);
    }

    /**
     * Get status of last succeeded repair, the returned status, if any, is always succeeded.
     * 
     * @param forCurrentNodesOnly If true, this method will only return repairs for current node set.
     *            If false, all historical repairs for any node set can be returned.
     * @return The object describing the status. null if no succeeded repair yet.
     */
    public DbRepairStatus getLastSucceededRepairStatus(boolean forCurrentNodesOnly) {
        return this.mbean.getLastSucceededRepairStatus(forCurrentNodesOnly);
    }

    public void resetRepairState() {
        mbean.resetRepairState();
    }
    
    /**
     * Remove multiple nodes from cluster.
     * 
     * @param nodeIds the ID list of vipr nodes, e.g. vipr1, vipr2, etc.
     */
    public void removeNodes(List<String> nodeIds) {
        for (String nodeId : nodeIds) {
            try {
                removeNode(nodeId);
                log.info("Remove node({}) from cassandra ring successful", nodeId);
            } catch (Exception e) {
                log.warn("Remove node({}) from cassandra ring failed", nodeId, e);
            }
        }
    }

    public void removeDataCenter(String dcName) {
        log.info("Removing Cassandra nodes for {}", dcName);
        mbean.removeDataCenter(dcName);
    }
    
    public void startNodeRepairAndWaitFinish(boolean canResume, boolean crossVdc) throws Exception {
        if (canResume && getLastSucceededRepairStatus(true) != null) {
            log.info("Resume last successful repair");
            return;
        }

        DbRepairStatus state = null;
        for (int i = 0; i < DB_REPAIR_MAX_RETRY_COUNT; i++) {
            startNodeRepair(canResume, crossVdc);
            state = waitDbRepairFinish(true);
            if (state != null) {
                break;
            }

            // It could be cluster state changed, so we have to wait for ANY repair to finish here
            // We don't care if it's NotFound, Success, Or Failed for other state, repair for current state is failed anyway.
            log.error("No db repair found for current cluster state, waiting for possible stale repair to finish");
            state = waitDbRepairFinish(false);

            // Trigger a new db repair
            log.info("Trigger a new db repair for current cluster state");
        }

        if (state.getStatus() == DbRepairStatus.Status.FAILED) {
            log.error("Db node repair started at {} is failed", state.getStartTime());
            throw new IllegalStateException("Repair failed");
        }

        log.info("Db node repair started at {} is finished", state.getStartTime());
    }

    public DbRepairStatus waitDbRepairFinish(boolean forCurrentStateOnly) throws Exception {
        for (int lastProgress = -1;; Thread.sleep(1000)) {
            DbRepairStatus status = getLastRepairStatus(forCurrentStateOnly);
            if (status == null) {
                log.info("No db repair found(forCurrentStateOnly={})", forCurrentStateOnly ? "true" : "false");
                return null;
            }

            if (status.getStatus() != DbRepairStatus.Status.IN_PROGRESS) {
                log.info("Db repair(forCurrentStateOnly={}) finished with state: {}",
                        forCurrentStateOnly ? "true" : "false", status.toString());
                return status;
            }

            int newProgress = status.getProgress();
            if (newProgress != lastProgress) {
                log.info("Db repair started at {} is in progress {}%", status.getStartTime(), newProgress);
                lastProgress = newProgress;
            }
        }
    }

    @Override
    public void close() {
        log.info("DbManagerOps.close() is called");
        try {
            if (this.conn != null) {
                this.conn.close();
                this.conn = null;
            }
        } catch (IOException e) {
            log.error("failed to close DbManagerOps", e);
        }
    }
}
