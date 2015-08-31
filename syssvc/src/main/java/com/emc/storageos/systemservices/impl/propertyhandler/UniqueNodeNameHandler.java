/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.*;
import com.emc.storageos.svcs.errorhandling.resources.*;
import com.emc.storageos.systemservices.impl.upgrade.*;
import org.slf4j.*;

import java.util.*;

public class UniqueNodeNameHandler implements UpdateHandler{
    private static final Logger _log = LoggerFactory.getLogger(UniqueNodeNameHandler.class);

    private static volatile CoordinatorClientExt _coordinator;

    private static final String USE_SHORT_NODE_NAME= "use_short_node_name";
    private static final String NODE_COUNT = "node_count";
    private static final String NODE_STANDALONE_NAME = "node_standalone_name";

    public static void setCoordinator(CoordinatorClientExt coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Checks if node_x_name properties have unique names
     *
     * If unique_short_name property is true then only short names are checked
     * Else full node names are checked for uniqueness
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps,PropertyInfoRestRep newProps){
        Map<String, String> propInfo = _coordinator.getPropertyInfo().getProperties();

        // get all node name properties (# of these change depending on deployment)
        ArrayList<String> nodeNameProperties = getNodeNameProperties(propInfo);

        ArrayList<String> changedProperties = new ArrayList<String>();
        String uniqueShortNameValue = getLatestValue(USE_SHORT_NODE_NAME,newProps,propInfo);

        // if unique short name is changed to true check all node name properties, otherwise check only changed
        if (isProprotyChanged(oldProps,newProps,USE_SHORT_NODE_NAME) && uniqueShortNameValue.equalsIgnoreCase("true")) {
            changedProperties.addAll(nodeNameProperties);
        } else {
            for(String prop : nodeNameProperties){
                if(isProprotyChanged(oldProps,newProps,prop)){
                    changedProperties.add(prop);
                }
            }
        }
        // no changed node name properties then return
        if (changedProperties.isEmpty()){
            return;
        }

        // check that node names can't be confused with node id of other nodes
        // ex: node name of vipr1 cannot be vipr2 but can be vipr1
        // ex: standalone can have any name
        for(String changedProp : changedProperties){
            String changedValue=getLatestValue(changedProp,newProps,propInfo).toLowerCase();

            // get node number from property (empty for standalone)
            String nodeNum=changedProp.replaceAll("\\D+","");

            // check if new value has name similar to node id and is not on standalone
            if (changedValue.matches("vipr\\d+") && !nodeNum.isEmpty()){

                // check that node name is not the same as it's node id (which would be ok)
                if (!changedValue.equals("vipr"+nodeNum)){
                    throw BadRequestException.badRequests.invalidNodeNameIsIdOfAnotherNode(changedProp,changedValue);
                }
            }
        }

        if (uniqueShortNameValue.equalsIgnoreCase("true")){
            // validates short names are unique (implies full hostname is unique as well)
            for(String changedProp : changedProperties){
                String changedValue=getLatestValue(changedProp,newProps,propInfo).split("\\.")[0];
                for(String prop : nodeNameProperties){
                    String compareValue=getLatestValue(prop,newProps,propInfo).split("\\.")[0];

                    if(!changedProp.equals(prop)&&changedValue.equals(compareValue)){
                        throw BadRequestException.badRequests.invalidNodeShortNamesAreNotUnique(changedValue,changedProp,prop);
                    }
                }
            }
        } else {
            // validates that full hostname is unique
            for(String changedProp : changedProperties){
                String changedValue=getLatestValue(changedProp,newProps,propInfo);
                for(String prop : nodeNameProperties){
                    String compareValue=getLatestValue(prop,newProps,propInfo);

                    if(!changedProp.equals(prop)&&changedValue.equals(compareValue)){
                        throw BadRequestException.badRequests.invalidNodeNamesAreNotUnique(changedValue,changedProp,prop);
                    }
                }
            }
        }
    }

    /**
     * After method is not needed, but must be implemented
     *
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps,PropertyInfoRestRep newProps){

    }

    public String getLatestValue(String property, PropertyInfoRestRep newProps, Map<String, String> propInfo) {
        String value = newProps.getProperty(property);
        if (value != null) {
            return value;
        }
        return propInfo.get(property);
    }

    public boolean isProprotyChanged(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps, String property) {
        String oldValue = oldProps.getProperty(property);
        String newValue = newProps.getProperty(property);

        if (newValue == null) {
            return false;
        }

        if (oldValue == null) {
            oldValue = "0";
        }

        if (oldValue.equals(newValue)) {
            return false;
        }

        return true;
    }


    private ArrayList<String> getNodeNameProperties(Map<String, String> propInfo) {
        ArrayList<String> nodeNameProperties = new ArrayList<String>();

        // check if standalone otherwise generate properties based on node count
        if (propInfo.containsKey(NODE_STANDALONE_NAME)) {
            nodeNameProperties.add(NODE_STANDALONE_NAME);
            return nodeNameProperties;
        }

        int nodeCount = Integer.parseInt(propInfo.get(NODE_COUNT));

        for (int i = 1; i <= nodeCount; i++) {
            nodeNameProperties.add("node_" + i + "_name");
        }

        return nodeNameProperties;
    }

}
