/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package render;

import java.util.Collection;

import javax.xml.bind.Marshaller;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;

import play.Logger;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;
import plugin.ApiModelPlugin;

import com.emc.vipr.model.catalog.ApiList;

/**
 * Renderer for rendering API Responses. This can render any model classes from the models.api package.
 * It can render them as either XML using JAXB or as JSON.
 *
 * @author Chris Dail
 */
public class RenderApiModel extends Result {
    private Object o;

    public static void renderApi(Object o) {
        throw new RenderApiModel(o);
    }
    
    public RenderApiModel(Object o) {
        this.o = o;
    }
    
    @Override
    public void apply(Request request, Response response) {
        try {
            if (request.format.equals("xml")) {
                setContentTypeIfNotSet(response, "application/xml");
                Marshaller m = ApiModelPlugin.getInstance().getCtx().createMarshaller();
                if (o instanceof Collection) {
                    m.marshal(new ApiList((Collection) o), response.out);
                }
                else {
                    m.marshal(o, response.out);
                }
            }
            else {
                ObjectMapper mapper = new ObjectMapper();
                AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
                AnnotationIntrospector secondary = new JacksonAnnotationIntrospector();
                mapper.setAnnotationIntrospector(new AnnotationIntrospector.Pair(introspector, secondary));

                String json = mapper.writeValueAsString(o);
                String encoding = getEncoding();
                setContentTypeIfNotSet(response, "application/json; charset="+encoding);
                response.out.write(json.getBytes(encoding));
            }
        }
        catch (Exception e) {
            Logger.error(e, "API Rendering error");
            throw new UnexpectedException(e);
        }
    }
}
