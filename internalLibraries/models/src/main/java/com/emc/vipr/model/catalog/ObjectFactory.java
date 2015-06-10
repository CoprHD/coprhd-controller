/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {
    public ApiList createApiList() {
        return new ApiList();
    }

    public ApprovalInfo createApprovalInfo() {
        return new ApprovalInfo();
    }

    public CategoryInfo createCategoryInfo() {
        return new CategoryInfo();
    }

    public ExecutionInfo createExecutionInfo() {
        return new ExecutionInfo();
    }

    public ExecutionLogInfo createExecutionLogInfo() {
        return new ExecutionLogInfo();
    }

    public ExecutionTaskInfo createExecutionTaskInfo() {
        return new ExecutionTaskInfo();
    }

    public Link createLink() {
        return new Link();
    }

    public NamedReference createNamedReference() {
        return new NamedReference();
    }

    public Option createOption() {
        return new Option();
    }

    public OrderInfo createOrderInfo() {
        return new OrderInfo();
    }

    public Reference createReference() {
        return new Reference();
    }

    public ServiceInfo createServiceInfo() {
        return new ServiceInfo();
    }

    public ValidationError createValidationError() {
        return new ValidationError();
    }
    
    public ExecutionWindowInfo createExecutionWindowInfo() {
        return new ExecutionWindowInfo();
    }
    
    public CompositeValidationError createCompositeValidationError() {
         return new CompositeValidationError();
    }

    public Parameter createParameter() {
        return new Parameter();
    }
}
