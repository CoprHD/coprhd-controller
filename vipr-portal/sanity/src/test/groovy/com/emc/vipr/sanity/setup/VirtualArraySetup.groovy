/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.setup.LocalSystem.*

import com.emc.storageos.model.auth.ACLAssignmentChanges
import com.emc.storageos.model.auth.ACLEntry
import com.emc.storageos.model.network.NetworkSystemCreate
import com.emc.storageos.model.varray.EndpointChanges
import com.emc.storageos.model.varray.NetworkCreate
import com.emc.storageos.model.varray.NetworkUpdate
import com.emc.storageos.model.varray.VirtualArrayCreateParam

class VirtualArraySetup {
    static final String VARRAY = "nh"
    static final String VARRAY2 = "nh2"
    static final String IP_NETWORK = "iptz"

    static def varrayId
    static def varray2Id
    static def fcNetworkAId
    static def fcNetworkBId

    static void setup() {
        println "Setting up Virtual Arrays"
        varrayId = client.varrays().search().byExactName(VARRAY).first()?.id
        varray2Id = client.varrays().search().byExactName(VARRAY2).first()?.id
        if (varrayId != null) {
            return
        }

        varrayId = client.varrays().create(new VirtualArrayCreateParam(VARRAY)).id
        varray2Id = client.varrays().create(new VirtualArrayCreateParam(VARRAY2)).id

        client.networks().create(new NetworkCreate(label: IP_NETWORK, varrays: [varrayId], transportType: "IP"))

        ciscoMdsSetup()

        client.networks().update(fcNetworkAId, new NetworkUpdate(
                varrays: [varrayId],
                endpointChanges: new EndpointChanges(
                add: pwwns([
                    "A1",
                    "A2",
                    "A3",
                    "A4",
                    "A5",
                    "A6",
                    "A7",
                    "A8",
                    "C1",
                    "C2",
                    "C3",
                    "C4",
                    "C5",
                    "C6",
                    "C7",
                    "C8"
                ])
                )
                ))

        client.networks().update(fcNetworkBId, new NetworkUpdate(
                varrays: [varrayId],
                endpointChanges: new EndpointChanges(
                add: pwwns([
                    "B1",
                    "B2",
                    "B3",
                    "B4",
                    "B5",
                    "B6",
                    "B7",
                    "B8",
                    "D1",
                    "D2",
                    "D3",
                    "D4",
                    "D5",
                    "D6",
                    "D7",
                    "D8"
                ])
                )
                ))
    }

    static void ciscoMdsSetup() {
        client.networkSystems().create(new NetworkSystemCreate(
                name: 'CiscoMdsSimulator',
                systemType: 'mds',
                ipAddress: System.getenv("SIMULATOR_CISCO_MDS"),
                portNumber: 22,
                userName: System.getenv("SIMULATOR_CISCO_MDS_USER"),
                password: System.getenv("SIMULATOR_CISCO_MDS_PW")
                )).waitFor(API_TASK_TIMEOUT)

        client.networks().all

        fcNetworkAId = client.networks().search().byExactName("VSAN_11").first()?.id
        fcNetworkBId = client.networks().search().byExactName("VSAN_12").first()?.id

        client.networks().update(fcNetworkAId, new NetworkUpdate(
                varrays: [varrayId],
                endpointChanges: new EndpointChanges(
                add: [
                    "51:00:50:56:9F:01:3B:A1",
                    "51:00:50:56:9F:01:3B:A2",
                    "51:00:50:56:9F:01:3B:A3",
                    "51:00:50:56:9F:01:3B:A4",
                    "19:99:00:00:00:00:00:28",
                    "19:99:00:00:00:00:00:29",
                    "19:99:00:00:00:00:00:2A",
                    "19:99:00:00:00:00:00:2B",
                    "19:99:00:00:00:00:00:2C",
                    "19:99:00:00:00:00:00:2D",
                    "19:99:00:00:00:00:00:2E",
                    "19:99:00:00:00:00:00:2F",
                    "19:99:00:00:00:00:00:50",
                    "19:99:00:00:00:00:00:51",
                    "19:99:00:00:00:00:00:52",
                    "19:99:00:00:00:00:00:53",
                    "19:99:00:00:00:00:00:54",
                    "19:99:00:00:00:00:00:55",
                    "19:99:00:00:00:00:00:56",
                    "19:99:00:00:00:00:00:57"
                ]
                )
                ))
        client.networks().update(fcNetworkBId, new NetworkUpdate(varrays: [varrayId]))
    }

    static void updateAcls(def tenantId) {
        println "Updating ACLs on Virtual Arrays"
        client.varrays().updateACLs(varrayId, new ACLAssignmentChanges(
                add: [
                    new ACLEntry(tenant: tenantId, aces: ["USE"])
                ]
                ))

        client.varrays().updateACLs(varray2Id, new ACLAssignmentChanges(
                add: [
                    new ACLEntry(tenant: tenantId, aces: ["USE"])
                ]
                ))
    }
}
