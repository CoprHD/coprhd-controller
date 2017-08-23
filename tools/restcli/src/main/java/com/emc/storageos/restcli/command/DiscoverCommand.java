/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import com.emc.storageos.driver.univmax.helper.DriverUtil;
import com.emc.storageos.driver.univmax.UniVmaxStorageDriver;
import com.emc.storageos.driver.univmax.rest.JsonUtil;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import org.apache.commons.lang.mutable.MutableInt;

import java.util.ArrayList;
import java.util.List;

public class DiscoverCommand extends CommandTmpl {

    private String user;
    private String pass;
    private boolean useSsl = true;
    private String host;
    private Integer port = 0;
    private String sym;
    private OPTION option = OPTION.PROVIDER;

    private enum OPTION {
        PROVIDER, SYMMETRIX, VOLUME, POOL, STORAGEPORT
    }

    @Override
    public void run(String[] args) {
        try {
            parseRestArgs(args);

            UniVmaxStorageDriver driver = new UniVmaxStorageDriver();
            StorageProvider provider = new StorageProvider();
            provider.setUseSSL(useSsl);
            provider.setProviderHost(host);
            provider.setPortNumber(port);
            provider.setUsername(user);
            provider.setPassword(pass);
            println("port:" + provider.getPortNumber() + ", host:" + provider.getProviderHost() +
                    ", user:" + provider.getUsername() + ", pass:" + provider.getPassword());
            List<StorageSystem> storageSystems = new ArrayList<>();
            StorageSystem system = new StorageSystem();
            if (this.sym != null) {
                system.setNativeId(sym);
                system.setIpAddress(host);
                system.setPortNumber(port);
                system.setUsername(user);
                system.setPassword(pass);
            }

            // discover storage provider
            DriverTask task = driver.discoverStorageProvider(provider, storageSystems);
            switch (option) {
                case PROVIDER:
                    showTaskInfo(task);
                    println("\n---- Details ----");
                    println("Storage Provider:");
                    println(JsonUtil.toJsonStringFinePrint(provider));
                    println("Supported Storage Systems:");
                    println(JsonUtil.toJsonStringFinePrint(storageSystems));
                    break;
                case SYMMETRIX:
                    // discover storage system
                    task = driver.discoverStorageSystem(system);

                    showTaskInfo(task);
                    println("\n---- Details ----");
                    println("Storage System:");
                    println(JsonUtil.toJsonStringFinePrint(system));
                    break;
                case VOLUME:
                    List<StorageVolume> volumeList = new ArrayList<>();
                    MutableInt nextPage = new MutableInt(0);
                    task = driver.getStorageVolumes(system, volumeList, nextPage);

                    showTaskInfo(task);
                    println("\n---- Details ----");
                    println("Volumes:");
                    for (StorageVolume volume : volumeList) {
                        println(JsonUtil.toJsonStringFinePrint(volume));
                    }
                    break;
            }
        } catch (Exception e) {
            println(DriverUtil.getStackTrace(e));
            usage();
        }
    }

    private void parseRestArgs(String[] args) {
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
                    this.host = args[++i];
                    break;
                case "--port":
                    this.port = Integer.valueOf(args[++i]);
                    break;
                case "--sym":
                    this.sym = args[++i];
                    break;
                case "--provider":
                    this.option = OPTION.PROVIDER;
                    break;
                case "--symmetrix":
                    this.option = OPTION.SYMMETRIX;
                    break;
                case "--volume":
                    this.option = OPTION.VOLUME;
                    break;
                case "--pool":
                    this.option = OPTION.POOL;
                    break;
                case "--storage-port":
                    this.option = OPTION.STORAGEPORT;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parameter: " + args[i]);
            }
        }

        if (this.host == null) {
            throw new IllegalArgumentException("Missing argument \"--host\"");
        }
        if (this.option == OPTION.PROVIDER) {
            if (this.sym != null) {
                this.option = OPTION.SYMMETRIX;
            }
        } else {
            // volume, pool, port
            if (this.sym == null) {
                throw new IllegalArgumentException("Option [--" + this.option.name().toLowerCase() +
                        "] needs symmetrix ID, please provide parameter \"--sym\".");
            }
        }
        if (port == 0) {
            port = RestClient.DEFAULT_PORT;
        }
    }

    @Override
    public void usage() {
        println("Description:\n\tDiscover VMAX resources.");
        println("Usage:");
        println("\trestcli discover [--provider|--symmetrix|--volume|--pool|--storage-port]" +
                " [--user USERNAME] [--pass PASSWORD] --host IP[|NAME] [--port PORT] [--sym symmetrix]");
    }
}
