/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.host.InitiatorRestRep;

public class HostApiMapperUtils {
    public static List<String> getWWNsFromInitiators(List<InitiatorRestRep> initiators) {
        final List<String> wwns = new ArrayList<String>();
        for (InitiatorRestRep initiator : initiators) {
            if (StringUtils.isNotBlank(initiator.getInitiatorNode())) {
                wwns.add(initiator.getInitiatorNode() + ":" + initiator.getInitiatorPort());
            }
            else {
                wwns.add(initiator.getInitiatorPort());
            }
        }
        return wwns;
    }
}
