/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers;

import java.io.File;

import play.Play;
import play.libs.MimeTypes;
import play.mvc.Controller;
import play.vfs.VirtualFile;

public class Assets extends Controller {

    public static void dynamicAsset(String template) {
        applyCacheControl();
        renderTemplate("assets/" + template);
    }

    public static void staticAsset(String path, String file) {
        File baseDir = VirtualFile.fromRelativePath(path).getRealFile();
        File f = new File(baseDir, file);
        if (!f.isFile()) {
            notFound();
        }
        applyCacheControl();
        String contentType = MimeTypes.getContentType(f.getName());
        response.setContentTypeIfNotSet(contentType);
        response.direct = f;
    }

    private static void applyCacheControl() {
        String cacheControl = Play.configuration.getProperty("http.cacheControl", "3600");
        response.setHeader("Cache-Control", "max-age=" + cacheControl);
    }
}
