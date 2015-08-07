/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.setup;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.storageos.usermanagement.model.RoleOrAcl;
import com.emc.storageos.usermanagement.util.ViPRClientHelper;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.VirtualDataCenters;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GeoMode extends TenantMode {

    private static Logger logger = LoggerFactory.getLogger(GeoMode.class);
    private static List<VirtualDataCenterRestRep> vdcList = new ArrayList<VirtualDataCenterRestRep>();

    @BeforeClass
    public static void setup_GeoTenantModeBase() throws Exception {

        VirtualDataCenters vdcs = superUserClient.vdcs();

        List<NamedRelatedResourceRep> list = vdcs.list();
        for (NamedRelatedResourceRep vdc : list) {
            VirtualDataCenterRestRep restRep = superUserClient.vdcs().get(vdc.getId());
            vdcList.add(restRep);
        }

        if (vdcList.size() < 2) {
            String errorMsg = "env check fail, only one VDC, Geo test need at least 2 vdcs";
            logger.error(errorMsg);
            throw new Exception(errorMsg);
        }

        logger.info("grant " + superUser + " security admin in both VDCs");
        ViPRCoreClient vdc1RootClient = new ViPRCoreClient(getVdcEndpointByIndex(0), true)
                .withLogin("root", rootPassword);
        ViPRClientHelper vdc1Helper = new ViPRClientHelper(vdc1RootClient);
        vdc1Helper.addRoleAssignment(null, superUser, RoleOrAcl.SecurityAdmin.toString());
        vdc1Helper.addRoleAssignment(null, superUser, RoleOrAcl.SystemAdmin.toString());
        vdc1Helper.addRoleAssignment(null, superUser, RoleOrAcl.SystemMonitor.toString());
        vdc1RootClient.auth().logout();

        ViPRCoreClient vdc2RootClient = new ViPRCoreClient(getVdcEndpointByIndex(1), true)
                .withLogin("root", rootPassword);
        ViPRClientHelper vdc2Helper = new ViPRClientHelper(vdc2RootClient);
        vdc2Helper.addRoleAssignment(null, superUser, RoleOrAcl.SecurityAdmin.toString());
        vdc2Helper.addRoleAssignment(null, superUser, RoleOrAcl.SystemAdmin.toString());
        vdc2Helper.addRoleAssignment(null, superUser, RoleOrAcl.SystemMonitor.toString());
        vdc2RootClient.auth().logout();
    }

    @AfterClass
    public static void teardown_GeoTenantModeBase() throws Exception {

    }

    public static String getVdcEndpointByIndex(int index) {
        if (vdcList.size() >= index) {
            return vdcList.get(index).getApiEndpoint();
        }

        return null;
    }
}
