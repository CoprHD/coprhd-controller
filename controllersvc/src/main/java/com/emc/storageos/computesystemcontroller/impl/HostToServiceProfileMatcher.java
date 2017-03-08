/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.UCSServiceProfile;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;

public class HostToServiceProfileMatcher {

    private static final Logger _log = LoggerFactory.getLogger(HostToServiceProfileMatcher.class);

    // Find the UCS service profiles that go with the given Hosts and associate them
    public static void matchHostsToServiceProfilesByUuid(URI hostId, DbClient _dbClient) {
        matchHostsToServiceProfilesByUuid(Arrays.asList(hostId), _dbClient);
    }

    public static void matchHostsToServiceProfilesByUuid(Collection<URI> hostIds, DbClient _dbClient) {
        _log.info("Matching hosts to service profiles by UUID");

        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "serviceProfile", "registrationStatus", "inactive"), hostIds);

        List<Host> hostsWithoutServiceProfile = new ArrayList<Host>();
        for (Host host : hosts){
           if (NullColumnValueGetter.isNullURI(host.getServiceProfile())){
                if ((host.getUuid() == null) || host.getInactive() ||
                    RegistrationStatus.UNREGISTERED.name().equals(host.getRegistrationStatus())) {
                _log.debug("Skipping: Host "+ host.getLabel()+ " is inactive, unregistered or has no UUID");
                continue;  // skip if no UUID or not active, do not update
            }

               hostsWithoutServiceProfile.add(host);
           }
        }
        if (hostsWithoutServiceProfile.isEmpty()){
           _log.info("No hosts without service profile association. Nothing to do.");
           return;
        }


        Collection<URI> allServiceProfileUris = _dbClient.queryByType(UCSServiceProfile.class, true);

        Collection<UCSServiceProfile> allServiceProfiles = _dbClient.queryObjectFields(UCSServiceProfile.class,
                Arrays.asList("uuid", "registrationStatus", "host", "inactive", "dn"),
                getFullyImplementedCollection(allServiceProfileUris));
        List<UCSServiceProfile> serviceProfilesWithoutHost = new ArrayList<UCSServiceProfile>();
        for (UCSServiceProfile serviceProfile : allServiceProfiles) {
            if (NullColumnValueGetter.isNullURI(serviceProfile.getHost())){
                 if (NullColumnValueGetter.isNullValue(serviceProfile.getUuid()) || serviceProfile.getInactive()) {
                    _log.debug("Skipping: service profile "+ serviceProfile.getDn() +" is inactive or has no UUID");
                    continue;  // skip if no UUID or not active
                 }

                serviceProfilesWithoutHost.add(serviceProfile);
            }
        }
        if (serviceProfilesWithoutHost.isEmpty()){
            _log.info("No service profiles without host association. Nothing to do");
            return;
        }
        matchServiceProfilesToHosts(serviceProfilesWithoutHost, hostsWithoutServiceProfile, _dbClient);
    }

    public static void matchServiceProfilesToHosts(ComputeSystem cs, DbClient _dbClient) {
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getComputeSystemServiceProfilesConstraint(cs.getId()), uris);

        List<UCSServiceProfile> allServiceProfiles = _dbClient.queryObject(UCSServiceProfile.class, uris, true);
        List<UCSServiceProfile> serviceProfilesWithoutHost = new ArrayList<UCSServiceProfile>();
        for (UCSServiceProfile serviceProfile : allServiceProfiles) {
            if (NullColumnValueGetter.isNullURI(serviceProfile.getHost())){
                 if (NullColumnValueGetter.isNullValue(serviceProfile.getUuid()) || serviceProfile.getInactive()) {
                    _log.debug("Skipping: service profile "+ serviceProfile.getDn() +" is inactive or has no UUID");
                    continue;  // skip if no UUID or not active
                 }

                serviceProfilesWithoutHost.add(serviceProfile);
            }
        }
        if (serviceProfilesWithoutHost.isEmpty()){
            _log.info("No service profiles without host association. Nothing to do");
            return;
        }
        
        Collection<URI> allHostUris = _dbClient.queryByType(Host.class, true);
        Collection<Host> hosts = _dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "serviceProfile", "registrationStatus", "inactive"), getFullyImplementedCollection(allHostUris));
        
        List<Host> hostsWithoutServiceProfile = new ArrayList<Host>();
        for (Host host : hosts){
           if (NullColumnValueGetter.isNullURI(host.getServiceProfile())){
                if ((host.getUuid() == null) || host.getInactive() ||
                    RegistrationStatus.UNREGISTERED.name().equals(host.getRegistrationStatus())) {
                _log.debug("Skipping: Host "+ host.getLabel()+ " is inactive, unregistered or has no UUID");
                continue;  // skip if no UUID or not active, do not update
            }

               hostsWithoutServiceProfile.add(host);
           }
        }
        if (hostsWithoutServiceProfile.isEmpty()){
           _log.info("No hosts without service profile association. Nothing to do.");
           return;
        }

        matchServiceProfilesToHosts(serviceProfilesWithoutHost, hostsWithoutServiceProfile, _dbClient);
        
    }

    private static void matchServiceProfilesToHosts(List<UCSServiceProfile> serviceProfiles, List<Host> hosts, DbClient _dbClient){
        List<Host> hostsToUpdate = new ArrayList<Host>();
        List<UCSServiceProfile> serviceProfilesToUpdate = new ArrayList<UCSServiceProfile>();
        _log.info("Locating matches among " + hosts.size() + " Hosts and " + serviceProfiles.size() +
                " ServiceProfiles");

        for (UCSServiceProfile serviceProfile: serviceProfiles) {
            _log.info("matching serviceProfile :" + serviceProfile.getDn());
            List<Host> matchedHosts = new ArrayList<Host>();
            for (Host host : hosts) {
                _log.info("checking host " + host.getId());
                if (!host.getUuid().equals(serviceProfile.getUuid())){
                   _log.debug("UUIDs do not match");
                   continue;
                }
                _log.info("UUID matched");
                matchedHosts.add(host);
            }
            if (matchedHosts.isEmpty()){
               _log.info("No match found");
               continue;
            }
            if (matchedHosts.size() > 1 ){
               StringBuffer hostNames = new StringBuffer();
               for (Host host: matchedHosts){
                  hostNames.append(host.getLabel()+ " ");
               }
               _log.error("Found multiple hosts "+ hostNames.toString()+ " that match service profile : " + serviceProfile.getLabel());
               throw ComputeSystemControllerException.exceptions.serviceProfileMatchedMultipleHosts(serviceProfile.getDn(), serviceProfile.getUuid(), hostNames.toString());
            }
            Host matchedHost = matchedHosts.get(0); 
            _log.info("Setting association host: "+ matchedHost.getLabel()+ " and serviceProfile: " + serviceProfile.getDn() );
            matchedHost.setServiceProfile(serviceProfile.getId());
            serviceProfile.setHost(matchedHost.getId());
            hostsToUpdate.add(matchedHost);
            serviceProfilesToUpdate.add(serviceProfile);
        }
        persistDataObjects(new ArrayList<DataObject>(serviceProfilesToUpdate),_dbClient);
        persistDataObjects(new ArrayList<DataObject>(hostsToUpdate), _dbClient);
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

    private static void persistDataObjects(List<DataObject> objects, DbClient _dbClient) {
        if (!objects.isEmpty()) {
            _dbClient.persistObject(objects);
        }
    }

}
