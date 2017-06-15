package com.emc.sa.service.vipr.customservices.tasks;

import java.util.List;

/**
 * GSON class for Vipr Async Operation response parsing
 */

public class ViprOperation {
    private List<ViprTask> task;

    public List<ViprTask> getTask() {
        return task;
    }

    public static class ViprTask {
        private Resource resource;
        private String state;
        private String start_time;
        private String op_id;
        private Link link;

        public Resource getResource() {
            return resource;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getStart_time() {
            return start_time;
        }

        public String getOp_id() {
            return op_id;
        }

        public Link getLink() {
            return link;
        }
    }

    public static class Resource {
        private String name;
        private String id;
        private Link link;

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public Link getLink() {
            return link;
        }
    }

    public static class Link {
        private String rel;
        private String href;

        public String getRel() {
            return rel;
        }

        public String getHref() {
            return href;
        }
    }
}

