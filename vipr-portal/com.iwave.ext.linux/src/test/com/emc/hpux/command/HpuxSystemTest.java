/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.List;
import java.util.Set;

import com.emc.hpux.HpuxSystem;
import com.iwave.ext.linux.model.HBAInfo;
import com.iwave.ext.linux.model.IPInterface;

public class HpuxSystemTest {

    public static void main(String[] args) {
        HpuxSystem hpux = new HpuxSystem("lglal017.lss.emc.com", 22, "sudo2", "sudo2");

        // PATH=/sbin:/usr/sbin

        // CommandOutput output = hpux
        // .executeCommand("export PATH=$PATH:/usr/local/bin; sudo sh -c \"echo hello\"");

        // System.out.println(output.getStdout());

        List<IPInterface> ips = hpux.listIPInterfaces();
        List<HBAInfo> hbas = hpux.listInitiators();
        Set<String> iqns = hpux.listIQNs();

        System.out.println("done.");

    }

}
