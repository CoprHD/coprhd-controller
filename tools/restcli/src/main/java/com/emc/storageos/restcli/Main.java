/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.restcli.command.CliCommandRest;
import com.emc.storageos.restcli.command.ProviderCommand;
import com.emc.storageos.restcli.command.VolumeCommand;

public class Main {
    private static boolean isDebug = false;

    private enum Cmd {
        HELP, REST, PROVIDER, VOLUME;
    }

    private static void usage() {
        System.out.println("Available commands:");
        System.out.println("\t" + Cmd.HELP.name().toLowerCase());
        System.out.println("\t" + Cmd.REST.name().toLowerCase());
        System.out.println("\t" + Cmd.PROVIDER.name().toLowerCase());
        System.out.println("\t" + Cmd.VOLUME.name().toLowerCase());
        System.out.println("please use \"restcli help <command>\" to show each command's usage.");
    }

    public static void main(String[] args) {

        if (isDebug) {
            List<String> params = new ArrayList<String>();
            params.add("rest");
            params.add("--get");
            params.add("--user");
            params.add("smc");
            params.add("--pass");
            params.add("smc");
            params.add("--url");
            params.add("https://lglw7150.lss.emc.com:8443/univmax/restapi/84/sloprovisioning/symmetrix/000196801468/storagegroup/stone_test_sg_auto_003");

            args = params.toArray(new String[] {});
        }
        if (args.length == 0) {
            usage();
            return;
        }

        Cmd cmd = null;
        boolean showUsage = false;
        try {
            cmd = Cmd.valueOf(args[0].trim().toUpperCase());
            if (cmd.equals(Cmd.HELP) && args.length == 2) {
                try {
                    showUsage = true;
                    cmd = Cmd.valueOf(args[1].trim().toUpperCase());
                } catch (Exception e) {
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid command: " + args[0]);
            usage();
            return;
        }

        switch (cmd) {
            default:
            case HELP:
                usage();
                break;
            case REST:
                CliCommandRest cc = new CliCommandRest();
                if (showUsage) {
                    cc.usage();
                } else {
                    cc.run(args);
                }
                break;
            case PROVIDER:
                ProviderCommand pc = new ProviderCommand();
                if (showUsage) {
                    pc.usage();
                } else {
                    pc.run(args);
                }
                break;
            case VOLUME:
                VolumeCommand vc = new VolumeCommand();
                if (showUsage) {
                    vc.usage();
                } else {
                    vc.run(args);
                }
                break;
        }
    }
}
