/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.jvmstat.monitor.*;
import java.net.URISyntaxException;

import java.util.Set;

public class CmdUtil {

    private static final Logger log = LoggerFactory.getLogger(CmdUtil.class);

    /**
     * Check if a Java process is running.
     */
    public static boolean isJavaProcessRunning(String processName) {
        boolean result = false;

        try {
            HostIdentifier hostIdentifier = new HostIdentifier("local://localhost");
            MonitoredHost monitoredHost;
            try {
                monitoredHost = MonitoredHost.getMonitoredHost(hostIdentifier);
            } catch (MonitorException e) {
                log.warn("Failed to get monitore host", e);
                return false;
            }

            Set<Integer> activeVms = (Set<Integer>) monitoredHost.activeVms();
            int thisProcessCount = 0;
            for (Integer activeVmId : activeVms) {
                try {
                    VmIdentifier vmIdentifier = new VmIdentifier("//" + String.valueOf(activeVmId) + "?mode=r");
                    MonitoredVm monitoredVm = monitoredHost.getMonitoredVm(vmIdentifier);
                    if (monitoredVm != null) {
                        String mainClass = MonitoredVmUtil.mainClass(monitoredVm, true);
                        log.debug("mainClass - {}", mainClass);
                        if (mainClass.toLowerCase().contains(processName.toLowerCase())) {
                            thisProcessCount++;
                            if (thisProcessCount > 1) {
                                result = true;
                                break;
                            }
                        }
                    }
                } catch (MonitorException e) {
                    log.debug("Ignoring monitor failure", e);
                }
            }
        } catch (URISyntaxException | MonitorException e) {
            log.debug("Ignoring uri syntax or monitor error", e);
        }
        return result;
    }

}
