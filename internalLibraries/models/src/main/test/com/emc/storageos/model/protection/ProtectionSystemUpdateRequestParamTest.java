/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.protection;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.model.pools.VirtualArrayAssignments;

/**
 */
public class ProtectionSystemUpdateRequestParamTest {

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
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}

}
