/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import groovy.lang.Closure;
import play.templates.FastTags;
import play.templates.GroovyTemplate;
import play.templates.TagContext;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Tags to support different licensing options.
 *
 * @author Chris Dail
 */
@FastTags.Namespace("license")
public class LicenseTags extends FastTags {
    public static void
            _ifController(Map<?, ?> args, Closure body, PrintWriter out, GroovyTemplate.ExecutableTemplate template, int fromLine) {
        doIf(LicenseUtils.isControllerLicensed(), body);
    }

    private static void doIf(boolean condition, Closure body) {
        if (condition) {
            body.call();
            TagContext.parent().data.put("_executeNextElse", false);
        }
        else {
            TagContext.parent().data.put("_executeNextElse", true);
        }
    }
}
