/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.apidocs.differencing;

/**
 * Presents all constants here.
 */
public class Constants {

    private Constants() {}

    // Root element node of rest api xml document
    public static final String ROOT_NODE = "api-docs";

    // General attributes
    public static final String ATTRIBUTE_NAME = "name";

    // Primary element of "data"
    public static final String DATA_NODE = "data";
    public static final String DATA_SCHEMA_NODE = "schema";
    public static final String ELEMENTS_NODE = "elements";
    public static final String TYPES_NODE = "type";
    public static final String ELEMENT_EXAMPLE_XML_NODE = "examplexml";

    // Primary element of "rest"
    public static final String REST_NODE = "rest";
    public static final String RESOURCES_NODE = "resources";
    public static final String OPERATION_NODE = "operation";
    public static final String OPERATION_PARAMETER_NODE = "parameter";
    public static final String OPERATION_INVALUE_NODE = "inValue";
    public static final String OPERATION_OUTVALUE_NODE = "outValue";
    public static final String XML_ELEMENT_NODE = "xmlElement";
    public static final String ATTRIBUTE_ELEMENT_NAME = "elementName";

    // CDATA prefix and suffix for example xml content
    public static final String EXAMLE_XML_HEADER = "<![CDATA[";
    public static final String EXAMLE_XML_TAILER = "]]>";

    // XML example code prefix and suffix, use to present xml in html page
    public static final String CODE_PREFIX = "<pre><code>";
    public static final String CODE_SUFFIX = "</code></pre>";

    // URL path separator
    public static final String URL_PATH_SEPARATOR = "/";

    public static final String NAME_STRING_SEPARATOR = "-";
    //XML file suffix
    public static final String XML_FILE_SUFFIX = "xml";

    // internal apis
    public static final String INTERNAL_API = "internal";

    public static final String COMMENT_MARKER = "#";
    public static final String TITLE_MARKER = "=";

}
