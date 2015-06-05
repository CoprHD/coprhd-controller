/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 *
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScaleIOQueryAllSCSIInitiatorsResult {

    public static final String INITIATOR_ID = "Id";
    public static final String INITIATOR_NAME = "Name";
    public static final String INITIATOR_STATE = "State";
    public static final String VOLUME_NAME = "VolName";
    public static final String VOLUME_LUN = "LUN";
    public static final String VOLUME_ITL = "ITL";

    private static final ScaleIOAttributes EMPTY_PROPERTY = new ScaleIOAttributes();
    private Map<String, ScaleIOAttributes> initiators = new HashMap<>();
    private Map<String, Map<String, ScaleIOAttributes>> mappedVolumes = new HashMap<>();
    private Map<String, Set<String>> volumeToIQN = new HashMap<>();

    public void addInitiator(String id, String name, String state, String iqn) {
        ScaleIOAttributes attributes = initiators.get(iqn);
        if (attributes == null) {
            attributes = new ScaleIOAttributes();
            initiators.put(iqn, attributes);
        }
        attributes.put(INITIATOR_NAME, name);
        attributes.put(INITIATOR_STATE, state);
        attributes.put(INITIATOR_ID, id);
    }

    public void addMappedVolume(String iqn, String volId, String volName, String lun, String itl) {
        Map<String, ScaleIOAttributes> volumesMap = mappedVolumes.get(iqn);
        if (volumesMap == null) {
            volumesMap = new HashMap<>();
            mappedVolumes.put(iqn, volumesMap);
        }

        ScaleIOAttributes attributes = volumesMap.get(iqn);
        if (attributes == null) {
            attributes = new ScaleIOAttributes();
            volumesMap.put(volId, attributes);
        }
        attributes.put(VOLUME_NAME, volName);
        attributes.put(VOLUME_LUN, lun);
        attributes.put(VOLUME_ITL, itl);

        Set<String> initiators = volumeToIQN.get(iqn);
        if (initiators == null) {
            initiators = new HashSet<>();
            volumeToIQN.put(volId, initiators);
        }
        initiators.add(iqn);
    }

    public ScaleIOAttributes getInitiator(String iqn) {
        return initiators.containsKey(iqn) ? initiators.get(iqn) : EMPTY_PROPERTY;
    }

    public Set<String> getAllInitiatorIds() {
        return (!initiators.isEmpty()) ? initiators.keySet() : Collections.<String>emptySet();
    }

    public boolean isVolumeMappedToInitiator(String volumeId, String iqn) {
        return volumeToIQN.containsKey(volumeId) && volumeToIQN.get(volumeId).contains(iqn);
    }

    public boolean hasMappedVolumes(String iqn) {
        return mappedVolumes.containsKey(iqn);
    }

    public Map<String, ScaleIOAttributes> getMappedVolumes(String iqn) {
        return mappedVolumes.containsKey(iqn) ? mappedVolumes.get(iqn) : Collections.EMPTY_MAP;
    }
}
