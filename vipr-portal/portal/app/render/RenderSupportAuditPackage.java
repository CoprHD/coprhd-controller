/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package render;

import play.exceptions.UnexpectedException;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;
import util.support.SupportAuditPackageCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Calendar;

/**
 * Renders a support audit logs package ZIP file while it is being constructed.
 */
public class RenderSupportAuditPackage extends Result {
    private SupportAuditPackageCreator supportAuditPackage;

    public static void renderSupportAuditPackage(SupportAuditPackageCreator supportAuditPackage) {
        throw new RenderSupportAuditPackage(supportAuditPackage);
    }

    public RenderSupportAuditPackage(SupportAuditPackageCreator supportAuditPackage) {
        this.supportAuditPackage = supportAuditPackage;
    }

    private String getFilename() {
        String timestamp = SupportAuditPackageCreator.formatTimestamp(Calendar.getInstance());
        return String.format("auditlogs-%s.zip", timestamp);
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
        supportAuditPackage.createJob(out).now();
        return in;
    }
}
