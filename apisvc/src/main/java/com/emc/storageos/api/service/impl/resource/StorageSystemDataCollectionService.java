/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.ScaleIODataMapper;
import com.emc.storageos.model.collectdata.ScaleIOCollectDataParam;
import com.emc.storageos.model.collectdata.ScaleIODeviceDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDCDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDSDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSystemDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOVolumeDataRestRep;
import com.emc.storageos.scaleio.ScaleIOException;
import com.emc.storageos.scaleio.api.ScaleIOConstants;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClientFactory;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIODevice;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOFaultSet;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOProtectionDomain;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDC;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSDS;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOStoragePool;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSystem;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * APIs for stateless data collection of systems
 */
@Path("/collect-data")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
        Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
public class StorageSystemDataCollectionService {
    private static final Logger log = LoggerFactory.getLogger(StorageSystemDataCollectionService.class);
    private ScaleIORestClientFactory scaleIORestClientFactory;

    private String SCALEIO = "ScaleIO";

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }

    /**
     * Collect Data for ScaleIO system
     *
     * @param param ScaleIO discovery information
     * @return Data collected for ScaleIO system
     */
    @POST
    @Path("/scaleio")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ScaleIOSystemDataRestRep discoverScaleIO(ScaleIOCollectDataParam param) {
        log.debug("discovering ScaleIO: {}", param.getIPAddress());
        URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(param.getIPAddress(), param.getPortNumber()));
        ScaleIORestClient client =
                (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI,param.getUserName(),param.getPassword());
        ScaleIOSystemDataRestRep sio = null;
        try {
            //collect and map scaleIO system
            ScaleIOSystem system = client.getSystem();
            sio = ScaleIODataMapper.map(system);

            //collect sds,device,fault set, and protection domain data
            List<ScaleIOSDS> allSDS = client.queryAllSDS();

            Map<String,ScaleIOProtectionDomain> pdMap = null;
            List<ScaleIOProtectionDomain> pdList = client.getProtectionDomains();
            if (null != pdList) {
                pdMap = pdList.stream().collect(Collectors.toMap(ScaleIOProtectionDomain::getId, p -> p));
            }

            List<ScaleIOFaultSet> fsList = client.queryAllFaultSets();
            Map<String,ScaleIOFaultSet> fsMap = null;
            if (null != fsList) {
                fsMap = client.queryAllFaultSets().stream().collect(Collectors.toMap(ScaleIOFaultSet::getId, f -> f));
            }

            Map<String,ScaleIOStoragePool> spMap = client.queryAllStoragePools().stream().collect(
                    Collectors.toMap(ScaleIOStoragePool::getId,s->s));

            //map SDS data
            List<ScaleIOSDSDataRestRep> scaleIOSDSDataRestReps = new ArrayList<ScaleIOSDSDataRestRep>();
            for (ScaleIOSDS sds : allSDS) {
                ScaleIOSDSDataRestRep sdsData = ScaleIODataMapper.map(sds);

                //map device data
                List<ScaleIODevice> devices = client.getSdsDevices(sds.getId());
                List<ScaleIODeviceDataRestRep> scaleIODeviceDataRestReps = new ArrayList<ScaleIODeviceDataRestRep>();
                if (null != devices) {
                    for (ScaleIODevice device : devices) {
                        ScaleIODeviceDataRestRep scaleIODeviceDataRestRep = ScaleIODataMapper.map(device);
                        //map storagepool data
                        scaleIODeviceDataRestRep.setStoragePool(ScaleIODataMapper.map(spMap.get(device.getStoragePoolId())));
                        scaleIODeviceDataRestReps.add(scaleIODeviceDataRestRep);
                    }
                    sdsData.setDevices(scaleIODeviceDataRestReps);
                }

                //map fault set data
                if (null != fsMap) {
                    sdsData.setFaultSet(ScaleIODataMapper.map(fsMap.get(sds.getFaultSetId())));
                }

                //map protection domain and IP data
                if (null != pdMap) {
                    sdsData.setProtectionDomain(ScaleIODataMapper.map(pdMap.get(sds.getProtectionDomainId())));
                }
                sdsData.setIpList(ScaleIODataMapper.mapIpList(sds.getIpList()));

                scaleIOSDSDataRestReps.add(sdsData);
            }
            sio.setSdsList(scaleIOSDSDataRestReps);

            //collect and map SDC data
            List<ScaleIOSDC> allSDC = client.queryAllSDC();
            List<ScaleIOSDCDataRestRep> scaleIOSDCDataRestReps = new ArrayList<ScaleIOSDCDataRestRep>();
            for (ScaleIOSDC sdc : allSDC) {
                ScaleIOSDCDataRestRep sdcData = ScaleIODataMapper.map(sdc);
                // map device data
                List<ScaleIOVolume> volumes = client.getSdcVolumes(sdc.getId());
                List<ScaleIOVolumeDataRestRep> scaleIOVolumeDataRestReps = new ArrayList<ScaleIOVolumeDataRestRep>();
                if (null != volumes) {
                    for (ScaleIOVolume volume : volumes) {
                        ScaleIOVolumeDataRestRep scaleIOVolumeDataRestRep = ScaleIODataMapper.map(volume);
                        // map storagepool data
                        scaleIOVolumeDataRestRep.setStoragePool(ScaleIODataMapper.map(spMap.get(volume.getStoragePoolId())));
                        scaleIOVolumeDataRestReps.add(scaleIOVolumeDataRestRep);
                    }
                    sdcData.setVolumes(scaleIOVolumeDataRestReps);
                }
                scaleIOSDCDataRestReps.add(sdcData);
            }
            sio.setSdcList(scaleIOSDCDataRestReps);

        } catch(ScaleIOException e){
            log.error(String.format("Exception was encountered in the ScaleIO client when connecting to instance %s",
                    param.getIPAddress()), e);
            throw APIException.badRequests.storageSystemClientException(SCALEIO,e.getLocalizedMessage());
        } catch (JSONException e) {
            log.error(String.format("Exception was encountered when attempting to discover ScaleIO Instance %s",
                    param.getIPAddress()), e);
            throw APIException.badRequests.cannotDiscoverStorageSystemUnexpectedResponse(SCALEIO);
        }

        return sio;
    }
}
