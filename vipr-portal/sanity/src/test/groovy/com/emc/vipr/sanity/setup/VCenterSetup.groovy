/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.setup.LocalSystem.*

import com.emc.storageos.model.host.vcenter.VcenterCreateParam

class VCenterSetup {

    static final String vCenterName = "VcenterSim"
    static def vCenterId

    static void setup() {
        println "Setting up vCenter simulator"
        vCenterId = client.vcenters().search().byExactName(vCenterName).first()?.id
        if (vCenterId != null) {
            return
        }
        client.vcenters().create(client.userTenantId, new VcenterCreateParam(name: vCenterName, ipAddress: System.getenv("VCENTER_SIMULATOR_IP"),
        portNumber: System.getenv("VCENTER_SIMULATOR_PORT") as int, userName: System.getenv("VCENTER_SIMULATOR_USERNAME"), password: System.getenv("VCENTER_SIMULATOR_PASSWORD"),
        useSsl: true)).waitFor()
    }
}
