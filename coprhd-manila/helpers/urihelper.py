'''
This class defines all the URIs used in the CLI module
Use the getter method to get the particular URI based on the component type
and the operation type.

This is singleton class, always use the 'singletonURIHelperInstance'
from outside this module/class to get a handle of the class.
'''


class URIHelper(object):

    '''
    This map will be a map of maps
    e.g for project component type, it will hold a map
    of its operations vs their uris
    '''
    COMPONENT_TYPE_VS_URIS_MAP = dict()

    '''
    Approval module map and its uris
    '''
    APPROVAL_GROUP_URIS_MAP = dict()
    URI_APPROVAL_APPROVALS = "/api/approvals"
    URI_APPROVAL_PENDING_APPROVALS = URI_APPROVAL_APPROVALS + "/pending"
    URI_APPROVAL_APPROVAL = URI_APPROVAL_APPROVALS + "/{0}"
    URI_APPROVAL_APPROVE = URI_APPROVAL_APPROVAL + "/approve"
    URI_APPROVAL_REJECT = URI_APPROVAL_APPROVAL + "/reject"

    '''
    Asset module map and its uris
    '''
    ASSET_OPTIONS_URIS_MAP = dict()
    URI_ASSET_OPTIONS_LIST = "/api/options/{0}"

    '''
    catalog module and its uris
    '''
    CATALOG_URIS_MAP = dict()
    URI_CATALOG_GET_SERVICE = "/api/services/{0}"
    URI_CATALOG_GET_SERVICE_DESCRIPTOR = URI_CATALOG_GET_SERVICE + \
        "/descriptor"
    URI_CATALOG_GET_CATEGORY = "/api/categories/{0}"
    URI_CATALOG_GET_CATALOG = "/api/catalog{0}"

    '''
    execution module map and its uris
    '''
    EXECUTION_WINDOW_URIS_MAP = dict()
    URI_EXECUTION_WINDOWS = "/admin/api/executionwindows"
    URI_EXECUTION_WINDOW = URI_EXECUTION_WINDOWS + "/{0}"
    URI_EXECUTION_WINDOW_CREATE = "/catalog/execution-windows"

    '''
    File System URIs
    '''
    FILESHARE_URIS_MAP = dict()
    URI_FILESHARES = "/file/filesystems"
    URI_FILESHARE = URI_FILESHARES + "/{0}"
    URI_FILESHARE_TASK_LIST = URI_FILESHARE + '/tasks'
    
    URI_FILESHARE_TASK=URI_FILESHARE_TASK_LIST+'/{1}'
    URI_FILESHARE_SEARCH_BY_PROJECT = '/file/filesystems/search?project={0}'
    URI_FILESHARE_TASK_BY_ID = '/vdc/tasks/{0}'
    '''
    Volume URIs
    '''
    VOLUME_URIS_MAP = dict()
    URI_VOLUMES = '/block/volumes'
    URI_VOLUME = URI_VOLUMES + '/{0}'
    URI_VOLUME_SEARCH_BY_PROJECT = '/block/volumes/search?project={0}'
    URI_VOLUME_TASK_LIST = URI_VOLUME + '/tasks'
    URI_VOLUME_TASK=URI_VOLUME_TASK_LIST+'/{1}'
    URI_VOLUME_TASK_BY_ID = '/vdc/tasks/{0}'

    '''
    Consistencygroup URIs
    '''
    CG_URIS_MAP = dict()
    URI_CGS = '/block/consistency-groups'
    URI_CG = URI_CGS + '/{0}'
    URI_CG_SEARCH_BY_PROJECT = '/block/consistency-groups/search?project={0}'
    URI_CG_TASK_LIST = URI_CG + '/tasks'
    URI_CG_TASK = URI_CG_TASK_LIST+'/{1}'
    URI_CG_TASK_BY_ID = '/vdc/tasks/{0}'
    
    '''
    Export Group URIs
    '''
    # Map to hold all export group uris
    EXPORT_GROUP_URIS_MAP = dict()
    URI_EXPORT_GROUP = "/block/exports"
    URI_EXPORT_GROUPS_SHOW = URI_EXPORT_GROUP + "/{0}"
    URI_EXPORT_GROUP_LIST = '/projects/{0}/resources'
    URI_EXPORT_GROUP_SEARCH = '/block/exports/search'
    URI_EXPORT_GROUP_SEARCH_BY_PROJECT = '/block/exports/search?project={0}'
    URI_EXPORT_GROUP_DEACTIVATE = URI_EXPORT_GROUPS_SHOW + '/deactivate'
    URI_EXPORT_GROUP_UPDATE = '/block/exports/{0}'
    URI_EXPORT_GROUP_TASKS_LIST = '/block/exports/{0}/tasks'
    URI_EXPORT_GROUP_TASK = URI_EXPORT_GROUP_TASKS_LIST+'/{1}'
    URI_EXPORT_GROUP_TASK_BY_ID = '/vdc/tasks/{0}'

    '''
    Order module map and its uris
    '''
    ORDER_URIS_MAP = dict()
    URI_ORDER_GET_ORDERS = "/api/orders"
    URI_ORDER_GET_ORDER = URI_ORDER_GET_ORDERS + "/{0}"
    URI_ORDER_GET_ORDER_EXECUTION = URI_ORDER_GET_ORDER + "/execution"

    '''
    Project module map and its uris
    '''
    PROJECT_URIS_MAP = dict()
    URI_PROJECT_QUOTA = "/projects/{0}/quota"

    '''
    Tenant module map and its uris
    '''
    TENANT_URIS_MAP = dict()
    URI_TENANT_QUOTA = "/tenants/{0}/quota"

    '''
    block vpool module map and its uris
    '''
    BLOCK_VPOOL_URIS_MAP = dict()
    URI_BLOCK_VPOOL_QUOTA = "/block/vpools/{0}/quota"
    
    
    OBJECT_VPOOL_URIS_MAP = dict()
    URI_OBJECT_VPOOL_QUOTA = "/object/vpools/{0}/quota"

    '''
    Datastore task APIs
    '''
    DATASTORE_URIS_MAP = dict()
    URI_DATASTORE_TASKS = "/vdc/data-stores/{0}/tasks"
    URI_DATASTORE_TASK = URI_DATASTORE_TASKS + "/{1}"

    '''
    file vpool module map and its uris
    '''
    FILE_VPOOL_URIS_MAP = dict()
    URI_FILE_VPOOL_QUOTA = "/file/vpools/{0}/quota"

    '''
    storage system  module map and its uris
    '''
    STORAGE_SYSTEM_URIS_MAP = dict()
    URI_STORAGE_SYSTEM_TASKS = "/vdc/storage-systems/{0}/tasks"
    URI_STORAGE_SYSTEM_TASK = URI_STORAGE_SYSTEM_TASKS + "/{1}"

    '''
    computing resurces map and its uris
    '''
    HOST_URIS_MAP = dict()
    URI_HOST_TASKS = "/compute/hosts/{0}/tasks"
    URI_HOST_TASK_BY_ID = '/vdc/tasks/{0}'

    CLUSTER_URIS_MAP = dict()
    URI_CLUSTER_TASKS = "/compute/clusters/{0}/tasks"
    URI_CLUSTER_TASK_BY_ID = '/vdc/tasks/{0}'

    VCENTER_URIS_MAP = dict()
    URI_VCENTER_TASKS = "/compute/vcenters/{0}/tasks"
    URI_VCENTER_TASK_BY_ID = '/vdc/tasks/{0}'

    INITIATOR_URIS_MAP = dict()
    URI_INITIATOR_TASKS = "/compute/initiators/{0}/tasks"
    URI_INITIATOR_TASK = URI_INITIATOR_TASKS + "/{1}"
    URI_INITIATOR_TASK_BY_ID = '/vdc/tasks/{0}'

    IPINTERFACE_URIS_MAP = dict()
    URI_IPINTERFACE_TASKS = "/compute/ip-interfaces/{0}/tasks"
    URI_IPINTERFACE_TASK_BY_ID = '/vdc/tasks/{0}'
    

    def __init__(self):
        '''
        During initialization of the class, lets fill all the maps
        '''
        self.__fillApprovalMap()
        self.__fillAssetOptionsMap()
        self.__fillCatalogMap()
        self.__fillExecutionWindowMap()
        self.__fillExportGroupMap()
        self.__fillOrderMap()
        self.__fillProjectMap()
        self.__fillTenantMap()
        self.__fillVpoolMap()
        self.__fillStorageSystemMap()
        self.__fillComputingResourcesMap()
        self.__fillFileSharesMap()
        self.__fillVolumeMap()
        self.__fillConsistencyGroupMap()

        self.__initializeComponentVsUriMap()
        self.__fillDataStoreMap()

    def __call__(self):
        return self

    def __initializeComponentVsUriMap(self):
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "approval"] = self.APPROVAL_GROUP_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "assetoptions"] = self.ASSET_OPTIONS_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["catalog"] = self.CATALOG_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "executionwindow"] = self.EXECUTION_WINDOW_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["export"] = self.EXPORT_GROUP_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["order"] = self.ORDER_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["project"] = self.PROJECT_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["tenant"] = self.TENANT_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "block_vpool"] = self.BLOCK_VPOOL_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "file_vpool"] = self.FILE_VPOOL_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "object_vpool"] = self.OBJECT_VPOOL_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP["datastore"] = self.DATASTORE_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "host"] = self.HOST_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "cluster"] = self.CLUSTER_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "vcenter"] = self.VCENTER_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "ipinterface"] = self.IPINTERFACE_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "initiator"] = self.INITIATOR_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "storagesystem"] = self.STORAGE_SYSTEM_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "fileshare"] = self.FILESHARE_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "volume"] = self.VOLUME_URIS_MAP
        self.COMPONENT_TYPE_VS_URIS_MAP[
            "consistencygroup"] = self.CG_URIS_MAP
        
    def __fillApprovalMap(self):
        self.APPROVAL_GROUP_URIS_MAP["approvals"] = self.URI_APPROVAL_APPROVALS
        self.APPROVAL_GROUP_URIS_MAP[
            "pending-approvals"] = self.URI_APPROVAL_PENDING_APPROVALS
        self.APPROVAL_GROUP_URIS_MAP["show"] = self.URI_APPROVAL_APPROVAL
        self.APPROVAL_GROUP_URIS_MAP["approve"] = self.URI_APPROVAL_APPROVE
        self.APPROVAL_GROUP_URIS_MAP["reject"] = self.URI_APPROVAL_REJECT

    def __fillAssetOptionsMap(self):
        self.ASSET_OPTIONS_URIS_MAP["list"] = self.URI_ASSET_OPTIONS_LIST

    def __fillCatalogMap(self):
        self.CATALOG_URIS_MAP["get-service"] = self.URI_CATALOG_GET_SERVICE
        self.CATALOG_URIS_MAP[
            "get-descriptor"] = self.URI_CATALOG_GET_SERVICE_DESCRIPTOR
        self.CATALOG_URIS_MAP["get-category"] = self.URI_CATALOG_GET_CATEGORY
        self.CATALOG_URIS_MAP["catalog"] = self.URI_CATALOG_GET_CATALOG

    def __fillExecutionWindowMap(self):
        self.EXECUTION_WINDOW_URIS_MAP["list"] = self.URI_EXECUTION_WINDOWS
        self.EXECUTION_WINDOW_URIS_MAP["show"] = self.URI_EXECUTION_WINDOW
        self.EXECUTION_WINDOW_URIS_MAP["create"] = self.URI_EXECUTION_WINDOW_CREATE

    def __fillExportGroupMap(self):
        self.EXPORT_GROUP_URIS_MAP["get_all"] = self.URI_EXPORT_GROUP
        self.EXPORT_GROUP_URIS_MAP["show"] = self.URI_EXPORT_GROUPS_SHOW
        self.EXPORT_GROUP_URIS_MAP["list"] = self.URI_EXPORT_GROUP_LIST
        self.EXPORT_GROUP_URIS_MAP["search"] = self.URI_EXPORT_GROUP_SEARCH
        self.EXPORT_GROUP_URIS_MAP[
            "search_by_project"] = self.URI_EXPORT_GROUP_SEARCH_BY_PROJECT
        self.EXPORT_GROUP_URIS_MAP[
            "deactivate"] = self.URI_EXPORT_GROUP_DEACTIVATE
        self.EXPORT_GROUP_URIS_MAP["update"] = self.URI_EXPORT_GROUP_UPDATE
        self.EXPORT_GROUP_URIS_MAP[
            "tasks_list"] = self.URI_EXPORT_GROUP_TASKS_LIST
        self.EXPORT_GROUP_URIS_MAP["task"] = self.URI_EXPORT_GROUP_TASK
        self.EXPORT_GROUP_URIS_MAP["task_by_id"] = self.URI_EXPORT_GROUP_TASK_BY_ID

    def __fillOrderMap(self):
        self.ORDER_URIS_MAP["list"] = self.URI_ORDER_GET_ORDERS
        self.ORDER_URIS_MAP["show"] = self.URI_ORDER_GET_ORDER
        self.ORDER_URIS_MAP[
            "show-execution"] = self.URI_ORDER_GET_ORDER_EXECUTION
        
    def __fillFileSharesMap(self):
        self.FILESHARE_URIS_MAP["tasks_list"] = self.URI_FILESHARE_TASK_LIST
        self.FILESHARE_URIS_MAP["task"] = self.URI_FILESHARE_TASK
        self.FILESHARE_URIS_MAP[
            "search_by_project"] = self.URI_FILESHARE_SEARCH_BY_PROJECT
        self.FILESHARE_URIS_MAP["show"] = self.URI_FILESHARE
        self.FILESHARE_URIS_MAP["task_by_id"] = self.URI_FILESHARE_TASK_BY_ID

    def __fillVolumeMap(self):
        self.VOLUME_URIS_MAP["tasks_list"] = self.URI_VOLUME_TASK_LIST
        self.VOLUME_URIS_MAP["task"] = self.URI_VOLUME_TASK
        self.VOLUME_URIS_MAP["task_by_id"] = self.URI_VOLUME_TASK_BY_ID
        self.VOLUME_URIS_MAP[
            "search_by_project"] = self.URI_VOLUME_SEARCH_BY_PROJECT
        self.VOLUME_URIS_MAP["show"] = self.URI_VOLUME

    def __fillConsistencyGroupMap(self):
        self.CG_URIS_MAP["tasks_list"] = self.URI_CG_TASK_LIST
        self.CG_URIS_MAP["task"] = self.URI_CG_TASK
        self.CG_URIS_MAP["task_by_id"] = self.URI_CG_TASK_BY_ID
        self.CG_URIS_MAP[
            "search_by_project"] = self.URI_CG_SEARCH_BY_PROJECT
        self.CG_URIS_MAP["show"] = self.URI_CG

    def __fillProjectMap(self):
        self.PROJECT_URIS_MAP["quota"] = self.URI_PROJECT_QUOTA

    def __fillTenantMap(self):
        self.TENANT_URIS_MAP["quota"] = self.URI_TENANT_QUOTA

    def __fillVpoolMap(self):
        self.BLOCK_VPOOL_URIS_MAP["quota"] = self.URI_BLOCK_VPOOL_QUOTA

        self.FILE_VPOOL_URIS_MAP["quota"] = self.URI_FILE_VPOOL_QUOTA
        
        self.OBJECT_VPOOL_URIS_MAP["quota"] = self.URI_OBJECT_VPOOL_QUOTA

    def __fillStorageSystemMap(self):
        self.STORAGE_SYSTEM_URIS_MAP["tasks_list"] = self.URI_STORAGE_SYSTEM_TASKS
        self.STORAGE_SYSTEM_URIS_MAP["task"] = self.URI_STORAGE_SYSTEM_TASK

    def __fillDataStoreMap(self):
        self.DATASTORE_URIS_MAP["tasks_list"] = self.URI_DATASTORE_TASKS
        self.DATASTORE_URIS_MAP["task"] = self.URI_DATASTORE_TASK

    def __fillComputingResourcesMap(self):
        self.HOST_URIS_MAP["tasks_list"] = self.URI_HOST_TASKS
        self.HOST_URIS_MAP["task"] = self.URI_HOST_TASK_BY_ID

        self.CLUSTER_URIS_MAP["tasks_list"] = self.URI_CLUSTER_TASKS
        self.CLUSTER_URIS_MAP["task"] = self.URI_CLUSTER_TASK_BY_ID

        self.VCENTER_URIS_MAP["tasks_list"] = self.URI_VCENTER_TASKS
        self.VCENTER_URIS_MAP["task"] = self.URI_VCENTER_TASK_BY_ID

        self.INITIATOR_URIS_MAP["tasks_list"] = self.URI_INITIATOR_TASKS
        self.INITIATOR_URIS_MAP["task"] = self.URI_INITIATOR_TASK
        self.INITIATOR_URIS_MAP["task_by_id"] = self.URI_INITIATOR_TASK_BY_ID

        self.IPINTERFACE_URIS_MAP["tasks_list"] = self.URI_IPINTERFACE_TASKS
        self.IPINTERFACE_URIS_MAP["task"] = self.URI_IPINTERFACE_TASK_BY_ID

    def getUri(self, componentType, operationType):
        return (
            self.COMPONENT_TYPE_VS_URIS_MAP.get(
                componentType).get(
                operationType)
        )

        return None

'''
Defining the singleton instance. Use this instance any time
the access is required for this module/class
'''
singletonURIHelperInstance = URIHelper()
