package com.iwave.ext.netapp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

            elem.addNewChild("source-location", sourceLocation);
        }
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
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

            elem.addNewChild("source-location", sourceLocation);
        }

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

    /**
     * delete source and target relation ship
     * 
     * @param sourceLocation
     * @param destLocation
     * @return
     */
    public boolean releaseSnapMirror(String sourceLocation, String destLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-release");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("source-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            result = new HashMap<String, String>();
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to release snapmirror of source-location=" + sourceLocation + "and destLocation=" + destLocation;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;

    }

    /**
     * sync source and target relation ship
     * 
     * @param sourceLocation
     * @param destLocation
     * @return
     */
    public boolean resyncSnapMirror(String sourceLocation, String destLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-resync");

        // Remaining params are optional
        if (sourceLocation != null && !sourceLocation.isEmpty()) {

            elem.addNewChild("source-location", sourceLocation);
        }

        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
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

    /**
     * Breaks a SnapMirror relationship between a source and destination volume of a data protection mirror.
     * 
     * @param destLocation
     * @return
     */
    public boolean breakSnapMirror(String volLocation) {
        Map<String, String> result = null;
        NaElement elem = new NaElement("snapmirror-break");

        // Remaining params are optional
        if (volLocation != null && !volLocation.isEmpty()) {
            elem.addNewChild("destination-location", volLocation);
        }

        try {
            server.invokeElem(elem);

        } catch (Exception e) {
            String msg = "Failed to break snapmirror - destLocation=" + volLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;

    }

    /**
     * Disables future transfers to a SnapMirror destination
     * 
     * @param destLocation
     * @return
     */
    public boolean quiesceSnapMirror(String destLocation) {
        NaElement elem = new NaElement("snapmirror-quiesce");

        // Remaining params are optional
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            server.invokeElem(elem);

        } catch (Exception e) {
            String msg = "Failed to quiesce snapmirror - destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Enables future transfers for a SnapMirror relationship that has been quiesced
     * 
     * @param destLocation
     * @return
     */
    public boolean resumeSnapMirror(String destLocation) {
        NaElement elem = new NaElement("snapmirror-resume");

        // Remaining params are optional
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            server.invokeElem(elem);

        } catch (Exception e) {
            String msg = "Failed to resume snapmirror - destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Deletes a connection specified by connection
     * 
     * @param destLocation
     * @return
     */
    public String deleteSnapMirrorConnection(String destLocation) {
        NaElement resultElem = null;
        NaElement elem = new NaElement("snapmirror-set-connection");

        // Remaining params are optional
        if (destLocation != null && !destLocation.isEmpty()) {
            elem.addNewChild("destination-location", destLocation);
        }

        try {
            resultElem = server.invokeElem(elem);
            return resultElem.getContent();

        } catch (Exception e) {
            String msg = "Failed to resume snapmirror - destLocation=" + destLocation;

            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * get the Snap mirror Status either on or off mode
     * 
     * @return
     */
    public boolean getSnapMirrorStatus() {
        NaElement elem = new NaElement("snapmirror-get-status");

        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(elem).getChildByName("is-available");
            log.info(resultElem);
            String value = resultElem.getContent();
            if (value.equals("false")) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            String msg = "Failed to get snapmirror status";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Enables snap mirror
     * 
     * @return
     */
    public boolean setSnapMirrorOn() {
        NaElement elem = new NaElement("snapmirror-on");
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to get snapmirror on";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    /**
     * Checks if license exists for SnapMirror
     * 
     * @return true if license exists for SnapMirror; false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean checkLicense() {

        boolean licenseValid = false;
        NaElement elem = new NaElement("license-v2-list-info");
        try {
            List<NaElement> licences = server.invokeElem(elem).getChildByName("licenses").getChildren();
            if (licences != null) {
                for (Iterator<NaElement> iterator = licences.iterator(); iterator.hasNext();) {
                    NaElement licenceElement = iterator.next();
                    NaElement packageElement = licenceElement.getChildByName("package");
                    NaElement typeElement = licenceElement.getChildByName("type");
                    if ("SnapMirror".equals(packageElement.getContent())
                            && "license".equals(typeElement.getContent())) {
                        licenseValid = true;
                    }
                }
            }

        } catch (Exception e) {
            String msg = "Failed to check snapmirror license.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return licenseValid;
    }

}
