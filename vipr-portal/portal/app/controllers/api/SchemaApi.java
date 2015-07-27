/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import controllers.Common;
import play.Logger;
import play.mvc.Controller;
import play.mvc.With;
import plugin.ApiModelPlugin;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;

/**
 * API to retrieve schemas for the API.
 *
 * @author Chris Dail
 */
@With(Common.class)
public class SchemaApi extends Controller {
   public static void schema() throws IOException {
       final StringWriter writer = new StringWriter();

       ApiModelPlugin.getInstance().getCtx().generateSchema(new SchemaOutputResolver() {
           public Result createOutput(String namespaceUri, String filename) throws IOException {
               Logger.debug("Generating API Schemas %s", filename);
               StreamResult result = new StreamResult(writer);
               result.setSystemId("schema.xsd");
               return result;
           }
       });
       renderXml(writer.toString());
   }
}
