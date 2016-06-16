/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vplexdbckr;

import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.vplex.api.VPlexApiClient;

/**
 * This program will check VPLEX database entries against the VPlex hardware.
 * It does this by initializing a dbClient instance, looking up the VPLEX systems in the database,
 * then it uses the VPLEX StorageSystem data to initialize the VPlexApiFactory to get a VPlexApiClient
 * which can make calls to the VPLEX hardware.
 * 
 * Currently the program performs these checks:
 * 1. For every virtual volume in the Vipr database, it insures that it can lookup the corresponding
 * VplexVirtualVolumeInfo structure with information about the virtual volume.
 * 2. Then it verifies that associataedVolumes are correct by comparing their WWN entries against entries
 * retrieved from the VPLEX for VPlexStorageVolumeInfo records for the virtual volume. 
 * Note that mirror WWNs are currently not checked.
 * Note also that this technique is ineffective (will indicate false errors) 
 * for certain types of storage devices where the WWN
 * does not match the WWN in the VPLEX such as Cinder devices.
 * 
 * The program currently takes no arguments, however in the future we may add additional services to
 * this program such as cleaning errant entries on the VPLEX.
 *
 */
public class Main {

    
    public static void main(String[] args) {
        System.out.println("vplexdbckr started");
        System.out.println("logs available at /opt/storageos/logs/vplexdbckr.log");
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/vplexdbckr-conf.xml"); // NOSONAR ("squid:S2444")
        VplexDBCkr vplexDBCkr = VplexDBCkr.getBean();
        vplexDBCkr.dbClientStart();
        List<StorageSystem> vplexSystems = vplexDBCkr.getVPlexSystems();
        for (StorageSystem vplexSystem : vplexSystems) {
            System.out.println("************Processing vplex: " + vplexSystem.getLabel());
            vplexDBCkr.checkVolumesOnVplex(vplexSystem.getId());
        }
        System.out.println("vplexdbckr done");
        System.exit(0);
    }

}
