/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package models.virtualpool;

import org.apache.commons.lang.StringUtils;

import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;

import com.emc.storageos.model.DataObjectRestRep;

/**
 * Common base for all virtual pool forms.
 */
public abstract class BaseVirtualPoolForm<T extends DataObjectRestRep> {
    public String id;

    @MaxSize(128)
    @MinSize(2)
    @Required
    public String name;

    @MaxSize(2048)
    @Required
    public String description;

    public boolean isNew() {
        return StringUtils.isBlank(id);
    }

    public void validate(String formName) {
        Validation.valid(formName, this);
    }

    public abstract void load(T virtualPool);

    public abstract T save();
}
