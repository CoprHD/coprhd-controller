/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.Host;

public class HostToComputeElementMatcher {

    private static final Logger _log = LoggerFactory.getLogger(HostToComputeElementMatcher.class);

    // Find the UCS Blades that go with the given Hosts and associate them
    public static void matchHostsToComputeElementsByUuid(URI hostId, DbClient _dbClient) {
        matchHostsToComputeElementsByUuid(Arrays.asList(hostId),_dbClient);
    }

    public static void matchHostsToComputeElementsByUuid(Collection<URI> hostIds, DbClient _dbClient) {
        _log.debug("matching hosts to compute elements by UUID");

        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class, 
                Arrays.asList("uuid","computeElement"), hostIds);
        
        if(hosts.isEmpty()) {
            _log.debug("No hosts found with UUIDs for matching");
            return;
        }

        Collection<URI> allComputeElementUris = _dbClient.queryByType(ComputeElement.class,true);

        Collection<ComputeElement> computeElements = _dbClient.queryObjectField(ComputeElement.class, 
                "uuid", getFullyImplementedCollection(allComputeElementUris));

        matchHostsToComputeElements(hosts,computeElements,_dbClient);
    }

    // Find Hosts that go with the given ComputeElements and associate them
    public static void matchComputeElementsToHostsByUuid(Collection<URI> ucsComputeElementUris, 
            DbClient _dbClient) {
        _log.debug("Matching compute elements to hosts by UUID");

        Collection<ComputeElement> computeElements = 
                _dbClient.queryObjectField(ComputeElement.class, "uuid", 
                        getFullyImplementedCollection(ucsComputeElementUris));

        if(computeElements.isEmpty()) {
            _log.info("No compute elements found with UUIDs for matching");
            return;
        }

        Collection<URI> allHostUris = _dbClient.queryByType(Host.class,true);

        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class, 
                Arrays.asList("uuid", "computeElement"), getFullyImplementedCollection(allHostUris));

        matchHostsToComputeElements(hosts,computeElements,_dbClient);
    }

    private static void matchHostsToComputeElements(Collection<Host> hosts,
            Collection<ComputeElement> computeElements, DbClient _dbClient) {

        _log.info("Scanning for matches among " + hosts.size() + " hosts and " + computeElements.size() + 
                " compute elements");
        Collection<Host> hostsToUpdate = new ArrayList<>();
        for(ComputeElement computeElement:computeElements){
            for(Host host:hosts) {
                if((host.getUuid() != null) &&
                        host.getUuid().equals(computeElement.getUuid())) {
                    if((host.getComputeElement() != null) &&  
                            host.getComputeElement().equals(computeElement.getId())) {
                        _log.debug("Host " + host.getId() + " and compute element " + 
                            computeElement.getId() + " already matched");
                    }
                    else {
                        _log.info("Matched host (" + host.getId() + 
                                ") with compute element (" + computeElement.getId() + ")");
                        host.setComputeElement(computeElement.getId());
                        hostsToUpdate.add(host);
                    }
                    break; //next compute element (assumes no duplicate UUIDs in hosts)
                }
            }
        }
        if(!hostsToUpdate.isEmpty()) {
            _dbClient.persistObject(hostsToUpdate);
        }
        _log.info("Matched " + hostsToUpdate.size() + " new hosts to compute element");
    }

    private static <T> Collection<T> getFullyImplementedCollection(Collection<T> collectionIn){
        // Convert objects (like URIQueryResultList) that only implement iterator to 
        //   fully implemented Collection
        Collection<T> collectionOut = new ArrayList<>();
        Iterator<T> iter = collectionIn.iterator();
        while (iter.hasNext()) {
            collectionOut.add(iter.next());
        }
        return collectionOut;
    }

}
