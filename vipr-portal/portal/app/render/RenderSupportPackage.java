/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package render;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Calendar;

import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;
import util.support.SupportPackageCreator;

/**
 * Renders a support package ZIP file while it is being constructed.
 * 
 * @author jonnymiller
 */
public class RenderSupportPackage extends Result {
    private SupportPackageCreator supportPackage;

    public static void renderSupportPackage(SupportPackageCreator supportPackage) {
        throw new RenderSupportPackage(supportPackage);
    }

    public RenderSupportPackage(SupportPackageCreator supportPackage) {
        this.supportPackage = supportPackage;
    }

    private String getFilename() {
        String timestamp = SupportPackageCreator.formatTimestamp(Calendar.getInstance());
        return String.format("logs-%s.zip", timestamp);
    }

    private String getContentType() {
        return "application/zip";
    }

    private String getContentDisposition() {
        return String.format("attachment; filename=%s", getFilename());
    }

    @Override
    public void apply(Request request, Response response) {
        try {
            response.contentType = getContentType();
            response.setHeader("Content-Disposition", getContentDisposition());
            response.direct = getContent();
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    private InputStream getContent() throws IOException {
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(out);
        supportPackage.createJob(out).now();
        return in;
    }
}
