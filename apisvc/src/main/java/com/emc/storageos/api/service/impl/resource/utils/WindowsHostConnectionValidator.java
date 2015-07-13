/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.model.host.HostParam;
import com.emc.storageos.util.KerberosUtil;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.winrm.WinRMTarget;

public class WindowsHostConnectionValidator extends HostConnectionValidator {

    protected final static Logger log = LoggerFactory.getLogger(WindowsHostConnectionValidator.class);
    
    private DbClient dbClient;
    
    @PostConstruct
    private void init() {
       addValidator(this);
    }

    @Override
    public HostType getType() {
        return HostType.Windows;
    }

    @Override
    public boolean validateConnection(HostParam hostParam, Host existingHost) {
        HostType hostType = HostType.valueOf(hostParam.getType());
        if (getType().equals(hostType) == false) {
            throw new IllegalStateException(String.format("Invalid HostType [%s]", hostParam.getType()));
        }
        
        String password = hostParam.getPassword();
        if (password == null && existingHost != null) {
            password = existingHost.getPassword();
        }        
        
        List<URI> authProviderIds = dbClient.queryByType(AuthnProvider.class, true); 
        List<AuthnProvider> authProviders = dbClient.queryObject(AuthnProvider.class, authProviderIds);

        KerberosUtil.initializeKerberos(authProviders);
        WinRMTarget target = new WinRMTarget(hostParam.getHostName(), hostParam.getPortNumber(), hostParam.getUseSsl(), hostParam.getUserName(),
                password);
        WindowsSystemWinRM cli = new WindowsSystemWinRM(target);
        try {
            cli.listDiskDrives();
            return true;
        }
        catch (Exception e) {
            log.info(String.format("Error Validating Host %s", hostParam.getName()),e);
        }
        return false;
    }
    
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

}
