package com.emc.vipr.sanity.setup

import static LocalSystem.*
import static Sanity.*
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
