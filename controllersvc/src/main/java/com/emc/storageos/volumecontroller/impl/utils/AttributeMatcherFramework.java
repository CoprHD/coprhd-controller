/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCodeException;
import com.emc.storageos.volumecontroller.AttributeMatcher;

/**
 * AttributeMatcherFramework is an framework class which abstract the matcher execution.
 * All clients should use this class to invoke matchAttributes to run matcher algorithm.
 * All the matchers are grouped by its nature. Each group will be executed in the order
 * they have added to the LinkedList. Since the beans are injected to a LinkedList using
 * spring configuration, we always guarantee the order.
 * The current sequence of attributeMatcher execution:
 * 1. ActivePoolMatcher
 * 2. NeighborhoodsMatcher
 * 3. CoSTypeAttributeMatcher
 * 4. ProtocolsAttrMatcher
 * 
 */
public class AttributeMatcherFramework implements ApplicationContextAware {

    private static final Logger _logger = LoggerFactory
            .getLogger(AttributeMatcherFramework.class);
    private static volatile ApplicationContext _context;

    @Override
    public void setApplicationContext(ApplicationContext appContext)
            throws BeansException {
        _context = appContext;
    }

    public static ApplicationContext getApplicationContext() {
        return _context;
    }

    /**
     * Match all attributes of CoS container & volumeParam container values against the given pools.
     * 
     * @param allPools : list of pools
     * @param cos : vpool
     * @param objValueToCompare : volumeParam container values.
     * @param dbClient
     * @param matcherGroupName : groupName to execute the matchers.matchers are grouped by its relativity
     * @param errorMessage : will contain error message
     * @return Returns list of matched StoragePool instances
     */
    public List<StoragePool> matchAttributes(List<StoragePool> allPools, Map<String, Object> attributeMap,
            DbClient dbClient, CoordinatorClient coordinator, String matcherGroupName, StringBuffer errorMessage) {

        List<StoragePool> matchedPools = new ArrayList<StoragePool>();
        if (!CollectionUtils.isEmpty(allPools)) {
            matchedPools.addAll(allPools);
            try {
                _logger.info("Starting execution of {} group matchers .", matcherGroupName);
                @SuppressWarnings("unchecked")
                List<AttributeMatcher> attrMatcherList = (List<AttributeMatcher>) getBeanFromContext(matcherGroupName);
                ObjectLocalCache cache = new ObjectLocalCache(dbClient);
                initializeCommonReferencesForAllMatchers(cache, coordinator);
                errorMessage.setLength(0); // Clear the existing content before matcher
                for (AttributeMatcher matcher : attrMatcherList) {
                    int poolSizeAtTheStart = matchedPools.size();
                    if (!matchedPools.isEmpty()) {
                        _logger.debug("passing {} pools to match", matchedPools.size());
                        matchedPools = matcher.runMatchStoragePools(matchedPools, attributeMap, errorMessage);
                        if (matchedPools.isEmpty()) {
                            _logger.info(String.format("Failed to find match because of %s",
                                    matcher.getClass().getSimpleName()));
                        } else if (matchedPools.size() < poolSizeAtTheStart) {
                            _logger.info(String.format("%s eliminated %d pools from the matched list",
                                    matcher.getClass().getSimpleName(), poolSizeAtTheStart - matchedPools.size()));
                        }
                    } else {
                        _logger.info("No storage pools found matching with attributeMap passed");
                        break;
                    }
                }
                cache.clearCache();
            } catch (Exception ex) {
                // Clearing the matched pools as there is an exception occurred while processing.
                matchedPools.clear();
                _logger.error("Exception occurred while matching pools with vPools", ex);
            } finally {
                _logger.info("Ended execution of {} group matchers .", matcherGroupName);
            }
        } else {
            String message = "Virtual pool does not have matching Storage pool. ";
            if (errorMessage != null && !errorMessage.toString().contains(message)) {
                errorMessage.append(message);
            }
            _logger.error(errorMessage.toString());
        }
        return matchedPools;
    }

    /**
     * Method will iterate through all AttributeMatchers (not a subset) and apply common references to them.
     *
     * @param cache [IN] - ObjectLocalCache to be applied to matchers
     * @param coordinator [IN] - Coordinator reference to be applied to matchers
     */
    private void initializeCommonReferencesForAllMatchers(ObjectLocalCache cache, CoordinatorClient coordinator) {
        Map<String, AttributeMatcher> uniqueMatcherMap = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<AttributeMatcher> vpoolMatchers = (List<AttributeMatcher>) getBeanFromContext(AttributeMatcher.VPOOL_MATCHERS);
        for (AttributeMatcher matcher : vpoolMatchers) {
            uniqueMatcherMap.put(matcher.getClass().getSimpleName(), matcher);
        }

        @SuppressWarnings("unchecked")
        List<AttributeMatcher> basicMatchers = (List<AttributeMatcher>) getBeanFromContext(AttributeMatcher.BASIC_PLACEMENT_MATCHERS);
        for (AttributeMatcher matcher : basicMatchers) {
            uniqueMatcherMap.put(matcher.getClass().getSimpleName(), matcher);
        }

        @SuppressWarnings("unchecked")
        List<AttributeMatcher> placementMatchers = (List<AttributeMatcher>) getBeanFromContext(AttributeMatcher.PLACEMENT_MATCHERS);
        for (AttributeMatcher matcher : placementMatchers) {
            uniqueMatcherMap.put(matcher.getClass().getSimpleName(), matcher);
        }

        // The matchers have been gathered in the Map by class name, making it a unique list of
        // matchers. Now iterate through them applying there references ...
        for (AttributeMatcher matcher : uniqueMatcherMap.values()) {
            matcher.setCoordinatorClient(coordinator);
            matcher.setObjectCache(cache);
        }
    }

    /**
     * Sometimes context is not loading properly resulting the beanFactory to null.
     * To avoid this, we should reload the context using refresh.
     * and the return the bean by its matcherGroupName.
     * 
     * @param matcherGroupName
     * @return beanObj
     */
    private Object getBeanFromContext(String matcherGroupName) {
        Object beanObj = _context.getBean(matcherGroupName);
        if (null == beanObj) {
            _logger.error("No bean found for groupName {0} to match Pools for give attributesMap", matcherGroupName);
            throw new ServiceCodeException(ServiceCode.CONTROLLER_STORAGE_ERROR,
                    "No bean found for groupName {0} to match Pools for give attributesMap", new Object[] { matcherGroupName });
        }
        return beanObj;
    }

    /**
     * Find the available attributes in a given varray.
     * 
     * @param vArrayId
     * @param neighborhoodPools
     * @param dbClient
     * @param matcherGroupName
     */
    public Map<String, Set<String>> getAvailableAttributes(URI vArrayId, List<StoragePool> neighborhoodPools,
            ObjectLocalCache cache, String matcherGroupName) {
        Map<String, Set<String>> vArrayAvailableAttrs = new HashMap<String, Set<String>>();
        try {
            @SuppressWarnings("unchecked")
            List<AttributeMatcher> attrMatcherList = (List<AttributeMatcher>) getBeanFromContext(matcherGroupName);
            for (AttributeMatcher matcher : attrMatcherList) {
                matcher.setObjectCache(cache);
                Map<String, Set<String>> availableAttribute = matcher.getAvailableAttribute(neighborhoodPools,
                        vArrayId);
                if (!availableAttribute.isEmpty()) {
                    _logger.info("Found available attributes using matcher {}", matcher);
                    vArrayAvailableAttrs.putAll(availableAttribute);
                }
            }
            _logger.info("Found {} available attributes for vArray {}", vArrayAvailableAttrs, vArrayId);
        } catch (Exception ex) {
            _logger.error("Exception occurred while getting available attributes for vArray {}", vArrayId, ex);
            vArrayAvailableAttrs.clear();
            throw new ServiceCodeException(ServiceCode.CONTROLLER_STORAGE_ERROR,
                    "Exception occurred while getting available attributes for vArray.", new Object[] { vArrayId });

        }
        return vArrayAvailableAttrs;
    }
}
