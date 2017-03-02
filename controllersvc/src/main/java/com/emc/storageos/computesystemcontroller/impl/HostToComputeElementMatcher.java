/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public class HostToComputeElementMatcher {

    private static final Logger _log = LoggerFactory.getLogger(HostToComputeElementMatcher.class);

    // Find the UCS Blades that go with the given Hosts and associate them
    public static void matchHostsToComputeElementsByUuid(URI hostId, DbClient _dbClient) {
        matchHostsToComputeElementsByUuid(Arrays.asList(hostId), _dbClient);
    }

    public static void matchHostsToComputeElementsByUuid(Collection<URI> hostIds, DbClient _dbClient) {
        _log.info("Matching hosts to compute elements by UUID");

        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "computeElement", "registrationStatus", "inactive"), hostIds);

        if (hosts.isEmpty()) {
            _log.debug("No hosts found with UUIDs for matching");
            return;
        }

        Collection<URI> allComputeElementUris = _dbClient.queryByType(ComputeElement.class, true);

        Collection<ComputeElement> computeElements = _dbClient.queryObjectFields(ComputeElement.class,
                Arrays.asList("uuid", "registrationStatus", "available", "inactive"),
                getFullyImplementedCollection(allComputeElementUris));

        matchHostsToComputeElements(hosts, computeElements, _dbClient);
    }

    // Find Hosts that go with the given ComputeElements and associate them
    public static void matchComputeElementsToHostsByUuid(Collection<URI> ucsComputeElementUris,
            DbClient _dbClient) {
        _log.info("Matching compute elements to hosts by UUID");

        Collection<ComputeElement> computeElements =
                _dbClient.queryObjectFields(ComputeElement.class,
                        Arrays.asList("uuid", "registrationStatus", "available", "inactive"),
                        getFullyImplementedCollection(ucsComputeElementUris));

        if (computeElements.isEmpty()) {
            _log.info("No compute elements found with UUIDs for matching");
            return;
        }

        Collection<URI> allHostUris = _dbClient.queryByType(Host.class, true);

        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "computeElement", "registrationStatus", "inactive"), getFullyImplementedCollection(allHostUris));

        matchHostsToComputeElements(hosts, computeElements, _dbClient);
    }

    private static void matchHostsToComputeElements(Collection<Host> hosts,
            Collection<ComputeElement> computeElements, DbClient _dbClient) {

        _log.info("Locating matches among " + hosts.size() + " Hosts and " + computeElements.size() +
                " ComputeElements");

        // If a Host does not match a CE, leave Host's CE attribute as it is
        // If a Host matches more than one ComputeElement (CE):
        // 1) prefer an unavailable CE (since that means it's associated to a Host in the ComputeSystem)
        // 2) if all matched CEs are available, match 1st one encountered (to aid troubleshooting)
        // 3) if >1 matched CE is unavailable, log error & leave Host's CE attribute as it is
        // Do not match inactive Hosts or Hosts with no UUID
        // Do not match inactive CEs

        Collection<Host> hostsToUpdate = new ArrayList<>();
        for (Host host : hosts) {
            _log.debug("Matching host " + host.getId());
            if ((host.getUuid() == null) || host.getInactive() ||
                    RegistrationStatus.UNREGISTERED.name().equals(host.getRegistrationStatus())) {
                _log.debug("Host is inactive, unregistered or has no UUID");
                continue;  // skip if no UUID or not active, do not update
            }
            boolean updateHost = true;
            ComputeElement matchedComputeElement = null;
            for (ComputeElement computeElement : computeElements) {
                _log.debug("Checking for match with ComputeElement " + computeElement.getId());
                if ((computeElement.getUuid() == null) || computeElement.getInactive() ||
                        RegistrationStatus.UNREGISTERED.name().equals(computeElement.getRegistrationStatus())) {
                    _log.debug("ComputeElement inactive or unregistered or has no UUID");
                    continue;  // skip inactive/unregistered ComputeElements
                }
                if (!host.getUuid().equals(computeElement.getUuid())) {
                    _log.debug("UUID mismatch");
                    continue;  // skip if UUIDs don't match
                }
                if (matchedComputeElement == null) {
                    _log.debug("UUID matched");
                    matchedComputeElement = computeElement;
                    continue;
                }
                if (matchedComputeElement.getAvailable() && !computeElement.getAvailable()) {
                    _log.debug("Changing match to a ComputeElement that is in use");
                    matchedComputeElement = computeElement; // prefer unavailable CE
                    continue;
                }
                if (!matchedComputeElement.getAvailable() && !computeElement.getAvailable()) {
                    // found 2 matching CEs which are associated to hosts in their ComputeSystem(s)
                    // (selecting the wrong CE may affect another Host when this Host is changed)
                    _log.error(host.getId() + " matches multiple ComputeElements: " +
                            matchedComputeElement.getId() + " and " + computeElement.getId() +
                            ".  Leaving Host associated with " + host.getComputeElement());
                    updateHost = false;
                }
            }
            if (updateHost && matchedComputeElement != null) {
                if ((!NullColumnValueGetter.isNullURI(host.getComputeElement())) &&
                        host.getComputeElement().equals(matchedComputeElement.getId())) {
                    _log.debug("Skipping " + host.getId() + " which is already matched to " +
                            matchedComputeElement.getId());
                } else {
                    _log.info("Updating Host " + host.getId() + " to ComputeElement " +
                            matchedComputeElement.getId());
                    host.setComputeElement(matchedComputeElement.getId());
                    hostsToUpdate.add(host);
                }
            }
        }
        if (!hostsToUpdate.isEmpty()) {
            _dbClient.persistObject(hostsToUpdate);
        }
        _log.info("Matched " + hostsToUpdate.size() + " Hosts to ComputeElements");
    }

    private static <T> Collection<T> getFullyImplementedCollection(Collection<T> collectionIn) {
        // Convert objects (like URIQueryResultList) that only implement iterator to
        // fully implemented Collection
        Collection<T> collectionOut = new ArrayList<>();
        Iterator<T> iter = collectionIn.iterator();
        while (iter.hasNext()) {
            collectionOut.add(iter.next());
        }
        return collectionOut;
    }

}
