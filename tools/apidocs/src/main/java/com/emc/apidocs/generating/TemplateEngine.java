/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.generating;

import com.emc.apidocs.DocReporter;
import com.emc.apidocs.KnownPaths;
import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple Template engine which calls out to Groovy to process template files
 */
public class TemplateEngine {
    private static Map<String, Template> templates = new HashMap<String, Template>();

    /**
     * Call the template with the parameters, but return the response as a string
     */
    public static String generateStringFromTemplate(File templateFile, Map<String, Object> parameters) {
        try {
            Template template = getTemplate(templateFile);
            Writable finishedTemplate = template.make(parameters);
            return finishedTemplate.toString();
        } catch (Throwable e) {
            DocReporter.printError("Error whilst generating page from template " + templateFile);
            DocReporter.printError(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Call the template with the parameters and save the response to the outputFile
     */
    public static void generateFileFromTemplate(File templateFile, File outputFile, Map<String, Object> parameters) {
        try {
            Template template = getTemplate(templateFile);
            Writable finishedTemplate = template.make(parameters);
            try {
                IOUtils.copy(new ByteArrayInputStream(finishedTemplate.toString().getBytes()), new FileOutputStream(outputFile));
            } catch (Exception e) {
                throw new RuntimeException("Error writing to file " + outputFile, e);
            }
        } catch (Throwable e) {
            DocReporter.printError("Error whilst generating page from file " + outputFile + " from template " + templateFile);
            DocReporter.printError(e.getMessage());
            throw new RuntimeException("Unable to process template" + templateFile, e);
        }
    }

    /**
     * Returns a cached template file
     */
    private static synchronized Template getTemplate(File templateFile) {
        if (templates.containsKey(templateFile.getName())) {
            return templates.get(templateFile.getName());
        }

        SimpleTemplateEngine engine = new SimpleTemplateEngine();
        try {
            String templateContents = IOUtils.toString(new FileInputStream(templateFile));
            templateContents = preprocessTemplate(templateContents);

            Template template = engine.createTemplate(templateContents);
            templates.put(templateFile.getName(), template);

            return template;
        } catch (Exception e) {
            throw new RuntimeException("Unable to process template" + templateFile, e);
        }
    }

    private static Pattern PRE_PROCESSOR_PATTERN = Pattern.compile("<@(.*)>");
    private static Pattern INCLUDE_TAG_PATTERN = Pattern.compile("include [\"'](.*)[\"']");

    private static String preprocessTemplate(String templateContents) {
        StringBuffer result = new StringBuffer(templateContents.length());

        Matcher tagMatcher = PRE_PROCESSOR_PATTERN.matcher(templateContents);
        while (tagMatcher.find()) {
            Matcher includeTag = INCLUDE_TAG_PATTERN.matcher(tagMatcher.group(1));
            if (includeTag.find()) {
                File partFile = KnownPaths.getTemplatePartFile(includeTag.group(1));
                try {
                    String templatePart = IOUtils.toString(new FileInputStream(partFile));

                    tagMatcher.appendReplacement(result, Matcher.quoteReplacement(templatePart));
                } catch (IOException e) {
                    throw new RuntimeException("Error reading template part " + partFile.getAbsolutePath(), e);
                }
            }
            else {
                tagMatcher.appendTail(result);
            }
        }

        tagMatcher.appendTail(result);
        return result.toString();
    }
}
