/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import com.emc.apidocs.differencing.DifferenceEngine;
import com.emc.apidocs.generating.PageGenerator;
import com.emc.apidocs.model.ApiDifferences;
import com.emc.apidocs.model.ApiErrorCode;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.emc.apidocs.processing.*;
import com.emc.apidocs.generating.*;
import com.emc.apidocs.tools.MetaData;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.google.common.collect.Lists;
import com.sun.javadoc.*;
import org.apache.commons.io.IOUtils;
import java.io.*;
import java.util.*;

/**
 * Doclet to process the ViPR API annotations and comments
 */
public class ApiDoclet {
    private static final String PAGE_TITLE_PROPERTY = "title:";
    private static final String OUTPUT_OPTION = "-d";
    private static final String CONTENT_OPTION = "-c";
    private static final String BUILD_OPTION = "-build";
    private static final String PORTAL_SRC_OPTION = "-portalsrc";
    private static final String ROOT_DIRECTORY = "-rootDirectory";
    private static final String INTERNAL_PATH = "internal";

    private static final List<String> DATASERVICES_CLASSES = Lists.newArrayList("S3Service", "AtmosService", "SwiftService");

    private static final String SYSTEM_SERVIES_PACKAGE = "com.emc.storageos.systemservices";

    private static String buildNumber = null;
    private static String rootDirectory = null;
    private static String portalSource = null;
    private static String outputDirectory;
    private static String contentDirectory;

    private static List<String> serviceBlackList = Lists.newArrayList();
    private static List<String> methodBlackList = Lists.newArrayList();

    /** MAIN Entry Point into the Doclet */
    public static boolean start(RootDoc root) {
        KnownPaths.init(contentDirectory, outputDirectory);
        init();

        loadServiceBlackList();
        loadMethodBlackList();
        List<ApiService> apiServices = findApiServices(root.classes());
        List<ApiErrorCode> errorCodes = findErrorCodes(root.classes());
        cleanupMethods(apiServices);
        saveMetaData(apiServices);

        ApiDifferences apiDifferences = calculateDifferences(apiServices);
        generateFiles(apiDifferences, apiServices, errorCodes);

        return true;
    }

    /** Required by Doclet, otherwise it does not process Generics correctly */
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    /** Required by Doclet to check command line options */
    public static int optionLength(String option) {
        if (option.equals(OUTPUT_OPTION)) {
            return 2;
        }

        if (option.equals(CONTENT_OPTION)) {
            return 2;
        }

        if (option.equals(BUILD_OPTION)) {
            return 2;
        }

        if (option.equals(PORTAL_SRC_OPTION)) {
            return 2;
        }
        if (option.equals(ROOT_DIRECTORY)) {
        	return 2;
        }

        return 1;
    }

    /** Required by Doclet to process the command line options */
    public static synchronized boolean validOptions(String options[][],
            DocErrorReporter reporter) {
        DocReporter.init(reporter);
        DocReporter.printWarning("Processing Options");
        boolean valid = true;
        boolean contentOptionFound = false;
        boolean outputOptionFound = false;
        boolean portalsrcOptionFound = false;

        // Make sure we have an OUTPUT and TEMPLATES option
        for (int i = 0; i < options.length; i++) {
            if (options[i][0].equals(OUTPUT_OPTION)) {
                outputOptionFound = true;
                valid = checkOutputOption(options[i][1], reporter);
            }
            else if (options[i][0].equals(CONTENT_OPTION)) {
                contentOptionFound = true;
                valid = checkContentOption(options[i][1], reporter);

            } else if (options[i][0].equals(PORTAL_SRC_OPTION)) {
                portalsrcOptionFound = true;
                valid = checkPortalSourceOption(options[i][1], reporter);

            } else if (options[i][0].equals(ROOT_DIRECTORY)) {
            	rootDirectory = options[i][1];
            	reporter.printWarning(rootDirectory);

            } else if (options[i][0].equals(BUILD_OPTION)) {
                buildNumber = options[i][1];
                reporter.printWarning("Build " + buildNumber);
            }
        }

        if (!contentOptionFound) {
            reporter.printError("Content dir option " + CONTENT_OPTION + " not specified");
        }

        if (!outputOptionFound) {
            reporter.printError("Output dir option " + OUTPUT_OPTION + " not specified");
        }

        if (!portalsrcOptionFound) {
            reporter.printError("Portal Source option " + PORTAL_SRC_OPTION + " not specified");
        }

        DocReporter.printWarning("Finished Processing Options");

        return valid && contentOptionFound && outputOptionFound && portalsrcOptionFound;
    }

    /** Processes the list of classes looking for ones that represent an API Service, and parsing them if found */
    public static synchronized List<ApiService> findApiServices(ClassDoc[] classes) {
        List<ApiService> apiServices = new ArrayList<ApiService>();
        for (ClassDoc classDoc : classes) {
            if (DATASERVICES_CLASSES.contains(classDoc.name())) {
                if (!classDoc.name().equals("AtmosService")) {
                    // Data Service service, so treat it slightly differently since it's actually split over operation classes
                    String baseURL = AnnotationUtils.getAnnotationValue(classDoc, KnownAnnotations.Path_Annotation,
                            KnownAnnotations.Value_Element, "");

                    for (ClassDoc operationClassDoc : findDataServiceOperations(classDoc)) {
                        apiServices.add(processClass(operationClassDoc, baseURL, true));
                    }
                }
            }
            else if (AnnotationUtils.hasAnnotation(classDoc, KnownAnnotations.Path_Annotation) &&
                    !serviceBlackList.contains(classDoc.qualifiedName()) &&
                    !serviceBlackList.contains(classDoc.name())) {
                String baseURL = AnnotationUtils.getAnnotationValue(classDoc, KnownAnnotations.Path_Annotation,
                        KnownAnnotations.Value_Element, "");

                if (!isInternalPath(baseURL)) {
                    apiServices.add(processClass(classDoc, baseURL, false));
                }
            }
        }

        if( null != portalSource) {
            // Add All Services from the Portal API
            apiServices.addAll(PlayRoutesParser.getPortalServices(portalSource));
        }
        
        return apiServices;
    }

    private static synchronized List<ApiErrorCode> findErrorCodes(ClassDoc[] classes) {
        // Find ServiceCode Class
        ClassDoc serviceCodeClass = null;
        for (ClassDoc classDoc : classes) {
            if (classDoc.qualifiedName().equals(ServiceCode.class.getCanonicalName())) {
                serviceCodeClass = classDoc;
                break;
            }
        }

        if (serviceCodeClass == null) {
            throw new RuntimeException("Unable to find ServiceCode Class");
        }

        // Extract ServiceCode information
        List<ApiErrorCode> errorCodes = Lists.newArrayList();
        for (FieldDoc field : serviceCodeClass.enumConstants()) {
            ApiErrorCode errorCode = new ApiErrorCode(ServiceCode.valueOf(field.name()));

            if (AnnotationUtils.hasAnnotation(field, KnownAnnotations.Deprecated_Annotation)) {
                errorCode.setDeprecated(true);
            }
            errorCodes.add(errorCode);
        }

        Collections.sort(errorCodes, new Comparator<ApiErrorCode>() {
            @Override
            public int compare(ApiErrorCode o1, ApiErrorCode o2) {
                return Integer.valueOf(o1.getCode()).compareTo(o2.getCode());
            }
        });

        return errorCodes;
    }

    private static synchronized void cleanupMethods(List<ApiService> apiServices) {
        // Cleanup
        for (ApiService apiService : apiServices) {
            for (ApiMethod apiMethod : apiService.methods) {
                TemporaryCleanup.applyCleanups(apiMethod);
            }
        }
        applyServiceTitleChanges(apiServices);
    }

    private static synchronized void saveMetaData(List<ApiService> apiServices) {
        MetaData.save(KnownPaths.getOutputFile("meta_data.json"), apiServices);
    }

    private static synchronized void generateFiles(ApiDifferences apiDifferences, List<ApiService> apiServices, List<ApiErrorCode> errorCodes) {
        PageGenerator pageGenerator = new PageGenerator(buildNumber);
        pageGenerator.generatePages(apiDifferences, apiServices, errorCodes);
    }

    private static synchronized ApiDifferences calculateDifferences(List<ApiService> apiServices) {
    	Properties prop = new Properties();
    	try {
    		FileInputStream fileInput = new FileInputStream(rootDirectory+"gradle.properties");
    		prop.load(fileInput);
    	}
    	catch (IOException e) {
        	throw new RuntimeException("Unable to load Gradle properties file", e);
        }
    	String docsMetaVersion = prop.getProperty("apidocsComparisionVersion");
        List<ApiService> oldServices = MetaData.load(KnownPaths.getMetaDataFile("MetaData-"+docsMetaVersion+".json"));
        DifferenceEngine differenceEngine = new DifferenceEngine();
        return differenceEngine.calculateDifferences(oldServices, apiServices);
    }

    /** Process a JAXRS Class into an API Service */
    public static synchronized ApiService processClass(ClassDoc classDoc, String baseUrl, boolean isDataService) {
        ApiService apiService = new ApiService();
        apiService.packageName = classDoc.containingPackage().name();
        apiService.javaClassName = classDoc.name();
        apiService.description = classDoc.commentText();
        apiService.path = baseUrl;

        addDefaultPermissions(classDoc, apiService);
        addDeprecated(classDoc, apiService);

        TemporaryCleanup.applyCleanups(apiService);

        // Process ALL methods on EMC classes, including super classes
        Set<String> methodsAdded = new HashSet<>();
        ClassDoc currentClass = classDoc;
        while (currentClass != null && currentClass.containingPackage().name().startsWith("com.emc")) {
            for (MethodDoc method : currentClass.methods()) {

                if (isApiMethod(method) &&
                        !isInternalMethod(method) &&
                        !methodBlackList.contains(apiService.getFqJavaClassName() + "::" + method.name()) &&
                        !methodBlackList.contains(apiService.javaClassName + "::" + method.name())) {
                    ApiMethod apiMethod = MethodProcessor.processMethod(apiService, method, apiService.path, isDataService);

                    // Some methods are marked internal via brief comments, but we only know that after processing it
                    if (!apiMethod.brief.toLowerCase().startsWith("internal")) {
                        // Add method to service only if it is not overridden by subclass
                        if (methodsAdded.add(apiMethod.javaMethodName + ":" + apiMethod.httpMethod + ":" + apiMethod.path)) {
                            apiService.addMethod(apiMethod);
                        } else {
                            System.out.println("Service " + classDoc.name() + ": skip overridden method " + currentClass.name() + "::" + method.name());
                        }
                    }
                }

                methodsAdded.add(method.name());
            }

            currentClass = currentClass.superclass();
        }

        return apiService;
    }

    public static synchronized void addDefaultPermissions(ClassDoc classDoc, ApiService apiService) {
        AnnotationDesc defaultPermissions = AnnotationUtils.getAnnotation(classDoc, KnownAnnotations.DefaultPermissions_Annotation);

        if (defaultPermissions != null) {
            for (AnnotationDesc.ElementValuePair pair : defaultPermissions.elementValues()) {
                if (pair.element().name().equals("readRoles")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiService.addReadRole(((FieldDoc) value.value()).name());
                    }
                }
                else if (pair.element().name().equals("writeRoles")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiService.addWriteRole(((FieldDoc) value.value()).name());
                    }
                }
                else if (pair.element().name().equals("readAcls")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiService.addReadAcl(((FieldDoc) value.value()).name());
                    }
                }
                else if (pair.element().name().equals("writeAcls")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiService.addWriteAcl(((FieldDoc) value.value()).name());
                    }
                }
            }
        }
    }

    private static synchronized List<ClassDoc> findDataServiceOperations(ClassDoc dataService) {
        List<ClassDoc> operations = new ArrayList<ClassDoc>();
        for (FieldDoc field : dataService.fields(false)) {
            if (field.name().endsWith("Operations") || field.name().endsWith("Operation")) {
                operations.add(field.type().asClassDoc());
            }
        }

        return operations;
    }

    public static boolean isApiMethod(MethodDoc method) {
        return AnnotationUtils.hasAnnotation(method, "javax.ws.rs.POST") ||
                AnnotationUtils.hasAnnotation(method, "javax.ws.rs.GET") ||
                AnnotationUtils.hasAnnotation(method, "javax.ws.rs.PUT") ||
                AnnotationUtils.hasAnnotation(method, "javax.ws.rs.DELETE");
    }

    public static boolean isInternalMethod(MethodDoc method) {
        return isInternalPath(AnnotationUtils.getAnnotationValue(method, KnownAnnotations.Path_Annotation, KnownAnnotations.Value_Element,
                ""));
    }

    public static boolean isInternalPath(String path) {
        return path.startsWith("/" + INTERNAL_PATH) || path.startsWith(INTERNAL_PATH + "/");
    }

    /**
     * Allows users to change titles of services, rather than using the default JavaClassName
     */
    private static synchronized void applyServiceTitleChanges(List<ApiService> services) {
        Properties titleChanges = new Properties();
        try {
            titleChanges.load(new FileInputStream(KnownPaths.getReferenceFile("ServiceTitleChanges.txt")));

            for (ApiService service : services) {
                if (titleChanges.containsKey(service.getFqJavaClassName())) {
                    service.titleOverride = titleChanges.get(service.getFqJavaClassName()).toString();
                } else if (titleChanges.containsKey(service.javaClassName)) {
                    service.titleOverride = titleChanges.get(service.javaClassName).toString();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Title Changes file", e);
        }

    }

    private static synchronized boolean checkOutputOption(String value, DocErrorReporter reporter) {
        File file = new File(value);
        if (!file.exists()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION + ") not found :" + file.getAbsolutePath());
            return false;
        }

        if (!file.isDirectory()) {
            reporter.printError("Output directory (" + OUTPUT_OPTION + ") is not a directory :" + file.getAbsolutePath());
            return false;
        }

        outputDirectory = value;
        if (!outputDirectory.endsWith("/")) {
            outputDirectory = outputDirectory + "/";
        }

        reporter.printWarning("Output Directory " + outputDirectory);

        return true;
    }

    private static synchronized boolean checkPortalSourceOption(String value, DocErrorReporter reporter) {
        File file = new File(value);
        if (!file.exists()) {
            reporter.printError("Portal Source directory (" + PORTAL_SRC_OPTION + ") not found :" + file.getAbsolutePath());
            return false;
        }

        portalSource = value;
        reporter.printWarning("Portal Source Directory " + portalSource);

        return true;
    }

    private static synchronized boolean checkContentOption(String contentDir, DocErrorReporter reporter) {
        File contentDirFile = new File(contentDir);
        if (!contentDirFile.exists()) {
            reporter.printError("Content directory (" + CONTENT_OPTION + ") not found :" + contentDirFile.getAbsolutePath());
            return false;
        }

        if (!contentDirFile.isDirectory()) {
            reporter.printError("Content directory (" + CONTENT_OPTION + ") is not a directory :" + contentDirFile.getAbsolutePath());
            return false;
        }
        contentDirectory = contentDir;
        if (!contentDirectory.endsWith("/")) {
            contentDirectory = contentDirectory + "/";
        }
        reporter.printWarning("Content Directory " + contentDirectory);

        return true;
    }

    private static synchronized void init() {
        KnownPaths.getHTMLDir().mkdirs();
    }

    private static synchronized void loadServiceBlackList() {
        try {
            serviceBlackList = IOUtils.readLines(new FileInputStream(KnownPaths.getReferenceFile("ServiceBlacklist.txt")));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Service blacklist", e);
        }
    }

    private static synchronized void loadMethodBlackList() {
        try {
            methodBlackList = IOUtils.readLines(new FileInputStream(KnownPaths.getReferenceFile("MethodBlackList.txt")));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load Method blacklist", e);
        }
    }

    public static synchronized void addDeprecated(ClassDoc method, ApiService apiService) {
        if (AnnotationUtils.hasAnnotation(method, KnownAnnotations.Deprecated_Annotation)) {
            apiService.isDeprecated = true;

            Tag[] deprecatedTags = method.tags("@deprecated");
            if (deprecatedTags.length > 0) {
                apiService.deprecatedMessage = deprecatedTags[0].text();
            }
        }
    }
}
