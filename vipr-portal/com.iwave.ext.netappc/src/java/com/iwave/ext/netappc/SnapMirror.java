package com.iwave.ext.netappc;

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

    public SnapmirrorResp SnapmirrorInitialize(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    public SnapmirrorResp SnapmirrorResync(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    public SnapmirrorResp SnapmirrorRestore(SnapmirrorInfo snapMirrorInfo) {
        SnapmirrorResp snapMirrorResp = new SnapmirrorResp();

        return snapMirrorResp;
    }

    public boolean SnapmirrorBreak(SnapmirrorInfo snapMirrorInfo) {

        return true;
    }

    public boolean SnapmirrorQuiesce(SnapmirrorInfo snapMirrorInfo) {
        return true;
    }

    public boolean SnapmirrorDestroy(SnapmirrorInfo snapMirrorInfo) {

        return true;
    }

    public boolean SnapmirrorResume(SnapmirrorInfo snapMirrorInfo) {

        return true;
    }

    public boolean SnapmirrorAbort(SnapmirrorInfo snapMirrorInfo) {
        return true;
    }
}
