/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs;

import com.emc.apidocs.model.ApiClass;
import com.emc.apidocs.model.ApiField;
import com.emc.apidocs.model.ApiMethod;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.Set;

/**
 * A collection of common Utilities
 */
public class Utils {
    private static String XML_START = "&lt;";
    private static String XML_END = "&gt;";
    private static String NEW_LINE = "\n";

    public static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                        ),
                " "
                );
    }

    public static String upperCaseFirstChar(String string) {
        if (string == null || string.equals("")) {
            return string;
        }

        return Character.toUpperCase(string.charAt(0)) +
                string.substring(1);
    }

    public static String lowerCaseFirstChar(String string) {
        if (string == null || string.equals("")) {
            return string;
        }

        return Character.toLowerCase(string.charAt(0)) +
                string.substring(1);
    }

    public static String mergePaths(String part1, String part2) {
        if (part1.endsWith("/")) {
            if (part2.startsWith("/")) {
                return part1 + part2.substring(1);
            }
            else {
                return part1 + part2;
            }
        }
        else {
            if (part2.startsWith("/")) {
                return part1 + part2;
            }
            else {
                return part1 + (part2.equals("") ? "" : "/") + part2;
            }
        }

    }

    public static String dedupeWords(String input) {
        Set<String> deduped = Sets.newHashSet(input.split(" "));
        StringBuffer output = new StringBuffer();
        for (String word : deduped) {
            output.append(word + " ");
        }

        return output.toString();
    }

    public static StringBuffer addSpaces(int number, StringBuffer buffer) {
        String response = "";
        for (int i = 0; i < number; i++) {
            buffer.append(" ");
        }

        return buffer;
    }

    public static String repeatSpace(int number) {
        String response = "";
        for (int i = 0; i < number; i++) {
            response = response + "   ";
        }
        return response;
    }

    public static String generateJson(ApiClass element) {
        StringBuffer buffer = new StringBuffer();
        generateJSON(element, buffer);

        try {
            GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
            Gson g = builder.create();
            JsonParser parser = new JsonParser();
            JsonElement el = parser.parse(buffer.toString());

            return g.toJson(el);
        } catch (Exception e) {
            throw new RuntimeException(buffer.toString(), e);
        }
    }

    public static String generateJSON(ApiClass element, StringBuffer buffer) {

        buffer.append("{\n");
        int counter = 0;
        for (ApiField field : element.fields) {
            generateJSON(field, buffer);
            counter++;

            if (counter < element.fields.size()) {
                buffer.append(",\n");
            }

        }
        buffer.append("}");

        return buffer.toString();
    }

    public static void generateJSON(ApiField field, StringBuffer buffer) {
        String name = field.jsonName;
        if (name == null) {
            name = !field.wrapperName.equals("") ? field.wrapperName : field.name;
        }

        buffer.append("\"")
                .append(name);
        buffer.append("\": ");

        if (field.collection) {
            if (field.hasChildElements()) {
                buffer.append("[\n");
                generateJSON(field.type, buffer);
                buffer.append("]");
            } else {
                buffer.append("[\"\"]");
            }
        } else {
            if (field.hasChildElements()) {
                generateJSON(field.type, buffer);
            }
            else {
                buffer.append("\"\"");
            }
        }
    }

    /**
     * Returns an XML Payload format for the given Api Class
     */
    public static String generateXml(ApiClass element) {
        StringBuffer buffer = new StringBuffer(XML_START + element.name + XML_END + "\n");
        for (ApiField field : element.fields) {
            generateXml(field, 1, buffer);
        }
        buffer.append(XML_START).append("/").append(element.name).append(XML_END);

        return buffer.toString();
    }

    private static void generateXml(ApiField element, int level, StringBuffer response) {
        if (!element.wrapperName.equals("")) {  // Output <WRAPPER>
            response.append(repeatSpace(level));
            response.append(XML_START).append(element.wrapperName).append(XML_END).append(NEW_LINE);
            level = level + 1;
        }

        if (!element.hasChildElements()) {  // Output as <name></name>
            response.append(repeatSpace(level));
            response.append(XML_START).append(element.name);
            addAttributes(element.type, response);
            response.append(XML_END);
            response.append(XML_START).append("/").append(element.name).append(XML_END).append(NEW_LINE);
        }
        else {
            response.append(repeatSpace(level));
            response.append(XML_START).append(element.name).append(XML_END).append(NEW_LINE);
            addAttributes(element.type, response);

            for (ApiField field : element.type.fields) {
                generateXml(field, level + 1, response);
            }

            response.append(repeatSpace(level));
            response.append(XML_START).append("/").append(element.name).append(XML_END).append(NEW_LINE);
        }

        if (!element.wrapperName.equals("")) {  // OUTPUT </WRAPPER>
            level = level - 1;
            response.append(repeatSpace(level));
            response.append(XML_START).append("/").append(element.wrapperName).append(XML_END).append(NEW_LINE);
        }
    }

    private static void addAttributes(ApiClass element, StringBuffer response) {
        if (element == null || element.attributes == null) {
            return;

        }
        for (ApiField attribute : element.attributes) {
            response.append(" " + attribute.name + "=\"\"");
        }
    }

    public static void dump(ApiMethod apiMethod) {
        System.out.println("=================================");
        System.out.println(apiMethod.httpMethod + " " + apiMethod.path);
        System.out.println("JavaMethod:" + apiMethod.javaMethodName);
        System.out.println("Brief: " + apiMethod.brief);
        System.out.println("Description:" + apiMethod.description);
        System.out.println("\nPATH PARAMETERS:");
        for (ApiField param : apiMethod.pathParameters) {
            System.out.println("- [" + param.name + "] " + param.description);
        }
        System.out.println("\nQUERY PARAMETERS");
        for (ApiField param : apiMethod.queryParameters) {
            System.out.println("- [" + param.name + "] " + param.description);
        }
        System.out.println("\nROLES:");
        for (String role : apiMethod.roles) {
            System.out.println("- " + role);
        }
        System.out.println("\nACLS:");
        for (String acl : apiMethod.acls) {
            System.out.println("- " + acl);
        }

        if (apiMethod.input != null) {
            System.out.println("INPUT: " + apiMethod.input.name);
        }

        if (apiMethod.output != null) {
            System.out.println("OUTPUT: " + apiMethod.output.name);
            dumpAsXml(apiMethod.output, 0);
        }
    }

    public static void dumpAsXml(ApiClass apiClass, int level) {
        printTabs(level);
        System.out.print("<" + apiClass.name);

        for (ApiField attribute : apiClass.attributes) {
            System.out.print(" " + attribute.name + "=\"\"");
        }

        if (apiClass.fields.isEmpty()) {
            System.out.println("/>");
        }
        else {
            System.out.println(">");
        }

        for (ApiField element : apiClass.fields) {
            if (element.isPrimitive()) {
                printTabs(level + 1);

                System.out.println("<" + element.name + "/>       " + element.primitiveType + "  [" + element.description + "]");
            }
            else {
                printTabs(level + 1);
                System.out.println("<" + element.name + ">      " + element.description + (element.collection ? "MANY" : ""));
                dumpAsXml(element.type, level + 2);
                printTabs(level + 1);
                System.out.println("</" + element.name + ">");
            }
        }

        if (!apiClass.fields.isEmpty()) {
            printTabs(level);
            System.out.println("</" + apiClass.name + ">");
        }
    }

    public static void printTabs(int times) {
        for (int i = 0; i < times; i++) {
            System.out.print("\t");
        }
    }
}
