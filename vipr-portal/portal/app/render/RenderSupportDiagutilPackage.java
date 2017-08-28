/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package render;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DiagutilJobStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.exceptions.UnexpectedException;
import play.mvc.Http.Response;
import play.mvc.Http.Request;
import play.mvc.results.Result;
import plugin.StorageOsPlugin;
import util.BourneUtil;
import util.support.SupportDiagutilCreator;
import util.support.SupportPackageCreator;

import java.io.*;
import java.util.Calendar;

public class RenderSupportDiagutilPackage extends Result {
    private static final Logger log = LoggerFactory.getLogger(RenderSupportDiagutilPackage.class);
    private String fileName;
    private SupportDiagutilCreator supportDiagutilCreator;

    public RenderSupportDiagutilPackage(SupportDiagutilCreator supportDiagutilCreator, String fileName) {
        this.supportDiagutilCreator = supportDiagutilCreator;
        this.fileName = fileName;
    }

    public static void renderSupportDiagutilPackage(SupportDiagutilCreator supportDiagutilCreator, String fileName) {
        throw  new RenderSupportDiagutilPackage(supportDiagutilCreator, fileName);
    }

    private String getFilename() {
        return fileName;
    }

    private String getContentType() {
        return "application/zip";
    }

    private String getContentDisposition() {
        return String.format("attachment; filename=%s", getFilename());
    }

    @Override
    public void apply(Request request, Response response) {
        try{
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
        supportDiagutilCreator.createJob(out).now();
        return in;
    }
}
