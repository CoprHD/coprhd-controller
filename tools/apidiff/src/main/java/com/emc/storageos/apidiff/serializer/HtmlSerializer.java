/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.apidiff.serializer;

import com.emc.storageos.apidiff.ServiceCatalogDiff;
import com.emc.storageos.apidiff.model.ApiDescriptor;
import com.emc.storageos.apidiff.model.ApiDescriptorDiff;
import com.emc.storageos.apidiff.model.ApiIdentifier;
import com.emc.storageos.apidiff.Constants;
import com.emc.storageos.apidiff.util.Pair;
import com.emc.storageos.apidiff.model.ServiceCatalog;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Outputs API differences of all services to html file
 */
public class HtmlSerializer extends AbstractSerializer {

    private final Map<String, StringBuilder> componentMap = new HashMap<String, StringBuilder>();

    public HtmlSerializer(List<ServiceCatalogDiff> serviceCatalogDiffList, File folder) {
        super(serviceCatalogDiffList, folder);
        file = new File(folder.getAbsolutePath() + File.separator + "apidiff.html");
        if (file.exists()) {
            file.delete();
        }
    }

    @Override
    public void output() {

        StringBuilder builder = new StringBuilder();
        String title = "API Differences";
        String subTitle = " Between ViPR " + diffList.get(0).getOldServiceCatalog().getVersion()
                + " and " + diffList.get(0).getNewServiceCatalog().getVersion();

        builder.append(HtmlSerializerHelper.buildHeader(title + subTitle));
        builder.append(HtmlSerializerHelper.buildBodyTitle(title, subTitle));

        // Inserts api summary
        builder.append(createSummary());

        // Inserts component info
        String components = createComponentChanges();

        builder.append(createComponentList());

        builder.append(components);

        builder.append(HtmlSerializerHelper.buildTailer());
        try {
            FileUtils.write(file, builder.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("Can't write result file: " + file.getAbsolutePath(), ex);
        }

    }

    /**
     * constructs summary of API changes
     * 
     * @return format string of API changes summary
     */
    public String createSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append(HtmlSerializerHelper.buildDivHeader("summary"));
        builder.append(HtmlSerializerHelper.buildContent("API", "summary", 2));
        builder.append(HtmlSerializerHelper.buildTableHeader());
        builder.append(HtmlSerializerHelper.buildTableHeaderRow(1,
                new Pair<String, Integer>("Service Category", 25),
                new Pair<String, Integer>("Added Number", 25),
                new Pair<String, Integer>("Changed Number", 25),
                new Pair<String, Integer>("Removed Number", 25)));

        int addedAll = 0, removedAll = 0, changedAll = 0;
        for (ServiceCatalogDiff serviceCatalogDiff : diffList) {
            String serviceName = serviceCatalogDiff.getOldServiceCatalog().getServiceName();
            int added = serviceCatalogDiff.getNewServiceCatalog().getApiMap().size();
            int changed = serviceCatalogDiff.getApiChangedMap().size();
            int removed = serviceCatalogDiff.getOldServiceCatalog().getApiMap().size();
            builder.append(HtmlSerializerHelper.buildTableRow(1,
                    new Pair<String, Integer>(HtmlSerializerHelper.buildInPageLink(serviceName), 25),
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
        return builder.toString();
    }

    /**
     * constructs component list which API are changed
     * 
     * @return html format string of API changed component list
     */
    public String createComponentList() {
        StringBuilder builder = new StringBuilder();
        builder.append(HtmlSerializerHelper.buildDivHeader("components"));
        builder.append(HtmlSerializerHelper.buildContent("Component", "list", 2));
        builder.append(HtmlSerializerHelper.buildListHeader());
        SortedSet<String> sortedSet = new TreeSet<String>(componentMap.keySet());
        for (String componentName : sortedSet) {
            builder.append(HtmlSerializerHelper.buildListItem(HtmlSerializerHelper.buildInPageLink(componentName)));
        }
        builder.append(HtmlSerializerHelper.buildListTailer());
        builder.append(HtmlSerializerHelper.buildDivTailer());
        return builder.toString();
    }

    /**
     * construct details of API changes by service and component name
     * 
     * @return html format string of API change details
     */
    private String createComponentChanges() {
        StringBuilder builder = new StringBuilder();
        for (ServiceCatalogDiff serviceCatalogDiff : diffList) {
            builder.append(createApiChanges(serviceCatalogDiff));
        }
        return builder.toString();
    }

    /**
     * constructs component list which API are changed
     * 
     * @return format string of API changed component list
     */
    public String createApiChanges(ServiceCatalogDiff serviceCatalogDiff) {
        StringBuilder builder = new StringBuilder();
        String serviceName = serviceCatalogDiff.getNewServiceCatalog().getServiceName();
        builder.append(HtmlSerializerHelper.buildDivHeader(serviceName));
        builder.append(HtmlSerializerHelper.buildContent("API", serviceName, 2));
        addNormalRecords(serviceCatalogDiff.getNewServiceCatalog(), "Added APIs");
        addComparisonRecords(serviceCatalogDiff, "Changed APIs");
        addNormalRecords(serviceCatalogDiff.getOldServiceCatalog(), "Removed APIs");

        SortedSet<String> sortedSet = new TreeSet<String>(componentMap.keySet());
        for (String key : sortedSet) {
            if (!key.startsWith(serviceName)) {
                continue;
            }
            StringBuilder value = componentMap.get(key);
            value.append(HtmlSerializerHelper.buildDivTailer());
            builder.append(value);
        }

        builder.append(HtmlSerializerHelper.buildDivTailer());

        return builder.toString();
    }

    /**
     * Adds "added" or "removed" records to component map
     * 
     * @param serviceCatalog
     *            The rest service which contains added/removed records
     * @param title
     *            The tile of records table
     */
    private void addNormalRecords(ServiceCatalog serviceCatalog, String title) {

        Map<String, StringBuilder> changedMap = new HashMap<String, StringBuilder>();

        for (Map.Entry<ApiIdentifier, ApiDescriptor> entry : serviceCatalog.getApiMap().entrySet()) {
            String componentName = serviceCatalog.getServiceName()
                    + Constants.NAME_STRING_SEPARATOR
                    + entry.getKey().getPath().split(Constants.URL_PATH_SEPARATOR)[1];
            StringBuilder componentValue = changedMap.get(componentName);
            if (componentValue == null) {
                componentValue = new StringBuilder();
                if (componentMap.get(componentName) == null) {
                    componentValue.append(HtmlSerializerHelper.buildDivHeader(componentName));
                    componentValue.append(HtmlSerializerHelper.buildContent("Component", componentName, 3));
                }
                componentValue.append(HtmlSerializerHelper.buildTableHeader());
                componentValue.append(HtmlSerializerHelper.buildTableHeaderRow(2,
                        new Pair<String, Integer>(title, 100)));
                changedMap.put(componentName, componentValue);
            }
            componentValue.append(addNormalRecord(entry.getKey(), entry.getValue(),
                    serviceCatalog.getElementMap()));
        }

        for (StringBuilder builder : changedMap.values()) {
            builder.append(HtmlSerializerHelper.buildTableTailer());
        }

        Iterator<Map.Entry<String, StringBuilder>> iterator = changedMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StringBuilder> entry = iterator.next();
            StringBuilder builder = componentMap.get(entry.getKey());
            if (builder == null) {
                builder = new StringBuilder();
                componentMap.put(entry.getKey(), builder);
            }
            builder.append(entry.getValue().toString());
            iterator.remove();
        }
    }

    private String addNormalRecord(final ApiIdentifier apiIdentifier,
            final ApiDescriptor apiResource,
            final Map<String, String> elmentMap) {

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

        String requestElement = elmentMap.get(apiResource.getRequestElement());
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

        String responseElement = elmentMap.get(apiResource.getResponseElement());
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
                new Pair<String, Integer>(apiIdentifier.getPath(), 25),
                new Pair<String, Integer>(builder.toString(), 75)
                );

    }

    /**
     * Adds "changed" records to component map
     * 
     * @param serviceCatalogDiff
     *            The instance of ServiceCatalogDiff
     * @param title
     *            The tile of records table
     */
    private void addComparisonRecords(ServiceCatalogDiff serviceCatalogDiff, String title) {

        Map<String, StringBuilder> changedMap = new HashMap<String, StringBuilder>();

        for (Map.Entry<ApiIdentifier, ApiDescriptorDiff> entry : serviceCatalogDiff.getApiChangedMap().entrySet()) {

            String componentName = serviceCatalogDiff.getNewServiceCatalog().getServiceName()
                    + Constants.NAME_STRING_SEPARATOR
                    + entry.getKey().getPath().split(Constants.URL_PATH_SEPARATOR)[1];
            StringBuilder componentValue = changedMap.get(componentName);
            if (componentValue == null) {
                componentValue = new StringBuilder();
                if (componentMap.get(componentName) == null) {
                    componentValue.append(HtmlSerializerHelper.buildDivHeader(componentName));
                    componentValue.append(HtmlSerializerHelper.buildContent("Component", componentName, 3));
                }
                componentValue.append(HtmlSerializerHelper.buildTableHeader());
                componentValue.append(HtmlSerializerHelper.buildTableHeaderRow(2,
                        new Pair<String, Integer>(title, 100)));
                changedMap.put(componentName, componentValue);
            }
            componentValue.append(addComparisonRecord(entry.getKey(), entry.getValue()));
        }

        for (StringBuilder builder : changedMap.values()) {
            builder.append(HtmlSerializerHelper.buildTableTailer());
        }

        Iterator<Map.Entry<String, StringBuilder>> iterator = changedMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StringBuilder> entry = iterator.next();
            StringBuilder builder = componentMap.get(entry.getKey());
            if (builder == null) {
                builder = new StringBuilder();
                componentMap.put(entry.getKey(), builder);
            }
            builder.append(entry.getValue().toString());
            iterator.remove();
        }

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
                new Pair<String, Integer>(apiIdentifier.getPath(), 25),
                new Pair<String, Integer>(builder.toString(), 75)
                );
    }

    private String addChangedField(final String name, Pair<String, String> pair) {
        if (name == null || pair == null) {
            return "";
        }

        String left = Constants.CODE_PREFIX;
        if (pair.getLeft() != null) {
            left += StringEscapeUtils.escapeHtml(pair.getLeft());
        }
        left += Constants.CODE_SUFFIX;

        String right = Constants.CODE_PREFIX;
        if (pair.getRight() != null) {
            right += StringEscapeUtils.escapeHtml(pair.getRight());
        }
        right += Constants.CODE_SUFFIX;

        return HtmlSerializerHelper.buildTableRow(1,
                new Pair<String, Integer>(name, 15),
                new Pair<String, Integer>(left, 40),
                new Pair<String, Integer>(right, 45)
                );
    }

}
