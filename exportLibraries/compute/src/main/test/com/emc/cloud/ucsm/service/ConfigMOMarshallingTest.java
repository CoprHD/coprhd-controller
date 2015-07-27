/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import com.emc.cloud.platform.clientlib.ClientGeneralException;
import com.emc.cloud.platform.ucs.in.model.ConfigConfMo;
import com.emc.cloud.platform.ucs.in.model.ConfigConfMos;
import com.emc.cloud.platform.ucs.in.model.ConfigConfig;
import com.emc.cloud.platform.ucs.in.model.LsPower;
import com.emc.cloud.platform.ucs.in.model.ObjectFactory;
import com.emc.cloud.ucsm.service.UCSMService;

@ContextConfiguration(locations={"classpath:applicationContext.xml"})
public class ConfigMOMarshallingTest extends AbstractTestNGSpringContextTests {
	
	@Autowired
	UCSMService ucsmService;
	
	@Autowired
	JAXBContext ucsInContext;
	
	ObjectFactory factory = new ObjectFactory();
		
	
	@Test(groups="onDemand")
	public void testMarshalling() throws ClientGeneralException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, JAXBException{
		
		String lsServerDN = "org-root/ls-vlad2-os-install";
		String powerState = "up";
		
		ConfigConfMo configConfMo = new ConfigConfMo();
		configConfMo.setInHierarchical("true");
		
		com.emc.cloud.platform.ucs.in.model.LsServer lsServer = new com.emc.cloud.platform.ucs.in.model.LsServer();
		lsServer.setDn(lsServerDN);
		
		LsPower lsPower = new LsPower();
		lsPower.setRn("power");
		lsPower.setState(powerState);
		
		lsServer.getContent().add(factory.createLsPower(lsPower));
		
		
		ConfigConfig configConfig = new ConfigConfig();
		configConfig.setManagedObject(factory.createLsServer(lsServer));
		
		configConfMo.getContent().add(factory.createConfigConfMoInConfig(configConfig));
		
		JAXBElement<ConfigConfMo> jaxbElement = factory.createConfigConfMo(configConfMo);
		
		StringWriter writer = new StringWriter();
		if(jaxbElement != null) {
			Marshaller marshaller = ucsInContext.createMarshaller();
			marshaller.marshal(jaxbElement, writer);
		}
		String payload = writer.toString();
		
		System.out.println("Payload : " + payload);

		
	}	

}
