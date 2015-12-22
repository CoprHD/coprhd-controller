package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VasaObjectMapper.toCapabilityProfile;

import java.net.URI;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.CapabilityProfile;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.vasa.CapabilityProfileBulkResponse;
import com.emc.storageos.model.vasa.CapabilityProfileCreateRequestParam;
import com.emc.storageos.model.vasa.CapabilityProfileCreateResponse;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/vasa/capabilityprofile")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class CapabilityProfileService extends AbstractCapabilityProfileService{

    private static final Logger _log = LoggerFactory.getLogger(CapabilityProfileService.class);
    
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public CapabilityProfileCreateResponse createCapabilityProfile(CapabilityProfileCreateRequestParam param) throws DatabaseException{
        ArgValidator.checkFieldNotEmpty(param.getName(), NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), DESCRIPTION);
//        CapabilityProfile capabilityProfile = prepareCapabilityProfile(param);
        VirtualPool capabilityProfile = prepareCapabilityProfile(param);
        return toCapabilityProfile(capabilityProfile);
    }

    private VirtualPool prepareCapabilityProfile(CapabilityProfileCreateRequestParam param) {
        VirtualPool capabilityProfile = new VirtualPool();
        capabilityProfile.setType(VirtualPool.Type.storagecontainer.name());
        populateCommonFields(capabilityProfile, param);
        populateCommonCapabilityProfileFields(capabilityProfile, param);
        _dbClient.createObject(capabilityProfile);
        return capabilityProfile;
    }
    
//    @GET
//    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
//    public CapabilityProfileBulkResponse getCapabilityProfiles(){
//        _log.info("@@@@@@@@ Getting Capability Profiles @@@@@@@@@@@");
//        List<URI> capabilityProfileUris = _dbClient.queryByType(VirtualPool.class, true);
//        List<VirtualPool> capabilityProfiles = _dbClient.queryObject(VirtualPool.class, capabilityProfileUris);
//        CapabilityProfileBulkResponse capabilityProfileBulkResponse = new CapabilityProfileBulkResponse();
//        if(null != capabilityProfiles){
//            for(VirtualPool capabilityProfile : capabilityProfiles){
//                if(capabilityProfile != null && capabilityProfile.getType().equals("storagecontainer")){
//                    capabilityProfileBulkResponse.getCapabilityProfiles().add(toCapabilityProfile(capabilityProfile));
//                }
//            }
//        }
//        return capabilityProfileBulkResponse;
//    }
}
