/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.model.collectdata.MasterDataRestRep;
import com.emc.storageos.model.collectdata.MdmClusterDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIODeviceDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOFaultSetDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOProtectionDomainDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDCDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDSDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDSIPDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOStoragePoolDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSystemDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOVolumeDataRestRep;
import com.emc.storageos.model.collectdata.SlavesDataRestRep;
import com.emc.storageos.model.collectdata.TieBreakersDataRestRep;
import com.emc.storageos.scaleio.api.restapi.response.Master;
import com.emc.storageos.scaleio.api.restapi.response.MdmCluster;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIODevice;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOFaultSet;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOProtectionDomain;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDC;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.scaleio.api.restapi.response.Slaves;
import com.emc.storageos.scaleio.api.restapi.response.TieBreakers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ScaleIODataMapper {
    public static ScaleIOSystemDataRestRep map(ScaleIOSystem from) {
        if (from == null) {
            return null;
        }
        ScaleIOSystemDataRestRep to = new ScaleIOSystemDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOSDCDataRestRep map(ScaleIOSDC from){
        if (from == null) {
            return null;
        }
        ScaleIOSDCDataRestRep to = new ScaleIOSDCDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOSDSDataRestRep map(ScaleIOSDS from){
        if (from == null) {
            return null;
        }
        ScaleIOSDSDataRestRep to = new ScaleIOSDSDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIODeviceDataRestRep map(ScaleIODevice from) {
        if (from == null) {
            return null;
        }
        ScaleIODeviceDataRestRep to = new ScaleIODeviceDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOVolumeDataRestRep map(ScaleIOVolume from) {
        if (from == null) {
            return null;
        }
        ScaleIOVolumeDataRestRep to = new ScaleIOVolumeDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOStoragePoolDataRestRep map(ScaleIOStoragePool from){
        if (from == null) {
            return null;
        }
        ScaleIOStoragePoolDataRestRep to = new ScaleIOStoragePoolDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOFaultSetDataRestRep map(ScaleIOFaultSet from){
        if (from == null) {
            return null;
        }
        ScaleIOFaultSetDataRestRep to = new ScaleIOFaultSetDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOProtectionDomainDataRestRep map(ScaleIOProtectionDomain from){
        if (from == null) {
            return null;
        }
        ScaleIOProtectionDomainDataRestRep to = new ScaleIOProtectionDomainDataRestRep();
        map(from, to);
        return to;
    }

    public static ScaleIOSDSIPDataRestRep map(ScaleIOSDS.IP from){
        if (from == null) {
            return null;
        }
        ScaleIOSDSIPDataRestRep to = new ScaleIOSDSIPDataRestRep();
        map(from, to);
        return to;
    }

    public static MdmClusterDataRestRep map(MdmCluster from) {
        if (from == null) {
            return null;
        }
        MdmClusterDataRestRep to = new MdmClusterDataRestRep();
        map(from, to);
        return to;
    }

    public static MasterDataRestRep map(Master from){
        if (from == null) {
            return null;
        }
        MasterDataRestRep to = new MasterDataRestRep();
        map(from, to);
        return to;
    }

    public static SlavesDataRestRep map(Slaves from){
        if (from == null) {
            return null;
        }
        SlavesDataRestRep to = new SlavesDataRestRep();
        map(from, to);
        return to;
    }

    public static TieBreakersDataRestRep map(TieBreakers from) {
        if (from == null) {
            return null;
        }
        TieBreakersDataRestRep to = new TieBreakersDataRestRep();
        map(from, to);
        return to;
    }

    public static SlavesDataRestRep[] mapArray(Slaves[] from) {
        if (from == null) {
            return null;
        }
        return Arrays.stream(from).map(ScaleIODataMapper::map).toArray(SlavesDataRestRep[]::new);
    }

    public static TieBreakersDataRestRep[] mapArray(TieBreakers[] from) {
        if (from == null) {
            return null;
        }
        return Arrays.stream(from).map(ScaleIODataMapper::map).toArray(TieBreakersDataRestRep[]::new);
    }

    public static List<ScaleIOSDCDataRestRep> mapSdcList(List<ScaleIOSDC> from) {
        if (from == null) {
            return null;
        }
        return from.stream().map(ScaleIODataMapper::map).collect(Collectors.toList());
    }

    public static List<ScaleIOSDSDataRestRep> mapSdsList(List<ScaleIOSDS> from) {
        if (from == null) {
            return null;
        }
        return from.stream().map(ScaleIODataMapper::map).collect(Collectors.toList());
    }

    public static List<ScaleIODeviceDataRestRep> mapDeviceList(List<ScaleIODevice> from) {
        if (from == null) {
            return null;
        }
        return from.stream().map(ScaleIODataMapper::map).collect(Collectors.toList());
    }

    public static List<ScaleIOSDSIPDataRestRep> mapIpList(List<ScaleIOSDS.IP> from) {
        if (from == null) {
            return null;
        }
        return from.stream().map(ScaleIODataMapper::map).collect(Collectors.toList());
    }

    public static void map(ScaleIOSystem from, ScaleIOSystemDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setName(from.getName());
        to.setId(from.getId());
        to.setInstallId(from.getInstallId());
        to.setMdmClusterState(from.getMdmClusterState());
        to.setMdmMode(from.getMdmMode());
        to.setPrimaryMdmActorIpList(from.getPrimaryMdmActorIpList());
        to.setSecondaryMdmActorIpList(from.getSecondaryMdmActorIpList());
        to.setSystemVersionName(from.getSystemVersionName());
        to.setTiebreakerMdmIpList(from.getTiebreakerMdmIpList());
        to.setMdmCluster(map(from.getMdmCluster()));

    }

    public static void map(MdmCluster from, MdmClusterDataRestRep to) {
        if (from == null) {
            return;
        }
        to.setId(from.getId());
        to.setClusterMode(from.getClusterMode());
        to.setClusterState(from.getClusterState());
        to.setGoodNodesNum(from.getGoodNodesNum());
        to.setGoodReplicasNum(from.getGoodReplicasNum());
        to.setMaster(map(from.getMaster()));
        to.setSlaves(mapArray(from.getSlaves()));
        to.setTieBreakers(mapArray(from.getTieBreakers()));
    }

    public static void map(Master from, MasterDataRestRep to){
        if (from == null) {
            return ;
        }
        to.setId(from.getId());
        to.setName(from.getName());
        to.setIps(from.getIps());
        to.setManagementIPs(from.getManagementIPs());
        to.setPort(from.getPort());
        to.setRole(from.getRole());
        to.setVersionInfo(from.getVersionInfo());
    }

    public static void map(Slaves from, SlavesDataRestRep to){
        if (from == null) {
            return;
        }
        to.setId(from.getId());
        to.setName(from.getName());
        to.setIps(from.getIps());
        to.setManagementIPs(from.getManagementIPs());
        to.setPort(from.getPort());
        to.setRole(from.getRole());
        to.setVersionInfo(from.getVersionInfo());
        to.setStatus(from.getStatus());
    }

    public static void map(TieBreakers from, TieBreakersDataRestRep to) {
        if (from == null) {
            return;
        }
        to.setId(from.getId());
        to.setName(from.getName());
        to.setIps(from.getIps());
        to.setManagementIPs(from.getManagementIPs());
        to.setPort(from.getPort());
        to.setRole(from.getRole());
        to.setVersionInfo(from.getVersionInfo());
        to.setStatus(from.getStatus());
    }

    public static void map(ScaleIOSDC from, ScaleIOSDCDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setMdmConnectionState(from.getMdmConnectionState());
        to.setSdcGuid(from.getSdcGuid());
        to.setSdcIp(from.getSdcIp());
    }

    public static void map(ScaleIOSDS from, ScaleIOSDSDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setFaultSetId(from.getFaultSetId());
        to.setPort(from.getPort());
        to.setProtectionDomainId(from.getProtectionDomainId());
        to.setSdsState(from.getSdsState());
    }

    public static void map(ScaleIODevice from, ScaleIODeviceDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setDeviceCurrentPathName(from.getDeviceCurrentPathName());
        to.setStoragePoolId(from.getStoragePoolId());
    }

    private static void map(ScaleIOVolume from, ScaleIOVolumeDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setSizeInKb(from.getSizeInKb());
        to.setVtreeId(from.getVtreeId());
        to.setVolumeType(from.getVolumeType());
        to.setStoragePoolId(from.getStoragePoolId());
    }

    public static void map(ScaleIOStoragePool from, ScaleIOStoragePoolDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setProtectionDomainId(from.getProtectionDomainId());
        to.setCapacityAvailableForVolumeAllocationInKb(from.getCapacityAvailableForVolumeAllocationInKb());
        to.setMaxCapacityInKb(from.getMaxCapacityInKb());
        to.setNumOfVolumes(from.getNumOfVolumes());
    }

    public static void map(ScaleIOFaultSet from, ScaleIOFaultSetDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setProtectionDomain(from.getProtectionDomain());
    }

    public static void map(ScaleIOProtectionDomain from, ScaleIOProtectionDomainDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setId(from.getId());
        to.setName(from.getName());
        to.setProtectionDomainState(from.getProtectionDomainState());
        to.setSystemId(from.getSystemId());
    }

    public static void map(ScaleIOSDS.IP from, ScaleIOSDSIPDataRestRep to) {
        if (from == null) {
            return;
        }

        to.setIp(from.getIp());
        to.setRole(from.getRole());
    }
}
