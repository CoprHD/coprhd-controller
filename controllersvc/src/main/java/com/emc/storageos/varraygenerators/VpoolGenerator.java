package com.emc.storageos.varraygenerators;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;

public class VpoolGenerator {
    private static Logger log = LoggerFactory.getLogger(VpoolGenerator.class);
    protected CoordinatorClient coordinator;
    protected DbClient dbClient;
    
    public VpoolGenerator(DbClient dbClient, CoordinatorClient coordinator) {
        this.dbClient = dbClient;
        this.coordinator = coordinator;
    }

    /**
     * Returns the first Virtual Pool (if any) matching the given label
     * @param label -- String label searched for
     * @return -- VirtualPool of null if there isn't a match
     */
    public VirtualPool getVpoolByName(String label) {
       List<VirtualPool> virtualPools = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, VirtualPool.class,
               PrefixConstraint.Factory.getFullMatchConstraint(VirtualPool.class, "label", label));
       if (!virtualPools.isEmpty()) {
           return virtualPools.get(0);
       }
       return null;
    }
    
    public VirtualPool makeVpoolFromTemplate(String labelPrefix, VpoolTemplate template) {
        VirtualPool vpool = new VirtualPool();
        vpool.setId(URIUtil.createId(VirtualPool.class));
        Map<String, String> attrMap = template.getAttrMap();
        for (String attribute : attrMap.keySet()) {
           if (attribute.equals("label")) {
               setValue(vpool, attribute, labelPrefix + attrMap.get(attribute), null);
           } else {
               setValue(vpool, attribute, attrMap.get(attribute), null);
           }
        }
        return vpool;
    }
    
    private void setValue(VirtualPool vpool, String attribute, String value, Set<String> values) {
        Class<? extends VirtualPool> vpoolClass = vpool.getClass();
        String methodName = "set" + attribute.substring(0,1).toUpperCase() + attribute.substring(1);
        try {   // String
            Method method = vpoolClass.getMethod(methodName, String.class);
            method.invoke(vpool,  value);
            return;
        } catch (NoSuchMethodException ex) {
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        } catch (InvocationTargetException ex) {
        }
        try {   // URI
            Method method = vpoolClass.getMethod(methodName, URI.class);
            method.invoke(vpool,  URI.create(value));
            return;
        } catch (NoSuchMethodException ex) {
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        } catch (InvocationTargetException ex) {
        }
        try {   // Boolean
            Method method = vpoolClass.getMethod(methodName, Boolean.class);
            method.invoke(vpool,  new Boolean(value));
            return;
        } catch (NoSuchMethodException ex) {
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        } catch (InvocationTargetException ex) {
        }
        try {   // Integer
            Method method = vpoolClass.getMethod(methodName, Integer.class);
            method.invoke(vpool,  new Integer(value));
            return;
        } catch (NoSuchMethodException ex) {
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        } catch (InvocationTargetException ex) {
        }
        try {   // StringSet
            methodName = "add" + attribute.substring(0,1).toUpperCase() + attribute.substring(1);
            Method method = vpoolClass.getMethod(methodName, Set.class);
            if (values == null) {
                String[] valueArray = value.split(",,");
                values = new HashSet<String>();
                values.addAll(Arrays.asList(valueArray));
            }
            method.invoke(vpool,  values);
            return;
        } catch (NoSuchMethodException ex) {
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        } catch (InvocationTargetException ex) {
        }
        log.info("unable to set attribute: " + attribute);
    }
}
