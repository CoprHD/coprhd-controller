/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@Deprecated
@XmlRootElement
public class CategoryInfo extends ModelInfo {

    /**
     * Name of this category
     */
    private String name;

    /**
     * Title of this category. Used as the title in the UI
     */
    private String title;

    /**
     * Description of this category. Used as the description in the UI
     */
    private String description;

    /**
     * Icon to show for this category.
     */
    private String image;

    /**
     * Child categories that this category contains
     */
    private List<NamedReference> subCategories;

    /**
     * Child services that this category contains
     */
    private List<ServiceInfo> services;

    @XmlElementWrapper(name = "sub_categories")
    @XmlElement(name = "category")
    public List<NamedReference> getSubCategories() {
        if (subCategories == null) {
            subCategories = new ArrayList<>();
        }
        return subCategories;
    }

    @XmlElementWrapper(name = "services")
    @XmlElement(name = "service")
    public List<ServiceInfo> getServices() {
        if (services == null) {
            services = new ArrayList<>();
        }
        return services;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Category %s (%s), '%s' '%s'", name, id, title, description));
        if (!services.isEmpty()) {
            sb.append("\nServices:");
            for (ServiceInfo service : services) {
                sb.append("\n- ").append(service.toString());
            }
        }
        if (!subCategories.isEmpty()) {
            sb.append("\nSub-categories:");
            for (NamedReference child : subCategories) {
                sb.append("\n- ").append(String.format("category %s (%s)", child.getName(), child.getId()));
            }
        }
        return sb.toString();
    }
}
