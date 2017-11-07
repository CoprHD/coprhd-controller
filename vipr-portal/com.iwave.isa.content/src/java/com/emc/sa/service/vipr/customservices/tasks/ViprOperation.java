/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
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
        private String name;
        private String id;
        private Link link;
        private String inactive;
        private String global;
        private String remote;
        private Vdc vdc;
        private String internal;
        private Resource resource;
        private Tenant tenant;
        private String state;
        private String description;
        private String progress;
        private String creation_time;
        private String op_id;
        private String[] associated_resources;
        private String start_time;
        private String allowed_operations;

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public String getInactive() {
            return inactive;
        }

        public String getGlobal() {
            return global;
        }

        public String getRemote() {
            return remote;
        }

        public Vdc  getVdc() {
            return vdc;
        }

        public String getInternal() {
            return internal;
        }

        public Tenant getTenant() {
            return tenant;
        }

        public String getDescription() {
            return description;
        }

        public String getProgress() {
            return progress;
        }

        public String getCreation_time() {
            return creation_time;
        }

        public String[] getAssociated_resources() {
            return associated_resources;
        }

        public String getAllowed_operations() {
            return allowed_operations;
        }

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

    public static class Tenant {
        private String id;
        private Link link;

        public String getId() {
            return id;
        }

        public Link getLink() {
            return link;
        }
    }
    public static class Resource {

        private String id;
        private String name;
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

    public static class Vdc {
        private String id;
        private Link link;

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

