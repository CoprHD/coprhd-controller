/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli;

import com.emc.storageos.restcli.command.*;

public class Main {

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
