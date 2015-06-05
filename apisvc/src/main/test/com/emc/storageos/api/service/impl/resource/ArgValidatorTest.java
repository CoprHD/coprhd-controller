/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import static org.apache.commons.lang.StringUtils.EMPTY;

import java.net.URI;

import org.junit.Assert;

import org.junit.Test;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.NotFoundException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class ArgValidatorTest extends Assert {
    
    @Test
    public void TestCheckUri_ValidUri(){
        ArgValidator.checkUri(URI.create("urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"));
    }
    
    @Test(expected=APIException.class)
    public void TestCheckUri_BadScheme(){
        try {
            ArgValidator.checkUri(URI.create("other:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"));
        } catch (APIException apiException) {
            assertEquals(ServiceCode.API_PARAMETER_INVALID_URI, apiException.getServiceCode());
            assertEquals("Parameter other:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197: is not a valid URI", apiException.getLocalizedMessage());
            throw apiException;
        }
    }
    
    @Test(expected=APIException.class)
    public void TestCheckUri_BadSchemeSpecificPart(){
        try {
            ArgValidator.checkUri(URI.create("urn:other:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"));
        } catch (APIException apiException) {
            assertEquals(ServiceCode.API_PARAMETER_INVALID_URI, apiException.getServiceCode());
            assertEquals("Parameter urn:other:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197: is not a valid URI", apiException.getLocalizedMessage());
            throw apiException;
        }
    }
    
    @Test(expected=APIException.class)
    public void TestCheckUri_EmptyUri(){
        try {
            ArgValidator.checkUri(URI.create(EMPTY));
        } catch (APIException apiException) {
            assertEquals(ServiceCode.API_PARAMETER_INVALID_URI, apiException.getServiceCode());
            assertEquals("Parameter  is not a valid URI", apiException.getLocalizedMessage());
            throw apiException;
        }
    }
    
    @Test(expected=APIException.class)
    public void TestCheckUri_NullUri(){
        try {
            ArgValidator.checkUri(null);
        } catch (APIException apiException) {
            assertEquals(ServiceCode.API_PARAMETER_INVALID_URI, apiException.getServiceCode());
            assertEquals("Parameter null is not a valid URI", apiException.getLocalizedMessage());
            throw apiException;
        }
    }
    
    @Test
    public void TestCheckFieldNotNull_PositiveCase(){
        final Object mockObject = new Object();
        ArgValidator.checkFieldNotNull(mockObject, "mock");
    }
    
    @Test(expected=BadRequestException.class)
    public void TestCheckFieldNotNull_NegativeCase(){
        try {
            ArgValidator.checkFieldNotNull(null, "mock");
        } catch (BadRequestException e) {
            assertEquals(ServiceCode.API_PARAMETER_MISSING, e.getServiceCode());
            assertEquals("Required parameter mock was missing or empty", e.getLocalizedMessage());
            throw e;
        }
    }
    
    @Test
    public void TestCheckEntity_PositiveCase(){
        ArgValidator.checkEntity(new StorageSystem(), URI.create("urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"), false);
    }

    @Test(expected=NotFoundException.class)
    public void TestCheckEntity_NegativeCase(){
        ArgValidator.checkEntity(null, URI.create("urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"), true);
    }

    @Test(expected=BadRequestException.class)
    public void TestCheckEntity_InactiveEntity_badrequest(){
        try {
            DataObject object = new DataObject(){};
            object.setId(URI.create("urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"));
            object.setInactive(true);
            ArgValidator.checkEntity(object, object.getId(), false);
        } catch (APIException bre) {
            assertEquals(ServiceCode.API_PARAMETER_INACTIVE, bre.getServiceCode());
            assertEquals("Entity with the given id urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197: is inactive and marked for deletion", bre.getLocalizedMessage());
            throw bre;
        }
    }

    @Test(expected=NotFoundException.class)
    public void TestCheckEntity_InactiveEntity_notfound(){
        try {
            DataObject object = new DataObject(){};
            object.setId(URI.create("urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197:"));
            object.setInactive(true);
            ArgValidator.checkEntity(object, object.getId(), true);
        } catch (APIException bre) {
            assertEquals(ServiceCode.API_URL_ENTITY_INACTIVE, bre.getServiceCode());
            assertEquals("Entity specified in URL with the given id urn:storageos:StorageSystem:2b91947d-749f-4356-aad7-dcd7f7906197: is inactive and marked for deletion", bre.getLocalizedMessage());
            throw bre;
        }
    }
}
