/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class ClientHttpMethodsFactory implements ApplicationContextAware {
	
	@Autowired
	ApplicationContext applicationContext;
	
	private String httpMethodsBeanNameRef;
	
	/**
	 * @return the httpMethodsBeanNameRef
	 */
	public String getHttpMethodsBeanNameRef() {
		return httpMethodsBeanNameRef;
	}

	/**
	 * @param httpMethodsBeanNameRef the httpMethodsBeanNameRef to set
	 */
	public void setHttpMethodsBeanNameRef(String httpMethodsBeanNameRef) {
		this.httpMethodsBeanNameRef = httpMethodsBeanNameRef;
	}

	/**
	 * Implement timeout and expiration
	 */
	private static final Map<String, ClientHttpMethods> uriToHttpMethodsMap = Collections.synchronizedMap(new HashMap<String, ClientHttpMethods>());
	
	public ClientHttpMethods createClientHttpMethods(String serviceURI,String username,String password) throws ClientGeneralException{
		
		
		String mapKey = new StringBuffer().append(serviceURI).append(username).append(password).toString();
		
		if(uriToHttpMethodsMap.get(mapKey) != null){
			if(SecurityContextHolder.getContext().getAuthentication() == null || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()){
				SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, password));
			}
			return uriToHttpMethodsMap.get(mapKey);
		}
		
		ClientHttpMethods clientHttpMethods = (ClientHttpMethods)applicationContext.getBean(httpMethodsBeanNameRef,serviceURI,username,password);
		uriToHttpMethodsMap.put(mapKey, clientHttpMethods);
		return clientHttpMethods;
	}

	@Override
	public void setApplicationContext(ApplicationContext arg0)
			throws BeansException {
		this.applicationContext = arg0;

	}
	
	public void closeClientHttpMethods(ClientHttpMethods clientHttpMethods) throws ClientGeneralException{
		clientHttpMethods.close();
		// clear cache
		String key = null;
		for (Entry<String, ClientHttpMethods> entry : uriToHttpMethodsMap.entrySet()) {
			if (entry.getValue().equals(clientHttpMethods)) {
				key = entry.getKey();
				break;
			}
		}
		if (key != null) {
			uriToHttpMethodsMap.remove(key);
		}
	}

}
