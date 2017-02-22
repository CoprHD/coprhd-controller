package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.ScaleIODataMapper;
import com.emc.storageos.model.collectdata.ScaleIODeviceDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOSDSDataRestRep;
import com.emc.storageos.model.collectdata.ScaleIOCollectDataParam;
import com.emc.storageos.model.collectdata.ScaleIOSystemDataRestRep;
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
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by aquinn on 2/7/17.
 */

@Path("/collect-data")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN,
        Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SECURITY_ADMIN, Role.RESTRICTED_SECURITY_ADMIN })
public class StorageSystemDataCollectionService {
    private static final Logger log = LoggerFactory.getLogger(StorageSystemDataCollectionService.class);
    private ScaleIORestClientFactory scaleIORestClientFactory;

    public void setScaleIORestClientFactory(
            ScaleIORestClientFactory scaleIORestClientFactory) {
        this.scaleIORestClientFactory = scaleIORestClientFactory;
    }

    @POST
    @Path("/scaleio")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ScaleIOSystemDataRestRep discoverScaleIO(ScaleIOCollectDataParam param) {
        log.info("discovering ScaleIO: {}", param.getIPAddress());
        URI baseURI = URI.create(ScaleIOConstants.getAPIBaseURI(param.getIPAddress(), param.getPortNumber()));
        ScaleIORestClient client = (ScaleIORestClient) scaleIORestClientFactory.getRESTClient(baseURI,param.getUserName(),param.getPassword());
        ScaleIOSystemDataRestRep sio = null;

        List<ScaleIOSDS> allSDS = null;
        List<ScaleIOSDC> allSDC = null;
        Map<String,ScaleIOProtectionDomain> pdMap = null;
        Map<String,ScaleIOFaultSet> fsMap = null;
        Map<String,ScaleIOStoragePool> spMap = null;
        try {
            //collect and map scaleIO system
            ScaleIOSystem system = client.getSystem();
            sio = ScaleIODataMapper.map(system);

            //collect sds,device,fault set, and protection domain data
            allSDS = client.queryAllSDS();
            pdMap = client.getProtectionDomains().stream().collect(
                    Collectors.toMap(ScaleIOProtectionDomain::getId, p->p));
            List<ScaleIOFaultSet> fsList = client.queryAllFaultSets();
            if (null != fsList) {
                fsMap = client.queryAllFaultSets().stream().collect(
                        Collectors.toMap(ScaleIOFaultSet::getId, f -> f));
            }
            spMap = client.queryAllStoragePools().stream().collect(
                    Collectors.toMap(ScaleIOStoragePool::getId,s->s));

            //map SDS data
            List<ScaleIOSDSDataRestRep> scaleIOSDSDataRestReps = new ArrayList<ScaleIOSDSDataRestRep>();
            for (ScaleIOSDS sds : allSDS) {
                ScaleIOSDSDataRestRep sdsData = ScaleIODataMapper.map(sds);

                //map device data
                List<ScaleIODevice> devices = client.getSdsDevices(sds.getId());
                List<ScaleIODeviceDataRestRep> scaleIODeviceDataRestReps = new ArrayList<ScaleIODeviceDataRestRep>();
                for(ScaleIODevice device : devices){
                    ScaleIODeviceDataRestRep scaleIODeviceDataRestRep = ScaleIODataMapper.map(device);
                    //map storagepool data
                    scaleIODeviceDataRestRep.setStoragePool(ScaleIODataMapper.map(spMap.get(device.getStoragePoolId())));
                    scaleIODeviceDataRestReps.add(scaleIODeviceDataRestRep);
                }
                sdsData.setDevices(scaleIODeviceDataRestReps);

                //map fault set data
                if (null != fsMap) {
                    sdsData.setFaultSet(ScaleIODataMapper.map(fsMap.get(sds.getFaultSetId())));
                }

                //map protection domain data
                sdsData.setProtectionDomain(ScaleIODataMapper.map(pdMap.get(sds.getProtectionDomainId())));

                //map Ip data
                sdsData.setIpList(ScaleIODataMapper.mapIpList(sds.getIpList()));

                scaleIOSDSDataRestReps.add(sdsData);
            }
            sio.setSds(scaleIOSDSDataRestReps);

            //collect and map SDC data
            sio.setSdcs(ScaleIODataMapper.mapSdcList(client.queryAllSDC()));

        } catch (Exception e) {
            log.error("Exception: ", e);
        }

        return sio;
    }
}
