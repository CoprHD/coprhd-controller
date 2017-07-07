/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import com.emc.storageos.driver.restvmax.RestVMAXStorageDriver;
import com.emc.storageos.driver.restvmax.rest.BackendType;
import com.emc.storageos.driver.restvmax.vmax.type.CapacityUnitType;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;

import java.util.ArrayList;
import java.util.List;

public class VolumeCommand extends CliCommand {
    private String user;
    private String pass;
    private String providerHost;
    private Integer port = 0;
    private boolean jsonFinePrint = true;
    private static final String INVALID_PARAM = "Invalid parameter: ";
    private String groupId;
    private Integer num_vol;
    private long capacity;
    private String poolId;
    private String capacityUnit = String.valueOf(CapacityUnitType.MB);

    @Override
    public void usage() {
        System.out.println("Description:\n\tStorage Provider Command.");
        System.out.println("Usage:");
        System.out.println("\trestcli volume [--fineprint|--nofineprint] [--user USERNAME] [--pass PASSWORD]" +
                " --ip IP_ADDR [--port PORT] --poolid POOLID --groupid GROUPID --num_vol NUM --capacity NUM [--capacityunit MB[|GB|TB]]");
    }
    public void run(String[] args) {
        this.parseRestArgs(args);
        RestVMAXStorageDriver driver = new RestVMAXStorageDriver();
        // connection
        StorageProvider provider = new StorageProvider();
        provider.setProviderHost(providerHost);
        if (port > 0) {
            provider.setPortNumber(port);
        } else {
            provider.setPortNumber(BackendType.VMAX.getPort());
        }
        provider.setUsername(user);
        provider.setPassword(pass);
        // pass parameter to create volume
        List<StorageVolume> volumes = new ArrayList<>();
        StorageVolume volume = new StorageVolume();
        volume.setStorageGroupId(groupId);
        volume.setStoragePoolId(poolId);
        volume.setRequestedCapacity(capacity);
        volumes.add(volume);
        // check correction
        System.out.println("port:" + provider.getPortNumber() + ", host:" + provider.getProviderHost() +
                ", user:" + provider.getUsername() + ", pass:" + provider.getPassword() + ", storageGroupId:" +  volume.getStorageGroupId()
                + ", poolId:" + volume.getStoragePoolId() + ", capacity:" + volume.getRequestedCapacity());
        List<StorageSystem> storageSystems = new ArrayList<>();
        DriverTask task = driver.discoverStorageProvider(provider, storageSystems);
        System.out.println(task.getMessage());
        task = driver.createVolumes(volumes, null);
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
                case "--groupid":
                    this.groupId = String.valueOf(args[++i]);
                    break;
                case "--poolid":
                    this.poolId = String.valueOf(args[++i]);
                    break;
                case "--num_vol":
                    this.num_vol = Integer.valueOf(args[++i]);
                    break;
                case "--capacity":
                    this.capacity = Long.valueOf(args[++i]);
                    break;
                case "--capacityunit":
                    this.capacityUnit = String.valueOf(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException(INVALID_PARAM + args[i]);
            }
        }

        if (this.capacityUnit.equals(CapacityUnitType.MB.toString())) {
            this.capacity *= 1024 * 1024;
        } else if (this.capacityUnit.equals(CapacityUnitType.GB.toString())) {
            this.capacity *= 1024 * 1024 * 1024;
        } else if (this.capacityUnit.equals(CapacityUnitType.TB.toString())) {
            this.capacity *= 1024 * 1024 * 1024 * 1024;
        }
    }

}
