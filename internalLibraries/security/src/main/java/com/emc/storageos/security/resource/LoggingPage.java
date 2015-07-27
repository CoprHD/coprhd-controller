/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */


package com.emc.storageos.security.resource;

import java.io.IOException;
import java.net.URI;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.RequestProcessingUtils;
import com.google.common.net.InetAddresses;



/**
 * This resource provides a way to preauthenticate with the service.
 */

@Path("/")
public class LoggingPage {

    private final Logger _log = LoggerFactory.getLogger(LoggingPage.class);
    
    @Autowired
    protected AuthSvcEndPointLocator _endpointLocator;
    
    @XmlRootElement(name = "login_data")
    public static class LoginData {
        /**
         * Placeholder string to test access to a resource.
         * @valid none
         */
        @XmlElement(name = "data")
        public String getData() {
            return _data;
        }
        private static String _data = "Logged In";
    }

    /**
     * This resource is no longer used.  See AuthenticationResource.getLoginToken()
     * @prereq none    
     * @brief Internal Only.  See AuthenticationResource.getLoginToken()
     * @return LoginData
     *
     * @throws DatabaseException When an error occurs querying the database.
     */
    
    @GET
    @Path("/login")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public LoginData landingPage() {
        LoginData data = new LoginData();
        return data;
    }

    /**
      /**
     * This resource is no longer used.  See AuthenticationResource.getProxyToken()
     * @prereq none    
     * @brief Internal Only. See AuthenticationResource.getProxyToken()
     * @param httpRequest
     * @param servletResponse
     * @throws IOException
     */
    @GET
    @Path("/proxytoken")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public boolean getProxyToken(@Context HttpServletRequest httpRequest, 
            @Context HttpServletResponse servletResponse) throws IOException {     
        URI endpoint = _endpointLocator.getAnEndpoint();
        StringBuilder redirectURL = new StringBuilder(endpoint.toString());
        if (!InetAddresses.isInetAddress(endpoint.getHost())){
            // ok, then, keep them on the same node
            redirectURL = RequestProcessingUtils.getOnNodeAuthsvcRedirectURL(httpRequest, endpoint);
}
        redirectURL.append("/proxytoken");
        _log.debug("Forwarding proxytoken request to {}", redirectURL.toString());
        servletResponse.sendRedirect(redirectURL.toString());

	// CQ 605833
	// Apparently, Jersey requires that a non-void type must be returned 
	// from a GET. Tthe following is added to suppress a WARNING that has 
	// no functional meaning
	return true;
    }
    
}
