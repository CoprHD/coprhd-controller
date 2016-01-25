/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import java.util.regex.Pattern;

import com.iwave.ext.text.TextParser;

public class GetNetworkAdapterMacAddressCommand extends AixResultsCommand<String> {

    private static final Pattern NETWORK_ADDRESS_PATTERN = Pattern.compile("Network Address[.]+([0-9a-fA-F]*)");

    public GetNetworkAdapterMacAddressCommand(String adapter) {
        setCommand("lscfg -vl " + adapter + " | grep \"Network Address\"");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        if (getOutput() != null && getOutput().getStdout() != null) {
            String stdout = getOutput().getStdout();
            TextParser parser = new TextParser();
            String macAddress = parser.findMatch(NETWORK_ADDRESS_PATTERN, stdout);
            results = normalizeMacAddress(macAddress);
        }
    }

    private String normalizeMacAddress(String mac) {
        return mac.replaceAll("(.{2})", "$1:").substring(0, 17);
    }

}