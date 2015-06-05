/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.HostType;
import com.emc.storageos.model.host.HostParam;
import com.iwave.ext.linux.LinuxSystemCLI;

public class LinuxHostConnectionValidator extends HostConnectionValidator {

    protected final static Logger log = LoggerFactory.getLogger(LinuxHostConnectionValidator.class);
    
    @PostConstruct
    public void init() {
        addValidator(this);
    }

    @Override
    public HostType getType() {
        return HostType.Linux;
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
        
        LinuxSystemCLI cli = new LinuxSystemCLI(hostParam.getHostName(), hostParam.getPortNumber(), hostParam.getUserName(),
                password);
        try {
            cli.listMountPoints();
            return true;
        }
        catch (Throwable e) {
            log.info(String.format("Error Validating Host %s", hostParam.getName()),e);
        }
        return false;
    }

}
