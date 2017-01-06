/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

public class HostToComputeElementMatcher {

    private static final Logger _log = LoggerFactory.getLogger(HostToComputeElementMatcher.class);

    private static StringBuffer discoveryFailureMessages = new StringBuffer();

    private static Collection<Host> allHosts;

    private static Collection<ComputeElement> eligibleComputeElements;
    private static Collection<Host> eligibleHosts;

    private static HostToComputeElementMatches hostToComputeElementMatches;

    private static DbClient dbClient;

    public static void matchHostsToComputeElements(DbClient _dbClient) {

        /**
         * Note: it's possible a UUID can change in UCS and Host discovery will get new UUID in ViPR,
         * and matcher will run before UCS discovery completes, so blade in UCS will have old UUID
         * until UCS discovery completes.  Same if UCS discovers & matcher is run, Host UUID will be stale.
         * (Consider only matching on UCS discovery)
         */

        dbClient = _dbClient;                  // set our client
        discoveryFailureMessages.setLength(0); // clear error message buffer

        load();                                // load all active hosts & computeElements

        filterEligibleHosts();                 // filter out unregistered hosts & hosts with bad UUIDs **
        filterEligibleComputeElements();       // filter out unregistered blades & blades with bad UUIDs **

        removeMissingBladeRefs();              // remove references in hosts to missing blades **

        filterHostsWithSameUuid();             // filter out hosts with duplicate UUIDs **
        filterCEsWithSameUuid();               // filter out blades with duplicate UUIDs **

        findUuidMatches();                     // find remaining hosts & blades whose UUIDs match

        removeMatchConflicts();                // remove conflicting matches (matching multiple hosts or blades)

        applyMatchesToHosts();                 // apply matches to hosts in DB

        // ** Host association(s) removed

        if (discoveryFailureMessages.length() > 0) {
            throw new IllegalStateException("Conflicts detected while matching hosts to blades.  " +
                    "Please correct or contact customer support. Conflicts are:" + discoveryFailureMessages);
        }
    }

    private static void load() {
        Collection<URI> allHostUris =
                dbClient.queryByType(Host.class, true); // active only

        allHosts = dbClient.queryObjectFields(Host.class,
                Arrays.asList("uuid", "computeElement", "registrationStatus", "hostName","bios","label"),
                getFullyImplementedCollection(allHostUris));

        Collection<URI> allComputeElementUris =
                dbClient.queryByType(ComputeElement.class, true); // active only

        Collection<ComputeElement> allComputeElements =
                dbClient.queryObjectFields(ComputeElement.class,
                        Arrays.asList("uuid", "registrationStatus", "available","label"),
                        getFullyImplementedCollection(allComputeElementUris));

        // initialize lists of eligible hosts & blades (to be filtered before matching)
        eligibleHosts = new ArrayList<Host>(allHosts);

        eligibleComputeElements = new ArrayList<ComputeElement>(allComputeElements);

        _log.info("Locating matches among " + allHosts.size() + " Hosts and " +
                allComputeElements.size() + " ComputeElements");
    }

    private static Collection<Host> filterEligibleHosts() {
        // filter out unregistered hosts & hosts with invalid UUID (and remove blade assoc's)
        for (Iterator<Host> eligibleHostsIter = eligibleHosts.iterator(); eligibleHostsIter.hasNext(); ) {
            Host host = eligibleHostsIter.next();
            _log.info("Checking eligibility of host '" + host.getLabel() + "'(" + host.getId() + ")");
            if ( (!isValidUuid(host.getUuid())) ||
                    RegistrationStatus.UNREGISTERED.name().equals(host.getRegistrationStatus())) {
                String hostCeMsg = "";
                if( !NullColumnValueGetter.isNullURI(host.getComputeElement()) ) {
                    hostCeMsg = "Removing association to (" + host.getComputeElement() + "). ";
                    host.setComputeElement(NullColumnValueGetter.getNullURI());
                    dbClient.updateObject(host);
                }
                _log.info("Host is unregistered or has an invalid UUID.  " + hostCeMsg + "Ignoring Host '" +
                        host.getLabel() + "'(" + host.getId() + ")[" + host.getUuid() + "]");
                eligibleHostsIter.remove();
            }
        }
        return eligibleHosts;
    }

    private static void filterEligibleComputeElements() {
        // filter out unregistered blades & blades with invalid UUID (removing all host assoc's)
        for (Iterator<ComputeElement> eligibleComputeElementsIter = eligibleComputeElements.iterator();
                eligibleComputeElementsIter.hasNext(); ) {
            ComputeElement computeElement = eligibleComputeElementsIter.next();
            _log.info("Checking for eligibility of ComputeElement '" + computeElement.getLabel() +
                    "'(" +computeElement.getId() + ")");
            if ((!isValidUuid(computeElement.getUuid()) ||
                    RegistrationStatus.UNREGISTERED.name().equals(computeElement.getRegistrationStatus()))) {
                _log.info("ComputeElement '" +
                        computeElement.getLabel() + "'(" +
                        computeElement.getId() + ")[" +
                        computeElement.getUuid() + "] is unregistered or has an invalid UUID.");
                for (Host hostToCheck:allHosts) { // remove associations to this blade
                    if ( !NullColumnValueGetter.isNullURI(hostToCheck.getComputeElement()) &&
                            hostToCheck.getComputeElement().equals(computeElement.getId()) ) {
                        _log.warn("Removing association of Host '" + hostToCheck.getLabel() +
                                "'(" + hostToCheck.getId() + ") with ComputeElement '" +
                                computeElement.getLabel() + "'(" +
                                computeElement.getId() + ")[" +
                                computeElement.getUuid() + "] which is unregistered or has invalid UUID.");
                        hostToCheck.setComputeElement(NullColumnValueGetter.getNullURI());
                        dbClient.updateObject(hostToCheck);
                    }
                }
                eligibleComputeElementsIter.remove();
            }
        }
    }

    private static void removeMissingBladeRefs() {
        // remove associations from Hosts to missing blades(i.e.: associated computeElement is not in DB)
        Collection<Host> hostsToUpdate = new ArrayList<>();
        hostLoop:
            for(Host host : eligibleHosts) {
                if(!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                    for(ComputeElement ce : eligibleComputeElements) {
                        if(host.getComputeElement().equals(ce.getId())) {
                            continue hostLoop; // host assoc'd to valid, eligible blade.  Skip to next host
                        }
                    }
                    _log.info("Host associated to unknown ComputeElement.  Host '" + host.getLabel() + "'(" +
                            host.getId() + ") is associated to ComputeElement " +
                            host.getComputeElement() + " which is not in ViPR DB.  Removing this association.");
                    host.setComputeElement(NullColumnValueGetter.getNullURI());
                    hostsToUpdate.add(host);
                }
            }
        dbClient.updateObject(hostsToUpdate);
    }

    private static Collection<Host> filterHostsWithSameUuid() {
        // filter out hosts with duplicate UUIDs
        HashMap<String,List<Host>> uuidHostMap = new HashMap<>(); // key=uuid, value=list of hosts with that uuid
        for (Host host : eligibleHosts) {
            if(!uuidHostMap.containsKey(host.getUuidOldFormat())) { // account for new UUID formatting
                uuidHostMap.put(host.getUuidOldFormat(),new ArrayList<Host>(Arrays.asList(host)));
            } else { // host has same uuid as another host already seen
                List<Host> hostsForUuid = uuidHostMap.get(host.getUuidOldFormat());
                hostsForUuid.add(host); // add host to list for this uuid
                uuidHostMap.put(host.getUuidOldFormat(),hostsForUuid); // put back in map
            }
        }
        for( String uuidOldFormat : uuidHostMap.keySet()) {
            if(uuidHostMap.get(uuidOldFormat).size() > 1) { // if >1 host for this uuid
                String errMsg = "";
                Collection<Host> hostsToUpdate = new ArrayList<>();
                for(Host host : uuidHostMap.get(uuidOldFormat)) {
                    String uuidFormatNote = "";
                    if(host.hasMixedEndianUuid()) {
                        uuidFormatNote = "(Host has newer, mixed-endian format, therefore matches older format uuid on blade.)  ";
                    }
                    errMsg += "Host UUID conflict.  Host '" +
                            host.getLabel() + "'(" +
                            host.getId() + ")[" +
                            uuidOldFormat + "](old UUID format) has same UUID as other Host(s). " +
                            uuidFormatNote;
                    if( !NullColumnValueGetter.isNullURI(host.getComputeElement()) ) {
                        _log.error("Host UUID conflict.  Host has same UUID as other hosts.  " +
                                "Removing blade association for host '" +
                                host.getLabel() + "'(" +
                                host.getId() + ")[" +
                                host.getUuid() + "] to ComputeElement (" +
                                host.getComputeElement() + "). " + uuidFormatNote);
                        host.setComputeElement(NullColumnValueGetter.getNullURI());
                        hostsToUpdate.add(host);
                    }
                    eligibleHosts.remove(host);
                }
                dbClient.updateObject(hostsToUpdate);
                _log.error(errMsg);
                discoveryFailureMessages.append(errMsg);
            }
        }
        return eligibleHosts;
    }

    private static Collection<ComputeElement> filterCEsWithSameUuid() {
        // filter out blades with duplicate UUIDs (UCS UUIDs are always old format)
        HashMap<String,List<ComputeElement>> uuidComputeElementMap = new HashMap<>();
        for (ComputeElement computeElement : eligibleComputeElements) {
            if(!uuidComputeElementMap.containsKey(computeElement.getUuid())) {
                uuidComputeElementMap.put(computeElement.getUuid(),
                        new ArrayList<ComputeElement>(Arrays.asList(computeElement)));
            } else { // computeElements with same UUID found
                List<ComputeElement> computeElementsForUuid = uuidComputeElementMap.get(computeElement.getUuid());
                computeElementsForUuid.add(computeElement);
                uuidComputeElementMap.put(computeElement.getUuid(),computeElementsForUuid);
            }
        }
        for( String uuid : uuidComputeElementMap.keySet()) {
            if(uuidComputeElementMap.get(uuid).size() > 1) {
                String errMsg = "";
                Collection<Host> hostsToUpdate = new ArrayList<>();
                for(ComputeElement computeElement : uuidComputeElementMap.get(uuid)) {
                    errMsg += "ComputeElement '" +
                            computeElement.getLabel() + "'(" +
                            computeElement.getId() + ") has the same UUID [" +
                            computeElement.getUuid() + "] as other ComputeElements.  UUID is ";
                    for(Host hostToCheck : eligibleHosts) {
                        if( !NullColumnValueGetter.isNullURI(hostToCheck.getComputeElement()) &&
                                hostToCheck.getComputeElement().equals(computeElement.getId()) ) {
                            _log.error("UUID conflict.  Removing ComputeElement association for Host '" +
                                hostToCheck.getLabel() + "'(" + hostToCheck.getId() + ") to ComputeElement '" +
                                    computeElement.getLabel() + "'(" + computeElement.getId() + ") because " +
                                "ComputeElement UUID [" + computeElement.getUuid() + "] is not unique. ");
                            hostToCheck.setComputeElement(NullColumnValueGetter.getNullURI());
                            hostsToUpdate.add(hostToCheck);
                        }
                    }
                    eligibleComputeElements.remove(computeElement);
                }
                dbClient.updateObject(hostsToUpdate);
                _log.error(errMsg);
                discoveryFailureMessages.append(errMsg);
            }
        }
        return eligibleComputeElements;
    }

    private static void findUuidMatches() {
        // check for uuid matches
        hostToComputeElementMatches = new HostToComputeElementMatches();
        for(Host host : eligibleHosts) {
            for(ComputeElement computeElement : eligibleComputeElements) {
                if (uuidsMatch(host,computeElement)) {
                    hostToComputeElementMatches.add(host, computeElement);
                    _log.info("UUID matched for Host '" + host.getLabel() + "'(" + host.getId() +
                            ") [" + host.getUuid() + "] and ComputeElement '" + computeElement.getLabel() +
                            "'(" + computeElement.getId() + ") [" + computeElement.getUuid() + "]");
                }
            }
        }
    }

    private static void removeMatchConflicts() {
        // validate & process matches
        for (HostComputeElementPair match : hostToComputeElementMatches.getMatches()) {

            Host matchedHost = match.getHost();
            if (hostToComputeElementMatches.getComputeElements(matchedHost).size() > 1) {
                String bladeList = "";
                for(HostComputeElementPair removedMatch :
                    hostToComputeElementMatches.removeMatches(matchedHost)) {
                    bladeList += "'" + removedMatch.getComputeElement().getLabel() +
                            "'(" + removedMatch.getComputeElement().getId() + ") ";
                }
                String errMsg = "Host '" + matchedHost.getLabel() + "'(" +
                        matchedHost.getId() + ") matched multiple ComputeElements: " + bladeList;
                discoveryFailureMessages.append(errMsg);
                _log.error(errMsg);
            }

            ComputeElement matchedBlade = match.getComputeElement();
            if (hostToComputeElementMatches.getHosts(matchedBlade).size() > 1) {
                String hostList = "";
                for(HostComputeElementPair removedMatch :
                    hostToComputeElementMatches.removeMatches(matchedBlade)) {
                    hostList += "'" + removedMatch.getHost().getLabel() +
                            "'(" + removedMatch.getHost().getId() + ") ";
                }
                String errMsg = "ComputeElement '" + matchedBlade.getLabel() + "'(" +
                        matchedBlade.getId() + ") matched multiple Hosts: " + hostList;
                discoveryFailureMessages.append(errMsg);
                _log.error(errMsg);
            }
        }
    }

    private static void applyMatchesToHosts() {
        Collection<Host> hostsToUpdate = new ArrayList<>();
        for (HostComputeElementPair match : hostToComputeElementMatches.getMatches()) {
            if(!match.getHost().getComputeElement().equals(match.getComputeElement().getId())) {
                match.getHost().setComputeElement(match.getComputeElement().getId());
                hostsToUpdate.add(match.getHost());
                _log.info("Matching host '" +
                        match.getHost().getLabel() + "'(" +
                        match.getHost().getId() + ")[" +
                        match.getHost().getUuid() + "] to ComputeElement '" +
                        match.getComputeElement().getLabel() + "'(" +
                        match.getComputeElement().getId() + ")[" +
                        match.getComputeElement().getUuid() + "]");
            }
        }
        dbClient.updateObject(hostsToUpdate);
    }

    private static boolean uuidsMatch(Host host, ComputeElement computeElement) {
        if( NullColumnValueGetter.isNullValue(host.getUuid()) ||
                NullColumnValueGetter.isNullValue(computeElement.getUuid()) ) {
            return false;
        }
        if(host.getUuidOldFormat().equals(computeElement.getUuid())) {
            _log.info("UUID match for " + host.getUuid());
            return true;
        }
        _log.info("No UUID match for " + host.getUuid() + " & " + computeElement.getUuid() );
        return false;
    }

    private static boolean isValidUuid(String uuid) {
        if ( NullColumnValueGetter.isNullValue(uuid) ) {
            return false;
        }
        final String uuidPattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
        Pattern r = Pattern.compile(uuidPattern,Pattern.CASE_INSENSITIVE);
        return r.matcher(uuid).matches();
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

    private static class HostComputeElementPair {
        private Host host;
        private ComputeElement computeElement;

        private HostComputeElementPair(Host host, ComputeElement computeElement) {
            setHost(host);
            setComputeElement(computeElement);
        }
        private Host getHost() {
            return host;
        }
        private void setHost(Host host) {
            this.host = host;
        }
        private ComputeElement getComputeElement() {
            return computeElement;
        }
        private void setComputeElement(ComputeElement computeElement) {
            this.computeElement = computeElement;
        }
    }

    private static class HostToComputeElementMatches {

        private List<HostComputeElementPair> hostComputeElementPairs;

        private HostToComputeElementMatches() {
            hostComputeElementPairs = new ArrayList<>();
        }
        
        private void add(Host host, ComputeElement computeElement) {
            hostComputeElementPairs.add(new HostComputeElementPair(host,computeElement));
        }

        private List<HostComputeElementPair> getMatches() {
            return hostComputeElementPairs;
        }

        private List<Host> getHosts(ComputeElement computeElement){
            List<Host> hostList = new ArrayList<>();
            for (HostComputeElementPair p: hostComputeElementPairs) {
                if (p.getComputeElement().getId().equals(computeElement.getId())) {
                    hostList.add(p.getHost());
                }
            }
            return hostList;
        }

        private List<ComputeElement> getComputeElements(Host host){
            List<ComputeElement> computeElementList = new ArrayList<>();
            for (HostComputeElementPair p: hostComputeElementPairs) {
                if (p.getHost().getId().equals(host.getId())) {
                    computeElementList.add(p.getComputeElement());
                }
            }
            return computeElementList;
        }

        private List<HostComputeElementPair> removeMatches(Host host) {
            List<HostComputeElementPair> removedMatches = new ArrayList<>();
            for (Iterator<HostComputeElementPair> matchIter = hostComputeElementPairs.iterator();
                    matchIter.hasNext(); ) {
                HostComputeElementPair match = matchIter.next();
                if (match.getHost().getId().equals(host.getId())) {
                    removedMatches.add(match);
                    matchIter.remove();
                }
            }
            return removedMatches;
        }

        private List<HostComputeElementPair> removeMatches(ComputeElement computeElement) {
            List<HostComputeElementPair> removedMatches = new ArrayList<>();
            for (Iterator<HostComputeElementPair> matchIter = hostComputeElementPairs.iterator();
                    matchIter.hasNext(); ) {
                HostComputeElementPair match = matchIter.next();
                if (match.getComputeElement().getId().equals(computeElement.getId())) {
                    removedMatches.add(match);
                    matchIter.remove();
                }
            }
            return removedMatches;
        }
    }
}
