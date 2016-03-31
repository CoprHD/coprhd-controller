/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

/**
 * Class representing the isilon snapshot Schedule object
 * member names should match the key names in json object
 * 
 * @author sauraa
 *
 */
public class IsilonSnapshotSchedule {
    private Integer id;
    private String name;
    private String path;
    private String schedule;
    private String pattern;
    private String alias;
    private Integer duration;/* expiration */

    public IsilonSnapshotSchedule() {
    }

    public IsilonSnapshotSchedule(String name, String path, String schedule, String pattern, Integer duration) {
        this.name = name;
        this.path = path;
        this.schedule = schedule;
        this.pattern = pattern;
        this.duration = duration;

    }

    /**
     * 
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 
     * @param name
     *            The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 
     * @return
     *         The path
     */
    public String getPath() {
        return path;
    }

    /**
     * 
     * @param path
     *            The path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 
     * @return
     *         The schedule
     */
    public String getSchedule() {
        return schedule;
    }

    /**
     * 
     * @param schedule
     *            The schedule
     */
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    /**
     * 
     * @return
     *         The pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 
     * @param pattern
     *            The pattern
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * 
     * @return
     *         The alias
     */
    public Object getAlias() {
        return alias;
    }

    /**
     * 
     * @param alias
     *            The alias
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * 
     * @return
     *         The duration
     */
    public Integer getDuration() {
        return duration;
    }

    /**
     * 
     * @param duration
     *            The duration
     */
    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

}
