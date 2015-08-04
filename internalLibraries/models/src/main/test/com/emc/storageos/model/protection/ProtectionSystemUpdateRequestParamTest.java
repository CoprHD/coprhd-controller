/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.protection;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.pools.VirtualArrayAssignments;

/**
 */
public class ProtectionSystemUpdateRequestParamTest {
	
	private static final Logger logger = LoggerFactory
            .getLogger(ProtectionSystemUpdateRequestParamTest.class);

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void test() {
        ProtectionSystemUpdateRequestParam param = new ProtectionSystemUpdateRequestParam();

        param.setIpAddress("foobarIP");
        param.setPassword("password");
        param.setPortNumber(344);
        param.setUserName("username");

        RPClusterVirtualArrayAssignmentChanges c1 = new RPClusterVirtualArrayAssignmentChanges();
        VirtualArrayAssignments va1 = new VirtualArrayAssignments();
        Set<String> vas = new HashSet<String>();
        vas.add("varray1");
        vas.add("varray2");
        va1.setVarrays(vas);
        c1.setClusterId("cluster1");
        c1.setAdd(va1);

        RPClusterVirtualArrayAssignmentChanges c2 = new RPClusterVirtualArrayAssignmentChanges();
        VirtualArrayAssignments va2 = new VirtualArrayAssignments();
        Set<String> vas2 = new HashSet<String>();
        vas2.add("varray3");
        vas2.add("varray4");
        va2.setVarrays(vas2);
        c2.setClusterId("cluster2");
        c2.setAdd(va2);

        Set<RPClusterVirtualArrayAssignmentChanges> cs = new HashSet<>();
        cs.add(c1);
        cs.add(c2);
        param.setVarrayChanges(cs);

        // create JAXB context and instantiate marshaller
        JAXBContext context;
        try {
            context = JAXBContext.newInstance(ProtectionSystemUpdateRequestParam.class);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            // Write to System.out
            m.marshal(param, System.out);
        } catch (JAXBException e) {
        	logger.error(e.getMessage(), e);
        }
    }

}
