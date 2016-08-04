/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.sa.service.linux.file;

import org.junit.Before;
import org.junit.Test;

import com.emc.sa.service.linux.LinuxSupport;
import com.iwave.ext.linux.LinuxSystemCLI;

public class MountNFSExportTest {

    private static LinuxSystemCLI linuxSystem;
    private static LinuxSupport linuxSupport;
    private final String mountPoint = "vipr-isi6111.lss.emc.com:/ifs/vipr/vpoolantar297/ProviderTenant/testproject/testexport";
    private final String path = "/workspace/test";

    @Before
    public void setup() throws Exception {
        linuxSystem = new LinuxSystemCLI();
        linuxSystem.setHost("10.247.142.210");
        linuxSystem.setUsername("root");
        linuxSystem.setPassword("Antar@123");
        linuxSystem.setPort(22);
        linuxSupport = new LinuxSupport(linuxSystem, null);
    }

    @Test
    public void mountExport() {
        linuxSupport.createDirectory(path);
        linuxSupport.addToFSTab(mountPoint, path, "auto", "sec=sys");
        linuxSupport.mountPath(path);
    }

    @Test
    public void unmountExport() {
        linuxSupport.unmountPath(path);
        linuxSupport.removeFromFSTab(path);
        linuxSupport.deleteDirectory(path);
    }
}
