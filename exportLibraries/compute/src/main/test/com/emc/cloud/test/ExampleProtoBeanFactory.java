/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ExampleProtoBeanFactory implements ApplicationContextAware {
	
	private static int factoryMethodInvocationCount = 0;
	
	@Autowired
	ApplicationContext applicationContext;
	
	private static final Map<String, ExampleProtoBean> textToExampleProtoBeanMap = Collections.synchronizedMap(new HashMap<String, ExampleProtoBean>());

	@Override
	public void setApplicationContext(ApplicationContext arg0)
			throws BeansException {
		this.applicationContext = arg0;

	}
	
	public ExampleProtoBean createExampleProtoBean(String text){
		factoryMethodInvocationCount++;
		System.out.println("Number of times factory method invoked : " + factoryMethodInvocationCount);
		if(textToExampleProtoBeanMap.get(text) != null){
			return textToExampleProtoBeanMap.get(text);
		}
		ExampleProtoBean bean = (ExampleProtoBean)applicationContext.getBean("exampleProtoBean", text);
		textToExampleProtoBeanMap.put(text, bean);
		return bean;
	}

}
