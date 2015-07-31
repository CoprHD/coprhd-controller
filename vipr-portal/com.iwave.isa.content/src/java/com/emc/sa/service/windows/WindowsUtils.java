/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;
import com.iwave.ext.windows.WindowsSystemWinRM;

public class WindowsUtils {

    private static boolean DEFAULT_USE_SSL = false;
    private static int DEFAULT_PORT_NUMBER = 5985;

    public static WindowsSystemWinRM createWindowsSystem(Host host, Cluster cluster) {

        boolean useSsl = host.getUseSSL() != null ? host.getUseSSL().booleanValue() : DEFAULT_USE_SSL;
        int portNumber = host.getPortNumber() != null ? host.getPortNumber().intValue() : DEFAULT_PORT_NUMBER;

        WindowsSystemWinRM windowsSystem = new WindowsSystemWinRM(host.getHostName(), portNumber, useSsl, host.getUsername(),
                host.getPassword());
        windowsSystem.setHostId(host.getId());

        if (cluster != null) {
            windowsSystem.setClusterId(cluster.getId());
        }
        return windowsSystem;
    }

    public static boolean isMountPointDriveLetterOnly(String mountpoint) {
        return mountpoint.length() == 1 || mountpoint.length() == 0;
    }

    public static String normalizeMountPath(String mountPath) {
        if (isMountPointDriveLetterOnly(mountPath)) {
            return getDriveLetterFromMountPath(mountPath);
        }
        else {
            return getDriveLetterFromMountPath(mountPath) + mountPath.substring(1);
        }
    }

    public static String getDriveLetterFromMountPath(String mountPath) {
        if (mountPath.length() > 0) {
            return mountPath.toUpperCase().substring(0, 1);
        }
        return StringUtils.EMPTY;
    }

}
