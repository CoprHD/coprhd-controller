package com.iwave.ext.netappc;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

import com.iwave.ext.netappc.model.SnapmirrorInfo;
import com.iwave.ext.netappc.model.SnapmirrorInfoResp;
import com.iwave.ext.netappc.model.SnapmirrorResp;

public class SnapMirror {
    private Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;

    public SnapMirror(NaServer server, String volumeName) {
        this.server = server;
        this.name = volumeName;
    }

    public SnapmirrorInfoResp createSnapMirror(SnapmirrorInfo snapMirrorInfo) {

        SnapmirrorInfoResp snapMirrorInfoResp = new SnapmirrorInfoResp();
        return snapMirrorInfoResp;
    }

    public SnapmirrorResp initialiseSnapMirror(SnapmirrorInfo snapMirrorInfo) {

        SnapmirrorResp snapMirrorResp = null;

        NaElement elem = new NaElement("snapmirror-initialize");

        // destination attributes
        prepSourceReq(elem, snapMirrorInfo);

        // source attributes
        prepDestReq(elem, snapMirrorInfo);

        try {
            NaElement results = server.invokeElem(elem);
            snapMirrorResp = parseRespJobResult(results);

        } catch (Exception e) {
            String msg = "Failed to Initialize SnapMirror: " + snapMirrorInfo.getDestinationVolume();
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return snapMirrorResp;
    }

    public SnapmirrorResp resyncSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    public SnapmirrorResp restoreSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    public SnapmirrorResp breakAsyncSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = null;

        NaElement elem = new NaElement("snapmirror-break");

        // destination attributes
        prepSourceReq(elem, snapMirrorInfo);

        // source attributes
        prepDestReq(elem, snapMirrorInfo);

        try {
            NaElement results = server.invokeElem(elem);
            snapMirrorResp = parseRespJobResult(results);

        } catch (Exception e) {
            String msg = "Failed to Break SnapMirror: " + snapMirrorInfo.getDestinationVolume();
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return snapMirrorResp;
    }

    public boolean quiesceSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        return true;
    }

    public SnapmirrorResp destroyAsyncSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = null;

        NaElement elem = new NaElement("snapmirror-destroy-async");

        // destination attributes
        prepSourceReq(elem, snapMirrorInfo);

        // source attributes
        prepDestReq(elem, snapMirrorInfo);

        try {
            NaElement results = server.invokeElem(elem);
            snapMirrorResp = parseRespJobResult(results);

        } catch (Exception e) {
            String msg = "Failed to Initialize SnapMirror: " + snapMirrorInfo.getDestinationVolume();
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return snapMirrorResp;
    }

    public boolean resumesnapMirror(SnapmirrorInfo snapMirrorInfo) {

        return true;
    }

    public SnapmirrorResp abortAsyncSnapMirror(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    // helper function

    private void prepSourceReq(NaElement elem, SnapmirrorInfo snapMirrorInfo) {
        if (elem != null && snapMirrorInfo.getSourceCluster() != null) {
            elem.addNewChild("source-cluster", snapMirrorInfo.getSourceCluster());
        }

        if (elem != null && snapMirrorInfo.getSourceLocation() != null) {
            elem.addNewChild("source-location", snapMirrorInfo.getSourceLocation());
        }

        if (elem != null && snapMirrorInfo.getSourceVolume() != null) {
            elem.addNewChild("source-volume", snapMirrorInfo.getSourceVolume());
        }

        if (elem != null && snapMirrorInfo.getSourceVserver() != null) {
            elem.addNewChild("source-vserver", snapMirrorInfo.getSourceVserver());
        }
    }

    private void prepDestReq(NaElement elem, SnapmirrorInfo snapMirrorInfo) {
        if (elem != null && snapMirrorInfo.getDestinationCluster() != null) {
            elem.addNewChild("destination-cluster", snapMirrorInfo.getDestinationCluster());
        }

        if (elem != null && snapMirrorInfo.getDestinationVolume() != null) {
            elem.addNewChild("destination-volume", snapMirrorInfo.getDestinationVolume());
        }

        if (elem != null && snapMirrorInfo.getDestinationLocation() != null) {
            elem.addNewChild("destination-location", snapMirrorInfo.getDestinationLocation());
        }

        if (elem != null && snapMirrorInfo.getDestinationVserver() != null) {
            elem.addNewChild("destination-vserver", snapMirrorInfo.getDestinationVserver());
        }
    }

    private SnapmirrorResp parseRespJobResult(NaElement results) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();
        // set result status
        String status = results.getChildContent("result-status");
        snapMirrorResp.setResultStatus(status);
        // set job
        Integer jobId = results.getChildIntValue("result-jobid", -1);
        snapMirrorResp.setResultJobid(jobId);

        if (status.equals("failed")) {
            // set messageId
            Integer errorCode = results.getChildIntValue("result-error-code", -1);
            snapMirrorResp.setResultErrorCode(errorCode);

            // set result message
            String message = results.getChildContent("result-error-message");

            if (message != null && !message.isEmpty()) {
                snapMirrorResp.setResultErrorMessage(message);
            }
        }
        return snapMirrorResp;
    }

}
