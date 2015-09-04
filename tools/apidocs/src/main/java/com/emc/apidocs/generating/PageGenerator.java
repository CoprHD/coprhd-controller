/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.generating;

import com.emc.apidocs.KnownPaths;
import com.emc.apidocs.model.*;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

/**
 */
public class PageGenerator {
    private static final String TOC_TEMPLATE = "TableOfContents.html";
    private static final String SERVICE_OVERVIEW_TEMPLATE = "Overview.html";
    private static final String METHOD_DETAIL_TEMPLATE = "Details.html";
    private static final String FOOTER_TEMPLATE = "Footer.html";
    private static final String STATIC_PAGE_TEMPLATE = "Page.html";
    private static final String SEARCH_TEMPLATE = "Search.html";
    private static final String ERROR_CODES_TEMPLATE = "ErrorCodes.html";
    private static final String API_DIFFERENCES = "API Differences";

    private static final String ALL_NEW_SERVICES_OVERVIEW = "AllNewServicesOverview.html";
    private static final String ALL_REMOVED_SERVICES_OVERVIEW = "AllRemovedServicesOverview.html";
    private static final String ALL_MODIFIED_METHODS_OVERVIEW = "AllModifiedMethodsOverview.html";
    private static final String ALL_REMOVED_METHODS_OVERVIEW = "AllRemovedMethodsOverview.html";
    private static final String ALL_ADDED_METHODS_OVERVIEW = "AllAddedMethodsOverview.html";
    private static final String ALL_DEPRECATED_METHODS_OVERVIEW = "AllDeprecatedMethodsOverview.html";

    private static final String SERVICE_REMOVED_METHODS_OVERVIEW = "ServiceRemovedMethodsOverview.html";
    private static final String SERVICE_NEW_METHODS_OVERVIEW = "ServiceNewMethodsOverview.html";
    private static final String SERVICE_MODIFIED_METHODS_OVERVIEW = "ServiceModifiedMethodsOverview.html";

    private static final String MODIFIED_METHOD_DETAIL = "ModifiedMethodDetail.html";

    private final String buildNumber;

    private static String generatedTableOfContents = "";
    private static String generatedFooter = "";

    public PageGenerator(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public void generatePages(ApiDifferences apiDifferences, List<ApiService> apiServices, List<ApiErrorCode> errorCodes) {
        StaticPageIndex staticPageIndex = new StaticPageIndex();

        generatedFooter = generateFooter();
        generatedTableOfContents = generateTableOfContents(apiServices, apiDifferences, staticPageIndex);
        generateStaticPages(staticPageIndex);
        generateSearchPage(apiServices);
        generateErrorCodes(errorCodes);

        generateChanges(apiDifferences, apiServices);

        for (ApiService apiService : apiServices) {
            generateServiceOverViewPage(apiService, apiDifferences.getChange(apiService.javaClassName));

            for (ApiMethod apiMethod : apiService.methods) {
                generateMethodDetailPage(apiMethod, apiDifferences.getChange(apiService.javaClassName, apiMethod.javaMethodName));
            }
        }
    }

    /** Generates all the static pages that just need formatting */
    public void generateStaticPages(StaticPageIndex staticPageIndex) {

        // Generate Static HTML pages
        for (StaticPageIndex.PageFile file : staticPageIndex.getAllStaticPages()) {
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("content", file.content);
            parameters.put("title", file.title);
            parameters.put("folderId", file.parentFolder.getId());
            parameters.put("pageFile", file.getGeneratedFileName());

            addCommonTemplateParameters(parameters);

            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(STATIC_PAGE_TEMPLATE),
                    KnownPaths.getHtmlOutputFile(file.getGeneratedFileName()),
                    parameters);
        }

        // Copy Artifacts
        File artifactsDir = KnownPaths.getHtmlOutputFile(StaticPageIndex.ARTIFACTS_DIR);
        artifactsDir.mkdir();
        for (File artifact : staticPageIndex.getAllArtifacts()) {
            try {
                FileOutputStream outputFile = new FileOutputStream(artifactsDir.getAbsolutePath() + "/" + artifact.getName());
                FileInputStream inputFile = new FileInputStream(artifact);

                IOUtils.copy(inputFile, outputFile);
                IOUtils.closeQuietly(inputFile);
            } catch (Exception e) {
                throw new RuntimeException("Error processing artifact " + artifact.getAbsolutePath(), e);
            }
        }
    }

    /** Generates the search page */
    public void generateSearchPage(List<ApiService> services) {
        List<ApiMethod> allMethods = new ArrayList<ApiMethod>();
        for (ApiService service : services) {
            allMethods.addAll(service.methods);
        }

        // Assign a unique Index Search Keys to all methods
        int key = 0;
        for (ApiMethod method : allMethods) {
            method.indexKey = "" + key++;
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiMethods", allMethods);
        parameters.put("title", "API Reference Search");

        addCommonTemplateParameters(parameters);

        TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(SEARCH_TEMPLATE),
                KnownPaths.getHtmlOutputFile(SEARCH_TEMPLATE),
                parameters);
    }

    /** Generates the search page */
    public void generateErrorCodes(List<ApiErrorCode> serviceCodes) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("errorCodes", serviceCodes);
        parameters.put("title", "Error Codes");

        addCommonTemplateParameters(parameters);

        TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ERROR_CODES_TEMPLATE),
                KnownPaths.getHtmlOutputFile(ERROR_CODES_TEMPLATE),
                parameters);
    }

    public String generateFooter() {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("buildNumber", buildNumber);

        return TemplateEngine.generateStringFromTemplate(KnownPaths.getTemplateFile(FOOTER_TEMPLATE),
                parameters);
    }

    public String generateTableOfContents(List<ApiService> services, ApiDifferences apiDifferences, StaticPageIndex staticPageIndex) {
        ApiReferenceTocOrganizer grouping = new ApiReferenceTocOrganizer(KnownPaths.getReferenceFile("ApiReferenceGrouping.txt"));

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("services", services);
        parameters.put("staticPageIndex", staticPageIndex);
        parameters.put("serviceGrouping", grouping.organizeServices(services));
        parameters.put("serviceDiff", getServiceDiff(grouping));
        parameters.put("apiDifferences", apiDifferences);

        // Sort Alphabetically Ascending
        Collections.sort(services, new Comparator<ApiService>() {
            @Override
            public int compare(ApiService apiService, ApiService apiService1) {
                return apiService.getTitle().compareTo(apiService1.getTitle());
            }
        });

        return TemplateEngine.generateStringFromTemplate(KnownPaths.getTemplateFile(TOC_TEMPLATE),
                parameters);
    }

    /**
     * Gets map of API Differences
     * 
     * @param grouping
     *            The instance of ApiReferenceTocOrganizer
     * @return the API Differences map which exists
     */
    private Map<String, String> getServiceDiff(ApiReferenceTocOrganizer grouping) {
        List<String> allList = grouping.groups.get(API_DIFFERENCES);
        if (allList == null || allList.size() == 0)
            return null;
        Map<String, String> diffMap = new HashMap<String, String>();
        for (String item : allList) {
            String pageLink = item.replaceAll(" ", "") + "_diff.html";
            File file = KnownPaths.getApiDiffFile(pageLink);
            if (file.exists())
                diffMap.put(item, pageLink);
        }
        return diffMap;
    }

    public void generateServiceOverViewPage(ApiService apiService, ApiDifferences.Change change) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiService", apiService);
        parameters.put("title", apiService.getTitle());
        parameters.put("change", change);

        addCommonTemplateParameters(parameters);

        TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(SERVICE_OVERVIEW_TEMPLATE),
                KnownPaths.getHtmlOutputFile(apiService.getOverviewFileName()),
                parameters);
    }

    public void generateMethodDetailPage(ApiMethod apiMethod, ApiDifferences.Change change) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiMethod", apiMethod);
        parameters.put("title", apiMethod.apiService.getTitle() + ":" + apiMethod.getTitle());
        parameters.put("change", change);

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(METHOD_DETAIL_TEMPLATE),
                    KnownPaths.getHtmlOutputFile(apiMethod.getDetailFileName()),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating details for :" + apiMethod.getQualifiedName(), e);
        }
    }

    public void generateChanges(ApiDifferences apiDifferences, List<ApiService> services) {
        generateAllNewServicesOverview(apiDifferences);
        generateAllRemovedServicesOverview(apiDifferences);
        generateAllModifiedMethodsOverview(apiDifferences);
        generateAllRemovedMethodsOverview(apiDifferences);
        generateAllAddedMethodsOverview(apiDifferences);
        generateAllDeprecatedMethodsOverview(services);

        for (ApiServiceChanges apiServiceChanges : apiDifferences.modifiedServices) {
            if (!apiServiceChanges.newMethods.isEmpty()) {
                generateServiceNewMethods(apiServiceChanges.service, apiServiceChanges.newMethods);
            }

            if (!apiServiceChanges.removedMethods.isEmpty()) {
                generateServiceRemovedMethods(apiServiceChanges.service, apiServiceChanges.removedMethods);
            }

            if (!apiServiceChanges.modifiedMethods.isEmpty()) {
                generateServiceModifiedMethods(apiServiceChanges.service, apiServiceChanges.modifiedMethods);

                for (ApiMethodChanges methodChanges : apiServiceChanges.modifiedMethods) {
                    generateModifiedMethodDetail(apiServiceChanges.service, methodChanges);
                }
            }
        }
    }

    public void generateAllNewServicesOverview(ApiDifferences apiDifferences) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiDifferences", apiDifferences);
        parameters.put("title", "New Services In the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_NEW_SERVICES_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_NEW_SERVICES_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Changes File", e);
        }
    }

    public void generateAllModifiedMethodsOverview(ApiDifferences apiDifferences) {
        List<ApiMethodChanges> modifiedMethods = Lists.newArrayList();

        for (ApiServiceChanges serviceChanges : apiDifferences.modifiedServices) {
            modifiedMethods.addAll(serviceChanges.modifiedMethods);
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("modifiedMethods", modifiedMethods);
        parameters.put("title", "Methods modified in the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_MODIFIED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_MODIFIED_METHODS_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Changes File", e);
        }
    }

    public void generateAllRemovedMethodsOverview(ApiDifferences apiDifferences) {

        List<ApiMethod> removedMethods = Lists.newArrayList();

        for (ApiServiceChanges serviceChanges : apiDifferences.modifiedServices) {
            removedMethods.addAll(serviceChanges.removedMethods);
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("methods", removedMethods);
        parameters.put("title", "Methods removed in the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_REMOVED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_REMOVED_METHODS_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Changes File", e);
        }
    }

    public void generateAllAddedMethodsOverview(ApiDifferences apiDifferences) {
        List<ApiMethod> addedMethods = Lists.newArrayList();

        for (ApiServiceChanges serviceChanges : apiDifferences.modifiedServices) {
            addedMethods.addAll(serviceChanges.newMethods);
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("methods", addedMethods);
        parameters.put("title", "Methods added in the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_ADDED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_ADDED_METHODS_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Changes File", e);
        }
    }

    public void generateAllDeprecatedMethodsOverview(List<ApiService> services) {
        List<ApiMethod> deprecatedMethods = Lists.newArrayList();

        for (ApiService service : services) {
            if (service.isDeprecated) {
                deprecatedMethods.addAll(service.methods);
            }
            else {
                for (ApiMethod apiMethod : service.methods) {
                    if (apiMethod.isDeprecated) {
                        deprecatedMethods.add(apiMethod);
                    }
                }
            }
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("methods", deprecatedMethods);
        parameters.put("title", "Methods deprecated in the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_DEPRECATED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_DEPRECATED_METHODS_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Deprecated File", e);
        }
    }

    public void generateAllRemovedServicesOverview(ApiDifferences apiDifferences) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiDifferences", apiDifferences);
        parameters.put("title", "Services Removed From the API");

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(ALL_REMOVED_SERVICES_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(ALL_REMOVED_SERVICES_OVERVIEW),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating all Changes File", e);
        }
    }

    public void generateServiceNewMethods(ApiService apiService, List<ApiMethod> newMethods) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiService", apiService);
        parameters.put("apiMethods", newMethods);
        parameters.put("title", "New Methods in " + apiService.getTitle());

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(SERVICE_NEW_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(apiService.getNewMethodsFileName()),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating New Methods File", e);
        }
    }

    public void generateServiceRemovedMethods(ApiService apiService, List<ApiMethod> removedMethods) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiService", apiService);
        parameters.put("apiMethods", removedMethods);
        parameters.put("title", "Methods Removed from " + apiService.getTitle());

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(SERVICE_REMOVED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(apiService.getRemovedMethodsFileName()),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating Removed Methods File", e);
        }
    }

    public void generateServiceModifiedMethods(ApiService apiService, List<ApiMethodChanges> methodChanges) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiService", apiService);
        parameters.put("methodChanges", methodChanges);
        parameters.put("title", "Methods Changed in " + apiService.getTitle());

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(SERVICE_MODIFIED_METHODS_OVERVIEW),
                    KnownPaths.getHtmlOutputFile(apiService.getModifiedMethodsFileName()),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating Removed Methods File", e);
        }
    }

    public void generateModifiedMethodDetail(ApiService apiService, ApiMethodChanges methodChanges) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("apiService", apiService);
        parameters.put("apiMethod", methodChanges.method);
        parameters.put("methodChanges", methodChanges);
        parameters.put("title", "Methods Changed in " + apiService.getTitle());

        addCommonTemplateParameters(parameters);

        try {
            TemplateEngine.generateFileFromTemplate(KnownPaths.getTemplateFile(MODIFIED_METHOD_DETAIL),
                    KnownPaths.getHtmlOutputFile(apiService.getModifiedMethodFileName(methodChanges.method.javaMethodName)),
                    parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error generating Removed Methods File", e);
        }
    }

    private void addCommonTemplateParameters(Map<String, Object> parameters) {
        parameters.put("tableOfContents", generatedTableOfContents);
        parameters.put("footer", generatedFooter);
    }
}
