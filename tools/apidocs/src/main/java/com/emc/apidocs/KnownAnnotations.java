/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs;

/**
 * Names of common annotations that are needed during processing
 */
public class KnownAnnotations {
    public static final String XMLElement_Annotation = "javax.xml.bind.annotation.XmlElement";
    public static final String XMLAttribute_Annotation = "javax.xml.bind.annotation.XmlAttribute";
    public static final String XMLRoot_Annotation = "javax.xml.bind.annotation.XmlRootElement";
    public static final String XMLElementWrapper_Annotation = "javax.xml.bind.annotation.XmlElementWrapper";
    public static final String XMLAccessorType_Annotation = "javax.xml.bind.annotation.XmlAccessorType";
    public static final String XMLTransient_Annotation = "javax.xml.bind.annotation.XmlTransient";
    public static final String XMLEnum_Annotation = "javax.xml.bind.annotation.XmlEnum";
    public static final String JsonProperty_Annotation = "org.codehaus.jackson.annotate.JsonProperty";

    public static final String CheckPermission_Annotation = "com.emc.storageos.security.authorization.CheckPermission";
    public static final String InheritCheckPermission_Annotation = "com.emc.storageos.security.authorization.InheritCheckPermission";
    public static final String DefaultPermissions_Annotation = "com.emc.storageos.security.authorization.DefaultPermissions";

    public static final String Length_Annotation = "com.emc.storageos.model.valid.Length";
    public static final String Range_Annotation = "com.emc.storageos.model.valid.Range";

    public static final String Path_Annotation = "javax.ws.rs.Path";
    public static final String PathParam_Annotation = "javax.ws.rs.PathParam";
    public static final String Value_Element = "value";

    public static final String Deprecated_Annotation = "java.lang.Deprecated";

    private static String getFQNName(Class clazz) {
        return clazz.getPackage() + "." + clazz.getSimpleName();
    }
}
