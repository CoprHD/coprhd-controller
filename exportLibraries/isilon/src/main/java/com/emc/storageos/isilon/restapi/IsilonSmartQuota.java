/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

/*
 * Class representing the isilon smart quota object
 * member names should match the key names in json object
 */

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class IsilonSmartQuota {
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Usage {
        private long physical;
        private long logical;
        private long inodes;

        Usage() {
        }

        Usage(long p, long l, long i) {
            physical = p;
            logical = l;
            inodes = i;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Thresholds {
        // All thresholds are optional except "hard" . If threshold is present, should be positive.
        // Have to use "null" values for optional thresholds when not present.
        private Long hard;
        private Boolean hard_exceeded;
        private Long hard_last_exceeded;
        private Long advisory;
        private Boolean advisory_exceeded;
        private Long advisory_last_exceeded;
        private Long soft;
        private Long soft_grace;
        private Boolean soft_exceeded;
        private Long soft_last_exceeded;

        Thresholds() {
        }

        /**
         * Constrcutor
         * 
         * @param h
         *            hard limit
         * @param a
         *            advisory limit
         * @param s
         *            soft limit
         * @param sg
         *            soft grace limit
         */
        public Thresholds(Long h, Long a, Long s, Long sg) {
            hard = h != 0L ? h : null;
            advisory = a != 0L ? a : null;
            soft = s != 0L ? s : null;
            soft_grace = sg != 0L ? sg : null;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append(" hard: " + hard);
            str.append(" hard limit exceeded: " + hard_exceeded);
            str.append(" hard limit last exceeded: " + hard_last_exceeded);
            str.append(" advisory: " + advisory);
            str.append(" advisory limit exceeded: " + advisory_exceeded);
            str.append(" advisory limit exceeded: " + advisory_last_exceeded);
            str.append(" soft: " + soft);
            str.append(" soft limit exceeded: " + soft_exceeded);
            str.append(" soft_grace: " + soft_grace);
            str.append(" soft limit last exceeded: " + soft_last_exceeded);
            return str.toString();
        }

        public Long getHard() {
            return hard;
        }

        public Long getAdvisory() {
            return advisory;
        }

        public Long getSoft() {
            return soft;
        }

        public Long getSoftGrace() {
            return soft_grace;
        }

        public Boolean getHardExceeded() {
            return hard_exceeded;
        }

        public Long getHardLastExceeded() {
            return hard_last_exceeded;
        }

        public Boolean getsoftExceeded() {
            return soft_exceeded;
        }

        public Long getsoftLastExceeded() {
            return soft_last_exceeded;
        }

        public Boolean getAdvisoryExceeded() {
            return advisory_exceeded;
        }

        public Long getAdvisoryLastExceeded() {
            return advisory_last_exceeded;
        }
    }

    private String id;
    private String path;
    private String type; /* <string[directory|user|group]> */
    private String user;
    private String group;
    private Boolean include_snapshots;
    private Boolean thresholds_include_overhead;
    private Boolean enforced;
    private Boolean linked;
    private String notifications; /* <string[default|custom|disabled]> */
    private Usage usage;
    private Thresholds thresholds;
    private Boolean container; // if true use "hard limit" to show available space to client.

    public IsilonSmartQuota() {
    }

    /**
     * Constructor for un-enforced quota
     * 
     * @param p
     *            path
     */
    public IsilonSmartQuota(String p) {
        path = p;
        type = "directory";
        include_snapshots = false; // default
        thresholds_include_overhead = false; // default
        enforced = false;
    }

    /**
     * Constructor for enforced quota
     * 
     * @param p
     *            path
     * @param h
     *            hard limit to be enforced
     */
    public IsilonSmartQuota(String p, long h) {
        path = p;
        type = "directory";
        include_snapshots = false; // default
        thresholds_include_overhead = false; // default
        enforced = true;
        thresholds = new Thresholds(h, null, null, null);
    }

    public IsilonSmartQuota(String p, long h, long a, long s, long sg) {
        path = p;
        type = "directory";
        include_snapshots = false; // default
        thresholds_include_overhead = false; // default
        enforced = true;
        thresholds = new Thresholds(h, a, s, sg);
    }

    /**
     * Constructor for quota modification
     * 
     * @param h
     *            hard limit to be enforced
     */
    public IsilonSmartQuota(long h) {
        thresholds_include_overhead = false; // default
        enforced = true;
        thresholds = new Thresholds(h, null, null, null);
    }

    public IsilonSmartQuota(long h, long a, long s, long sg) {
        thresholds_include_overhead = false; // default
        enforced = true;
        thresholds = new Thresholds(h, a, s, sg);
    }

    /**
     * Constructor for enforced quota
     * 
     * @param p
     *            path
     * @param h
     *            hard limit to be enforced
     * @param bThresholdsIncludeOverhead
     *            flag to indicate if overhead should be included in quota
     * @param bIncludeSnapshots
     *            flag to indicate if snapshot should be included under quota
     */
    public IsilonSmartQuota(String p, long h, boolean bThresholdsIncludeOverhead, boolean bIncludeSnapshots) {
        path = p;
        type = "directory";
        include_snapshots = bIncludeSnapshots; // custom value
        thresholds_include_overhead = bThresholdsIncludeOverhead; // custom value
        enforced = true;
        thresholds = new Thresholds(h, null, null, null);
    }

    public IsilonSmartQuota(String p, String type, long h, long a, long s, long sg, Boolean bThresholdsIncludeOverhead,
            Boolean bIncludeSnapshots) {
        path = p;
        this.type = type;
        include_snapshots = bIncludeSnapshots; // custom value
        thresholds_include_overhead = bThresholdsIncludeOverhead; // custom value
        enforced = true;
        thresholds = new Thresholds(h, a, s, sg);
    }

    /**
     * Constructor for un-enforced quota
     * 
     * @param p
     *            path
     * @param bThresholdsIncludeOverhead
     *            flag to indicate if overhead should be included in quota
     * @param bIncludeSnapshots
     *            flag to indicate if snapshot should be included under quota
     */
    public IsilonSmartQuota(String p, boolean bThresholdsIncludeOverhead, boolean bIncludeSnapshots) {
        path = p;
        type = "directory";
        include_snapshots = bIncludeSnapshots; // custom value
        thresholds_include_overhead = bThresholdsIncludeOverhead; // custom value
        enforced = false;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(" Quota ( id: " + id);
        str.append(" , path: " + path);
        str.append(" , enforced: " + enforced);
        str.append(" , include_snapshots: " + include_snapshots);
        str.append(" , thresholds_include_overhead: " + thresholds_include_overhead);
        str.append((thresholds != null) ? (", thresholds: " + thresholds.toString()) : "");
        str.append(" )");
        return str.toString();
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Long h, Long a, Long s, Long sg) {
        thresholds = new Thresholds(h, a, s, sg);
    }

    public long getUsagePhysical() {
        return usage.physical;
    }

    public void setUsage(long p, long l, long i) {
        usage = new Usage(p, l, i);
    }

    public Boolean getContainer() {
        return container;
    }

    public void setContainer(Boolean container) {
        this.container = container;
    }

}
