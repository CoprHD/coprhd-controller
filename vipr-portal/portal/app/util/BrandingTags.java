/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import play.Play;
import play.templates.FastTags;
import play.templates.GroovyTemplate;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Locale;

/**
 * Tags to support Branding.
 * 
 * @author Chris Dail
 */
@FastTags.Namespace("Branding")
public class BrandingTags extends FastTags {
    public static void _applicationName(Map<?, ?> args, Closure body, PrintWriter out, GroovyTemplate.ExecutableTemplate template,
            int fromLine) {
        out.write(getApplicationName());
    }

    public static void _favicon(Map<?, ?> args, Closure body, PrintWriter out, GroovyTemplate.ExecutableTemplate template, int fromLine) {
        out.write("<link rel=\"shortcut icon\" type=\"image/png\" href=\"");
        out.write(getBrandPath() + "favicon.ico");
        out.write("\"></link>");
    }

    public static String getApplicationName() {
        return MessagesUtils.get(getBrandingMessagePrefix() + "application.name");
    }

    public static String getBrandPath() {
        return "/public/branding/";
    }

    private static String getBrandingMessagePrefix() {
    	Locale locale=Locale.getDefault();
    	
    	String currentCountry = locale.getCountry(); 
    	String currentLanguage = locale.getLanguage();
    	
        String brand = Play.configuration.getProperty("branding.brand", "");
        if (StringUtils.isNotEmpty(brand)) {
            return brand + ".";
        }

        if (!currentLanguage.equals("zh") && !currentCountry.equals("CN")) 
        	return "Dell-";
        
        return "";
    }
}
