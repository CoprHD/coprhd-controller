/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.rest.request;

import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * The base class for all POST/PUT REST API requests which have a body in JSON format.
 *
 * Created by gang on 9/26/16.
 */
public abstract class BaseRequest implements RequestBodyGenerator {

    /**
     * Return the name of the template which is in Velocity format. Since the template
     * file is named as the same as the RequestBodyGenerator class and end with ".vm", the template
     * file needed is just the class full name with a ".vm" postfix.
     *
     * @return
     */
    public String getTemplateName() {
        return this.getClass().getName().replace('.', '/') + ".vm";
    }

    /**
     * Return the arguments map for the template rendering.
     *
     * @return
     */
    public abstract Map<String, Object> getArguments();

    @Override
    public String getRequestBody() {
        // Prepare the engine.
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        engine.init();
        // Load Template.
        Template template = engine.getTemplate(this.getTemplateName(), "UTF-8");
        // Prepare arguments.
        VelocityContext context = new VelocityContext();
        Map<String, Object> arguments = this.getArguments();
        for(String name : arguments.keySet()) {
            context.put(name, arguments.get(name));
        }
        // Render the template with arguments and return.
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }
}
