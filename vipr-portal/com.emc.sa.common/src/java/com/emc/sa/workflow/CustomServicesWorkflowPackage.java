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
package com.emc.sa.workflow;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.model.customservices.CustomServicesPrimitiveResourceRestRep;
import com.emc.storageos.model.customservices.CustomServicesPrimitiveRestRep;
import com.emc.storageos.model.customservices.CustomServicesWorkflowRestRep;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;


public final class CustomServicesWorkflowPackage {

    private final WorkflowMetadata metadata;
    private final ImmutableMap<URI, CustomServicesWorkflowRestRep> workflows;
    private final ImmutableMap<URI, CustomServicesPrimitiveRestRep> operations;
    private final ImmutableMap<URI, ResourcePackage> resources;
    
    private CustomServicesWorkflowPackage(final WorkflowMetadata metadata,
            final ImmutableMap<URI, CustomServicesWorkflowRestRep> subWorkflows,
            final ImmutableMap<URI, CustomServicesPrimitiveRestRep> operations,
            final ImmutableMap<URI, ResourcePackage> resources) {
        this.metadata = metadata;
        this.workflows = subWorkflows;
        this.operations = operations;
        this.resources = resources;
    }
    
    public WorkflowMetadata metadata() {
        return metadata;
    }
    
    public ImmutableMap<URI, CustomServicesWorkflowRestRep> workflows() {
        return workflows;
    }
    
    public ImmutableMap<URI, CustomServicesPrimitiveRestRep> operations() {
        return operations;
    }
    
    public ImmutableMap<URI, ResourcePackage> resources() {
        return resources;
    }
    
    public static class Builder {
        private WorkflowMetadata metadata;
        private Map<URI, CustomServicesWorkflowRestRep> workflows = new HashMap<URI, CustomServicesWorkflowRestRep>();
        private Map<URI, CustomServicesPrimitiveRestRep> operations = new HashMap<URI, CustomServicesPrimitiveRestRep>();
        private Map<URI, ResourcePackage> resources = new HashMap<URI, ResourcePackage>();
        
        public void metadata(final WorkflowMetadata metadata) {
            this.metadata = metadata;
        }
        
        public Builder addWorkflow(final CustomServicesWorkflowRestRep workflow) {
            this.workflows.put(workflow.getId(), workflow);
            return this;
        }
        
        public Builder addOperation(final CustomServicesPrimitiveRestRep operation) {
            this.operations.put(operation.getId(), operation);
            return this;
        }
        
        public Builder addResource(final ResourcePackage resourcePackage) {
            this.resources.put(resourcePackage.metadata().getId(), resourcePackage);
            return this;
        }
        
        public CustomServicesWorkflowPackage build() {
            return new CustomServicesWorkflowPackage(metadata, ImmutableMap.copyOf(workflows), ImmutableMap.copyOf(operations), ImmutableMap.copyOf(resources));
        }

    }
    
    public static class WorkflowVersion {
        
        private final int major;
        private final int minor;
        private final int servicePack;
        private final int patch;
        
        public WorkflowVersion(final int major, final int minor, final int servicePack, final int patch) {
            this.major = major;
            this.minor = minor;
            this.servicePack = servicePack;
            this.patch = patch;
        }
        
        public int major() {
            return major;
        }
        
        public int minor() {
            return minor;
        }
        
        public int servicePack() {
            return servicePack;
        }
        
        public int patch() {
            return patch;
        }
        
        @Override
        public String toString() {
            return major + "." + minor + "." + servicePack + "." + patch;
        }
    }

    public static class WorkflowMetadata {
        private URI id;
        private WorkflowVersion version;
        
        public WorkflowMetadata() {}
        
        public WorkflowMetadata(final URI id, final WorkflowVersion version) {
            this.id = id;
            this.version = version;
        }
        
        public URI getId() {
            return id;
        }
        
        public void setId(final URI id) {
            this.id = id;
        }
        
        public void setVersion(final WorkflowVersion version) {
            this.version = version;
        }
        
        public WorkflowVersion getVersion() {
            return version;
        }

        public byte[] toBytes() {
            return (version.toString()+id.toString()).getBytes(Charsets.UTF_8);
        }
    }
    
    public static class ResourcePackage {
        private final CustomServicesPrimitiveResourceRestRep metadata;
        private final byte[] bytes;
        
        public ResourcePackage(CustomServicesPrimitiveResourceRestRep metadata, byte[] bytes) {
            this.metadata = metadata;
            this.bytes = bytes;
        }
        
        public CustomServicesPrimitiveResourceRestRep metadata() {
            return metadata;
        }
        
        public byte[] bytes() {
            return bytes;
        }
        
        public static class ResourceBuilder {
            private CustomServicesPrimitiveResourceRestRep metadata;
            private byte[] bytes;
            
            public void metadata(final CustomServicesPrimitiveResourceRestRep metadata) {
                this.metadata = metadata;
            }
            
            public void bytes(byte[] bytes) {
                this.bytes = bytes;
            }
            
            public ResourcePackage build() throws IOException {
                if( null == metadata ) {
                    throw new IOException();
                }
                if( null == bytes ) {
                    throw new IOException();
                }
                
                return new ResourcePackage(metadata, bytes);
            }
            
        }
    }
}
