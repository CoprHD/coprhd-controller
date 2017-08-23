/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.restcli.command;

import com.emc.storageos.driver.univmax.helper.DriverUtil;
import com.emc.storageos.driver.univmax.UniVmaxStorageDriver;
import com.emc.storageos.driver.univmax.rest.JsonUtil;
import com.emc.storageos.driver.univmax.rest.RestClient;
import com.emc.storageos.driver.univmax.rest.type.common.CapacityUnitType;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.StorageVolume;

import java.util.ArrayList;
import java.util.List;

public class VolumeCommand extends CommandTmpl {
    private String user;
    private String pass;
    private Boolean useSsl = true;
    private String providerHost;
    private Integer port = 0;
    private static final String INVALID_PARAM = "Invalid parameter";
    private String groupId;
    private long capacity;
    private String sym;
    private String poolId;
    private String capacityUnit = String.valueOf(CapacityUnitType.MB);

    @Override
    public void usage() {
        println("Description:\n\tStorage Volume Command.");
        println("Usage:");
        println("\trestcli volume [--user USERNAME] [--pass PASSWORD]" +
                " [--ssl|--nossl] --host IP[|HOSTNAME] [--port PORT] " +
                "--sym SYMMETRIX_ID --poolid POOL_ID --groupid GROUP_ID " +
                "--capacity NUM [--capacityunit MB[|GB|TB]]");
    }

    @Override
    public void run(String[] args) {
        try {
            this.parseRestArgs(args);
            UniVmaxStorageDriver driver = new UniVmaxStorageDriver();
            // connection
            if (port == 0) {
                port = RestClient.DEFAULT_PORT;
            }
            RestClient client = new RestClient(useSsl, providerHost, port, user, pass);
            driver.getDriverDataUtil().addRestClient(sym, client);

            // pass parameter to create volume
            List<StorageVolume> volumes = new ArrayList<>();
            StorageVolume volume = new StorageVolume();
            volume.setStorageGroupId(groupId);
            volume.setStoragePoolId(poolId);
            volume.setRequestedCapacity(capacity);
            volume.setStorageSystemId(sym);
            volumes.add(volume);
            // check parameter
            println("Input parameter:");
            println("\tport:" + port +
                    ", host:" + providerHost +
                    ", user:" + user +
                    ", pass:" + pass +
                    ", storageGroupId:" + volume.getStorageGroupId() +
                    ", poolId:" + volume.getStoragePoolId() +
                    ", symmetrixId:" + volume.getStorageSystemId() +
                    ", capacity:" + volume.getRequestedCapacity());
            println("\ncreateVolumes: start ....");

            DriverTask task = driver.createVolumes(volumes, null);

            println();
            showTaskInfo(task);
            println("\n---- Details ----");
            println("Volume:");
            println(JsonUtil.toJsonStringFinePrint(volume));
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
                    this.providerHost = args[++i];
                    break;
                case "--port":
                    this.port = Integer.valueOf(args[++i]);
                    break;
                case "--sym":
                    this.sym = String.valueOf(args[++i]);
                    break;
                case "--poolid":
                    this.poolId = String.valueOf(args[++i]);
                    break;
                case "--groupid":
                    this.groupId = String.valueOf(args[++i]);
                    break;
                case "--capacity":
                    this.capacity = Long.valueOf(args[++i]);
                    break;
                case "--capacityunit":
                    this.capacityUnit = String.valueOf(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException(INVALID_PARAM + ": " + args[i]);
            }
        }

        if (this.capacityUnit.equals(CapacityUnitType.MB.toString())) {
            this.capacity *= 1024 * 1024;
        } else if (this.capacityUnit.equals(CapacityUnitType.GB.toString())) {
            this.capacity *= 1024 * 1024 * 1024;
        } else if (this.capacityUnit.equals(CapacityUnitType.TB.toString())) {
            this.capacity *= 1024 * 1024 * 1024 * 1024;
        }

        if (this.providerHost == null || this.sym == null || this.poolId == null
                || this.groupId == null || this.capacity == 0 || this.capacityUnit == null) {
            throw new IllegalArgumentException(INVALID_PARAM);
        }
    }
}
