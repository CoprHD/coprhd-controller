/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff;

import com.emc.storageos.apidiff.model.ApiDescriptor;
import com.emc.storageos.apidiff.model.ApiDescriptorDiff;
import com.emc.storageos.apidiff.model.ApiIdentifier;
import com.emc.storageos.apidiff.model.ServiceCatalog;
import com.emc.storageos.apidiff.util.Pair;
import com.emc.storageos.apidiff.util.XmlDiff;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Records difference of one REST API service between old version and new version, it includes added,
 * changed and removed APIs.
 */
public class ServiceCatalogDiff {

    private final ServiceCatalog oldServiceCatalog;
    private final ServiceCatalog newServiceCatalog;

    // The map to store changed api
    private final Map<ApiIdentifier, ApiDescriptorDiff> apiChangedMap =
            new HashMap<ApiIdentifier, ApiDescriptorDiff>();

    public ServiceCatalogDiff(ServiceCatalog oldServiceCatalog, ServiceCatalog newServiceCatalog) {
        this.oldServiceCatalog = oldServiceCatalog;
        this.newServiceCatalog = newServiceCatalog;
    }

    public Map<ApiIdentifier, ApiDescriptorDiff> getApiChangedMap() {
        return apiChangedMap;
    }

    public ServiceCatalog getOldServiceCatalog() {
        return oldServiceCatalog;
    }

    public ServiceCatalog getNewServiceCatalog() {
        return newServiceCatalog;
    }

    /**
     * Computes differences between old repository and new repository
     */
    public void generateDiff() {

        Map<String, Pair<String, String>> elementChangedMap = new HashMap<String, Pair<String, String>>();
        for (Map.Entry<String, String> entry : newServiceCatalog.getElementMap().entrySet()) {
            Pair<String, String> diff =
                    compareXml(oldServiceCatalog.getElementMap().get(entry.getKey()), entry.getValue());
            if (diff != null)
                elementChangedMap.put(entry.getKey(), diff);
        }

        Iterator<Map.Entry<ApiIdentifier, ApiDescriptor>> newApiMapIterator =
                newServiceCatalog.getApiMap().entrySet().iterator();
        while (newApiMapIterator.hasNext()) {
            Map.Entry<ApiIdentifier, ApiDescriptor> entry = newApiMapIterator.next();
            ApiDescriptor oldApiResource = oldServiceCatalog.getApiMap().get(entry.getKey());
            if ( oldApiResource == null)
                continue;

            Pair<String, String> paramDiff = compareParameter(oldApiResource.getParameters(),
                    entry.getValue().getParameters());
            Pair<String, String> requestDiff =
                    elementChangedMap.get(entry.getValue().getRequestElement());
            Pair<String, String> responseDiff =
                    elementChangedMap.get(entry.getValue().getResponseElement());

            if (paramDiff != null || requestDiff != null || responseDiff != null)
                apiChangedMap.put(entry.getKey(),
                        new ApiDescriptorDiff(paramDiff, requestDiff, responseDiff));

            newApiMapIterator.remove();
            oldServiceCatalog.getApiMap().remove(entry.getKey());
        }

        oldServiceCatalog.update();
        newServiceCatalog.update();

    }

    /**
     * Compares two parameter list in old resource and new resource
     * @param oldParameters
     *          The list of old parameters
     * @param newParameters
     *          The list of new parameters
     * @return pair of different parameter string if they are different, else null
     */
    public Pair<String, String> compareParameter(List<String> oldParameters, List<String> newParameters) {
        Iterator<String> paramIter = newParameters.iterator();
        while (paramIter.hasNext()) {
            String param = paramIter.next();
            int index = oldParameters.indexOf(param);
            if (index != -1) {
                paramIter.remove();
                oldParameters.remove(index);
            }
        }

        if (oldParameters.size() != 0 || newParameters.size() != 0)
            return new Pair<String, String>(
                    Arrays.toString(oldParameters.toArray(new String[oldParameters.size()])),
                    Arrays.toString(newParameters.toArray(new String[newParameters.size()])));

        return null;
    }

    /**
     * Compares two string which are well formed XML
     * @param oldXml
     *          The old xml string
     * @param newXml
     *          The new xml string
     * @return pair of different xml string if they are different, else null
     */
    public Pair<String, String> compareXml(String oldXml, String newXml) {

        if (oldXml == null && newXml == null)
            return null;
        if (oldXml == null)
            return new Pair<String, String>(null, newXml);
        if (newXml == null)
            return new Pair<String, String>(oldXml, null);

        SAXBuilder saxBuilder = new SAXBuilder();
        saxBuilder.setReuseParser(true);
        Document oldDocument = null, newDocument = null;
        try {
            oldDocument = saxBuilder.build(new StringReader(oldXml));
            newDocument = saxBuilder.build(new StringReader(newXml));
        } catch (Exception ex) {
            System.err.println("Invalid XML content:\n " + oldXml + "\n and: \n" + newXml);
            ex.printStackTrace();
        }

        if (oldDocument == null || newDocument == null)
            return null;

        return XmlDiff.compareXml(oldDocument, newDocument);

    }

}
