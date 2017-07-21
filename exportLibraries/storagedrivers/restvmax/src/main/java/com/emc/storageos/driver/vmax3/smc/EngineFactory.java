/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc;

import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.vmax3.smc.symmetrix.config.ConfigEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.hw.HardwareEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.mv.ExportEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.replication.ReplicationEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.sg.StorageGroupEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.srdf.SrdfEngine;
import com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.VolumeEngine;

final public class EngineFactory {
    private AuthenticationInfo authenticationInfo;
    private VolumeEngine volumeEngine;
    private StorageGroupEngine storageGroupEngine;
    private ReplicationEngine replicationEngine;
    private SrdfEngine srdfEngine;
    private ExportEngine exportEngine;

    private ConfigEngine configEngine;
    private HardwareEngine hardwareEngine;

    public EngineFactory(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;

    }

    public VolumeEngine genVolumeEngine() {
        if (volumeEngine == null) {
            volumeEngine = new VolumeEngine(authenticationInfo);
        }
        return volumeEngine;
    }

    public StorageGroupEngine genStorageGroupEngine() {
        if (storageGroupEngine == null) {
            storageGroupEngine = new StorageGroupEngine(authenticationInfo);
        }
        return storageGroupEngine;
    }

    public ConfigEngine genConfigEngine() {
        if (configEngine == null) {
            configEngine = new ConfigEngine(authenticationInfo);
        }
        return configEngine;
    }

    public HardwareEngine genHardwareEngine() {
        if (hardwareEngine == null) {
            hardwareEngine = new HardwareEngine(authenticationInfo);
        }
        return hardwareEngine;
    }

    public ReplicationEngine genReplicationEngine() {
        if (replicationEngine == null) {
            replicationEngine = new ReplicationEngine(authenticationInfo);
        }
        return replicationEngine;
    }

    public SrdfEngine genSrdfEngine() {
        if (srdfEngine == null) {
            srdfEngine = new SrdfEngine(authenticationInfo);
        }
        return srdfEngine;
    }

    public ExportEngine genExportEngine() {
        if (exportEngine == null) {
            exportEngine = new ExportEngine(authenticationInfo);
        }
        return exportEngine;
    }
}
