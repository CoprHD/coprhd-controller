/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.platform.clientlib;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import javax.xml.bind.JAXBElement;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;

import com.emc.cloud.platform.ucs.in.model.AaaLogin;
import com.emc.cloud.platform.ucs.in.model.AaaLogout;
import com.emc.cloud.platform.ucs.in.model.ObjectFactory;
import com.emc.cloud.platform.ucs.model.ext.OutStatus;

public class ClientHttpMethodsImpl implements ClientHttpMethods, InitializingBean,ApplicationContextAware {
	
	@Autowired
	ApplicationContext applicationContext;
	
	private String httpReqFactoryBeanRef;
	private ClientHttpRequestFactory requestFactory;
	String serviceURI;

	private ObjectFactory objectFactory = new ObjectFactory();
	
	/**
	 * @return the httpReqFactoryBeanRef
	 */
	public String getHttpReqFactoryBeanRef() {
		return httpReqFactoryBeanRef;
	}

	/**
	 * @param httpReqFactoryBeanRef the httpReqFactoryBeanRef to set
	 */
	public void setHttpReqFactoryBeanRef(String httpReqFactoryBeanRef) {
		this.httpReqFactoryBeanRef = httpReqFactoryBeanRef;
	}

	public ClientHttpMethodsImpl(String serviceURI,String username,String password) throws ClientGeneralException {
		this.serviceURI = serviceURI;
		
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, password));
	}
	
	private Authentication endpointLogin(String serviceURI, String username,String password) throws ClientGeneralException {
		
			
		
			Authentication authentication = null;

			ClientHttpRequest httpRequest = requestFactory.create();
			
			AaaLogin aaaLogin = new AaaLogin();
			aaaLogin.setInName(username);
			aaaLogin.setInPassword(password);
			com.emc.cloud.platform.ucs.out.model.AaaLogin response = httpRequest.httpPostXMLObject(getServiceURI(),objectFactory.createAaaLogin(aaaLogin),com.emc.cloud.platform.ucs.out.model.AaaLogin.class);

			
			if(response != null && response.getOutCookie() != null && !response.getOutCookie().isEmpty()){
				authentication = new UsernamePasswordAuthenticationToken(username, password,Collections.EMPTY_LIST);
				((UsernamePasswordAuthenticationToken)authentication).setDetails(response);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}else{
				throw new ClientGeneralException(ClientMessageKeys.UNAUTHORIZED,new String[]{getServiceURI(),"","Unable to authenticate username/credentials pair"});
			}
			return authentication;
		
	}

	/*
	 * Delegate such that there is only one injection required in the services
	 */
	public String getServiceURI(){
		return this.serviceURI;
	}
	
	
	public void setServiceURI(String serviceURI){
		this.serviceURI = serviceURI;
	}



	private static final Logger LOGGER = LoggerFactory.getLogger(ClientHttpMethodsImpl.class);
	
	    //=======================================================================================
		// Entity routines
		//=======================================================================================
	
		public <T> T postEntity(JAXBElement<?> jaxbElement,Class<T> returnType) throws ClientGeneralException{
			
			
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			
			Assert.notNull(authentication,"No credentials provided for call");
			
			if(authentication instanceof UsernamePasswordAuthenticationToken && !authentication.isAuthenticated()){
				
				Assert.notNull(authentication.getName(),"No username provided for call");
				Assert.notNull(authentication.getCredentials(),"No password provided for call");
				
				authentication = endpointLogin(getServiceURI(), authentication.getName(), authentication.getCredentials().toString());
			}
			
	        T result = null;
	        try {
	            ClientHttpRequest httpRequest = requestFactory.create();
	            
	            if(authentication instanceof UsernamePasswordAuthenticationToken && authentication.isAuthenticated()){	            
	            	com.emc.cloud.platform.ucs.out.model.AaaLogin loginResponse = (com.emc.cloud.platform.ucs.out.model.AaaLogin)authentication.getDetails();
	            	try {
	            		if(loginResponse != null){
						BeanUtils.setProperty(jaxbElement.getValue(), "cookie", loginResponse.getOutCookie());
	            		}
					} catch (IllegalAccessException e) {
						LOGGER.error("Unable to set the cookie on object type: " + jaxbElement.getValue(), e);
						throw new ClientGeneralException(ClientMessageKeys.MODEL_EXCEPTION);
					} catch (InvocationTargetException e) {
						LOGGER.error("Unable to set the cookie on object type: " + jaxbElement.getValue(), e);
						throw new ClientGeneralException(ClientMessageKeys.MODEL_EXCEPTION);
					}
	            }
	            
	            result = httpRequest.httpPostXMLObject(getServiceURI(),jaxbElement,returnType);
	        } catch (ClientGeneralException e) {
	            LOGGER.debug(e.getLocalizedMessage(),e);
	            throw e;
	        }
	        return result;
		}
		
		
	    @Override
	    public void afterPropertiesSet() throws Exception {
	        requestFactory = (ClientHttpRequestFactory)applicationContext.getBean(httpReqFactoryBeanRef);
	        if (requestFactory == null) {
	            throw new IllegalArgumentException("httpMethods object requires non-null requestFactory");
	        }
	    }

		@Override
		public void setApplicationContext(ApplicationContext arg0)
				throws BeansException {
			this.applicationContext = arg0;
			
		}

		@Override
		public void close() throws ClientGeneralException {
			
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			
			if(authentication == null){
				/**
				 * No-op - There's no security context to log out!
				 */
				return;
			}else if(authentication instanceof UsernamePasswordAuthenticationToken){
					endpointLogout(authentication);
			}
		}

		private void endpointLogout(Authentication authentication) throws ClientGeneralException {

			if(authentication == null){
				return;
			}
			
			Assert.isInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
			Assert.isTrue(authentication.isAuthenticated());
			Assert.isTrue(authentication.getDetails() != null);
			Assert.isInstanceOf(com.emc.cloud.platform.ucs.out.model.AaaLogin.class,authentication.getDetails());
			
			AaaLogout aaaLogout = new AaaLogout();
			aaaLogout.setInCookie(((com.emc.cloud.platform.ucs.out.model.AaaLogin)authentication.getDetails()).getOutCookie());
			ClientHttpRequest httpRequest = requestFactory.create();
			com.emc.cloud.platform.ucs.out.model.AaaLogout response = httpRequest.httpPostXMLObject(getServiceURI(), objectFactory.createAaaLogout(aaaLogout),com.emc.cloud.platform.ucs.out.model.AaaLogout.class);
			
			if(OutStatus.SUCCESS.getValue().equals(response.getOutStatus())){
				return;
			}else{
				throw new ClientGeneralException(ClientMessageKeys.SECURITY_EXCEPTION,new String[]{"Unable to log out session : " + authentication.getDetails()});
			}
			
		}
}
