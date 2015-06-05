/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.core.filters.IdFilter;
import com.google.common.collect.Sets;

public class FindVirtualArrayInitiators extends ViPRExecutionTask<Set<Initiator>> {

    private URI varray;
    private Collection<Initiator> initiators;

    public FindVirtualArrayInitiators(String varray, Collection<Initiator> initiators) {
        this(uri(varray), initiators);
    }

    public FindVirtualArrayInitiators(URI varray, Collection<Initiator> initiators) {
        this.varray = varray;
        this.initiators = initiators;
        provideDetailArgs(varray);
    }

    @Override
    public Set<Initiator> executeTask() throws Exception {
        IdFilter<VirtualArrayRestRep> filter = new IdFilter<VirtualArrayRestRep>(varray);

        Set<Initiator> results = Sets.newHashSet();
        for (Initiator initiator : initiators) {
            String initiatorPort = initiator.getInitiatorPort();
            List<VirtualArrayRestRep> virtualArrays = getClient().varrays().findByInitiatorPort(initiatorPort, filter);

            // Filter will only return the virtual array we are searching for
            if (virtualArrays.size() > 0) {
                results.add(initiator);
            }
        }
        return results;
    }
}
