/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.audit;


import org.joda.time.DateTime;

public class AuditLogRequest {
    private String serviceType; //serviceType of autid log to retrieve
    private String user; // retrieve auditlog belong to the user
    private String result; //retrieve auditlog with the status
    private String keyword; //retrieve auditlog contain the keyword
    private DateTime startTime; //Start Time to retrieve auditlog
    private DateTime endTime;   //End Time to retrieve auditlog
    private String timeBucket;
    private String language;

    public AuditLogRequest() {
    }

    public static class Builder {
        private String serviceType = null;
        private String user = null;
        private String result = null;
        private String keyword = null;
        private DateTime startTime = null;
        private DateTime endTime = null;
        private String timeBucket = null;
        private String language = null;

        public Builder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder start(DateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder end(DateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder timeBucket(String timeBucket) {
            this.timeBucket = timeBucket;
            return this;
        }

        public Builder lang(String language) {
            this.language = language;
            return this;
        }

        public Builder() {
        }

        public AuditLogRequest build() {
            return new AuditLogRequest(this);
        }
    }

    private AuditLogRequest(Builder builder) {
        this.serviceType = builder.serviceType;
        this.user = builder.user;
        this.result = builder.result;
        this.keyword = builder.keyword;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.timeBucket = builder.timeBucket;
        this.language = builder.language;

    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(DateTime endTime) {
        this.endTime = endTime;
    }

    public String getTimeBucket() {
        return timeBucket;
    }

    public void setTimeBucket(String timeBucket) {
        this.timeBucket = timeBucket;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString (){
        StringBuilder sb = new StringBuilder();
        sb.append("service type =" + this.serviceType).append(" timeBucket=" + this.timeBucket).
                append(" start time="+ this.startTime).append(" end Time=" + this.endTime).
                append(" user=" + this.user).append(" result=" + this.result).
                append(" keyword=" +this.keyword).append(" language="+ this.language);
        return sb.toString();
    }
}