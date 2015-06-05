/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;


@Cf("ObjectNetwork")
public class ObjectNetwork extends DataObject {
    private URI _network;

    // List of data nodes which have been registered to this
    // ObjectNetwork as a single comma separated string.
    // e.g. IP1,IP2,IP3,...IPN
    private String _registeredDataNodes;

    public ObjectNetwork() {
        _network = null;
    }

    public ObjectNetwork(URI network) {
        setNetwork(network);
    }

    @Name("network")
    public URI getNetwork() {
        return _network;
    }

    public void setNetwork(URI network) {
        _network = network;
        setChanged("network");
    }

    @Name("registeredDataNodes")
    public String getRegisteredDataNodes() {
        return _registeredDataNodes;
    }

    public void setRegisteredDataNodes(String registeredDataNodes) {
        _registeredDataNodes = registeredDataNodes;
        setChanged("registeredDataNodes");
    }

    public String serializeRegisteredDataNodes( List<String> registeredDataNodes) {
        StringBuilder registeredDataNodesBuilder = new StringBuilder();

        for( String registeredDataNode : registeredDataNodes) {
            registeredDataNodesBuilder.append(registeredDataNode);
            registeredDataNodesBuilder.append(",");
        }

        return registeredDataNodesBuilder.toString();
    }

    public List<String> deserializeRegisteredDataNodes( String serializedRegisteredDataNodes ) {
        List<String> registeredDataNodesList = new ArrayList<String>();

        if( serializedRegisteredDataNodes != null &&
                serializedRegisteredDataNodes.length() > 0) {
            String nodeIPs[] = serializedRegisteredDataNodes.split(",");
            for(String nodeIP : nodeIPs) {
                registeredDataNodesList.add(nodeIP);
            }
        }
        return registeredDataNodesList;
    }
}
