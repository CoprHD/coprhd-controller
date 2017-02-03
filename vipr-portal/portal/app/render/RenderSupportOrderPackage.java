/*
 * Copyright (c) 2017 EMC Corporation
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
import util.support.SupportOrderPackageCreator;

/**
 * Renders a support order package ZIP file while it is being constructed.
 */
public class RenderSupportOrderPackage extends Result {
    private SupportOrderPackageCreator supportOrderPackage;

    public static void renderSupportAuditPackage(SupportOrderPackageCreator supportAuditPackage) {
        throw new RenderSupportOrderPackage(supportAuditPackage);
    }

    public RenderSupportOrderPackage(SupportOrderPackageCreator supportAuditPackage) {
        this.supportOrderPackage = supportAuditPackage;
    }

    private String getFilename() {
        String timestamp = SupportOrderPackageCreator.formatTimestamp(Calendar.getInstance());
        return String.format("orders-%s.zip", timestamp);
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
        supportOrderPackage.createJob(out).now();
        return in;
    }
}
