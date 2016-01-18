/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.util.MissingFormatArgumentException;
import java.util.MissingResourceException;

import org.junit.Assert;
import org.junit.Test;

public class MessagesTest {

    @Test
    public void createByName() {
        Messages messages = new Messages("com.emc.sa.util.MessagesTestOne");
        Assert.assertEquals("One", messages.get("message"));
    }

    @Test
    public void createFromClass() {
        Messages messages = new Messages(getClass(), "MessagesTestOne");
        Assert.assertEquals("One", messages.get("message"));
    }

    @Test
    public void chainedBundles() {
        Messages parent = new Messages(getClass(), "MessagesTestOne");
        Messages child = new Messages(parent, getClass(), "MessagesTestTwo");

        Assert.assertEquals("One", parent.get("message"));
        Assert.assertEquals("Two", child.get("message"));

        // Message that is not in the parent, only in the child
        try 
        {
            parent.get("child");
            Assert.fail("Parent bundle should not have contained 'child' key");
        } catch (MissingResourceException e) {
            // this is expected
        }

        Assert.assertEquals("Child", child.get("child"));

        // Messages that is in the parent, not in the child but accessible through parent
        Assert.assertEquals("Parent", parent.get("parent"));
        Assert.assertEquals("Parent", child.get("parent"));
    }

    @Test
    public void subdirBundle() {
        Messages messages = new Messages(getClass(), "messages/Subdir");
        Assert.assertEquals("Subdir", messages.get("message"));
    }

    @Test
    public void formattedMessage() {
        Messages messages = new Messages(getClass(), "MessagesTestOne");
        Assert.assertEquals("Message is Hello", messages.get("formatted", "Hello", "Unused"));
    }

    @Test(expected = MissingFormatArgumentException.class)
    public void missingFormatSpecifier() {
        Messages messages = new Messages(getClass(), "MessagesTestOne");
        messages.get("missingArg", "some arg");
    }

    @Test
    public void escapedMessage() {
        Messages messages = new Messages(getClass(), "MessagesTestOne");
        Assert.assertEquals("Escaped %s", messages.get("escaped", "Hello"));
    }
}
