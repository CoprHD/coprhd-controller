/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class ServiceDefinitionReaderTest {
    private static final String FIELD_FORMAT = "{ items:{ field:{ %s } } }";
    private static final String GROUP_FORMAT = "{ items:{ group:{ type:'group' %s } } }";
    private static final String TABLE_FORMAT = "{ items:{ table:{ type:'table' %s } } }";

    private static String field(String contents) {
        return String.format(FIELD_FORMAT, contents);
    }

    private static String group(String contents) {
        if (StringUtils.isNotBlank(contents)) {
            return String.format(GROUP_FORMAT, "," + contents);
        }
        else {
            return String.format(GROUP_FORMAT, "");
        }
    }

    private static String table(String contents) {
        if (StringUtils.isNotBlank(contents)) {
            return String.format(TABLE_FORMAT, "," + contents);
        }
        else {
            return String.format(TABLE_FORMAT, "");
        }
    }

    private ServiceDefinition readService(String content) {
        try {
            ServiceDefinitionReader reader = new ServiceDefinitionReader();
            return reader.readService(new ByteArrayInputStream(content.getBytes()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ItemDefinition readItem(String content) {
        ServiceDefinition service = readService(content);
        Assert.assertNotNull(service);
        Assert.assertNotNull(service.items);
        Assert.assertEquals(1, service.items.size());
        ItemDefinition item = service.items.values().iterator().next();
        return item;
    }

    private FieldDefinition readField(String content) {
        ItemDefinition item = readItem(field(content));
        Assert.assertEquals(FieldDefinition.class, item.getClass());
        return (FieldDefinition) item;
    }

    private ValidationDefinition readValidation(String content) {
        return readField("validation:{ " + content + " }").validation;
    }

    private GroupDefinition readGroup(String content) {
        ItemDefinition item = readItem(group(content));
        Assert.assertEquals(GroupDefinition.class, item.getClass());
        return (GroupDefinition) item;
    }

    private TableDefinition readTable(String content) {
        ItemDefinition item = readItem(table(content));
        Assert.assertEquals(TableDefinition.class, item.getClass());
        return (TableDefinition) item;
    }

    @Test
    public void testBasicService() {
        String json = "{ serviceId:'basic', descriptionKey: 'hello', warningMessageKey:'warn' }";
        ServiceDefinition service = readService(json);
        Assert.assertEquals("basic", service.serviceId);
        Assert.assertEquals("hello", service.descriptionKey);
        Assert.assertEquals("warn", service.warningMessageKey);
        Assert.assertNotNull(service.items);
        Assert.assertTrue(service.items.isEmpty());
    }

    @Test
    public void testEmptyFields() {
        String json = "{ items:{ a:{}, b:{} } }";
        ServiceDefinition service = readService(json);
        Assert.assertNotNull(service.items);
        Assert.assertEquals(2, service.items.size());

        Assert.assertNotNull(service.items.get("a"));
        Assert.assertEquals(FieldDefinition.class, service.items.get("a").getClass());

        Assert.assertNotNull(service.items.get("b"));
        Assert.assertEquals(FieldDefinition.class, service.items.get("b").getClass());
    }

    @Test
    public void testFieldsTablesAndGroups() {
        String json = "{ items:{ one:{}, two:{ type:'group' }, three:{ type:'table' } } }";
        ServiceDefinition service = readService(json);

        Assert.assertNotNull(service.items);
        Assert.assertEquals(3, service.items.size());

        Assert.assertNotNull(service.items.get("one"));
        Assert.assertEquals(FieldDefinition.class, service.items.get("one").getClass());

        Assert.assertNotNull(service.items.get("two"));
        Assert.assertEquals(GroupDefinition.class, service.items.get("two").getClass());

        Assert.assertNotNull(service.items.get("three"));
        Assert.assertEquals(TableDefinition.class, service.items.get("three").getClass());
    }

    @Test
    public void testItems() {
        ItemDefinition item = readItem("{ items: { item: { labelKey:'LabelKey', descriptionKey:'DescriptionKey' } } }");
        Assert.assertEquals("LabelKey", item.labelKey);
        Assert.assertEquals("DescriptionKey", item.descriptionKey);
    }

    @Test
    public void testFieldRequired() {
        Assert.assertTrue(readField("").required);
        Assert.assertTrue(readField("required:true").required);
        Assert.assertFalse(readField("required:false").required);
    }

    @Test
    public void testFieldLockable() {
        Assert.assertFalse(readField("").lockable);
        Assert.assertTrue(readField("lockable:true").lockable);
        Assert.assertFalse(readField("lockable:false").lockable);
    }

    @Test
    public void testFieldInitialValue() {
        Assert.assertNull(readField("").initialValue);
        Assert.assertEquals("", readField("initialValue:''").initialValue);
        Assert.assertEquals("Value", readField("initialValue:'Value'").initialValue);
    }

    @Test
    public void testFieldSelect() {
        Assert.assertEquals(ServiceField.SELECT_ONE, readField("").select);
        Assert.assertEquals(ServiceField.SELECT_ONE, readField("select:'one'").select);
        Assert.assertEquals(ServiceField.SELECT_MANY, readField("select:'many'").select);
    }

    @Test
    public void testFieldOptions() {
        Assert.assertTrue(readField("").options.isEmpty());
        Map<String, String> options = readField("options:{a:'A', b:'B'}").options;
        Assert.assertEquals(2, options.size());
        Assert.assertEquals("A", options.get("a"));
        Assert.assertEquals("B", options.get("b"));
    }

    @Test
    public void testFieldValidation() {
        Assert.assertNull(readValidation("").min);
        Assert.assertNull(readValidation("").regEx);
        Assert.assertNull(readValidation("").errorKey);

        Assert.assertEquals(new Integer(1), readValidation("min:1").min);
        Assert.assertEquals(new Integer(5), readValidation("max:5").max);
        Assert.assertEquals(".*", readValidation("regEx:'.*'").regEx);
        Assert.assertEquals("ErrorKey", readValidation("errorKey:'ErrorKey'").errorKey);
    }

    @Test
    public void testGroup() {
        Assert.assertTrue(readGroup("").collapsible);
        Assert.assertTrue(readGroup("collapsible:true").collapsible);
        Assert.assertFalse(readGroup("collapsible:false").collapsible);

        Assert.assertFalse(readGroup("").collapsed);
        Assert.assertTrue(readGroup("collapsed:true").collapsed);
        Assert.assertFalse(readGroup("collapsed:false").collapsed);

        Assert.assertNotNull(readGroup("").items);
        Assert.assertEquals(0, readGroup("").items.size());

        Map<String, ItemDefinition> items = readGroup("items:{a:{}, b:{}}").items;
        Assert.assertEquals(2, items.size());
        Assert.assertEquals(FieldDefinition.class, items.get("a").getClass());
        Assert.assertEquals(FieldDefinition.class, items.get("b").getClass());
    }

    @Test
    public void testTable() {
        Assert.assertTrue(readTable("").fields.isEmpty());

        Map<String, FieldDefinition> fields = readTable("items:{a:{type:'text'}, b:{type:'number'}}").fields;
        Assert.assertEquals(2, fields.size());
        Assert.assertEquals("text", fields.get("a").type);
        Assert.assertEquals("number", fields.get("b").type);
    }
}
