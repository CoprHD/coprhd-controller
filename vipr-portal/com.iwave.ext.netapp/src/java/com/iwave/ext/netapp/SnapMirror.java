package com.iwave.ext.netapp;

import java.util.HashMap;
import java.util.Map;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

public class SnapMirror {

    private Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;

    public SnapMirror(NaServer server, String name)
    {
        this.name = name;
        this.server = server;
    }

    public boolean createSnapMirror(String sourceLocation, String destLocation) {
        NaElement elem = new NaElement("snapmirror-create");

        if (sourceLocation != null && !sourceLocation.isEmpty()) {
            elem.addNewChild("source-location", sourceLocation);
        }
        // Remaining params are optional
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to update snapmirror of source-location=" + sourceLocation +
                    "and destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    public boolean initializeSnapMirror(String sourceLocation, String destLocation) {
        NaElement elem = new NaElement("snapmirror-initialize");

        if (sourceLocation != null && !sourceLocation.isEmpty()) {
            elem.addNewChild("source-location", sourceLocation);
        }
        // Remaining params are optional
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to update snapmirror of source-location=" + sourceLocation +
                    "and destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    // snapmirror-set-schedule
    public boolean setSnapMirrorSchedule(String type, String scheduleTime, String sourceLocation, String destLocation) {
        NaElement elem = new NaElement("snapmirror-set-schedule");
        // Remaining params are optional
        if (type != null && !type.isEmpty()) {
            elem.addNewChild(type, scheduleTime);
        }
        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("destination-location", sourceLocation);
        }
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("source-location", destLocation);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to set schedule snapmirror of schedule Time" + scheduleTime;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    // snapmirror-set-schedule

    /**
     * Delete the schedule for a given destination
     * 
     * @param destinationLocation
     * @return
     */
    public boolean deleteSnapMirrorSchedule(String destinationLocation) {
        NaElement elem = new NaElement("snapmirror-delete-schedule");
        // Remaining params are optional

        // Remaining params are optional
        if (destinationLocation != null && !destinationLocation.isEmpty()) {
            elem.addNewChild("destination-location", destinationLocation);
        }
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete snapmirror: " + destinationLocation;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    public boolean updateSnapMirror(String sourceLocation, String destLocation) {
        NaElement elem = new NaElement("snapmirror-update");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("destination-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("source-location", destLocation);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to update snapmirror of source-location=" + sourceLocation +
                    "and destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /* delete source and target relation ship */
    public boolean deleteSnapMirror(String sourceLocation, String destLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-destroy-async");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("destination-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("source-location", destLocation);
        }

        try {
            result = new HashMap<String, String>();
            NaElement resultElem = server.invokeElem(elem);
            for (Map.Entry entry : result.entrySet()) {

            }

        } catch (Exception e) {
            String msg = "Failed to delete snapmirror of source-location=" + sourceLocation + "and destLocation=" + destLocation;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;

    }

    /* sync source and target relation ship */
    public boolean resyncSnapMirror(String sourceLocation, String destLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-resync");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("destination-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("source-location", destLocation);
        }

        try {
            result = new HashMap<String, String>();
            NaElement resultElem = server.invokeElem(elem);
            for (Map.Entry entry : result.entrySet()) {

            }

        } catch (Exception e) {
            String msg = "Failed to resync snapmirror of source-location=" + sourceLocation +
                    "and destLocation=" + destLocation;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;

    }

    /* sync source and target relation ship */
    public boolean breakSnapMirror(String sourceLocation, String destLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-break");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("destination-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("source-location", destLocation);
        }

        try {
            result = new HashMap<String, String>();
            NaElement resultElem = server.invokeElem(elem);
            for (Map.Entry entry : result.entrySet()) {

            }

        } catch (Exception e) {
            String msg = "Failed to break snapmirror of source-location=" + sourceLocation +
                    "and destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;

    }
}
