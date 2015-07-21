/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.VirtualMachine;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;

public class VirtualMachineFinder extends ModelFinder<VirtualMachine> {

    public VirtualMachineFinder(DBClientWrapper client) {
        super(VirtualMachine.class, client);
    }

    public List<VirtualMachine> findByDatacenter(URI datacenterId) {
        List<NamedElement> virtualMachines = findIdsByDatacenter(datacenterId);
        
        return findByIds(toURIs(virtualMachines));        
    }   
    
    public List<NamedElement> findIdsByDatacenter(URI datacenterId) {
        return client.findBy(VirtualMachine.class, VirtualMachine.DATACENTER_ID, datacenterId);
    }    
    
}
