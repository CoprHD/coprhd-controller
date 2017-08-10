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
import play.exceptions.UnexpectedException;
import play.mvc.Http.Response;
import play.mvc.Http.Request;
import play.mvc.results.Result;
import plugin.StorageOsPlugin;
import util.BourneUtil;
import util.support.SupportPackageCreator;

import java.io.*;
import java.util.Calendar;

public class RenderSupportDiagutilPackage extends Result {
    private String nodeId;
    private String fileName;
    public static void renderSupportDiagutilPackage(String nodeId, String fileName) {
        throw new RenderSupportDiagutilPackage(nodeId, fileName);
    }

    public RenderSupportDiagutilPackage(String nodeId, String fileName) {
        this.nodeId = nodeId;
        this.fileName = fileName;
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
        writeData(out);
        return in;
    }

    private void writeData(OutputStream out) {
        InputStream in = BourneUtil.getSysClient().diagutil().getAsStream(nodeId, fileName);
        try {
            IOUtils.copyLarge(in, out);
            updataDiagutilStatusByCoordinator(DiagutilInfo.DiagutilStatus.COMPLETE);
        }catch (IOException e ){
            updataDiagutilStatusByCoordinator(DiagutilInfo.DiagutilStatus.DOWNLOAD_ERROR);
        }finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }

    }

    private void updataDiagutilStatusByCoordinator (DiagutilInfo.DiagutilStatus status) {
        if (StorageOsPlugin.isEnabled()) {
            CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
            DiagutilJobStatus jobStatus = coordinatorClient.queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class);
            jobStatus.setStatus(status);
            coordinatorClient.persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, jobStatus);
        }
    }
}
