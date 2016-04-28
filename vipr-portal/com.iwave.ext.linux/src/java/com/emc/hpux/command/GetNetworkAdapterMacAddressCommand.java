/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

public class GetNetworkAdapterMacAddressCommand extends HpuxResultsCommand<String> {

    public GetNetworkAdapterMacAddressCommand(String adapter) {
        setCommand("lanscan | grep " + adapter + " | awk '{print $2}'");
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            String macAddress = stdout;
            results = normalizeMacAddress(macAddress);
        }
    }

    private String normalizeMacAddress(String mac) {
        return mac.replaceAll("0x", "").replaceAll("(.{2})", "$1:").substring(0, 17);
    }

}