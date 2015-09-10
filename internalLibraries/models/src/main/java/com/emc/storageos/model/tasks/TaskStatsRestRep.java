/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tasks;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "task_stats")
public class TaskStatsRestRep {
    int pending;
    int ready;
    int error;

    public TaskStatsRestRep() {
    };

    public TaskStatsRestRep(int pending, int ready, int error) {
        this.pending = pending;
        this.error = error;
        this.ready = ready;
    }

    /** Number of tasks in a pending state */
    @XmlElement(name = "pending")
    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    /** Number of tasks in a ready state */
    @XmlElement(name = "ready")
    public int getReady() {
        return ready;
    }

    public void setReady(int ready) {
        this.ready = ready;
    }

    /** Number of tasks in a error state */
    @XmlElement(name = "error")
    public int getError() {
        return error;
    }

    public void setError(int error) {
        this.error = error;
    }
}
