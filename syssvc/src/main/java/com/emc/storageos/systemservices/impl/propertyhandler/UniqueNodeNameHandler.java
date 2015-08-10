/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.*;
import com.emc.storageos.svcs.errorhandling.resources.*;
import org.slf4j.*;

import java.util.*;

public class UniqueNodeNameHandler implements UpdateHandler{
    private static final Logger _log = LoggerFactory.getLogger(UniqueNodeNameHandler.class);

    private static final String USE_SHORT_NODE_NAME= "use_short_node_name";
    private static final String NODE_COUNT = "node_count";
    private static final String NODE_STANDALONE_NAME = "node_standalone_name";

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
        ArrayList<String> nodeNameProperties = getNodeNameProperties(newProps);
        ArrayList<String> changedProperties = new ArrayList<String>();
        String uniqueShortNameValue = newProps.getProperty(USE_SHORT_NODE_NAME);

        //if unique short name is changed to true check all properties
        if (isProprotyChanged(oldProps,newProps,USE_SHORT_NODE_NAME) && uniqueShortNameValue.equalsIgnoreCase("true")) {
            changedProperties.addAll(nodeNameProperties);
        } else {
            for(String prop : nodeNameProperties){
                if(isProprotyChanged(oldProps,newProps,prop)){
                    changedProperties.add(prop);
                }
            }
        }

        if (changedProperties.isEmpty()){
            return;
        }

        if (uniqueShortNameValue.equalsIgnoreCase("true")){
            //validates short names are unique (implies full hostname is unique as well)
            for(String changedProp : changedProperties){
                String changedValue=newProps.getProperty(changedProp).split("\\.")[0];
                for(String prop : nodeNameProperties){
                    String compareValue=newProps.getProperty(prop).split("\\.")[0];

                    if(!changedProp.equals(prop)&&changedValue.equals(compareValue)){
                        throw BadRequestException.badRequests.invalidNodeShortNamesAreNotUnique(changedValue,changedProp,prop);
                    }
                }
            }
        } else {
            //validates that full hostname is unique
            for(String changedProp : changedProperties){
                String changedValue=newProps.getProperty(changedProp);
                for(String prop : nodeNameProperties){
                    String compareValue=newProps.getProperty(prop);

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


    private ArrayList<String> getNodeNameProperties(PropertyInfoRestRep newProps) {
        ArrayList<String> nodeNameProperties = new ArrayList<String>();

        //check if standalone otherwise generate properties based on node count
        if (newProps.getProperty(NODE_STANDALONE_NAME) != null) {
            nodeNameProperties.add(NODE_STANDALONE_NAME);
            return nodeNameProperties;
        }

        int nodeCount = Integer.parseInt(newProps.getProperty(NODE_COUNT));

        for (int i = 1; i <= nodeCount; i++) {
            nodeNameProperties.add("node_" + i + "_name");
        }

        return nodeNameProperties;
    }

}
