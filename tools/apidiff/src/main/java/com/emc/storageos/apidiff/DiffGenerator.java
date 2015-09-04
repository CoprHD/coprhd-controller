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

import com.emc.storageos.apidiff.model.ServiceCatalog;
import com.emc.storageos.apidiff.model.ServiceCatalogBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Main class to generate diff document
 */
public class DiffGenerator {

    private DiffGenerator() {
    }

    /**
     * Generate API differences list for all valid services, such as: apisvc, objcontrollersvc, syssvc and so on.
     *
     * @param oldFolder
     *            The folder of old version api files
     * @param newFolder
     *            The folder of new version api files
     * @return service diff list
     */
    public static List<ServiceCatalogDiff> generate(File oldFolder, File newFolder) {
        List<ServiceCatalogDiff> diffList = new ArrayList<ServiceCatalogDiff>();
        List<ServiceCatalog> oldApiList = generateRestServiceList(oldFolder);
        List<ServiceCatalog> newApiList = generateRestServiceList(newFolder);

        for (ServiceCatalog newServiceCatalog : newApiList) {
            String serviceName = newServiceCatalog.getServiceName();
            boolean found = false;
            for (ServiceCatalog oldServiceCatalog : oldApiList) {
                if (serviceName.equals(oldServiceCatalog.getServiceName())) {
                    ServiceCatalogDiff serviceCatalogDiff =
                            new ServiceCatalogDiff(oldServiceCatalog, newServiceCatalog);
                    serviceCatalogDiff.generateDiff();
                    diffList.add(serviceCatalogDiff);
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.println("Can't find original version of"
                        + serviceName + "API, please check your file name");
            }
        }

        Collections.sort(diffList, new Comparator<ServiceCatalogDiff>() {
            @Override
            public int compare(ServiceCatalogDiff o1, ServiceCatalogDiff o2) {
                return o1.getNewServiceCatalog().getServiceName().compareTo(
                        o2.getNewServiceCatalog().getServiceName());
            }
        });

        return diffList;
    }

    /**
     * loads and generates all ServiceCatalogs according to xml files under folder
     * 
     * @param folder
     *            The folder which includes API xml files
     * @return the list of ServiceCatalog
     */
    private static List<ServiceCatalog> generateRestServiceList(File folder) {
        List<ServiceCatalog> apiList = new ArrayList<ServiceCatalog>();
        Collection<File> files = FileUtils.listFiles(folder,
                new String[] { Constants.XML_FILE_SUFFIX }, false);
        for (File file : files) {
            System.out.println("Loading: " + file.getAbsolutePath());
            apiList.add(ServiceCatalogBuilder.build(file));
        }
        if (apiList.isEmpty())
            return null;
        return apiList;
    }
}
