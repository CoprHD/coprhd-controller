/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.processing;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.emc.apidocs.AnnotationUtils;
import com.emc.apidocs.DocReporter;
import com.emc.apidocs.KnownAnnotations;
import com.emc.apidocs.Utils;
import com.emc.apidocs.generating.ExampleLoader;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.google.common.collect.Lists;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationValue;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Tag;

/**
 */
public class MethodProcessor {
    private static final Pattern DATA_SERVICE_PARAM_PATTERN = Pattern
            .compile("(request|response)\\s*(Header|Payload|Query)?\\s*([^0-1]*)([0-1]*)\\s*([\\-0-1]*)\\s*([^ ]*)\\s*([\\s\\S]*)");

    private static final String S3_URL_FORMAT = "Host Style: http://bucketname.ns1.emc.com";
    private static final String ATMOS_URL_FORMAT = "Host Style: http://emc.com";

    /** Create an APIMethod from a standard Method */
    public static ApiMethod processMethod(ApiService apiService, MethodDoc method, String baseURL, boolean isDataService) {
        try {
            ApiMethod apiMethodDesc = new ApiMethod();
            apiMethodDesc.javaMethodName = method.name();
            apiMethodDesc.apiService = apiService;
            apiMethodDesc.isDataService = isDataService;

            addPath(method, apiMethodDesc, baseURL);
            addHttpMethod(method, apiMethodDesc);
            addDescription(method, apiMethodDesc);
            addBriefDescription(method, apiMethodDesc);
            addResponseDescription(method, apiMethodDesc);
            addPrerequisites(method, apiMethodDesc);
            addSecurity(method, apiMethodDesc);

            if (isDataService) {
                addDataServiceInformation(method, apiMethodDesc);
            }
            else {
                addInputs(method, apiMethodDesc);
            }
            addOutput(method, apiMethodDesc);
            addQueryParameters(method, apiMethodDesc);
            addPathParameters(method, apiMethodDesc);
            addExamples(apiMethodDesc);
            addDeprecated(method, apiMethodDesc);

            return apiMethodDesc;
        } catch (Exception e) {
            throw new RuntimeException("Error processing " + apiService.getFqJavaClassName() + "::" + method.name(), e);
        }
    }

    private static void addPath(MethodDoc method, ApiMethod apiMethod, String baseUrl) {
        String methodPath = AnnotationUtils
                .getAnnotationValue(method, KnownAnnotations.Path_Annotation, KnownAnnotations.Value_Element, "");

        // TODO : Change this for RegEx
        apiMethod.path = Utils.mergePaths(baseUrl, methodPath.replace("{ignore: .+}", "").replace("{ignore:.*}", ""));
    }

    public static void addHttpMethod(MethodDoc method, ApiMethod apiMethod) {
        if (AnnotationUtils.hasAnnotation(method, "javax.ws.rs.POST")) {
            apiMethod.httpMethod = "POST";
        } else if (AnnotationUtils.hasAnnotation(method, "javax.ws.rs.GET")) {
            apiMethod.httpMethod = "GET";
        } else if (AnnotationUtils.hasAnnotation(method, "javax.ws.rs.DELETE")) {
            apiMethod.httpMethod = "DELETE";
        } else if (AnnotationUtils.hasAnnotation(method, "javax.ws.rs.PUT")) {
            apiMethod.httpMethod = "PUT";
        } else {
            apiMethod.httpMethod = "KNOWN";
        }
    }

    public static void addDescription(MethodDoc method, ApiMethod apiMethod) {
        apiMethod.description = method.commentText();
    }

    public static void addResponseDescription(MethodDoc method, ApiMethod apiMethod) {
        for (Tag tag : method.tags("@return")) {
            apiMethod.responseDescription = Utils.upperCaseFirstChar(tag.text());
            return;
        }

        apiMethod.responseDescription = "";
    }

    public static void addBriefDescription(MethodDoc method, ApiMethod apiMethod) {

        String brief = "";
        for (Tag tag : method.tags("@brief")) {
            brief = tag.text();
        }

        apiMethod.brief = brief;

        // Fix the issue where @Brief is before the main comment and thus picks up both the brief and the main comment
        if (brief.contains("\n")) {
            int briefEnd = brief.indexOf("\n");
            apiMethod.brief = Utils.upperCaseFirstChar(brief.substring(0, briefEnd));
            apiMethod.description = brief.substring(briefEnd + 1);
        }

        // Use brief as the comment if we have nothing else
        if (apiMethod.description.equals("")) {
            apiMethod.description = apiMethod.brief;
        }
    }

    public static void addPrerequisites(MethodDoc method, ApiMethod apiMethod) {
        for (Tag tag : method.tags("@prereq")) {
            if (!tag.text().toLowerCase().equals("none")) {
                apiMethod.addPrerequisite(tag.text());
            }
        }
    }

    public static void addInputs(MethodDoc method, ApiMethod apiMethod) {
        for (Parameter parameter : method.parameters()) {
            if (!AnnotationUtils.hasAnnotation(parameter, "javax.ws.rs.PathParam")
                    && !AnnotationUtils.hasAnnotation(parameter, "javax.ws.rs.QueryParam")
                    && !AnnotationUtils.hasAnnotation(parameter, "javax.ws.rs.HeaderParam")
                    && !AnnotationUtils.hasAnnotation(parameter, "javax.ws.rs.core.Context")) {
                
                if(!TypeUtils.isPrimitiveType(parameter.type())) {
                    apiMethod.setFqRequestType(parameter.type().qualifiedTypeName());
                    apiMethod.input = JaxbClassProcessor.convertToApiClass(parameter.type().asClassDoc());
                }
            }
        }
    }

    public static void addDeprecated(MethodDoc method, ApiMethod apiMethod) {
        if (AnnotationUtils.hasAnnotation(method, KnownAnnotations.Deprecated_Annotation)) {
            apiMethod.isDeprecated = true;

            Tag[] deprecatedTags = method.tags("@deprecated");
            if (deprecatedTags.length > 0) {
                apiMethod.deprecatedMessage = deprecatedTags[0].text();
            }
        }
    }

    /** Data services mainly use headers, and thus the @param comments contain more information than usual */
    public static void addDataServiceInformation(MethodDoc method, ApiMethod apiMethod) {
        Tag[] urlFormat = method.tags("@UrlFormat");
        if (urlFormat.length > 0) {
            apiMethod.urlFormat = urlFormat[0].text();

            // Update method path as Data service paths can't be picked up from the code
            if (apiMethod.urlFormat.startsWith(S3_URL_FORMAT)) {
                // S3 services have a URL format that's more comment style, so need to extract it
                int hostStyleEnd = apiMethod.urlFormat.indexOf("\n");
                apiMethod.path = apiMethod.urlFormat.substring(S3_URL_FORMAT.length(), hostStyleEnd);
            }
            else if (apiMethod.urlFormat.startsWith(ATMOS_URL_FORMAT)) {
                // S3 services have a URL format that's more comment style, so need to extract it
                int hostStyleEnd = apiMethod.urlFormat.indexOf("\n");
                apiMethod.path = apiMethod.urlFormat.substring(ATMOS_URL_FORMAT.length(), hostStyleEnd);

            }
            else {
                apiMethod.path = Utils.mergePaths(apiMethod.path, apiMethod.urlFormat);
            }
        }

        for (Tag tag : method.tags("@param")) {
            if (tag.text().startsWith("request") || tag.text().startsWith("response")) {
                try {
                    Matcher param = DATA_SERVICE_PARAM_PATTERN.matcher(tag.text());

                    if (param.find()) {
                        if (!param.group(4).equals("")) {

                            ApiField desc = new ApiField();
                            desc.name = param.group(3);
                            desc.primitiveType = param.group(6);
                            desc.min = Integer.valueOf(param.group(4));
                            desc.max = Integer.valueOf(param.group(5));
                            desc.description = param.group(7);

                            // Now find out where it goes
                            if (param.group(1).equals("request")) {
                                if (param.group(2) == null || param.group(2).equals("Header")) {
                                    apiMethod.headerParameters.add(desc);
                                }
                            }
                            else {
                                if (param.group(2) == null || param.group(2).equals("Header")) {
                                    apiMethod.responseHeaders.add(desc);
                                }
                            }
                        }
                        else {
                            DocReporter.printWarning("Ignoring :" + tag.text());
                        }

                    }
                    else {
                        DocReporter.printWarning("Data Services parameter did not match RegEx pattern");
                        DocReporter.printWarning(tag.text());
                    }
                } catch (Exception e) {
                    DocReporter.printError(tag.text());
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void addOutput(MethodDoc method, ApiMethod apiMethod) {
        if (method.returnType() != null &&
                !method.returnType().typeName().equals("void") &&
                !TypeUtils.isPrimitiveType(method.returnType())) {

            if (AnnotationUtils.hasAnnotation(method.returnType().asClassDoc(), KnownAnnotations.XMLRoot_Annotation)) {
                apiMethod.setFqReturnType(method.returnType().qualifiedTypeName());
                apiMethod.output = JaxbClassProcessor.convertToApiClass(method.returnType().asClassDoc());
                apiMethod.isTaskResponse = method.returnType().asClassDoc().name().equals("TaskResourceRep") ||
                        method.returnType().asClassDoc().name().equals("TaskList");
            }
        }
    }

    /**
     * NOTE : Needs some work to work out inherited roles/acls
     */
    public static void addSecurity(MethodDoc method, ApiMethod apiMethod) {
        AnnotationDesc checkPermission = AnnotationUtils.getAnnotation(method, KnownAnnotations.CheckPermission_Annotation);

        if (checkPermission != null) {
            // CheckPermission signifies that this method should use the explicit set of permissions
            for (AnnotationDesc.ElementValuePair pair : checkPermission.elementValues()) {
                if (pair.element().name().equals("roles")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiMethod.addRole(((FieldDoc) value.value()).name());
                    }
                }
                else if (pair.element().name().equals("acls")) {
                    for (AnnotationValue value : (AnnotationValue[]) pair.value().value()) {
                        apiMethod.addAcl(((FieldDoc) value.value()).name());
                    }
                }
            }
        } else if (AnnotationUtils.hasAnnotation(method, KnownAnnotations.InheritCheckPermission_Annotation)) {
            // InheritCheckPermission signifies that the method should inherit from teh DefaultPermission read or write lists
            boolean inheritWrite = AnnotationUtils.getAnnotationValue(method, KnownAnnotations.InheritCheckPermission_Annotation,
                    "writeAccess", false);

            if (inheritWrite) {
                apiMethod.acls.addAll(apiMethod.apiService.writeAcls);
                apiMethod.roles.addAll(apiMethod.apiService.writeRoles);
            }
            else {
                apiMethod.acls.addAll(apiMethod.apiService.readAcls);
                apiMethod.roles.addAll(apiMethod.apiService.readRoles);
            }
        }
    }

    public static void addQueryParameters(MethodDoc method, ApiMethod apiMethod) {
        apiMethod.queryParameters = getApiParameters(method, "javax.ws.rs.QueryParam");
    }

    public static void addPathParameters(MethodDoc method, ApiMethod apiMethod) {
        apiMethod.pathParameters = getApiParameters(method, "javax.ws.rs.PathParam");
    }

    public static void addExamples(ApiMethod apiMethod) {
        apiMethod.xmlExample = ExampleLoader.loadExample(apiMethod.getXmlExampleFilename());
        apiMethod.jsonExample = ExampleLoader.loadExample(apiMethod.getJsonExampleFilename());
    }

    public static List<ApiField> getApiParameters(MethodDoc method, String parameterAnnotation) {
        List<ApiField> apiParams = Lists.newArrayList();
        for (Parameter parameter : method.parameters()) {
            if (AnnotationUtils.hasAnnotation(parameter, parameterAnnotation)) {
                ApiField apiParam = new ApiField();

                apiParam.name = AnnotationUtils.getAnnotationValue(parameter, parameterAnnotation, KnownAnnotations.Value_Element,
                        parameter.name());
                if (parameter.type().asClassDoc() != null) {
                    apiParam.type = JaxbClassProcessor.convertToApiClass(parameter.type().asClassDoc());
                }

                for (ParamTag paramTag : method.paramTags()) {
                    if (paramTag.parameterName().equals(parameter.name())) {
                        apiParam.description = paramTag.parameterComment();
                    }
                }

                apiParams.add(apiParam);
            }
        }

        return apiParams;
    }
}
