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

package com.emc.storageos.apidiff.serializer;

import com.emc.storageos.apidiff.Constants;
import com.emc.storageos.apidiff.Main;
import com.emc.storageos.apidiff.ServiceCatalogDiff;
import com.emc.storageos.apidiff.model.ApiChangeEnum;
import com.emc.storageos.apidiff.model.ApiDescriptor;
import com.emc.storageos.apidiff.model.ApiDescriptorDiff;
import com.emc.storageos.apidiff.model.ApiIdentifier;
import com.emc.storageos.apidiff.util.Pair;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Outputs API differences of all services to html file
 */
public class HtmlSerializerMultiPages extends AbstractSerializer {

    class ComponentView {

        final List<String> added = new ArrayList<String>();
        final List<String> changed = new ArrayList<String>();
        final List<String> removed = new ArrayList<String>();
    }

    private final Map<String, ComponentView> componentMap = new TreeMap<String, ComponentView>();

    public HtmlSerializerMultiPages(final List<ServiceCatalogDiff> diffList, File folder) {
        super(diffList,folder);
        file = new File(folder.getAbsolutePath() + File.separator + "apidiff-docs");
        if (!file.exists())
            file.mkdir();
    }

    @Override
    public void output() {
        String oldVersion = null, newVersion = null;
        for (ServiceCatalogDiff serviceCatalogDiff : diffList) {
            buildComponentList(serviceCatalogDiff);
            if (oldVersion == null)
                oldVersion = serviceCatalogDiff.getOldServiceCatalog().getVersion();
            if (newVersion == null)
                newVersion = serviceCatalogDiff.getNewServiceCatalog().getVersion();
        }

        // Create component pages
        outputComponents();

        // Creates index file
        outputIndex(oldVersion, newVersion);

    }

    private String[] getParts(String path) {
        String[] services = path.split(Constants.URL_PATH_SEPARATOR);

        String[] parts = new String[2];
        parts[1] = services[1];
        if (services.length == 2) {
            parts[2] = services[2];
        }
        else {
            parts[2] = "";
        }

        return parts;
    }

    private void buildComponentList(final ServiceCatalogDiff serviceCatalogDiff) {
        for (Map.Entry<ApiIdentifier, ApiDescriptor> entry : serviceCatalogDiff.getNewServiceCatalog().getApiMap().entrySet()) {
            getComponentView(entry.getKey().getPath()).added.add(
                    addNormalRecord(entry.getKey(), entry.getValue(),
                            serviceCatalogDiff.getNewServiceCatalog().getElementMap()));
        }

        for (Map.Entry<ApiIdentifier, ApiDescriptorDiff> entry : serviceCatalogDiff.getApiChangedMap().entrySet()) {
            getComponentView(entry.getKey().getPath()).changed.add(addComparisonRecord(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<ApiIdentifier, ApiDescriptor> entry :
                serviceCatalogDiff.getOldServiceCatalog().getApiMap().entrySet()) {
            getComponentView(entry.getKey().getPath()).removed.add(
                    addNormalRecord(entry.getKey(), entry.getValue(),
                            serviceCatalogDiff.getOldServiceCatalog().getElementMap()));
        }
    }

    /**
     * constructs summary of API changes
     */
    private void outputIndex(final String oldVersion, final String newVersion) {

        StringBuilder builder = new StringBuilder();
        String title = "API Differences";
        String subTitle = " Between ViPR " + oldVersion + " and " + newVersion;

        builder.append(HtmlSerializerHelper.buildHeader(title + subTitle));
        builder.append(HtmlSerializerHelper.buildBodyTitle(title, subTitle));
        builder.append(HtmlSerializerHelper.buildDivHeader("summary"));
        builder.append(HtmlSerializerHelper.buildContent("API", "summary", 2));
        builder.append(HtmlSerializerHelper.buildTableHeader());
        builder.append(HtmlSerializerHelper.buildTableHeaderRow(1,
                new Pair<String, Integer>("Service Category", 25),
                new Pair<String, Integer>("Added Number", 25),
                new Pair<String, Integer>("Changed Number", 25),
                new Pair<String, Integer>("Removed Number", 25)));

        int addedAll = 0, removedAll = 0, changedAll = 0;
        for (Map.Entry<String, ComponentView> entry : componentMap.entrySet()) {
            String componentName = entry.getKey();
            int added = entry.getValue().added.size();
            int changed = entry.getValue().changed.size();
            int removed = entry.getValue().removed.size();
            String linkPage = componentName.replaceAll(" ", "") + "_diff.html";
            builder.append(HtmlSerializerHelper.buildTableRow(1,
                    new Pair<String, Integer>(
                            HtmlSerializerHelper.buildLink(linkPage, componentName), 25),
                    new Pair<String, Integer>(Integer.toString(added), 25),
                    new Pair<String, Integer>(Integer.toString(changed), 25),
                    new Pair<String, Integer>(Integer.toString(removed), 25)));
            addedAll += added;
            changedAll += changed;
            removedAll += removed;
        }

        builder.append(HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>("Total", 25),
                new Pair<String, Integer>(Integer.toString(addedAll), 25),
                new Pair<String, Integer>(Integer.toString(changedAll), 25),
                new Pair<String, Integer>(Integer.toString(removedAll), 25)));

        builder.append(HtmlSerializerHelper.buildTableTailer());
        builder.append(HtmlSerializerHelper.buildDivTailer());
        builder.append(HtmlSerializerHelper.buildTailer());
        outputToFile(file.getAbsolutePath() + File.separator + "index.html", builder.toString());
    }

    private void outputComponents() {

        for (Map.Entry<String, ComponentView> entry : componentMap.entrySet()) {
            StringBuilder builder = new StringBuilder();
            builder.append(HtmlSerializerHelper.buildHeader(entry.getKey()));
            builder.append(HtmlSerializerHelper.buildBodyHeader());
            builder.append(HtmlSerializerHelper.buildBodyTitle(entry.getKey(), null));
            if (entry.getValue().added.size() > 0) {
                Collections.sort(entry.getValue().added);
                // Added APIs
                builder.append(HtmlSerializerHelper.buildDivHeader(ApiChangeEnum.Added.toString()));
                builder.append(HtmlSerializerHelper.buildContent(ApiChangeEnum.Added.toString(), "REST API", 2));
                builder.append(HtmlSerializerHelper.buildTableHeader());
                for (String added : entry.getValue().added)
                    builder.append(added);
                builder.append(HtmlSerializerHelper.buildTableTailer());
                builder.append(HtmlSerializerHelper.buildDivTailer());
            }
            if (entry.getValue().changed.size() > 0) {
                Collections.sort(entry.getValue().changed);
                // Changed APIs
                builder.append(HtmlSerializerHelper.buildSideLine());
                builder.append(HtmlSerializerHelper.buildDivHeader(ApiChangeEnum.Changed.toString()));
                builder.append(HtmlSerializerHelper.buildContent(ApiChangeEnum.Changed.toString(), "REST API", 2));
                builder.append(HtmlSerializerHelper.buildTableHeader());
                for (String changed : entry.getValue().changed)
                    builder.append(changed);
                builder.append(HtmlSerializerHelper.buildTableTailer());
                builder.append(HtmlSerializerHelper.buildDivTailer());
            }
            if (entry.getValue().removed.size() > 0) {
                // Removed APIs
                builder.append(HtmlSerializerHelper.buildSideLine());
                builder.append(HtmlSerializerHelper.buildDivHeader(ApiChangeEnum.Removed.toString()));
                builder.append(HtmlSerializerHelper.buildContent(ApiChangeEnum.Removed.toString(), "REST API", 2));
                builder.append(HtmlSerializerHelper.buildTableHeader());
                for (String removed : entry.getValue().removed)
                    builder.append(removed);
                builder.append(HtmlSerializerHelper.buildTableTailer());
                builder.append(HtmlSerializerHelper.buildDivTailer());
            }
            // add tailer
            builder.append(HtmlSerializerHelper.buildTailer());
            String fileName = file.getAbsolutePath() + File.separator
                    + entry.getKey().replaceAll(" ", "") + "_diff.html";
            outputToFile(fileName, builder.toString());
        }

    }

    private String addNormalRecord(final ApiIdentifier apiIdentifier,
                                   final ApiDescriptor apiResource,
                                   final Map<String, String> elementMap) {

        // Constructs html content for added/removed apis
        StringBuilder builder = new StringBuilder();
        builder.append(HtmlSerializerHelper.buildTableHeader());
        builder.append(HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>("URI", 15),
                new Pair<String, Integer>(apiIdentifier.getHttpMethod() + " "
                        + apiIdentifier.getPath(), 85)
        ));
        builder.append(HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>("Parameter", 15),
                new Pair<String, Integer>(apiResource.getParameters().toString(), 85)
        ));

        String requestElement = elementMap.get(apiResource.getRequestElement());
        if (requestElement != null) {
            requestElement = String.format("%s%s%s", Constants.CODE_PREFIX,
                    StringEscapeUtils.escapeHtml(requestElement), Constants.CODE_SUFFIX);
        } else {
            requestElement = "";
        }
        builder.append(HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>("Request Body", 15),
                new Pair<String, Integer>(requestElement, 85)
        ));

        String responseElement = elementMap.get(apiResource.getResponseElement());
        if (responseElement != null) {
            responseElement = String.format("%s%s%s", Constants.CODE_PREFIX,
                    StringEscapeUtils.escapeHtml(responseElement), Constants.CODE_SUFFIX);
        } else {
            responseElement = "";
        }
        builder.append(HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>("Response Body", 15),
                new Pair<String, Integer>(responseElement, 85)
        ));
        builder.append(HtmlSerializerHelper.buildTableTailer());

        return HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>(getSubServiceName(apiIdentifier.getPath()), 15),
                new Pair<String, Integer>(builder.toString(), 85)
        );

    }

    private String addComparisonRecord(final ApiIdentifier apiIdentifier,
                                       final ApiDescriptorDiff apiDescriptorDiff) {

        // Constructs html content for added/removed apis
        StringBuilder builder = new StringBuilder();
        builder.append(HtmlSerializerHelper.buildTableHeader());
        builder.append(HtmlSerializerHelper.buildTableHeaderRow(1,
                new Pair<String, Integer>("Item", 15),
                new Pair<String, Integer>("Old", 40),
                new Pair<String, Integer>("New", 45)
        ));
        builder.append(HtmlSerializerHelper.buildTableRow(2,
                new Pair<String, Integer>("URI", 15),
                new Pair<String, Integer>(apiIdentifier.getHttpMethod() + " "
                        + apiIdentifier.getPath(), 85)
        ));

        builder.append(addChangedField("Parameter", apiDescriptorDiff.getParamDiff()));
        builder.append(addChangedField("Request Body", apiDescriptorDiff.getRequestElementDiff()));
        builder.append(addChangedField("Response Body", apiDescriptorDiff.getResponseElementDiff()));
        builder.append(HtmlSerializerHelper.buildTableTailer());

        return HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>(getSubServiceName(apiIdentifier.getPath()), 15),
                new Pair<String, Integer>(builder.toString(), 85)
        );
    }

    private String addChangedField(final String name, Pair<String, String> pair) {
        if (name == null || pair == null)
            return "";

        String left=Constants.CODE_PREFIX;
        if (pair.getLeft() != null)
            left += StringEscapeUtils.escapeHtml(pair.getLeft());
        left += Constants.CODE_SUFFIX;

        String right=Constants.CODE_PREFIX;
        if (pair.getRight() != null)
            right += StringEscapeUtils.escapeHtml(pair.getRight());
        right += Constants.CODE_SUFFIX;

        return HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>(name, 15),
                new Pair<String, Integer>(left, 40),
                new Pair<String, Integer>(right, 45)
        );
    }

    private ComponentView getComponentView(String path) {
        String serviceName = "";
        String componentName = "";

        String[] parts = path.split(Constants.URL_PATH_SEPARATOR);
        serviceName = parts[1];
        if (parts.length > 2) {
            componentName = parts[2].startsWith("{") ? "" : parts[2];
        }

        String key = null;
        for (Map.Entry<String, List<Pair<String, String>>> entry : Main.serviceNamingMap.entrySet()) {
            for (Pair<String, String> item : entry.getValue()) {
                String tmp = serviceName + "." + componentName;
                if (item.getLeft().equals(tmp) || item.getLeft().equals(serviceName)) {
                    key = entry.getKey();
                    break;
                }
            }
            if (key != null)
                break;
        }

        if (key == null)
            throw new RuntimeException("Please check api PATH: /"+serviceName + "/" + componentName);

        ComponentView componentView = componentMap.get(key);
        if (componentView == null) {
            componentView = new ComponentView();
            componentMap.put(key, componentView);
        }
        return componentView;
    }

    private String getSubServiceName(String path) {
        String serviceName = "";
        String componentName = "";

        String[] parts = path.split(Constants.URL_PATH_SEPARATOR);
        serviceName = parts[1];
        if (parts.length > 2) {
            componentName = parts[2].startsWith("{") ? "" : parts[2];
        }

        for (Map.Entry<String, List<Pair<String, String>>> entry : Main.serviceNamingMap.entrySet()) {
            for (Pair<String, String> item : entry.getValue()) {
                String tmp = serviceName + "." + componentName;
                if (item.getLeft().equals(tmp) || item.getLeft().equals(serviceName)) {
                    return item.getRight();
                }
            }
        }

        throw new RuntimeException("Please check api PATH: /"+serviceName + "/" + componentName);
    }

    private static void outputToFile(final String fileName, final String content) {
        try {
            File file = new File(fileName);
            FileUtils.write(file, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Can't write result file: " + fileName, ex);
        }
    }



}
