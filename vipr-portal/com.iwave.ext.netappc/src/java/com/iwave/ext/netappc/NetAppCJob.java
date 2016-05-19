package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.log4j.Logger;

import com.iwave.ext.netappc.model.SnapmirrorCronScheduleInfo;

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
        NaElement elemSchedule = prepareScheduleTime(fsRpoValue, fsRpoType);
        elem.addChildElem(elemSchedule);

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
        NaElement elemSchedule = prepareScheduleTime(fsRpoValue, fsRpoType);
        elem.addChildElem(elemSchedule);

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

    private SnapmirrorCronScheduleInfo parseSnapMirrorCronSchedule(NaElement results) {
        SnapmirrorCronScheduleInfo cronScheduleInfo = new SnapmirrorCronScheduleInfo();

        if (results != null) {

            // job-schedule-cron-month
            NaElement elemScheduleCronMonths = results.getChildByName("job-schedule-cron-month");
            if (elemScheduleCronMonths != null) {
                List<Integer> monthList = new ArrayList<Integer>();
                for (NaElement elemMonth : (List<NaElement>) elemScheduleCronMonths.getChildren()) {
                    String monthString = elemMonth.getContent();
                    if (monthString != null && !monthString.isEmpty()) {
                        Integer monthInteger = Integer.getInteger(monthString);
                        monthList.add(monthInteger);
                    }
                }
                if (!monthList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronDay(monthList);
                }
            }

            // job-schedule-cron-day
            NaElement elemScheduleCronDays = results.getChildByName("job-schedule-cron-day");
            if (elemScheduleCronDays != null) {
                List<Integer> dayList = new ArrayList<Integer>();
                for (NaElement elemDay : (List<NaElement>) elemScheduleCronDays.getChildren()) {
                    String dayString = elemDay.getContent();
                    if (dayString != null && !dayString.isEmpty()) {
                        Integer dayInteger = Integer.getInteger(dayString);
                        dayList.add(dayInteger);
                    }
                }
                if (!dayList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronDay(dayList);
                }
            }

            // job-schedule-cron-hour
            NaElement elemScheduleCronHours = results.getChildByName("job-schedule-cron-hour");
            if (elemScheduleCronHours != null) {
                List<Integer> hourList = new ArrayList<Integer>();
                for (NaElement elemHour : (List<NaElement>) elemScheduleCronHours.getChildren()) {
                    String hourString = elemHour.getContent();
                    if (hourString != null && !hourString.isEmpty()) {
                        Integer hourInteger = Integer.getInteger(hourString);
                        hourList.add(hourInteger);
                    }
                }
                if (!hourList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronHour(hourList);
                }
            }

            // job-schedule-cron-minute
            NaElement elemScheduleCronMinute = results.getChildByName("job-schedule-cron-minute");
            if (elemScheduleCronMinute != null) {
                List<Integer> minuteList = new ArrayList<Integer>();
                for (NaElement elemMinute : (List<NaElement>) elemScheduleCronHours.getChildren()) {
                    String minuteString = elemMinute.getContent();
                    if (minuteString != null && !minuteString.isEmpty()) {
                        Integer minuteInteger = Integer.getInteger(minuteString);
                        minuteList.add(minuteInteger);
                    }
                }
                if (!minuteList.isEmpty()) {
                    cronScheduleInfo.setJobScheduleCronMinute(minuteList);
                }
            }
        }

        return cronScheduleInfo;
    }

    NaElement prepareScheduleTime(String fsRpoValue, String fsRpoType) {
        NaElement elemSchedule = null;
        switch (fsRpoType.toUpperCase()) {
            case "MINUTES":
                elemSchedule = new NaElement("job-schedule-cron-minutes");
                elemSchedule.addNewChild("job-schedule-cron-minute", fsRpoValue);
                break;
            case "HOURS":
                elemSchedule = new NaElement("job-schedule-cron-hour");
                elemSchedule.setContent(fsRpoValue);
                break;
            case "DAYS":
                elemSchedule = new NaElement("job-schedule-cron-days");
                elemSchedule.addNewChild("job-schedule-cron-day", fsRpoValue);
                break;
            case "MONTH":
                elemSchedule = new NaElement("job-schedule-cron-months");
                elemSchedule.addNewChild("job-schedule-cron-month", fsRpoValue);
                break;
        }
        return elemSchedule;
    }

}
