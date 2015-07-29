/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface PlacementExceptions {
    @DeclareServiceCode(ServiceCode.PLACEMENT_NUMPATHSLTNETWORKS)
    public PlacementException pathsLTNetworks(Integer numPaths, Integer numNetworks);

    @DeclareServiceCode(ServiceCode.PLACEMENT_CANNOTALLOCATEPORTS)
    public PlacementException cannotAllocateRequestedPorts(
            String network, String array, Integer requested, Integer allocated, Integer available);

    @DeclareServiceCode(ServiceCode.PLACEMENT_NOSTORAGEPORTSINNETWORK)
    public PlacementException noStoragePortsInNetwork(String networkLabel);

    @DeclareServiceCode(ServiceCode.PLACEMENT_CANNOTALLOCATEMINPATHS)
    public PlacementException cannotAllocateMinPaths(
            int needed, int initiatorCount, int pathsPerInitiator, int minPaths, int maxPaths);

    @DeclareServiceCode(ServiceCode.PLACEMENT_HOSTHASFEWERTHANMINPATHS)
    public PlacementException hostHasFewerThanMinPaths(String hostName, String hostURI, int ports, int minPaths);

    @DeclareServiceCode(ServiceCode.PLACEMENT_HOSTHASUNUSEDINITIATORS)
    public PlacementException hostHasUnusedInitiators(String hostName, String hostURI);

    @DeclareServiceCode(ServiceCode.PLACEMENT_INSUFFICENTREDUNDANCY)
    public PlacementException insufficientRedundancy(Integer maxPaths, Integer hardwareDomains);

}
