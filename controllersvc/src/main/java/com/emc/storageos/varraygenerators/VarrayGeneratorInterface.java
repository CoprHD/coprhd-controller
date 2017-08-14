package com.emc.storageos.varraygenerators;

import com.emc.storageos.db.client.model.DiscoveredSystemObject;

public interface VarrayGeneratorInterface {
    public void generateVarraysForDiscoveredSystem(DiscoveredSystemObject system);
}
