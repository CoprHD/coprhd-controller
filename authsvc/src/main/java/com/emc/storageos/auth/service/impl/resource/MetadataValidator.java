/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.auth.service.impl.resource;

import com.emc.storageos.model.auth.SamlMetadata;
import org.apache.commons.lang.StringUtils;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.springframework.security.saml.metadata.MetadataManager;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.net.MalformedURLException;
import java.net.URL;

import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.springframework.security.saml.metadata.MetadataManager;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import java.net.MalformedURLException;
import java.net.URL;

public class MetadataValidator implements Validator {

    public boolean supports(Class<?> clazz) {
        return clazz.equals(SamlMetadata.class);
    }

    public void validate(Object target, Errors errors) {

        SamlMetadata metadata = (SamlMetadata) target;
        if (metadata == null) {
            errors.rejectValue("metadata", null, "Selected value is not supported.");
            return;
        }

        if (metadata.getBaseMetadata() == null) {
            errors.rejectValue("baseMetadata", null, "Selected value is not supported.");
            return;
        }

        if (metadata.getExtendedMetadata() == null) {
            errors.rejectValue("extendedMetadata", null, "Selected value is not supported.");
            return;
        }

        if (StringUtils.isBlank(metadata.getBaseMetadata().getEntityID())) {
            errors.rejectValue("entityID", null, "Selected value is not supported.");
        }

        if (StringUtils.isBlank(metadata.getBaseMetadata().getEntityBaseURL())) {
            errors.rejectValue("entityBaseURL", null, "Selected value is not supported.");
        }

        if (metadata.getBaseMetadata().getSamlResponseAttributes() == null ||
                CollectionUtils.isEmpty(metadata.getBaseMetadata().getSamlResponseAttributes())) {
            errors.rejectValue("nameID", null, "At least one NameID must be selected.");
        }
    }

}