/*
 * Copyright 2015-2016 Dell Inc. or its subsidiaries.
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
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.util.List;
import java.util.Map;

import com.emc.sa.descriptor.ServiceDescriptor;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.CatalogCategory;
import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.CatalogServiceField;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.CatalogServiceCommonParam;
import com.emc.vipr.model.catalog.CatalogServiceCreateParam;
import com.emc.vipr.model.catalog.CatalogServiceFieldParam;
import com.emc.vipr.model.catalog.CatalogServiceFieldRestRep;
import com.emc.vipr.model.catalog.CatalogServiceList;
import com.emc.vipr.model.catalog.CatalogServiceRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class CatalogServiceMapper {

    public static final CatalogServiceMapper instance = new CatalogServiceMapper();

    public static CatalogServiceMapper getInstance() {
        return instance;
    }

    private CatalogServiceMapper() {
    }

    public static CatalogServiceRestRep map(CatalogService from, ServiceDescriptor descriptor,
            List<CatalogServiceField> catalogServiceFields) {
        if (from == null) {
            return null;
        }
        CatalogServiceRestRep to = new CatalogServiceRestRep();
        mapDataObjectFields(from, to);

        if (from.getCatalogCategoryId() != null) {
            to.setCatalogCategory(toRelatedResource(ResourceTypeEnum.CATALOG_CATEGORY, from.getCatalogCategoryId().getURI()));
        }
        if (from.getDefaultExecutionWindowId() != null) {
            to.setDefaultExecutionWindow(toRelatedResource(ResourceTypeEnum.EXECUTION_WINDOW, from.getDefaultExecutionWindowId().getURI()));
        }

        if (from.getApprovalRequired() != null) {
            to.setApprovalRequired(from.getApprovalRequired());
        }
        if (from.getExecutionWindowRequired() != null) {
            to.setExecutionWindowRequired(from.getExecutionWindowRequired());
        }
        
        to.setBaseService(from.getBaseService());
        to.setDescription(from.getDescription());
        to.setImage(from.getImage());
        to.setMaxSize(from.getMaxSize());
        to.setTitle(from.getTitle());
        to.setSortedIndex(from.getSortedIndex());
        
        if (descriptor != null) {
            to.setServiceDescriptor(ServiceDescriptorMapper.map(descriptor));
        }

        if (catalogServiceFields != null) {
            for (CatalogServiceField catalogServiceField : catalogServiceFields) {
                CatalogServiceFieldRestRep catalogServiceFieldRestRep = new CatalogServiceFieldRestRep();
                mapDataObjectFields(catalogServiceField, catalogServiceFieldRestRep);
                catalogServiceFieldRestRep.setOverride(catalogServiceField.getOverride());
                catalogServiceFieldRestRep.setValue(catalogServiceField.getValue());
                catalogServiceFieldRestRep.setSortedIndex(catalogServiceField.getSortedIndex());
                to.getCatalogServiceFields().add(catalogServiceFieldRestRep);
            }
        }
        
        if (from.getRecurringAllowed() != null) {
            to.setRecurringAllowed(from.getRecurringAllowed());
        }
        
        return to;
    }

    public static CatalogService createNewObject(CatalogServiceCreateParam param, CatalogCategory catalogCategory) {
        CatalogService newObject = new CatalogService();
        newObject.setId(URIUtil.createId(CatalogService.class));

        updateObject(newObject, param, catalogCategory);

        return newObject;
    }

    public static void updateObject(CatalogService object, CatalogServiceCommonParam param, CatalogCategory catalogCategory) {

        if (param.getBaseService() != null) {
            object.setBaseService(param.getBaseService());
        }
        if (param.getDescription() != null) {
            object.setDescription(param.getDescription());
        }
        if (param.getDefaultExecutionWindow() != null) {
            object.setDefaultExecutionWindowId(new NamedURI(param.getDefaultExecutionWindow(), "ExecutionWindow"));
        }
        if (param.getCatalogCategory() != null) {
            object.setCatalogCategoryId(new NamedURI(param.getCatalogCategory(), catalogCategory.getLabel()));
        }
        if (param.getImage() != null) {
            object.setImage(param.getImage());
        }
        if (param.getMaxSize() != null) {
            object.setMaxSize(param.getMaxSize());
        }
        if (param.getTitle() != null) {
            object.setTitle(param.getTitle());
        }
        if (param.getApprovalRequired() != null) {
            object.setApprovalRequired(param.getApprovalRequired());
        }
        if (param.getExecutionWindowRequired() != null) {
            object.setExecutionWindowRequired(param.getExecutionWindowRequired());
        }

        // Reset the order index if the service is moved to a different category
        if (object.getCatalogCategoryId() == null || param.getCatalogCategory().equals(object.getCatalogCategoryId().getURI()) == false) {
            object.setSortedIndex(null);
        }
        
        if (param.getRecurringAllowed() != null) {
            object.setRecurringAllowed(param.getRecurringAllowed());
        }
    }

    public static List<CatalogServiceField> createNewObjectList(CatalogService catalogService, List<CatalogServiceFieldParam> fieldParams) {
        List<CatalogServiceField> catalogServiceFields = Lists.newArrayList();
        if (fieldParams != null) {
            for (CatalogServiceFieldParam fieldParam : fieldParams) {
                CatalogServiceField catalogServiceField = createNewObject(catalogService, fieldParam);
                catalogServiceFields.add(catalogServiceField);
            }
        }
        return catalogServiceFields;
    }

    public static CatalogServiceField createNewObject(CatalogService catalogService, CatalogServiceFieldParam param) {
        CatalogServiceField newObject = new CatalogServiceField();
        newObject.setId(URIUtil.createId(CatalogServiceField.class));
        newObject.setCatalogServiceId(new NamedURI(catalogService.getId(), catalogService.getLabel()));
        newObject.setLabel(param.getName());

        updateObject(newObject, param);

        return newObject;
    }

    public static List<CatalogServiceField> updateObjectList(CatalogService catalogService,
            List<CatalogServiceField> existingCatalogServiceFields, List<CatalogServiceFieldParam> params) {
        List<CatalogServiceField> updatedFields = Lists.newArrayList();

        Map<String, CatalogServiceField> existingFields = toMap(existingCatalogServiceFields);
        for (CatalogServiceFieldParam param : params) {
            if (existingFields.keySet().contains(param.getName())) {
                CatalogServiceField existingField = existingFields.get(param.getName());
                updateObject(existingField, param);
                updatedFields.add(existingField);
            }
            else {
                CatalogServiceField newField = createNewObject(catalogService, param);
                updatedFields.add(newField);
            }
        }

        return updatedFields;
    }

    public static void updateObject(CatalogServiceField object, CatalogServiceFieldParam param) {
        if (param.getOverride() != null) {
            object.setOverride(param.getOverride());
        }
        if (param.getValue() != null) {
            object.setValue(param.getValue());
        }
    }

    public static CatalogServiceList toCatalogServiceList(List<CatalogService> catalogServices) {
        CatalogServiceList list = new CatalogServiceList();
        for (CatalogService catalogService : catalogServices) {
            NamedRelatedResourceRep resourceRep = toNamedRelatedResource(ResourceTypeEnum.CATALOG_SERVICE,
                    catalogService.getId(), catalogService.getLabel());
            list.getCatalogServices().add(resourceRep);
        }
        return list;
    }

    private static Map<String, CatalogServiceField> toMap(List<CatalogServiceField> catalogServiceFields) {
        Map<String, CatalogServiceField> fields = Maps.newTreeMap();
        if (catalogServiceFields != null) {
            for (CatalogServiceField catalogServiceField : catalogServiceFields) {
                fields.put(catalogServiceField.getLabel(), catalogServiceField);
            }
        }
        return fields;
    }

}
