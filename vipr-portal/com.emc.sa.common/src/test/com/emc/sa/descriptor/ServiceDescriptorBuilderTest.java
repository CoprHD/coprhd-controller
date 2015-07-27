/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.io.ByteArrayInputStream;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

public class ServiceDescriptorBuilderTest {
    private String getBundleName(String name) {
        return getClass().getPackage().getName() + ".resources." + name;
    }

    private ServiceDescriptor readDescriptor(String content, String... bundles) {
        return readDescriptor(content, Locale.getDefault(), bundles);
    }

    private ServiceDescriptor readDescriptor(String content, Locale locale, String... bundles) {
        try {
            ServiceDefinitionReader reader = new ServiceDefinitionReader();
            ServiceDefinition service = reader.readService(new ByteArrayInputStream(content.getBytes()));
            ServiceDescriptorBuilder builder = new ServiceDescriptorBuilder(bundles);
            return builder.build(service);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDescriptorDescription() {
        String json = "{ descriptionKey: 'testDescriptorDescriptionKey.description' }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        Assert.assertEquals("Hello World", descriptor.getDescription());
    }

    @Test
    public void testDescriptorDefaultDescriptionKey() {
        String json = "{ serviceId: 'DefaultDescriptionKey' }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        Assert.assertEquals("Default Description", descriptor.getDescription());
    }

    @Test
    public void testMultipleResourceBundles() {
        String json = "{ serviceId: 'MultiBundles', items: { one: {} } }";

        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"),
                getBundleName("SecondaryMessages"));
        ServiceField one = descriptor.getField("one");
        Assert.assertNotNull(one);

        Assert.assertEquals("MultiBundles One Label", one.getLabel());
        Assert.assertEquals("MultiBundles One Description", one.getDescription());
    }

    @Test
    public void testValidationError() {
        String json = "{ serviceId: 'FailureMessageKey', items: { field: { validation: { errorKey: 'failureMessageKey' } } } }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        ServiceField field = descriptor.getField("field");
        Assert.assertNotNull(field);
        Assert.assertEquals("Failure Message", field.getValidation().getError());
    }

    @Test
    public void testDefaultValidationError() {
        String json = "{ serviceId: 'DefaultFailureMessageKey', items: { field: { validation: { } } } }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        ServiceField field = descriptor.getField("field");
        Assert.assertNotNull(field);
        Assert.assertEquals("Failure Message", field.getValidation().getError());
    }

    @Test
    public void testBaseKey() {
        String json = "{ baseKey: 'BaseKey', serviceId: 'ServiceID' }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        Assert.assertEquals("Base Key", descriptor.getDescription());
    }

    @Test
    public void testOptions() {
        String json = "{ items: { field: { options: {'a':'A', 'b':'B'} } } }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        ServiceField field = descriptor.getField("field");
        Assert.assertNotNull(field);
        Assert.assertNotNull(field.getOptions());
        Assert.assertEquals(2, field.getOptions().size());
        Assert.assertEquals("A", field.getOptions().get("a"));
        Assert.assertEquals("B", field.getOptions().get("b"));
    }

    @Test
    public void testNoTitle() {
        String json = "{ baseKey: 'BaseKey', serviceId: 'ServiceID' }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("PrimaryMessages"));
        Assert.assertEquals("ServiceID", descriptor.getTitle());
    }

    @Test
    public void testNoFieldLabel() {
        String json = "{ items: { field: { options: {'a':'A', 'b':'B'} } } }";
        ServiceDescriptor descriptor = readDescriptor(json, getBundleName("SecondaryMessages"));
        ServiceField field = descriptor.getField("field");
        Assert.assertNotNull(field);
        Assert.assertEquals("field", field.getLabel());
    }
}
