/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.List;

import netapp.manage.NaAPIFailedException;
import netapp.manage.NaElement;
import netapp.manage.NaErrno;
import netapp.manage.NaServer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.netapp.model.Quota;
import com.iwave.ext.netapp.model.QuotaUser;

public class QuotaCommands {

    public enum QuotaStatus {
        ON,
        OFF,
        RESIZING,
        INITIALIZING,
        SHUTTING_DOWN
    }

    private Logger log = Logger.getLogger(getClass());

    private NaServer server = null;

    public QuotaCommands(NaServer server) {
        this.server = server;
    }

    public List<Quota> quotaReport(String path, String volume) {
        NaElement elem = new NaElement("quota-report");
        if (StringUtils.isNotBlank(path)) {
            elem.addNewChild("path", path);
        }
        if (StringUtils.isNotBlank(volume)) {
            elem.addNewChild("volume", volume);
        }

        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }

        List<Quota> quotas = Lists.newArrayList();
        for (NaElement e : (List<NaElement>) resultElem.getChildren()) {
            if ("error".equalsIgnoreCase(e.getName())) {
                String errno = e.getChildContent("errno");
                String reason = e.getChildContent("reason");
                String message = String.format("%s: %s", errno, reason);
                throw new NetAppException(message);
            }
            else if ("quotas".equalsIgnoreCase(e.getName())) {
                for (NaElement quotaElem : (List<NaElement>) e.getChildren()) {
                    Quota quota = new Quota();
                    quota.setQtree(quotaElem.getChildContent("tree"));
                    quota.setDiskLimit(quotaElem.getChildContent("disk-limit"));
                    quota.setDiskUsed(quotaElem.getChildContent("disk-used"));
                    quota.setFileLimit(quotaElem.getChildContent("file-limit"));
                    quota.setFilesUsed(quotaElem.getChildContent("files-used"));
                    quota.setQuotaTarget(quotaElem.getChildContent("quota-target"));
                    quota.setQuotaType(quotaElem.getChildContent("quota-type"));
                    quota.setSoftDiskLimit(quotaElem.getChildContent("soft-disk-limit"));
                    quota.setSoftFileLimit(quotaElem.getChildContent("soft-file-limit"));
                    quota.setThreshold(quotaElem.getChildContent("threshold"));
                    quota.setVfiler(quotaElem.getChildContent("vfiler"));
                    quota.setVolume(quotaElem.getChildContent("volume"));

                    // users
                    NaElement quotaUsersElem = (NaElement) quotaElem
                            .getChildByName("quota-users");
                    if (quotaUsersElem != null) {
                        for (NaElement quotaUserElem : (List<NaElement>) quotaUsersElem.getChildren()) {
                            QuotaUser quotaUser = new QuotaUser();
                            quotaUser.setUserId(quotaUserElem.getChildContent("quota-user-id"));
                            quotaUser.setUserName(quotaUserElem.getChildContent("quota-user-name"));
                            quotaUser.setUserType(quotaUserElem.getChildContent("quota-user-type"));
                            quota.getQuotaUsers().add(quotaUser);
                        }
                    }

                    quotas.add(quota);
                }
            }
        }
        return quotas;
    }

    public Quota getTreeQuota(String volume, String path) {
        return getQuota(volume, path, "tree", "");
    }

    public Quota getQuota(String volume, String quotaTarget, String quotaType, String qtree) {
        NaElement elem = new NaElement("quota-get-entry");
        elem.addNewChild("volume", volume);
        elem.addNewChild("quota-target", quotaTarget);
        elem.addNewChild("quota-type", quotaType);
        elem.addNewChild("qtree", qtree);
        try {
            NaElement result = server.invokeElem(elem);
            Quota quota = new Quota();
            quota.setVolume(volume);
            quota.setQuotaTarget(quotaTarget);
            quota.setQuotaType(quotaType);
            quota.setQtree(qtree);
            quota.setDiskLimit(result.getChildContent("disk-limit"));
            quota.setFileLimit(result.getChildContent("file-limit"));
            quota.setSoftDiskLimit(result.getChildContent("soft-disk-limit"));
            quota.setSoftFileLimit(result.getChildContent("soft-file-limit"));
            quota.setThreshold(result.getChildContent("threshold"));
            return quota;
        } catch (NaAPIFailedException e) {
            if (e.getErrno() == NaErrno.EQUOTADOESNOTEXIST) {
                return null;
            }
            throw createError(elem, e);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }
    
    public List<Quota> getTreeQuotas() {
        return getQuotas();
    }

    public List<Quota> getQuotas() {
        NaElement elem = new NaElement("quota-list-entries-iter");
        try {
            List<Quota> quotas = Lists.newArrayList();
            NaElement result = server.invokeElem(elem);
            for (NaElement quotaElem : (List<NaElement>) result.getChildByName("attributes-list").getChildren()) {
                Quota quota = new Quota();
                quota.setVolume(quotaElem.getChildContent("volume"));
                quota.setQuotaTarget(quotaElem.getChildContent("quota-target"));
                quota.setQuotaType(quotaElem.getChildContent("quota-type"));
                quota.setQtree(quotaElem.getChildContent("tree"));
                quota.setDiskLimit(quotaElem.getChildContent("disk-limit"));
                quota.setFileLimit(quotaElem.getChildContent("file-limit"));
                quota.setSoftDiskLimit(quotaElem.getChildContent("soft-disk-limit"));
                quota.setSoftFileLimit(quotaElem.getChildContent("soft-file-limit"));
                quota.setThreshold(quotaElem.getChildContent("threshold"));
                quotas.add(quota);
            }
            return quotas;
        } catch (NaAPIFailedException e) {
            if (e.getErrno() == NaErrno.EQUOTADOESNOTEXIST) {
                return null;
            }
            throw createError(elem, e);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    public void addDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        addDiskLimitQuota(volume, path, "tree", "", diskLimitInKB, thresholdInKB);
    }

    public void modifyDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        modifyDiskLimitQuota(volume, path, "tree", "", diskLimitInKB, thresholdInKB);
    }

    public void addDiskLimitQuota(String volume, String quotaTarget, String quotaType,
            String qtree, long diskLimitInKB, long thresholdInKB) {
        NaElement elem = new NaElement("quota-add-entry");
        elem.addNewChild("volume", volume);
        elem.addNewChild("quota-target", quotaTarget);
        elem.addNewChild("quota-type", quotaType);
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("disk-limit", String.valueOf(diskLimitInKB));
        if (thresholdInKB > 0) {
            elem.addNewChild("threshold", String.valueOf(thresholdInKB));
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }

    }

    public void setDiskLimitTreeQuota(String volume, String path, long diskLimitInKB,
            long thresholdInKB) {
        setDiskLimitQuota(volume, path, "tree", "", diskLimitInKB, thresholdInKB);
    }

    public void setDiskLimitQuota(String volume, String quotaTarget, String quotaType,
            String qtree, long diskLimitInKB, long thresholdInKB) {
        NaElement elem = new NaElement("quota-set-entry");
        elem.addNewChild("volume", volume);
        elem.addNewChild("quota-target", quotaTarget);
        elem.addNewChild("quota-type", quotaType);
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("disk-limit", String.valueOf(diskLimitInKB));
        if (thresholdInKB > 0) {
            elem.addNewChild("threshold", String.valueOf(thresholdInKB));
        }
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    public void modifyDiskLimitQuota(String volume, String quotaTarget, String quotaType,
            String qtree, long diskLimitInKB, long thresholdInKB) {
        NaElement elem = new NaElement("quota-modify-entry");
        elem.addNewChild("volume", volume);
        elem.addNewChild("quota-target", quotaTarget);
        elem.addNewChild("quota-type", quotaType);
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("disk-limit", String.valueOf(diskLimitInKB));
        if (thresholdInKB > 0) {
            elem.addNewChild("threshold", String.valueOf(thresholdInKB));
        }
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    public void deleteTreeQuota(String volume, String path) {
        deleteQuota(volume, path, "tree", "");
    }

    public void deleteQuota(String volume, String quotaTarget, String quotaType, String qtree) {
        NaElement elem = new NaElement("quota-delete-entry");
        elem.addNewChild("volume", volume);
        elem.addNewChild("quota-target", quotaTarget);
        elem.addNewChild("quota-type", quotaType);
        elem.addNewChild("qtree", qtree);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    /**
     * Starts to turn quotas on for a volume. A successful return from this API does not mean that quotas are on,
     * merely that an attempt to start it has been triggered
     */
    public void turnQuotaOn(String volume) {
        NaElement elem = new NaElement("quota-on");
        elem.addNewChild("volume", volume);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    /**
     * Turns the quota subsystem off for a volume
     */
    public void turnQuotaOff(String volume) {
        NaElement elem = new NaElement("quota-off");
        elem.addNewChild("volume", volume);
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            throw createError(elem, e);
        }
    }

    public void startQuotaResize(String volumeName) {
        NaElement elem = new NaElement("quota-resize");
        elem.addNewChild("volume", volumeName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to start quota resizing on volume: " + volumeName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Returns the current status of Quotas on the specified volume
     */
    public QuotaStatus getQuotaStatus(String volumeName) {
        NaElement elem = new NaElement("quota-status");
        elem.addNewChild("volume", volumeName);

        try {
            NaElement result = server.invokeElem(elem);
            String status = result.getChildContent("status");

            if (status.equals("on")) {
                return QuotaStatus.ON;
            } else if (status.equals("off")) {
                return QuotaStatus.OFF;
            } else if (status.equals("resizing")) {
                return QuotaStatus.RESIZING;
            } else if (status.equals("shutting down")) {
                return QuotaStatus.SHUTTING_DOWN;
            } else {
                throw new NetAppException("Unknown quota status value " + status);
            }

        } catch (Exception e) {
            String msg = "Failed to get quota status: " + volumeName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Starts a Resize Quota operation on the specified volume.
     * 
     * This only starts the resize operation and returns immediately, {@link #getQuotaStatus(String)} should be used
     * to find out when the current status of the operation
     */
    public void startResize(String volumeName) {
        NaElement elem = new NaElement("quota-resize");
        elem.addNewChild("volume", volumeName);

        try {
            server.invokeElem(elem);

        } catch (Exception e) {
            String msg = "Failed to start quota resizing on volume: " + volumeName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    protected NetAppException createError(NaElement elem, Exception e) {
        String message = "Failed to run " + elem.getName();
        log.error(message, e);
        return new NetAppException(message, e);
    }
}
