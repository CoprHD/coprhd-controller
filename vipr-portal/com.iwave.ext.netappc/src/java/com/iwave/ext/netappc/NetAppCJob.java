/*
 * Copyright (c) 2012-2016 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

import com.iwave.ext.netappc.model.SnapmirrorCronScheduleInfo;

/**
 * 
 * Cron Schedule Management operations for Snapmirror
 *
 */
public class NetAppCJob {

    private Logger log = Logger.getLogger(getClass());

    private String name = "";
    private NaServer server = null;

    public NetAppCJob(NaServer server, String jobName) {
        this.server = server;
        this.name = jobName;
    }

    // operation schedule
    /**
     * Create a new cron job schedule entry.
     * 
     * @param fsRpoValue
     * @param fsRpoType
     * @param name
     * @return
     */
    public SnapmirrorCronScheduleInfo createCronSchedule(String fsRpoValue, String fsRpoType, String name) {
        SnapmirrorCronScheduleInfo cronScheduleInfo = null;

        NaElement elem = new NaElement("job-schedule-cron-create");

        // The name of the job schedule
        elem.addNewChild("job-schedule-name", name);

        // If set to true, returns the job-schedule-cron on successful creation
        elem.addNewChild("return-record", "true");

        // schedule time of job
        prepareScheduleTime(fsRpoValue, fsRpoType, elem);

        try {
            NaElement results = server.invokeElem(elem);

            cronScheduleInfo = parseSnapMirrorCronSchedule(results);

        } catch (Exception e) {
            String msg = "Failed to create Cron Schedule with a : " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }

        return cronScheduleInfo;
    }

    /**
     * Modify an existing cron job schedule entry.
     * 
     * @param fsRpoValue
     * @param fsRpoType
     * @param name
     * @return
     */
    public SnapmirrorCronScheduleInfo modifyCronSchedule(String fsRpoValue, String fsRpoType, String name) {
        SnapmirrorCronScheduleInfo cronScheduleInfo = null;
        NaElement elem = new NaElement("job-schedule-cron-create");

        // The name of the job schedule
        elem.addNewChild("job-schedule-name", name);

        // If set to true, returns the job-schedule-cron on successful creation
        elem.addNewChild("return-record", "true");

        // schedule time of job
        prepareScheduleTime(fsRpoValue, fsRpoType, elem);
        try {
            NaElement results = server.invokeElem(elem);

            cronScheduleInfo = parseSnapMirrorCronSchedule(results);

        } catch (Exception e) {
            String msg = "Failed to create Cron Schedule Jobname : " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return cronScheduleInfo;
    }

    /**
     * Get a single cron job schedule entry.
     * 
     * @param name
     * @return
     */
    public SnapmirrorCronScheduleInfo getCronSchedule(String name) {
        SnapmirrorCronScheduleInfo cronScheduleInfo = null;
        NaElement elem = new NaElement("job-schedule-cron-create");

        // The name of the job schedule
        elem.addNewChild("job-schedule-name", name);

        try {
            NaElement results = server.invokeElem(elem);

            cronScheduleInfo = parseSnapMirrorCronSchedule(results);

        } catch (Exception e) {
            String msg = "Failed to create Cron Schedule Jobname : " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return cronScheduleInfo;
    }

    /**
     * Delete a single cron job schedule entry. The entry must not be in use.
     * 
     * @param jobName
     * @return
     */
    public boolean deleteCronSchedule(String jobName) {
        NaElement elem = new NaElement("job-schedule-cron-create");
        elem.addNewChild("job-schedule-name", jobName);

        try {
            NaElement results = server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete Cron Schedule Jobname : " + name;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
        return true;
    }

    private SnapmirrorCronScheduleInfo parseSnapMirrorCronSchedule(NaElement resultElem) {

        SnapmirrorCronScheduleInfo cronScheduleInfo = new SnapmirrorCronScheduleInfo();

        NaElement elemScheduleCron = null;
        if (resultElem != null) {
            NaElement results = resultElem.getChildByName("result").getChildByName("job-schedule-cron-info");
            List<Integer> scheduleList = null;
            // job-schedule-cron-month
            elemScheduleCron = results.getChildByName("job-schedule-cron-month");
            if (elemScheduleCron != null) {
                scheduleList = processScheduleElem(elemScheduleCron);
                if (scheduleList != null && !scheduleList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronMonth(scheduleList);
                }
            }

            // job-schedule-cron-day
            elemScheduleCron = results.getChildByName("job-schedule-cron-day");
            if (elemScheduleCron != null) {
                scheduleList = processScheduleElem(elemScheduleCron);
                if (scheduleList != null && !scheduleList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronDay(scheduleList);
                }
            }

            // job-schedule-cron-hour
            elemScheduleCron = results.getChildByName("job-schedule-cron-hour");
            if (elemScheduleCron != null) {
                scheduleList = processScheduleElem(elemScheduleCron);
                if (scheduleList != null && !scheduleList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronHour(scheduleList);
                }
            }

            // job-schedule-cron-minute
            elemScheduleCron = results.getChildByName("job-schedule-cron-minute");
            if (elemScheduleCron != null) {
                scheduleList = processScheduleElem(elemScheduleCron);
                if (scheduleList != null && !scheduleList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronMinute(scheduleList);
                }
            }

            NaElement cronName = results.getChildByName("job-schedule-name");

            if (cronName != null) {
                cronScheduleInfo.setJobScheduleName(cronName.getContent());
            }

        }

        return cronScheduleInfo;
    }

    List<Integer> processScheduleElem(NaElement elems) {
        List<Integer> dataList = new ArrayList<Integer>();
        if (elems != null) {
            for (NaElement dataElem : (List<NaElement>) elems.getChildren()) {
                String dataStr = dataElem.getContent();
                if (dataStr != null && !dataStr.isEmpty()) {
                    Integer dataInt = Integer.getInteger(dataStr);
                    dataList.add(dataInt);
                }
            }
        }
        return dataList;
    }

    void prepareScheduleTime(String fsRpoValue, String fsRpoType, NaElement elemRoot) {
        NaElement elemSchedule = null;
        switch (fsRpoType.toUpperCase()) {
            case "MINUTES":
                elemSchedule = new NaElement("job-schedule-cron-minute");
                elemSchedule.addNewChild("cron-minute", fsRpoValue);
                elemRoot.addChildElem(elemSchedule);
                break;
            case "HOURS":
                elemSchedule = new NaElement("job-schedule-cron-hour");
                elemSchedule.addNewChild("cron-hour", fsRpoValue);
                elemRoot.addChildElem(elemSchedule);

                elemSchedule = new NaElement("job-schedule-cron-minute");
                elemSchedule.addNewChild("cron-minute", "0");
                elemRoot.addChildElem(elemSchedule);
                break;
            case "DAYS":
                elemSchedule = new NaElement("job-schedule-cron-day");
                elemSchedule.addNewChild("cron-day", fsRpoValue);
                elemRoot.addChildElem(elemSchedule);

                elemSchedule = new NaElement("job-schedule-cron-hour");
                elemSchedule.addNewChild("cron-hour", fsRpoValue);
                elemRoot.addChildElem(elemSchedule);

                elemSchedule = new NaElement("job-schedule-cron-minute");
                elemSchedule.addNewChild("cron-minute", "0");
                elemRoot.addChildElem(elemSchedule);

                break;
            case "MONTH":
                elemSchedule = new NaElement("job-schedule-cron-month");
                elemSchedule.addNewChild("cron-month", fsRpoValue);
                elemRoot.addChildElem(elemSchedule);
                break;
        }
    }

}
