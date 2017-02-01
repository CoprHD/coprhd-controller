/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class XtremIOExportMaskInitiatorsValidator extends AbstractXtremIOValidator {

    private static final Logger log = LoggerFactory.getLogger(XtremIOExportMaskInitiatorsValidator.class);

    private ArrayListMultimap<String, Initiator> initiatorToIGMap;
    private ArrayListMultimap<String, Initiator> knownInitiatorToIGMap;

    public XtremIOExportMaskInitiatorsValidator(StorageSystem storage, ExportMask exportMask) {
        super(storage, exportMask);
    }

    public void setInitiatorToIGMap(ArrayListMultimap<String, Initiator> initiatorToIGMap) {
        this.initiatorToIGMap = initiatorToIGMap;
    }

    public void setKnownInitiatorToIGMap(ArrayListMultimap<String, Initiator> knownInitiatorToIGMap) {
        this.knownInitiatorToIGMap = knownInitiatorToIGMap;
    }

    /**
     * Get list of initiators associated with the IG.
     *
     * a. If there are unknown initiators in IG, fail the operation
     * b. i) If Cluster export:
     * - - - If there are additional initiators other than the ones in ExportMask:
     * - - - check if all of them belong to different host but same cluster (Single IG with all cluster initiators)
     * - ii) Host export: Check additional initiators belong to same host or different host
     * - - -- If different host, fail the operation
     *
     * Reason for failing: We do not want to cause DU by choosing an operation when additional initiators
     * are present in the IG. Better fail the operation explaining the situation instead of resulting in DU.
     */
    @Override
    public boolean validate() throws Exception {
        log.info("Initiating initiators validation of XtremIO ExportMask: " + id);
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(getDbClient(), storage, getClientFactory());
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();

            if (knownInitiatorToIGMap == null) {
                knownInitiatorToIGMap = ArrayListMultimap.create();
            }

            // Don't validate against backing masks or RP
            if (ExportMaskUtils.isBackendExportMask(getDbClient(), exportMask)) {
                log.info("validation against backing mask for VPLEX or RP is disabled.");
                return true;
            }

            List<Initiator> knownInitiatorsInIGs = new ArrayList<Initiator>();
            List<String> allInitiatorsInIGs = new ArrayList<String>();
            List<XtremIOInitiator> initiators = client.getXtremIOInitiatorsInfo(xioClusterName);
            for (XtremIOInitiator initiator : initiators) {
                String igNameInInitiator = initiator.getInitiatorGroup().get(1);
                if (initiatorToIGMap.keySet().contains(igNameInInitiator)) {
                    allInitiatorsInIGs.add(Initiator.normalizePort(initiator.getPortAddress()));
                    Initiator knownInitiator = NetworkUtil.getInitiator(initiator.getPortAddress(), getDbClient());
                    if (knownInitiator != null) {
                        knownInitiatorsInIGs.add(knownInitiator);
                        knownInitiatorToIGMap.put(igNameInInitiator, knownInitiator);
                    }
                }
            }
            log.info("Initiators present in IG: {}", allInitiatorsInIGs);

            // Fail the operation if there are unknown initiators in the IG (not registered in ViPR)
            if (knownInitiatorsInIGs.size() < allInitiatorsInIGs.size()) {
                Collection<String> knownInitiatorNames = Collections2.transform(knownInitiatorsInIGs,
                        CommonTransformerFunctions.fctnInitiatorToPortName());
                Set<String> differences = Sets.difference(Sets.newHashSet(allInitiatorsInIGs), Sets.newHashSet(knownInitiatorNames));
                for (String diff : differences) {
                    getLogger().logDiff(exportMask.getId().toString(), "initiators", ValidatorLogger.NO_MATCHING_ENTRY, diff);
                }

                checkForErrors();
            }

            for (String igName : initiatorToIGMap.keySet()) {
                List<Initiator> requestedInitiatorsInIG = initiatorToIGMap.get(igName);
                List<Initiator> initiatorsInIG = knownInitiatorToIGMap.get(igName);
                String hostName = null;
                String clusterName = null;
                for (Initiator initiator : requestedInitiatorsInIG) {
                    if (null != initiator.getHostName()) {
                        // initiators already grouped by Host
                        hostName = initiator.getHostName();
                        clusterName = initiator.getClusterName();
                        break;
                    }
                }
                Collection<String> knownInitiators = Collections2.transform(Lists.newArrayList(initiatorsInIG),
                        CommonTransformerFunctions.fctnInitiatorToPortName());
                Collection<String> requestedInitiators = Collections2.transform(requestedInitiatorsInIG,
                        CommonTransformerFunctions.fctnInitiatorToPortName());

                log.info("Validation requested initiators: {}", requestedInitiators);
                log.info("Validation discovered initiators: {}", knownInitiators);
                knownInitiators.removeAll(requestedInitiators);
                log.info("Validation unknown initiators in IG: {}", knownInitiators);
                if (!knownInitiators.isEmpty()) {
                    List<String> listToIgnore = new ArrayList<String>();
                    log.info(
                            "There are other initiators present in the IG - {}. Checking if they all belong to same host or different host but same cluster.",
                            knownInitiators);
                    log.info("Host name: {}, Cluster name: {}", hostName, clusterName);
                    // check if the other initiators belong to different host
                    for (Initiator ini : initiatorsInIG) {
                        if (NullColumnValueGetter.isNotNullValue(clusterName) && ini.getHostName() != null
                                && !ini.getHostName().equalsIgnoreCase(hostName)) {
                            // check if they belong to same cluster
                            if (ini.getClusterName() != null && clusterName != null && !clusterName.isEmpty()
                                    && ini.getClusterName().equalsIgnoreCase(clusterName)) {
                                listToIgnore.add(Initiator.normalizePort(ini.getInitiatorPort()));
                            }
                        } else if (ini.getHostName() != null && ini.getHostName().equalsIgnoreCase(hostName)) {
                            listToIgnore.add(Initiator.normalizePort(ini.getInitiatorPort()));
                        }
                    }

                    log.info("Validation initiators that belong to same host or cluster: {}", listToIgnore);
                    knownInitiators.removeAll(listToIgnore);
                    log.info("Validation remaining initiators that are not managed by controller: {}", knownInitiators);

                    for (String knownInitiator : knownInitiators) {
                        getLogger().logDiff(exportMask.getId().toString(), "initiators", ValidatorLogger.NO_MATCHING_ENTRY, knownInitiator);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask initiators: " + ex.getMessage(), ex);
            if (getConfig().isValidationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(
                        "Unexpected exception validating ExportMask initiators: " + ex.getMessage());
            }
        }

        checkForErrors();

        log.info("Completed initiator validation of XtremIO ExportMask: " + id);

        return true;
    }

}
