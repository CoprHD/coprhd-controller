/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.sanity.catalog

import static com.emc.vipr.sanity.Sanity.*
import static org.junit.Assert.*

import com.emc.storageos.model.BulkIdParam
import com.emc.vipr.client.core.util.ResourceUtils
import com.emc.vipr.model.catalog.CatalogImageCreateParam
import com.emc.vipr.model.catalog.CatalogImageRestRep
import com.emc.vipr.model.catalog.CatalogImageUpdateParam

class CatalogImageServiceHelper {

    static List<URI> createdImages;

    static byte[] createdBytes = new byte[100];
    static byte[] updatedBytes = new byte[50];

    static createCatalogImage(URI tenantId) {

        CatalogImageCreateParam imageCreate = new CatalogImageCreateParam();
        imageCreate.setContentType("test");
        imageCreate.setData(createdBytes);
        imageCreate.setTenant(tenantId);
        return catalog.images().create(imageCreate);
    }

    static createAnotherCatalogImage(URI tenantId) {

        CatalogImageCreateParam imageCreate = new CatalogImageCreateParam();
        imageCreate.setContentType("test");
        imageCreate.setData(createdBytes);
        imageCreate.setTenant(tenantId);
        return catalog.images().create(imageCreate);
    }

    static updateCatalogImage(URI imageId) {

        CatalogImageUpdateParam imageUpdate = new CatalogImageUpdateParam();
        imageUpdate.setContentType("testUpdate");
        imageUpdate.setData(updatedBytes);
        return catalog.images().update(imageId, imageUpdate);
    }

    static void catalogImageServiceTest() {

        println "  ## Catalog Image Test ## "
        createdImages = new ArrayList<URI>();

        println "Getting tenantId to create catalog image"
        URI tenantId = catalog.getUserTenantId();
        println ""

        println "tenantId: " + tenantId
        println ""

        println "Creating catalog image"
        CatalogImageRestRep createdImage =
                createCatalogImage(tenantId);
        createdImages.add(createdImage.getId());
        println ""

        println "createdImageId: " + createdImage.getId();
        println ""

        println "Creating another catalog image"
        CatalogImageRestRep anotherImage =
                createAnotherCatalogImage(tenantId);
        createdImages.add(anotherImage.getId());
        println ""

        println "createdImageId: " + anotherImage.getId();
        println ""

        assertNotNull(createdImage);
        assertNotNull(createdImage.getId());
        assertEquals("test", createdImage.getContentType());
        assertTrue(createdBytes == createdImage.getData());

        List<URI> imageIds = new ArrayList<URI>();
        imageIds.add(createdImage.getId());
        imageIds.add(anotherImage.getId());

        println "Listing bulk resources - catalog images"
        println ""

        BulkIdParam bulkIds = new BulkIdParam();
        bulkIds.setIds(imageIds);

        List<CatalogImageRestRep> images =
                catalog.images().getBulkResources(bulkIds);

        assertNotNull(images);
        assertEquals(2, images.size());
        assertEquals(Boolean.TRUE, imageIds.contains(images.get(0).getId()));
        assertEquals(Boolean.TRUE, imageIds.contains(images.get(1).getId()));

        println "Listing execution windows by tenant"
        println ""

        images =
                catalog.images().getByTenant(tenantId);

        List<URI> retrievedImages = ResourceUtils.ids(images);

        assertNotNull(images);
        assertEquals(Boolean.TRUE, images.size() >= 2);
        assertEquals(Boolean.TRUE, retrievedImages.contains(imageIds.get(0)));
        assertEquals(Boolean.TRUE, retrievedImages.contains(imageIds.get(1)));

        println "Getting catalog image " + createdImage.getId();

        CatalogImageRestRep retrievedImage =
                catalog.images().get(createdImage.getId());
        println ""

        assertEquals(createdImage.getId(), retrievedImage.getId());

        println "Updating catalog image " + retrievedImage.getId();
        println ""

        CatalogImageRestRep updatedImage =
                updateCatalogImage(retrievedImage.getId());

        assertNotNull(updatedImage);
        assertEquals("testUpdate", updatedImage.getContentType());
        assertTrue(updatedBytes == updatedImage.getData());

        println "Deleting catalog images";
        println ""

        catalog.images().deactivate(updatedImage.getId());
        catalog.images().deactivate(anotherImage.getId());

        println "Getting deactivated catalog image " + updatedImage.getId();
        updatedImage = catalog.images().get(updatedImage.getId());
        println ""

        if (updatedImage != null) {
            assertEquals(true, updatedImage.getInactive());
        }
    }

    static void catalogImageServiceTearDown() {
        println "  ## Catalog Image Test Clean up ## "

        println "Getting catalog images"
        println ""
        if (createdImages != null) {

            createdImages.each {
                println "Getting catalog images: " + it;
                println ""
                CatalogImageRestRep imageToDelete =
                        catalog.images().get(it);
                if (imageToDelete != null
                && !imageToDelete.getInactive()) {
                    println "Deleting catalog image: " + it;
                    println ""
                    catalog.images().deactivate(it);
                }
            }
        }

        println "Cleanup Complete.";
        println ""
    }
}
