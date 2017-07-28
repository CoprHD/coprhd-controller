/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.restcli.command;

import com.emc.storageos.driver.univmax.UniVmaxStorageDriver;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;

import java.util.ArrayList;
import java.util.List;

public class ProviderCommand extends CliCommand {
    private String user;
    private String pass;
    private String providerHost;
    private Integer port = 0;
    private boolean useSsl = true;
    private static final String INVALID_PARAM = "Invalid parameter: ";

    @Override
    public void usage() {
        System.out.println("Description:\n\tStorage Provider Command.");
        System.out.println("Usage:");
        System.out.println("\trestcli provider [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|HOSTNAME] [--port PORT]");
    }

    public void run(String[] args) {
        this.parseRestArgs(args);
        UniVmaxStorageDriver driver = new UniVmaxStorageDriver();
        StorageProvider provider = new StorageProvider();
        provider.setUseSSL(useSsl);
        provider.setProviderHost(providerHost);
        provider.setPortNumber(RestClient.DEFAULT_PORT);
        if (port > 0) {
            provider.setPortNumber(port);
        }
        provider.setUsername(user);
        provider.setPassword(pass);
        System.out.println("port:" + provider.getPortNumber() + ", host:" + provider.getProviderHost() +
                ", user:" + provider.getUsername() + ", pass:" + provider.getPassword());
        List<StorageSystem> storageSystems = new ArrayList<>();

        DriverTask task = driver.discoverStorageProvider(provider, storageSystems);

        System.out.println("Task Status:\n\t" + task.getStatus().toString());
        System.out.println("Task Message:\n\t" + task.getMessage());
        System.out.println("Provider Version:\n\t" + provider.getProviderVersion());
        System.out.println("Supported Symmetrix Systems:");
        for (StorageSystem system : storageSystems) {
            System.out.println("\t" + system.getSerialNumber());
        }
    }

    private void parseRestArgs(String[] args) {
        String paramFile = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--user":
                    this.user = args[++i];
                    break;
                case "--pass":
                    this.pass = args[++i];
                    break;
                case "--ssl":
                    this.useSsl = true;
                    break;
                case "--nossl":
                    this.useSsl = false;
                    break;
                case "--host":
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