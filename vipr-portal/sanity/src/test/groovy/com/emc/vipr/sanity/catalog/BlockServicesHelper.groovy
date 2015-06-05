package com.emc.vipr.sanity.catalog

import static org.junit.Assert.*
import static com.emc.vipr.sanity.setup.Sanity.*

class BlockServicesHelper {
    
    static def CREATE_BLOCK_VOLUME_SERVICE = "BlockStorageServices/CreateBlockVolume"
    static def REMOVE_BLOCK_VOLUME_SERVICE = "BlockStorageServices/RemoveBlockVolumes"
    
    static void createAndRemoveBlockVolumeTest() {
        println "  ## Create Block Volume Test ## "
        
        println "Browsing categories"
        browseCategories()
        println ""
        
        // place the order to create a block volume
        def creationOrder = createBlockVolume()
        
        // place the order to remove the block volume from the creation order
        removeBlockVolume(creationOrder)
    }
    
    static def browseCategories() {
        println "Browsing root category:"
        def rootCategory = catalog.categories().getRootCatalogCategory(catalog.getUserTenantId())
        println rootCategory.title
        println ""
        
        println "Browsing block services category:"
        def blockCategory = catalog.browse().path("BlockStorageServices").category()
        println blockCategory.title
        println ""
    }
    
    static def createBlockVolume() {
        def overrideParameters = [:]
        overrideParameters.name = "create_block_volume_test_"+Calendar.instance.time.time
        overrideParameters.size = "1"
        return CatalogServiceHelper.placeOrder(CREATE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

    static def removeBlockVolume(creationOrder) {
        def overrideParameters = [:]
        def executionInfo = CatalogServiceHelper.getExecutionInfo(creationOrder)
        assertNotNull(executionInfo)
        assertNotNull(executionInfo.affectedResources)
        assertEquals(1, executionInfo.affectedResources.size())
        def createdVolumeId = executionInfo.affectedResources[0]
        overrideParameters.volumes = createdVolumeId
        return CatalogServiceHelper.placeOrder(REMOVE_BLOCK_VOLUME_SERVICE, overrideParameters)
    }

}
