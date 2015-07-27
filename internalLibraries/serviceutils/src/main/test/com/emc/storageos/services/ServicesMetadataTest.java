/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services;

import java.util.*;

import org.junit.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Sets;

public class ServicesMetadataTest {

    private static Set<ServiceMetadata> _controlNodeServices;
    private static Set<ServiceMetadata> _extraNodeServices;

    @BeforeClass
    public static void populateServices() throws Exception {
        ServiceMetadata apisvc = new ServiceMetadata();
        apisvc.setName("apisvc");
        apisvc.setPort(9080);
        apisvc.setIsControlNodeService(true);
        apisvc.setRoles("control");
        ServiceMetadata syssvc = new ServiceMetadata();
        syssvc.setName("syssvc");
        syssvc.setPort(9998);
        syssvc.setIsControlNodeService(true);
        syssvc.setIsExtraNodeService(true);
        syssvc.setRoles("control object");
        ServiceMetadata datasvc = new ServiceMetadata();
        datasvc.setName("datasvc");
        datasvc.setPort(1001);
        datasvc.setIsExtraNodeService(true);
        datasvc.setRoles("object");
        LinkedHashMap<String, ServiceMetadata> services = new LinkedHashMap<String,
        ServiceMetadata>();
        services.put(apisvc.getName(), apisvc);
        services.put(syssvc.getName(), syssvc);
        services.put(datasvc.getName(), datasvc);
        
        RoleMetadata controlRole = new RoleMetadata();
        controlRole.setName("control");
        RoleMetadata dataRole = new RoleMetadata();
        dataRole.setName("object");
        LinkedHashMap<String, RoleMetadata> roles = new LinkedHashMap<String,
        RoleMetadata>();
        roles.put(controlRole.getName(), controlRole);
        roles.put(dataRole.getName(), dataRole);
        
        ServicesMetadata _servicesMetadata = new ServicesMetadata();
        _servicesMetadata.setServiceMetadataMap(services);
        _servicesMetadata.setRoleMetadataMap(roles);
        _servicesMetadata.afterPropertiesSet();
    }

    @Test
    public void testServicesOrder(){
        Map<String, ServiceMetadata> services = ServicesMetadata.getServiceMetadataMap();
        Assert.assertNotNull(services);
        int cntr = 0;
        for(String key: services.keySet()){
            switch (cntr++){
                case 0: Assert.assertEquals(key, "apisvc");
                        break;
                case 1: Assert.assertEquals(key, "syssvc");
                        break;
                case 2: Assert.assertEquals(key, "datasvc");
                        break;
                default: Assert.fail();
            }
        }
    }

    @Test
    public void testControlNodeServices(){
        List<String> services = ServicesMetadata.getControlNodeServiceNames();
        Assert.assertNotNull(services);
        int cntr = 0;
        for(String key:services){
            switch (cntr++){
                case 0: Assert.assertEquals(key, "apisvc");
                    break;
                case 1: Assert.assertEquals(key, "syssvc");
                    break;
                default: Assert.fail();
            }
        }
    }

    @Test
    public void testExtraNodeServices(){
        List<String> services = ServicesMetadata.getExtraNodeServiceNames();
        Assert.assertNotNull(services);
        int cntr = 0;
        for(String key:services){
            switch (cntr++){
                case 0: Assert.assertEquals(key, "syssvc");
                    break;
                case 1: Assert.assertEquals(key, "datasvc");
                    break;
                default: Assert.fail();
            }
        }
    }

    @Test
    public void testRoleIndex() {
        Set<String> controlServices = Sets.newHashSet(
                ServicesMetadata.getRoleServiceNames("control"));
        Set<String> dataServices = Sets.newHashSet(
                ServicesMetadata.getRoleServiceNames("object"));
        Set<String> bothServices = Sets.newHashSet(
                ServicesMetadata.getRoleServiceNames("control", "object"));

        Assert.assertEquals(0, Sets.symmetricDifference(controlServices, 
                Sets.newHashSet("apisvc","syssvc")).size());
        Assert.assertEquals(0, Sets.symmetricDifference(dataServices, 
                Sets.newHashSet("datasvc","syssvc")).size());
        Assert.assertEquals(0, Sets.symmetricDifference(bothServices, 
                Sets.newHashSet("datasvc","apisvc","syssvc")).size());
    }
}
