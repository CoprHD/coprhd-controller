/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package plugin;

import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;

import play.Logger;
import play.PlayPlugin;
import play.mvc.Http.Request;

import com.emc.vipr.model.catalog.ObjectFactory;

/**
 * Plugin for binding API Models to API services.
 * 
 * @author Chris Dail
 */
public class ApiModelPlugin extends PlayPlugin {
    private static ApiModelPlugin instance = null;
    public JAXBContext ctx;

    public static ApiModelPlugin getInstance() {
        return instance;
    }

    public JAXBContext getCtx() {
        return ctx;
    }

    public void onApplicationStart() {
        instance = this;// NOSONAR
                        // ("Suppressing Sonar violation of Lazy initialization of static fields should be synchronized for instance")
        Logger.info("API Model Plugin Loaded");
        try {
            ClassLoader cl = ObjectFactory.class.getClassLoader();
            ctx = JAXBContext.newInstance("com.emc.vipr.model.catalog:com.emc.storageos.model", cl);
        } catch (JAXBException e) {
            Logger.error(e, "Error initializing JAXB context");
        }
    }

    public Object bind(String name, Class clazz, Type type, Annotation[] annotations, Map<String, String[]> params) {
        Request request = Request.current();
        if (request != null && request.format != null) {
            if (request.format.equals("xml") || request.contentType.equals("application/xml")) {
                return getXml(clazz);
            }
            else if (request.format.equals("json") || request.contentType.equals("application/json")) {
                return getJson(clazz);
            }
        }

        return null;
    }

    private Object getXml(Class clazz) {
        try {
            if (clazz.getAnnotation(XmlRootElement.class) != null) {
                Unmarshaller um = ctx.createUnmarshaller();
                StringReader sr = new StringReader(Request.current().params.get("body"));
                return um.unmarshal(sr);
            }
        } catch (JAXBException e) {
            Logger.error("Problem parsing XML: %s", e.getMessage());
        }
        return null;
    }

    private Object getJson(Class clazz) {
        try {
            // Json files do not need to extend this, but our API Models do
            if (clazz.getAnnotation(XmlRootElement.class) != null) {
                ObjectMapper mapper = new ObjectMapper();
                AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
                AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();
                mapper.setAnnotationIntrospector(new AnnotationIntrospector.Pair(introspector, secondary));
                return mapper.readValue(Request.current().params.get("body"), clazz);
            }
        } catch (Exception e) {
            Logger.error("Problem parsing JSON: %s", e.getMessage());
        }
        return null;
    }
}
