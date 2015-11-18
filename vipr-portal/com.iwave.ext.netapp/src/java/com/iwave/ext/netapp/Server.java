/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sdorcas
 * 
 */
public class Server {

    private Logger _log = LoggerFactory.getLogger(Server.class);

    private NaServer server = null;

    public Server(String host, int port, String username, String password, boolean useHTTPS)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, null, false);
    }
    
    public Server(String host, int port, String username, String password, boolean useHTTPS, boolean isCluster)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, null, isCluster);
    }

    public Server(String host, int port, String username, String password, boolean useHTTPS, 
    		String vFilerName)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, vFilerName, false);
    }
    
    public Server(String host, int port, String username, String password, boolean useHTTPS, 
    		String vFilerName, boolean isCluster)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, vFilerName, isCluster);
    }

    public Server(String host, int port, String username, String password, boolean useHTTPS, 
    		boolean isVserver, String vServerName)
    {
        server = createNaServer(host, port, username, password, useHTTPS, isVserver, vServerName, false);
    }
    
    public Server(String host, int port, String username, String password, boolean useHTTPS, 
    		boolean isVserver, String vServerName, boolean isCluster)
    {
        server = createNaServer(host, port, username, password, useHTTPS, isVserver, vServerName, isCluster);
    }

    public NaServer getNaServer()
    {
        return server;
    }

    private NaServer createNaServer(String addr, int port, String username, String password, boolean useHTTPS, boolean isVserver,
            String vServerName, boolean isCluster)
    {
        NaServer server = null;
        try {
        	// Each Data ONTAP version comes with its ontapi api version.
        	// Data ONTAP 8.1.1 comes with ontapi v1.17. 8.1.2 P4 comes with 1.19.
        	// When we send a request to NetApp with lower( less than 1.20) ontapi version,
        	// It throws an exception "Version 1.20 was requested, but only 1.19 is supported".
        	// Added this condition to support lower versions of ontapi for 7-mode.
        	if (isCluster) {
        		server = new NaServer(addr, 1, 20);
        	} else {
        		server = new NaServer(addr, 1, 17);
        	}
            
            server.setServerType(NaServer.SERVER_TYPE_FILER);
            server.setStyle(NaServer.STYLE_LOGIN_PASSWORD);
            if (useHTTPS) {
                server.setTransportType(NaServer.TRANSPORT_TYPE_HTTPS);
            }
            else {
                server.setTransportType(NaServer.TRANSPORT_TYPE_HTTP);
            }
            server.setPort(port);
            server.setAdminUser(username, password);
            server.setKeepAliveEnabled(true);

            if (isVserver) {
                if (vServerName != null && !vServerName.isEmpty()) {
                    server.setVserver(vServerName);
                }
            } else {
                if (vServerName != null && !vServerName.isEmpty()) {
                    server.setVfilerTunneling(vServerName);
                }
            }
        } catch (NoClassDefFoundError nCDFE) {
            // If the ONTAP classes are not found (in case for OSS version), we propagate
            // the error back to upper layers. In normal scenarios, we are not supposed to catch the
            // NoClassDefFoundError, but in this case we have a definite use case where there will be
            // no class found in OSS version of file controller and we need to handle this scenario gracefully.
            throw new NetAppException("ONTAP SDK APIs could not be found in the classpath. "
                    + "Please check the classpath for availability of the appropriate jar.");
        } catch (Exception e) {
            _log.error("Problem while connecting to Array due to {}", e);
        }
        return server;
    }

    public NaElement invoke(String command) {
        return invoke(new NaElement(command));
    }

    public NaElement invoke(NaElement command) {
        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(command);
        } catch (Exception e) {
            throw new NetAppException(String.format("Failed to execute %s", command), e);
        }
        return resultElem;
    }

}
