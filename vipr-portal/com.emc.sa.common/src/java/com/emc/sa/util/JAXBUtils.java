/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * Utility for acquiring JAXB Contexts and performing serialization/deserialization. This class leverages special files
 * in META-INF/jaxb-index. These allow any plugin to contribute to the jaxb context used for serialization in the
 * engine.
 */
public class JAXBUtils {

    private static final Logger LOG = Logger.getLogger(JAXBUtils.class);

    private static JAXBContext CONTEXT;

    /** Serialize the given object to XML */
    public static String marshal(Object obj) throws JAXBException {
        StringWriter writer = new StringWriter();
        JAXBUtils.getJAXBContext().createMarshaller().marshal(obj, writer);
        return writer.toString();
    }

    /** Unmarshall the XML string to the Type of T */
    @SuppressWarnings("unchecked")
    public static <T> T unmarshal(String xml) throws JAXBException, IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes());
        Object obj = JAXBUtils.getJAXBContext().createUnmarshaller().unmarshal(input);
        input.close();

        return (T) obj;
    }

    public static JAXBContext getJAXBContext() throws JAXBException {
        if (CONTEXT == null) {
            CONTEXT = getJAXBContext(JAXBUtils.class.getClassLoader());
        }
        return CONTEXT;
    }

    private static JAXBContext getJAXBContext(ClassLoader classLoader) throws JAXBException {
        String contextPath = findContextPath(classLoader);
        return JAXBContext.newInstance(contextPath);
    }

    private static String findContextPath(ClassLoader classLoader) throws JAXBException {
        Set<String> packageNames = new HashSet<String>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/jaxb-index");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                LOG.debug("Found jaxb-index: " + url);
                readPackages(url, packageNames);
            }
        }
        catch (IOException e) {
            LOG.info("Failed to load META-INF/jaxb-index files", e);
            throw new JAXBException(e);
        }
        return StringUtils.join(packageNames, ":");
    }

    private static void readPackages(URL url, Set<String> packageNames) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            for (String s = r.readLine(); s != null; s = r.readLine()) {
                s = StringUtils.trimToNull(s);
                if (s != null) {
                    LOG.debug("Adding package JAXB context: " + s);
                    packageNames.add(s);
                }
            }
        }
        finally {
            r.close();
        }
    }
}
