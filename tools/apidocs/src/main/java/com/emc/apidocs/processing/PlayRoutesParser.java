/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.processing;

import com.emc.apidocs.generating.ExampleLoader;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Look for Play! Route files and parse out the ApiService information
 */
public class PlayRoutesParser {

    private static Pattern docPattern = Pattern.compile("##begin\n"+
                                                         "((## .*\n)*)"+   // this is groups 1 and 2
                                                         "## @brief (.*)\n"+
                                                         "(## @param (.*)\n)*"+
                                                         "## @prereq (.*)\n"+
                                                         "##end\n"+
                                                         "([^\\s]*)\\s*([^\\s]*)\\s*(.*)");

    private static Pattern paramPattern = Pattern.compile("## @param ([^ ]*)\\s(.*)");

    public static Collection<ApiService> getPortalServices(String portalSrcRoot) {
        try {
            Map<String, ApiService> apiServices = Maps.newHashMap();
            parse(new File(portalSrcRoot+"/conf/routes"), apiServices,"");

            return apiServices.values();
        } catch(Exception e) {
            throw new RuntimeException("Error reading portal services",e);
        }
    }

    private static void parse(File routeFile, Map<String, ApiService> services, String moduleRoot) throws Exception {
        byte[] bytes = IOUtils.toByteArray(new FileInputStream(routeFile));

        String file = new String(bytes);

        Matcher serviceMatcher = docPattern.matcher(file);
        while (serviceMatcher.find()) {
            int groupCount = serviceMatcher.groupCount();

            String className = getClassName(serviceMatcher.group(groupCount));
            String packageName = getPackage(serviceMatcher.group(groupCount));

            ApiService apiService = null;
            if (services.containsKey(className)) {
                apiService = services.get(className);
            }
            else {
                apiService = new ApiService();
                apiService.packageName = packageName;
                apiService.javaClassName = className;
                apiService.description = "";
                apiService.path = moduleRoot+"/api";

                services.put(className, apiService);
            }

            if (!serviceMatcher.group(groupCount -1).endsWith("{<json|xml>format}")) {
                ApiMethod apiMethod = new ApiMethod();
                apiMethod.javaMethodName = getMethodName(serviceMatcher.group(serviceMatcher.groupCount()));
                apiMethod.httpMethod = serviceMatcher.group(groupCount - 2);
                apiMethod.path = moduleRoot+serviceMatcher.group(groupCount - 1);
                apiMethod.description = serviceMatcher.group(1).replaceAll("## ","");
                apiMethod.brief = serviceMatcher.group(3);
                apiMethod.apiService = apiService;
                apiMethod.alert = "<b>Note:</b> Hosted on HTTPS port 443";

                apiMethod.xmlExample = ExampleLoader.loadExample(apiMethod.getXmlExampleFilename());
                apiMethod.jsonExample = ExampleLoader.loadExample(apiMethod.getJsonExampleFilename());


                addParameterInfo(serviceMatcher.group(0), apiMethod);

                apiService.addMethod(apiMethod);
            }
        }
    }

    private static void addParameterInfo(String javadocText, ApiMethod method) {
        Matcher paramMatcher = paramPattern.matcher(javadocText);
        while (paramMatcher.find()) {
            if (paramMatcher.group(1).equals("response")) {
                method.responseDescription = paramMatcher.group(2);
            }
            else {
                ApiField field = new ApiField();
                field.name = paramMatcher.group(1);
                field.description = paramMatcher.group(2);

                if (method.path.contains("{"+field.name+"}")) {
                    method.pathParameters.add(field);
                } else {
                    method.queryParameters.add(field);
                }
            }
        }
    }

    private static String getClassName(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        return parts[parts.length-2].replace("Api","Service");
    }

    private static String getMethodName(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        return parts[parts.length-1];
    }

    private static String getPackage(String qualifiedName) {
        return qualifiedName.substring(0,qualifiedName.lastIndexOf("."));
    }
}
