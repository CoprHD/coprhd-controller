/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads service definition JSON from an input stream.
 */
public class ServiceDefinitionReader {
    private static final JsonParser PARSER = new JsonParser();
    private static final Gson GSON = new Gson();

    public ServiceDefinition readService(InputStream in) throws IOException {
        JsonObject descriptor = readDescriptor(in);

        // Remove the items so they aren't interpreted when converting from JSON
        JsonElement items = descriptor.remove("items");
        // Backwards compatibility
        JsonElement fields = descriptor.remove("fields");

        ServiceDefinition service = GSON.fromJson(descriptor, ServiceDefinition.class);
        if (items != null && items.isJsonObject()) {
            for (ItemDefinition item : readItems(items.getAsJsonObject())) {
                service.addItem(item);
            }
        }
        if (fields != null && fields.isJsonObject()) {
            for (ItemDefinition item : readItems(fields.getAsJsonObject())) {
                service.addItem(item);
            }
        }
        return service;
    }

    private List<ItemDefinition> readItems(JsonObject parent) {
        List<ItemDefinition> items = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : parent.entrySet()) {
            String name = entry.getKey();
            JsonElement child = entry.getValue();
            if (child.isJsonObject()) {
                ItemDefinition item = readItem(child.getAsJsonObject());
                item.name = name;
                items.add(item);
            }
        }
        return items;
    }

    private ItemDefinition readItem(JsonObject obj) {
        String type = getString(obj, "type");
        if (ServiceItem.TYPE_GROUP.equals(type)) {
            return readGroup(obj);
        }
        else if (ServiceItem.TYPE_TABLE.equals(type)) {
            return readTable(obj);
        }
        else if (ServiceItem.TYPE_MODAL.equals(type)) {
            return readModal(obj);
        }
        else {
            return readField(obj);
        }
    }

    private GroupDefinition readGroup(JsonObject obj) {
        JsonElement items = obj.remove("items");
        GroupDefinition group = GSON.fromJson(obj, GroupDefinition.class);
        if (items != null && items.isJsonObject()) {
            for (ItemDefinition item : readItems(items.getAsJsonObject())) {
                group.addItem(item);
            }
        }
        return group;
    }

    private TableDefinition readTable(JsonObject obj) {
        JsonElement items = obj.remove("items");
        // Ensure 'fields' doesn't get automatically mapped onto the table fields
        obj.remove("fields");
        TableDefinition table = GSON.fromJson(obj, TableDefinition.class);
        if (items != null && items.isJsonObject()) {
            for (ItemDefinition item : readItems(items.getAsJsonObject())) {
                // Only allow fields in tables
                if (item instanceof FieldDefinition) {
                    table.addField((FieldDefinition) item);
                }
            }
        }
        return table;
    }
    
    private ModalDefinition readModal(JsonObject obj) {
        JsonElement items = obj.remove("items");
        ModalDefinition modal = GSON.fromJson(obj, ModalDefinition.class);
        if (items != null && items.isJsonObject()) {
            for (ItemDefinition item : readItems(items.getAsJsonObject())) {
                modal.addItem(item);
            }
        }
        return modal;
    }

    private FieldDefinition readField(JsonObject obj) {
        return GSON.fromJson(obj, FieldDefinition.class);
    }

    /**
     * Reads a descriptor from an InputStream and injects i18n properties where applicable.
     * 
     * @param in
     *            the input stream.
     * @return the JSON descriptor.
     * 
     * @throws IOException
     *             if an I/O error occurs.
     */
    private JsonObject readDescriptor(InputStream in) throws IOException {
        try {
            return PARSER.parse(new InputStreamReader(in)).getAsJsonObject();
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    /**
     * Gets a JSON property as a string. If the property is a primitive it is converted to a string, otherwise null is
     * returned.
     * 
     * @param obj
     *            the JSON object.
     * @param name
     *            the property name.
     * @return the property value as a string.
     */
    private static String getString(JsonObject obj, String name) {
        JsonElement property = obj.get(name);
        return (property != null) && property.isJsonPrimitive() ? property.getAsString() : null;
    }
}