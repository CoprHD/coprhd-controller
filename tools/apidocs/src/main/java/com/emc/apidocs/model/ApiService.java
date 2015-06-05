/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.model;

import com.emc.apidocs.Utils;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes an API service
 */
public class ApiService {

    public String packageName;
    public List<String> readRoles = new ArrayList<String>();
    public List<String> writeRoles = new ArrayList<String>();
    public List<String> readAcls = new ArrayList<String>();
    public List<String> writeAcls = new ArrayList<String>();
    public String javaClassName;
    public List<ApiMethod> methods = new ArrayList<ApiMethod>();
    public String description;
    public String path;
    public String titleOverride;
    public boolean isDeprecated;
    public String deprecatedMessage = "";

    public void addMethod(ApiMethod method) {
        methods.add(method);
    }
    
    public String getFqJavaClassName() {
        return packageName+"."+javaClassName;
    }

    public String getOverviewFileName() {
        return javaClassName.replaceAll("\\.","_")+"_"+getPackageHash()+"_overview.html";
    }

    public String getNewMethodsFileName() {
        return javaClassName.replaceAll("\\.","_")+"_"+getPackageHash()+"_newMethodsOverview.html";
    }

    public String getRemovedMethodsFileName() {
        return javaClassName.replaceAll("\\.","_")+"_"+getPackageHash()+"_removedMethodsOverview.html";
    }

    public String getModifiedMethodsFileName() {
        return javaClassName.replaceAll("\\.","_")+"_"+getPackageHash()+"_modifiedMethodsOverview.html";
    }

    public String getModifiedMethodFileName(String methodName) {
        return javaClassName.replaceAll("\\.","_")+"_"+getPackageHash()+"_modifiedMethod_"+methodName.replaceAll("\\.","_")+".html";
    }

    public String getPackageHash() {
        return DigestUtils.md5Hex(packageName);
    }

    public String getTitle() {
        if (titleOverride != null) {
            return  titleOverride;
        }

        String splitCamel = Utils.splitCamelCase(javaClassName);

        // Fix common prefix issues caused by camel splitting
        if (splitCamel.startsWith("S 3")) {
            splitCamel = "S3"+splitCamel.substring(3);
        }
        else if (splitCamel.startsWith("Un ")) {
            splitCamel = "Un"+splitCamel.substring(3);
        }

        // Strip off Service
        if (splitCamel.endsWith(" Service")) {
            int start = splitCamel.indexOf(" Service");
            splitCamel = splitCamel.substring(0, start);
        }

        return Utils.upperCaseFirstChar(splitCamel);
    }

    public void addReadRole(String role) {
        readRoles.add(role);
    }

    public void addWriteRole(String role) {
        writeRoles.add(role);
    }

    public void addReadAcl(String acl) {
        readAcls.add(acl);
    }

    public void addWriteAcl(String acl) {
        writeAcls.add(acl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApiService that = (ApiService) o;

        if (javaClassName != null ? !javaClassName.equals(that.javaClassName) : that.javaClassName != null)
            return false;
        if (packageName != null ? !packageName.equals(that.packageName) : that.packageName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = packageName != null ? packageName.hashCode() : 0;
        result = 31 * result + (javaClassName != null ? javaClassName.hashCode() : 0);
        return result;
    }
}
