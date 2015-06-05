/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.api;

import static com.emc.sa.api.mapper.UserPreferencesMapper.map;
import static com.emc.sa.api.mapper.UserPreferencesMapper.updateObject;
import static com.emc.storageos.db.client.URIUtil.uri;

import javax.annotation.PostConstruct;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.sa.api.utils.ValidationUtils;
import com.emc.sa.catalog.UserPreferenceManager;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.catalog.impl.SearchConstants;
import com.emc.vipr.model.catalog.UserPreferencesRestRep;
import com.emc.vipr.model.catalog.UserPreferencesUpdateParam;

@Path("/user/preferences")
public class UserPreferenceService extends CatalogResourceService {

    private static final Logger log = Logger.getLogger(UserPreferenceService.class);

    private static final String EVENT_SERVICE_TYPE = "user-preferences";
    
    @Autowired
    private UserPreferenceManager userPreferenceManager;

    @PostConstruct
    public void init() {
        log.info("Initializing UserPreferenceService");
    }
    
    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }        
    
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    public UserPreferencesRestRep get(@DefaultValue("") @QueryParam(SearchConstants.USER_NAME_PARAM) String username) {

        StorageOSUser user = getUserFromContext();
        if (StringUtils.isBlank(username)) {
            username = user.getUserName();
        }
        verifyAuthorized(username, user);
        
        UserPreferences userPreferences = userPreferenceManager.getPreferences(username);

        return map(userPreferences);
    }
    
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("")
    public UserPreferencesRestRep update(UserPreferencesUpdateParam param) {
        
        StorageOSUser user = getUserFromContext();
        String username = param.getUsername();
        if (StringUtils.isBlank(username)) {
            username = user.getUserName();
        }
        verifyAuthorized(username, user);        
        
        UserPreferences userPreferences = userPreferenceManager.getPreferences(username);
        
        validateParam(param, userPreferences);
        
        updateObject(userPreferences, param);

        userPreferenceManager.updatePreferences(userPreferences);
        
        auditOpSuccess(OperationTypeEnum.UPDATE_USER_PREFERENCES, userPreferences.auditParameters());
        
        userPreferences = userPreferenceManager.getPreferences(userPreferences.getUserId());
        
        return map(userPreferences);
    }  
    
    private void validateParam(UserPreferencesUpdateParam input, UserPreferences existing) {
        if (StringUtils.isNotBlank(input.getEmail())) {
            for (String email : StringUtils.split(input.getEmail(), ",")) {
                email = StringUtils.trim(email);
                if (ValidationUtils.isValidEmail(email) == false) {
                    throw APIException.badRequests.propertyValueTypeIsInvalid("email", "email");
                }
            }
        }
    }    
    
    protected void verifyAuthorized(String username, StorageOSUser user) {
        if(!(username.equals(user.getUserName()) || isSystemAdminOrMonitorUser() ||
                        _permissionsHelper.userHasGivenRole(user, uri(user.getTenantId()), Role.TENANT_ADMIN))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }    

}
