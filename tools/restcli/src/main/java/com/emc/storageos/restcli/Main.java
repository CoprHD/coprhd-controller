/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli;


import com.emc.storageos.restcli.command.DiscoverCommand;
import com.emc.storageos.restcli.command.RestCommand;
import com.emc.storageos.restcli.command.VolumeCommand;

public class Main {

    private enum Cmd {
        HELP, REST, DISCOVER, VOLUME;
    }

    private static void usage() {
        System.out.println("Available commands:");
        for (Cmd c : Cmd.values()) {
            System.out.println("\t" + c.name().toLowerCase());
        }
        System.out.println("please use \"restcli help <command>\" to show each command's usage.");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        Cmd cmd;
        boolean showUsage = false;
        try {
            cmd = Cmd.valueOf(args[0].trim().toUpperCase());
            if (cmd.equals(Cmd.HELP) && args.length == 2) {
                showUsage = true;
                cmd = Cmd.valueOf(args[1].trim().toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid command: " + args[0]);
            usage();
            return;
        }

        switch (cmd) {
            case REST:
                RestCommand cc = new RestCommand();
                if (showUsage) {
                    cc.usage();
                } else {
                    cc.run(args);
                }
                break;
            case DISCOVER:
                DiscoverCommand dc = new DiscoverCommand();
                if (showUsage) {
                    dc.usage();
                } else {
                    dc.run(args);
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
            case HELP:
            default:
                usage();
                break;
        }
    }
}
