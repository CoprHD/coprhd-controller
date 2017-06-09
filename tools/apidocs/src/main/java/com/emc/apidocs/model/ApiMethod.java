/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.model;

import java.security.MessageDigest;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

import com.emc.apidocs.Utils;
import com.google.common.collect.Lists;

/**
 * Describes an API method
 */
public class ApiMethod {
    private static String XML_EXTENSION = "_xml_example.txt";
    private static String JSON_EXTENSION = "_json_example.txt";
    private String fqReturnType;
    private String fqRequestType;
    public String javaMethodName;
    public boolean isDataService = false;
    public String httpMethod;
    public String path;
    public String brief;
    public String description;
    public List<String> roles = Lists.newArrayList();
    public List<String> acls = Lists.newArrayList();
    public List<ApiField> queryParameters = Lists.newArrayList();
    public List<ApiField> pathParameters = Lists.newArrayList();
    public List<ApiField> headerParameters = Lists.newArrayList();
    public List<ApiField> responseHeaders = Lists.newArrayList();
    public List<String> prerequisites = Lists.newArrayList();
    public ApiClass input;
    public ApiClass output;
    public String responseDescription;
    public ApiService apiService;
    public String urlFormat;
    public String alert;
    public boolean isTaskResponse = false;
    public boolean isDeprecated;
    public String deprecatedMessage = "";

    public String indexKey;

    // Examples are stored as [0]=Request [1]=Response
    public String[] xmlExample;
    public String[] jsonExample;

    public String getDetailFileName() {
        return apiService.javaClassName.replace("\\.", "_") + "_" +
                javaMethodName.replaceAll("\\.", "_") + "_" +
                uniqueHash() + "_" +
                apiService.getPackageHash() + "_detail.html";
    }

    public String getTitle() {
        String splitCamel = Utils.splitCamelCase(javaMethodName);

        return Utils.upperCaseFirstChar(splitCamel);
    }

    public boolean hasRequestPayload() {
        return input != null;
    }

    public boolean hasResponsePayload() {
        return output != null;
    }

    public void addRole(String role) {
        roles.add(role);
    }

    public void addAcl(String acl) {
        acls.add(acl);
    }

    public void addPrerequisite(String prerequisite) {
        prerequisites.add(prerequisite);
    }

    public String getQualifiedName() {
        return apiService.javaClassName + "::" + javaMethodName;
    }

    public String getXmlExampleFilename() {
        return getExampleFilePrefix() + XML_EXTENSION;
    }

    public String getJsonExampleFilename() {
        return getExampleFilePrefix() + JSON_EXTENSION;
    }

    public String getIndexKey() {
        if (indexKey != null) {
            return indexKey;
        }

        return apiService.javaClassName + javaMethodName;
    }

    public String getIndexText() {
        // Index Key is added if there is not brief in order to make the search text unique since we're loading these into a map
        return Utils.dedupeWords(path + " " + (brief.equals("") ? indexKey : brief.toLowerCase())).trim();
    }

    public boolean hasDeprecatedMessage() {
        return !StringUtils.isBlank(deprecatedMessage);
    }

    private String getExampleFilePrefix() {
        String fixedName = httpMethod + path.replaceAll("/", "_").replaceAll("\\.", "_");
        if (fixedName.endsWith("_")) {
            fixedName = fixedName.substring(0, fixedName.length() - 1);
        }
        return fixedName;
    }

    private String uniqueHash() {
        MessageDigest digest = DigestUtils.getMd5Digest();

        digest.update(httpMethod.getBytes());
        digest.update(path.getBytes());

        for (ApiField field : queryParameters) {
            digest.update(field.name.getBytes());
        }

        for (ApiField field : pathParameters) {
            digest.update(field.name.getBytes());
        }

        return Hex.encodeHexString(digest.digest());
    }

    public String getFqReturnType() {
        return fqReturnType;
    }

    public void setFqReturnType(final String fqReturnType) {
        this.fqReturnType = fqReturnType;
    }

    public String getFqRequestType() {
        return fqRequestType;
    }
    
    public void setFqRequestType(final String fqRequestType) {
        this.fqRequestType = fqRequestType;
    }
}
