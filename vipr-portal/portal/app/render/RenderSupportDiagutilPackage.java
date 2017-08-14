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
    private String nodeId;
    private String fileName;
    private SupportDiagutilCreator supportDiagutilCreator;
    public static void renderSupportDiagutilPackage(String nodeId, String fileName) {
        throw new RenderSupportDiagutilPackage(nodeId, fileName);
    }

    public RenderSupportDiagutilPackage(String nodeId, String fileName) {
        this.nodeId = nodeId;
        this.fileName = fileName;
    }

    public RenderSupportDiagutilPackage(SupportDiagutilCreator supportDiagutilCreator) {
        this.supportDiagutilCreator = supportDiagutilCreator;
    }

    public static void renderSupportDiagutilPackage(SupportDiagutilCreator supportDiagutilCreator) {
        throw  new RenderSupportDiagutilPackage(supportDiagutilCreator);
    }

    private String getFilename() {
        return String.format("%s.zip", fileName);
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

/*    private void writeData(OutputStream out) {
        InputStream in = BourneUtil.getSysClient().diagutil().getAsStream(nodeId, fileName);
        try {
            byte[] buffer = new byte[102400];
            int n;
            while((n=in.read(buffer)) != -1) {
                log.info("read size is {}",n);
                out.write(buffer, 0, n);
            }
            //IOUtils.copyLarge(in, out);
            updataDiagutilStatusByCoordinator(DiagutilInfo.DiagutilStatus.COMPLETE);
        }catch (IOException e ){
            log.error("read/write error",e);
            updataDiagutilStatusByCoordinator(DiagutilInfo.DiagutilStatus.DOWNLOAD_ERROR);
        }finally {
            log.info("finally here");
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
    }*/
}
