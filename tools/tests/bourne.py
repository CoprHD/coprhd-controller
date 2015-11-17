# coding=utf-8
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import httplib
import cjson
import argparse
import sys
import os
import time
import json
import uuid
import base64
import urllib
import requests
import email
from email.Utils import formatdate
import cookielib
import telnetlib
import xml.etree.ElementTree as ET
#import xml2obj as x2o
import copy
import hmac
import re
import hashlib
import cookielib
import binascii
import datetime
import socket
import zlib
import struct
from time import sleep

try:
    # OpenSUSE CoprHD kits tend to display certificate warnings which aren't
    # relevant to running sanity tests
    requests.packages.urllib3.disable_warnings()
except AttributeError:
    # Swallow error, likely ViPR devkit
    pass

URI_SERVICES_BASE               = ''
URI_CATALOG                     = URI_SERVICES_BASE + '/catalog'
URI_CATALOG_VPOOL                 = URI_CATALOG       + '/vpools'
URI_CATALOG_VPOOL_FILE            = URI_CATALOG_VPOOL   + '/file'
URI_CATALOG_VPOOL_BLOCK           = URI_CATALOG_VPOOL   + '/block'
URI_CATALOG_VPOOL_OBJECT          = URI_CATALOG_VPOOL   + '/object'
URI_VPOOLS                         = URI_SERVICES_BASE + '/{0}/vpools'
URI_VPOOLS_MATCH                   = URI_SERVICES_BASE + '/{0}/vpools/matching-pools'
URI_OBJ_VPOOL                     = URI_SERVICES_BASE + '/{0}/data-services-vpools'
URI_VPOOL_INSTANCE                = URI_VPOOLS + '/{1}'
URI_OBJ_VPOOL_INSTANCE            = URI_OBJ_VPOOL + '/{1}'
URI_VPOOL_ACLS                    = URI_VPOOL_INSTANCE + '/acl'

URI_VPOOL_UPDATE                  = URI_VPOOL_INSTANCE + '/assign-matched-pools'
URI_VPOOL_DEACTIVATE              = URI_VPOOL_INSTANCE + '/deactivate'
URI_BLOCKVPOOLS_BULKGET            = URI_SERVICES_BASE + '/block/vpools/bulk'
URI_FILEVPOOLS_BULKGET             = URI_SERVICES_BASE + '/file/vpools/bulk'
URI_SMISPROVIDER_BULKGET        = URI_SERVICES_BASE + '/vdc/smis-providers/bulk'
URI_BLOCKSNAPSHOT_BULKGET       = URI_SERVICES_BASE + '/block/snapshots/bulk'
URI_FILESNAPSHOT_BULKGET        = URI_SERVICES_BASE + '/file/snapshots/bulk'
URI_EXPORTGROUP_BULKGET         = URI_SERVICES_BASE + '/block/exports/bulk'

URI_LOGOUT                      = URI_SERVICES_BASE + '/logout'
URI_MY_PASSWORD_CHANGE          = URI_SERVICES_BASE + '/password'
URI_USER_PASSWORD_CHANGE        = URI_MY_PASSWORD_CHANGE + '/reset/'
URI_USER_PASSWORD_GET           = URI_SERVICES_BASE + '/config/properties'
URI_USER_PASSWORD_PATTERN       = 'system_{0}_encpassword","value":"(.+?)"'
URI_TENANT                      = URI_SERVICES_BASE + '/tenant'
URI_TENANTS	                = URI_SERVICES_BASE + '/tenants/{0}'
URI_TENANTS_DEACTIVATE		= URI_TENANTS + '/deactivate'
URI_TENANTS_ROLES               = URI_TENANTS	    + '/role-assignments'
URI_TENANTS_SUBTENANT           = URI_TENANTS	    + '/subtenants'
URI_TENANTS_BULKGET           = URI_SERVICES_BASE      + '/tenants/bulk'
URI_TENANTS_HOSTS               = URI_TENANTS      + '/hosts'
URI_TENANTS_CLUSTERS            = URI_TENANTS      + '/clusters'
URI_TENANTS_VCENTERS            = URI_TENANTS      + '/vcenters'

URI_NODEOBJ                     = '/nodeobj/?name={0}'

URI_PROJECTS	                = URI_TENANTS 	    + '/projects'
URI_PROJECT			= URI_SERVICES_BASE + '/projects/{0}'
URI_PROJECT_ACLS		= URI_PROJECT	    + '/acl'
URI_PROJECTS_BULKGET            = URI_SERVICES_BASE + '/projects/bulk'

URI_FILESYSTEMS_LIST            = URI_SERVICES_BASE         + '/file/filesystems'
URI_FILESYSTEM                  = URI_SERVICES_BASE         + '/file/filesystems/{0}'
URI_FILESHARE_BULKGET           = URI_FILESYSTEMS_LIST      + '/bulk'
URI_FILESYSTEMS_EXPORTS         = URI_FILESYSTEM            + '/exports'
URI_FILESYSTEMS_EXPORTS_UPDATE  = URI_FILESYSTEM            + '/export'
URI_FILESYSTEMS_UNEXPORT        = URI_FILESYSTEM            + '/export'
URI_FILESYSTEMS_EXPAND          = URI_FILESYSTEM            + '/expand'
URI_FILESYSTEMS_SHARES          = URI_FILESYSTEM            + '/shares'
URI_FILESYSTEMS_UNSHARE         = URI_FILESYSTEMS_SHARES    + '/{1}'
URI_FILESYSTEMS_SHARES_ACL      = URI_FILESYSTEMS_SHARES    + '/{1}/acl'
URI_FILESYSTEMS_SHARES_ACL_SHOW = URI_FILESYSTEMS_SHARES    + '/{1}/acl'
URI_FILESYSTEMS_SHARES_ACL_DELETE = URI_FILESYSTEMS_SHARES  + '/{1}/acl'
URI_FILESYSTEM_SNAPSHOT         = URI_FILESYSTEM            + '/protection/snapshots'
URI_FILESYSTEMS_SEARCH          = URI_FILESYSTEMS_LIST      + '/search'
URI_FILESYSTEMS_SEARCH_PROJECT      = URI_FILESYSTEMS_SEARCH  + '?project={0}'
URI_FILESYSTEMS_SEARCH_PROJECT_NAME = URI_FILESYSTEMS_SEARCH_PROJECT  + '&name={1}'
URI_FILESYSTEMS_SEARCH_NAME         = URI_FILESYSTEMS_SEARCH  + '?name={0}'
URI_FILESYSTEMS_SEARCH_TAG          = URI_FILESYSTEMS_SEARCH  + '?tag={0}'
URI_FILE_SNAPSHOTS              = URI_SERVICES_BASE         + '/file/snapshots'
URI_FILE_SNAPSHOT               = URI_FILE_SNAPSHOTS        + '/{0}'
URI_FILE_SNAPSHOT_EXPORTS       = URI_FILE_SNAPSHOT         + '/exports'
URI_FILE_SNAPSHOT_UNEXPORT      = URI_FILE_SNAPSHOT          + '/export'
URI_FILE_SNAPSHOT_RESTORE       = URI_FILE_SNAPSHOT         + '/restore'
URI_FILE_SNAPSHOT_SHARES        = URI_FILE_SNAPSHOT         + '/shares'
URI_FILE_SNAPSHOT_SHARES_ACL      = URI_FILE_SNAPSHOT_SHARES    + '/{1}/acl'
URI_FILE_SNAPSHOT_SHARES_ACL_SHOW = URI_FILE_SNAPSHOT_SHARES    + '/{1}/acl'
URI_FILE_SNAPSHOT_SHARES_ACL_DELETE = URI_FILE_SNAPSHOT_SHARES    + '/{1}/acl'
URI_FILE_SNAPSHOT_UNSHARE       = URI_FILE_SNAPSHOT_SHARES  + '/{1}'
URI_FILE_SNAPSHOT_TASKS         = URI_FILE_SNAPSHOT         + '/tasks/{1}'

URI_FILE_QUOTA_DIR_LIST         = URI_FILESYSTEM + '/quota-directories'
URI_FILE_QUOTA_DIR_BASE         = URI_SERVICES_BASE + '/file/quotadirectories'
URI_FILE_QUOTA_DIR              = URI_FILE_QUOTA_DIR_BASE + '/{0}'
URI_FILE_QUOTA_DIR_DELETE       = URI_FILE_QUOTA_DIR + '/deactivate'

URI_VDC                     = URI_SERVICES_BASE  + '/vdc'
URI_VDC_GET                 = URI_VDC    + '/{0}'
URI_VDC_DISCONNECT_POST     = URI_VDC    + '/{0}/disconnect'
URI_VDC_RECONNECT_POST      = URI_VDC    + '/{0}/reconnect'
URI_VDC_SECRETKEY           = URI_VDC    + '/secret-key'
URI_VDC_CERTCHAIN           = URI_VDC    + '/keystore'

URI_VDCINFO                 =  '/object/vdcs' 
URI_VDCINFO_GET             = URI_VDCINFO    + '/vdc' + '/{0}'
URI_VDCINFO_INSERT =  URI_VDCINFO_GET
URI_VDCINFO_LOCAL = URI_VDCINFO + '/vdc/local'
URI_VDCINFO_LIST = URI_VDCINFO + '/vdc/list'

URI_CONTROL                     = URI_SERVICES_BASE + '/control'
URI_RECOVERY                    = URI_CONTROL + '/cluster/recovery'
URI_DB_REPAIR                   = URI_CONTROL + '/cluster/dbrepair-status'

URI_BACKUP                      = URI_SERVICES_BASE + '/backupset'
URI_BACKUP_CREATE               = URI_BACKUP + '/backup?tag={0}'
URI_BACKUP_DELETE               = URI_BACKUP + '/backup?tag={0}'
URI_BACKUP_LIST                 = URI_BACKUP
URI_BACKUP_DOWNLOAD             = URI_BACKUP + '/download?tag={0}'

URI_VOLUME_LIST                 = URI_SERVICES_BASE  + '/block/volumes'
URI_VOLUME_BULKGET              = URI_VOLUME_LIST  + '/bulk'
URI_VOLUME                      = URI_VOLUME_LIST    + '/{0}'
URI_VOLUME_EXPAND               = URI_VOLUME         + '/expand'
URI_VOLUMES_EXPORTS             = URI_VOLUME         + '/exports'
URI_VOLUMES_UNEXPORTS           = URI_VOLUME         + '/exports/{1},{2},{3}'
URI_VOLUMES_DEACTIVATE          = '/block/volumes/deactivate'
URI_BLOCK_SNAPSHOTS_LIST        = URI_VOLUME         + '/protection/snapshots'
URI_BLOCK_SNAPSHOTS             = URI_SERVICES_BASE  + '/block/snapshots/{0}'
URI_BLOCK_SNAPSHOTS_EXPORTS     = URI_BLOCK_SNAPSHOTS + '/exports'
URI_BLOCK_SNAPSHOTS_UNEXPORTS   = URI_BLOCK_SNAPSHOTS + '/exports/{1},{2},{3}'
URI_BLOCK_SNAPSHOTS_RESTORE     = URI_BLOCK_SNAPSHOTS + '/restore'
URI_BLOCK_SNAPSHOTS_ACTIVATE    = URI_BLOCK_SNAPSHOTS + '/activate'
URI_BLOCK_SNAPSHOTS_EXPOSE      = URI_BLOCK_SNAPSHOTS + '/expose'
URI_BLOCK_SNAPSHOTS_TASKS       = URI_BLOCK_SNAPSHOTS + '/tasks/{1}'
URI_VOLUME_CHANGE_VPOOL           = URI_VOLUME          + '/vpool'
URI_VOLUME_CHANGE_VPOOL_MATCH     = URI_VOLUME          + '/vpool-change/vpool'
URI_VOLUMES_SEARCH              = URI_VOLUME_LIST     + '/search'
URI_VOLUMES_SEARCH_PROJECT      = URI_VOLUMES_SEARCH  + '?project={0}'
URI_VOLUMES_SEARCH_PROJECT_NAME = URI_VOLUMES_SEARCH_PROJECT  + '&name={1}'
URI_VOLUMES_SEARCH_NAME         = URI_VOLUMES_SEARCH  + '?name={0}'
URI_VOLUMES_SEARCH_TAG          = URI_VOLUMES_SEARCH  + '?tag={0}'
URI_VOLUMES_SEARCH_WWN          = URI_VOLUMES_SEARCH  + '?wwn={0}'
URI_VOLUME_CHANGE_VARRAY            = URI_VOLUME          + '/varray'
URI_VOLUME_CONTINUOUS           = URI_VOLUME          + '/protection/continuous-copies'
URI_VOLUME_CHANGE_LINK           = URI_VOLUME_CONTINUOUS
URI_VOLUME_FULL_COPY            = URI_VOLUME_LIST     + '/{0}/protection/full-copies'
URI_VOLUME_FULL_COPY_ACTIVATE   = URI_VOLUME_LIST     + '/{0}/protection/full-copies/{1}/activate'
URI_VOLUME_FULL_COPY_DETACH     = URI_VOLUME_LIST     + '/{0}/protection/full-copies/{1}/detach'
URI_VOLUME_FULL_COPY_CHECK_PROGRESS = URI_VOLUME_LIST     + '/{0}/protection/full-copies/{1}/check-progress'
URI_FULL_COPY = URI_SERVICES_BASE  + '/block/full-copies'
URI_FULL_COPY_RESTORE = URI_FULL_COPY + '/{0}/restore'
URI_FULL_COPY_RESYNC = URI_FULL_COPY + '/{0}/resynchronize'
URI_ADD_JOURNAL = URI_VOLUME_LIST + '/protection/addJournalCapacity'

URI_UNMANAGED                    = URI_VDC + '/unmanaged'
URI_UNMANAGED_UNEXPORTED_VOLUMES = URI_UNMANAGED + '/volumes/ingest'
URI_UNMANAGED_VOLUMES_SEARCH     = URI_UNMANAGED + "/search"
URI_UNMANAGED_VOLUMES_SEARCH_NAME= URI_UNMANAGED_VOLUMES_SEARCH + "?name={0}"
URI_UNMANAGED_EXPORTED_VOLUMES   = URI_UNMANAGED + '/volumes/ingest-exported' 
URI_UNMANAGED_TASK               = URI_VDC + '/tasks/{0}'

URI_BLOCK_MIRRORS_BASE          = URI_VOLUME               + '/protection/continuous-copies'
URI_BLOCK_MIRRORS_LIST          = URI_BLOCK_MIRRORS_BASE
URI_BLOCK_MIRRORS_READ          = URI_BLOCK_MIRRORS_BASE   + '/{1}'
URI_BLOCK_MIRRORS_ATTACH        = URI_BLOCK_MIRRORS_BASE   + '/start'
URI_BLOCK_MIRRORS_DETACH_ALL    = URI_BLOCK_MIRRORS_BASE   + '/stop'
#URI_BLOCK_MIRRORS_DETACH        = URI_BLOCK_MIRRORS_BASE   + '/{1}/stop'
URI_BLOCK_MIRRORS_PAUSE_ALL    = URI_BLOCK_MIRRORS_BASE   + '/pause'
#URI_BLOCK_MIRRORS_PAUSE        = URI_BLOCK_MIRRORS_BASE   + '/{1}/pause'
URI_BLOCK_MIRRORS_RESUME_ALL    = URI_BLOCK_MIRRORS_BASE   + '/resume'
URI_BLOCK_MIRRORS_DEACTIVATE    = URI_BLOCK_MIRRORS_BASE   + '/deactivate'
#URI_BLOCK_MIRRORS_RESUME        = URI_BLOCK_MIRRORS_BASE   + '/{1}/resume'
#URI_BLOCK_SNAPSHOTS_RESTORE     = URI_BLOCK_SNAPSHOTS     + '/restore'

URI_BLOCK_CONSISTENCY_GROUP_BASE    = URI_SERVICES_BASE  + '/block/consistency-groups'
URI_BLOCK_CONSISTENCY_GROUP_CREATE  = URI_BLOCK_CONSISTENCY_GROUP_BASE
URI_BLOCK_CONSISTENCY_GROUP         = URI_BLOCK_CONSISTENCY_GROUP_BASE + '/{0}'
URI_BLOCK_CONSISTENCY_GROUP_TASKS   = URI_BLOCK_CONSISTENCY_GROUP + '/tasks/{1}'
URI_BLOCK_CONSISTENCY_GROUP_DELETE  = URI_BLOCK_CONSISTENCY_GROUP + '/deactivate'
URI_BLOCK_CONSISTENCY_GROUP_BULK    = URI_BLOCK_CONSISTENCY_GROUP_BASE + "/bulk"

URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_BASE       = URI_BLOCK_CONSISTENCY_GROUP + "/protection/snapshots"
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_CREATE     = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_BASE
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_LIST     = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_BASE
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT            = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_BASE + "/{1}"
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_TASKS      = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT + "/tasks/{2}"
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_ACTIVATE   = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT + "/activate"
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_DEACTIVATE = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT + "/deactivate"
URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_RESTORE    = URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT + "/restore"

URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE       = URI_BLOCK_CONSISTENCY_GROUP + "/protection/continuous-copies"
URI_BLOCK_CONSISTENCY_GROUP_SWAP                  = URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/swap"
URI_BLOCK_CONSISTENCY_GROUP_FAILOVER              = URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/failover"
URI_BLOCK_CONSISTENCY_GROUP_FAILOVER_CANCEL       = URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/failover-cancel"

#Object Platform ECS bucket definitions
URI_ECS_BUCKET_LIST                     = URI_SERVICES_BASE             + '/object/buckets'
URI_ECS_BUCKET                          = URI_SERVICES_BASE             + '/object/buckets/{0}'

URI_NETWORKSYSTEMS              = URI_SERVICES_BASE   + '/vdc/network-systems'
URI_NETWORKSYSTEM               = URI_NETWORKSYSTEMS  + '/{0}'
URI_NETWORKSYSTEM_DISCOVER      = URI_NETWORKSYSTEMS  + '/{0}/discover'

URI_NETWORKSYSTEM_FCENDPOINTS         = URI_NETWORKSYSTEMS  + '/{0}/fc-endpoints'
URI_NETWORKSYSTEM_FCENDPOINTS_FABRIC  = URI_NETWORKSYSTEM_FCENDPOINTS + '?fabric-id={1}'

URI_NETWORKSYSTEM_VDCREFERENCES = URI_NETWORKSYSTEMS + '/san-references/{0},{1}'
URI_NETWORKSYSTEM_REGISTER             = URI_NETWORKSYSTEMS  + '/{0}/register'
URI_NETWORKSYSTEM_DEREGISTER           = URI_NETWORKSYSTEMS  + '/{0}/deregister'

URI_NETWORKSYSTEM_ALIASES              = URI_NETWORKSYSTEM  + '/san-aliases'
URI_NETWORKSYSTEM_ALIASES_FABRIC       = URI_NETWORKSYSTEM_ALIASES  + '?fabric-id={1}'
URI_NETWORKSYSTEM_ALIASES_REMOVE       = URI_NETWORKSYSTEM_ALIASES  + '/remove'

URI_NETWORKSYSTEM_ZONES           	   = URI_NETWORKSYSTEM  + '/san-fabrics/{1}/san-zones'
URI_NETWORKSYSTEM_ZONES_QUERY      	   = URI_NETWORKSYSTEM_ZONES  + '?zone-name={2}&exclude-members={3}&exclude-aliases={4}'
URI_NETWORKSYSTEM_ZONES_REMOVE         = URI_NETWORKSYSTEM_ZONES  + '/remove'
URI_NETWORKSYSTEM_ZONES_ACTIVATE       = URI_NETWORKSYSTEM_ZONES  + '/activate'

URI_DISCOVERED_STORAGEDEVICES   = URI_SERVICES_BASE   + '/vdc/storage-systems'
URI_DISCOVERED_STORAGEDEVICE    = URI_DISCOVERED_STORAGEDEVICES  + '/{0}'
URI_STORAGEDEVICES              = URI_SERVICES_BASE   + '/vdc/storage-systems'
URI_STORAGEDEVICE               = URI_STORAGEDEVICES  + '/{0}'
URI_STORAGEDEVICE_DISCOVERALL   = URI_STORAGEDEVICES  + '/discover'
URI_STORAGESYSTEMS_BULKGET      = URI_DISCOVERED_STORAGEDEVICES + '/bulk'
URI_DISCOVERED_STORAGEDEVICE_DISCOVER    = URI_STORAGEDEVICE + '/discover'
URI_DISCOVERED_STORAGEDEVICE_NS = URI_DISCOVERED_STORAGEDEVICE_DISCOVER + '?namespace={1}'

URI_STORAGEPOOLS                = URI_STORAGEDEVICE   + '/storage-pools'
URI_STORAGEPOOL                 = URI_SERVICES_BASE   + '/vdc/storage-pools/{0}'
URI_STORAGEPOOL_SHOW            = URI_STORAGEPOOLS    + '/{1}'
URI_STORAGEPOOL_REGISTER        = URI_STORAGEPOOLS    + '/{1}/register'
URI_STORAGEPOOL_DEREGISTER      = URI_STORAGEPOOL     + '/deregister'
URI_STORAGEPOOL_UPDATE          = URI_STORAGEPOOL
URI_STORAGEPOOLS_BULKGET        = URI_SERVICES_BASE   + '/vdc/storage-pools/bulk'

URI_STORAGEPORTS                = URI_STORAGEDEVICE   + '/storage-ports'
URI_STORAGEPORT                 = URI_SERVICES_BASE   + '/vdc/storage-ports/{0}'
URI_STORAGEPORT_SHOW            = URI_STORAGEPORTS    + '/{1}'
URI_STORAGEPORT_UPDATE          = URI_STORAGEPORT
URI_STORAGEPORT_REGISTER        = URI_STORAGEPORTS    + '/{1}/register'
URI_STORAGEPORT_DEREGISTER      = URI_STORAGEPORT     + '/deregister'
URI_STORAGEPORTS_BULKGET        = URI_SERVICES_BASE   + '/vdc/storage-ports/bulk'

URI_VARRAYS               = URI_SERVICES_BASE   + '/vdc/varrays'
URI_VARRAY                = URI_VARRAYS   + '/{0}'
URI_VARRAY_PORTS          = URI_VARRAY + '/storage-ports'
URI_VARRAY_ACLS           = URI_VARRAY + '/acl'
URI_VARRAYS_BULKGET       = URI_VARRAYS   + '/bulk'

URI_NETWORKS              = URI_SERVICES_BASE   + '/vdc/networks'
URI_VARRAY_NETWORKS       = URI_VARRAY    + '/networks'
URI_NETWORK               = URI_NETWORKS  + '/{0}'
URI_NETWORK_ENDPOINTS     = URI_NETWORK   + '/endpoints'
URI_NETWORK_ASSIGN        = URI_NETWORK   + ''
URI_NETWORK_UNASSIGN      = URI_NETWORK   + ''
URI_NETWORKS_BULKGET      = URI_NETWORKS + '/bulk'
URI_NETWORK_DEACTIVATE    = URI_NETWORK   + '/deactivate?force={1}'
URI_NETWORK_REGISTER      = URI_NETWORK   + '/register'
URI_NETWORK_DEREGISTER    = URI_NETWORK   + '/deregister'

URI_SMISPROVIDERS               = URI_SERVICES_BASE   + '/vdc/smis-providers'
URI_SMISPROVIDER                = URI_SMISPROVIDERS   + '/{0}'

URI_STORAGEPROVIDERS               = URI_SERVICES_BASE   + '/vdc/storage-providers'
URI_STORAGEPROVIDER                = URI_STORAGEPROVIDERS   + '/{0}'

URI_STORAGETIER                 = URI_SERVICES_BASE   + '/vdc/storage-tiers/{0}'
URI_STORAGETIERS                 = URI_SERVICES_BASE   + '/vdc/storage-tiers'

URI_EXPORTGROUP_LIST            = URI_SERVICES_BASE   + '/block/exports'
URI_EXPORTGROUP_INSTANCE        = URI_SERVICES_BASE   + '/block/exports/{0}'
URI_EXPORTGROUP_VOLUMES         = URI_SERVICES_BASE   + '/block/exports/{0}/volumes'
URI_EXPORTGROUP_VOLUME_INSTANCE = URI_SERVICES_BASE   + '/block/exports/{0}/volumes/{1}'
URI_EXPORTGROUP_VOLUMES_REMOVE  = URI_SERVICES_BASE   + '/block/exports/{0}/remove-volumes'
URI_EXPORTGROUP_INITS           = URI_SERVICES_BASE   + '/block/exports/{0}/initiators'
URI_EXPORTGROUP_INIT_DELETE     = URI_SERVICES_BASE   + '/block/exports/{0}/initiators/{1},{2}'
URI_EXPORTGROUP_INITS_REMOVE    = URI_SERVICES_BASE   + '/block/exports/{0}/remove-initiators'
URI_EXPORTGROUP_SEARCH_PROJECT  = URI_EXPORTGROUP_LIST + '/search?project={0}'

URI_HOSTS                       = URI_SERVICES_BASE   + '/compute/hosts'
URI_HOST                        = URI_SERVICES_BASE   + '/compute/hosts/{0}'
URI_HOSTS_BULKGET               = URI_HOSTS           + '/bulk'
URI_HOST_INITIATORS             = URI_SERVICES_BASE   + '/compute/hosts/{0}/initiators'
URI_HOST_IPINTERFACES           = URI_SERVICES_BASE   + '/compute/hosts/{0}/ip-interfaces'
URI_INITIATORS                  = URI_SERVICES_BASE   + '/compute/initiators'
URI_INITIATOR                   = URI_SERVICES_BASE   + '/compute/initiators/{0}'
URI_INITIATOR_REGISTER          = URI_SERVICES_BASE   + '/compute/initiators/{0}/register'
URI_INITIATOR_DEREGISTER        = URI_SERVICES_BASE   + '/compute/initiators/{0}/deregister'
URI_INITIATORS_BULKGET          = URI_SERVICES_BASE   + '/compute/initiators/bulk'
URI_IPINTERFACES                = URI_SERVICES_BASE   + '/compute/ip-interfaces'
URI_IPINTERFACE                 = URI_SERVICES_BASE   + '/compute/ip-interfaces/{0}'
URI_IPINTERFACE_REGISTER        = URI_SERVICES_BASE   + '/compute/ip-interfaces/{0}/register'
URI_IPINTERFACE_DEREGISTER      = URI_SERVICES_BASE   + '/compute/ip-interfaces/{0}/deregister'
URI_IPINTERFACES_BULKGET        = URI_SERVICES_BASE   + '/compute/ip-interfaces/bulk'
URI_VCENTERS                    = URI_SERVICES_BASE   + '/compute/vcenters'
URI_VCENTER                     = URI_SERVICES_BASE   + '/compute/vcenters/{0}'
URI_VCENTERS_BULKGET            = URI_VCENTERS        + '/bulk'
URI_VCENTER_DATACENTERS         = URI_VCENTER         + '/vcenter-data-centers'
URI_CLUSTERS                    = URI_SERVICES_BASE   + '/compute/clusters'
URI_CLUSTER                     = URI_SERVICES_BASE   + '/compute/clusters/{0}'
URI_CLUSTERS_BULKGET            = URI_CLUSTERS        + '/bulk'
URI_DATACENTERS                 = URI_SERVICES_BASE   + '/compute/vcenter-data-centers'
URI_DATACENTER                  = URI_SERVICES_BASE   + '/compute/vcenter-data-centers/{0}'
URI_DATACENTERS_BULKGET         = URI_SERVICES_BASE   + '/compute/vcenter-data-centers/bulk'

URI_DATA_STORE_LIST            = URI_SERVICES_BASE   + '/vdc/data-stores'
URI_DATA_STORE                 = URI_SERVICES_BASE   + '/vdc/data-stores/{0}'
URI_DATA_STORE_BULKGET         = URI_DATA_STORE_LIST + '/bulk'
URI_KEYPOOLS                    = URI_SERVICES_BASE + '/object/keypools'
URI_KEYPOOLS_INSTANCE           = URI_KEYPOOLS + '/{0}'
URI_KEYPOOLS_ACCESSMODE_INSTANCE = URI_KEYPOOLS + '/access-mode' + '/{0}'
URI_KEYPOOLS_FILEACCESS_INSTANCE = URI_KEYPOOLS + '/fileaccess' + '/{0}'
URI_KEY_INSTANCE                = URI_KEYPOOLS_INSTANCE + '/{1}'
URI_KEYS                        = URI_SERVICES_BASE + '/object/keypools'
URI_KEYS_INSTANCE               = URI_KEYS + '/{0}'

URI_ATMOS_DEVICE_LIST           = URI_SERVICES_BASE   + '/object/atmos-importer'
URI_ATMOS_DEVICE_TASK           = URI_SERVICES_BASE   + '/object/atmos-importer/{0}/tasks/{1}'
URI_ATMOS_DEVICE                = URI_SERVICES_BASE   + '/object/atmos-importer/{0}'
URI_ATMOS_DEVICE_DELETE         = URI_SERVICES_BASE   + '/object/atmos-importer/{0}/deactivate'

URI_OBJECT_INGESTION_LIST       = URI_SERVICES_BASE   + '/object/ingestion'
URI_OBJECT_INGESTION            = URI_SERVICES_BASE   + '/object/ingestion/{0}'
URI_OBJECT_INGESTION_DELETE     = URI_SERVICES_BASE   + '/object/ingestion/{0}/deactivate'
URI_OBJECT_INGESTION_OP_STATUS  = URI_SERVICES_BASE   + '/object/ingestion/{0}/tasks/{1}'

URI_OBJECTTZ                    = URI_SERVICES_BASE + '/object/networks'
URI_OBJECTTZ_INSTANCE           = URI_OBJECTTZ + '/{0}'
URI_OBJECTTZ_DELETE             = URI_OBJECTTZ + '/{0}/deactivate'

URI_DISCOVERED_PROTECTION_SYSTEMS   = URI_SERVICES_BASE   + '/vdc/protection-systems'
URI_DISCOVERED_PROTECTION_SYSTEM    = URI_DISCOVERED_PROTECTION_SYSTEMS + '/{0}'
URI_PROTECTION_SYSTEM = URI_SERVICES_BASE + '/vdc/protection-systems/{0}'
URI_PROTECTION_SYSTEMS = URI_SERVICES_BASE + '/vdc/protection-systems'
URI_PROTECTION_SYSTEM_DISCOVER = URI_PROTECTION_SYSTEM + '/discover'
URI_PROTECTION_SYSTEM_UPDATE          = URI_PROTECTION_SYSTEM
URI_DISCOVERED_PROTECTION_SYSTEM_DISCOVER    = URI_PROTECTION_SYSTEM + '/discover'
URI_DISCOVERED_PROTECTION_SYSTEM_NS = URI_DISCOVERED_PROTECTION_SYSTEM_DISCOVER + '?namespace={1}'

URI_PROTECTIONSET = URI_SERVICES_BASE + '/block/protection-sets/{0}'
URI_PROTECTIONSETS = URI_SERVICES_BASE + '/block/protection-sets'

URI_VDC_ROLES               = URI_SERVICES_BASE + '/vdc/role-assignments'
URI_VDC_AUTHN_PROFILE = URI_SERVICES_BASE + '/vdc/admin/authnproviders'
URI_VDC_AUTHN_PROFILES = URI_SERVICES_BASE + '/vdc/admin/authnproviders/{0}'

URI_AUTO_TIER_POLICY = URI_SERVICES_BASE + '/vdc/auto-tier-policies/{0}'

URI_WORKFLOW_LIST               = URI_SERVICES_BASE + '/vdc/workflows'
URI_WORKFLOW_RECENT             = URI_WORKFLOW_LIST + '/recent'
URI_WORKFLOW_INSTANCE           = URI_WORKFLOW_LIST + '/{0}'
URI_WORKFLOW_STEPS              = URI_WORKFLOW_INSTANCE + '/steps'

URI_AUDIT_QUERY = URI_SERVICES_BASE + '/audit/logs/?time_bucket={0}&language={1}'
URI_MONITOR_QUERY = URI_SERVICES_BASE + '/monitoring/events/?time_bucket={0}'

URI_RESOURCE_DEACTIVATE	     = '{0}/deactivate'

URI_S3_SERVICE_BASE             = ''
URI_S3_BUCKET_INSTANCE          = URI_S3_SERVICE_BASE + '/{0}'
URI_S3_KEY_INSTANCE             = URI_S3_SERVICE_BASE + '/{0}/{1}'
URI_S3_KEY_INSTANCE_ALTERNATE   = URI_S3_SERVICE_BASE + '/{0}' #used when the bucket name is part of the Host header
URI_S3_PING                     = URI_S3_SERVICE_BASE + '/'
URI_S3_DATANODE			= URI_S3_SERVICE_BASE + '/'

URI_ATMOS_SERVICE_BASE           = '/rest'
URI_ATMOS_OBJECTS                = URI_ATMOS_SERVICE_BASE + '/objects'
URI_ATMOS_OBJECTS_OID            = URI_ATMOS_OBJECTS  + '/{0}'
URI_ATMOS_NAMESPACE              = URI_ATMOS_SERVICE_BASE + '/namespace'
URI_ATMOS_NAMESPACE_PATH         = URI_ATMOS_NAMESPACE + '{0}'
URI_ATMOS_SUBTENANT_BASE         = URI_ATMOS_SERVICE_BASE + '/subtenant'
URI_ATMOS_SUBTENANT_INSTANCE     = URI_ATMOS_SUBTENANT_BASE + '/{0}'

URI_ATMOS_OBJECT_INSTANCE       = URI_ATMOS_OBJECTS + '/{0}'
URI_ATMOS_NAMESPACE_INSTANCE    = URI_ATMOS_NAMESPACE + '/{0}'

URI_SWIFT_SERVICE_BASE          = '/v1'
URI_SWIFT_ACCOUNT_INSTANCE      = URI_SWIFT_SERVICE_BASE + '/{0}'
URI_SWIFT_CONTAINER_INSTANCE    = URI_SWIFT_SERVICE_BASE + '/{0}/{1}'
URI_SWIFT_KEY_INSTANCE          = URI_SWIFT_SERVICE_BASE + '/{0}/{1}/{2}'

URI_NAMESPACE_COMMON            = URI_SERVICES_BASE + '/object/namespaces'
URI_NAMESPACE_BASE              = URI_NAMESPACE_COMMON + '/namespace'
URI_NAMESPACE_INSTANCE          = URI_NAMESPACE_BASE + '/{0}'
URI_NAMESPACE_TENANT_BASE       = URI_NAMESPACE_COMMON + '/tenant'
URI_NAMESPACE_TENANT_INSTANCE   = URI_NAMESPACE_TENANT_BASE + '/{0}'
URI_NAMESPACE_RETENTION_BASE    = URI_NAMESPACE_INSTANCE + '/retention'
URI_NAMESPACE_RETENTION_INSTANCE= URI_NAMESPACE_RETENTION_BASE + '/{1}'

URI_BUCKET_COMMON               = '/object/bucket'
URI_BUCKET_INSTANCE             = URI_BUCKET_COMMON + '/{0}'
URI_BUCKET_RETENTION            = URI_BUCKET_INSTANCE + '/retention'
URI_BUCKET_UPDATE_OWNER         = URI_BUCKET_INSTANCE + '/owner'

URI_SECRET_KEY                  = URI_SERVICES_BASE + '/object/secret-keys'
URI_SECRET_KEY_USER             = URI_SERVICES_BASE + '/object/user-secret-keys/{0}'
URI_DELETE_SECRET_KEY_USER             = URI_SERVICES_BASE + '/object/user-secret-keys/{0}/deactivate'
URI_WEBSTORAGE_USER             = URI_SERVICES_BASE + '/object/users'
URI_WEBSTORAGE_USER_DEACTIVATE  = URI_WEBSTORAGE_USER + '/deactivate'
URI_BASEURL_BASE                = URI_SERVICES_BASE + '/object/baseurl'
URI_BASEURL_INSTANCE            = URI_BASEURL_BASE + '/{0}'
URI_BASEURL_DEACTIVATE          = URI_BASEURL_BASE + '/{0}/deactivate'
URI_PASSWORDGROUP               = URI_SERVICES_BASE + '/object/user-password/{0}'
URI_PASSWORDGROUP_DEACTIVATE    = URI_PASSWORDGROUP + '/deactivate'

URI_MIGRATIONS                  = URI_SERVICES_BASE + '/block/migrations'
URI_MIGRATION                   = URI_MIGRATIONS + '/{0}'
URI_ZONE                        = URI_SERVICES_BASE + '/zone/{0}'
URI_ZONES	                = URI_SERVICES_BASE + '/zone'
URI_ZONE_CAPACITY               = URI_SERVICES_BASE + '/zone/capacity'

URI_REPLICATION_GROUP           = URI_SERVICES_BASE + '/vdc/data-service/vpools/{0}'
URI_REPLICATION_GROUPS          = URI_SERVICES_BASE + '/vdc/data-service/vpools'
URI_REPLICATION_EXTEND          = URI_SERVICES_BASE + '/vdc/data-service/vpools/{0}/addvarrays'
URI_REPLICATION_COMPRESS        = URI_SERVICES_BASE + '/vdc/data-service/vpools/{0}/removevarrays'

URI_VNAS_SERVERS                = URI_SERVICES_BASE + '/vdc/vnas-servers'
URI_VNAS_SERVER                 = URI_SERVICES_BASE + '/vdc/vnas-servers/{0}'
URI_VNAS_SERVER_ASSIGN          = URI_SERVICES_BASE + '/projects/{0}/assign-vnas-servers'
URI_VNAS_SERVER_UNASSIGN        = URI_SERVICES_BASE + '/projects/{0}/unassign-vnas-servers'

URI_GEO_SERVICES_BASE           = ''
URI_CHUNKINFO                   = URI_GEO_SERVICES_BASE + '/chunkinfo'
URI_CHUNKDATA                   = URI_GEO_SERVICES_BASE + '/chunkdata/{0}'

URI_OBJ_CERT                    = '/object-cert/keystore'
URI_OBJ_SECRET_KEY                    = '/object-cert/secret-key'

OBJCTRL_INSECURE_PORT           = '9010'
OBJCTRL_PORT                    = '4443'
S3_INSECURE_PORT                = '9020'
S3_PORT                         = '9021'
ATMOS_INSECURE_PORT             = '9022'
ATMOS_PORT                      = '9023'
SWIFT_INSECURE_PORT             = '9024'
SWIFT_PORT                      = '9025'

GEO_PORT                        = '9096'
GEO_INSECURE_PORT               = '9096'

URI_KICKSTART                   = URI_SERVICES_BASE + '/kickstart'
URI_WHOAMI                      = URI_SERVICES_BASE + '/user/whoami'
URI_OBJECT_PROPERTIES           = URI_SERVICES_BASE + '/config/object/properties'

URI_PROXY_TOKEN = URI_SERVICES_BASE + '/proxytoken'

PROD_NAME                       = 'storageos'
TENANT_PROVIDER                 = 'urn:storageos:TenantOrg:provider:'

API_SYNC_TIMEOUT                = os.getenv('BOURNE_API_SYNC_TIMEOUT', 120000)

USE_SSL                         = os.getenv('BOURNE_USE_SSL', 1)
PORT                            = os.getenv('BOURNE_PORT', '4443')
BOURNE_DEBUG                    = os.getenv('BOURNE_DEBUG', 0)
FILE_ACCESS_MODE_HEADER         = "x-emc-file-access-mode"
FILE_ACCESS_DURATION_HEADER     = "x-emc-file-access-duration"
HOST_LIST_HEADER                = "x-emc-file-access-host-list"
USER_HEADER                     = "x-emc-file-access-uid"
TOKEN_HEADER                    = "x-emc-file-access-token"
START_TOKEN_HEADER                    = "x-emc-file-access-start-token"
END_TOKEN_HEADER                    = "x-emc-file-access-end-token"
FILE_ACCESS_PRESERVE_DIR_STRUCTURE_HEADER = "x-emc-file-access-preserve-directory-structure"

SKIP_SECURITY                   = os.getenv('BOURNE_SECURITY_DISABLED', 0)

SWIFT_AUTH_TOKEN = 'X-Auth-Token'
SWIFT_AUTH_USER = 'X-Auth-User'
SWIFT_AUTH_KEY = 'X-Auth-Key'
SWIFT_DELETE_AT = 'X-Delete-At'
SWIFT_COPY_FROM = 'X-Copy-From'
SWIFT_DELETE_AFTER = 'X-Delete-After'

SWIFT_X_CONTAINER_READ = "X-Container-Read"
SWIFT_X_CONTAINER_WRITE = "X-Container-Write"

HTTP_OK = 200
HTTP_NO_CONTENT = 204
HTTP_NOT_FOUND=404

S3_XML_NS = 'http://s3.amazonaws.com/doc/2006-03-01/'
OPENSTACK_XML_NS = "http://docs.openstack.org/identity/api/v2.0"

SEC_REDIRECT                = 302
SEC_TOKEN_FILE            = os.getenv('BOURNE_TOKEN_FILE', 'token.txt')
SEC_AUTHTOKEN_HEADER                = 'X-SDS-AUTH-TOKEN'

SEC_PROXYTOKEN_HEADER = 'X-SDS-AUTH-PROXY-TOKEN'
PROXY_USER_NAME = 'proxyuser'
PROXY_USER_PASSWORD = 'ChangeMe'

COOKIE_FILE                     = os.getenv('BOURNE_COOKIE_FILE', 'cookiejar')
# Number of seconds a request should wait for response.
# It only effects the connection process itself, not the downloading of the response body
REQUEST_TIMEOUT_SECONDS = 120
# Total time for server reconnection
MAX_WAIT_TIME_IN_SECONDS=240

CONTENT_TYPE_JSON='application/json'
CONTENT_TYPE_XML='application/xml'
CONTENT_TYPE_OCTET='application/octet-stream'
LB_GUI_PORT = '443'
LB_API_PORT = '4443'
APISVC_PORT = '8443'

_headers = {'Content-Type': 'application/json', 'ACCEPT': 'application/json,text/html,application/octet-stream'}
_ipaddr = None
_port = LB_API_PORT

class ServiceNotAvailableError(Exception):
    def __init__(self, value):
        self.value = value
    def __str__(self):
        return repr(self.value)

        # Define the exceptions

class LoginError(Exception):
    def __init__(self, msg, code):
        self.msg = msg
        self.code = code

class Bourne:
    _DEFAULT_HEADERS = { 'Content-Type': 'application/json',
            'ACCEPT': 'application/json,application/xml,text/html,application/octet-stream' }

    def __init__(self):
        self._reset_headers()

    def _reset_headers(self):
        self._headers = copy.deepcopy(Bourne._DEFAULT_HEADERS)

    # decorator to reset headers to default
    # use this if an api changes _headers
    def resetHeaders(func):
        def inner_func(self, *args, **kwargs):
            try:
                return func(self, *args, **kwargs)
            finally:
                self._reset_headers()
        return inner_func

    # This method is responsible for setting the ip address and port that will be
    # used to connect to the backend storageos services.
    # Need to be a bit smarter when it comes to figuring out what port to set.
    # There are 3 situations we need to test for:
    #   1. the port parameter is 4443 and the BOURNE_PORT env. variable is not set
    #   2. the port parameter is 4443 and the BOURNE_PORT env. variable is set
    #   3. the port parameter is not 4443
    # Cases #1 & #2 are mainly about connecting to the APISvc.  If the BOURNE_PORT env.
    # variable is set, then use the BOURNE_PORT variable.  Case #3 is about
    # connecting to something other than the APISvc. In that case, just use the supplied
    # port parameter.
    def connect(self, ipaddr, port = LB_API_PORT):
        self._ipaddr = ipaddr

        if str(port) == LB_API_PORT:
            if len(PORT) > 0:
                self._port = PORT
            else:
                self._port = port
        else:
            self._port = port

    def test_firewall(self, ipaddr):
        testfirewall=os.getenv('TEST_APPLIANCE', 'yes')
        if (testfirewall == 'yes'):
            self.test_firewall_port(ipaddr, 9160, False)
            self.test_firewall_port(ipaddr, 9083, True)
            self.test_firewall_port(ipaddr, 9998, True)

    # helper function to test that the provided port is open or closed.
    # If the toBeOpen parameter is True, this function will throw an exception if that port is closed by firewall.
    # If the toBeOpen parameter is False, this function will throw an exception if that port is open by firewall.
    def test_firewall_port(self, ipaddr, port, toBeOpen):
        fwMessage='To disable firewall test, use the environment variable TEST_APPLIANCE=no: \n' +  \
                  '    TEST_APPLIANCE=no ./sanity <bourne_ip> security'
        throwException=False
        errorMessage=""

        ipaddrLocal = re.sub('[\[\]]', '',ipaddr)
        try:
            timeout=5
            session = telnetlib.Telnet(ipaddrLocal, port, timeout)
            if (not (toBeOpen)):
                errorMessage="The following port is open, but shoud be closed by firewall: " + str(port)
                throwException=True
        except:
            if (toBeOpen):
                errorMessage="The following port is closed, but shoud be open by firewall: " + str(port)
                throwException=True
        if(throwException):
           print (errorMessage)
           print (fwMessage)
           raise Exception(errorMessage)

    #
    # This function handles the login request flow when a request is submitted directly to the APISvc.  That means a request
    # has been sent directly to:  https://[host]:8443/path/to/svc?[params].  In this situation, the APISvc has a servlet
    # filter that examines the requests header for an authentication token.  If the token is not found, the request is
    # redirected to the Authsvc for authorization/authentiation.  This function follows the steps required to properly
    # authenticate the User so that their original APISvc request can continue.
    #
    # param url - the URL to submit to the backend storageos services.
    # param user - username to log in with
    # param password - the password to log in with
    # param cookiejar - structure to store cookie information sent from the backend storageos services.
    # return - the response structure after the User has been properly authenticated.
    # exception - will be thrown if any errors occur during the login process.
    #
    def _apisvc_login(self, url, user, password, cookiejar):
        login_response = requests.get(url, headers=self._headers, verify=False, cookies=cookiejar, allow_redirects=False)

        # If we get a redirect we are working with a non load-balanced environment.  The original behaviour was to send
        # the /login request to the APISvc.  A servlet filter in front of that service would capture that /login request and
        # redirect the request to the AuthSvc.
        if(login_response.status_code == SEC_REDIRECT):
            # Pull the requested location from the header
            location = login_response.headers['Location']
            # Header doesn't contain original request location, throw error
            if(not location):
                raise LoginError('The redirect location of the authentication service is not provided', login_response.status_code)

            # Make the second request
            # The expectation is that the second request will go to the authsvc, which will then prompt us for our credentials ( HTTP 401 )
            login_response = requests.get(location, headers=self._headers, verify=False, cookies=cookiejar, allow_redirects=False)
            if(not login_response.status_code == requests.codes['ok']):
                if(not login_response.status_code == requests.codes['unauthorized']):
                    print 'ERROR: The authentication service failed to ask for credentials (401) to authenticate the user'
                    raise LoginError('The authentication service failed to reply with 401', login_response.status_code)

                # We got the expected HTTP 401 response code, now provide the credentials
                # Again, the request to /login should get redirected to the AuthSvc
                login_response = requests.get(location, headers=self._headers, auth=(user,password), verify=False, cookies=cookiejar, allow_redirects=False)
                if(not login_response.status_code == SEC_REDIRECT):
                    raise LoginError('The authentication service failed to authenticate the user', login_response.status_code)

        # If we don't get a 302 ( redirect ) after the first request to /login
        # then something is wrong.  Report the HTTP response code to the User and throw an error.
        # This will stop the execution.
        else:
            message = '''ERROR: The first request to login page does not redirect/forwarded to the authentication service.
                         ERROR (cont): Expecting HTTP 302 status code
                         ERROR (cont): Are you running against an appliance with security disabled?
                         ERROR (cont): Try with setting BOURNE_SECURITY_DISABLED set to 1.'''
            raise LoginError(message, login_response.status_code)

        return login_response

    #
    # This function handles the login request flow when a request is submitted through a Load-Balancer.  That means a request
    # has been sent to:  https://[host]:4443/path/to/svc?[params].  In this situation, the Load-Balancer forwards the request off
    # to the APISvc.  The APISvc has a security servelt filter that examines the requests header for an authentication token.
    # If the token is not found, the request is redirected to the Authsvc ( via the Load-Balancer ) for authorization/authentiation.
    # This function follows the steps required to properly authenticate the User so that their original APISvc request can continue.
    #
    # param url - the URL to submit to the backend storageos services.
    # param user - username to log in with
    # param password - the password to log in with
    # param cookiejar - structure to store cookie information sent from the backend storageos services.
    # return - the response structure after the User has been properly authenticated.
    # exception - will be thrown if any errors occur during the login process.
    #
    def _lb_login(self, url, user, password, cookiejar):
        login_response = requests.get(url, headers=self._headers, verify=False, cookies=cookiejar, allow_redirects=False)

        # If we make a request and we get a 401, that means we are running within a Load-Balanced deployment.
        # Reason we know this:  because the load-balancer is configured to forward all requests to /login context
        # directly to the AuthSvc.  This bypasses the redirection in a non load-balanced configuration.
        if(login_response.status_code == requests.codes['unauthorized']):
            # Now provide the credentials
            login_response = requests.get(url, headers=self._headers, auth=(user,password), verify=False, cookies=cookiejar, allow_redirects=False)
        # If we don't get a 401 ( unauthorized ) after the first request to /login
        # then something is wrong.  Report the HTTP response code to the User and throw an error.
        # This will stop the execution.
        else:
            message = '''ERROR: The first request to login page does not redirect/forwarded to the authentication service.
                         ERROR (cont): Expecting HTTP 401 status code
                         ERROR (cont): Are you running against an appliance with security disabled?
                         ERROR (cont): Try with setting BOURNE_SECURITY_DISABLED set to 1.'''
            raise LoginError(message, login_response.status_code)

        if(login_response.status_code != requests.codes['ok']):
                raise LoginError(' Error: '+login_response.text, login_response.status_code)

        return login_response

    #
    #
    #
    def login(self, user, password):
        if SKIP_SECURITY == '1':
            return
        self._reset_headers()
        scheme = 'https://'
        ipaddr = self._ipaddr
        port=PORT

        cookiejar = cookielib.LWPCookieJar()
        if USE_SSL == '0':
            return

        loginURL = scheme+ipaddr+':'+port+'/login'

        startTime = time.time()
        while True:
            try:
                if(port == APISVC_PORT):
                    login_response = self._apisvc_login(loginURL, user, password, cookiejar)
                elif(port == LB_API_PORT):
                    login_response = self._lb_login(loginURL, user, password, cookiejar)
                else:
                    print 'ERROR: Invalid port specified (port = ' + str(port) + ')'
                    raise Exception('Invalid port: ' + str(port) + '.')
                break
            except LoginError as e:
                if(e.code <=500 or e.code >= 600):
                    print 'Login failed with non-retryable error'
                    raise
                elif(time.time()>(startTime+MAX_WAIT_TIME_IN_SECONDS)):
                    print 'Login retry timed out'
                    raise
                else:
                    print 'The login failed with code: ' + repr(e.code) +' Retrying . . .'
                    time.sleep(5)

        authToken = login_response.headers[SEC_AUTHTOKEN_HEADER]
        if (not authToken):
            raise Exception('The token is not generated by authentication service')

        newHeaders = self._headers
        newHeaders[SEC_AUTHTOKEN_HEADER] = authToken

        # Make the final call to get the page with the token
        login_response = requests.get(loginURL, headers=newHeaders, verify=False, cookies=cookiejar, allow_redirects=False)

        if(login_response.status_code != requests.codes['ok']):
            raise Exception('Login failure code: ' + str(login_response.status_code) + ' Error: ' + login_response.text)

        # Token file handling
        if (os.path.exists(SEC_TOKEN_FILE)):
            os.remove(SEC_TOKEN_FILE)
        tokenFile = open(SEC_TOKEN_FILE , "w")
        tokenFile.write(authToken)
        tokenFile.close()

        # Cookie handling
        for cookie in login_response.cookies:
            cookiejar.set_cookie(cookie)

        if(os.path.exists(COOKIE_FILE)):
                os.remove(COOKIE_FILE)
        cookiejar.save(COOKIE_FILE, ignore_discard=True, ignore_expires=True);


    def pretty_print_json(self, jsonObj):
        print json.dumps(jsonObj, sort_keys=True, indent=4)

    def pretty_print_xml(self, etNode):
        #reader = Sax2.Reader()
        #docNode = reader.fromString(ElementTree.tostring(etNode))
        #tmpStream = StringIO()
        #PrettyPrint(docNode, stream=tmpStream)
        #print tmpStream.getvalue()
        print ET.tostring(etNode)

    # req_timeout: Number of seconds a request should wait for response. It only effects the connection process itself, not the downloading of the response body
    def __run_request(self, method, uri, body, req_timeout, headers=None):
        scheme = 'https://'
        ipaddr = self._ipaddr
        cookies=None
        port = str(self._port)
        if uri == URI_KICKSTART:
            scheme = 'https://'
            port   = '6443'
        elif USE_SSL == '0':
            scheme = 'http://'
            if port == OBJCTRL_PORT:
                port   = OBJCTRL_INSECURE_PORT
            elif port == S3_PORT:
                port = S3_INSECURE_PORT
            elif port == ATMOS_PORT:
                port = ATMOS_INSECURE_PORT
            elif port == SWIFT_PORT:
                port = SWIFT_INSECURE_PORT
            elif port == GEO_PORT:
                port = GEO_INSECURE_PORT
            else:
                port   = '8080'
        # HACK, to remove
        if port == GEO_PORT:
            scheme = 'http://'

        url = scheme+ipaddr+':'+port+uri
        cookiejar = cookielib.LWPCookieJar()
        if SKIP_SECURITY != '1':
            if (not os.path.exists(COOKIE_FILE)):
                raise Exception(COOKIE_FILE + ' : Cookie not found : Please authenticate user')
            if (not os.path.isfile(COOKIE_FILE)):
                raise Exception(COOKIE_FILE + ' : Not a cookie file')
            cookiejar.load(COOKIE_FILE, ignore_discard=True, ignore_expires=True)
        newHeaders = copy.deepcopy(self._headers)
        if(headers != None):
            for hdr in headers.iterkeys():
                newHeaders[hdr] = headers[hdr]

        newHeaders = self.update_headers(newHeaders)

        startTime = time.time()
        response=None
        while True:
            try:
                if method == 'POST':
                    if(BOURNE_DEBUG == '1'):
                        print 'POST: ' + url
			if (body is not None):
			   try:
                              self.pretty_print_json(cjson.decode(body))
			   except:
                              try:
			         print 'Body: ' + body;
                              except:
			         print 'No Body'
                        else:
			   print 'No Body'
                    response = requests.post(url,data=body,headers=newHeaders, verify=False, cookies=cookiejar, timeout=req_timeout)
                elif method == 'PUT':
                    if(BOURNE_DEBUG == '1'):
                        print 'PUT: ' + url
 			try:
                           self.pretty_print_json(cjson.decode(body))
			except:
                          if (body):
			      print 'Body: ' + body;
                    response = requests.put(url,data=body,headers=newHeaders, verify=False, cookies=cookiejar, timeout=req_timeout)
                elif method == 'DELETE':
                    if(BOURNE_DEBUG == '1'):
                        print 'DELETE: ' + url
                    response = requests.delete(url,headers=newHeaders,verify=False, cookies=cookiejar, timeout=req_timeout)
                elif method == 'HEAD':
                    if(BOURNE_DEBUG == '1'):
                        print 'HEAD: ' + url
                    response = requests.head(url,headers=newHeaders,verify=False,cookies=cookiejar, timeout=req_timeout)
                elif method == 'OPTIONS':
                    if(BOURNE_DEBUG == '1'):
                        print 'OPTIONS: ' + url
                    response = requests.options(url,headers=newHeaders,verify=False,cookies=cookiejar, timeout=req_timeout)
                elif method == 'GET':
                    if(BOURNE_DEBUG == '1'):
                        print 'GET ' + url
                        print 'Headers', newHeaders
                    response = requests.get(url,headers=newHeaders,verify=False, cookies=cookiejar, timeout=req_timeout)
                elif method == 'GET-stream':
                    if(BOURNE_DEBUG == '1'):
                        print 'GET ' + url
                        print 'Headers', newHeaders
                    response = requests.get(url,headers=newHeaders,verify=False, cookies=cookiejar, timeout=req_timeout, stream=True)
                else:
                    raise Exception("Unsupported method:", method)

                if BOURNE_DEBUG == '1':
                    try:
            		print 'Headers: ', newHeaders
                        print 'Response code ' + str(response.status_code)
                        print 'Response '
                        self.pretty_print_json(cjson.decode(response.text))
                    except:
                        print 'Exception printing debug output'
            except requests.exceptions.Timeout:
                # continue with retry
                if ((startTime+MAX_WAIT_TIME_IN_SECONDS)<time.time()):
                    raise
                else:
                    time.sleep(3)
                    continue
            except:
                raise
            if response.status_code<=500 or response.status_code>=600 or (startTime + MAX_WAIT_TIME_IN_SECONDS) < time.time():
                return response
            else:
                print 'The return status is ' + str(response.status_code) + ', retry'
                time.sleep(3)

    def __json_decode(self, rsp):
        if (not rsp):
            print 'empty rsp'
            return ''

        try:
            return cjson.decode(str(rsp))
        except:
            raise Exception('Response JSON decode failure. RSP = ' + rsp)

    def __xml_decode(self, rsp):
        if not rsp:
            return ''
        try:
            return ET.fromstring(str(rsp))
        except:
            raise Exception('Response XML decode failure. Response= '+rsp)

    def update_headers(self, currentHeaders):
        if SKIP_SECURITY == '1':
	    return currentHeaders
        # Get the saved token
        if (not os.path.exists(SEC_TOKEN_FILE)):
            raise Exception(SEC_TOKEN_FILE + ' : Token file not found : Please authenticate user')
        if (not os.path.isfile(SEC_TOKEN_FILE)):
            raise Exception(SEC_TOKEN_FILE + ' : The token.txt is not a regular file')
        tokenFile = open(SEC_TOKEN_FILE , "r")
        authToken = tokenFile.read()
        if (not authToken):
            raise Exception('Failed to get the saved token from token.txt')
        newHeaders = currentHeaders
        newHeaders[SEC_AUTHTOKEN_HEADER] = authToken
        return newHeaders

    def __op_status(self, obj, opid):
        status = obj['operationStatus']
        if (opid in status):
            return status.get(opid).get('status')
        raise Exception('operation status not found for ' + opid + ' ' + str(status))


    # This method calls the REST API and returns the response received, if it is success. It retries upon failure to connect to server.
    # Retry Logic: When the request takes more than REQUEST_TIMEOUT_SECONDS or when the node/service is down it throws TimeOut or ConnectionError respectively. When these exceptions are caught, it sleeps for REQUEST_TIMEOUT_SECONDS (if not already elapsed) and retries untill MAX_WAIT_TIME_IN_SECONDS is reached. If response is not received in this time, it raises exception.
    def api_check_success(self, method, uri, parms = None, qparms = None, content_type=CONTENT_TYPE_JSON, accept=CONTENT_TYPE_JSON, req_timeout=REQUEST_TIMEOUT_SECONDS):
        message_timer=0
        start_time = time.time()
        while True:
            try:
                # get current time in seconds
                request_time=time.time()
                return self.__api_check_success(method, uri, parms, qparms, content_type, accept, REQUEST_TIMEOUT_SECONDS)
            except (requests.ConnectionError, requests.Timeout, requests.exceptions.ConnectionError, requests.exceptions.Timeout, ServiceNotAvailableError) as e:
                # get elapsed time by subtracting the original request time in seconds from current time in seconds
                if time.time() < request_time + REQUEST_TIMEOUT_SECONDS:
                    time.sleep(REQUEST_TIMEOUT_SECONDS)
                # add elapsed time to the message_time
                message_timer += (time.time() - request_time)
                if time.time() > start_time + MAX_WAIT_TIME_IN_SECONDS:
                    print("re-throwing the exception since we have gone through allocated time")
                    raise
                else:
                    # only show messages every 30 seconds.
                    if message_timer > 30:
                        print("Connection error while making request: " + str(e)+". Retrying...")
                        # zero out the message timer
                        message_timer=0
                    continue


    # content_type: The MIME type of the body of the request. Ex:application/xml,application/json,application/octet-stream
    # accept: Content types that are acceptable for response. Ex:application/xml,application/json,application/octet-stream
    # req_timeout: Number of seconds a request should wait for response. It only effects the connection process itself, not the downloading of the response body
    def __api_check_success(self, method, uri, parms = None, qparms = None, content_type=CONTENT_TYPE_JSON, accept=CONTENT_TYPE_JSON, req_timeout=REQUEST_TIMEOUT_SECONDS):
        response = self.__api(method, uri, parms, qparms, content_type=content_type, accept=accept, req_timeout=req_timeout)
        if (response.status_code != 200 and response.status_code != 202):
            print response.status_code, response.reason
            print response.text
            raise Exception("Request is not successful: "+ method + " " + uri)

        try:
            if method == 'GET-stream':
                return response

            if accept == CONTENT_TYPE_JSON:
                return self.__json_decode(response.text)
            elif accept == CONTENT_TYPE_XML:
                return self.__xml_decode(response.text)
            else:
                return response
        except:
            raise Exception('Unable to decode reponse: '+response)


    def __api(self, method, uri, parms = None, qparms = None, content_type=CONTENT_TYPE_JSON, accept=CONTENT_TYPE_JSON, req_timeout = REQUEST_TIMEOUT_SECONDS, headers = None):
        body = None
        if (parms and content_type==CONTENT_TYPE_JSON):
            body = cjson.encode(parms)
        else:
            body = parms
        if (qparms):
            if( '?' in uri ):
                first = False
            else:
                uri += "?"
                first = True
            for qk in qparms.iterkeys():
                if (not first):
                    uri += '&'
                    uri += qk
                else:
                    first = False
                    uri += qk

                if (qparms[qk] != None):
                    uri += '=' + qparms[qk]

        if(content_type==None):
            del self._headers['Content-Type']
        else:
            self._headers['Content-Type'] = content_type

        self._headers['ACCEPT'] = accept
        return self.__run_request(method, uri, body, req_timeout=req_timeout, headers=headers)

    #the newly added headers param is used to explicitly set the HTTP request headers
    #for just the current API call. They take the highest precedence when in conflict
    def coreapi(self, method, uri, parms = None, qparms = None, user = None, content_type = CONTENT_TYPE_JSON, headers = None):
        return self.__api(method, uri, parms, qparms, content_type=content_type, accept=content_type, req_timeout=120, headers=headers)

    def api(self, method, uri, parms = None, qparms = None, content_type = CONTENT_TYPE_JSON, req_timeout = REQUEST_TIMEOUT_SECONDS):
        response = self.__api(method, uri, parms, qparms, content_type=content_type, accept=content_type, req_timeout=req_timeout)
        h = response.headers
        ctype = h.get('content-type',"")
        if ctype == CONTENT_TYPE_OCTET:
            return response.content
        try:
            return self.__json_decode(response.text)
        except:
            return response.text

    def api_check_error(self, method, uri, status_code, service_code, message, parms = None, qparms = None):
        response = self.__api(method, uri, parms, qparms)
        if (int(response.status_code) != status_code):
            raise Exception("Unexpected HTTP status: expected %d, actual %d" % (status_code, response.status_code))

        error = self.__json_decode(response.text)
        if (error["code"] != service_code):
            raise Exception("Unexpected ServiceCode: expected %d, actual %d" % (service_code, error["code"]))
        if (error["details"] != message):
            raise Exception("Unexpected ServiceCode detail: expected %s, actual %s" % (message, error["details"]))

    def api_sync(self, id, op, showfn, ignore_error=False):
        obj = showfn(id)
        tmo = 0

        while (self.__op_status(obj, op) == 'pending'):
            time.sleep(1)
            obj = showfn(id)
            tmo += 1
            if (tmo > API_SYNC_TIMEOUT):
                break

        if (self.__op_status(obj, op) == 'pending'):
            raise Exception('Timed out waiting for request in pending state: ' + op)

        if (self.__op_status(obj, op) == 'error' and not ignore_error):
            raise Exception('There was an error encountered: ' + str(op))

        return self.__op_status(obj, op)

    def api_sync_2(self, id, op, show_opfn, ignore_error=False):
        tmo = 0

        while True:
            try:
                obj_op = show_opfn(id, op)
                if (obj_op['state'] != 'pending'):
                    break

            except requests.exceptions.ConnectionError:
                print "ConnectionError received"

            tmo += 3
            if (tmo > API_SYNC_TIMEOUT):
                break

            time.sleep(3)

        if (type(obj_op) is dict):
            if (obj_op['state'] == 'pending'):
                raise Exception('Timed out waiting for request in pending state: ' + op)

            if (obj_op['state'] == 'error' and not ignore_error):
                raise Exception('There was an error encountered:\n' + json.dumps(obj_op, sort_keys=True, indent=4))

        return obj_op

    #
    # Handles the case where a URI requires two ID parameters, for example:
    # /block/volumes/<pid>/protection/mirrors/<sid>
    # pid = Primary ID
    # sid = Secondary ID
    #
    def api_sync_3(self, pid, sid, op, show_opfn, ignore_error=False):
        obj_op = show_opfn(pid, sid, op)
        tmo = 0
        while (obj_op['state'] == 'pending'):
            time.sleep(3)
            obj_op = show_opfn(pid, sid, op)
            tmo += 3
            if (tmo > API_SYNC_TIMEOUT):
                break

        if (type(obj_op) is dict):
            print str(obj_op)
            if (obj_op['state'] == 'pending'):
                raise Exception('Timed out waiting for request in pending state: ' + op)

            if (obj_op['state'] == 'error' and not ignore_error):
                raise Exception('There was an error encountered:\n' + json.dumps(obj_op, sort_keys=True, indent=4))

        return obj_op

    def __is_uri(self, name):
        try:
            (urn, prod, trailer) = name.split(':', 2)
            return (urn == 'urn' and prod == PROD_NAME)
        except:
            return False

    #
    # Encode HashSet as an array
    #
    def __encode_map(self, stringmap):
        entry = dict()
        for mapentry in stringmap:
            (key, value) = mapentry.split('=', 1)
            entry[key] = value
        return entry

    #
    # Encode HashSet as a list
    #
    def __encode_list(self,stringmap):
        entry = list();
        for mapentry in stringmap:
            (name, value) = mapentry.split('=', 1)
            entry.append({ 'name'  : name,
                           'value' : value })
        return entry

    def blockcos_bulkgetids(self):
        ids = self.__blockcos_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def blockcos_bulkpost(self, ids):
        return self.__blockcos_bulkget_reps(ids)

    def __blockcos_bulkget_ids(self):
        return self.api('GET', URI_BLOCKVPOOLS_BULKGET)

    def __blockcos_bulkget_reps(self, ids):
        return self.api('POST', URI_BLOCKVPOOLS_BULKGET, ids)

    def filecos_bulkgetids(self):
        ids = self.__filecos_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def filecos_bulkpost(self, ids):
        return self.__filecos_bulkget_reps(ids)

    def __filecos_bulkget_ids(self):
        return self.api('GET', URI_FILEVPOOLS_BULKGET)

    def __filecos_bulkget_reps(self, ids):
        return self.api('POST', URI_FILEVPOOLS_BULKGET, ids)

    def smisprovider_bulkgetids(self):
        ids = self.__smisprovider_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def smisprovider_bulkpost(self, ids):
        return self.__smisprovider_bulkget_reps(ids)

    def __smisprovider_bulkget_ids(self):
        return self.api('GET', URI_SMISPROVIDER_BULKGET)

    def __smisprovider_bulkget_reps(self, ids):
        return self.api('POST', URI_SMISPROVIDER_BULKGET, ids)

    def blocksnapshot_bulkgetids(self):
        ids = self.__blocksnapshot_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def blocksnapshot_bulkpost(self, ids):
        return self.__blocksnapshot_bulkget_reps(ids)

    def __blocksnapshot_bulkget_ids(self):
        return self.api('GET', URI_BLOCKSNAPSHOT_BULKGET)

    def __blocksnapshot_bulkget_reps(self, ids):
        return self.api('POST', URI_BLOCKSNAPSHOT_BULKGET, ids)

    def filesnapshot_bulkgetids(self):
        ids = self.__filesnapshot_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def filesnapshot_bulkpost(self, ids):
        return self.__filesnapshot_bulkget_reps(ids)

    def __filesnapshot_bulkget_ids(self):
        return self.api('GET', URI_FILESNAPSHOT_BULKGET)

    def __filesnapshot_bulkget_reps(self, ids):
        return self.api('POST', URI_FILESNAPSHOT_BULKGET, ids)

    def exportgroup_bulkgetids(self):
        ids = self.__exportgroup_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def exportgroup_bulkpost(self, ids):
        return self.__exportgroup_bulkget_reps(ids)

    def __exportgroup_bulkget_ids(self):
        return self.api('GET', URI_EXPORTGROUP_BULKGET)

    def __exportgroup_bulkget_reps(self, ids):
        return self.api('POST', URI_EXPORTGROUP_BULKGET, ids)

    def update_chunkinfo(self, primaryZone, message):
        self._headers['x-emc-primaryzone'] = primaryZone
        self._port = GEO_PORT
        return self.coreapi('POST', URI_CHUNKINFO, message, None, content_type=CONTENT_TYPE_OCTET)

    @resetHeaders
    def send_chunkdata(self, chunkId, primaryZone, secondaryZone, repGroup, data):

    	if len(chunkId) != 36:
    		raise Exception('wrong chunkId format (must be uuid) ' + chunkId)

        all_data = ""
        for d in data:
            d = self.getDataValueFromCli(d)
            d = self._addChecksum(d, chunkId)
            all_data += d

        length = len(all_data)

        self._headers['x-emc-primaryzone'] = primaryZone
        self._headers['x-emc-secondaryzone'] = secondaryZone
        self._headers['x-emc-dataservice-vpool'] = repGroup
        self._headers['x-emc-chunklength'] = str(length)
        # TODO: just REPO
        self._headers['x-emc-chunk-datatype'] = "0"

        self._port = GEO_PORT

        return self.coreapi('POST', URI_CHUNKDATA.format(chunkId), all_data, None, content_type=CONTENT_TYPE_OCTET)

    @resetHeaders
    def delete_chunkdata(self, chunkId, repGroup):
        self._headers['x-emc-dataservice-vpool'] = repGroup

        self._port = GEO_PORT

        return self.coreapi('DELETE', URI_CHUNKDATA.format(chunkId), None, None, content_type=CONTENT_TYPE_OCTET)
       	
    def repgroup_create(self, repGrpId, name, cos_list, isAllowAllNamespaces):
        parms = dict()
        parms['id'] = repGrpId
        parms['name'] = name
        parms['description'] = name
        parms['isAllowAllNamespaces'] = isAllowAllNamespaces
        parms['zone_mappings'] = []

        for cos in cos_list.split(','):
            pair = cos.split('.')
            zone_uuid = self.vdcinfo_query(pair[0])
            cos_uuid = self.neighborhood_query(pair[1])
            parms['zone_mappings'].append({"name" : zone_uuid, "value" : cos_uuid})

        return self.coreapi('POST', URI_REPLICATION_GROUPS, parms)

    def repgroup_add(self, repGrpId, cos_list):
        parms = dict()
        parms['mappings'] = []

        for cos in cos_list.split(','):
            pair = cos.split('.')
            zone_uuid = self.vdcinfo_query(pair[0])
            cos_uuid = self.neighborhood_query(pair[1])
            parms['mappings'].append({"name" : zone_uuid, "value" : cos_uuid})

        return self.coreapi('PUT', URI_REPLICATION_EXTEND.format(repGrpId), parms)

    def repgroup_remove(self, repGrpId, cos_list):
        parms = dict()
        parms['mappings'] = []

        for cos in cos_list.split(','):
            pair = cos.split('.')
            zone_uuid = self.vdcinfo_query(pair[0])
            cos_uuid = self.neighborhood_query(pair[1])
            parms['mappings'].append({"name" : zone_uuid, "value" : cos_uuid})

        return self.coreapi('PUT', URI_REPLICATION_COMPRESS.format(repGrpId), parms)

    def repgroup_show(self, grpId):
        o = self.api('GET', URI_REPLICATION_GROUP.format(grpId))
        if (not o):
            return None
        else:
            return o;

    def repgroup_list(self):
        o = self.api('GET', URI_REPLICATION_GROUPS)
        if (not o):
            return {};
        else:
            return o;

    # replication group name from uuid
    def repgroup_query(self, name):
        if (self.__is_uri(name)):
            return name
        rg_res = self.repgroup_list()
        rg_list = rg_res['data_service_vpool']
        for rg_iter in rg_list :
            rg = self.repgroup_show(rg_iter['id'])
            if (rg['name'] == name):
                return rg['id']
        return None

    #
    # Encode HashSet of VPOOL parameters as a list
    #
    def encode_cos(self,stringmap):
        cos_params = {'vpool_param' : self.__encode_list(stringmap)}
        return cos_params

    def cos_list(self, type):
            o = self.api('GET', URI_VPOOLS.format(type))
            if (not o):
                return {};
            return o['virtualpool']



    def cos_create(self, type, name, description, useMatchedPools,
                   protocols, numpaths, minpaths, pathsperinitiator, systemtype,
                   highavailability, haNhUri, haCosUri, activeProtectionAtHASite, metropoint, file_cos, provisionType,
                   mirrorCosUri, neighborhoods, expandable, sourceJournalSize, journalVarray, journalVpool, standbyJournalVarray, 
                   standbyJournalVpool, rp_copy_mode, rp_rpo_value, rp_rpo_type, protectionCoS,
                   multiVolumeConsistency, max_snapshots, max_mirrors, thin_volume_preallocation_percentage,
                   long_term_retention, system_type, srdf, auto_tiering_policy_name, host_io_limit_bandwidth, host_io_limit_iops,
		   auto_cross_connect):

        if (type != 'block' and type != 'file' and type != "object" ):
            raise Exception('wrong type for vpool: ' + str(type))

        parms = dict()

        if (name):
            parms['name'] = name
        if (description):
            parms['description'] = description
        if (useMatchedPools):
            parms['use_matched_pools'] = useMatchedPools
        if (protocols):
            parms['protocols'] = protocols
        if (system_type):
            parms['system_type'] = system_type

        if (numpaths):
            parms['num_paths'] = numpaths
        if (minpaths):
            parms['min_paths'] = minpaths
        if (pathsperinitiator):
            parms['paths_per_initiator'] = pathsperinitiator
        if (systemtype):
            parms['system_type'] = systemtype

        if (highavailability):
            if (highavailability == 'vplex_local'):
                parms['high_availability'] = {'type' : highavailability, 'autoCrossConnectExport' : auto_cross_connect}
            else:
                parms['high_availability'] = {'type' : highavailability, 'metroPoint' : metropoint, 'ha_varray_vpool' : {'varray' : haNhUri, 'vpool' : haCosUri, 'activeProtectionAtHASite' : activeProtectionAtHASite}, 'autoCrossConnectExport' : auto_cross_connect}

        if (file_cos):
            parms['file_vpool'] = file_cos

        if (provisionType):
            parms['provisioning_type'] = provisionType

        if (expandable):
            parms['expandable'] = expandable

        if(multiVolumeConsistency):
           parms['multi_volume_consistency'] = multiVolumeConsistency

        if (thin_volume_preallocation_percentage):
            parms['thin_volume_preallocation_percentage'] = thin_volume_preallocation_percentage;

        if (auto_tiering_policy_name):
            parms['auto_tiering_policy_name'] = auto_tiering_policy_name;
            
        if (long_term_retention):
            parms['long_term_retention'] = long_term_retention;

        if (max_snapshots or max_mirrors or protectionCoS or srdf):
            cos_protection_params = dict()

            if (type == 'block'):

		if (srdf):
                    cos_protection_srdf_params = dict()

                    copies = srdf.split(',')
                    copyEntries = []
                    for copy in copies:
                        copyParam = copy.split(":")
                        copy = dict()
                        copy['varray'] = self.neighborhood_query(copyParam[0])
                        copy['vpool'] = self.cos_query("block", copyParam[1])
			try:
			    copy['remote_copy_mode'] = copyParam[2]
			except:
                            pass

                        copyEntries.append(copy)
                    cos_protection_srdf_params['remote_copy_settings'] = copyEntries
                    cos_protection_params['remote_copies'] = cos_protection_srdf_params

                if (max_mirrors):
                    cos_protection_mirror_params = dict()
                    cos_protection_mirror_params['max_native_continuous_copies'] = max_mirrors
                    if (mirrorCosUri):
                        cos_protection_mirror_params['protection_mirror_vpool'] = mirrorCosUri
                    cos_protection_params['continuous_copies'] = cos_protection_mirror_params

                if (protectionCoS):
                    cos_protection_rp_params = dict()

                    if (sourceJournalSize or rp_copy_mode or rp_rpo_value or standbyJournalVarray or standbyJournalVpool or journalVarray or journalVpool):
                    	sourcePolicy = dict();
                    	if (sourceJournalSize):
                        	sourcePolicy['journal_size'] = sourceJournalSize
                    	if (rp_copy_mode):
                        	sourcePolicy['remote_copy_mode'] = rp_copy_mode;
                    	if (rp_rpo_value):
                        	sourcePolicy['rpo_value'] = rp_rpo_value;
                    	if (rp_rpo_type):
                        	sourcePolicy['rpo_type'] = rp_rpo_type;
			if (journalVarray):
				sourcePolicy['journal_varray'] = self.neighborhood_query(journalVarray);
			if (journalVpool):
				sourcePolicy['journal_vpool'] = self.cos_query("block", journalVpool);
			if (standbyJournalVarray):
				sourcePolicy['standby_journal_varray'] = self.neighborhood_query(standbyJournalVarray);
			if (standbyJournalVpool):
				sourcePolicy['standby_journal_vpool'] = self.cos_query("block", standbyJournalVpool);

                    	cos_protection_rp_params['source_policy'] = sourcePolicy

                    copies = protectionCoS.split(',')
                    copyEntries = []
                    for copy in copies:
                        copyParam = copy.split(":")
                        copy = dict()
                        copy['varray'] = self.neighborhood_query(copyParam[0])
                        copy['vpool'] = self.cos_query("block", copyParam[1])
			try:
			    copyPolicy = dict()
			    copyPolicy['journal_size'] = copyParam[2]
			    copyPolicy['journal_varray'] = self.neighborhood_query(copyParam[3])
			    copyPolicy['journal_vpool'] = self.cos_query("block", copyParam[4])
			    copy['policy'] = copyPolicy
			except:
                            pass

                        copyEntries.append(copy)
                    cos_protection_rp_params['copies'] = copyEntries
                    cos_protection_params['recoverpoint'] = cos_protection_rp_params

            if (max_snapshots):
                cos_protection_snapshot_params = dict()
                cos_protection_snapshot_params['max_native_snapshots'] = max_snapshots
                cos_protection_params['snapshots'] = cos_protection_snapshot_params

            parms['protection'] = cos_protection_params

        nhs = list()
        if(neighborhoods):
            for n in neighborhoods:
                nhs.append(self.neighborhood_query(n))
            parms['varrays'] = nhs

        if (host_io_limit_bandwidth):
            parms['host_io_limit_bandwidth'] = host_io_limit_bandwidth
            
        if (host_io_limit_iops):
            parms['host_io_limit_iops'] = host_io_limit_iops
            
        if (type == 'object'):
            del parms['protection']

        print "VPOOL CREATE Params = ", parms
        return self.api('POST', URI_VPOOLS.format(type), parms)

    def cos_match(self, type, useMatchedPools,
                   protocols, numpaths, highavailability, haNhUri, haCosUri, activeProtectionAtHASite, metropoint, file_cos, provisionType,
                   mirrorCosUri, neighborhoods, expandable, sourceJournalSize, journalVarray, journalVpool, standbyJournalVarray, 
                   standbyJournalVpool, rp_copy_mode, rp_rpo_value, rp_rpo_type, protectionCoS,
                   multiVolumeConsistency, max_snapshots, max_mirrors, thin_volume_preallocation_percentage,
                   system_type, srdf):

        if (type != 'block' and type != 'file' and type != "object" ):
            raise Exception('wrong type for vpool: ' + str(type))

        parms = dict()

        if (useMatchedPools):
            parms['use_matched_pools'] = useMatchedPools
        if (protocols):
            parms['protocols'] = protocols
        if (system_type):
            parms['system_type'] = system_type

        if (numpaths):
            parms['num_paths'] = numpaths

        if (highavailability):
            if (highavailability == 'vplex_local'):
                parms['high_availability'] = {'type' : highavailability}
            else:
                parms['high_availability'] = {'type' : highavailability, 'metroPoint' : metropoint, 'ha_varray_vpool' : {'varray' : haNhUri, 'vpool' : haCosUri, 'activeProtectionAtHASite' : activeProtectionAtHASite}}

        if (file_cos):
            parms['file_vpool'] = file_cos

        if (provisionType):
            parms['provisioning_type'] = provisionType

        if (expandable):
            parms['expandable'] = expandable

        if(multiVolumeConsistency):
           parms['multi_volume_consistency'] = multiVolumeConsistency

        if (thin_volume_preallocation_percentage):
            parms['thin_volume_preallocation_percentage'] = thin_volume_preallocation_percentage;

        if (max_snapshots or max_mirrors or protectionCoS or srdf):
            cos_protection_params = dict()

            if (type == 'block'):

		if (srdf):
                    cos_protection_srdf_params = dict()

                    copies = srdf.split(',')
                    copyEntries = []
                    for copy in copies:
                        copyParam = copy.split(":")
                        copy = dict()
                        copy['varray'] = self.neighborhood_query(copyParam[0])
                        copy['vpool'] = self.cos_query("block", copyParam[1])
			try:
			    copy['remote_copy_mode'] = copyParam[2]
			except:
                            pass

                        copyEntries.append(copy)
                    cos_protection_srdf_params['remote_copy_settings'] = copyEntries
                    cos_protection_params['remote_copies'] = cos_protection_srdf_params

                if (max_mirrors):
                    cos_protection_mirror_params = dict()
                    cos_protection_mirror_params['max_native_continuous_copies'] = max_mirrors
                    if (mirrorCosUri):
                        cos_protection_mirror_params['protection_mirror_vpool'] = mirrorCosUri
                    cos_protection_params['continuous_copies'] = cos_protection_mirror_params

                if (protectionCoS):
                    cos_protection_rp_params = dict()

            	    if (sourceJournalSize):
	                sourcePolicy = dict()
	                sourcePolicy['journal_size'] = sourceJournalSize
			sourcePolicy['journal_varray'] = journalVarray
			sourcePolicy['journal_vpool'] = journalVpool
			sourcePolicy['standby_journal_varray'] = standbyJournalVarray
			sourcePolicy['standby_journal_vpool'] = standbyJournalVpool
	                cos_protection_rp_params['source_policy'] = sourcePolicy

                    copies = protectionCoS.split(',')
                    copyEntries = []
                    for copy in copies:
                        copyParam = copy.split(":")
                        copy = dict()
                        copy['varray'] = self.neighborhood_query(copyParam[0])
                        copy['vpool'] = self.cos_query("block", copyParam[1])
			try:
			    copyPolicy = dict()
			    copyPolicy['journal_size'] = copyParam[2]
			    copyPolicy['journal_varray'] = self.neighborhood_query(copyParam[3])
			    copyPolicy['journal_vpool'] = self.cos_query("block", copyParam[4])
			    copy['policy'] = copyPolicy
			except:
                            pass

                        copyEntries.append(copy)
                    cos_protection_rp_params['copies'] = copyEntries
                    cos_protection_params['recoverpoint'] = cos_protection_rp_params

            if (max_snapshots):
                cos_protection_snapshot_params = dict()
                cos_protection_snapshot_params['max_native_snapshots'] = max_snapshots
                cos_protection_params['snapshots'] = cos_protection_snapshot_params

            parms['protection'] = cos_protection_params

        nhs = list()
        if(neighborhoods):
            for n in neighborhoods:
                nhs.append(self.neighborhood_query(n))
            parms['varrays'] = nhs

        return self.api('POST', URI_VPOOLS_MATCH.format(type), parms)

    #
    # Assign pools to CoS or change the max snapshots/mirrors values
    # Note that you can either do pool assignments or snapshot/mirror changes at a time
    #
    def cos_update(self, pooladds, poolrems, type, cosuri, max_snapshots, max_mirrors, expandable, use_matched, host_io_limit_bandwidth, host_io_limit_iops):
        params = dict()
        if (pooladds or poolrems):
            poolassignments = dict();
            if (pooladds):
                pool = dict()
                pool['storage_pool'] = []
                for id in pooladds:
                    pool['storage_pool'].append(id)
                poolassignments['add'] = pool
            if (poolrems):
                pool = dict();
                pool['storage_pool'] = []
                for id in poolrems:
                    pool['storage_pool'].append(id)
                poolassignments['remove'] = pool;
            params['assigned_pool_changes'] = poolassignments
            return self.api('PUT', URI_VPOOL_UPDATE.format(type, cosuri), params)

        if (max_snapshots or max_mirrors):
            vpool_protection_param = dict()
            if (max_snapshots):
                vpool_protection_snapshot_params = dict() #base class attribute
                vpool_protection_snapshot_params['max_native_snapshots'] = max_snapshots
                vpool_protection_param['snapshots'] = vpool_protection_snapshot_params

            if(max_mirrors):
                vpool_protection_mirror_params = dict()
                vpool_protection_mirror_params['max_native_continuous_copies'] = max_mirrors
                vpool_protection_param['continuous_copies'] = vpool_protection_mirror_params
            params['protection'] = vpool_protection_param
        if (expandable):
            params['expandable'] = expandable
        if (use_matched):
            params['use_matched_pools'] = use_matched
            
        if (host_io_limit_bandwidth):
            params['host_io_limit_bandwidth'] = host_io_limit_bandwidth
            
        if (host_io_limit_iops):
            params['host_io_limit_iops'] = host_io_limit_iops
            
        return self.api('PUT', URI_VPOOL_INSTANCE.format(type, cosuri), params)


    def objcos_create(self, name, description):
        parms = dict()
        if (name):
            parms['name'] = name
        if (description):
            parms['description'] = description
        parms['type'] = 'OBJ_AND_HDFS'
        return self.api('POST', URI_OBJ_VPOOL.format('object'), parms)

    def objcos_query(self, name):
        if (self.__is_uri(name)):
            return name
        cos_res = self.objcos_list()
        neighborhoods = self.neighborhood_list()
        for nb in neighborhoods:
            neighborhood = self.neighborhood_show(nb['id'])
            if (neighborhood['name'] == name):
                return neighborhood['id']
        raise Exception('bad vpool name ' + str(name) + ' of type: ' + str(type))

    def objcos_show(self, uri):
        return self.neighborhood_show(uri)

    def objcos_list(self):
        return self.neighborhood_list()

    def objcos_delete(self, uri):
        return self.api('POST', URI_OBJ_VPOOL_INSTANCE.format('object', uri) + "/deactivate" )

    def cos_delete(self, type, uri):
        if(type=='object'):
            return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_OBJ_VPOOL_INSTANCE.format(type, uri)))
        else:
            return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_VPOOL_INSTANCE.format(type, uri)))

    def cos_name(self, type, uri):
        cos = self.cos_show(type, uri)
        return cos['name']

    def cos_show(self, type, uri):
        return self.api('GET', URI_VPOOL_INSTANCE.format(type, uri))

    def cos_query(self, type, name):
        if (self.__is_uri(name)):
            return name
        cos_res = self.cos_list(type)
        for cs in cos_res :
            if(BOURNE_DEBUG=='1'):
                print "found vpool = ",cs
            try:
                cos = self.cos_show(type, cs['id'])
                if (cos['name'] == name):
                    return cos['id']
            except:
                continue
        raise Exception('bad vpool name ' + name + ' of type: ' + type)

    def cos_add_acl(self, uri, type, tenant):
        tenant = self.__tenant_id_from_label(tenant)
        self.cos_add_tenant_acl(uri, type, tenant)

    def cos_add_tenant_acl(self, uri, type, tenant_id):
	parms = {
            'add':[{
                'privilege': ['USE'],
                'tenant': tenant_id,
                }]
            }
        if(type=='object'):
            response = self.__api('PUT', URI_VPOOL_ACLS.format(type, uri), parms)
        else:
            response = self.__api('PUT', URI_VPOOL_ACLS.format(type, uri), parms)
        if (response.status_code != 200):
            print "cos_add_acl failed with code: ", response.status_code
            raise Exception('cos_add_acl: failed')

    def tenant_create(self, name, domain, key, value):
        parms = {
	        'name': name,
	        'user_mappings': [{
                    'domain': domain,
                    'attributes':[{
                        'key':key,
                        'value':[value]
                     }],
                 }]
        }
        print parms
        uri = self.tenant_getid()
        return self.api('POST', URI_TENANTS_SUBTENANT.format(uri), parms)

    def tenant_deactivate(self, subtId):
        uri = self.tenant_getid()
        return self.api('POST', URI_TENANTS_DEACTIVATE.format(subtId))

    def tenant_list(self, uri=None):
        if (not uri):
            uri = self.tenant_getid()
        o = self.api('GET', URI_TENANTS_SUBTENANT.format(uri))
        if (not o):
            return {}
        #print 'tenant_list (', uri, ') :', o
        return o['subtenant']

    def tenant_getid(self):
	o = self.api('GET', URI_TENANT)
	return o['id']

    def tenant_name(self, uri):
	t = self.tenant_show(uri)
        return t['name']

    def tenant_show(self, name):
        uri = self.__tenant_id_from_label(name)
        return self.api('GET', URI_TENANTS.format(uri))

    def tenant_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_TENANTS.format(uri)))

    def tenant_query(self, label):
        return self.__tenant_query(label)

    def tenant_bulkgetids(self):
        ids = self.__tenant_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def tenant_bulkpost(self, ids):
        return self.__tenant_bulkget_reps(ids)

    def __tenant_bulkget_ids(self):
        return self.api('GET', URI_TENANTS_BULKGET)

    def __tenant_bulkget_reps(self, ids):
        return self.api('POST', URI_TENANTS_BULKGET, ids)

    def __tenant_query(self, label):
        id = self.tenant_getid()
        subtenants = self.tenant_list(id)
        for tenant in subtenants:
            if (tenant['name'] == label):
                return tenant['id']
        return None

    def __tenant_id_from_label(self, tenant):
        uri = None
        if (not tenant):
            uri = self.tenant_getid()
        else:
            if not self.__is_uri(tenant):
                uri = self.__tenant_query(tenant)
            else:
                uri = tenant
        if (not uri):
            raise Exception('bad tenant name: ' + tenant)
        return uri

    def tenant_assign_admin(self, tenant, subject_id ):
        uri = self.__tenant_id_from_label(tenant)
        parms = {
            'role_assignment_change': {
                'add': [{
                    'role': ['TENANT_ADMIN'],
                    'subject_id': subject_id,
                }]
            }}
        response = self.__api('PUT', URI_TENANTS_ROLES.format(uri), parms)
        if (response.status_code != 200):
            print "tenant_assign_admin failed with code: ", response.status_code
            raise Exception('tenant_assign_admin: failed')

    def tenant_add_attribute(self, tenant, domain, key, value):
        uri = self.__tenant_id_from_label(tenant)
        tenant = self.api('GET', URI_TENANTS.format(uri))
        user_mappings = tenant['user_mappings']
        for user_mapping in user_mappings:
            if(domain == user_mapping['domain']):
                for attribute in user_mapping['attributes']:
                    if (key == attribute['key'] and value in attribute['value']):
                        print "tenant contains attribute " + key + "=" + value + " already"
                        return
        parms = {
                 'user_mapping_changes': {
                     'add': [{
                        'domain': domain,
                        'attributes':[{
                            'key':key,
                            'value':[value]
                         }],
                     }]}
                 }
        self.api('PUT', URI_TENANTS.format(uri), parms)

    def tenant_update_domain(self, tenantURI, domain, operation, key, value):
        if( not operation in ['add', 'remove']):
            raise Exception('type must be add or remove')
        parms = {
                 'user_mapping_changes': {
                     operation: [{
                        'domain': domain,
                        'attributes':[{
                            'key':key,
                            'value':[value],
                         }],
                     }]}
                 }
        self.api('PUT', URI_TENANTS.format(tenantURI), parms)

    def tenant_add_group(self, tenant, domain, ingroup):
        uri = self.__tenant_id_from_label(tenant)
        tenant = self.api('GET', URI_TENANTS.format(uri))
        user_mappings = tenant['user_mappings']
        for user_mapping in user_mappings:
            if(domain == user_mapping['domain']):
                for group in user_mapping['groups']:
                    if (group == ingroup):
                        print "tenant contains group mapping " + group + " already"
                        return
        parms = {
                 'user_mapping_changes': {
                     'add': [{
                        'domain': domain,
                        'groups': [ingroup],
                     }]}
                 }
        print parms
        self.api('PUT', URI_TENANTS.format(uri), parms)

    def tenant_remove_group(self, tenant, domain, ingroup):
        uri = self.__tenant_id_from_label(tenant)
        parms = {
                 'user_mapping_changes': {
                     'remove': [{
                        'domain': domain,
                        'groups': [ingroup],
                     }]}
                 }
        print parms
        self.api('PUT', URI_TENANTS.format(uri), parms)

    def tenant_update_namespace(self, tenant, namespace):
        if( 'urn:storageos:' in tenant ):
            print "URI passed in Tenant Namespace = ", tenant
            uri = tenant
        else:
            uri = self.__tenant_id_from_label(tenant)
            print "URI mapped in tenant namespace = ", uri

        parms = {
                 'namespace' : namespace
                 }
        self.api('PUT', URI_TENANTS.format(uri), parms)

    def project_list(self, tenant):
        uri = self.__tenant_id_from_label(tenant)
        o = self.api('GET', URI_PROJECTS.format(uri), None)
        if (not o):
            return {}
        return o['project']

    def project_name(self, uri):
        p = self.project_show(uri)
        return p['name']

    def project_show(self, uri):
        return self.api('GET', URI_PROJECT.format(uri))

    def project_create(self, label, tenant):
        uri = self.__tenant_id_from_label(tenant)
        self.project_create_with_tenant_id(label, uri)

    def project_create_with_tenant_id(self, label, tenant_id):
        parms = { 'name'  : label, }
        return self.api('POST', URI_PROJECTS.format(tenant_id), parms)

    def tenant_project_query(self, tenant_id, proj_name):
        projects = self.project_list(tenant_id)
        for project in projects:
            if (project['name'] == proj_name):
                return project['id']
        return None

    def project_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_PROJECT.format(uri)))

    def project_query(self, name):
        if (self.__is_uri(name)):
            return name
        label = name
        tenants = self.tenant_list(self.tenant_getid())
        ids = [self.tenant_getid()]
        for tenant in tenants:
            ids.append(tenant['id'])

        # since we are using sysadmin as user, check on root tenant and all
        # subtenants for now go in reverse order, most likely, we are in
        # the latest subtenant
        for tenant in ids:
            projects = self.project_list(tenant)
            for project in projects:
                if (project['name'] == label):
                    return project['id']
        raise Exception('bad project name: ', name)

    def project_add_acl(self, name, user):
        id = self.project_query(name)
        parms = {
            'add':[{
                'privilege': ['ALL'],
                'subject-id': user,
                }]
            }
        response = self.__api('PUT', URI_PROJECT_ACLS.format(id), parms)
        if (response.status_code != 200):
            print "project_add_acl failed with code: ", response.status_code
            raise Exception('project_add_acl: failed')

    def project_bulkgetids(self):
        ids = self.__project_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def project_bulkpost(self, ids):
        return self.__project_bulkget_reps(ids)

    def __project_bulkget_ids(self):
        return self.api('GET', URI_PROJECTS_BULKGET)

    def __project_bulkget_reps(self, ids):
        return self.api('POST', URI_PROJECTS_BULKGET, ids)

    def authn_provider_create(self, mode, url, managerdn, managerpwd, sbase,
                              sfilter, groupattr, name, domain, whitelist, searchscope,
                              groupobjectclasses, groupmemberattributes):
        whitelist_array=[]
        whitelist_array = whitelist.split(',')
        
        groupobjectclasses_array=[]
        if (groupobjectclasses != None):
            groupobjectclasses_array=groupobjectclasses.split(',') 
        
        groupmemberattributes_array=[]
        if (groupmemberattributes != None):		
            groupmemberattributes_array=groupmemberattributes.split(',') 
        parms = { 'mode' : mode,
                 'server_urls' : [ url ],
                 'manager_dn' : managerdn,
                 'manager_password' : managerpwd,
                 'search_base' :sbase,
                 'search_filter' : sfilter,
                 'group_attribute' : groupattr,
                 'name' : name,
                 'group_whitelist_values' : whitelist_array,
                 'search_scope' : searchscope,
                 'group_object_class': groupobjectclasses_array,
                 'group_member_attribute': groupmemberattributes_array}

        # skip these negative tests if security is disabled
        if (SKIP_SECURITY != '1'):
            response = self.__api('POST', URI_VDC_AUTHN_PROFILE, parms)
            if (response.status_code != 400):
                print "Failed to validate a profile without domains tag: ", response.status_code
                raise Exception("Create a bad authentication provider failed")
            parms['domains'] = [ ]
            response = self.__api('POST', URI_VDC_AUTHN_PROFILE, parms)
            if (response.status_code != 400):
                print "Failed to validate a profile without domain tag: ", response.status_code
                raise Exception("Create a bad authentication provider failed")
            # test bad content type
            response = self.__api('POST', URI_VDC_AUTHN_PROFILE, parms, None, 'bad')
            if (response.status_code != 400):
                print "Failed to test against bad content type: ", response.status_code
                raise Exception("Could not test bad content type POST")
            # test missing content type
            response = self.__api('POST', URI_VDC_AUTHN_PROFILE, parms, None, None)
            if (response.status_code != 415):
                print "Failed to test against missing content type: ", response.status_code
                raise Exception("Could not test missing content type POST")
        # now create the real authn provider
        parms['domains'] = [ domain ]
        response = self.__api('POST', URI_VDC_AUTHN_PROFILE, parms)
        if (response.status_code != 200):
            print "Failed to create authentication provider: ", response.status_code
            rawresponse = response.text
            print rawresponse
            alreadyExists = rawresponse.find("already exists") != -1 or rawresponse.find("Duplicate label") != -1
            if (alreadyExists):
                print "Domain configuration already exists.  Ignoring and continuing with the tests..."
                return response
            raise Exception("Create authentication provider failed")
        else:
            return response

    def authn_provider_show(self, uri):
        return self.api('GET', URI_VDC_AUTHN_PROFILES.format(uri))

    def authn_provider_list(self):
        response = self.api('GET', URI_VDC_AUTHN_PROFILE)
        o = response['authnprovider']
        profiles = []
        for pr in o:
            profiles.append(pr.get('id'))
        return profiles

    def authn_provider_query(self, name):
        uris = self.authn_provider_list()
        for uri in uris:
            profile = self.authn_provider_show(uri)
            if (profile['name'] == name):
                return profile['id']  # return the first that matches for now
        raise Exception('bad authn provider name : ', name)

    def authn_provider_update(self, newName):
        parms = { 'name' : newName }
        uris = self.authn_provider_list()
        for uri in uris:
            parmsDomains = { 'domain_changes' : { 'add' : [ ] }}
            profile = self.authn_provider_show(uri)
            savedProviderName = profile['name']

            parmsDomains['domain_changes']['add'] = [ 'test.dummydomain.com' ]
            response = self.__api('PUT', URI_VDC_AUTHN_PROFILES.format(uri), parmsDomains)
            # Test that you can update with a domain that is not empty (and does not exist already)
            if (response.status_code != 200):
                rawresponse = response.text
                print rawresponse
                alreadyExists = rawresponse.find("Domain") != -1 and rawresponse.find("already exists in another authentication provider") != -1
                if (alreadyExists):
                    print "Domain configuration already exists during update.  Ignoring and continuing with the tests..."
                else:
                    print "Failed to update a profile with domain tag: ", response.status_code
                    raise Exception("Failed to update the profile with domains tag")

            # Update the actual cert passed in and the provider name
            response = self.__api('PUT', URI_VDC_AUTHN_PROFILES.format(uri), parms)
            if (response.status_code != 200):
                print "Failed to update authentication provider: ", response.status_code
                raise Exception("Update authentication provider failed")
            else:
                profile = self.authn_provider_show(uri)
                if (profile['name'] == newName):
                    # Verify that after the provider update the number of provider entries stays the same
                    urisLatest = self.authn_provider_list()
                    if (len(uris) != len(urisLatest)):
                        raise Exception("After updating the provider the number of providers in the system is changed")
                    else:
                        # restore the original provider name to run the rest of the tests
                        parms['name'] = savedProviderName
                        response = self.__api('PUT', URI_VDC_AUTHN_PROFILES.format(uri), parms)
                        if (response.status_code != 200):
                            raise Exception("Failed to restore the original provider name")
                        return uri
                else:
                    raise Exception("Update authentication provider:  the update actually did not happen")

    def fileshare_list(self, project):
        puri = self.project_query(project)
        puri = puri.strip()
        results = self.fileshare_search(None, puri, None);
        resources = results['resource']
        fileshares = []
        for resource in resources:
            fileshares.append(resource['id'])
        return fileshares

    def fileshare_bulkget(self):
        ids = self.__fileshare_bulkget_ids()
        # retrieve the first 10 fileshares only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return self.__fileshare_bulkget_reps(chunk)

    def __fileshare_bulkget_ids(self):
        return self.api('GET', URI_FILESHARE_BULKGET)

    def __fileshare_bulkget_reps(self, ids):
        return self.api('POST', URI_FILESHARE_BULKGET, ids)

    def fileshare_bulkgetids(self):
        ids = self.__fileshare_bulkget_ids()
        # retrieve the first 10 fileshares only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def fileshare_bulkpost(self, ids):
        return self.api('POST', URI_FILESHARE_BULKGET, ids)

    def fileshare_show(self, uri):
        return self.api('GET', URI_FILESYSTEM.format(uri))

    def fileshare_show_task(self, fs, task):
        uri_file_task = URI_FILESYSTEM + '/tasks/{1}'
        return self.api('GET', uri_file_task.format(fs, task))


    def fileshare_create(self, label, project, neighborhood, cos, size, protocols, protection):
        parms = {
            'name'              : label,
            'varray'      : neighborhood,
            'vpool'               : cos,
            'size'              : size,
        }
        if (protocols):
            parms['protocols']  = {'protocols' : protocols}

        print 'parms: ' + str(parms)
        o = self.api('POST', URI_FILESYSTEMS_LIST, parms, {'project': project})
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)
        return  s

    def fileshare_export(self, uri, endpoints, type, perm, rootuser, protocol, comments):

        parms = {
            'type'              : type,
            'permissions'       : perm,
            'root_user'         : rootuser,
            'protocol'          : protocol,
            'endpoints'         : endpoints,
            'comments'          : comments,
        }

        o = self.api('POST', URI_FILESYSTEMS_EXPORTS.format(uri), parms)
        print 'OOO: ' + str(o) + ' :OOO'
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_export_update(self, uri, operation, securityflavor, user, roothosts, readonlyhosts, readwritehosts, subDir ):

        exportRulesparam = dict()
        exportRulesparam['secFlavor'] = securityflavor
        if(roothosts):
            exportRulesparam['rootHosts'] = roothosts
        if(readonlyhosts):
            exportRulesparam['readOnlyHosts'] = readonlyhosts
        if(readwritehosts):
            exportRulesparam['readWriteHosts'] = readwritehosts
        if(user):
            exportRulesparam['anon'] = user

        exportRulerequest = {'exportRules':[exportRulesparam]}

        if("add"== operation):
            request = {'add': exportRulerequest}
        elif("delete" == operation):
            request = {'delete' : exportRulerequest}
        else:
            request = {'modify' : exportRulerequest}
  
        o = self.api('PUT',URI_FILESYSTEMS_EXPORTS_UPDATE.format(uri), request)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_unexport(self, uri):
        url = URI_FILESYSTEMS_UNEXPORT.format(uri)
        o = self.api('DELETE', url)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_expand(self, uri, size):

        url = URI_FILESYSTEMS_EXPAND.format(uri)

        parms = {
            'new_size'      : size
        }

        o = self.api('POST', url, parms)
        self.assert_is_dict(o)
        print o
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s


    def fileshare_share(self, uri, sharename, description):

        parms = {
            'name'      : sharename,
            'description'       : description
        }

        o = self.api('POST', URI_FILESYSTEMS_SHARES.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_share_acl(self, uri, sharename, user, permission, domain, group, operation):
        cifs_acl_param = dict()
        cifs_acl_param['share_name'] = sharename
        if(permission):
            cifs_acl_param['permission'] = permission
        if(user):
            cifs_acl_param['user'] = user
        if(domain):
            cifs_acl_param['domain'] = domain
        if(group):
            cifs_acl_param['group'] = group

        acl_cifs_request = {'acl':[cifs_acl_param]}

        if("add"== operation):
            request = {'add': acl_cifs_request}
        elif("delete" == operation):
            request = {'delete' : acl_cifs_request}
        elif("modify" == operation):
            request = {'modify' : acl_cifs_request}

        o = self.api('PUT',URI_FILESYSTEMS_SHARES_ACL.format(uri,sharename),request)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_acl_show(self, uri, sharename):
        return self.api('GET', URI_FILESYSTEMS_SHARES_ACL_SHOW.format(uri,sharename))

    def fileshare_acl_delete(self, uri, sharename):
        o = self.api('DELETE', URI_FILESYSTEMS_SHARES_ACL_DELETE.format(uri, sharename))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_unshare(self, uri, sharename):

        o = self.api('DELETE', URI_FILESYSTEMS_UNSHARE.format(uri, sharename))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return s

    def fileshare_delete(self, uri, forceDelete):

	parms = {
            'forceDelete'      : forceDelete
        }
	print parms
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_FILESYSTEM.format(uri)), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.fileshare_show_task)

        return (o, s)

    def fileshare_query(self, name):
        if (self.__is_uri(name)):
            return name

        (pname, label) = name.rsplit('/', 1)
        puri = self.project_query(pname)
        puri = puri.strip()

        results = self.fileshare_search(None, puri)
        resources = results['resource']
        for resource in resources:
             if (resource['match'] == label):
                 return resource['id']
        raise Exception('bad fileshare name : ' + name )

    def fileshare_search(self, name, project=None, tag=None):
        if (self.__is_uri(name)):
            return name

        if (name):
            if (project):
                return  self.api('GET', URI_FILESYSTEMS_SEARCH_PROJECT_NAME.format(project,name))
            else:
                return  self.api('GET', URI_FILESYSTEMS_SEARCH_NAME.format(name))
        if (tag):
            return  self.api('GET', URI_FILESYSTEMS_SEARCH_TAG.format(tag))
        if (project):
            return  self.api('GET', URI_FILESYSTEMS_SEARCH_PROJECT.format(project))
     
    def fileshare_quota_task(self, id, task):
        uri_quota_task = '/vdc/tasks/{0}'
        return self.api('GET', uri_quota_task.format(task))

    def fileshare_list_quota_dir(self, uri):
        return self.api('GET', URI_FILE_QUOTA_DIR_LIST.format(uri))

    def fileshare_quota_dir_query(self, fsUri, name):
        if (self.__is_uri(name)):
            return name
        
        results = self.fileshare_list_quota_dir(fsUri)
        resources = results['quota_dir']
        for resource in resources:
             if (resource['name'] == name):
                 return resource['id']
        raise Exception('bad quota dir name : ' + name )
        
    def fileshare_create_quota_dir(self, fsuri, label, size, oplocks, sec):
        parms = {
            'name'              : label
            }
        if (size):
            parms['size']  = size
                        
        if (oplocks):
            parms['oplock']  = oplocks
        if (sec):
            parms['security_style']  = sec    
            
        o = self.api('POST', URI_FILE_QUOTA_DIR_LIST.format(fsuri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['id'], self.fileshare_quota_task)
        return  s    
    
    def fileshare_update_quota_dir(self, uri, size, oplocks, sec):
        parms = dict()
        if (size):
            parms['size']  = size
                        
        if (oplocks):
            parms['oplock']  = oplocks
        if (sec):
            parms['security_style']  = sec    
            
        o = self.api('POST', URI_FILE_QUOTA_DIR.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['id'], self.fileshare_quota_task)
        return  s    
    
    def fileshare_delete_quota_dir(self, uri, forceDelete):
        parms = dict()
        if (forceDelete):
            parms['forceDelete']  = forceDelete
                        
        o = self.api('POST', URI_FILE_QUOTA_DIR_DELETE.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['id'], self.fileshare_quota_task)
        return  s    
         
    def fileshare_show_quota_dir(self, uri):
        return self.api('GET', URI_FILE_QUOTA_DIR.format(uri))


    def snapshot_create(self, fsuri, snaplabel):
        parms = {
            'name'  : snaplabel,
        }

        o = self.api('POST', URI_FILESYSTEM_SNAPSHOT.format(fsuri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_export(self, uri, host):
        parms = {
            'host'   : host,
        }

        o = self.api('POST', URI_FILE_SNAPSHOT_EXPORTS.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_export(self, uri, endpoints, type, perm, rootuser, protocol):

        parms = {
            'type'              : type,
            'permissions'       : perm,
            'root_user'         : rootuser,
            'protocol'          : protocol,
            'endpoints'         : endpoints
        }

        o = self.api('POST', URI_FILE_SNAPSHOT_EXPORTS.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_unexport(self, uri):

        url = URI_FILE_SNAPSHOT_UNEXPORT.format(uri)

        o = self.api('DELETE', url)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s


    def snapshot_share(self, uri, sharename, description, permission):

        parms = {
            'name'      : sharename,
            'description'       : description,
            'permission'	: permission
        }

        o = self.api('POST', URI_FILE_SNAPSHOT_SHARES.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_share_acl(self, uri, sharename, user, permission, domain, group, operation):
        cifs_acl_param = dict()
        cifs_acl_param['share_name'] = sharename
        if(permission):
            cifs_acl_param['permission'] = permission
        if(user):
            cifs_acl_param['user'] = user
        if(domain):
            cifs_acl_param['domain'] = domain
        if(group):
            cifs_acl_param['group'] = group

        acl_cifs_request = {'acl':[cifs_acl_param]}

        if("add"== operation):
            request = {'add': acl_cifs_request}
        elif("delete" == operation):
            request = {'delete' : acl_cifs_request}
        elif("modify" == operation):
            request = {'modify' : acl_cifs_request}

        o = self.api('PUT',URI_FILE_SNAPSHOT_SHARES_ACL.format(uri,sharename),request)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_acl_show(self, uri, sharename):
        return self.api('GET', URI_FILE_SNAPSHOT_SHARES_ACL_SHOW.format(uri,sharename))

    def snapshot_acl_delete(self, uri, sharename):
        o = self.api('DELETE', URI_FILE_SNAPSHOT_SHARES_ACL_DELETE.format(uri,sharename))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)
        return s

    def snapshot_unshare(self, uri, sharename):

        o = self.api('DELETE', URI_FILE_SNAPSHOT_UNSHARE.format(uri, sharename))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_delete(self, uri):
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_FILE_SNAPSHOT.format(uri)))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_restore(self, uri):
        o = self.api('POST', URI_FILE_SNAPSHOT_RESTORE.format(uri))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.snapshot_show_task)

        return s

    def snapshot_share_list(self, uri):
        o = self.api('GET', URI_FILE_SNAPSHOT_SHARES.format(uri))

        if (not o):
            return {}
        else:
            return o

    def snapshot_list(self, fsuri):
        o = self.api('GET', URI_FILESYSTEM_SNAPSHOT.format(fsuri))

        if (not o):
            return {}
        else:
            return o['snapshot']

    def snapshot_show(self, uri):
        return self.api('GET', URI_FILE_SNAPSHOT.format(uri))

    def snapshot_show_task(self, snap, task):
        return self.api('GET', URI_FILE_SNAPSHOT_TASKS.format(snap,task))


    def snapshot_query(self, name, fname):
        if (self.__is_uri(name)):
            return name

        (sname, label) = name.rsplit('/', 1)
        furi = self.fileshare_query(fname)
        furi = furi.strip()

        snaps = self.snapshot_list(furi)
        for snap in snaps:
            snapshot = self.snapshot_show(snap['id'])
            if (snapshot['name'] == label):
                return snapshot['id']
        raise Exception('bad snapshot name : ' + name)

    def networksystem_create(self, label, type, devip, devport, username, password,
        smis_ip, smis_port, smisuser, smispw, smisssl):

        parms = { 'name'             : label,
                   'system_type'        : type,
                   'ip_address'         : devip,
                   'port_number'        : devport,
                   'user_name'          : username,
                   'password'          : password,
                   }
        if(smis_ip):
            parms['smis_provider_ip'] = smis_ip
        if(smis_port):
            parms['smis_port_number'] = smis_port
        if (smisuser):
            parms['smis_user_name'] = smisuser
        if (smispw):
            parms['smis_password'] = smispw
        if (smisssl):
            parms['smis_use_ssl'] = smisssl

        o = self.api('POST', URI_NETWORKSYSTEMS, parms)
        print 'OOO: ' + str(o) + ' :OOO'
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)
        return s

    def networksystem_show_task(self, device, task):
        uri_device_task = URI_NETWORKSYSTEM + '/tasks/{1}'
        return self.api('GET', uri_device_task.format(device,task))

    def networksystem_update(self, label, type, devip, devport, username, password,
        uri, smis_ip, smis_port, smisuser, smispw, smisssl):

        parms = { 'name'             : label,
                   'system_type'     : type,
                   'ip_address'      : devip,
                   'port_number'     : devport,
                   'user_name'       : username,
                   'password'        : password,
                   }
        if(smis_ip):
            parms['smis_provider_ip'] = smis_ip
        if(smis_port):
            parms['smis_port_number'] = smis_port
        if (smisuser):
            parms['smis_user_name'] = smisuser
        if (smispw):
            parms['smis_password'] = smispw
        if (smisssl):
            parms['smis_use_ssl'] = smisssl
        return self.api('PUT', URI_NETWORKSYSTEM.format(uri), parms)

    def networksystem_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_NETWORKSYSTEM.format(uri)))

    def networksystem_show(self, uri):
        return self.api('GET', URI_NETWORKSYSTEM.format(uri))

    def networksystem_discover(self, uri):
        return self.api('POST', URI_NETWORKSYSTEM_DISCOVER.format(uri))

    def networksystem_list_connections(self, uri, fabricId):
        if (fabricId):
			return self.api('GET', URI_NETWORKSYSTEM_FCENDPOINTS_FABRIC.format(uri, fabricId))
        else:
            return self.api('GET', URI_NETWORKSYSTEM_FCENDPOINTS.format(uri))

    def networksystem_physical_inventory(self, uri):
        return 'Function not supported'

    def networksystem_zonereferences(self, initiator, target):
        return self.api('GET', URI_NETWORKSYSTEM_VDCREFERENCES.format(initiator,target))

    def networksystem_query(self, name):
        if (self.__is_uri(name)):
            return name

        systems = self.networksystem_list()
        for system in systems:
            if (system['name'] == name):
                return system['id']
        print systems;
        raise Exception('bad networksystem name: ' + name)

    def networksystem_list(self):
        o = self.api('GET', URI_NETWORKSYSTEMS)
        if (not o):
            return {};
        systems = o['network_system'];
        if(type(systems) != list):
            return [systems];
        return systems;

    def networksystem_register(self, uri):
        return self.api('POST', URI_NETWORKSYSTEM_REGISTER.format(uri))

    def networksystem_deregister(self, uri):
        return self.api('POST', URI_NETWORKSYSTEM_DEREGISTER.format(uri))
	   
    def portalias_list(self, uri, fabricId):
        if (fabricId):
            return self.api('GET', URI_NETWORKSYSTEM_ALIASES_FABRIC.format(uri, fabricId))
        else:
            return self.api('GET', URI_NETWORKSYSTEM_ALIASES.format(uri))

    def portalias_create(self, uri, fabricId, aliases):
        if (not aliases):
            raise Exception( 'No aliases were provided')
        else:
            aliasesArr = aliases.split('#')
            if ( len(aliasesArr) <= 0):
                raise Exception( 'No aliases were provided')
            else:
                aliasesParam=[]
                i=0
                for alias in aliasesArr:
                    nameAddress = alias.split(',');
                    try:
                        name = nameAddress[0];
                        address = nameAddress[1];
                        aliasesParam.append({'name':name, 'address':address})
                        i+=1
                    except:
                        raise Exception('Name or address was not provided for an alias');
                    
                    
                createParam = dict()
                createParam['wwn_alias']=aliasesParam
                
                if (fabricId):
                    createParam['fabric_id']=fabricId
                
                print createParam
                o = self.api('POST', URI_NETWORKSYSTEM_ALIASES.format(uri), createParam)                    
                    
                return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)

                    
    def portalias_delete(self, uri, fabricId, aliases):
        if (not aliases):
            raise Exception( 'No aliases were provided')
        else:
            aliasesArr = aliases.split('#')
            if ( len(aliasesArr) <= 0):
                raise Exception( 'No aliases were provided')
            
            aliasesParam=[]
            i=0
            for alias in aliasesArr:
                nameAddress = alias.split(',');            
                try:
                    name = nameAddress[0]
                    
                    try:
                        address = nameAddress[1]
                    except:
                        address = '';
                        
                    aliasesParam.append({'name':name, 'address':address})
                    i+=1
                except:
                    raise Exception( 'name was not provided for an alias')
                    
                
            deleteParam = dict()
            deleteParam['wwn_alias']=aliasesParam
            
            if (fabricId):
                deleteParam['fabric_id']=fabricId
            
            print deleteParam
            o = self.api('POST', URI_NETWORKSYSTEM_ALIASES_REMOVE.format(uri), deleteParam)
            print o;
                
            return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)
        
    def portalias_update(self, uri, fabricId, aliases):
        if (not aliases):
            raise Exception( 'No aliases were provided')
        else:
            aliasesArr = aliases.split('#')
            if ( len(aliasesArr) <= 0):
                raise Exception( 'No aliases were provided')                                      
            else:
                aliasesParam=[]
                i=0
                for alias in aliasesArr:
                    nameAddress = alias.split(',');            
                    try:
                        name = nameAddress[0]
                        
                        try:
                            newAddress = nameAddress[1]
                        except:
                            newArress = ''
                        
                        try:
                            address = nameAddress[2]
                        except:
                            address = '';
                            
                        try:
                            newName = nameAddress[3]
                        except:
                            newName = ''
                            
                
                    except:
                        raise Exception('name or new_address was not provided in an alias for updating')
                        
                    aliasesParam.append({'name':name, 'address':address, 'new_address':newAddress, 'new_name':newName})
                    i+=1
                    
                updateParam = dict()
                updateParam['wwn_alias_update']=aliasesParam
                
                if (fabricId):
                    updateParam['fabric_id']=fabricId
                
                print updateParam
                o = self.api('PUT', URI_NETWORKSYSTEM_ALIASES.format(uri), updateParam)
                print o;
                    
                return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)

    def zone_list(self, uri, fabricId,zoneName, excludeMembers, excludeAliases):
        if (fabricId):
            if ( zoneName == None ):
                zoneName = ""
            return self.api('GET', URI_NETWORKSYSTEM_ZONES_QUERY.format(uri, fabricId, zoneName,excludeMembers, excludeAliases))     
        else:
            raise Exception('fabricid was not provided')

    def zone_create(self, uri, fabricId, zones):
        if (not zones):
            raise Exception( 'No zones were provided')
        elif (not fabricId):
            raise Exception( 'fabricid or vsan was not provided');
        else:
            zonesArr = zones.split('#')
            if ( len(zonesArr) <= 0):
                raise Exception( 'No zones were provided')
            else:
                zonesParam=[]
                i=0
                for zone in zonesArr:
                    zoneArr = zone.split(',');
                    try:
                        name = zoneArr[0];
                        members = zoneArr[1].split('+');
                        
                        if ( len(members) <= 0):
                            raise Exception() 
                            
                        zonesParam.append({'name':name, 'members':members})
                        i+=1
                    except:
                        raise Exception('Name or members was not provided for a zone');
                    
                    
                createParam = dict()
                createParam['san_zone']=zonesParam
                
                print createParam
                
                o = self.api('POST', URI_NETWORKSYSTEM_ZONES.format(uri,fabricId), createParam)
                return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)

    def zone_delete(self, uri, fabricId, zones):
        if (not zones):
            raise Exception( 'No zones were provided')
        elif (not fabricId):
            raise Exception( 'fabricid or vsan was not provided');
        else:
            zonesArr = zones.split('#')
            if ( len(zonesArr) <= 0):
                raise Exception( 'No zones were provided')
            else:
                zonesParam=[]
                i=0
                for zone in zonesArr:
                    zoneArr = zone.split(',');
                    try:
                        name = zoneArr[0];
                        
                        try:
                            members = zoneArr[1].split('+');
                        except:
                            members=[]
                            
                        zonesParam.append({'name':name, 'members':members})
                        i+=1
                    except:
                        raise Exception('Name was not provided for a zone');
                    
                    
                deleteParam = dict()
                deleteParam['san_zone']=zonesParam
                
                print deleteParam
                
                o = self.api('POST', URI_NETWORKSYSTEM_ZONES_REMOVE.format(uri,fabricId), deleteParam)
                return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)
                
    def zone_update(self, uri, fabricId, zones):
        if (not zones):
            raise Exception( 'No zones were provided')
        elif (not fabricId):
            raise Exception( 'fabricid or vsan was not provided');
        else:
            zonesArr = zones.split('#')
            if ( len(zonesArr) <= 0):
                raise Exception( 'No zones were provided')
            else:
                zonesParam=[]
                i=0
                for zone in zonesArr:
                    zoneArr = zone.split(',');
                    
                    try:
                        name = zoneArr[0];
                        
                        try:
                            addMembers = zoneArr[1].split('+');
                        except:
                            addMembers=[]
                            
                        try:
                            removeMembers = zoneArr[2].split('+');
                        except:
                            removeMembers=[]
                            
                        zonesParam.append({'name':name, 'add':addMembers, 'remove':removeMembers})
                        i+=1
                    except:
                        raise Exception('Name was not provided for a zone');
                    
                    
                updateParam = dict()
                updateParam['san_zone_update']=zonesParam
                
                print updateParam
                
                o = self.api('PUT', URI_NETWORKSYSTEM_ZONES.format(uri,fabricId), updateParam)
                return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)
                
    def zone_activate(self, uri, fabricId):
        if (fabricId):
            o = self.api('POST', URI_NETWORKSYSTEM_ZONES_ACTIVATE.format(uri,fabricId))
            return self.api_sync_2(o['resource']['id'], o['op_id'], self.networksystem_show_task)
        else:
            raise Exception('fabricid was not provided')
            
    def storagedevice_create(self, label, type, devip, devport, username, password,
        serialno, smis_ip, smis_port, smisuser, smispw, smisssl, uri):

        parms = {  'system_type'        : type,
                   'ip_address'         : devip,
                   'port_number'        : devport,
                   'user_name'          : username,
                   'password'           : password,
                   'name'               : label,
                   #'registration_mode'  : registrationmode,
                   #'registration_status': registrationstatus,
                   }
        if(serialno):
            parms['serial_number'] = serialno
        if(smis_ip):
            parms['smis_provider_ip'] = smis_ip
        if(smis_port):
            parms['smis_port_number'] = smis_port
        if (smisuser):
            parms['smis_user_name'] = smisuser
        if (smispw):
            parms['smis_password'] = smispw
        if (smisssl):
            parms['smis_use_ssl'] = smisssl
        o = self.api('POST', uri, parms)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.storagedevice_show_task)
        return s

    def storagedevice_discover_all(self, ignore_error):
        o = self.api('POST', URI_STORAGEDEVICE_DISCOVERALL);
        for task in o['task'] :
            s=self.api_sync_2(task['resource']['id'],task['op_id'],self.storagedevice_show_task, ignore_error)
            # self.pretty_print_json(s)
        # Check if all the storagedevices come up as 'COMPLETED'
        refs = self.storagedevice_list()
        count=0
        while True: 
            all_complete=True
            for ref in refs:
               ss = self.storagedevice_show(ref['id'])
               # self.pretty_print_json(ss)
               disc_status = ss['job_discovery_status']
               if (disc_status != 'COMPLETE'):
                  all_complete=False
            if (all_complete):
               return "discovery is completed"
            else:
               # Timeout after some time
               if (count > 180): 
                  return "Timed out waiting for disovery to complete"
               else:
                  time.sleep(10)
                  count = count + 1

        return "discovery is completed"
    
    def storagedevice_discover_namespace(self, native_guid, namespace, ignore_error):
        if (self.__is_uri(native_guid)):
            return name
        systems = self.storagedevice_list()
        for system in systems:
            try:
                storage_system = self.show_element(system['id'], URI_DISCOVERED_STORAGEDEVICE)
                if (storage_system['native_guid'] == native_guid):
                    o = self.api('POST', URI_DISCOVERED_STORAGEDEVICE_NS.format(system['id'], namespace));
                    s=self.api_sync_2(o['resource']['id'],o['op_id'],self.storagedevice_show_task, ignore_error)
                    return "discovery of namespace is completed"
            except KeyError:
                print 'no name key'
        raise Exception('bad storagedevice name: ' + native_guid)

    def storagedevice_show(self, uri):
        return self.api('GET', URI_STORAGEDEVICE.format(uri))

    def storagedevice_update(self, uri, max_resources):
        parms = dict()
        parms['max_resources'] = max_resources
        return self.api('PUT', URI_STORAGEDEVICE.format(uri), parms)

    def storagedevice_show_task(self, device, task):
        uri_device_task = URI_STORAGEDEVICE + '/tasks/{1}'
        return self.api('GET', uri_device_task.format(device,task))

    def register_element(self, provideruri, systemuri, resourceuri):
        return self.api('POST', resourceuri.format(provideruri, systemuri))

    def storagedevice_refresh(self, uri, resourceuri):
        return self.api('POST', resourceuri.format(uri))

    def storagedevice_query(self, name):
        if (self.__is_uri(name)):
            return name
        systems = self.storagedevice_list()
        for system in systems:
            try:
                if (system['name'] == name):
                    return system['id']
            except KeyError:
                print 'no name key'
        raise Exception('bad storagedevice name: ' + name)

    def storagedevice_querybyIp(self, ip):
        systems = self.discovered_storagedevice_list()
        print 'SYSTEMS: ' + str(systems) + ' :SYSTEMS'
        for system in systems:
            try:
                storage_system = self.show_element(system['id'], URI_DISCOVERED_STORAGEDEVICE)
                print("Storage_System: " + str(storage_system) + " :Storage_System" )
                if (storage_system['ip_address'] == ip):
                    return system['id']
            except KeyError:
                print 'no ip key'
        raise Exception('bad ip: ' + ip)

    def storagedevice_querybynativeguid(self, native_guid):
        systems = self.discovered_storagedevice_list()
        print 'SYSTEMS: ' + str(systems) + ' :SYSTEMS'
        for system in systems:
            try:
                storage_system = self.show_element(system['id'], URI_DISCOVERED_STORAGEDEVICE)
                print("Storage_System: " + str(storage_system) + " :Storage_System" )
                if (storage_system['native_guid'] == native_guid):
                    return system['id']
            except KeyError:
                print 'no native_guid key'
        raise Exception('bad native_guid: ' + native_guid)

    def storagedevice_list(self):
        o = self.api('GET', URI_STORAGEDEVICES)
        if (not o):
            return {};
        systems = o['storage_system'];
        if(type(systems) != list):
            return [systems];
        return systems;

    def discovered_storagedevice_list(self):
        o = self.api('GET', URI_DISCOVERED_STORAGEDEVICES)
        if (not o):
            return {};
        systems = o['storage_system'];
        if(type(systems) != list):
            return [systems];
        return systems;

    def storagesystem_bulkgetids(self):
        ids = self.__storagesystem_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def storagesystem_bulkpost(self, ids):
        return self.__storagesystem_bulkget_reps(ids)

    def __storagesystem_bulkget_ids(self):
        return self.api('GET', URI_STORAGESYSTEMS_BULKGET)

    def __storagesystem_bulkget_reps(self, ids):
        return self.api('POST', URI_STORAGESYSTEMS_BULKGET, ids)

    def storageport_bulkgetids(self):
        ids = self.__storageport_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def storageport_bulkpost(self, ids):
        return self.__storageport_bulkget_reps(ids)

    def __storageport_bulkget_ids(self):
        return self.api('GET', URI_STORAGEPORTS_BULKGET)

    def __storageport_bulkget_reps(self, ids):
        return self.api('POST', URI_STORAGEPORTS_BULKGET, ids)

    def storagepool_bulkgetids(self):
        ids = self.__storagepool_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def storagepool_bulkpost(self, ids):
        return self.__storagepool_bulkget_reps(ids)

    def __storagepool_bulkget_ids(self):
        return self.api('GET', URI_STORAGEPOOLS_BULKGET)

    def __storagepool_bulkget_reps(self, ids):
        return self.api('POST', URI_STORAGEPOOLS_BULKGET, ids)

    def transportzone_bulkgetids(self):
        ids = self.__transportzone_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def transportzone_bulkpost(self, ids):
        return self.__transportzone_bulkget_reps(ids)

    def __transportzone_bulkget_ids(self):
        return self.api('GET', URI_NETWORKS_BULKGET)

    def __transportzone_bulkget_reps(self, ids):
        return self.api('POST', URI_NETWORKS_BULKGET, ids)

    def storagepool_update(self, pooluri, nhadds, nhrems, max_resources):
        parms = dict()
        nhassignments = dict();
        parms['varray_assignment_changes'] = nhassignments
        if (nhadds):
            nh = dict();
            nh['varrays'] = nhadds
            nhassignments['add'] = nh;
        if (nhrems):
            nh = dict();
            nh['varrays'] = nhrems
            nhassignments['remove'] = nh;

        if (max_resources):
            parms['max_resources'] = max_resources
        return self.api('PUT', URI_STORAGEPOOL_UPDATE.format(pooluri), parms)

    def storagepool_register(self, systemuri, pooluri):
        return self.api('POST', URI_STORAGEPOOL_REGISTER.format(systemuri, pooluri))

    def storagepool_deregister(self, name):
        #
        # name = { pool_uri | concat(storagedevice, label) }
        #
        try:
            (sdname, label) = name.split('/', 1)
        except:
            return name

        sduri = self.storagedevice_query(sdname)

        pools = self.storagepool_list(sduri)
        for pool in pools:
            pool = self.storagepool_show(sduri, pool['id'])
            if (pool['native_guid'] == label):
                return self.api('POST', URI_STORAGEPOOL_DEREGISTER.format(pool['id']))
        raise Exception('bad storagepool name')

    def storagepool_show(self, systemuri, pooluri):
        return self.api('GET', URI_STORAGEPOOL_SHOW.format(systemuri, pooluri))

    def storagepool_query(self, name):
        #
        # name = { pool_uri | concat(storagedevice, label) }
        #
        try:
            (sdname, label) = name.split('/', 1)
        except:
            return name

        sduri = self.storagedevice_query(sdname)

        pools = self.storagepool_list(sduri)
        for pool in pools:
            pool = self.storagepool_show(sduri, pool['id'])
            if (pool['native_guid'] == label):
                return pool['id']
        raise Exception('bad storagepool name')

    def storagepool_list(self, uri):
        o = self.api('GET', URI_STORAGEPOOLS.format(uri))
        if (not o):
            return {};
        else:
            return o['storage_pool']

    def list_elements(self, uri, resourceuri):
        o = self.api('GET', resourceuri.format(uri))
        if (not o):
            return {};
        elif (isinstance(o['id'], str)):
            return [o['id']]
        else:
            return o['id']

    def list_poolsbycos(self, uri, resourceuri):
         o = self.api('GET', resourceuri.format(uri))
         return o

    # to get list of storagesystems
    def list_discovered_elements(self, resourceuri):
        o = self.api('GET', resourceuri)
        if (not o):
            return {};
        elif (isinstance(o['id'], str)):
            return [o['id']]
        else:
            return o['id']

    def show_element(self, uri, resourceuri):
        return self.api('GET', resourceuri.format(uri))

    def vdc_show(self, uri):
        return self.api('GET', URI_VDC_GET.format(uri))

    def vdc_query(self, name):
        if (self.__is_uri(name)):
            return name
        vdcs = vdc_list()
        for vdc in vdcs:
        	if vdc["name"] == name:
        		return vdc["id"]
        raise Exception('bad vdc name ' + name)

    def vdc_show_task(self, vdc, task):
        uri_vdc_task = URI_VDC_GET + '/tasks/{1}'
        result = self.api('GET', uri_vdc_task.format(vdc, task))
        if isinstance(result, str) or isinstance(result, basestring):
           raise requests.exceptions.ConnectionError("unexpected error")
        return result

    def vdc_add(self, name, endpoint, key, certificate_chain, dataEndpoint=None, cmdEndpoint=None):
        parms = {
            'name'              : name,
            'api_endpoint'      : endpoint,
            'secret_key'        : key,
            'certificate_chain' : certificate_chain,
        }

        if dataEndpoint:
        	parms['geo_data_endpoint'] = dataEndpoint
        if cmdEndpoint:
        	parms['geo_command_endpoint'] = cmdEndpoint

        print "VDC ADD Params = ", parms
        resp = self.api('POST', URI_VDC, parms, {})
        print "VDC ADD RESP = ", resp
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.vdc_show_task)
        return result

    def vdc_update(self, id, name, dataEndpoint=None, cmdEndpoint=None):
        parms = {
            'name'  : name,
        }

        if dataEndpoint:
        	parms['geo_data_endpoint'] = dataEndpoint
        if cmdEndpoint:
        	parms['geo_command_endpoint'] = cmdEndpoint

        print "VDC UPDATE Params = ", parms
        resp = self.api('PUT', URI_VDC_GET.format(id), parms, {})
        print "VDC UPDATE RESP = ", resp
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.vdc_show_task)
        return result

    def vdc_del(self, id):
        print "VDC DEL id = ", id
        resp = self.api('DELETE', URI_VDC_GET.format(id))
        print "VDC DEL RESP = ", resp
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.vdc_show_task)
        return result

    def vdc_disconnect(self, id):
        print "VDC DIS id = ", id
        resp = self.api('POST', URI_VDC_DISCONNECT_POST.format(id))
        print "VDC DIS RESP = ", resp
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.vdc_show_task)
        return result

    def vdc_reconnect(self, id):
        print "VDC REC id = ", id
        resp = self.api('POST', URI_VDC_RECONNECT_POST.format(id))
        print "VDC REC RESP = ", resp
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.vdc_show_task)
        return result

    def vdc_get_id(self, vdcname):
        vdclist = self.vdc_list()
        for eachvdc in vdclist:
            for detail in vdclist[eachvdc]:
                if(detail['name'] == vdcname):
                    return detail['id']

        return None

    def vdc_list(self):
        resp = self.api('GET', URI_VDC)
        return resp

    def vdc_get_secret_key(self):
        o = self.api('GET', URI_VDC_SECRETKEY)
        return o['secret_key']

    def vdc_get_certchain(self):
        o = self.api('GET', URI_VDC_CERTCHAIN)
        return o['chain']

    def vdc_get_local(self):
        vdclist = self.vdc_list()
        for eachvdc in vdclist:
            for detail in vdclist[eachvdc]:
            	vdc = self.vdc_show(detail['id'])
            	if vdc["local"] == True:
                	return detail['id']
        return None

    def obj_add_key_cert_pair(self, key, certificate_chain):
        parms = {
            'system_selfsigned' : False,
            'key_and_certificate' : {
                'private_key'        : key,
                'certificate_chain' : certificate_chain
                }
            }

        resp = self.api('PUT', URI_OBJ_CERT, parms, {})
        print "VDC ADD OBJ CERT = ", resp
        return resp

    def obj_generate_key_cert_pair(self, ips):
        parms = {
            'system_selfsigned' : True
        }
        if(ips != None):
            parms = {
                'system_selfsigned' : True,
                'ip_addresses' : ips
            }
        resp = self.api('PUT', URI_OBJ_CERT, parms, {})
        print "VDC ADD OBJ CERT = ", resp
        return resp

    def obj_get_certChain(self):
        o = self.api('GET', URI_OBJ_CERT)
        return o['chain']

    def tirgger_node_recovery(self):
        return self.api('POST', URI_RECOVERY)

    def get_recovery_status(self):
        return self.api('GET', URI_RECOVERY)
    
    def create_backup(self,name):
        return self.api('POST', URI_BACKUP_CREATE.format(name))

    def delete_backup(self,name):
        return self.api('DELETE', URI_BACKUP_DELETE.format(name))

    def list_backup(self):
        return self.api('GET', URI_BACKUP_LIST)
   
    def download_backup(self,name):
        return self.api('GET', URI_BACKUP_DOWNLOAD.format(name), None, None, content_type=CONTENT_TYPE_OCTET)

    def get_db_repair_status(self):
        return self.api('GET', URI_DB_REPAIR)

    def volume_list(self, project):
        puri = self.project_query(project)
        puri = puri.strip()
        results = self.volume_search(None, puri)
        return results['resource']

    def volume_bulkget(self):
        ids = self.__volume_bulkget_ids()
        # retrieve the first 10 volumes only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return self.__volume_bulkget_reps(chunk)

    def __volume_bulkget_ids(self):
        return self.api('GET', URI_VOLUME_BULKGET)

    def __volume_bulkget_reps(self, ids):
        return self.api('POST', URI_VOLUME_BULKGET, ids)

    def volume_bulkgetids(self):
        ids = self.__volume_bulkget_ids()
        # retrieve the first 10 volumes only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def volume_bulkpost(self, ids):
        return self.api('POST', URI_VOLUME_BULKGET, ids)

    def get_ids_chunk(self, ids, start, end):
        chunk = { 'id' : ids[start:end] }
        #self.pretty_print_json(chunk)
        return chunk

    def volume_show(self, uri):
        return self.api('GET', URI_VOLUME.format(uri))

    def volume_show_task(self, vol, task):
        uri_vol_task = URI_VOLUME + '/tasks/{1}'
        return self.api('GET', uri_vol_task.format(vol, task))

    def volume_exports(self, uri):
        return self.api('GET', URI_VOLUMES_EXPORTS.format(uri))

    def volume_create(self, label, project, neighborhood, cos, size, isThinVolume, count, protocols, protection, consistencyGroup):
        parms = {
            'name'              : label,
            'varray'      : neighborhood,
            'project'           : project,
            'vpool'               :  cos,
            'size'              : size,
            'count'             : count,
        }

        if (protocols):
            parms['protocols'] = {'protocol' : protocols}

        if (consistencyGroup != ''):
            parms['consistency_group'] =  consistencyGroup

        print "VOLUME CREATE Params = ", parms
        resp = self.api('POST', URI_VOLUME_LIST, parms, {})
        print "RESP = ", resp
        self.assert_is_dict(resp)
        tr_list = resp['task']
        #print 'DEBUG : debug operation for volume : ' + o['resource']['id']
        print tr_list
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
            result.append(s)
        return result

    def volume_add_journal(self, copyName, project, neighborhood, cos, size, count, consistencyGroup):
        parms = {
            'name' : copyName,
            'varray' : neighborhood,
            'project' : project,
            'vpool' :  cos,
            'size' : size,
            'count' : count,
	    'consistency_group' : consistencyGroup,
        }        

        print "ADD JOURNAL Params = ", parms
        resp = self.api('POST', URI_ADD_JOURNAL, parms, {})
        print "RESP = ", resp
        self.assert_is_dict(resp)
        tr_list = resp['task']
        #print 'DEBUG : debug operation for volume : ' + o['resource']['id']
        print tr_list
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
            result.append(s)
        return result

    def volume_full_copy(self, label, sourceVolume, count, createInactive):
        parms = {
            'name' : label,
            'count': count,
            'create_inactive': createInactive
        }
        resp = self.api('POST', URI_VOLUME_FULL_COPY.format(sourceVolume), parms, {})
        self.assert_is_dict(resp)
        tr_list = resp['task']
        #print 'DEBUG : debug operation for volume : ' + o['resource']['id']
        print tr_list
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
            result.append(s)
        return result

    def volume_full_copy_resync(self, fullCopyVolume):
        resp = self.api('POST', URI_FULL_COPY_RESYNC.format(fullCopyVolume), {}, {})
        self.assert_is_dict(resp)
        tr_list = resp['task']
        print tr_list
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
            result.append(s)
        return result

    def volume_full_copy_restore(self, fullCopyVolume):
        resp = self.api('POST', URI_FULL_COPY_RESTORE.format(fullCopyVolume), {}, {})
        self.assert_is_dict(resp)
        tr_list = resp['task']
        print tr_list
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
            result.append(s)
        return result

    def volume_activate(self, sourceVolume, fullCopyVolume):
        resp = self.api('POST', URI_VOLUME_FULL_COPY_ACTIVATE.format(sourceVolume, fullCopyVolume), {}, {})
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.volume_show_task)
        return result

    def volume_full_copy_check_progress(self, sourceVolume, fullCopyVolume):
        resp = self.api('POST', URI_VOLUME_FULL_COPY_CHECK_PROGRESS.format(sourceVolume, fullCopyVolume), {}, {})
        self.assert_is_dict(resp)
        return resp

    def volume_detach(self, sourceVolume, fullCopyVolume):
        resp = self.api('POST', URI_VOLUME_FULL_COPY_DETACH.format(sourceVolume, fullCopyVolume), {}, {})
        self.assert_is_dict(resp)
        result = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.volume_show_task)
        return result

    def volume_full_copies(self, uri):
        return self.api('GET', URI_VOLUME_FULL_COPY.format(uri))

    def volume_change_cos(self, uri, cos_uri, cg_uri):
        parms = {
            'vpool' : cos_uri,
            'consistency_group' : cg_uri
        }
        tr = self.api('PUT', URI_VOLUME_CHANGE_VPOOL.format(uri), parms, {})

        self.assert_is_dict(tr)
        result = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
        return result

    def volume_change_cos_matches(self, uri):
        tr = self.api('GET', URI_VOLUME_CHANGE_VPOOL_MATCH.format(uri))
        return tr

    def volume_change_nh(self, uri, nh_uri):
        parms = {
            'varray' : nh_uri,
        }
        tr = self.api('PUT', URI_VOLUME_CHANGE_VARRAY.format(uri), parms, {})
        self.assert_is_dict(tr)
        result = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
        return result

    def volume_expand(self, uri, size):
        parms = {
            'new_size'          : size,
        }

        tr = self.api('POST', URI_VOLUME_EXPAND.format(uri), parms)
        self.assert_is_dict(tr)
        result = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
        return result

    def volume_change_link(self, uri, operation, copy_uri, type):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['copyID'] = copy_uri
        copy['type'] = type

        copy_entries.append(copy)
        copies_param['copy'] = copy_entries

        o = self.api('POST', URI_VOLUME_CHANGE_LINK.format(uri) + "/" + operation, copies_param)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['task'][0]['resource']['id'], o['task'][0]['op_id'], self.volume_show_task)
        m = s['message']
        return (s, m)

    def volume_verify(self, uri, field, value):
        o = self.api('GET', URI_VOLUME.format(uri))
        self.assert_is_dict(o)
        foundValue = 'N/A';
        if field == 'personality':
           personality = 'N/A'
           if 'protection' in o.keys():
              if 'recoverpoint' in o['protection'].keys():
                 personality = o['protection']['recoverpoint']['personality'];
           if 'srdf' in o['protection'].keys():
              personality = o['protection']['srdf']['personality'];
           if personality == value:
              return;

        if field in o.keys():
           foundValue = o[field];
           if o[field] == value:
              return;
        elif value == "none":
           return;

        print 'ERROR: Volume field FAILED Verfication: ' + field + ' IS: ' + foundValue + ', SHOULD BE: ' + value;
        return -1;

    def volume_delete(self, uri, wait, vipronly):
        s = ""
        m = ""
        posturi = URI_RESOURCE_DEACTIVATE.format(URI_VOLUME.format(uri))
        if (vipronly):
            posturi = posturi + '?type=VIPR_ONLY'
        o = self.api('POST', posturi)
        if (wait):
           self.assert_is_dict(o)
           sync = self.api_sync_2(o['resource']['id'], o['op_id'], self.volume_show_task)
           s = sync['state']
           m = sync['message']
        return (o, s, m)

    def volume_multi_delete(self, uris, wait, vipronly):
        params = {}
        ids = []
        if (type(uris) is list):
            for u in uris:
                ids.append(u)
        else:
            ids.append(uris)
        params['id'] = ids
        s = ""
        m = ""
        posturi = URI_VOLUMES_DEACTIVATE
        if (vipronly):
            posturi = posturi + '?type=VIPR_ONLY'
        o = self.api('POST', posturi, params)
        if (wait):
            self.assert_is_dict(o)
            tr_list = o['task']
            for tr in tr_list:
                sync = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
                s = sync['state']
                m = sync['message']
        return (o, s, m)

    def volume_query(self, name):
        if (self.__is_uri(name)):
            return name

        if (len(name.split('/')) == 3):
            (voluri, uri) = self.block_mirror_query(name)
            if (uri == None):
               return self.block_snapshot_query(name)
            else:
               return uri

        (pname, label) = name.rsplit('/', 1)
        puri = self.project_query(pname)
        puri = puri.strip()

        results = self.volume_search(None, puri)
        resources = results['resource']
        for resource in resources:
             if (resource['match'] == label):
                 return resource['id']
        raise Exception('bad volume name ' + name)

    def volume_search(self, name, project=None, tag=None, wwn=None):
        if (self.__is_uri(name)):
            return name

        if (wwn):
            return  self.api('GET', URI_VOLUMES_SEARCH_WWN.format(wwn))
        if (tag):
            return  self.api('GET', URI_VOLUMES_SEARCH_TAG.format(tag))
        if (name):
            if (project):
                return  self.api('GET', URI_VOLUMES_SEARCH_PROJECT_NAME.format(project,name))
            else:
                return  self.api('GET', URI_VOLUMES_SEARCH_NAME.format(name))
        if (project):
            return  self.api('GET', URI_VOLUMES_SEARCH_PROJECT.format(project))


    def volume_name(self, uri):
        volume = self.volume_show(uri)
        return volume['name']

    def migration_create(self, volume_uri, source_system_uri, target_system_uri, vpool_uri):
        parms = {
            'volume' : volume_uri,
            'source_storage_system' : source_system_uri,
            'target_storage_system' : target_system_uri,
        }
        if (vpool_uri):
            parms['vpool'] = vpool_uri

        resp = self.api('POST', URI_MIGRATIONS, parms, {})
        self.assert_is_dict(resp)
        print resp
        s = self.api_sync_2(resp['resource']['id'], resp['op_id'], self.volume_show_task)
        return s

    def migration_query(self, name):
        if (self.__is_uri(name)):
            return name
        migrations = self.migration_list()
        for migration in migrations:
            try:
                if (migration['name'] == name):
                    return migration['id']
            except KeyError:
                print 'no name key'
        raise Exception('Bad migration name: ' + name)

    def migration_list(self):
        migrationlist = self.api('GET', URI_MIGRATIONS)
        if (not migrationlist):
            return {}
        migrations = migrationlist['block_migration']
        if (type(migrations) != list):
            return [migrations]
        return migrations

    def migration_show(self, uri):
        return self.api('GET', URI_MIGRATION.format(uri))

    #
    # Block Mirror
    #

    def block_mirror_show_task(self, volume, mirror, task):
        return self.api('GET', URI_BLOCK_MIRROR_TASKS.format(volume, mirror, task))

    def block_mirror_show(self, volume, mirror):
        return self.api('GET', URI_BLOCK_MIRRORS_READ.format(volume, mirror))

    def block_mirror_list(self, volume):
        vuri = self.volume_query(volume)
        vuri = vuri.strip()
        o = self.api('GET', URI_BLOCK_MIRRORS_LIST.format(vuri))
        print "Mirror list response: " + str(o)
        self.assert_is_dict(o)
        blkmirrors = o['mirror']
        ids = []
        if (not o):
            return {}
        else :
           if (type(blkmirrors) != list):
              blkmirrors = [blkmirrors]
           for blkmirror in blkmirrors:
               ids.append(blkmirror.get('id'))
        return ids

    def block_mirror_query(self, name):
        if (self.__is_uri(name)):
            return name

        (sname, label) = name.rsplit('/', 1)
        furi = self.volume_query(sname)
        furi = furi.strip()

        return (furi, self.block_mirror_get_id_by_name(furi,label))
        raise Exception('bad mirror name')

    def block_mirror_get_id_by_name(self, volume, name):
        vuri = self.volume_query(volume)
        vuri = vuri.strip()
        o = self.api('GET', URI_BLOCK_MIRRORS_LIST.format(vuri))
        self.assert_is_dict(o)
        blkmirrors = o['mirror']
        ids = []
        if (not o):
            return {}
        else :
           if (type(blkmirrors) != list):
              blkmirrors = [blkmirrors]
           for blkmirror in blkmirrors:
              print 'The requested name : ' + name
              if(name == blkmirror.get('name')):
                print 'The selected id : ' + blkmirror.get('id')
                return blkmirror.get('id')

    def block_mirror_pause_all(self, volume):
       copies_param = dict()
       copy = dict()
       copy_entries = []

       copy['type'] = "native"
       copy_entries.append(copy)
       copies_param['copy'] = copy_entries

       o = self.api('POST', URI_BLOCK_MIRRORS_PAUSE_ALL.format(volume), copies_param)
       self.assert_is_dict(o)
       print "MIRROR_PAUSE_RESP: " + str(o)
       s = self.api_sync_2(o['task'][0]['resource']['id'], o['task'][0]['op_id'], self.volume_show_task)
       return (o, s['state'], s['message'])

    def block_mirror_resume_all(self, volume):
       copies_param = dict()
       copy = dict()
       copy_entries = []

       copy['type'] = "native"
       copy_entries.append(copy)
       copies_param['copy'] = copy_entries

       o = self.api('POST', URI_BLOCK_MIRRORS_RESUME_ALL.format(volume), copies_param)
       self.assert_is_dict(o)
       print "MIRROR_RESUME_RESP: " + str(o)
       s = self.api_sync_2(o['task'][0]['resource']['id'], o['task'][0]['op_id'], self.volume_show_task)
       return (o, s['state'], s['message'])

    def block_mirror_deactivate(self, volume_uri, mirror_uri):
       copies_param = dict()
       copy = dict()
       copy_entries = []

       copy['type'] = "native"
       copy['copyID'] = mirror_uri
       copy_entries.append(copy)
       copies_param['copy'] = copy_entries

       resp= self.api('POST', URI_BLOCK_MIRRORS_DEACTIVATE.format(volume_uri), copies_param)
       self.assert_is_dict(resp)
       print "RESP = ", resp
       tr_list = resp['task']
       print tr_list
       result = list()
       for tr in tr_list:
          s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
          result.append(s)
       return result

    def block_mirror_attach(self, volume, label, count):

       copies_param = dict()
       copy = dict()
       copy_entries = []

       copy['name'] = label
       copy['count'] = count
       copy['type'] = "native"
       copy_entries.append(copy)
       copies_param['copy'] = copy_entries

       resp = self.api('POST', URI_BLOCK_MIRRORS_ATTACH.format(volume), copies_param)
       self.assert_is_dict(resp)
       print "RESP = ", resp
       tr_list = resp['task']
       print tr_list
       result = list()
       for tr in tr_list:
          s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
          result.append(s)
       return result

    def block_mirror_detach_all(self, volume):
       copies_param = dict()
       copy = dict()
       copy_entries = []

       copy['type'] = "native"
       copy_entries.append(copy)
       copies_param['copy'] = copy_entries

       resp = self.api('POST', URI_BLOCK_MIRRORS_DETACH_ALL.format(volume), copies_param)
       self.assert_is_dict(resp)
       print "RESP = ", resp
       tr_list = resp['task']
       print tr_list
       result = list()
       for tr in tr_list:
          s = self.api_sync_2(tr['resource']['id'], tr['op_id'], self.volume_show_task)
          result.append(s)
       return result

    #
    # Block Consistency Groups
    #

    def block_consistency_group_create(self, project, label):
        parms = {
            'name'  : label,
            'project' : project,
        }

        return self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_CREATE, parms)

    def block_consistency_group_show_task(self, group, task):
        return self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_TASKS.format(group, task))

    def block_consistency_group_show(self, group):
        return self.api('GET', URI_BLOCK_CONSISTENCY_GROUP.format(group))

    def block_consistency_group_query(self, name):
        if (self.__is_uri(name)):
            return name

        return (self.block_consistency_group_get_id_by_name(name))
        raise Exception('bad consistency group name')

    def block_consistency_group_get_id_by_name(self, name):
        print 'The requested name : ' + name
        resource = self.search('block_consistency_group', None, name, None, False)
        for consistencyGroup in resource:
             if (consistencyGroup.get('match') == name):
                 return consistencyGroup.get('id')

    def block_consistency_group_delete(self, group_uri):
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_DELETE.format(group_uri))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_consistency_group_show_task)

        return (o, s)

    def block_consistency_group_update(self, group, add, remove):
        to_add = {}
        to_remove = {}

        if (add):
            volumes = []
            for vol in add.split(','):
                volumes.append(self.volume_query(vol))
            to_add['volume'] = volumes

        if (remove):
            volumes = []
            for vol in remove.split(','):
                volumes.append(self.volume_query(vol))
            to_remove['volume'] = volumes

        update_params = {}
        update_params['add_volumes'] = to_add
        update_params['remove_volumes'] = to_remove

        o = self.api('PUT', URI_BLOCK_CONSISTENCY_GROUP.format(group), update_params)
        self.assert_is_dict(o)

        if ('op_id' in o):
            s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_consistency_group_show_task)
        else:
            s = o['details']

        return s

    def block_consistency_group_bulkgetids(self):
        ids = self.__block_consistency_group_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def block_consistency_group_bulkpost(self, ids):
        return self.__block_consistency_group_bulkget_reps(ids)

    def __block_consistency_group_bulkget_ids(self):
        return self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_BULK)

    def __block_consistency_group_bulkget_reps(self, ids):
        return self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_BULK, ids)

    def block_consistency_group_snapshot_create(self, group, label, createInactive):
        parms = {
            'name'  : label,
            'create_inactive' : createInactive,
        }
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_CREATE.format(group), parms)
        self.assert_is_dict(o)
        snapshots = o['task']
        id = ''
        task_id = ''
        if (not o):
            return {}
        else :
           if (type(snapshots) != list):
              snapshots = [snapshots]
           for snap in snapshots:
               id = snap['resource']['id']
               task_id = snap['op_id']

        s = self.api_sync_2(id, task_id, self.block_consistency_group_show_task)
        return (o, s)

    def block_consistency_group_swap(self, group, copyType, targetVarray):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['type'] = copyType
        copy['copyID'] = targetVarray
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries
        
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_SWAP.format(group), copies_param )
        self.assert_is_dict(o)
        
        if ('task' in o):
            tasks = []
            for task in o['task']:
                s = self.api_sync_2(task['resource']['id'], task['op_id'], self.block_consistency_group_show_task)
                tasks.append(s)
            s = tasks
        else:
            s = o['details']

        return s

    def block_consistency_group_failover(self, group, copyType, targetVarray):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['type'] = copyType
        copy['copyID'] = targetVarray
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries
        
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_FAILOVER.format(group), copies_param )
        self.assert_is_dict(o)
        
        if ('task' in o):
            tasks = []
            for task in o['task']:
                s = self.api_sync_2(task['resource']['id'], task['op_id'], self.block_consistency_group_show_task)
                tasks.append(s)
            s = tasks
        else:
            s = o['details']

        return s

    def block_consistency_group_failover_cancel(self, group, copyType, targetVarray):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['type'] = copyType
        copy['copyID'] = targetVarray
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries
        
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_FAILOVER_CANCEL.format(group), copies_param )
        self.assert_is_dict(o)
        
        if ('task' in o):
            tasks = []
            for task in o['task']:
                s = self.api_sync_2(task['resource']['id'], task['op_id'], self.block_consistency_group_show_task)
                tasks.append(s)
            s = tasks
        else:
            s = o['details']

        return s

    def block_consistency_group_snapshot_show_task(self, group, snapshot, task):
        return self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_TASKS.format(group, snapshot, task))

    def block_consistency_group_snapshot_show(self, group, snapshot):
        return self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT.format(group, snapshot))

    def block_consistency_group_snapshot_query(self, group, name):
        if (self.__is_uri(name)):   
            return name

        return (self.block_consistency_group_snapshot_get_id_by_name(group, name))
        raise Exception('bad consistency group snapshot name')

    def block_consistency_group_snapshot_list(self, group):
        groupId = self.block_consistency_group_query(group)
        groupId = groupId.strip()
        o = self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_LIST.format(groupId))
        self.assert_is_dict(o)
        snapshots = o['snapshot']
        ids = []
        if (not o):
            return {}
        else :
           if (type(snapshots) != list):
              snapshots = [snapshots]
           for snapshot in snapshots:
               ids.append(snapshot.get('id'))
        return ids

    def block_consistency_group_snapshot_get_id_by_name(self, group, name):
        groupid = self.block_consistency_group_query(group)
        groupid = groupid.strip()
        o = self.api('GET', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_LIST.format(groupid))
        self.assert_is_dict(o)
        snapshots = o['snapshot']
        ids = []
        if (not o):
            return {}
        else :
           if (type(snapshots) != list):
              snapshots = [snapshots]
           print 'The requested consistency group snapshot name : ' + name
           for snapshot in snapshots:
              if(name == snapshot.get('name')):
                print 'The selected id : ' + snapshot.get('id')
                return snapshot.get('id')

    def block_consistency_group_snapshot_activate(self, group, snapshot):
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_ACTIVATE.format(group, snapshot))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_consistency_group_show_task)

        return (o, s)

    def block_consistency_group_snapshot_deactivate(self, group, snapshot):
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_DEACTIVATE.format(group, snapshot))
        self.assert_is_dict(o)
        tasks = []
        for task in o['task']:
            s = self.api_sync_2(task['resource']['id'], task['op_id'], self.block_consistency_group_show_task)
            tasks.append(s)
        return tasks

    def block_consistency_group_snapshot_restore(self, group, snapshot):
        o = self.api('POST', URI_BLOCK_CONSISTENCY_GROUP_SNAPSHOT_RESTORE.format(group, snapshot))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_consistency_group_show_task)

        return (o, s)

    #
    # varray APIs
    #
    def neighborhood_list(self):
        o = self.api('GET', URI_VARRAYS)
        if (not o):
            return {};
        else:
            return o['varray']

    def neighborhood_storageports(self, uri):
        o = self.api('GET', URI_VARRAY_PORTS.format(uri))
        if (not o):
            return {};
        else:
            return o['storage_port']

    def neighborhood_name(self, uri):
        n = self.neighborhood_show(uri)
        return n['name']

    def neighborhood_show(self, uri):
        return self.api('GET', URI_VARRAY.format(uri))

    def neighborhood_create(self, label, autoSanZoning, protectionType):

        req = dict()
        req['name'] = label
        if (autoSanZoning):
             req['auto_san_zoning'] = autoSanZoning

        if (protectionType):
            req['protection_type'] = protectionType

        return self.api('POST', URI_VARRAYS, req)

    def neighborhood_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_VARRAY.format(uri)))

    def neighborhood_query(self, name):
        if (self.__is_uri(name)):
            return name

        neighborhoods = self.neighborhood_list()
        for nb in neighborhoods:
            neighborhood = self.neighborhood_show(nb['id'])
            if (neighborhood['name'] == name):
                return neighborhood['id']
        raise Exception('bad varray name ' + name)

    def neighborhood_search(self, initiator_port):
        searchstr = URI_VARRAYS + '/search?initiator_port={0}'
        return self.api('GET', searchstr.format(initiator_port))

    def neighborhood_add_acl(self, uri, tenant):
        id = self.__tenant_id_from_label(tenant)
        self.neighborhood_add_aclInternal(uri, id)

    def neighborhood_add_aclInternal(self, uri, tenantUri):
        parms = {
            'add':[{
                'privilege': ['USE'],
                'tenant': tenantUri,
                }]
        }
        response = self.__api('PUT', URI_VARRAY_ACLS.format(uri), parms)
        if (response.status_code != 200):
            print "neighborhood_add_acl failed with code: ", response.status_code
            raise Exception('neighborhood_add_acl: failed')

    def neighborhood_bulkgetids(self):
        ids = self.__neighborhood_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def neighborhood_bulkpost(self, ids):
        return self.__neighborhood_bulkget_reps(ids)

    def __neighborhood_bulkget_ids(self):
        return self.api('GET', URI_VARRAYS_BULKGET)

    def __neighborhood_bulkget_reps(self, ids):
        return self.api('POST', URI_VARRAYS_BULKGET, ids)

    #
    # Hosts
    #
    def host_bulkgetids(self):
        ids = self.__host_bulkget_ids()
        print "ids=", ids
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def host_bulkpost(self, ids):
        return self.__host_bulkget_reps(ids)

    def __host_bulkget_ids(self):
        return self.api('GET', URI_HOSTS_BULKGET)

    def __host_bulkget_reps(self, ids):
        return self.api('POST', URI_HOSTS_BULKGET, ids)

    #
    # Clusters
    #
    def cluster_bulkgetids(self):
        ids = self.__cluster_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def cluster_bulkpost(self, ids):
        return self.__cluster_bulkget_reps(ids)

    def __cluster_bulkget_ids(self):
        return self.api('GET', URI_CLUSTERS_BULKGET)

    def __cluster_bulkget_reps(self, ids):
        return self.api('POST', URI_CLUSTERS_BULKGET, ids)

    #
    # Vcenters
    #
    def vcenter_bulkgetids(self):
        ids = self.__vcenter_bulkget_ids()
        # retrieve the first 10 ids only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def vcenter_bulkpost(self, ids):
        return self.__vcenter_bulkget_reps(ids)

    def __vcenter_bulkget_ids(self):
        return self.api('GET', URI_VCENTERS_BULKGET)

    def __vcenter_bulkget_reps(self, ids):
        return self.api('POST', URI_VCENTERS_BULKGET, ids)

    #
    # Transport Zone
    #
    def transportzone_list(self, neighborhood):
        o = self.api('GET', URI_VARRAY_NETWORKS.format(neighborhood))

        if (not o):
            return {}
        else:
            return o['network']

    def transportzone_listall(self):
        o = self.api('GET', URI_NETWORKS.format())

        if (not o):
            return {}
        else:
            return o['network']

    def transportzone_show(self, uri):
        return self.api('GET', URI_NETWORK.format(uri))

    def transportzone_create(self, label, neighborhood, type):
        parms = dict()
        if (label):
            parms['name'] = label
        if (type):
            parms['transport_type'] = type
        return self.api('POST', URI_VARRAY_NETWORKS.format(neighborhood), parms)

    def transportzone_create2(self, label, type, neighborhoods, endpoints):
        parms = dict()
        parms['name'] = label
        parms['transport_type'] = type
        nhs = []
        eps = []
        if (neighborhoods):
           nhLbls = neighborhoods.split(',')
           for nhLbl in nhLbls:
              nhs.append(self.neighborhood_query(nhLbl))
        parms['varrays'] = nhs
        if (endpoints):
           eps  = endpoints.split(',')
        parms['endpoints'] = eps
        return self.api('POST', URI_NETWORKS, parms)

    def transportzone_update(self, id, label, addNeighborhoods, remNeighborhoods, addEndpoints, remEndpoints):
        parms = dict()
        parms['name'] = label
        nhChanges = {}
        if (addNeighborhoods):
           addNhs = []
           nhLbls = addNeighborhoods.split(',')
           for nhLbl in nhLbls:
              addNhs.append(self.neighborhood_query(nhLbl))
           nh = dict();
           nh['varrays'] = addNhs
           nhChanges['add'] = nh
           print str(nhChanges)
        if (remNeighborhoods):
           remNhs = []
           nhLbls = remNeighborhoods.split(',')
           for nhLbl in nhLbls:
              remNhs.append(self.neighborhood_query(nhLbl))
           nh = dict()
           nh['varrays']=remNhs
           nhChanges['remove'] = nh
        parms['varray_assignment_changes'] = nhChanges
        epChanges = {}
        if (addEndpoints):
           addEps  = addEndpoints.split(',')
           epChanges['add'] = addEps
        if (remEndpoints):
           remEps  = remEndpoints.split(',')
           epChanges['remove'] = remEps
        parms['endpoint_changes'] = epChanges
        return self.api('PUT', URI_NETWORK.format(id), parms)

    def transportzone_assign(self, id, neighborhood):
        parms =  {
            'varrays' : [ neighborhood ]
        }
        return self.api('PUT', URI_NETWORK_ASSIGN.format(id), parms)

    def transportzone_unassign(self, id):
        parms =  {
            'varrays' : [  ]
        }
        return self.api('PUT', URI_NETWORK_UNASSIGN.format(id), parms)

    def transportzone_delete(self, uri, force):
        return self.api('POST', URI_NETWORK_DEACTIVATE.format(uri, force))

    def transportzone_queryall(self, name):
        if (self.__is_uri(name)):
            return name
        tzs = self.transportzone_listall()
        #tzs = resp['network-info']
        for tz in tzs:
            if (tz['name'] == name):
                return tz['id'];
        raise Exception('bad transportzone name: ' + name)

    def transportzone_query(self, name):
        if (self.__is_uri(name)):
            return name

        try:
            (tname, label) = name.rsplit('/', 1)
        except:
            label = name

        return self.transportzone_queryall(label)

    def transportzone_add(self, uri, endpoints):
        parms = {
                    'endpoints'     : endpoints,
                    'op'            : 'add',
                }
        return self.api('PUT', URI_NETWORK_ENDPOINTS.format(uri), parms)

    def transportzone_remove(self, uri, endpoints):
        parms = {
                    'endpoints'      : endpoints,
                    'op'            : 'remove',
                }
        return self.api('PUT', URI_NETWORK_ENDPOINTS.format(uri), parms)

    def transportzone_register(self, uri):
        return self.api('POST', URI_NETWORK_REGISTER.format(uri))

    def transportzone_deregister(self, uri):
       return self.api('POST', URI_NETWORK_DEREGISTER.format(uri))

    def storageport_update(self, spuri, tzone, addvarrays, rmvarrays):
        parms = dict()

        varrayassignments = dict();
        if (addvarrays or rmvarrays):
            parms['varray_assignment_changes'] = varrayassignments
        if (addvarrays):
            addsarray = []
            adds = addvarrays.split(',')
            for add in adds:
                adduri = self.neighborhood_query(add)
                addsarray.append(adduri)
            addsdict = dict()
            addsdict['varrays'] = addsarray
            varrayassignments['add'] = addsdict
        if (rmvarrays):
            rmsarray = []
            rms = rmvarrays.split(',')
            for rm in rms:
                rmuri = self.neighborhood_query(rm)
                rmsarray.append(rmuri)
            rmsdict = dict()
            rmsdict['varrays'] = rmsarray
            varrayassignments['remove'] = rmsdict

        if (tzone):
           tzuri = self.transportzone_query(tzone)
           if (tzuri):
              parms['network'] = tzuri
        return self.api('PUT', URI_STORAGEPORT_UPDATE.format(spuri), parms)

    def storageport_register(self, systemuri, spuri):
        return self.api('POST', URI_STORAGEPORT_REGISTER.format(systemuri, spuri))

    def storageport_deregister(self, name):
        #
        # name = { port_uri | concat(storagedevice, label) }
        #
        try:
            (sdname, label) = name.split('/', 1)
        except:
            return name

        sduri = self.storagedevice_query(sdname)

        ports = self.storageport_list(sduri)
        for port in ports:
            port = self.storageport_show(sduri, port['id'])
            if (port['native_guid'] == label):
                return self.api('POST', URI_STORAGEPORT_DEREGISTER.format(port['id']))
        raise Exception('bad storageport name')

    def storageport_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_STORAGEPORT.format(uri)))

    def storageport_show(self, systemuri, porturi):
        return self.api('GET', URI_STORAGEPORT_SHOW.format(systemuri, porturi))

    def storageport_get(self, porturi):
        return self.api('GET', URI_STORAGEPORT.format(porturi))

    def storageport_query(self, name):
        #
        # name = { port_uri | concat(storagedevice, port) }
        # 
        try:
            (sdname, port) = name.split('/', 1)
        except:
            return name

        pguri = self.storagedevice_query(sdname)

        ports = self.storageport_list(pguri)
        for p in ports:
            sport = self.storageport_show(pguri, p['id'])
            if (sport['name'] == port):
                return sport['id']
        raise Exception('bad storageport name: ' + name)

    def storageport_list(self, sduri):
        o = self.api('GET', URI_STORAGEPORTS.format(sduri))
        if (not o):
            return {};
        else:
            return o['storage_port']

    #
    # SMI-S providers APIs
    #
    def smisprovider_list(self):
        o = self.api('GET', URI_SMISPROVIDERS)
        if (not o):
            return {};
        else:
            return o

    def smisprovider_show(self, uri):
        return self.api('GET', URI_SMISPROVIDER.format(uri))

    def smisprovider_show_task(self, uri, task):
        uri_smisprovider_task = URI_SMISPROVIDER + '/tasks/{1}'
        return self.api('GET', uri_smisprovider_task.format(uri, task))


    def smisprovider_create(self, name, ipaddress, port, username, password, usessl):
        req = dict()
        req['name'] = name
        req['ip_address'] = ipaddress
        req['port_number'] = port
        req['user_name'] = username
        req['password'] = password
        req['use_ssl'] = usessl

        o = self.api('POST', URI_SMISPROVIDERS, req)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.smisprovider_show_task)
        return  s

    def smisprovider_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_SMISPROVIDER.format(uri)))

    def smisprovider_query(self, name):
        if (self.__is_uri(name)):
            return name

        providers = self.smisprovider_list()
        for provider in providers['smis_provider']:
            smisprovider = self.smisprovider_show(provider['id'])
            if (smisprovider['name'] == name):
                return smisprovider['id']
        raise Exception('bad smisprovider name ' + name)


    #
    # Storage providers APIs
    #
    def storageprovider_list(self):
        o = self.api('GET', URI_STORAGEPROVIDERS)
        if (not o):
            return {};
        else:
            return o

    def storageprovider_show(self, uri):
        return self.api('GET', URI_STORAGEPROVIDER.format(uri))

    def storageprovider_show_task(self, uri, task):
        uri_storageprovider_task = URI_STORAGEPROVIDER + '/tasks/{1}'
        return self.api('GET', uri_storageprovider_task.format(uri, task))


    def storageprovider_create(self, name, ipaddress, port, username, password, usessl, interface, secondary_username, secondary_password, element_manager_url, sio_cli):
        req = dict()
        req['name'] = name
        req['ip_address'] = ipaddress
        req['port_number'] = port
        req['user_name'] = username
        req['password'] = password
        req['use_ssl'] = usessl
        req['interface_type'] = interface
        req['sio_cli'] = sio_cli
        req['secondary_username'] = secondary_username
        req['secondary_password'] = secondary_password
        req['element_manager_url'] = element_manager_url

        o = self.api('POST', URI_STORAGEPROVIDERS, req)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.storageprovider_show_task)
        return  s

    def storageprovider_delete(self, uri):
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_STORAGEPROVIDER.format(uri)))

    def storageprovider_query(self, name):
        if (self.__is_uri(name)):
            return name

        providers = self.storageprovider_list()
        for provider in providers['storage_provider']:
            storageprovider = self.storageprovider_show(provider['id'])
            if (storageprovider['name'] == name):
                return storageprovider['id']
        raise Exception('bad storageprovider name ' + name)

    #
    # export group apis
    #
    def export_group_show(self, uri):
        return self.api('GET', URI_EXPORTGROUP_INSTANCE.format(uri))

    def export_show_tasks(self, uri):
	uri_exp_task = URI_EXPORTGROUP_INSTANCE + '/tasks'
	return self.api('GET', uri_exp_task.format(uri))

    def export_show_task(self, uri, task):
	uri_exp_task = URI_EXPORTGROUP_INSTANCE + '/tasks/{1}'
	return self.api('GET', uri_exp_task.format(uri, task))

    def export_group_list(self, project):
        puri = self.project_query(project).strip()
        puri = puri.strip()
        results =  self.api('GET', URI_EXPORTGROUP_SEARCH_PROJECT.format(puri))
        resources = results['resource']
        exportgroups = []
        for resource in resources:
            exportgroups.append(resource['id'])
        return exportgroups

    def export_group_create(self, name, project, neighborhood, type, volspec, initList, hostList, clusterList, pathParam):
        projectURI = self.project_query(project).strip()
        nhuri = self.neighborhood_query(neighborhood).strip()

        parms = {
            'name' : name,
            'project' : projectURI,
            'varray' : nhuri,
        }

	# Optionally add path parameters
        if (pathParam['max_paths'] > 0):
            print 'Path parameters', pathParam
	    parms['path_parameters'] = pathParam

        # Build volume parameter, if specified
        if (volspec):
           vols = volspec.split(',')
           volentry = []
           for vol in vols:
              volparam = vol.split('+')
              volmap = dict()
              if (len(volparam) > 0):
                  volmap['id'] = self.volume_query(volparam[0])
              if (len(volparam) > 1):
                  volmap['lun'] = volparam[1]
              volentry.append(volmap)
           parms['volumes'] = volentry

        # Build initiators parameter, if specified
        if (initList):
           inits = initList.split(',')
           initEntry = []
           for initLbl in inits:
              initEntry.append(self.initiator_query(initLbl))
           parms['initiators'] = initEntry
        # Build initiators parameter, if specified
        if (hostList):
           hosts = hostList.split(',')
           hostEntry = []
           for hostLbl in hosts:
              hostEntry.append(self.host_query(hostLbl))
           parms['hosts'] = hostEntry

        # Build clusters parameter, if specified
        if (clusterList):
           clusters = clusterList.split(',')
           clusterEntry = []
           for clusterLbl in clusters:
              clusterEntry.append(self.cluster_query(clusterLbl))
           parms['clusters'] = clusterEntry
	if (type):
           parms['type'] = type
        else:
           parms['type'] = 'Initiator'

        parms['project'] = projectURI

        if(BOURNE_DEBUG == '1'):
	   print str(parms)

        o = self.api('POST', URI_EXPORTGROUP_LIST, parms)
        self.assert_is_dict(o)
        try:
	    s = self.api_sync_2(o['resource']['id'], o['op_id'], self.export_show_task)
        except:
            print o
        return (o, s)

    def export_group_update(self, groupId, addVolspec, addInitList, addHostList, addClusterList, remVolList, remInitList, remHostList, remClusterList, pathParam):
        parms = {}

	# Optionally add path parameters
        if (pathParam['max_paths'] > 0):
            print 'Path parameters', pathParam
	    parms['path_parameters'] = pathParam

        # Build volume change input, if specified
        volChanges = {}
        if (addVolspec):
           vols = addVolspec.split(',')
           volentry = []
           for vol in vols:
              volparam = vol.split('+')
              volmap = dict()
              if (len(volparam) > 0):
                  volmap['id'] = self.volume_query(volparam[0])
              if (len(volparam) > 1):
                  volmap['lun'] = volparam[1]
              volentry.append(volmap)
           volChanges['add'] = volentry
        if (remVolList):
           vols = remVolList.split(',')
           volEntry = []
           for volLbl in vols:
              volEntry.append(self.volume_query(volLbl))
           volChanges['remove'] = volEntry
        parms['volume_changes'] = volChanges

        # Build initiator change input, if specified
        initChanges = {}
        if (addInitList):
           inits = addInitList.split(',')
           initEntry = []
           for initLbl in inits:
              initEntry.append(self.initiator_query(initLbl))
           initChanges['add'] = initEntry
        if (remInitList):
           inits = remInitList.split(',')
           initEntry = []
           for initLbl in inits:
              initEntry.append(self.initiator_query(initLbl))
           initChanges['remove'] = initEntry
        parms['initiator_changes'] = initChanges

        # Build host change input, if specified
        hostChanges = {}
        if (addHostList):
           hosts = addHostList.split(',')
           hostEntry = []
           for hostLbl in hosts:
              hostEntry.append(self.host_query(hostLbl))
           hostChanges['add'] = hostEntry
        if (remHostList):
           hosts = remHostList.split(',')
           hostEntry = []
           for hostLbl in hosts:
              hostEntry.append(self.host_query(hostLbl))
           hostChanges['remove'] = hostEntry
        parms['host_changes'] = hostChanges

        # Build cluster change input, if specified
        clusterChanges = {}
        if (addClusterList):
           clusters = addClusterList.split(',')
           clusterEntry = []
           for clusterLbl in clusters:
              clusterEntry.append(self.cluster_query(clusterLbl))
           clusterChanges['add'] = clusterEntry
        if (remClusterList):
           clusters = remClusterList.split(',')
           clusterEntry = []
           for clusterLbl in clusters:
              clusterEntry.append(self.cluster_query(clusterLbl))
           clusterChanges['remove'] = clusterEntry
        parms['cluster_changes'] = clusterChanges

        print str(parms)
        o = self.api('PUT', URI_EXPORTGROUP_INSTANCE.format(groupId), parms)
        self.assert_is_dict(o)
        print 'OOO: ' + str(o) + ' :OOO'
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.export_show_task)
        return (o, s)

    def export_group_query(self, groupId):
        if (self.__is_uri(groupId)):
            return groupId

        (project, gname) = groupId.rsplit('/', 1)
        puri = self.project_query(project)
        names = self.export_group_list(puri)
        for name in names:
            export_group = self.export_group_show(name)
            if (export_group['name'] == gname and export_group['inactive'] == False):
                return export_group['id']
        raise Exception('bad export group name')

    def export_group_delete(self, groupId):
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_EXPORTGROUP_INSTANCE.format(groupId)))
        self.assert_is_dict(o)
        try:
            s = self.api_sync_2(o['resource']['id'], o['op_id'], self.export_show_task)
        except:
            print o;
        return (o, s)

    def export_group_add_volume(self, groupId, volspec):
        volentry = []
        vols = volspec.split(',')
        for vol in vols:
           volparam = vol.split('+')
           volmap = dict()
           if (len(volparam) > 0):
               volmap['id'] = self.volume_query(volparam[0])
           if (len(volparam) > 1):
               volmap['lun'] = volparam[1]
           volentry.append(volmap)

        parms = dict()
        parms['volume'] = volentry

        o = self.api('POST', URI_EXPORTGROUP_VOLUMES.format(groupId), parms)
        self.assert_is_dict(o)
        if ('op_id' in o):
            s = self.api_sync_2(groupId, o['op_id'], self.export_show_task)
        else:
            self.pretty_print_json(o)
            s = 'error'
        return (o, s)

    def export_group_remove_volume(self, groupId, volspec):
        volentry = []
        vols = volspec.split(',')
        for vol in vols:
           voluri = self.volume_query(vol)
           volentry.append(voluri)

        parms = dict()
        parms['volume'] = volentry

        o = self.api('POST', URI_EXPORTGROUP_VOLUMES_REMOVE.format(groupId), parms)
        self.assert_is_dict(o)
        if ('op_id' in o):
            s = self.api_sync_2(groupId, o['op_id'], self.export_show_task)
        else:
            self.pretty_print_json(o)
            s = 'error'
        return (o, s)


    def export_group_add_initiator(self, groupId, initspec):
        initkvarr = []
        inits = initspec.split(',')
        for init in inits:
           parameters = init.split('+')
           initkvdict = dict()
           initkvdict['protocol'] = parameters[0]
           initkvdict['initiator_node'] = parameters[1]
           initkvdict['initiator_port'] = parameters[2]
           initkvdict['hostname'] = parameters[3]
           if (len(parameters) == 5):
              initkvdict['clustername'] = parameters[4]
           initkvarr.append(initkvdict)

        parms = dict()
        parms['initiator'] = initkvarr

        o = self.api('POST', URI_EXPORTGROUP_INITS.format(groupId), parms)
        self.assert_is_dict(o)
        if ('op_id' in o):
            s = self.api_sync_2(groupId, o['op_id'], self.export_show_task)
        else:
            s = 'error'
        return (o, s)

    def export_group_remove_initiator(self, groupId, initspec):
        initkvarr = []
        inits = initspec.split(',')
        for init in inits:
           (protocol, port) = init.split('+')
           initkvdict = dict()
           initkvdict['protocol'] = protocol
           initkvdict['port'] = port
           initkvarr.append(initkvdict)

        parms = dict()
        parms['initiator'] = initkvarr

        o = self.api('POST', URI_EXPORTGROUP_INITS_REMOVE.format(groupId), parms)
        self.assert_is_dict(o)
        if ('op_id' in o):
            s = self.api_sync_2(groupId, o['op_id'], self.export_show_task)
        else:
            s = 'error'
        return (o, s)

    #
    # block snapshot
    #
    def block_snapshot_show(self, uri):
        return self.api('GET', URI_BLOCK_SNAPSHOTS.format(uri))

    def block_snapshot_show_task(self, snap, task):
        return self.api('GET', URI_BLOCK_SNAPSHOTS_TASKS.format(snap, task))

    def block_snapshot_query(self, name):
        if (self.__is_uri(name)):
            return name

        (sname, label) = name.rsplit('/', 1)
        furi = self.volume_query(sname)
        furi = furi.strip()

        uris = self.block_snapshot_list(furi)
        for uri in uris:
            snapshot = self.block_snapshot_show(uri)
            if (snapshot['name'] == label):
                return snapshot['id']
        raise Exception('bad snapshot name')

    def block_snapshot_create(self, volume, label, create_inactive, rp):
        parms = {
            'name'  : label,
            'create_inactive' : create_inactive,
	    'type'  : rp
        }

        o = self.api('POST', URI_BLOCK_SNAPSHOTS_LIST.format(volume), parms)
        self.assert_is_dict(o)
        snapshots = o['task']
        id = ''
        task_id = ''
        if (not o):
            return {}
        else :
           if (type(snapshots) != list):
              snapshots = [snapshots]
           for snap in snapshots:
               id = snap['resource']['id']
               task_id = snap['op_id']

        s = self.api_sync_2(id, task_id, self.block_snapshot_show_task)
        return (o, s['state'], s['message'])

    def block_snapshot_activate(self, snapshot):
        vuri = self.block_snapshot_query(snapshot)
        vuri = vuri.strip()
        o = self.api('POST', URI_BLOCK_SNAPSHOTS_ACTIVATE.format(vuri))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_snapshot_show_task)
        return (o, s['state'], s['message'])

    def block_snapshot_delete(self, uri):
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_BLOCK_SNAPSHOTS.format(uri)))
        self.assert_is_dict(o)
        tasks = []
        for task in o['task']:
            s = self.api_sync_2(task['resource']['id'], task['op_id'], self.block_snapshot_show_task)
            tasks.append(s)
        return tasks

    def block_snapshot_list(self, volume):
        vuri = self.volume_query(volume)
        vuri = vuri.strip()
        o = self.api('GET', URI_BLOCK_SNAPSHOTS_LIST.format(vuri))
        self.assert_is_dict(o)
        snaps = o['snapshot']
        ids = []
        if (not o):
            return {}
        else :
           if (type(snaps) != list):
              snaps = [snaps]
           for snap in snaps:
               ids.append(snap.get('id'))
        return ids

    def block_snapshot_restore(self, snapshot):
        vuri = self.block_snapshot_query(snapshot)
        vuri = vuri.strip()
        o = self.api('POST', URI_BLOCK_SNAPSHOTS_RESTORE.format(vuri))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_snapshot_show_task)
        return (o, s['state'], s['message'])

    def block_snapshot_exports(self, snapshot):
        vuri = self.block_snapshot_query(snapshot).strip()
        return self.api('GET', URI_BLOCK_SNAPSHOTS_EXPORTS.format(vuri))

    def block_snapshot_expose(self, snapshot):
        vuri = self.block_snapshot_query(snapshot)
        vuri = vuri.strip()
        o = self.api('POST', URI_BLOCK_SNAPSHOTS_EXPOSE.format(vuri))
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.block_snapshot_show_task)
        return (o, s['state'], s['message'])

#
# protection system APIs
#
    def protectionsystem_show_task(self, protectionsystem, task):
        uri_task = URI_PROTECTION_SYSTEM + '/tasks/{1}'
        return self.api('GET', uri_task.format(protectionsystem,task))

    def protectionsystem_discover(self, uri, ignore_error):
        task = self.api('POST', URI_PROTECTION_SYSTEM_DISCOVER.format(uri));
	s=self.api_sync_2(task['resource']['id'],task['op_id'],self.protectionsystem_show_task, ignore_error)
        return "discovery is completed"

    def protectionsystem_discover_namespace(self, native_guid, namespace, ignore_error):
        if (self.__is_uri(native_guid)):
            return name
        systems = self.protectionsystem_list()
        for system in systems:
            try:
                protection_system = self.show_element(system['id'], URI_PROTECTION_SYSTEM)
                if (protection_system['native_guid'] == native_guid or protection_system['name'] == native_guid):
                    o = self.api('POST', URI_DISCOVERED_PROTECTION_SYSTEM_NS.format(system['id'], namespace));
                    s=self.api_sync_2(o['resource']['id'],o['op_id'],self.protectionsystem_show_task, ignore_error)
                    return "discovery of namespace is completed"
            except KeyError:
                print 'no name key'
        raise Exception('bad protection system native_guid: ' + native_guid)

    def protectionsystem_list(self):
        o = self.api('GET', URI_PROTECTION_SYSTEMS)
        if (not o):
            return {};
	systems = o['protection_system'];
	if(type(systems) != list):
	    return [systems];
        return systems;

    def protectionsystem_show(self, uri):
        return self.api('GET', URI_PROTECTION_SYSTEM.format(uri))

    def protectionsystem_query(self, name):
        if (self.__is_uri(name)):
            return name

        protectionsystems = self.protectionsystem_list()
        for protection_system in protectionsystems:
            protectionsystem = self.protectionsystem_show(protection_system['id'])
            if (protectionsystem['name'] == name):
                return protectionsystem['id']
        raise Exception('bad protectionsystem name ' + name)

    def protectionsystem_delete(self, uri):
        return  self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_PROTECTION_SYSTEM.format(uri)))

    def protectionsystem_create(self, name, system_type, ip_address, port_number, user_name, password, registration_mode):

        parms = dict()
        if (name):
            parms['name'] = name
        if (system_type):
            parms['system_type'] = system_type
        if (ip_address):
            parms['ip_address'] = ip_address
        if (port_number):
            parms['port_number'] = port_number
        if (user_name):
            parms['user_name'] = user_name
        if (password):
            parms['password'] = password
        if (registration_mode):
            parms['registration_mode'] = registration_mode

        resp = self.api('POST', URI_PROTECTION_SYSTEMS, parms)
        print resp
        return self.api_sync_2(resp['resource']['id'], resp['op_id'], self.protectionsystem_show_task)

    def protectionsystem_update(self, psuri, cluster, addvarrays, rmvarrays):
        parms = dict()
        if (addvarrays or rmvarrays):
 	    if (cluster):
		varrayassignment = dict();
		varrayassignment['cluster_id'] = cluster
	        if (addvarrays):
        	    addsarray = []
	            adds = addvarrays.split(',')
        	    for add in adds:
	                adduri = self.neighborhood_query(add)
	                addsarray.append(adduri)
	            addsdict = dict()
        	    addsdict['varrays'] = addsarray
	            varrayassignment['add'] = addsdict
	        if (rmvarrays):
	            rmsarray = []
	            rms = rmvarrays.split(',')
	            for rm in rms:
	                rmuri = self.neighborhood_query(rm)
	                rmsarray.append(rmuri)
	            rmsdict = dict()
	            rmsdict['varrays'] = rmsarray
	            varrayassignment['remove'] = rmsdict
	    vassignsarray = []
	    varrayassignments = dict();
	    vassignsarray.append(varrayassignment);
	    parms['varray_assignment_changes'] = vassignsarray

        return self.api('PUT', URI_PROTECTION_SYSTEM_UPDATE.format(psuri), parms)

    #
    # Search API's
    #
    def search(self, resource_type, scope, prefix, project, tag):
        search_scope = {
        }
        if(scope):
            search_scope["tenant"] = scope
        if(project):
            search_scope["project"] = project
        if(tag):
            search_scope["tag"] = prefix
        elif(prefix):
            search_scope["name"] = prefix

        uri = ''
        if resource_type == "authnprovider":
            uri = URI_VDC_AUTHN_PROFILE
        elif resource_type == "auto_tiering_policy":
            uri = URI_SERVICES_BASE + '/vdc/auto-tier-policies'
        elif resource_type == "fileshare":
            uri = URI_FILESYSTEMS_LIST
        elif resource_type == "volume":
            uri = URI_VOLUME_LIST
        elif resource_type == "project":
            uri = URI_SERVICES_BASE + '/projects'
        elif resource_type == "tenant":
            uri = URI_SERVICES_BASE + '/tenants'
        elif resource_type == "block_vpool":
            uri = URI_SERVICES_BASE + '/block/vpools'
        elif resource_type == 'file_vpool':
            uri = URI_SERVICES_BASE + '/file/vpools'
        elif resource_type == "varray":
            uri = URI_VARRAYS
        elif resource_type == "network_system":
            uri = URI_NETWORKSYSTEMS
        elif resource_type == "storage_system":
            uri = URI_STORAGEDEVICES
        elif resource_type == "protection_system":
            uri = URI_PROTECTION_SYSTEMS
        elif resource_type == "protectionset":
            uri = URI_PROTECTIONSETS
        elif resource_type == "smis_provider":
            uri = URI_SMISPROVIDERS
        elif resource_type == "storage_tier":
            uri = URI_STORAGETIERS
        elif resource_type == "network":
            uri = URI_NETWORKS
        elif resource_type == "storage_pool":
            uri = URI_SERVICES_BASE + '/vdc/storage-pools'
        elif resource_type == "storage_port":
            uri = URI_SERVICES_BASE + '/vdc/storage-ports'
        elif resource_type == "snapshot":
            uri = URI_FILE_SNAPSHOTS
        elif resource_type == "block_snapshot":
            uri = URI_SERVICES_BASE + '/block/snapshots'
        elif resource_type == "block_export":
            uri = URI_SERVICES_BASE + '/block/exports'
        elif resource_type == "block_consistency_group":
            uri = URI_SERVICES_BASE + '/block/consistency-groups'
        elif resource_type == "vcenter":
            uri = URI_VCENTERS
        elif resource_type == "datacenter":
            uri = URI_DATACENTERS
        elif resource_type == "host":
            uri = URI_HOSTS
        elif resource_type == "cluster":
            uri = URI_CLUSTERS
        elif resource_type == "ipinterface":
            uri = URI_IPINTERFACES
        elif resource_type == "initiator":
            uri = URI_INITIATORS
        else:
            raise Exception('Unknown resource type ' + resource_type)
        searchuri =  uri + '/search'
        results =  self.api('GET', searchuri, None, search_scope)
        return results['resource']
    #
    # Tag API's
    #
    def getTagURI(self, resource_type, id):
        uri = ''
        if resource_type == "authnprovider":
            uri = URI_VDC_AUTHN_PROFILES.format(id)
        elif resource_type == "auto_tiering_policy":
            uri = URI_AUTO_TIER_POLICY.format(id)
        elif resource_type == "fileshare":
            uri = URI_FILESYSTEM.format(id)
        elif resource_type == "volume":
            uri = URI_VOLUME.format(id)
        elif resource_type == "project":
            uri = URI_PROJECT.format(id)
        elif resource_type == "tenant":
            uri = URI_TENANTS.format(id)
        elif resource_type == "block_vpool":
            uri = URI_SERVICES_BASE + '/block/vpools/{0}'.format(id)
        elif resource_type == 'file_vpool':
            uri = URI_SERVICES_BASE + '/file/vpools/{0}'.format(id)
        elif resource_type == 'vpool':
            uri = URI_SERVICES_BASE + '/object/data-services-vpools/{0}'.format(id)
        elif resource_type == "varray":
            uri = URI_VARRAY.format(id)
        elif resource_type == "network_system":
            uri = URI_NETWORKSYSTEM.format(id)
        elif resource_type == "storage_system":
            uri = URI_STORAGEDEVICE.format(id)
        elif resource_type == "protection_system":
            uri = URI_PROTECTION_SYSTEM.format(id)
        elif resource_type == "protectionset":
            uri = URI_PROTECTIONSET.format(id)
        elif resource_type == "smis_provider":
            uri = URI_SMISPROVIDER.format(id)
        elif resource_type == "storage_tier":
            uri = URI_STORAGETIER.format(id)
        elif resource_type == "network":
            uri = URI_NETWORK.format(id)
        elif resource_type == "storage_pool":
            uri = URI_STORAGEPOOL.format(id)
        elif resource_type == "storage_port":
            uri = URI_STORAGEPORT.format(id)
        elif resource_type == "snapshot":
            uri = URI_FILE_SNAPSHOT.format(id)
        elif resource_type == "block_snapshot":
            uri = URI_BLOCK_SNAPSHOTS.format(id)
        elif resource_type == "block_export":
            uri = URI_EXPORTGROUP_INSTANCE.format(id)
        elif resource_type == "vcenter":
            uri = URI_VCENTER.format(id)
        elif resource_type == "datacenter":
            uri = URI_DATACENTER.format(id)
        elif resource_type == "host":
            uri = URI_HOST.format(id)
        elif resource_type == "cluster":
            uri = URI_CLUSTER.format(id)
        elif resource_type == "ipinterface":
            uri = URI_IPINTERFACE.format(id)
        elif resource_type == "initiator":
            uri = URI_INITIATOR.format(id)
        else:
            raise Exception('Unknown resource type ' + resource_type)
        return uri + '/tags'

    def tag(self, resource_type, id, tags):
        target = self.getTagURI(resource_type, id)
        params = {
            'add': tags
        }
        self.api('PUT', target, params)

    def untag(self, resource_type, id, tags):
        target = self.getTagURI(resource_type, id)
        params = {
            'remove': tags
        }
        self.api('PUT', target, params)

    def datastore_create(self, type, label, cos, filecos, size, mountpoint):

        if (type == 'commodity'):
            params = dict()
            params['nodes'] = []
            params['nodes'].append({"nodeId":label, "name":label, "description":"Commodity Sanity Node", "virtual_array":cos})
            o = self.api('POST', URI_DATA_STORE_LIST + "/" + type, params)
            print ('data store creation result is %s' % o)
            sync_out_list = []
            for task in o['task']:
                s = self.api_sync_2(task['resource']['id'], task['op_id'], self.datastore_show_task)
                sync_out_list.append(s)
            print "sync completed"
            return (o, sync_out_list)

        else:
            params = {
                'name'              : label,
                'virtual_array'       : cos,
            }

            if (size):
                params['size'] = size

            if (mountpoint):
                params['mount_point']  = mountpoint

            if (filecos):
                params['file_data_services_vpool'] = filecos

            o = self.api('POST', URI_DATA_STORE_LIST + "/" + type, params)
            print ('data store creation result is %s' % o)
            s = self.api_sync_2(o['resource']['id'], o['op_id'], self.datastore_show_task)
            print "sync completed"
            return (o, s)

    def datastore_delete(self, uri, type):
        print "uri is ", uri
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_DATA_STORE.format(uri)), None)
        r = self.waitfor_op_deletion(uri, type)
        return (o, r)

    def waitfor_op_deletion(self, id, type):
        response  = self.coreapi('GET', URI_DATA_STORE_LIST + "/" + type + "/" + id)

        if(BOURNE_DEBUG == '1'):
            print ('Datastore deletion response is %s' % response.text)

        tmo = 0
        while (response.text != 'invalid pool'):
            time.sleep(3)
            response  = self.coreapi('GET', URI_DATA_STORE_LIST + "/" + type + "/" + id)
            print ('response is %s' % response.text)
            tmo += 3

            if (tmo > API_SYNC_TIMEOUT):
                break

        if (response.text != 'invalid pool'):
            raise Exception('Timed out waiting for deletion of data store: ' + id)

        return response

    def datastore_show(self, type, uri):
        return self.api('GET', URI_DATA_STORE_LIST + "/" + type + "/" + uri)

    def datastore_show_task(self, uri, task):
        uri_object_task = URI_DATA_STORE + '/tasks/{1}'
        return self.api('GET', uri_object_task.format(uri, task))

    def datastore_list(self):
        o = self.api('GET', URI_DATA_STORE_LIST)
        if (not o):
            return {};
        else:
            return o['data_store']

    def datastore_query(self, type, label):
        if (self.__is_uri(label)):
            return label

        o = self.api('GET', URI_DATA_STORE_LIST)
        pools = o['data_store']
        ids = []
        if (not o):
           return ()
        else :
           for pool in pools:
              try:
                pool_details = self.datastore_show(type, pool['id'])
                if (pool_details['name'] == label):
                    return pool_details.get('id')
              except:
                pass

        raise Exception('bad pool name '+label)

    def datastore_bulkget(self):
        return self.api('GET', URI_DATA_STORE_BULKGET)

    def datastore_bulkgetids(self):
        ids = self.datastore_bulkget()
        # retrieve the first 10 volumes only
        chunk = self.get_ids_chunk(ids['id'], 0, 10)
        return chunk

    def datastore_bulkpost(self, ids):
        return self.api('POST', URI_DATA_STORE_BULKGET, ids)

    def atmosdevice_create(self, namespace, project, name, atmosip, tenant, tenantid, admin, password, token):
        parms = {'name': name,
            'namespace': namespace,
            'ip': atmosip,
            'tenant_name': tenant,
            'tenant_id': tenantid,
            'tenant_admin': admin,
            'tenant_admin_password': password}

        if (project != None):
            project = self.project_query(project).strip()
            parms['project'] = project

        o = self.api('POST', URI_ATMOS_DEVICE_LIST, parms)
        # only POST uses /object/atmos-importer
        # GETs continue to use /vdc/data-stores
        token = o['op_id']
        s = self.api_sync_2(o['resource']['id'], token, self.atmosdevice_show_task)
        return (o, s)

    def atmosdevice_update(self, uri, atmosip, tenant, admin, password):
        parms = {}
        if(atmosip):
            parms['ip'] = atmosip

        if(tenant):
            parms['tenant_name'] = tenant

        if(admin):
            parms['tenant_admin'] = admin

        if(password):
            parms['tenant_admin_password'] = password

        token = 'cli-update-' + uri
        response = self.coreapi('PUT', URI_ATMOS_DEVICE.format(uri), parms)

        if (response.status_code != 200):
            print "update atmos device failed with code: ", response.status_code
            raise Exception('update atmos device failed')
        return response.text


    def atmosdevice_query(self, label):
        if (self.__is_uri(label)):
            return label

        o = self.api('GET', URI_ATMOS_DEVICE_LIST)
        devices = o['atmos_device']
        if (not o):
            return ()
        else:
            for device in devices:
                try:
                    device_details = self.atmosdevice_show(device['id'])
                    if (device_details['name'] == label):
                        return device.get('id')
                except:
                    pass

        raise Exception('bad device name '+ label)

    def atmosdevice_show(self, uri):
        return self.api('GET', URI_ATMOS_DEVICE.format(uri))


    def atmosdevice_list(self):
        o = self.api('GET', URI_ATMOS_DEVICE_LIST)
        devices = o['atmos_device']
        ids = []
        if (not o):
            return ()
        else:
            for device in devices:
                ids.append(device.get('id'))
        return ids

    def atmosdevice_show_task(self, uri, task):
        return self.api('GET', URI_ATMOS_DEVICE_TASK.format(uri, task))

    def atmosdevice_delete(self, uri):
        o = self.api('POST', URI_ATMOS_DEVICE_DELETE.format(uri), None)
        token = o['op_id']
        r = self.api_sync_2(uri, token, self.atmosdevice_show_task)
        return (o, r)

    def objectingestion_create(self, dataStoreName, fileshareId, keypoolName,
                               dataStoreDescription):
        parms = {
            'datastore_name'            : dataStoreName,
            'filesystem_device_info'    : { 'fileshare_id': fileshareId },
            'keypool_name'              : keypoolName
        }

        if (dataStoreDescription):
            parms['datastore_description'] = dataStoreDescription

        return self.api('POST', URI_OBJECT_INGESTION_LIST, parms)

    def objectingestion_op_status(self, objectingestionId, opId):
        return self.api('GET', URI_OBJECT_INGESTION_OP_STATUS.format(objectingestionId, opId))

    def objectingestion_list(self):
        o = self.api('GET', URI_OBJECT_INGESTION_LIST)
        if (not o):
            return {};
        else:
            return o['object_ingestion']

    def objectingestion_show(self, objectingestionId):
        print self.api('GET', URI_OBJECT_INGESTION.format(objectingestionId))

    def objectingestion_delete(self, objectingestionId):
        print self.api('POST', URI_OBJECT_INGESTION_DELETE.format(objectingestionId))

    def _s3_hmac_base64_sig(self, method, bucket, objname, uid, secret, content_type, parameters_to_sign=None):
        '''
        calculate the signature for S3 request

         StringToSign = HTTP-Verb + "\n" +
         * Content-MD5 + "\n" +
         * Content-Type + "\n" +
         * Date + "\n" +
         * CanonicalizedAmzHeaders +
         * CanonicalizedResource
        '''
        buf = ""
        # HTTP-Verb
        buf += method + "\n"

        # Content-MD5, a new line is needed even if it does not exist
        md5 = self._headers.get('Content-MD5')
        if md5 != None:
            buf += md5
        buf += "\n"

        #Content-Type, a new line is needed even if it does not exist
        if content_type != None:
            buf+=content_type
        buf += "\n"

        # Date, it should be removed if "x-amz-date" is set
        if self._headers.get("x-amz-date") == None:
            date = self._headers.get('Date')
            if date != None:
                buf += date
        buf += "\n"

        # CanonicalizedAmzHeaders, does not support multiple headers with same name
        canonicalizedAmzHeaders = []
        for header in self._headers.keys():
            if header.startswith("x-amz-") or header.startswith("x-emc-"):
                canonicalizedAmzHeaders.append(header)

        canonicalizedAmzHeaders.sort()

        for name in canonicalizedAmzHeaders:
            buf +=name+":"+self._headers[name]+"\n"

        #CanonicalizedResource represents the Amazon S3 resource targeted by the request.
        buf += "/"
        if bucket != None:
            buf += bucket
        if objname != None:
            buf += "/" + urllib.quote(objname)

        if parameters_to_sign !=None:
            para_names = parameters_to_sign.keys()
            para_names.sort()
            separator = '?';
            for name in para_names:
                value = parameters_to_sign[name]
                buf += separator
                buf += name
                if value != None and value != "":
                    buf += "=" + value
                separator = '&'

        if BOURNE_DEBUG == '1':
            print 'message to sign with secret[%s]: %s\n' % (secret, buf)
        macer = hmac.new(secret.encode('UTF-8'), buf, hashlib.sha1)

        signature = base64.b64encode(macer.digest())
        if BOURNE_DEBUG == '1':
            print "calculated signature:"+signature

        # The signature
        self._headers['Authorization'] = 'AWS ' + uid + ':' + signature

    def _set_auth_and_ns_header(self, method, namespace, bucket, objname, uid, secret, content_type = CONTENT_TYPE_XML, parameters_to_sign=None):
        if self._headers.get("x-amz-date") == None:
            self._headers['Date'] = formatdate()
        if (uid):
            self._s3_hmac_base64_sig(method, bucket, objname, uid, secret, content_type, parameters_to_sign)
        else:
            # anonymous still requires namespace
            self._headers['x-emc-namespace'] = namespace

    def _computeMD5(self, value):
        m = hashlib.md5()
        if value != None:
            m.update(value)
        return m.hexdigest()

    def _checkETag(self, response, md5str):
        responseEtag = response.headers['ETag'];
        # strip enclosing quotes from returned ETag before matching with
        # calculated MD5
        if (responseEtag.startswith("\"") and responseEtag.endswith("\"")):
            responseEtag = responseEtag[1:-1]
        if (responseEtag != md5str):
            s = "data md5 mismatch! local md5: %s, response etag: %s" % \
                (md5str, responseEtag)
            print s
            raise Exception(s)

    @resetHeaders
    def bucket_switch(self, namespace, bucket, mode, hosts, duration, token, user, uid, secret, preserveDirStructure = False):

        self._headers[FILE_ACCESS_MODE_HEADER] = mode
        print "switching bucket %s to file access mode %s, preserveDirStructure=%s" % (bucket, mode, preserveDirStructure)
        self._headers[FILE_ACCESS_PRESERVE_DIR_STRUCTURE_HEADER] = str(preserveDirStructure)
        if (user != ''):
            self._headers[USER_HEADER] = user
        if (hosts != ''):
            self._headers[HOST_LIST_HEADER] = hosts
        if (duration != ''):
            self._headers[FILE_ACCESS_DURATION_HEADER] = duration
        if (token != ''):
            self._headers[TOKEN_HEADER] = token
        else:
            if (self._headers.has_key(TOKEN_HEADER)):
                del self._headers[TOKEN_HEADER]

        qparms = {'accessmode': None}
        self._set_auth_and_ns_header('PUT', namespace,bucket, None, uid, secret, parameters_to_sign = qparms)
        response = self.coreapi('PUT', URI_S3_BUCKET_INSTANCE.format(bucket), None, qparms , content_type=CONTENT_TYPE_XML)
        return response

    @resetHeaders
    def bucket_fileaccesslist(self, namespace,  bucket, uid, secret):
        qparms = {'fileaccess':None}
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)

        return self.coreapi('GET', URI_S3_BUCKET_INSTANCE.format(bucket), None, qparms, content_type=CONTENT_TYPE_XML)

    @resetHeaders
    def bucket_switchget(self, namespace, bucket, uid, secret):
        qparms = {'accessmode':None}
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)

        response = self.coreapi('GET', URI_S3_BUCKET_INSTANCE.format(bucket), None, qparms, content_type=CONTENT_TYPE_XML)
        return response

    # swift related operations --begin

    def __swift_getkey(self, key):
        return "{%s}%s" % (OPENSTACK_XML_NS, key)

     #format a dict to XML, all leaf elements will be taken as attributes
    def swift_formatxml(self, parent, parms):
        for key in parms.keys():
            value = parms[key]
            if type(value) == dict:
                e = ET.Element(self.__swift_getkey(key))
                if parent == None:
                    parent = e
                else:
                    parent.append(e)
                self.swift_formatxml(e, value)
            else:
                parent.set(key, value)
        return parent

    @resetHeaders
    def swift_authenticate(self, uid, password):
        self._headers[SWIFT_AUTH_USER] =  uid
        self._headers[SWIFT_AUTH_KEY] = password
        response = self.coreapi('GET', '/auth/v1.0', None, None, None, content_type=CONTENT_TYPE_XML)
        headers = response.headers
        token = headers.get(SWIFT_AUTH_TOKEN)
        if not token:
            raise Exception('authentication failed')
        if BOURNE_DEBUG == '1':
            print '%s=%s' % (SWIFT_AUTH_TOKEN, token)
        return token

    @resetHeaders
    def swift_authenticate_v2(self, uid, password, ctype=CONTENT_TYPE_JSON):
        parms = dict()
        parms['auth'] = dict()
        parms['auth']['passwordCredentials'] = dict()
        parms['auth']['passwordCredentials']['username'] = uid
        parms['auth']['passwordCredentials']['password'] = password
        if ctype == CONTENT_TYPE_XML:
            parms = ET.tostring(self.swift_formatxml(None, parms), "UTF-8")

        if BOURNE_DEBUG == '1':
            print parms

        # get unscoped token
        response = self.api('POST', '/v2.0/tokens', parms, content_type=ctype)
        unscoped_token = None
        if ctype == CONTENT_TYPE_JSON:
            unscoped_token = response["access"]["token"]["id"]
        else:
            unscoped_token = ET.fromstring(response).find(self.__swift_getkey("token")).get("id")

        # use unscoped token to get tenants info
        self._headers[SWIFT_AUTH_TOKEN] = unscoped_token
        response = self.api('GET', '/v2.0/tenants', content_type=ctype)
        tenantName = None
        if ctype == CONTENT_TYPE_JSON:
            tenantName = response["tenants"][0]["name"]
        else:
            tenantName = ET.fromstring(response).find(self.__swift_getkey("tenant")).get("name")

        # use unscoped token plus tenantNmae to get scoped token info
        parms = dict()
        parms['auth'] = dict()
        parms['auth']['tenantName'] = tenantName
        parms['auth']['token'] = dict()
        parms['auth']['token']['id'] = unscoped_token
        if ctype == CONTENT_TYPE_XML:
            parms = ET.tostring(self.swift_formatxml(None, parms), "UTF-8")

        response = self.api("POST", "/v2.0/tokens", parms, content_type=ctype)
        if ctype == CONTENT_TYPE_JSON:
            scoped_token = response["access"]["token"]["id"]
        else:
            scoped_token = ET.fromstring(response).find(self.__swift_getkey("token")).get("id")

        if BOURNE_DEBUG == '1':
            print 'the scoped token is %s' % scoped_token

        return scoped_token

    @resetHeaders
    def containers_list(self, namespace, project, token):
        qparms = {"format" : "json"}
        if project:
            puri = self.project_query(project)
            puri = puri.strip()
            self._headers['x-emc-project-id'] = puri

        self._headers[SWIFT_AUTH_TOKEN] = token

        response = self.api("GET", URI_SWIFT_ACCOUNT_INSTANCE.format(namespace), None, qparms, content_type=CONTENT_TYPE_XML)
        return response

    @resetHeaders
    def containers_meta(self, namespace, project, token):
        if project:
            puri = self.project_query(project)
            puri = puri.strip()
            self._headers['x-emc-project-id'] = puri

        self._headers[SWIFT_AUTH_TOKEN] = token

        response = self.coreapi("HEAD", URI_SWIFT_ACCOUNT_INSTANCE.format(namespace), None, None, content_type=CONTENT_TYPE_XML)
        return response

    @resetHeaders
    def container_create(self, namespace, project, container, cos, x_container_read, x_container_write, metadata, token):
        if project:
            puri = self.project_query(project)
            puri = puri.strip()
            self._headers['x-emc-project-id'] = puri
            if cos:
                curi = self.cos_query("object", cos)
                curi = curi.strip()
            self._headers['x-emc-cos'] = curi
        if metadata:
            self._headers = dict(self._headers.items() + metadata.items())

        self._headers[SWIFT_AUTH_TOKEN] = token
        self._headers[SWIFT_X_CONTAINER_READ] = x_container_read
        self._headers[SWIFT_X_CONTAINER_WRITE] = x_container_write

        response = self.coreapi('PUT', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, None, content_type=CONTENT_TYPE_XML)
        if response.status_code != 201 and response.status_code != 202:
            print "container create failed with code: ", response.status_code
            raise Exception("failed to create container")

    @resetHeaders
    def container_delete(self, namespace, container, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.coreapi('DELETE', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, None, content_type=CONTENT_TYPE_XML)
        if response.status_code != 204:
            if response.status_code != 409:
                print "container_delete failed with code: ", response.status_code
            else:
                print "container is not empty, can not delete conflict"
            raise Exception('failed to delete container')

    @resetHeaders
    def container_switchfileaccess(self, namespace, container, mode, hosts, duration, token, user, swift_token, preserveDirStructure = False):
        self._headers[SWIFT_AUTH_TOKEN] = swift_token
        self._headers[FILE_ACCESS_MODE_HEADER] =  mode
        print "switching container %s to file access mode %s, preserveDirStructure=%s" % (container, mode, preserveDirStructure)
        self._headers[FILE_ACCESS_PRESERVE_DIR_STRUCTURE_HEADER] = str(preserveDirStructure)
        if (duration != ''):
            self._headers[FILE_ACCESS_DURATION_HEADER] = duration
        if (hosts != ''):
            self._headers[HOST_LIST_HEADER] = hosts
        if (user != ''):
            self._headers[USER_HEADER] = user
        if (token != None):
            self._headers[TOKEN_HEADER] = token

        response = self.coreapi('PUT', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, {'accessmode':None}, content_type=CONTENT_TYPE_XML)

        h = response.headers
        if (FILE_ACCESS_MODE_HEADER in h) :
            print '%s=%s' % (FILE_ACCESS_MODE_HEADER, h[FILE_ACCESS_MODE_HEADER])
        if (FILE_ACCESS_DURATION_HEADER in h) :
            print '%s=%s' % (FILE_ACCESS_DURATION_HEADER, h[FILE_ACCESS_DURATION_HEADER])
        if (HOST_LIST_HEADER in h):
            print '%s=%s' % (HOST_LIST_HEADER, h[HOST_LIST_HEADER])
        if (USER_HEADER in h):
            print '%s=%s' % (USER_HEADER, h[USER_HEADER])
        if (TOKEN_HEADER in h):
            print '%s=%s' % (TOKEN_HEADER, h[TOKEN_HEADER])

        if (START_TOKEN_HEADER in h):
            print '%s=%s' % (START_TOKEN_HEADER, h[START_TOKEN_HEADER])

        if (END_TOKEN_HEADER in h):
            print '%s=%s' % (START_TOKEN_HEADER, h[END_TOKEN_HEADER])
        return response

    @resetHeaders
    def container_getfileaccess(self, namespace, container, swift_token):
        self._headers[SWIFT_AUTH_TOKEN] = swift_token
        response = self.coreapi('GET', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None,
            {'fileaccess': None, 'format': 'json'},
            None, CONTENT_TYPE_XML)
        return response

    @resetHeaders
    def container_getaccessmode(self, namespace, container, swift_token):
        self._headers[SWIFT_AUTH_TOKEN] = swift_token
        response = self.coreapi('GET', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, {'accessmode':None}, content_type=CONTENT_TYPE_XML)

        h = response.headers
        print '%s=%s' % (FILE_ACCESS_MODE_HEADER, h[FILE_ACCESS_MODE_HEADER])   
        if (FILE_ACCESS_DURATION_HEADER in h):
            print '%s=%s' % (FILE_ACCESS_DURATION_HEADER, h[FILE_ACCESS_DURATION_HEADER])
        if (HOST_LIST_HEADER in h):
           print '%s=%s' % (HOST_LIST_HEADER, h[HOST_LIST_HEADER])
        if (USER_HEADER in h):
           print '%s=%s' % (USER_HEADER, h[USER_HEADER])
        if (TOKEN_HEADER in h):
           print '%s=%s' % (TOKEN_HEADER, h[TOKEN_HEADER])
        return response

    @resetHeaders
    def container_metadata(self, namespace, container, metadata, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        if metadata:
            self._headers = dict(self._headers.items() + metadata.items())

        response = self.coreapi('POST', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, None, content_type=CONTENT_TYPE_XML)
        if response.status_code != 204:
            print "container set/remove metadata failed with code: ", response.status_code
            raise Exception("failed to set/remove container metadata")

    @resetHeaders
    def container_header(self, namespace, container, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.coreapi('HEAD', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, None, content_type=CONTENT_TYPE_XML)
        if response.status_code != 204:
            print "container get metadata failed with code: ", response.status_code
            raise Exception("failed to get container metadata")

        headers = response.headers
        for key in headers.keys():
            if key.lower().startswith('x-container-meta-'):
                print "%s: %s" % (key, headers[key])
        return headers

    @resetHeaders
    def container_objs_list(self, namespace, container, params, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        qparms = self._build_list_params(params)
        return self.api('GET', URI_SWIFT_CONTAINER_INSTANCE.format(namespace, container), None, qparms, content_type=CONTENT_TYPE_XML)

    @resetHeaders
    def container_object_create(self, namespace, container, key, value, headers, token, deleteAt, deleteAfter):
        self._headers[SWIFT_AUTH_TOKEN] = token

        if deleteAt != None:
            self._headers[SWIFT_DELETE_AT] = deleteAt

        if deleteAfter != None:
            self._headers[SWIFT_DELETE_AFTER] = deleteAfter

        if value is None:
            print "empty object, setting Content-Length to 0"
            self._headers['Content-Length'] = str(0)

        if headers:
            for header_name,header_value in headers.items():
                self._headers[header_name] = header_value
        print self._headers
        response = self.coreapi('PUT', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), value, None, content_type=CONTENT_TYPE_OCTET)
        # check response status
        if (response.status_code != 201):
            s = "container_object_create key:%s, container:%s, namespace:%s, token:%s failed with code:%d " % \
                (key, container, namespace, token, response.status_code)
            print s
            print response.headers
            print response.content
            raise Exception(s)
        return response.content

    @resetHeaders
    def container_object_show(self, namespace, container, key, range_str, token):
        if range_str:
            self._headers['Range'] = range_str
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.__api('GET', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), None, None, content_type=CONTENT_TYPE_OCTET)

        # check response status
        if (response.status_code != 200 and response.status_code != 204 and response.status_code != 206 and response.status_code != 404):
            s = "container_object_show key:%s, container:%s, namespace:%s, token:%s failed with code:%d " % \
                (key, container, namespace, token, response.status_code)
            print s
            print response.headers
            print response.content
            raise Exception(s)
        if response.status_code == 404:
            return response.status_code
        return response.content

    @resetHeaders
    def container_object_delete(self, namespace, container, key, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.coreapi('DELETE', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), None, None, content_type=CONTENT_TYPE_OCTET)
        if (response.status_code != 204):
            s = "container_object_delete failed with code: %d" % response.status_code
            raise Exception(s)

    @resetHeaders
    def container_object_head(self, namespace, container, key, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.coreapi('HEAD', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), None, None, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != HTTP_OK:
            print "object failed with code: ", response.status_code
            raise Exception("failed to head key")
        print response.headers
        return response.headers

    @resetHeaders
    def container_object_post(self, namespace, container, key, headers, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        if headers:
            for header_name,header_value in headers.items():
                self._headers[header_name] = header_value
        print self._headers

        response = self.coreapi('POST', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), None, None, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != 202:
            print "object failed with code: ", response.status_code
            raise Exception("failed to head key")
        print response

    @resetHeaders
    def container_object_copy(self, namespace, src_container, src_key, dst_container, dst_key, headers, token):
        self._headers[SWIFT_AUTH_TOKEN] = token
        copy_from_str = "%s/%s" % (src_container, src_key)
        self._headers[SWIFT_COPY_FROM] = copy_from_str
        if headers:
            for header_name,header_value in headers.items():
                self._headers[header_name] = header_value
        print headers
        response = self.coreapi('PUT', URI_SWIFT_KEY_INSTANCE.format(namespace, dst_container, dst_key), None, None, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != 201:
            print "object failed with code: ", response.status_code
            raise Exception("failed to head key")
        return response.headers


    @resetHeaders
    def container_object_update(self, namespace, container, key, range_str, payload, token):
        self._headers['Range'] = range_str
        self._headers[SWIFT_AUTH_TOKEN] = token
        response = self.coreapi('PUT', URI_SWIFT_KEY_INSTANCE.format(namespace, container, key), payload, None, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != 200:
            print "container_object_update failed with code: ", response.status_code
            raise Exception('failed to update key')
    # swift related operation  --end

    @resetHeaders
    def baseurl_create(self, baseUrl, name, namespaceInHost):
        parms = {
            'name': name,
            'base_url': baseUrl,
            'is_namespace_in_host': namespaceInHost
            }
        response = self.coreapi('POST', URI_BASEURL_BASE, parms)
        if(response.status_code != HTTP_OK):
            raise Exception('failed to create baseurl')

        resp_parms = self.__json_decode(response.text)
        return resp_parms['id']

    @resetHeaders
    def baseurl_get(self, baseUrlId):
        response = self.coreapi('GET', URI_BASEURL_INSTANCE.format(baseUrlId))
        if(response.status_code != HTTP_OK):
            raise Exception('failed to get baseurl')

        resp_parms = self.__json_decode(response.text)
        baseurl_obj = resp_parms
        if(baseUrlId != baseurl_obj['id']):
            raise Exception('got baseurl with id ' + baseurl_obj['id'] + ' instead of one with id ' +  baseUrlId)
        return baseurl_obj

    @resetHeaders
    def baseurl_list(self):
        response = self.coreapi('GET', URI_BASEURL_BASE)
        if(response.status_code != HTTP_OK):
            raise Exception('failed to get baseurl')
        resp_parms = self.__json_decode(response.text)
        return resp_parms['base_url']

    @resetHeaders
    def baseurl_delete(self, baseUrlId):
        response = self.coreapi('POST', URI_BASEURL_DEACTIVATE.format(baseUrlId))
        if response.status_code != HTTP_OK:
            raise Exception('failed to delete baseurl')

    @resetHeaders
    def bucket_update_acl(self, namespace, bucket, uid, secret, bodyAclValue, headerCannedAclValue=None):
        if (headerCannedAclValue):
            self._headers['x-amz-acl'] = headerCannedAclValue
        qparms = {'acl': None}
        self._set_auth_and_ns_header('PUT', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('PUT', uri, bodyAclValue, qparms, None, CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to update bucket acl')

    @resetHeaders
    def bucket_get_acl(self, namespace, bucket, uid, secret):
        qparms = {'acl': None}

        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)

     	return self.api('GET', URI_S3_BUCKET_INSTANCE.format(bucket),
                None, qparms, content_type=CONTENT_TYPE_XML)
    @resetHeaders
    def s3_ping(self, namespace):
        qparms = {'ping': None}
        self._headers['x-emc-namespace'] = namespace
        self._set_auth_and_ns_header('GET', namespace, None, None, None, None, parameters_to_sign = qparms)
        uri = URI_S3_PING
        response = self.coreapi('GET', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        print "status", response.status_code
	if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('ping failure')
        return response.text

    @resetHeaders
    def s3_datanode(self, namespace, uid, secret):
        qparms = {'endpoint': None}
        self._headers['x-emc-namespace'] = namespace
        self._set_auth_and_ns_header('GET', namespace, None, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_DATANODE
        response = self.coreapi('GET', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        print "status", response.status_code
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('getting datanode failure')
        else:
            print "succeed"
            print response.text

    @resetHeaders
    def bucket_create(self, namespace, bucket, uid, secret, rg=None, fileSystemEnabled = False, proj=None):
        if(rg != None):
            self._headers['x-emc-dataservice-vpool'] = rg

        if(proj != None):
            self._headers['x-emc-project-id'] = proj

        if(fileSystemEnabled):
            self._headers['x-emc-file-system-access-enabled'] = 'true'

        self._set_auth_and_ns_header('PUT',namespace, bucket, None, uid, secret)

        response = self.coreapi('PUT', URI_S3_BUCKET_INSTANCE.format(bucket), None, None, content_type=CONTENT_TYPE_XML)
        h = response.headers
        if ( h['location']):
            return h['location']
        else:
            print "bucket_create failed with code: ", response.status_code
            raise Exception('failed to create bucket')

    @resetHeaders
    def bucket_delete(self, namespace, bucket, uid, secret):
        self._set_auth_and_ns_header('DELETE', namespace, bucket, None, uid, secret)
        response = self.coreapi('DELETE', URI_S3_BUCKET_INSTANCE.format(bucket), None, None, content_type=CONTENT_TYPE_XML)
        if (response.status_code != 204):
            print "bucket_delete failed with code: ", response.status_code
            raise Exception('failed to delete bucket')

    @resetHeaders
    def bucket_list(self, namespace, uid, secret):
        self._set_auth_and_ns_header('GET', namespace, None, None, uid, secret)
        return self.api('GET', URI_S3_SERVICE_BASE, None, None, content_type=CONTENT_TYPE_XML)

    @resetHeaders
    def bucket_head(self, namespace, bucket, uid, secret):
        self._set_auth_and_ns_header('HEAD', namespace, bucket, None, uid, secret)
        response = self.coreapi('HEAD', URI_S3_BUCKET_INSTANCE.format(bucket), None, None, content_type=CONTENT_TYPE_XML)
        if response.status_code == HTTP_OK:
            print " HEAD Bucket for " , bucket, " resp code:", response.status_code
        else:
            print "HEAD Bucket for ", bucket, "failed with resp code:", response.status_code
            raise Exception('HEAD Bucket failed')

    def _build_versioning_payload(self, status):
        root = ET.Element('VersioningConfiguration')
        root.set('xmlns', S3_XML_NS)
        ET.SubElement(root, 'Status').text = status
        return ET.tostring(root)

    def _get_versioning_status(self, payload):
        tree = ET.fromstring(payload)
        return tree.findtext('./{' + S3_XML_NS + '}Status')

    @resetHeaders
    def bucket_versioning_get(self, namespace, bucket, uid, secret):
        qparms = {'versioning':None}
        self._set_auth_and_ns_header('GET',namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('GET', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to get versioning status')
        return self._get_versioning_status(response.text)

    @resetHeaders
    def bucket_versioning_put(self, namespace, bucket, status, uid, secret):
        qparms = {'versioning':None}
        self._set_auth_and_ns_header('PUT', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        parms = self._build_versioning_payload(status)
        response = self.coreapi('PUT', uri, parms, qparms,
                                None, CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to put versioning status')

    def _build_lifecycle_payload(self, rules):
        root = ET.Element('LifecycleConfiguration')
        root.set('xmlns', S3_XML_NS)

        json_rules = cjson.decode(rules)
        for r in json_rules.get('rules'):
            rule = ET.SubElement(root, 'Rule')
            ET.SubElement(rule, 'ID').text = r.get('id')
            ET.SubElement(rule, 'Prefix').text = r.get('prefix')
            ET.SubElement(rule, 'Status').text = r.get('status')

            e = r.get('expiration')
            expiration = ET.SubElement(rule, 'Expiration')
            if e.get('days'):
                ET.SubElement(expiration, 'Days').text = str(e.get('days'))
            if e.get('date'):
                ET.SubElement(expiration, 'Date').text = e.get('date')
        return ET.tostring(root)

    @resetHeaders
    def bucket_lifecycle_get(self, namespace, bucket, uid, secret):
        qparms = {'lifecycle': None}
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('GET', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to get lifecycle configuration')
        return response.text

    @resetHeaders
    def bucket_lifecycle_del(self, namespace, bucket, uid, secret):
        qparms = {'lifecycle': None}
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('DELETE', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        if response.status_code != 204:
            print "failure", response.status_code, response.text
            raise Exception('failed to get lifecycle configuration')
        return response.text

    @resetHeaders
    def bucket_lifecycle_put(self, namespace, bucket, rules, uid, secret):
        qparms = {'lifecycle': None}
        self._set_auth_and_ns_header('PUT', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        parms = self._build_lifecycle_payload(rules)
        response = self.coreapi('PUT', uri, parms, qparms, None, CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to put lifecycle configuration')

    def _build_cors_payload(self, rules):
        root = ET.Element('CORSConfiguration')
        root.set('xmlns', S3_XML_NS)

        json_rules = cjson.decode(rules)
        for r in json_rules.get('rules'):
            rule = ET.SubElement(root, 'CORSRule')

            origin = r.get('origin')
            for o in origin:
                ET.SubElement(rule, 'AllowedOrigin').text = o

            method = r.get('method')
            for m in method:
                ET.SubElement(rule, 'AllowedMethod').text = m

            header = r.get('header')
            for h in header:
                ET.SubElement(rule, 'AllowedHeader').text = h
        return ET.tostring(root)



    @resetHeaders
    def bucket_cors_get(self, namespace, bucket, uid, secret):
        qparms = {'cors': None}
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('GET', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to get CORS configuration')
        return response.text

    @resetHeaders
    def bucket_cors_delete(self, namespace, bucket, uid, secret):
        qparms = {'cors': None}
        self._set_auth_and_ns_header('DELETE', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('DELETE', uri, qparms = qparms, content_type = CONTENT_TYPE_XML)
        if response.status_code != HTTP_NO_CONTENT:
            print "failure", response.status_code, response.text
            raise Exception('failed to delete CORS configuration')

    @resetHeaders
    def bucket_cors_put(self, namespace, bucket, rules, uid, secret):
        qparms = {'cors': None}
        self._set_auth_and_ns_header('PUT', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        parms = self._build_cors_payload(rules)
        response = self.coreapi('PUT', uri, parms, qparms, None, CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to put CORS configuration')


    def node_create(self, name):
        return self.api('POST', URI_NODEOBJ.format(name))

    def security_logout(self):
        response = self.__api('GET', URI_LOGOUT)
        if (response.status_code != 200):
            print "logout failed with code: ", response.status_code
            raise Exception('security logout: failed')

    def security_add_tenant_role(self, tenant, objecttype, objectname, role):
        uri = self.__tenant_id_from_label(tenant)
        if( not objecttype in ['subject_id', 'group']):
            raise Exception('type must be subject_id or group')
        if( not role in ['TENANT_ADMIN','PROJECT_ADMIN', 'TENANT_APPROVER']):
            raise Exception('role must be TENANT_ADMIN, PROJECT_ADMIN, or TENANT_APPROVER')
        self.security_add_tenant_id_role(uri, objecttype, objectname, role)

    def security_remove_tenant_role(self, tenant, objecttype, objectname, role):
        uri = self.__tenant_id_from_label(tenant)
        if( not objecttype in ['subject_id', 'group']):
            raise Exception('type must be subject_id or group')
        if( not role in ['TENANT_ADMIN','PROJECT_ADMIN', 'TENANT_APPROVER']):
            raise Exception('role must be TENANT_ADMIN, PROJECT_ADMIN, or TENANT_APPROVER')
        self.security_remove_tenant_id_role(uri, objecttype, objectname, role)

    def security_add_tenant_id_role(self, tenant_id, objecttype, objectname, role):
        parms = {
                 "add" : [ { "role" : [role], objecttype : objectname }]
                 }
        print parms
        response = self.__api('PUT', URI_TENANTS_ROLES.format(tenant_id), parms)
        if (response.status_code != 200):
            print "security assign role failed with code: ", response.status_code
            raise Exception('security assign role: failed')

    def security_remove_tenant_id_role(self, tenant_id, objecttype, objectname, role):
        parms = {
                 "remove" : [ { "role" : [role], objecttype : objectname }]
                 }
        print parms
        response = self.__api('PUT', URI_TENANTS_ROLES.format(tenant_id), parms)
        if (response.status_code != 200):
            print "security assign role failed with code: ", response.status_code
            raise Exception('security assign role: failed')

    def _get_s3_key_uri(self, bucket, key, alternateFormat = False):
        if(alternateFormat):
            return URI_S3_KEY_INSTANCE_ALTERNATE.format(urllib.quote(key))
        return URI_S3_KEY_INSTANCE.format(urllib.quote(bucket), urllib.quote(key))

    @resetHeaders
    def bucket_key_create(self, namespace, bucket, key, value, uid, headers, secret, baseurl = None, bucketNameFormat = 1, namespaceFormat = 1):
    	value = self.getDataValueFromCli(value)
        if headers:
            for header_name,header_value in headers.items():
                self._headers[header_name] = header_value

        if(bucketNameFormat == 2): #set the bucket name in the Host header and not in the path
            if(baseurl == None):
                raise Exception('Base URL should be specified if the alternate format of URI needs to be used')
            host = bucket + '.'
            if(namespaceFormat == 2):
                host = host + namespace + '.'
            else:
                self._headers['x-emc-namespace'] = namespace
            host = host + baseurl
            self._headers['Host'] = host

        if value is None:
            print "empty object, setting Content-Length to 0"
            self._headers['Content-Length'] = str(0)

        self._set_auth_and_ns_header('PUT', namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET)
        print self._headers
        md5str = self._computeMD5(value)
        altUriFmt = False
        if(bucketNameFormat == 2):
                altUriFmt = True
        response = self.coreapi('PUT', self._get_s3_key_uri(bucket, key, altUriFmt), value, None, content_type=CONTENT_TYPE_OCTET)
        #TODO: server returns
        if (response.status_code != 200 and response.status_code != 204 ):
            print "bucket_key_create failed with code: ", response.status_code
            raise Exception('failed to create key')
        if BOURNE_DEBUG == '1':
            print response.headers
        self._checkETag(response, md5str)

    @resetHeaders
    def bucket_key_copy(self, namespace, bucket, key, destbucket, destkey, uid, secret):
        self._headers['x-amz-copy-source'] = URI_S3_KEY_INSTANCE.format(urllib.quote_plus(bucket), urllib.quote_plus(key))
        self._headers['x-amz-metadata-directive'] = 'COPY'
        self._set_auth_and_ns_header('PUT', namespace, destbucket, destkey, uid, secret)
        # TODO: rewire scripts to return etag so that we can validate
        response = self.coreapi('PUT', self._get_s3_key_uri(destbucket, destkey), None, None, content_type=CONTENT_TYPE_XML)
        #TODO: error may be embedded in 200
        if (response.status_code != 200 and response.status_code != 204):
            print "bucket_key_copy failed with code: ", response.status_code
            raise Exception('failed to copy key')

    @resetHeaders
    def bucket_key_delete(self, namespace, bucket, key, version, uid, secret):
        qparms = None
        if version is not None:
            qparms = {'versionId': version}
        self._set_auth_and_ns_header('DELETE', namespace, bucket, key, uid, secret, 'text/plain', qparms)

        response = self.coreapi('DELETE', self._get_s3_key_uri(bucket, key),
                None, qparms, None, 'text/plain')

        if (response.status_code != 204):
            print "bucket_key_delete failed with code: ", response.status_code
            raise Exception('failed to delete key')

    @resetHeaders
    def bucket_key_update(self, namespace, bucket, key, value, uid, secret, range):
        self._headers['Range'] = range
        self._set_auth_and_ns_header('PUT', namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET)
        md5str = self._computeMD5(value)
        response = self.coreapi('PUT', self._get_s3_key_uri(bucket, key), value, None, content_type=CONTENT_TYPE_OCTET)
        #TODO: server returns
        if (response.status_code != 200 and response.status_code != 204 ):
            print "bucket_key_update failed with code: ", response.status_code
            raise Exception('failed to update/append key')
        return response

    @resetHeaders
    def bucket_key_update_acl(self, namespace, bucket, key, bodyAclValue, cannedAclValue, aclHeaders, uid, secret):
    	if (cannedAclValue):
    		self._headers["x-amz-acl"] = cannedAclValue
    	if (aclHeaders):
    		for acl in aclHeaders:
    			pair = acl.split(':')
    			self._headers[pair[0]] = pair[1]
        qparms = {'acl':None}
        self._set_auth_and_ns_header('PUT', namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET, parameters_to_sign = qparms)
        if (bodyAclValue):
            md5str = self._computeMD5(bodyAclValue)
        response = self.coreapi('PUT', self._get_s3_key_uri(bucket, key) + '?acl', bodyAclValue, None, content_type=CONTENT_TYPE_OCTET)
        if (response.status_code != 200 and response.status_code != 204 ):
            print "bucket_key_update failed with code: ", response.status_code
            raise Exception('failed to update ACL')
        return response

    @resetHeaders
    def bucket_key_get_acl(self, namespace, bucket, key, uid, secret):
        qparms = {'acl': None}

        self._set_auth_and_ns_header('GET', namespace, bucket, key, uid, secret, parameters_to_sign = qparms)

     	return self.api('GET', self._get_s3_key_uri(bucket, key),
                None, qparms, content_type=CONTENT_TYPE_XML)

    # build qparms for list
    def _build_list_params(self, params, qparms = None):
        if qparms is None:
            qparms = {}
        for (key, value) in params.iteritems():
            if value is not None:
                qparms[key] = value
        return qparms

    @resetHeaders
    def bucket_key_list(self, namespace, bucket, params, uid, secret):
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret)
        qparms = self._build_list_params(params)
        return self.api('GET', URI_S3_BUCKET_INSTANCE.format(bucket),
                None, qparms, content_type=CONTENT_TYPE_XML)

    @resetHeaders
    def bucket_key_list_versions(self, namespace, bucket, params, uid, secret):
        qparms = {}
        qparms['versions'] = None
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, parameters_to_sign = qparms)
        qparms = self._build_list_params(params, qparms)
        return self.api('GET', URI_S3_BUCKET_INSTANCE.format(bucket),
                None, qparms, content_type=CONTENT_TYPE_XML)

    @resetHeaders
    def bucket_key_show(self, namespace, bucket, key, version, uid, secret, range=None):
        return self._bucket_key_read('GET', namespace, bucket, key, version, uid, secret, range)

    @resetHeaders
    def bucket_key_head(self, namespace, bucket, key, version, uid, secret):
        return self._bucket_key_read('HEAD', namespace, bucket, key, version, uid, secret)

    @resetHeaders
    def _bucket_key_read(self, method, namespace, bucket, key, version, uid, secret, range=None):
        qparms = None
        if version is not None:
            qparms = {'versionId': version}
        if range != None:
            self._headers['Range'] = range

        self._set_auth_and_ns_header(method, namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET, parameters_to_sign = qparms )

        response = self.__api(method, self._get_s3_key_uri(bucket,key), None, qparms, content_type=CONTENT_TYPE_OCTET)

        # check response status
        if (response.status_code != 200 and response.status_code != 204 and response.status_code != 206 ):
            s = "bucket_key_read (%s) key:%s, bucket:%s, namespace:%s, uid:%s, secret:%s, version:%s failed with code:%d " % \
                (method, key, bucket, namespace, uid, secret, version, response.status_code)
            print s
            print response.headers
            print response.content
            raise Exception(s)

        if method == 'GET':
            if BOURNE_DEBUG == '1':
                print response.headers

            return response.content
        else:
            return response.headers

    @resetHeaders
    def bucket_key_options(self, namespace, bucket, key, origin, method, header, uid, secret):
        self._set_auth_and_ns_header('OPTIONS', namespace, bucket, key, uid, secret)

        self._headers['Origin'] = origin
        self._headers['Access-Control-Request-Method'] = method
        if header:
            self._headers['Access-Control-Request-Headers'] = header

        response = self.coreapi('OPTIONS', self._get_s3_key_uri(bucket, key), content_type=CONTENT_TYPE_XML)
        if (response.status_code != HTTP_OK):
            print "OPTIONS Object failed with status code ", response.status_code
            raise Exception('OPTIONS Object failed')
        print response.headers

    @resetHeaders
    def bucket_options(self, namespace, bucket, origin, method, header, uid, secret):
        self._set_auth_and_ns_header('OPTIONS', namespace, bucket, None, uid, secret)

        self._headers['Origin'] = origin
        self._headers['Access-Control-Request-Method'] = method
        if header:
            self._headers['Access-Control-Request-Headers'] = header

        response = self.coreapi('OPTIONS', URI_S3_BUCKET_INSTANCE.format(bucket), content_type=CONTENT_TYPE_XML)
        if (response.status_code != HTTP_OK):
            print "OPTIONS Bucket failed with status code ", response.status_code
            raise Exception('OPTIONS Bucket failed')
        print response.headers

    # atmos related operations --begin
    def atmos_hmac_base64_sig(self, method, content_type, uri, date, secret):
    	byteRangeStr = ""

        custom_headers = {}
        for header in self._headers.iterkeys():
            if re.match('^x-emc-', header, re.IGNORECASE):
                custom_headers[header.lower()] = self._headers[header]
            if header == "Range":
            	byteRangeStr = self._headers[header]

        if ('x-emc-signature' in custom_headers):
            del custom_headers['x-emc-signature']

        msg = method + '\n' + \
              content_type + '\n' + \
              byteRangeStr + '\n' + \
              date + '\n' + \
              uri.lower() + '\n'

        sorted_headers = custom_headers.keys()
        sorted_headers.sort()
        for sorted_header in sorted_headers:
            msg += sorted_header + ':' + custom_headers[sorted_header] + '\n'
        msg = msg.rstrip()

        if(BOURNE_DEBUG == '1'):
            print 'message to sign:\n' + msg

        key = base64.b64decode(secret)
        macer = hmac.new(key, msg, hashlib.sha1)

        if(BOURNE_DEBUG == '1'):
            print "hmac string:"+base64.b64encode(macer.digest())

        return base64.b64encode(macer.digest())

    def atmos_object_create(self, namespace, value, uid, secret):
        uri = ""
        if (namespace):
            uri = URI_ATMOS_NAMESPACE_INSTANCE.format(namespace)
        else:
            uri = URI_ATMOS_OBJECTS
        method = 'POST'
        content_type = CONTENT_TYPE_OCTET
        date = email.Utils.formatdate(timeval=None, localtime=False, usegmt=True)

        length = str(0)
        if value is not None:
            length = str(len(value))
        self._headers['Content-Length'] = length
        self._headers['date'] = date
        #_headers['x-emc-date'] = date
        self._headers['x-emc-uid'] = uid
        self._headers['x-emc-meta'] = 'color=red,city=seattle,key=é'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig(method, content_type, uri, date, secret)

        response = self.coreapi(method, uri, value, None, None, content_type)

        #cleanup the global variable
        del self._headers['Content-Length']
        del self._headers['date']
        del self._headers['x-emc-uid']
        del self._headers['x-emc-signature']

        if (response.status_code != 201):
            print "atmos_object_create failed with code: ", response.status_code
            if(BOURNE_DEBUG == '1'):
                print 'response:\n' + response.content
            raise Exception('failed to create object')

        location = response.headers['location']
        match = re.match(r"/rest/objects/(\w+)", location)
        if (not match):
            print "The location header doesn't contain a valid object id: ", location
            raise Exception('failed to create object')

        objectid = match.group(1)

        if(BOURNE_DEBUG == '1'):
            print 'object id:\n' + objectid
        return objectid

    def atmos_object_read(self, oid, namespace, uid, secret):
        uri = ""
        if (namespace):
            uri = URI_ATMOS_NAMESPACE_INSTANCE.format(namespace)
        elif (oid):
            uri = URI_ATMOS_OBJECT_INSTANCE.format(oid)
        else:
            print "Neither object id or namespace is provided"
            raise Exception('failed to read object')

        method = 'GET'
        content_type = CONTENT_TYPE_OCTET
        date = email.Utils.formatdate(timeval=None, localtime=False, usegmt=True)

        self._headers['x-emc-date'] = date
        self._headers['date'] = date
        self._headers['x-emc-uid'] = uid
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig(method, content_type, uri, date, secret)

        response = self.coreapi(method, uri, None, None, None, content_type)

        #cleanup the global variable
        del self._headers['date']
        del self._headers['x-emc-date']
        del self._headers['x-emc-uid']
        del self._headers['x-emc-signature']

        if (response.status_code != 200):
            print "atmos_object_read failed with code: ", response.status_code
            raise Exception('failed to read object')

        return response.content


    def atmos_object_delete(self, oid, namespace, uid, secret):
        uri = ""
        if (namespace):
            uri = URI_ATMOS_NAMESPACE_INSTANCE.format(namespace)
        elif (oid):
            uri = URI_ATMOS_OBJECT_INSTANCE.format(oid)
        else:
            print "Neither object id or namespace is provided"
            raise Exception('failed to delete object')

        method = 'DELETE'
        content_type = CONTENT_TYPE_OCTET
        date = email.Utils.formatdate(timeval=None, localtime=False, usegmt=True)

        self._headers['x-emc-date'] = date
        self._headers['date'] = date
        self._headers['x-emc-uid'] = uid
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig(method, content_type, uri, date, secret)

        response = self.coreapi(method, uri, None, None, None, content_type)

        #cleanup the global variable
        del self._headers['date']
        del self._headers['x-emc-date']
        del self._headers['x-emc-uid']
        del self._headers['x-emc-signature']

        if (response.status_code != 204):
            print "atmos_object_read failed with code: ", response.status_code
            raise Exception('failed to delete object')

    # atmos related operation  --end

    def namespace_create(self, tenant, namespace, project, cos, rg, allowed_vpools_list):
        myport=self._port
        self._port=PORT
        tenant_uri = self.__tenant_id_from_label(tenant)
        project_uri = None
        cos_uri = None
        rg_uri = None
        if(project != None):
            project_uri = self.project_query(project)
        self._port=myport
        if(cos != None):
            cos_uri = self.neighborhood_query(cos)
        if (rg != None):
        	rg_uri = self.repgroup_query(rg)
        return self.namespace_createInternal(tenant_uri, namespace, project_uri, cos_uri, rg_uri, allowed_vpools_list)

    def namespace_createInternal(self, tenant_uri, namespace, project_uri, cos_uri, rg_uri, allowed_vpools_list):
        allowedList =[]
        allowedList.append(allowed_vpools_list)
        parms = {
                'namespace' : namespace,
                'tenant' : tenant_uri,
		        'allowed_vpools_list' : allowedList
        }
        if ( project_uri != None ):
            parms['default_object_project'] = project_uri
        if ( cos_uri != None ):
            parms['default_data_services_vpool'] = cos_uri
        if ( rg_uri != None):
            parms['default_data_services_rg'] = rg_uri

        response = self.coreapi('POST', URI_NAMESPACE_BASE, parms)
        if response.status_code != HTTP_OK:
            print "failure:", response.text
            raise Exception('failed to create namespace')
        return self.__json_decode(response.text)


    def namespace_update(self, tenant, namespace, project, repGroup, vpools_added_to_allowed_vpools_list, vpools_added_to_disallowed_vpools_list):
        myport=self._port
        self._port=PORT
        tenant_uri = self.__tenant_id_from_label(tenant)
        project_uri = self.project_query(project)
        self._port = myport
        rg_uri = self.repgroup_query(repGroup)

        allowedList = []
        allowedList.append(vpools_added_to_allowed_vpools_list)
        disAllowedList = []
        disAllowedList.append(vpools_added_to_disallowed_vpools_list)  
        parms = {
                'tenant' : tenant_uri,
                'default_object_project' : project_uri,
                'default_data_services_vpool' : rg_uri,
                'vpools_added_to_allowed_vpools_list' : allowedList,
                'vpools_added_to_disallowed_vpools_list' : disAllowedList
        }
        response = self.coreapi('PUT', URI_NAMESPACE_INSTANCE.format(namespace), parms)
        if response.status_code != HTTP_OK:
            print "failure:", response, response.text
            raise Exception('failed to update namespace')
        return self.__json_decode(response.text)

    def namespace_delete(self, namespace):
        response = self.coreapi('POST', URI_RESOURCE_DEACTIVATE.format(URI_NAMESPACE_INSTANCE.format(namespace)))
        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception('failed to delete namespace')

    def namespace_show(self, namespace):
        response = self.coreapi('GET', URI_NAMESPACE_INSTANCE.format(namespace))
        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception('failed to get namespace')
        return self.__json_decode(response.text)

    def namespace_show_tenant(self, tenant):
        myport=self._port
        self._port=PORT
        tenant_uri = self.__tenant_id_from_label(tenant)
        self._port=myport
        response = self.coreapi('GET', URI_NAMESPACE_TENANT_INSTANCE.format(tenant_uri))
        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception('failed to get namespace')
        return self.__json_decode(response.text)

    def namespace_list(self):
        o = self.api('GET', URI_NAMESPACE_COMMON)
        namespaces = o['namespace']
        ids = []
        if (type(namespaces) != list):
            namespaces = [namespaces]
        for namespace in namespaces:
            ids.append(namespace.get('id'))
        return ids

    def checkAtmosResponse(self, response):
        if (response.status_code != 200 and response.status_code != 204 and response.status_code != 201 and response.status_code != 206):
            print "failed with code: ", response.status_code
            raise Exception('failed operation ')

    @resetHeaders
    def subtenant_create(self, namespace, project, cos, uid, secret):
        #self._headers['x-emc-cos'] = cos
        #self._headers['x-emc-project-id'] = project
        #self._headers['x-emc-namespace'] = namespace

        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = uid
        self._headers['x-emc-file-system-access-enabled'] = 'true'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('PUT', CONTENT_TYPE_OCTET, URI_ATMOS_SUBTENANT_BASE, self._headers['date'], secret)

        #response = self.coreapi('PUT', URI_ATMOS_SUBTENANT_BASE, None, None, None, content_type = CONTENT_TYPE_OCTET, port=ATMOS_PORT)
        response = self.coreapi('PUT', URI_ATMOS_SUBTENANT_BASE, None, None, None, content_type = CONTENT_TYPE_OCTET)
        h = response.headers
        if ( h['subtenantID']):
            return h['subtenantID']
        else:
            print "subtenant_create failed with code: ", response.status_code
            raise Exception('failed to create subtenant')

    def subtenant_delete(self, namespace, subtenant, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = uid
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('DELETE', CONTENT_TYPE_OCTET, URI_ATMOS_SUBTENANT_INSTANCE.format(subtenant), self._headers['date'], secret)
        print  URI_ATMOS_SUBTENANT_INSTANCE.format(subtenant)
        print self._headers['x-emc-signature']
        print secret
        #response = self.coreapi('DELETE', URI_ATMOS_SUBTENANT_INSTANCE.format(subtenant), None, None, None, content_type = CONTENT_TYPE_OCTET, port=ATMOS_PORT)
        response = self.coreapi('DELETE', URI_ATMOS_SUBTENANT_INSTANCE.format(subtenant), content_type = CONTENT_TYPE_OCTET)
        if (response.status_code != 200 and response.status_code != 204):
            print "subtenant_delete failed with code: ", response.status_code
            raise Exception('failed to delete subtenant' + subtenant)


    # value starting with @ char is a file, e.g. @/etc/hosts
    def getDataValueFromCli(self, value):
        if value and value.find('@') == 0:
            with open(value[1:], "rb") as f:
                f.seek(0)
                value = f.read()
        return value

    def _addChecksum(self, value, epoch):
    	# returns value in format <len, data, checksum>
    	size = len(value)
    	size = struct.pack('>i', size)
    	checksum = zlib.crc32(size + value) & 0xffffffff
    	checksum = struct.pack('>q', checksum)
    	epoch = struct.pack('>36s', epoch)
    	return size + value + checksum + epoch

    @resetHeaders
    def atmos_key_create(self, namespace, project, subtenant, keypath, value, uid, secret, useracl=None, groupacl=None):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        self._headers['x-emc-meta'] = 'color=red,city=seattle'
        self._headers['x-emc-listable-meta'] = 'country=usa'
        if (useracl):
            self._headers['x-emc-useracl'] = useracl
        if (groupacl):
            self._headers['x-emc-groupacl'] = groupacl
        if (keypath != ''):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS

        value = self.getDataValueFromCli(value)

        content_type = 'application/octet-stream'
        print self._headers
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('POST', content_type, uri, self._headers['date'], secret)
        response = self.coreapi('POST', uri, value, content_type = CONTENT_TYPE_OCTET)
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_update(self, namespace, project, subtenant, keypath, value, uid, secret, byteRange=None, useracl=None, groupacl=None):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        self._headers['x-emc-meta'] = 'color=red,city=seattle'
        self._headers['x-emc-listable-meta'] = 'country=usa'
        if (useracl):
            self._headers['x-emc-useracl'] = useracl
        if (groupacl):
            self._headers['x-emc-groupacl'] = groupacl
        if (keypath != ''):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS

        if byteRange != None:
        	self._headers["Range"] = byteRange

        value = self.getDataValueFromCli(value)

        content_type = 'application/octet-stream'
        print self._headers
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('PUT', content_type, uri, self._headers['date'], secret)
        response = self.coreapi('PUT', uri, value, content_type = CONTENT_TYPE_OCTET)
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_delete(self, namespace, project, subtenant, keypath, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = 'application/octet-stream'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('DELETE', content_type, uri, self._headers['date'], secret)
        response = self.coreapi('DELETE', uri, None, content_type = CONTENT_TYPE_OCTET)
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_show(self, namespace, project, subtenant, keypath, apiType, uid, secret, byteRange=None, includeMd=False):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if byteRange != None:
        	self._headers["Range"] = byteRange
        if includeMd:
        	self._headers["x-emc-include-meta"] = "1"

        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)

        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, uri, self._headers['date'], secret)
        response = self.coreapi('GET', uri, None, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_rename(self, namespace, project, subtenant, keypath, target, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        self._headers['x-emc-path'] = target
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            print 'no support for rename using object api'
            return
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('POST', content_type, uri+'?rename', self._headers['date'], secret)
        response = self.coreapi('POST', uri, qparms = {'rename':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_getinfo(self, namespace, project, subtenant, keypath, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, uri+'?info', self._headers['date'], secret)
        response = self.coreapi('GET', uri, qparms = {'info':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_listtags(self, namespace, project, subtenant, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE
        else :
            uri = URI_ATMOS_OBJECTS
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, uri+'?listabletags', self._headers['date'], secret)
        response = self.coreapi('GET', uri, qparms = {'listabletags':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_listobjectsbytags(self, namespace, project, subtenant, tag, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (tag != ''):
            self._headers['x-emc-tags'] = tag
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, URI_ATMOS_OBJECTS, self._headers['date'], secret)
        response = self.coreapi('GET', URI_ATMOS_OBJECTS, None, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_getumd(self, namespace, project, subtenant, keypath, umdkey, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        self._headers['x-emc-tags'] = umdkey
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, uri+'?metadata/user', self._headers['date'], secret)
        response = self.coreapi('GET', uri, qparms = {'metadata/user':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_setumd(self, namespace, project, subtenant, keypath, umd, tagType, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (tagType == 'Y'):
            self._headers['x-emc-listable-meta'] = umd
        else :
            self._headers['x-emc-meta'] = umd
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('POST', content_type, uri+'?metadata/user', self._headers['date'], secret)
        response = self.coreapi('POST', uri, qparms = {'metadata/user':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response


    @resetHeaders
    def atmos_key_getacl(self, namespace, project, subtenant, keypath, apiType, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('GET', content_type, uri+'?acl', self._headers['date'], secret)
        response = self.coreapi('GET', uri, qparms = {'acl':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    @resetHeaders
    def atmos_key_setacl(self, namespace, project, subtenant, keypath, apiType, useracl, groupacl, uid, secret):
        self._headers['date'] = formatdate()
        self._headers['x-emc-uid'] = subtenant + '/' + uid
        if (useracl):
            self._headers['x-emc-useracl'] = useracl
        if (groupacl):
            self._headers['x-emc-groupacl'] = groupacl
        if (apiType == 'N'):
            uri = URI_ATMOS_NAMESPACE_PATH.format(keypath)
        else :
            uri = URI_ATMOS_OBJECTS_OID.format(keypath)
        print uri
        content_type = '*/*'
        self._headers['x-emc-signature'] = self.atmos_hmac_base64_sig('POST', content_type, uri+'?acl', self._headers['date'], secret)
        response = self.coreapi('POST', uri, qparms = {'acl':None}, content_type = '*/*')
        self.checkAtmosResponse(response)
        return response

    def security_add_zone_role(self, objecttype, objectname, role):
        if( not objecttype in ['subject_id', 'group']):
            raise Exception('type must be subject_id or group')
        if( not role in ['SYSTEM_MONITOR','SYSTEM_AUDITOR','SYSTEM_ADMIN','SECURITY_ADMIN','TENANT_ADMIN',]):
            raise Exception('role must be SYSTEM_MONITOR, SYSTEM_AUDITOR, SYSTEM_ADMIN, SECURITY_ADMIN, or TENANT_ADMIN')

        parms = {
                 "add" : [ { "role" : [role], objecttype : objectname }]
                 }
        print parms
        response = self.__api('PUT', URI_VDC_ROLES, parms)
        if (response.status_code != 200):
            print "security assign role failed with code: ", response.status_code
            raise Exception('security assign role: failed')

    def _mpu_parse_init_response(self, payload):
        root = ET.fromstring(payload)
        inittag = '{' + S3_XML_NS + '}InitiateMultipartUploadResult'
        buckettag = '{' + S3_XML_NS + '}Bucket'
        keytag = '{' + S3_XML_NS + '}Key'
        uploadidtag = '{' + S3_XML_NS + '}UploadId'

        if root.tag != inittag:
            print "invalid response payload", payload
            raise Exception('Invalid response, no InitiateMultipartUploadResult')
        bucket = root.find(buckettag).text
        key = root.find(keytag).text
        uploadid = root.find(uploadidtag).text
        return {"bucket" : bucket, "key" : key, "uploadId" : uploadid}

    @resetHeaders
    def bucket_initiate_mpu(self, namespace, bucket, key, uid, secret):
        qparms = {'uploads':None}
        self._set_auth_and_ns_header('POST', namespace, bucket, key, uid, secret, parameters_to_sign = qparms)
        uri = self._get_s3_key_uri(bucket, key)
        response = self.coreapi('POST', uri, None, qparms , content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to initiate mpu!')
        return self._mpu_parse_init_response(response.text)

    @resetHeaders
    def bucket_upload_mpu(self, namespace, bucket, key, uid, secret, uploadId, partNum):
        qparms = {'uploadId':uploadId, 'partNumber':str(partNum)}
        self._set_auth_and_ns_header('PUT', namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET, parameters_to_sign = qparms)
        uri = self._get_s3_key_uri(bucket, key)
        value = str(uuid.uuid4())
        for i in range(100): 
            value = value + str(uuid.uuid4()) 
        md5str = self._computeMD5(value)
        response = self.coreapi('PUT', uri, value, qparms, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to upload a part for mpu!')
        self._checkETag(response, md5str)
        return response.headers['ETag']

    @resetHeaders
    def bucket_copy_part(self, namespace, bucket, key, srcbucket, srckey, uid, secret,
    uploadId, partNum):
        qparms = {'uploadId':uploadId, 'partNumber':str(partNum)}
        self._headers['x-amz-copy-source'] = URI_S3_KEY_INSTANCE.format(urllib.quote_plus(srcbucket), urllib.quote_plus(srckey))
        self._set_auth_and_ns_header('PUT', namespace, bucket, key, uid, secret, CONTENT_TYPE_OCTET, parameters_to_sign = qparms)
        uri = self._get_s3_key_uri(bucket, key)
        response = self.coreapi('PUT', uri, None, qparms, content_type=CONTENT_TYPE_OCTET)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to upload a part for mpu!')
        # parse and return etag from response
        print "got response: %s" % response.text
        tree = ET.fromstring(response.text)
        etag = tree.findtext('./{' + S3_XML_NS + '}ETag')
        return etag

    def _build_complete_mpu_payload(self, etagdict):
        root = ET.Element('CompleteMultipartUpload')
        root.set('xmlns', S3_XML_NS)
        # Note, the part list should be in ascending order
        sorted_keys = etagdict.keys()
        for key in sorted_keys:
           partElem = ET.SubElement(root, 'Part')
           ET.SubElement(partElem, 'PartNumber').text = str(key)
           ET.SubElement(partElem, 'ETag').text = etagdict[key]
        return ET.tostring(root)

    def _parse_complete_mpu_response(self, response):
        version = None
        if 'x-amz-version-id' in response.headers:
            version = response.headers['x-amz-version-id']
        payload = response.text
        root = ET.fromstring(payload)
        completetag = '{' + S3_XML_NS + '}CompleteMultipartUploadResult'
        uritag = '{' + S3_XML_NS + '}Location'
        buckettag = '{' + S3_XML_NS + '}Bucket'
        keytag = '{' + S3_XML_NS + '}Key'
        etagtag = '{' + S3_XML_NS + '}ETag'

        if root.tag != completetag:
            print "invalid response", response
            raise Exception('Invalid response, no CompleteMultipartUploadResult')
        bucket = root.find(buckettag).text
        key = root.find(keytag).text
        uri = root.find(uritag).text
        etag = root.find(etagtag).text
        return {'version':version, 'etag':etag, 'uri':uri, 'key':key, 'bucket':bucket}

    def _parse_list_mpu_parts_response(self, payload):
        result = {}
        root = ET.fromstring(payload)
        listtag = '{' + S3_XML_NS + '}ListPartsResult'
        buckettag = '{' + S3_XML_NS + '}Bucket'
        keytag = '{' + S3_XML_NS + '}Key'

        if root.tag != listtag:
            print "invalid response payload", payload
            raise Exception('Invalid response, no ListPartsResult')
        result['bucket'] = root.find(buckettag).text
        result['key'] = root.find(keytag).text

        initiatortag = '{' + S3_XML_NS + '}Initiator'
        idtag =  '{' + S3_XML_NS + '}ID'
        nametag =  '{' + S3_XML_NS + '}DisplayName'
        ownertag= '{' + S3_XML_NS + '}Owner'

        initiator = root.find(initiatortag)
        print "debug initiator = ",initiator
        result['initiator'] = {'id':initiator.find(idtag).text, 'name':initiator.find(nametag).text}

        owner = root.find(ownertag)
        result['owner'] = {'id':owner.find(idtag).text, 'name':owner.find(nametag).text}

        maxtag = '{' + S3_XML_NS + '}MaxParts'
        markertag = '{' + S3_XML_NS + '}PartNumberMarker'
        nexttag = '{' + S3_XML_NS + '}NextPartNumberMarker'
        trunctag = '{' + S3_XML_NS + '}IsTruncated'

        result['maxparts'] = root.find(maxtag).text
        if None != root.find(markertag):
            result['marker'] = root.find(markertag).text
        result['truncated'] =  root.find(trunctag).text
        if None != root.find(nexttag):
            result['nextmarker'] = root.find(nexttag).text

        parttag = '{' + S3_XML_NS + '}Part'
        etagtag = '{' + S3_XML_NS + '}ETag'
        sizetag = '{' + S3_XML_NS + '}Size'
        mtimetag = '{' + S3_XML_NS + '}LastModified'
        partnumtag = '{' + S3_XML_NS + '}PartNumber'

        index = 1
        parts = []
        for part in root.findall(parttag):
            partdict = {}
            partdict['num'] = part.find(partnumtag).text
            partdict['etag'] = part.find(etagtag).text
            partdict['mtime'] = part.find(mtimetag).text
            partdict['size'] = part.find(sizetag).text
            parts.append(partdict)
        result['parts'] = parts
        return result


    def _parse_list_mpu_uploads_response(self, payload):
        result = {}
        root = ET.fromstring(payload)
        list_tag = '{' + S3_XML_NS + '}ListMultipartUploadsResult'
        bucket_tag = '{' + S3_XML_NS + '}Bucket'
        keymarker_tag = '{' + S3_XML_NS + '}KeyMarker'
        uploadidmarker_tag = '{' + S3_XML_NS + '}UploadIdMarker'
        nextkeymarker_tag = '{' + S3_XML_NS + '}NextKeyMarker'
        nextuploadidmarker_tag = '{' + S3_XML_NS + '}NextUploadIdMarker'
        maxuploads_tag = '{' + S3_XML_NS + '}MaxUploads'
        delimiter_tag = '{' + S3_XML_NS + '}Delimiter'
        prefix_tag = '{' + S3_XML_NS + '}Prefix'
        commonprefixes_tag = '{' + S3_XML_NS + '}CommonPrefixes'
        istruncated_tag = '{' + S3_XML_NS + '}IsTruncated'
        upload_tag = '{' + S3_XML_NS + '}Upload'

        if root.tag != list_tag:
            print "invalid response payload", payload
            raise Exception('Invalid response, no ListMultipartUploadsResult')
        result['bucket'] = root.find(bucket_tag).text
        if None != root.find(keymarker_tag):
            result['keymarker'] = root.find(keymarker_tag).text
        if None != root.find(uploadidmarker_tag):
            result['uploadidmarker'] = root.find(uploadidmarker_tag).text
        if None != root.find(nextkeymarker_tag):
            result['nextkeymarker'] = root.find(nextkeymarker_tag).text
        if None != root.find(nextuploadidmarker_tag):
            result['nextuploadidmarker'] = root.find(nextuploadidmarker_tag).text
        if None != root.find(maxuploads_tag):
            result['maxuploads'] = root.find(maxuploads_tag).text
        if None != root.find(delimiter_tag):
            result['delimiter'] = root.find(delimiter_tag).text
        if None != root.find(prefix_tag):
            result['prefix'] = root.find(prefix_tag).text
        if None != root.find(istruncated_tag):
            result['istruncated'] = root.find(istruncated_tag).text

        uploads = []
        for upload in root.findall(upload_tag):
            uploaddict = {}
            key_tag = '{' + S3_XML_NS + '}Key'
            uploadid_tag = '{' + S3_XML_NS + '}UploadId'
            initiator_tag = '{' + S3_XML_NS + '}Initiator'
            id_tag =  '{' + S3_XML_NS + '}ID'
            name_tag =  '{' + S3_XML_NS + '}DisplayName'
            owner_tag= '{' + S3_XML_NS + '}Owner'
            initated_tag = '{' + S3_XML_NS + '}Initiated'

            initiator = root.find(initiator_tag)
            if None != initiator:
                uploaddict['initiator'] = {'id':initiator.find(id_tag).text, 'name':initiator.find(name_tag).text}

            owner = root.find(owner_tag)
            if None != owner:
                uploaddict['owner'] = {'id':owner.find(id_tag).text, 'name':owner.find(name_tag).text}

            uploaddict['key'] = upload.find(key_tag).text
            uploaddict['uploadid'] = upload.find(uploadid_tag).text

            uploads.append(uploaddict)
        result['uploads'] = uploads

        commonPrefixes = []
        for prefix in root.findall(commonprefixes_tag):
            commonPrefixes.append({'prefix':prefix.find(prefix_tag).text})
        result['commonPrefixes'] = commonPrefixes

        return result

    @resetHeaders
    def bucket_complete_mpu(self, namespace, bucket, key, uid, secret, uploadId, etagdict):
        qparms = {'uploadId':uploadId}
        self._set_auth_and_ns_header('POST', namespace, bucket, key, uid, secret, CONTENT_TYPE_XML, parameters_to_sign = qparms)
        uri = self._get_s3_key_uri(bucket, key)
        parms = self._build_complete_mpu_payload(etagdict)
        response = self.coreapi('POST', uri, parms, qparms, content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to complete mpu!')
        return self._parse_complete_mpu_response(response)

    @resetHeaders
    def bucket_abort_mpu(self, namespace, bucket, key, uid, secret, uploadId):
        qparms = {'uploadId':uploadId}
        self._set_auth_and_ns_header('DELETE', namespace, bucket, key, uid, secret, CONTENT_TYPE_XML, parameters_to_sign = qparms)
        uri = self._get_s3_key_uri(bucket, key)
        response = self.coreapi('DELETE', uri, None, qparms, content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK and response.status_code != HTTP_NO_CONTENT:
            print "failure", response
            raise Exception('failed to abort mpu!')
        return response

    @resetHeaders
    def bucket_list_mpu_parts(self, namespace, bucket, key, uid, secret, uploadId, maxParts, partNumMarker):
        qparms = {'uploadId':uploadId}
        parameters_to_sign = {'uploadId':uploadId}
        if None != maxParts:
            qparms['max-parts'] = maxParts
        if None != partNumMarker:
            qparms['part-number-marker'] = partNumMarker
        self._set_auth_and_ns_header('GET', namespace, bucket, key, uid, secret, CONTENT_TYPE_XML, parameters_to_sign)
        uri = self._get_s3_key_uri(bucket, key)
        response = self.coreapi('GET', uri, None, qparms, content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to list mpu parts!')
        return self._parse_list_mpu_parts_response(response.text)

    @resetHeaders
    def bucket_list_mpu_uploads(self, namespace, bucket, uid, secret, maxUploads, keyMarker, uploadIdMarker, delimiter, prefix):
        parameters_to_sign = {'uploads':None}
        qparms = {'uploads':None}
        if keyMarker != None:
            qparms['key-marker'] = keyMarker
        if uploadIdMarker != None:
            qparms['upload-id-marker'] = uploadIdMarker
        if maxUploads != None:
            qparms['max-uploads'] = maxUploads
        if delimiter != None:
            qparms['delimiter'] = delimiter
        if prefix != None:
            qparms['prefix'] = prefix
        self._set_auth_and_ns_header('GET', namespace, bucket, None, uid, secret, CONTENT_TYPE_XML, parameters_to_sign)
        uri = URI_S3_BUCKET_INSTANCE.format(bucket)
        response = self.coreapi('GET', uri, None, qparms, content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response
            raise Exception('failed to list mpu uploads!')
        return self._parse_list_mpu_uploads_response(response.text)

    def objtz_list(self):
        return self.api('GET', URI_OBJECTTZ)

    def objtz_show(self, uri):
        return self.api('GET', URI_OBJECTTZ_INSTANCE.format(uri))

    def objtz_create(self, name, tz):
        parms = dict()
        if (name):
            parms['name'] = name
        if (tz):
            parms['network'] = tz
        return self.api('POST', URI_OBJECTTZ, parms)

    def objtz_update(self, objtz, tz):
        parms = dict()
        if (tz):
            parms['network'] = tz
        return self.api('PUT', URI_OBJECTTZ_INSTANCE.format(objtz), parms)

    def objtz_delete(self, objtz):
        return self.api('POST', URI_OBJECTTZ_DELETE.format(objtz))


    def passwordgroup_create(self, uid, password, groups, namespace):
        parms = dict()
        if password:
            parms['password'] = password
        if groups:
            parms['groups_list'] = groups
        if namespace:
            parms['namespace'] = namespace

        response = self.__api('PUT', URI_PASSWORDGROUP.format(uid), parms)

        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception("failed to update password/groups")

    def passwordgroup_update(self, uid, password, groups):
        parms = dict()
        if password:
            parms['password'] = password
        if groups:
            parms['groups_list'] = groups

        response = self.__api('POST', URI_PASSWORDGROUP.format(uid), parms)

        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception("failed to update password/groups")

    def passwordgroup_listgroup(self, uid):
        response = self.__api('GET', URI_PASSWORDGROUP.format(uid))
        if response.status_code == HTTP_OK:
            content = None
            try:
                content = self.__json_decode(response.text)
            except:
                content = response.text
            return content.get('groups_list')
        elif response.status_code == HTTP_NOT_FOUND:
            return None
        else:
            print "failure:", response
            raise Exception("failed to list user groups")

    def passwordgroup_remove(self, uid):
        response = self.__api('POST', URI_PASSWORDGROUP_DEACTIVATE.format(uid))
        if response.status_code != HTTP_NO_CONTENT:
            print "failure:", response
            raise Exception("failed to remove user record")

    def secret_create_key_user(self, user, expiryForExistingKey):
        uriToUse = URI_SECRET_KEY_USER.format(user)

        parms = {
                    'existing_key_expiry_time_mins'              : expiryForExistingKey
                }

        return self.api('POST', uriToUse, parms)

    def secret_delete_key_user(self, user, secretKeyToDelete):
        uriToUse = URI_DELETE_SECRET_KEY_USER.format(user)
        parms = {
                'secret_key': secretKeyToDelete
                }

        response = self.__api('POST', uriToUse, parms)

        return response

    def secret_show_key_user(self, user):
        uriToUse = URI_SECRET_KEY_USER.format(user)
        return self.api('GET', uriToUse)

    def secret_create_key(self, expiryForExistingKey):
        parms = {
                            'existing_key_expiry_time_mins'              : expiryForExistingKey
                }
        return self.api('POST', URI_SECRET_KEY, parms)

    def secret_delete_key(self, secretKeyToDelete):
        response = None

        parms = {
                'secret_key': secretKeyToDelete
                }
        response = self.__api('POST', URI_SECRET_KEY + '/deactivate', parms)

        if response.status_code != HTTP_OK:
            print "failure:", response
            raise Exception('failed to delete user secret key')

        return response

    def secret_show_key(self):
        return self.api('GET', URI_SECRET_KEY)

    def add_webstorage_user(self, uid, namespace):
        parms = {
                'user': uid,
                'namespace': namespace
                }
        response = self.api('POST', URI_WEBSTORAGE_USER, parms)
        return response

    def remove_webstorage_user(self, uid):
        parms = {
                'user': uid
                }
        print "calling delete user api with uid: ", uid
        response = self.__api('POST', URI_WEBSTORAGE_USER_DEACTIVATE, parms)

    def list_webstorage_user(self):
        o = self.api('GET', URI_WEBSTORAGE_USER)
        users = o['users_list']
        ids = []
        if (not o):
           return ()
        else:
            if (type(users) != list):
                users = [users]
            for user in users:
                ids.append(user)
        return ids

    def verify_user(self, expected_user):
        response = self.api('GET', URI_WHOAMI)
        user = response['common_name']
        if(expected_user.lower() != user):
            raise Exception(user + " logged in but " + expected_user + " was expected")  
			
    def set_object_props(self, properties):
        params = self.to_object_props_params(properties)
        response = self.api_check_success('PUT', URI_OBJECT_PROPERTIES, params, None, CONTENT_TYPE_JSON, CONTENT_TYPE_JSON)
        return response

    def to_object_props_params(self, props):
        params = dict()
        #properties = dict()
        params['properties'] = props
        return params
        #params['properties'] = properties
        #properties['entry'] = []
        #for key, value in props.iteritems():
        #    entry = dict()
        #    entry['key'] = key
        #    entry['value'] = value
        #    properties['entry'].append(entry)
        #return params


    def get_object_props(self):
        response = self.api_check_success('GET', URI_OBJECT_PROPERTIES)
        return self.from_object_props_params(response)

    def from_object_props_params(self, params):
        return params['properties']
#        props = dict()
#        entry = params['properties']['entry']
#        if(isinstance(entry, dict)):
#            key = entry['key']
#            value = entry['value']
#            props[key] = value
#        else:
#            for entry in params['properties']['entry']:
#                print type(entry)
#                #print key
#                #print value
#                key = entry['key']
#                value = entry['value']
#                props[key] = value
#        return props

    def verify_user_roles(self, roles):
        response = self.api('GET', URI_WHOAMI)
        rolesRead = response['vdc_roles']
        rolesRead.extend(response['home_tenant_roles'])
        for role in roles.split(","):
            if (role not in rolesRead):
                raise Exception(role + " not found in list for this user, roles read " + str(rolesRead))
        print "Roles verified"

    def kickstart_get(self):
        response = self.__api('GET', URI_KICKSTART)
        if (response.status_code != 200):
            print "Connect to kickstart failed", response.status_code
            raise Exception('Connect to kickstart failed')

    def test_proxy_token(self):
        if SKIP_SECURITY == '1':
            return
        # as root
        response = self.__api('GET', URI_PROXY_TOKEN)
        if (response.status_code != 200):
            print "Could not get proxy token", response.status_code
            raise Exception('Get proxy token failed')
        h = response.headers
        proxytoken = h.get(SEC_PROXYTOKEN_HEADER, "")
        print 'proxy token: ', proxytoken
        if (SEC_PROXYTOKEN_HEADER in _headers):
            del _headers[SEC_PROXYTOKEN_HEADER]
        # as a non tenant_admin user
        if (SEC_AUTHTOKEN_HEADER in _headers):
            del _headers[SEC_AUTHTOKEN_HEADER]
        self.login(PROXY_USER_NAME, PROXY_USER_PASSWORD)
        id = self.tenant_getid()
        response2 = self.__api('GET', URI_TENANTS.format(id))
        if (response2.status_code != 403):
            print "Get tenant/id should have failed for this user, but got this code instead: ", response2.status_code
            raise Exception('Negative test for tenant/id failed.')
        # add proxy token
        self._headers[SEC_PROXYTOKEN_HEADER] = proxytoken
        response3 = self.__api('GET', URI_TENANTS.format(id))
        if (response3.status_code != 200):
            print "Get tenant/id should have succeeded with proxy token but did not: ", response3.status_code
            raise Exception('Proxy token test failed.')
        self._headers[SEC_PROXYTOKEN_HEADER] = ""
        print 'Proxy token test completed.'

    def test_password_change(self, user, password):
        if SKIP_SECURITY == '1':
            return
        parms = { 'password' : 'ChangeMe' }
        response = self.__api('PUT', URI_MY_PASSWORD_CHANGE, parms)
        if (response.status_code != 400):
            print "Did not get 400 when trying to change the password of the currently logged in user with the same value", response.status_code
            raise Exception('Password change for the logged in user with the same value failed to return an error')
        # Test another user password change
        parms = {
                    'username' : 'svcuser',
                    'password' : 'ChangeMe'
                }

        response = self.__api('PUT', URI_USER_PASSWORD_CHANGE, parms)
        if (response.status_code != 400):
            print "Did not get 400 when trying to change the password of the provided user with the same value", response.status_code
            raise Exception('Password change for the provided user with the same value failed to return an error')

    def audit_query(self, timeslot, language):
        print 'Querying audit logs (timeslot: ' + timeslot + ' language: ' + language + ') ...';
        response = self.__api('GET', URI_AUDIT_QUERY.format(timeslot, language))
        if (response.status_code != 200):
            print "Query audit logs failed", response.status_code
            raise Exception('Query audit logs failed')
        print 'Query audit logs succeed. Result: ' + response.text

    def test_formlogin(self, user, password):
        if SKIP_SECURITY == '1':
            return
        scheme = 'https://'
        ipaddr = self._ipaddr
        port=PORT
        cookiejar = cookielib.LWPCookieJar()
        if USE_SSL == '0':
            scheme = 'http://'
            ipaddr = ipaddr
            port   = '8080'

        if(port == APISVC_PORT):
            authsvcPort = '7443'
        elif(port == LB_API_PORT):
            authsvcPort = LB_API_PORT

        login_response = requests.get(scheme+ipaddr+':'+port+'/tenant?using-formlogin',
                                      headers=_headers, verify=False, cookies=cookiejar, allow_redirects=False)
        if(not login_response.status_code == SEC_REDIRECT):
            raise Exception('The first response to /tenant?using-formlogin is not redirect (302)')
        location = login_response.headers['Location']
        if(not location):
            raise Exception('The redirect location of the authentication service is not provided')
        # Make the second request. Should get 200 with form login page
        login_response = requests.get(location, headers=_headers, verify=False,
                                      cookies=cookiejar, allow_redirects=False)
        if(login_response.status_code != requests.codes['ok']):
            raise Exception('Failed to get custom login page. Failure code: '
                         + str(login_response.status_code) + ' Error: ' + login_response.text)

        # Get the formlogin page
        FORM_LOGIN_URI = scheme+ipaddr+':' + '4443' + '/formlogin?using-formlogin'
        login_response = requests.get(FORM_LOGIN_URI, verify=False, allow_redirects=False, cookies=cookiejar)
        if(login_response.status_code != requests.codes['ok']):
            raise Exception('Failed to get custom login page. Failure code: '
                            + str(login_response.status_code) + ' Error: ' + str(login_response.text) )
        # Check /formlogin
        login_response = requests.get(scheme+ipaddr+':'+authsvcPort+'/formlogin', verify=False, allow_redirects=False)
        if(login_response.status_code != 200):
            raise Exception('The request /formlogin should return 200, but is ' + str(login_response.status_code))
        # Check the formlogin with some service but without the signature.
        # The returned page should be the form login page
        FORM_LOGIN_URI_NO_SIGNATURE=FORM_LOGIN_URI + '&service=https://www.fake.com:1234/someservice'
        login_response = requests.get(FORM_LOGIN_URI_NO_SIGNATURE, verify=False, allow_redirects=False, cookies=cookiejar)
        if(login_response.status_code != 200):
            raise Exception('ERROR: The server failed to return 200 for service reguest to /formlogin. The return code is: '
                            + str(login_response.status_code))
        theContent=login_response.content
        match = re.search('action=".(.+?)"', theContent)
        if match:
            foundService = match.group(1)
        if (not match):
            raise Exception('ERROR: The server failed to return the service URI in the form login. The return code is: '
                            + str(login_response.status_code))
        POST_LOGIN_URI=scheme+ipaddr+':' + '4443' + foundService
        parms = {
            'username': 'root',
            'password': 'ChangeMe'
        }
        newHeaders=_headers
        newHeaders["Content-Type"] = "application/x-www-form-urlencoded"

        response = requests.post(POST_LOGIN_URI, headers=newHeaders, data=parms, verify=False, allow_redirects=False, cookies=cookiejar)
        print str(POST_LOGIN_URI)
        if(response.status_code != 302):
            raise Exception('ERROR: The server failed to return 302. The return code is: '
                            + str(response.status_code))
        # Check that the Location does not have the fake URL
        location = response.headers['Location']
        locationText = str(location)
        fakeURLFound = locationText.find('www.fake.com') != -1
        if (fakeURLFound):
            raise Exception('Failed to get redirected to the original request site.')

    def test_vulnerability(self, user, password):
        if SKIP_SECURITY == '1':
            return
        scheme = 'https://'
        ipaddr = self._ipaddr
        port=PORT
        cookiejar = cookielib.LWPCookieJar()
        # cookiejar_new will contain authentication token from /login request
        cookiejar_new = cookielib.LWPCookieJar()
        if USE_SSL == '0':
            scheme = 'http://'
            ipaddr = ipaddr
            port   = '8080'

        if(port == APISVC_PORT):
            authsvcPort = '7443'
        elif(port == LB_API_PORT):
            authsvcPort = LB_API_PORT
        # Variables
        FAKE_URL='https://www.fake.com'
        FAKE_URL_PATTERN='www.fake.com'
        FORM_LOGIN_FAKE_URL=scheme+ipaddr+':'+port+'/formlogin?using-formlogin=true&service='+FAKE_URL+'/tenant';
        LOGIN_FAKE_URL=scheme+ipaddr+':'+port+'/login?using-cookies=true&service='+FAKE_URL+'/tenant';
        # Test the /login URL
        login_response = requests.get(LOGIN_FAKE_URL, headers=_headers, 
                                      auth=(user,password), verify=False, cookies=cookiejar, allow_redirects=False)
        if(login_response.status_code != SEC_REDIRECT):
            print "test_vulnerability:login_response.status_code=" + str(login_response.status_code)
            raise Exception('The response to GET request to the auth service /login with proper credentials is not redirect (302)')
        location = login_response.headers['Location']
        if(not location):
            raise Exception('The redirect location of the GET request to the auth service /login is not provided')
        for cookie in login_response.cookies:
            cookiejar_new.set_cookie(cookie)
        # The location should not contain fake URL
        locationText = str(location)
        fakeURLFound = locationText.find(FAKE_URL_PATTERN) != -1
        if (fakeURLFound):
            raise Exception('GET /login with proper user credentials failed to get redirected to the request site instead of the fake site.')
        # GET /formlogin. It should return formlogin page, because we did not provide a token
        # but provided only user credentials. See CTRL-1830
        login_response = requests.get(FORM_LOGIN_FAKE_URL, headers=_headers, 
                                      auth=(user,password), verify=False, cookies=cookiejar, allow_redirects=False)
        if(login_response.status_code != requests.codes['ok']):
            print "test_vulnerability:login_response.status_code=" + str(login_response.status_code)
            raise Exception('The response to the GET request to the auth service /formlogin with proper credentials and without token is not OK')
        formlogin_page = login_response.content
        if(not formlogin_page):
            raise Exception('The GET request to /formlogin without a token did not produce the formlogin page')
        # Check that this is a real form login page by looking for FORMLOGIN_PATTERN
        FORMLOGIN_PATTERN='<title>ViPR Login</title>'
        formLoginCheck = formlogin_page.find(FORMLOGIN_PATTERN) != -1
        if (not formLoginCheck):
           raise Exception('GET /formlogin with proper token failed to return a proper form login page containing the pattern: ' + FORMLOGIN_PATTERN)
        # Repeat the GET request with a token
        login_response = requests.get(FORM_LOGIN_FAKE_URL, headers=_headers, 
                                      auth=(user,password), verify=False, cookies=cookiejar_new, allow_redirects=False)
        if(login_response.status_code != SEC_REDIRECT):
            print "test_vulnerability:login_response.status_code=" + str(login_response.status_code)
            raise Exception('The GET request to /formlogin with proper token did not redirect (302)')
        location = login_response.headers['Location']
        if(not location):
            raise Exception('The redirect location of the authentication service is not provided')
        # The location should not contain fake URL
        locationText = str(location)
        fakeURLFound = locationText.find(FAKE_URL_PATTERN) != -1
        if (fakeURLFound):
            raise Exception('GET /formlogin with proper token failed to get redirected to the request site instead of the fake site.')
        print "bourne.test_vulnerability finished OK"

    def test_tenant_access_permissions(self, user, password):
        if SKIP_SECURITY == '1':
            return
        ipaddr = self._ipaddr
        port=PORT
        cookiejar = cookielib.LWPCookieJar()
        id = self.tenant_getid()
        print "root tenant id: " + str(id)
        subtenants = self.tenant_list(id)
        for tenant in subtenants:
            print "subtenant id: " + str(tenant['id'])
            self.login(user, password)
            # try root tenant
            response = self.__api('GET', URI_PROJECTS.format(id))
            print "response status to get projects for root tenant = " + str(response.status_code)
            if (response.status_code != 200):
                print "The access to the projects of the root tenant should result in 200 status code"
                raise Exception('test_tenant_access_permissions: failed')
            # try the first subtenant
            response = self.__api('GET', URI_PROJECTS.format(tenant['id']))
            print "response status to get projects for the first subtenant = " + str(response.status_code)
            if (response.status_code != 403):
                print "The access to the root tenant/{ROOT_TENANT_ID}/projects are not allowed for the user user1@secureldap.com"
                print "The response status = " + str(response.status_code)
                raise Exception('test_tenant_access_permissions: failed')
            # try to get subtenants of the root subtenant. For user1 this operation should fail with error code 403
            response = self.__api('GET', URI_TENANTS_SUBTENANT.format(id))
            print "Response status to get subtenants of the root tenant = " + str(response.status_code)
            if (response.status_code != 403):
                print "The access to the tenants/{ROOT_TENANT_ID}/subtenants are not allowed for the user user1@secureldap.com"
                print "The response status = " + str(response.status_code)
                raise Exception('test_tenant_access_permissions: failed')
            # try to get subtenants of the subtenant. In V1 this operarion is not supported
            response = self.__api('GET', URI_TENANTS_SUBTENANT.format(tenant['id']))
            print "Response status to get subtenants of the subtenant = " + str(response.status_code)
            if (response.status_code != 405):
                print "The access to the tenants/{SUB_TENANT_ID}/subtenants are not allowed"
                print "The response status = " + str(response.status_code)
                raise Exception('test_tenant_access_permissions: failed')
            break

    def test_tenant_duplicate_message(self, domain, subtenantName, deactivateTenant):
        if SKIP_SECURITY == '1':
            return
        id = self.tenant_getid()
        if deactivateTenant == "true":
            # We create test subtenant only if this is the first test where the tenantID is extected.
            response = self.tenant_create(subtenantName, domain, "50", subtenantName)
        subtenants = self.tenant_list(id)
        foundNewSubtenant=False
        for tenant in subtenants:
            subtName = tenant['name']
            subtId = tenant['id']
            if (subtName == subtenantName):
                foundNewSubtenant=True
                if(BOURNE_DEBUG == '1'):
                    print "test_tenant_duplicate_message: got the subtenant ID: "+ str(subtId)
                if deactivateTenant == "true":
                    self.tenant_deactivate(subtId)
                response = self.tenant_create(subtenantName, domain, "50", subtenantName)
                if(BOURNE_DEBUG == '1'):
                    print "test_tenant_duplicate_message: the request to create a subtenant with duplicated mappings and name returns: "+ str(response)
                # Here we should get an error which contains the tenant URI which has the same user mappings
                rawresponse=str(response)
                entryFound = rawresponse.find("A component/resource with the label " + subtenantName + " already exists") != -1
                if deactivateTenant == "false":
                    if (not entryFound):
                        msg = 'Did not get the duplicate subtenant error while creating a subtenant with the same name and user mapping'
                        raise Exception(msg)
                break
        if(not foundNewSubtenant):
            raise Exception('test_tenant_duplicate_message: failed to find the newly created subtenant')

    def test_tenant_domain_update(self, user, password, domain, key, value):
        if SKIP_SECURITY == '1':
            return
        ipaddr = self._ipaddr
        port=PORT
        cookiejar = cookielib.LWPCookieJar()
        domainWithSpaces = "  " + domain + "    "
        id = self.tenant_getid()
        response = self.__api('GET', URI_TENANTS.format(id))
        rawresponse = response.text
        entryFound = rawresponse.find(domain) != -1
        if (entryFound):
        # remove the domain entry secureldap.com from root tenant
            self.tenant_update_domain(id, domainWithSpaces, "remove", key, value)
        # get the root tenant again to make sure that the domain secureldap.com is not there
        response = self.__api('GET', URI_TENANTS.format(id))
        rawresponse = response.text
        entryFound = rawresponse.find(domain) != -1
        if (entryFound):
            raise Exception('Failed to remove domain entry secureldap.com in the root tenant.')
        # add new domain with spaces before and after domain name
        self.tenant_update_domain(id, domainWithSpaces, "add", key, value)
        response = self.__api('GET', URI_TENANTS.format(id))
        rawresponse = response.text
        entryFound = rawresponse.find(domain) != -1
        if (not entryFound):
            raise Exception('Failed to add domain entry secureldap.com in the root tenant with spaces.')

    def test_logout(self, user):
        if SKIP_SECURITY == '1':
            return
        response = self.__api('GET', URI_LOGOUT)
        if (response.status_code != 200):
            print "logout failed with code: " + str(response.status_code)
            raise Exception('security logout: failed')
        rawresponse = response.text
        logoutOK = rawresponse.find(user) != -1
        if (not logoutOK):
            raise Exception('Failed to logout the user: ' + user)

    def monitor_query(self, timeslot, language):
        print 'Querying monitor logs (timeslot: ' + timeslot + ' language: ' + language + ') ...';
        response = self.__api('GET', URI_MONITOR_QUERY.format(timeslot))
        if (response.status_code != 200):
            print "Query monitor logs failed", response.status_code
            raise Exception('Query monitor logs failed')
        print 'Query monitor logs succeed. Result: ' + response.text

    # This routine will raise an exception of the obj passed
    # in is not a dictionary
    def assert_is_dict(self, obj):
        if (not type(obj) is dict):
            raise Exception(obj)

    def workflow_list(self):
        return self.api('GET', URI_WORKFLOW_LIST)

    def workflow_get(self, uri):
        return self.api('GET', URI_WORKFLOW_INSTANCE.format(uri))

    def workflow_recent(self):
        return self.api('GET', URI_WORKFLOW_RECENT)

    def workflow_steps(self, uri):
        return self.api('GET', URI_WORKFLOW_STEPS.format(uri))

    #
    # Compute Resources - Vcenter
    #
    def vcenter_create(self, label, tenant, ipaddress, devport,
                    username, password, osversion, usessl):
        uri = self.__tenant_id_from_label(tenant)
        parms = { 'name'            : label,
                   'ip_address'     : ipaddress,
                   'os_version'     : osversion,
                   'port_number'    : devport,
                   'user_name'      : username,
                   'password'       : password,
                   'use_ssl'        : usessl
                   }
        return self.api('POST', URI_TENANTS_VCENTERS.format(uri), parms)

    def vcenter_list(self, tenant):
        uri = self.__tenant_id_from_label(tenant)
        o = self.api('GET', URI_TENANTS_VCENTERS.format(uri), None)
        if (not o):
            return {}
        return o['vcenter']

    def vcenter_query(self, name):
        if (self.__is_uri(name)):
            return name
        label = name
        tenants = self.tenant_list(self.tenant_getid())
        # since we are using sysadmin as user, check on all subtenants for now
        # go in reverse order, most likely, we are in the latest subtenant
        for tenant in reversed(tenants):
            #print tenant
            vcenters = self.vcenter_list(tenant['id'])
            for vcenter in vcenters:
                if (vcenter['name'] == label):
                    return vcenter['id']
        raise Exception('bad vcenter name: ' + name)

    def vcenter_show(self, name):
        uri = self.vcenter_query(name)
        return self.api('GET', URI_VCENTER.format(uri))

    def vcenter_delete(self, name):
        uri = self.vcenter_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_VCENTER.format(uri)))

    #
    # Compute Resources - Vcenter Data Center
    #
    def datacenter_create(self, label, vcenter):
        uri = self.vcenter_query(vcenter)
        parms = { 'name'            : label
                   }
        return self.api('POST', URI_VCENTER_DATACENTERS.format(uri), parms)

    def datacenter_list(self, vcenter):
        uri = self.vcenter_query(vcenter)
        o = self.api('GET', URI_VCENTER_DATACENTERS.format(uri), None)
        if (not o):
            return {}
        return o['vcenter_data_center']

    def datacenter_query(self, name):
        if (self.__is_uri(name)):
            return name
        (vcenter, label) = name.split('/', 1)
        datacenters = self.datacenter_list(vcenter)
        for datacenter in datacenters:
            if (datacenter['name'] == label):
                return datacenter['id']
        raise Exception('bad datacenter name: ' + name)

    def datacenter_show(self, name):
        (vcenter, label)  = name.split('/', 1)
        vcenterUri = self.vcenter_query(vcenter)
        uri = self.datacenter_query(name)
        return self.api('GET', URI_DATACENTER.format(uri))

    def datacenter_delete(self, name):
        (vcenter, label) = name.split('/', 1)
        vcenterUri = self.vcenter_query(vcenter)
        uri = self.datacenter_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_DATACENTER.format(uri)))

    #
    # Compute Resources - Cluster
    #
    def cluster_create(self, label, tenant, project, datacenter):
        uri = self.__tenant_id_from_label(tenant)
        parms = { 'name'            : label
                   }
        if(project):
            parms['project'] = self.tenant_project_query(tenant, project)
            if (not parms['project']):
                raise Exception('Could not find project : ' + project + ' for tenant org ' + tenant)
        if(datacenter):
            parms['vcenter_data_center'] = self.datacenter_query(datacenter)
        return self.api('POST', URI_TENANTS_CLUSTERS.format(uri), parms)

    def cluster_list(self, tenant):
        uri = self.__tenant_id_from_label(tenant)
        o = self.api('GET', URI_TENANTS_CLUSTERS.format(uri), None)
        if (not o):
            return {}
        return o['cluster']

    def cluster_query(self, name):
        if (self.__is_uri(name)):
            return name
        (tenantLbl, label)  = name.split('/', 1)
        tenant = self.tenant_query(tenantLbl)
        clusters = self.cluster_list(tenant)
        for cluster in clusters:
            if (cluster['name'] == label):
                return cluster['id']
        raise Exception('bad cluster name: ' + name)

    def cluster_show(self, name):
        uri = self.cluster_query(name)
        return self.api('GET', URI_CLUSTER.format(uri))

    def cluster_delete(self, name):
        uri = self.cluster_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_CLUSTER.format(uri)))

    #
    # Compute Resources - Host
    #
    def host_create(self, label, tenant, type, hostname, devport,
                    username, password, osversion, usessl,
                    project, cluster, datacenter, discoverable):
        uri = self.__tenant_id_from_label(tenant)

        parms = { 'name'            : label,
                   'type'           : type,
                   'host_name'      : hostname,
                   'os_version'     : osversion,
                   'port_number'    : devport,
                   'user_name'      : username,
                   'password'       : password,
                   'use_ssl'        : usessl,
                   'discoverable'   : discoverable
                   }
        if(datacenter):
            parms['vcenter_data_center'] = self.datacenter_query(datacenter)
        if(cluster):
            parms['cluster'] = self.cluster_query(cluster)
        if(project):
            parms['project'] = self.tenant_project_query(tenant, project)
        return self.api('POST', URI_TENANTS_HOSTS.format(uri), parms)

    def host_update(self, uri, cluster):
        clusterURI = None
        if (cluster):
            clusterURI = self.cluster_query(cluster)

        parms = {
                    'cluster' : clusterURI
        }
        return self.api('PUT', URI_HOST.format(uri), parms);

    def host_list(self, tenant):
        uri = self.__tenant_id_from_label(tenant)
        o = self.api('GET', URI_TENANTS_HOSTS.format(uri), None)
        if (not o):
            return {}
        return o['host']

    def host_query(self, name):
        if (self.__is_uri(name)):
            return name
        label = name
        tenants = self.tenant_list(self.tenant_getid())
        # since we are using sysadmin as user, check on all subtenants for now
        # go in reverse order, most likely, we are in the latest subtenant
        for tenant in reversed(tenants):
            print tenant
            hosts = self.host_list(tenant['id'])
            for host in hosts:
	        host_detail = self.host_show(host['id'])
                if (host['name'] == label and host_detail['inactive'] == False):
                    return host['id']
        # also check the root tenant as a last result
        hosts = self.host_list(self.tenant_getid())
        for host in hosts:
	    host_detail = self.host_show(host['id'])
            if (host['name'] == label and host_detail['inactive'] == False):
                return host['id']
        raise Exception('bad host name: ' + name)

    def host_show(self, name):
        uri = self.host_query(name)
        return self.api('GET', URI_HOST.format(uri))

    def host_delete(self, name):
        uri = self.host_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_HOST.format(uri)))

    def initiator_show_tasks(self, uri):
	uri_initiator_task = URI_INITIATORS + '/tasks'
	return self.api('GET', uri_initiator_task.format(uri))

    def initiator_show_task(self, uri, task):
	uri_initiator_task = URI_INITIATOR + '/tasks/{1}'
	return self.api('GET', uri_initiator_task.format(uri, task))

    #
    # Compute Resources - host initiator
    #
    def initiator_create(self, host, protocol, port, node):
        uri = self.host_query(host)
        parms = { 'protocol': protocol,
                  'initiator_port'    : port,
                  'initiator_node'    : node,
                   }
        o = self.api('POST', URI_HOST_INITIATORS.format(uri), parms)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.initiator_show_task)
        return (o, s)

    def initiator_list(self, host):
        uri = self.host_query(host)
        o = self.api('GET', URI_HOST_INITIATORS.format(uri), None)
        if (not o):
            return {}
        return o['initiator']

    def initiator_query(self, name):
        if (name.find('/') == -1 and self.__is_uri(name)):
            return name
        (host, label) = name.split('/', 1)
        host_uri = self.host_query(host)
        initiators = self.initiator_list(host_uri)
        for initiator in initiators:
            if (initiator['name'] == label):
                return initiator['id']
        raise Exception('bad initiator port: ' + name)

    def initiator_show(self, name):
        (host, label)  = name.split('/', 1)
        hostUri = self.host_query(host)
        uri = self.initiator_query(name)
        return self.api('GET', URI_INITIATOR.format(uri))

    def initiator_delete(self, name):
        (host, label) = name.split('/', 1)
        hostUri = self.host_query(host)
        uri = self.initiator_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_INITIATOR.format(uri)))

    def initiator_register(self, name):
        uri = self.initiator_query(name)
        return self.api('POST', URI_INITIATOR_REGISTER.format(uri))

    def initiator_deregister(self, name):
        uri = self.initiator_query(name)
        return self.api('POST', URI_INITIATOR_DEREGISTER.format(uri))

    #
    # Compute Resources - host ipinterface
    #
    def ipinterface_create(self, host, protocol, ipaddress, netmask, prefix, scope):
        uri = self.host_query(host)
        parms = { 'protocol'        : protocol,
                  'ip_address'      : ipaddress,
                  'netmask'         : netmask,
                  'prefix_length'   : prefix,
                  'scope_id'        : scope
                   }
        return self.api('POST', URI_HOST_IPINTERFACES.format(uri), parms)

    def ipinterface_list(self, host):
        uri = self.host_query(host)
        print uri
        o = self.api('GET', URI_HOST_IPINTERFACES.format(uri), None)
        if (not o):
            return {}
        return o['ip_interface']

    def ipinterface_query(self, name):
        if (self.__is_uri(name)):
            return name
        (host, label) = name.split('/', 1)
        ipinterfaces = self.ipinterface_list(host)
        for ipinterface in ipinterfaces:
            if (ipinterface['name'] == label):
                return ipinterface['id']
        raise Exception('bad ipinterface ip address: ' + name)

    def ipinterface_show(self, name):
        (host, label)  = name.split('/', 1)
        hostUri = self.host_query(host)
        uri = self.ipinterface_query(name)
        return self.api('GET', URI_IPINTERFACE.format(uri))

    def ipinterface_delete(self, name):
        (host, label) = name.split('/', 1)
        hostUri = self.host_query(host)
        uri = self.ipinterface_query(name)
        return self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_IPINTERFACE.format(uri)))

    def ipinterface_register(self, name):
        uri = self.ipinterface_query(name)
        return self.api('POST', URI_IPINTERFACE_REGISTER.format(uri))

    def ipinterface_deregister(self, name):
        uri = self.ipinterface_query(name)
        return self.api('POST', URI_IPINTERFACE_DEREGISTER.format(uri))

    #
    # Retention classes
    #

    def retention_class_list(self, namespace):
        return self.api('GET', URI_NAMESPACE_RETENTION_BASE.format(namespace))

    def retention_class_get(self, namespace, name):
        return self.api('GET', URI_NAMESPACE_RETENTION_INSTANCE.format(namespace, name))

    def retention_class_create(self, namespace, name, period):
        params = {
            'name': name,
            'period': period
        }

        return self.api('POST', URI_NAMESPACE_RETENTION_BASE.format(namespace), params)

    def retention_class_update(self, namespace, name, period):
        params = {
            'period': period
        }

        return self.api('PUT', URI_NAMESPACE_RETENTION_INSTANCE.format(namespace, name), params)

    def set_bucket_retention(self, bucket, period):
        params = {
            'period': period
        }
        return self.api('PUT', URI_BUCKET_RETENTION.format(bucket), params)

    def get_bucket_retention(self, bucket):

        return self.api('GET', URI_BUCKET_RETENTION.format(bucket))

    def _build_update_bucket_owner_payload(self, namespace, newowner):
        root = ET.Element('object_bucket_update_owner')
        ET.SubElement(root, 'namespace').text = namespace
        ET.SubElement(root, 'new_owner').text = newowner
        return ET.tostring(root)

    def bucket_update_owner(self, namespace, bucket, newowner):
        uri = URI_BUCKET_UPDATE_OWNER.format(bucket)
        parms = self._build_update_bucket_owner_payload(namespace, newowner)
        response = self.coreapi('POST', uri, parms, content_type=CONTENT_TYPE_XML)
        if response.status_code != HTTP_OK:
            print "failure", response.status_code, response.text
            raise Exception('failed to update bucket owner')

    def vdcinfo_insert(self, name, secretkey, dataEndpoint, cmdEndpoint):
        parms = {
            'vdcName'            : name,
            'dataEndPoints'      : dataEndpoint,
            'cmdEndPoints'       : cmdEndpoint,
            'secretKeys'         : secretkey,
        }

        while True:
            print "VDC insert Params = ", parms
            try: 
                resp = self.coreapi('PUT', URI_VDCINFO_INSERT.format(name), parms, {})
                print resp.status_code
                if (resp.status_code != 200):
                    raise Exception("vdcinfo_insert failed, will retry")
                return resp
            except:
                time.sleep(10)
                continue

    def vdcinfo_show(self, uri):
        return self.api('GET', URI_VDCINFO_GET.format(uri))

    def vdcinfo_query(self, name):
        print 'name' , name
        if name.startswith('urn:storageos:VirtualDataCenterData'):
            return name
        vdcinfo = self.vdcinfo_show(name)
        print vdcinfo
        return vdcinfo['vdcId']

    def vdcinfo_local(self):
        return self.api('GET', URI_VDCINFO_LOCAL)

    def vdcinfo_list(self):
        return self.api('GET', URI_VDCINFO_LIST)

    def vnas_list(self):
        vnaslist = self.api('GET', URI_VNAS_SERVERS)
        if('vnas_server' in vnaslist):
            return vnaslist['vnas_server']
    
    def vnas_query(self, name):
        if name.startswith('urn:storageos:VirtualNAS'):
            return name
	vnasservers = self.vnas_list()
        for vnas in vnasservers:
            if('name' in vnas and vnas['name'] == name):
                return vnas['id']	
        raise Exception('bad vnas name ' + name)
		
    def vnas_show(self, name):
        vnasid = self.vnas_query(name)
	if(vnasid is not None):
	    return self.api('GET', URI_VNAS_SERVER.format(vnasid))
			
    def assign_vnas(self, name, project):
        vnasid = self.vnas_query(name)
	projectURI = self.project_query(project)
        params = dict()
        vnaslist = []
	if(projectURI is not None):
            vnaslist.append(vnasid)
            params['vnas_server'] = vnaslist
	    return self.api('PUT', URI_VNAS_SERVER_ASSIGN.format(projectURI), params)	

    def unassign_vnas(self, name, project):
        vnasid = self.vnas_query(name)
	projectURI = self.project_query(project)
        params = dict()
        vnaslist = []
	if(projectURI is not None):
            vnaslist.append(vnasid)
            params['vnas_server'] = vnaslist
            return self.api('PUT', URI_VNAS_SERVER_UNASSIGN.format(projectURI), params)			
		

    def unmanaged_volume_query(self, name):
        if (self.__is_uri(name)):
            return name
        results = self.un_managed_volume_search(name)
        resources = results['resource']
        for resource in resources:
             if (resource['match'] == name):
                 return resource['id']
        raise Exception('bad volume name ' + name)

    def un_managed_volume_search(self, name):
        if (self.__is_uri(name)):
            return name
        if (name):
            return  self.api('GET', URI_UNMANAGED_VOLUMES_SEARCH_NAME.format(name))
        

    def ingest_show_task(self, vol, task):
        uri_ingest_task = URI_VDC + '/tasks/{1}'
        return self.api('GET', uri_ingest_task.format(vol, task))

    def ingest_exported_volumes(self, host, cluster, varray, vpool, project, volspec):
        projectURI = self.project_query(project).strip()
        varrayURI = self.neighborhood_query(varray).strip()
        vpoolURI = self.cos_query("block", vpool).strip()

        params = {
            'project' : projectURI,
            'varray'  : varrayURI,
            'vpool'   : vpoolURI,
        }

        # Build volume parameter, if specified
        if (volspec):
           vols = volspec.split(',')
           volentry = []
           for vol in vols:
              volentry.append(self.unmanaged_volume_query(vol))
           params['unmanaged_volume_list'] = volentry

        if (host):
            hostURI = self.host_query(host)
            params['host'] = hostURI

        # Build cluster parameter, if specified
        if (cluster):
            clusterURI = self.cluster_query(cluster)
            params['cluster'] = clusterURI

        if(BOURNE_DEBUG == '1'):
            print str(parms)

        resp = self.api('POST', URI_UNMANAGED_EXPORTED_VOLUMES, params)
        self.assert_is_dict(resp)
        if('details' in resp):
           print "Failed operation: "+ resp['details']
           return resp;
        tr_list = resp['task']
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['id'], self.ingest_show_task)
            result.append(s)
        return result
    
    def ingest_unexported_volumes(self, varray, vpool, project, volspec):
        projectURI = self.project_query(project).strip()
        varrayURI = self.neighborhood_query(varray).strip()
        vpoolURI = self.cos_query("block", vpool).strip()
        params = {
            'project' : projectURI,
            'varray'  : varrayURI,
            'vpool'   : vpoolURI,
        }
        # Build volume parameter
        if (volspec):
           volentry = []
           vols = volspec.split(',')
           for vol in vols:
              volentry.append(self.unmanaged_volume_query(vol))
           params['unmanaged_volume_list'] = volentry
        
        if(BOURNE_DEBUG == '1'):
            print str(params)
        
        resp = self.api('POST', URI_UNMANAGED_UNEXPORTED_VOLUMES, params)
        self.assert_is_dict(resp)
        if('details' in resp):
           print "Failed operation: "+ resp['details']
           return resp;
        tr_list = resp['task']
        result = list()
        for tr in tr_list:
            s = self.api_sync_2(tr['resource']['id'], tr['id'], self.ingest_show_task)
            result.append(s)
        return result

    #
    # ECS Bucket oprations
    #
    def ecs_bucket_show_task(self, bkt, task):
        uri_bucket_task = URI_ECS_BUCKET + '/tasks/{1}'
        return self.api('GET', uri_bucket_task.format(bkt, task))

    def ecs_bucket_create(self, label, project, neighbourhood, cos,
						soft_quota, hard_quota, owner):
		params = {
			'name'          : label,
			'varray'        : neighbourhood,
			'vpool'         : cos,
   			'soft_quota'    : soft_quota,
			'hard_quota'    : hard_quota,
			'owner'         : owner
			}

		print "ECS BUCKET CREATE Params = ", params
		o = self.api('POST', URI_ECS_BUCKET_LIST, params, {'project': project})
		self.assert_is_dict(o)
		s = self.api_sync_2(o['resource']['id'], o['op_id'], self.ecs_bucket_show_task)
		return s

    # input param to be changed to label
    def ecs_bucket_delete(self, uri):
        params = {
        'forceDelete'   : 'false'
        }

        print "ECS bucket delete = ", URI_RESOURCE_DEACTIVATE.format(URI_ECS_BUCKET.format(uri), params)
        o = self.api('POST', URI_RESOURCE_DEACTIVATE.format(URI_ECS_BUCKET.format(uri)), params)
        self.assert_is_dict(o)
        s = self.api_sync_2(o['resource']['id'], o['op_id'], self.ecs_bucket_show_task)
        return (o, s)

