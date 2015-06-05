/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.generating;

import com.emc.apidocs.model.ApiService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Reads the TOC grouping file and organizes the ApiServices into the correct structure for the TOC
 *
 */
public class ApiReferenceTocOrganizer {
    private static final String COMMENT_MARKER = "#";
    private static final String TITLE_MARKER = "=";

    public Map<String, List<String>> groups = Maps.newLinkedHashMap();
    public Map<String, String> serviceToGroup = Maps.newHashMap();

    public ApiReferenceTocOrganizer(File file) {
        try {
            List<String> services = null;
            String currentGroup = null;
            for (String line : IOUtils.readLines(new FileInputStream(file))) {
                if (line.startsWith(TITLE_MARKER)) {
                    services = Lists.newArrayList();
                    currentGroup = line.substring(1);
                    groups.put(currentGroup, services);
                }
                else if (!line.equals("") && !line.startsWith(COMMENT_MARKER)) {
                    services.add(line);
                    serviceToGroup.put(line, currentGroup);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading Group File "+file,e);
        }
    }

    public Map<String, List<ApiService>> organizeServices(List<ApiService> services) {
        List<ApiService> allServices = Lists.newArrayList(services);
        Map<String, List<ApiService>> sortedServices = Maps.newLinkedHashMap();

        removeObjectDataServices(allServices); // Data Services appear in a different section

        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            List<ApiService> categoryServices = Lists.newArrayList();

            List<ApiService> foundServices = Lists.newArrayList();
            for (String serviceName : entry.getValue()) {    // Loop through services for this category

                for (ApiService service : allServices) { // Find the service in the list of services
                    if (service.getFqJavaClassName().equals(serviceName) || service.javaClassName.equals(serviceName)) {
                        foundServices.add(service);
                        categoryServices.add(service);
                    }
                }
            }
            if (!categoryServices.isEmpty()) { // Only include non-empty categories
                sortedServices.put(entry.getKey(), categoryServices);
            }
            allServices.removeAll(foundServices);
        }

        // Generate an Exception for non-assigned services
        if (!allServices.isEmpty()) {
            String servicesLeft = "";
            for (ApiService service : allServices) {
                servicesLeft=servicesLeft+"\n"+service.getFqJavaClassName();
            }

            throw new RuntimeException(servicesLeft+" not assigned to any TOC category in ApiReferenceGrouping.txt");
        }

        return sortedServices;
    }

    private void removeObjectDataServices(List<ApiService> services) {
        List<ApiService> dataservices = Lists.newArrayList();
        for (ApiService service : services) {
            if (service.packageName.endsWith("s3.operation") ||
                service.packageName.endsWith("atmos.operation") ||
                service.packageName.endsWith("swift.operation")) {
                dataservices.add(service);

            }
        }

        services.removeAll(dataservices);
    }
}
