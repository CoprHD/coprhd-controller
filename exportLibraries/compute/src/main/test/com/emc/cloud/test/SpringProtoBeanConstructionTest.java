/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@ContextConfiguration(locations={"classpath:applicationContext.xml"})
public class SpringProtoBeanConstructionTest extends
		AbstractTestNGSpringContextTests {
	
	@Autowired
	ExampleProtoBeanFactory exampleProtoBeanFactory;
	
	@Test(groups="runByDefault")
	public void testBeanCreation(){
		assertNotNull(applicationContext);
		for (int j = 0; j < 2; j++) {
			for (int i = 0; i < 10; i++) {
				ExampleProtoBean bean = exampleProtoBeanFactory
						.createExampleProtoBean("Texxt : " + i);
				System.out.println(bean);
			}
		}
	}

}
