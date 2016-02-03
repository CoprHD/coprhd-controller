/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.audit;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Enumeration;
import java.sql.Timestamp;
import java.net.NetworkInterface;
import java.net.InetAddress;

import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.services.OperationTypeEnum;

public class SystemAudit implements Runnable {
    private static final Logger _log = LoggerFactory.getLogger(SystemAudit.class);
    private static final String EVENT_SERVICE_TYPE = "SystemAudit";
    private static String _auditlogfile = "/var/log/auth";
    private static String _auditlogCPfile = "/var/log/auth.checkpoint";
    private static int _scanInterval = 120 * 1000; // 2m

    /*
     * Here are pattern of system logs we are going to audit.
     * There are only one kind of auditlog we recorded for now.
     * 1. ssh login/execution log. Format is like following.
     * For password failure:
     * "2013-03-05 04:47:52 [auth] err sshd[8824]: error: PAM: Authentication failure for root from localhost"
     * For incorrect pkcs key
     * "2013-08-01 05:12:04 [auth] err sshd[22030]: error: key_read: uudecode aaaAAAAB3NzaC1yc2EAA...BMQfrbasOGYeu1Kjv root@lglw2020\n failed"
     */
    private static Pattern auditlogPattern = Pattern
            .compile("([0-9-:\\s]{19}) (\\[auth\\]) (info|err) (sshd.*:|logger.*:) (Accepted.*|error.*|[0-9-:\\s]{19}.*) (for|key_read: uudecode \\S+) (.*)( from |@)([\\p{Alnum}\\.]+)[\\p{Alnum}\\s]*");

    private AuditLogManager _auditMgr = null;

    public SystemAudit(DbClient dbClient, CoordinatorClient coordinatorClient) {
        _log.info("SystemAudit initialized");

        _auditMgr = new AuditLogManager(dbClient, coordinatorClient);
    }

    public void setScanInterval(int interval) {
        _scanInterval = interval;
    }

    public void run() {
        // Endlessly monitor system logs (only "auth" for now) and
        // persistent them into cassandra db for audit
        while (true) {

            String host = null;
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface current = interfaces.nextElement();
                    if (!current.isUp() || current.isLoopback() || current.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = current.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress current_addr = addresses.nextElement();
                        if (current_addr.isLoopbackAddress()) {
                            continue;
                        }
                        host = current_addr.getHostAddress();
                    }
                }
            } catch (Exception e) {
                _log.error("can not get system ip address, using default localhost");
                host = "localhost";
            }

            RandomAccessFile logCPfile = null;
            BufferedReader br = null;
            try {
                _log.info("Scanning log message for system audit in {} seconds ...",
                        _scanInterval / 1000);
                Thread.sleep(_scanInterval);

                // 1. read last audited log from checkpoint file
                logCPfile = new RandomAccessFile(_auditlogCPfile, "rw");
                long lastAuditedlogTime = 0;
                String lastAuditedlog = logCPfile.readLine();
                if (lastAuditedlog != null) {
                    Matcher m = auditlogPattern.matcher(lastAuditedlog);
                    if (m.find()) {
                        Timestamp lastAuditedlogTimestamp = Timestamp.valueOf(m.group(1));
                        lastAuditedlogTime = lastAuditedlogTimestamp.getTime();
                    }
                }

                // 2. scan auditlog file to insert qualified logs to cassandra
                br = new BufferedReader(new FileReader(_auditlogfile));
                String line = null;
                Matcher m = null;

                Timestamp currlogTimestamp = null;
                while ((line = br.readLine()) != null) {
                    m = auditlogPattern.matcher(line);
                    if (m.find()) {
                        currlogTimestamp = Timestamp.valueOf(m.group(1));
                        if (currlogTimestamp.getTime() < lastAuditedlogTime) {
                            continue;
                        } else if (currlogTimestamp.getTime() == lastAuditedlogTime) {
                            if (line.equals(lastAuditedlog)) {
                                continue;
                            }
                        }

                        if (m.group(4).startsWith("sshd")) {
                            if (m.group(3).equals("err")) {
                                recordSysAuditLog(OperationTypeEnum.SSH_LOGIN,
                                        currlogTimestamp.getTime(), AuditLogManager.AUDITLOG_FAILURE,
                                        null, m.group(7), m.group(9), host);
                            } else {
                                recordSysAuditLog(OperationTypeEnum.SSH_LOGIN,
                                        currlogTimestamp.getTime(), AuditLogManager.AUDITLOG_SUCCESS,
                                        null, m.group(7), m.group(9), host);
                            }
                        }

                        // update audited log into checkpoint file
                        logCPfile.seek(0);
                        logCPfile.writeBytes(line);
                        logCPfile.setLength(line.length());
                    }
                }

            } catch (FileNotFoundException e) {
                _log.error("Failed to open system log file: {}", e.toString());
            } catch (Exception e) {
                _log.error("Problem scanning system log file: {}", e.toString());
            } finally {
                try {
                    br.close();
                    logCPfile.close();
                } catch (Exception e) {
                    _log.warn("Problem while releasing file resources", e.toString());
                }
            }
        }
    }

    public void recordSysAuditLog(OperationTypeEnum auditType,
            long logTime,
            String operationalStatus,
            String description,
            Object... descparams) {
        _auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                logTime,
                operationalStatus,
                description,
                descparams);
    }

}
