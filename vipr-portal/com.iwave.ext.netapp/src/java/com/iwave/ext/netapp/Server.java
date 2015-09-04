/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

/**
 * @author sdorcas
 *
 */
public class Server {

    private NaServer server = null;

    Server(String host, int port, String username, String password, boolean useHTTPS)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, null);
    }

    public Server(String host, int port, String username, String password, boolean useHTTPS, String vFilerName)
    {
        server = createNaServer(host, port, username, password, useHTTPS, false, vFilerName);
    }

    public Server(String host, int port, String username, String password, boolean useHTTPS, boolean isVserver, String vServerName)
    {
        server = createNaServer(host, port, username, password, useHTTPS, isVserver, vServerName);
    }

    public NaServer getNaServer()
    {
        return server;
    }

    private NaServer createNaServer(String addr, int port, String username, String password, boolean useHTTPS, boolean isVserver,
            String vServerName)
    {
        NaServer server = null;
        try {
            server = new NaServer(addr, 1, 20);
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
            e.printStackTrace();
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
