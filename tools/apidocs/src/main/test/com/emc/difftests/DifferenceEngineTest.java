/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.difftests;

import com.emc.apidocs.differencing.DifferenceEngine;
import com.emc.apidocs.generating.ApiReferenceTocOrganizer;
import com.emc.apidocs.model.ApiDifferences;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.emc.apidocs.model.ApiServiceChanges;
import com.emc.apidocs.tools.MetaData;

import java.io.File;
import java.util.List;
import java.util.Map;

public class DifferenceEngineTest {

    public static void main(String[] args) throws Exception {
        List<ApiService> oldApi = MetaData.load(EncunciationReaderTests.class.getResourceAsStream("MetaData-1.1.json"));
        List<ApiService> newApi = MetaData.load(EncunciationReaderTests.class.getResourceAsStream("MetaData-2.0.json"));

        ApiDifferences differences = DifferenceEngine.calculateDifferences(oldApi, newApi);

        ApiReferenceTocOrganizer organizer = new ApiReferenceTocOrganizer(new File(
                "/Users/maddid/SourceCode/bourne/vipr-controller/tools/apidocs/src/content/reference/ApiReferenceGrouping.txt"));
        Map<String, List<ApiService>> newServicesToc = organizer.organizeServices(differences.newServices);
        Map<String, List<ApiService>> removedServicesToc = organizer.organizeServices(differences.removedServices);

        System.out.println("\n===== NEW SERVICES:");
        dumpServices(newServicesToc);

        System.out.println("\n===== SERVICE CHANGES");
        for (ApiServiceChanges changes : differences.modifiedServices) {
            System.out.println(changes.service.getFqJavaClassName());
            if (!changes.newMethods.isEmpty()) {
                System.out.println("---- NEW METHODS");
                for (ApiMethod apiMethod : changes.newMethods) {
                    System.out.println("-- " + apiMethod.httpMethod + " " + apiMethod.path);
                }
            }

            if (!changes.removedMethods.isEmpty()) {
                System.out.println("---- REMOVED METHODS");
                for (ApiMethod apiMethod : changes.removedMethods) {
                    System.out.println("-- " + apiMethod.httpMethod + " " + apiMethod.path);
                }
            }

        }
    }

    private static void dumpServices(Map<String, List<ApiService>> tocs) {
        for (Map.Entry<String, List<ApiService>> toc : tocs.entrySet()) {
            System.out.println(toc.getKey().toUpperCase());
            for (ApiService newService : toc.getValue()) {
                System.out.println(" - " + newService.getFqJavaClassName() + "  " + newService.getTitle() + " " + newService.path);
            }
        }
    }
}
