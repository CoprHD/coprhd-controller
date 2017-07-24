/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.restcli.command;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.driver.univmax.UnivmaxStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;

public class ProviderCommand extends CliCommand {
    private String user;
    private String pass;
    private String providerHost;
    private Integer port = 8443;
    private boolean jsonFinePrint = true;
    private static final String INVALID_PARAM = "Invalid parameter: ";

    @Override
    public void usage() {
        System.out.println("Description:\n\tStorage Provider Command.");
        System.out.println("Usage:");
        System.out.println("\trestcli provider [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " --ip IP_ADDR [--port PORT]");
    }

    public void run(String[] args) {
        this.parseRestArgs(args);
        UnivmaxStorageDriver driver = new UnivmaxStorageDriver();
        StorageProvider provider = new StorageProvider();
        provider.setProviderHost(providerHost);
        if (port > 0) {
            provider.setPortNumber(port);
        } else {
            provider.setPortNumber(port);
        }
        provider.setUsername(user);
        provider.setPassword(pass);
        System.out.println("port:" + provider.getPortNumber() + ", host:" + provider.getProviderHost() +
                ", user:" + provider.getUsername() + ", pass:" + provider.getPassword());
        List<StorageSystem> storageSystems = new ArrayList<>();
        DriverTask task = driver.discoverStorageProvider(provider, storageSystems);
        System.out.println(task.getMessage());
    }

    private void parseRestArgs(String[] args) {
        String paramFile = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--fineprint":
                    this.jsonFinePrint = true;
                    break;
                case "--nofineprint":
                    this.jsonFinePrint = false;
                    break;
                case "--user":
                    this.user = args[++i];
                    break;
                case "--pass":
                    this.pass = args[++i];
                    break;
                case "--ip":
                    this.providerHost = args[++i];
                    break;
                case "--port":
                    this.port = Integer.valueOf(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException(INVALID_PARAM + args[i]);
            }
        }
    }
}