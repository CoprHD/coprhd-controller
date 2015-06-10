/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

// This is the even object as required by the fullCalendar.js library
public class Event {
    
    public String id;
    public String title;
    public String start;
    public String end;
    public long startMillis;
    public long endMillis;
    public boolean allDay = false;
    public String url;
    public String className;
    public String color;
    
    public Event(String id, String name, DateTime startDate, DateTime endDate, DateTimeZone tz) {
        this.id = id;
        this.title = name;
        this.start = startDate.withZone(tz).toString();
        this.end = endDate.withZone(tz).toString();
        this.startMillis = startDate.getMillis();
        this.endMillis = endDate.getMillis();
        
        long now = System.currentTimeMillis();
        if (now >= startMillis && now <= endMillis) {
            className = "active";
        }
        else if (now > endMillis) {
            className = "past";
        }
        else {
            className = "future";
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Event [");
        sb.append(start);
        sb.append(" to ");
        sb.append(end);
        sb.append("]");
        return sb.toString();
    }
}
