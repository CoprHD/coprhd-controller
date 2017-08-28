/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.setup

class LocalSystem {
    @Lazy static String macAddress = getMac()

    @Lazy static String hostName = getDefaultNetwork().inetAddresses.collect {it.hostName}.last()

    static String pwwn(String index) {
        "50:$macAddress:$index"
    }

    static String nwwn(String index) {
        "51:$macAddress:$index"
    }

    static def pwwns(def indices) {
        indices.collect {pwwn it}
    }

    static String getMac() {
        getDefaultNetwork().hardwareAddress.collect {String.format("%02x", it)}.join(":")
    }

    static NetworkInterface getDefaultNetwork() {
        NetworkInterface.networkInterfaces.find {!it.loopback}
    }
}
