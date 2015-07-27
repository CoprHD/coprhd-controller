/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.cloud.platform.clientlib;

import java.io.IOException;
import java.io.StringWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHttpRequest {
    private HttpClient httpClient;
    private JAXBContext unmarshallingJaxbContext;
    private JAXBContext marshallingJaxbContext;
    public static final Logger log = LoggerFactory.getLogger(ClientHttpRequest.class);
    
    @SuppressWarnings("unused")
    private ClientHttpRequest() {
        //NOP - use the factory
    }

    ClientHttpRequest(HttpClient httpClient,JAXBContext mjaxbContext,JAXBContext umjaxbContext) throws ClientGeneralException {  
        this.httpClient = httpClient;
        this.marshallingJaxbContext = mjaxbContext;
        this.unmarshallingJaxbContext = umjaxbContext;
        if (this.httpClient == null) {
            throw new IllegalArgumentException("request object requires a non-null httpClient");           
        }       
        if (this.marshallingJaxbContext == null) {
            throw new IllegalArgumentException("request object requires a non-null marhsallingJaxbContext");
        }
        if (this.unmarshallingJaxbContext == null) {
            throw new IllegalArgumentException("request object requires a non-null unmarshallingJaxbContext");
        }
        
    }

    
    @Deprecated
    public String httpGetString(String url) throws ClientGeneralException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Content-Type", "application/json");
        get.setHeader("ACCEPT", "application/json");
        try {
            String response = httpClient.execute(get, new StringHttpResponseHandler());
            return response;
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
        }
    }

	public Object httpGetXML(String url) throws ClientGeneralException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Content-Type", "application/xml");
        get.setHeader("ACCEPT", "application/xml");
        
        log.debug("GET: " + url);
        try {
            Object response = httpClient.execute(get, new XMLHttpResponseHandler(unmarshallingJaxbContext, "GET " + url, ""));
            return response;
        } catch (ClientHttpResponseException ex) {
            throw new ClientResponseException(ex);
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION,new String[] {ex.getMessage()}, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION,new String[] {ex.getMessage()}, ex);
        }
	}
	
	
	
	public String httpGetObjectXML(String url) throws ClientGeneralException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Content-Type", "application/xml");
        get.setHeader("ACCEPT", "application/xml");
        
        log.debug("GET: " + url);
        try {
            HttpResponse response = httpClient.execute(get);
            return EntityUtils.toString(response.getEntity());
        } catch (ClientHttpResponseException ex) {
            throw getMaskedDetailClientGeneralException(ex);
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
        }

	}
	
	
	public <T> T httpPostXMLObject(String url,JAXBElement<?> jaxbElement,Class<T> returnType) throws ClientGeneralException {
		HttpPost post = new HttpPost(url);
		post.setHeader("Content-Type", "application/xml");
		post.setHeader("ACCEPT", "application/xml");
		log.debug("POST: " + url);
		try {
			StringWriter writer = new StringWriter();
			if(jaxbElement != null) {
				Marshaller marshaller = marshallingJaxbContext.createMarshaller();
				marshaller.marshal(jaxbElement, writer);
			}
			String payload = writer.toString();
			if(payload != null && payload.length() != 0){
				post.setEntity(new StringEntity(payload));
		        // log payloads at DEBUG level only because they may contain passwords				
				log.debug("Payload:\n" + maskPayloadCredentials(payload));
			}
			return httpClient.execute(post,new XMLHttpResponseHandler<T>(unmarshallingJaxbContext, "POST " + url, payload != null ? payload.toString() : ""));
		}
          catch (ClientHttpResponseException ex) {
              throw getMaskedDetailClientGeneralException(ex);
		} catch (ClientProtocolException ex) {
			throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
		} catch (IOException ex) {
			throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
		} catch (JAXBException ex) {
			throw new ClientGeneralException(ClientMessageKeys.MODEL_EXCEPTION, ex);
		}
	}
		
	public Object httpPostXMLObject(String url) throws ClientGeneralException {
        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/xml");
        post.setHeader("ACCEPT", "application/xml");
        log.debug("POST: " + url);
        try {
            Object response = httpClient.execute(post,new XMLHttpResponseHandler(unmarshallingJaxbContext, "POST " + url, ""));
            return response;
        } catch (ClientHttpResponseException ex) {
            throw getMaskedDetailClientGeneralException(ex);
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
        }
	}
	
	public Object httpPutXMLObject(String url,JAXBElement<?> jaxbElement) throws ClientGeneralException {
        HttpPut post = new HttpPut(url);
        post.setHeader("Content-Type", "application/xml");
        post.setHeader("ACCEPT", "application/xml");
        log.debug("PUT: " + url);
        try {
            Marshaller marshaller = marshallingJaxbContext.createMarshaller();
            StringWriter writer = new StringWriter();
            marshaller.marshal(jaxbElement, writer);
            
            String payload = writer.toString();
            if(payload != null && payload.length() != 0){
                post.setEntity(new StringEntity(payload));
                // log payloads at DEBUG level only because they may contain passwords              
                log.debug("Payload:\n" + maskPayloadCredentials(payload));
            }
            Object response = httpClient.execute(post,new XMLHttpResponseHandler(unmarshallingJaxbContext, "PUT " + url, payload != null ? payload.toString() : ""));
            return response;
        } catch (ClientHttpResponseException ex) {
            throw getMaskedDetailClientGeneralException(ex);
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
        } catch (JAXBException ex) {
            throw new ClientGeneralException(ClientMessageKeys.MODEL_EXCEPTION, ex);
        }
    }
	
	public Object httpDeleteXMLObject(String url) throws ClientGeneralException {
        HttpDelete delete = new HttpDelete(url);
        delete.setHeader("Content-Type", "application/xml");
        delete.setHeader("ACCEPT", "application/xml");
        log.debug("DELETE: " + url);
        try {
            Object response = httpClient.execute(delete, new XMLHttpResponseHandler(unmarshallingJaxbContext, "DELETE " + url, ""));
            return response;
        } catch (ClientHttpResponseException ex) {
            throw getMaskedDetailClientGeneralException(ex);
        } catch (ClientProtocolException ex) {
            throw new ClientGeneralException(ClientMessageKeys.CLIENT_PROTOCOL_EXCEPTION, ex);
        } catch (IOException ex) {
            throw new ClientGeneralException(ClientMessageKeys.IO_EXCEPTION, ex);
        }
    }

    private String maskPayloadCredentials(String protectPayload){
        /*Properties of the payload are pattern matched based on the below keywords
          And then masked beyond recognition. This is protect the passwords and session cookies
          from appearing in clear text.
        */
        String maskProperties[] = new String []{"inPassword","inCookie","cookie","password","Cookie","Password"};

        String pattern="";
        for (int index =0 ;index<maskProperties.length;index++){
            if(index ==0){
                //start building the pattern.
                pattern+="(";
            }
            pattern+=maskProperties[index];
            if(index == maskProperties.length-1){
                //end the pattern.
                pattern+=")\\S+(\\s|$)";
            }else{
                pattern+="|";
            }
        }
        log.debug("Pattern for masking sensitive payload info {}", pattern);
        return protectPayload.replaceAll(pattern,"$1=\"masked\"");
    }

    private ClientGeneralException getMaskedDetailClientGeneralException(ClientHttpResponseException ex){
        /*
            Returns a ClientGeneralException with masked details for a ClientHttpResponseException
        */
        ClientMessageKeys key = null;
        ClientGeneralException exception = null;
        if(ex.bcode!=null){
            try{
                key = ClientMessageKeys.byErrorCode(Integer.parseInt(ex.bcode));
                exception = new ClientGeneralException(key,ex);
            }
            catch (NumberFormatException msg){
                log.error(maskPayloadCredentials(ex.getMessage()));
                return new ClientGeneralException(ClientMessageKeys.UNEXPECTED_FAILURE);
            }
            catch(Exception msg){
                log.error(maskPayloadCredentials(ex.getMessage()));
                return new ClientResponseException(ex);
            }
        }
        return exception;
    }
}
