/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.common;

import org.junit.Assert;
import org.junit.Test;

public class VdcVersionComparatorTest {

	private static final String VDC1_VERSION = "2.2";
	private static final String VDC2_VERSION = "2.5";	
		
    @Test
    public void shouldVersion25GreaterThan22(){
    	String version2_2 = VDC1_VERSION;
    	String version2_5 = VDC2_VERSION;
    	Assert.assertTrue(VdcUtil.VdcVersionComparator.compare(version2_5, version2_2)>0);
    }
    
    @Test
    public void shouldVersion221GreaterThan22(){
    	String version2_2_1 = "2.2.1";
    	String version2_2 = VDC1_VERSION;
		Assert.assertTrue(VdcUtil.VdcVersionComparator.compare(version2_2_1, version2_2)>0);
    }

}
