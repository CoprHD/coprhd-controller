/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

public class GetVIONetworkAdapterMacAddressCommand extends GetNetworkAdapterMacAddressCommand {

    public GetVIONetworkAdapterMacAddressCommand(String adapter) {
        super(adapter);
        setCommand("lsdev -dev " + adapter + " -vpd | grep \"Network Address\"");
    }
}