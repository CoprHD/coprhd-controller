/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.impl;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.db.client.model.AllowedGeoVersion;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DbKeyspace;
import com.emc.storageos.db.client.model.Name;

public class DbClientGeoVersionTest {
	private static final String VERSION_2_6 = "2.6";
	private static final String VERSION_2_5 = "2.5";
	private PropertyDescriptor dummyPd1 = null;
	private PropertyDescriptor dummyPd2 = null;
	private Method maxVersionMethod = null;
	private Method hasGeoVersionMethod = null;
	private DbClientImpl client1 = null;
	private DbClientImpl client2 = null;
	
	@Before
	public void setup(){
		try{		
			BeanInfo beanInfo = Introspector.getBeanInfo(DummyGeoObject.class);
			PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
			for(PropertyDescriptor pd : pds){
				if(pd.getDisplayName().equals("dummyField1")){
					this.dummyPd1 = pd;
				}
				if(pd.getDisplayName().equals("dummyField2")){
					this.dummyPd2 = pd;
				}
			}
			
			maxVersionMethod = DbClientImpl.class.getDeclaredMethod("getMaxGeoAllowedVersion", Class.class, PropertyDescriptor.class);
			this.maxVersionMethod.setAccessible(true);
			
			hasGeoVersionMethod = DbClientImpl.class.getDeclaredMethod("hasGeoVersionAnnotation", Class.class, PropertyDescriptor.class);
			this.hasGeoVersionMethod.setAccessible(true);
			client1 = new DbClientImpl();
			client2 = new DbClientImpl();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void shouldGetMaxAllowedGeoVersion(){
		DbClientImpl cf1 = new DbClientImpl();
		DbClientImpl cf2 = new DbClientImpl();
		try{
			String field1_version = (String) this.maxVersionMethod.invoke(cf1, new Object[]{DummyGeoObject.class, this.dummyPd1});
			Assert.assertEquals(VERSION_2_5, field1_version);
			
			String field2_version = (String) this.maxVersionMethod.invoke(cf2, new Object[]{DummyGeoObject.class, this.dummyPd2});
			Assert.assertEquals(VERSION_2_6, field2_version);
		}catch (Exception e){
			e.printStackTrace();
		}
	}
	
	@Test
	public void testHasGeoVersionAnnotation(){
		try{
			Boolean hasGeoVersion = (Boolean) this.hasGeoVersionMethod.invoke(client1, new Object[]{DummyGeoObject.class, this.dummyPd1});
			Assert.assertTrue(hasGeoVersion);

			hasGeoVersion = (Boolean) this.hasGeoVersionMethod.invoke(client2, new Object[]{DummyGeoObject.class, this.dummyPd2});
			Assert.assertTrue(hasGeoVersion);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	@SuppressWarnings("serial")
	@AllowedGeoVersion(version=VERSION_2_5)
	@DbKeyspace(DbKeyspace.Keyspaces.GLOBAL)
	private class DummyGeoObject extends DataObject{
		private String dummyField1;
		private String dummyField2;

		@AllowedGeoVersion(version=VERSION_2_5)
	    @Name("dummy1")
		public String getDummyField1() {
			return dummyField1;
		}

		public void setDummyField1(String dummyField1) {
			this.dummyField1 = dummyField1;
		}

		@AllowedGeoVersion(version=VERSION_2_6)
	    @Name("dummy2")
		public String getDummyField2() {
			return dummyField2;
		}

		public void setDummyFiel2(String dummyField2) {
			this.dummyField2 = dummyField2;
		}
	}
	private class DummyLocalObject extends DataObject{
	}
	
}
