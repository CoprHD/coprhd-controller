/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

import static com.emc.vipr.sanity.Sanity.*
import static com.emc.vipr.sanity.setup.LocalSystem.*

import com.emc.storageos.model.host.HostCreateParam
import com.emc.storageos.model.host.InitiatorCreateParam
import com.emc.storageos.model.host.cluster.ClusterCreateParam

class HostSetup {
    static final String cluster1Name = "sanityCluster1"
    static final String cluster2Name = "sanityCluster2"

    static def cluster1Id
    static def cluster2Id
    static def hosts = []

    static void setup() {
        println "Setting up hosts and clusters"
        cluster1Id = client.clusters().search().byExactName(cluster1Name).first()?.id
        cluster2Id = client.clusters().search().byExactName(cluster2Name).first()?.id
        if (cluster1Id != null) {
            return
        }

        cluster1Id = client.clusters().create(client.userTenantId, new ClusterCreateParam(name: cluster1Name, project: ProjectSetup.projectId)).id
        cluster2Id = client.clusters().create(client.userTenantId, new ClusterCreateParam(name: cluster2Name, project: ProjectSetup.projectId)).id

        int hostCounter = 1
        [cluster1Id, cluster2Id].each {clusterId->
            2.times {
                String hostName = "host${ProjectSetup.tenantName}$hostCounter"
                def host = client.hosts().create(new HostCreateParam(
                        tenant: client.userTenantId,
                        name: hostName,
                        type: "Windows",
                        hostName: "${hostName}.lss.emc.com",
                        portNumber: 8111,
                        userName: "user",
                        password: "password",
                        cluster: clusterId,
                        discoverable: false
                        )).get()
                ['A', 'B', 'C', 'D'].each {letter->
                    client.initiators().create(host.id, new InitiatorCreateParam(
                            protocol: "FC",
                            node: nwwn("$letter$hostCounter"),
                            port: pwwn("$letter$hostCounter")
                            ))
                }

                client.initiators().create(host.id, new InitiatorCreateParam(
                        protocol: "FC",
                        port: "51:00:50:56:9F:01:3B:A$hostCounter",
                        node: "50:00:50:56:9F:01:3B:A$hostCounter"
                        ))

                hosts << host
                hostCounter++
            }
        }
    }
}
