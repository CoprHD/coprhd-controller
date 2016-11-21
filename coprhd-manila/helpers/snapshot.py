# Copyright (c)2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

from manila.share.drivers.coprhd.helpers import common as commoncoprhdapi
import manila.share.drivers.coprhd.helpers.fileshare as fileshare
import manila.share.drivers.coprhd.helpers.tag as tag
from manila.share.drivers.coprhd.helpers import volume
from manila.share.drivers.coprhd.helpers import consistencygroup
import json
import time
from threading import Timer
from manila.share.drivers.coprhd.helpers.common import SOSError


class Snapshot(object):

    # The class definition for operations on 'Snapshot'.

    # Commonly used URIs for the 'Snapshot' module
    URI_SNAPSHOTS = '/{0}/snapshots/{1}'
    URI_BLOCK_SNAPSHOTS = '/block/snapshots/{0}'
    URI_FILE_SNAPSHOTS = '/file/snapshots/{0}'
    URI_BLOCK_SNAPSHOTS_SEARCH = '/block/snapshots/search'
    URI_FILE_SNAPSHOTS_SEARCH = '/file/snapshots/search'
    URI_SEARCH_SNAPSHOT_BY_TAG = '/block/snapshots/search?tag={0}'
    URI_BLOCK_SNAPSHOTS_SEARCH_BY_PROJECT_AND_NAME \
        = URI_BLOCK_SNAPSHOTS_SEARCH + "?project={0}&name={1}"
    URI_FILE_SNAPSHOTS_SEARCH_BY_PROJECT_AND_NAME \
        = URI_FILE_SNAPSHOTS_SEARCH + "?project={0}&name={1}"
    URI_SNAPSHOT_LIST = '/{0}/{1}/{2}/protection/snapshots'
    URI_SNAPSHOT_EXPORTS = '/{0}/snapshots/{1}/exports'
    URI_SNAPSHOT_MODIFY_EXPORTS = '/file/snapshots/{0}/export'
    URI_SNAPSHOT_SHOW_EXPORTS = '/file/snapshots/{0}/export'
    URI_SNAPSHOT_UNEXPORTS_FILE = '/file/snapshots/{0}/export'
    URI_SNAPSHOT_VOLUME_EXPORT = '/{0}/snapshots/{1}/exports'
    URI_SNAPSHOT_UNEXPORTS_VOL = URI_SNAPSHOT_EXPORTS + '/{2},{3},{4}'
    URI_FILE_SNAPSHOT_SHARES = '/file/snapshots/{0}/shares'
    URI_FILE_SNAPSHOT_UNSHARE = URI_FILE_SNAPSHOT_SHARES + '/{1}'
    URI_SNAPSHOT_RESTORE = '/{0}/snapshots/{1}/restore'
    URI_BLOCK_SNAPSHOTS_ACTIVATE = '/{0}/snapshots/{1}/activate'

    URI_FILE_SNAPSHOT_TASKS = '/{0}/snapshots/{1}/tasks'
    URI_SNAPSHOT_TASKS_BY_OPID = '/vdc/tasks/{0}'

    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'

    URI_CONSISTENCY_GROUP = "/block/consistency-groups"
    URI_CONSISTENCY_GROUPS_SNAPSHOT = URI_CONSISTENCY_GROUP + \
        "/{0}/protection/snapshots"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE \
        = URI_CONSISTENCY_GROUP + "/{0}/protection/snapshots/{1}"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_ACTIVATE \
        = URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/activate"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE \
        = URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/deactivate"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE \
        = URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/restore"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_RESYNC \
        = URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/resynchronize"

    URI_BLOCK_SNAPSHOTS_TAG = URI_BLOCK_SNAPSHOTS + '/tags'
    URI_FILE_SNAPSHOTS_TAG = URI_FILE_SNAPSHOTS + '/tags'
    URI_CONSISTENCY_GROUP_TAG = URI_CONSISTENCY_GROUP + '/{0}/tags'
    URI_SNAPSHOT_RESYNC = '/{0}/snapshots/{1}/resynchronize'
    
    URI_VPLEX_SNAPSHOT_IMPORT = '/block/snapshots/{0}/expose'
    
    URI_CIFS_ACL = URI_FILE_SNAPSHOTS + '/shares/{1}/acl'
    

    SHARES = 'filesystems'
    VOLUMES = 'volumes'
    OBJECTS = 'objects'
    CG = 'consistency-groups'

    FILE = 'file'
    BLOCK = 'block'
    OBJECT = 'object'
    
    TYPE_REPLIC_LIST = ["NATIVE", "RP", "SRDF"]
    BOOLEAN_TYPE = ["true" ,"false"]

    isTimeout = False
    timeout = 300

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Given the project name and snapshot name,
    the search will be performed to find
    if the snapshot with the given name exists or not.
    If found, the uri of the snapshot
    will be returned
    '''

    def search_blocktype_by_project_and_name(self, projectName, snapshotName):

        return (
            commoncoprhdapi.search_by_project_and_name(
                projectName,
                snapshotName,
                Snapshot.URI_BLOCK_SNAPSHOTS_SEARCH_BY_PROJECT_AND_NAME,
                self.__ipAddr,
                self.__port)
        )

    '''
    Given the project name and snapshot name,
    the search will be performed to find
    if the snapshot with the given name exists or not.
    If found, the uri of the snapshot
    will be returned
    '''

    def search_filetype_by_project_and_name(self, projectName, snapshotName):

        return (
            commoncoprhdapi.search_by_project_and_name(
                projectName,
                snapshotName,
                Snapshot.URI_FILE_SNAPSHOTS_SEARCH_BY_PROJECT_AND_NAME,
                self.__ipAddr,
                self.__port)
        )

    def snapshot_create(self, otype, typename, ouri,
                        snaplabel, inactive, rptype, sync ,readonly=False,synctimeout=0):
        '''new snapshot is created, for a given shares or volumes
            parameters:
                otype      : either file or block or object
                type should be provided
                typename   : either filesystem or volume
                or consistency-groups should be provided
                ouri       : uri of filesystems or volume
                snaplabel  : name of the snapshot
                activate   : activate snapshot in vnx and vmax
                rptype     : type of replication
        '''

        # check snapshot is already exist
        is_snapshot_exist = True
        try:
            self.snapshot_query(otype, typename, ouri, snaplabel)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(is_snapshot_exist):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Snapshot with name " +
                snaplabel +
                " already exists under " +
                typename)

        body = None
        
        if(otype == Snapshot.BLOCK):
            parms = {
                'name': snaplabel,
                # if true, the snapshot will not activate the synchronization
                # between source and target volumes
                'create_inactive': inactive
            }
            if(rptype):
                parms['type'] = rptype
            if(readonly == "true"):
                parms['read_only'] = readonly
            body = json.dumps(parms)

        else:
            parms = {
                'name': snaplabel
            }
            if(readonly == "true"):
                parms['read_only'] = readonly
            body = json.dumps(parms)
        
        
        # REST api call
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Snapshot.URI_SNAPSHOT_LIST.format(otype, typename, ouri), body)
        o = commoncoprhdapi.json_decode(s)

        task = None
        if(otype == Snapshot.BLOCK):
            task = o["task"][0]
        else:
            task = o

        if(sync):
            return (
                self.block_until_complete(
                    otype,
                    task['resource']['id'],
                    task["id"],synctimeout)
            )
        else:
            return o

    def snapshot_show_task_opid(self, otype, snap, taskid):
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_SNAPSHOT_TASKS_BY_OPID.format(taskid),
            None)
        if (not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        return o

    def snapshot_show_task(self, otype, suri):
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_FILE_SNAPSHOT_TASKS.format(otype, suri),
            None)
        if (not s):
            return []
        o = commoncoprhdapi.json_decode(s)
        return o["task"]

    def snapshot_list_uri(self, otype, otypename, ouri):
        '''
        Makes REST API call to list snapshot under a shares or volumes
         parameters:
            otype     : either file or block or object type should be provided
            otypename : either filesystem or volumes
            or consistency-groups should be provided
            ouri      : uri of filesystem or volumes or consistency-group

        Returns:
            return list of snapshots
        '''
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_SNAPSHOT_LIST.format(otype, otypename, ouri), None)
        o = commoncoprhdapi.json_decode(s)
        return o['snapshot']

    def snapshot_list(self, otype, otypename, filesharename,
                      volumename, cg, project, tenant):
        resourceUri = self.storageResource_query(
            otype,
            filesharename,
            volumename,
            cg,
            project,
            tenant)
        if(resourceUri is not None):
            return self.snapshot_list_uri(otype, otypename, resourceUri)
        return None

    def snapshot_show_uri(self, otype, resourceUri, suri, xml=False):
        '''
        Retrieves snapshot details based on snapshot Name or Label
        Parameters:
            otype : either file or block
            suri : uri of the Snapshot.
            resourceUri: uri of the source resource
            typename: either filesystem or volumes
            or consistency-groups should be provided
        Returns:
            Snapshot details in JSON response payload
        '''
        if(resourceUri is not None and
           resourceUri.find('BlockConsistencyGroup') > 0):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE.format(
                    resourceUri,
                    suri),
                None,
                None, xml)
        else:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Snapshot.URI_SNAPSHOTS.format(otype, suri), None, None, xml)

        if(xml is False):
            return commoncoprhdapi.json_decode(s)
        return s

    def snapshot_show(self, storageresType,
                      storageresTypename, resourceUri, name, xml):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return (
            self.snapshot_show_uri(
                storageresType,
                resourceUri,
                snapshotUri,
                xml)
        )

    '''Delete a snapshot by uri
        parameters:
            otype : either file or block
            suri : Uri of the Snapshot.
    '''

    def snapshot_delete_uri(self, otype, resourceUri, suri, sync,synctimeout):
        s = None
        if(otype == Snapshot.FILE):

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_RESOURCE_DEACTIVATE.format(
                    Snapshot.URI_FILE_SNAPSHOTS.format(suri)),
                None)
        elif(resourceUri.find("Volume") > 0):

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_RESOURCE_DEACTIVATE.format(
                    Snapshot.URI_BLOCK_SNAPSHOTS.format(suri)),
                None)
        elif(resourceUri.find("BlockConsistencyGroup") > 0):

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE.format(
                    resourceUri,
                    suri),
                None)
        o = commoncoprhdapi.json_decode(s)
        task = None
        if(otype == Snapshot.BLOCK):
            task = o["task"][0]
        else:
            task = o

        if(sync):
            return (
                self.block_until_complete(
                    otype,
                    task['resource']['id'],
                    task["id"],synctimeout)
            )
        else:
            return o

    def snapshot_delete(self, storageresType,
                        storageresTypename, resourceUri, name, sync,synctimeout=0):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        self.snapshot_delete_uri(
            storageresType,
            resourceUri,
            snapshotUri,
            sync,synctimeout)

    def snapshot_restore(self, storageresType,
                         storageresTypename, resourceUri, name, sync,synctimeout=0, syncdirection=None):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return (
            self.snapshot_restore_uri(
                storageresType,
                storageresTypename,
                resourceUri,
                snapshotUri,
                sync,synctimeout,
                syncdirection)
        )

    def snapshot_restore_uri(self, otype, typename, resourceUri, suri, sync,synctimeout=0,syncdirection=None):
        ''' Makes REST API call to restore Snapshot under a shares or volumes
            parameters:
                otype    : either file or block or
                object type should be provided
                typename : either filesystem or volumes should be provided
                suri     : uri of a snapshot
                resourceUri: base resource uri

            returns:
                restore the snapshot
        '''
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            if(syncdirection is not None):
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE = Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE + "?" + "syncDirection=" + syncdirection
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE.format(
                    resourceUri,
                    suri), None)
        else:
            if(syncdirection is not None):
                Snapshot.URI_SNAPSHOT_RESTORE = Snapshot.URI_SNAPSHOT_RESTORE + "?" + "syncDirection=" + syncdirection
                
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_RESTORE.format(otype, suri), None)
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o
        
    
    
    def snapshot_resync(self, storageresType,
                         storageresTypename, resourceUri, name, sync,synctimeout=0):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return (
            self.snapshot_resync_uri(
                storageresType,
                storageresTypename,
                resourceUri,
                snapshotUri,
                sync,synctimeout)
        )
        
    
    
    def import_snapshot_vplex(self, storageresType,
                         storageresTypename, resourceUri, name):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return (
            self.snapshot_vplex_import_uri(
                storageresType,
                storageresTypename,
                resourceUri,
                snapshotUri)
        )

    def snapshot_resync_uri(self, otype, typename, resourceUri, suri, sync,synctimeout=0):
        ''' Makes REST API call to resync Snapshot under a shares or volumes
            parameters:
                otype    : either file or block or
                object type should be provided
                typename : either filesystem or volumes should be provided
                suri     : uri of a snapshot
                resourceUri: base resource uri

            returns:
                
        '''
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESYNC.format(
                    resourceUri,
                    suri), None)
        else:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_RESYNC.format(otype, suri), None)
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o
        
        
    
    
    
    def snapshot_vplex_import_uri(self, otype, typename, resourceUri, suri):
        ''' Makes REST API call to import Snapshot as vplex volume 
            parameters:
                otype    : block should be provided
                typename : volumes should be provided
                suri     : uri of a snapshot
                resourceUri: base resource uri

            returns:
                
        '''
        
        (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_VPLEX_SNAPSHOT_IMPORT.format(suri), None)
        o = commoncoprhdapi.json_decode(s)

  
        return o
    
    
    
        

    def snapshot_activate_uri(self, otype, typename, resourceUri, suri, sync,synctimeout=0):

        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_ACTIVATE.format(
                    resourceUri,
                    suri),
                None)
        else:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_BLOCK_SNAPSHOTS_ACTIVATE.format(otype, suri),
                None)
        o = commoncoprhdapi.json_decode(s)
        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o

    def snapshot_activate(self, storageresType,
                          storageresTypename, resourceUri, name, sync):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        snapshotUri = snapshotUri.strip()
        return (
            self.snapshot_activate_uri(
                storageresType,
                storageresTypename,
                resourceUri,
                snapshotUri,
                sync)
        )

    def snapshot_export_file_uri(
            self, otype, suri, permissions, securityType, protocol,
            rootUserMapping, endpoints,
            sharename,
            description,
            permission_type,
            sync, subdir,synctimeout=0):

        o = None
        if(protocol == "NFS"):

            parms = {'type': securityType,
                     'permissions': permissions,
                     'root_user': rootUserMapping,
                     'endpoints': endpoints,
                     'protocol': protocol
                     }
            if(subdir):
                parms["sub_directory"] = subdir
            body = json.dumps(parms)
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_EXPORTS.format(otype, suri),
                body)
            o = commoncoprhdapi.json_decode(s)

        else:
            parms = {
                'name': sharename,
                'description': description,
                'permission_type': permission_type
            }
            if(subdir):
                parms["subDirectory"] = subdir
            if(permissions and permissions in ["read", "change", "full"]):
                parms["permission"] = permissions
            body = json.dumps(parms)
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Snapshot.URI_FILE_SNAPSHOT_SHARES.format(suri), body)
            o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o

    def snapshot_export_file(
            self, storageresType, storageresTypename, resourceUri, name,
            permissions,
            securityType,
            protocol,
            rootUserMapping,
            endpoints,
            sharename,
            description,
            permission_type,
            sync, subdir,synctimeout=0):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.snapshot_export_file_uri(storageresType, snapshotUri,
                                             permissions,
                                             securityType,
                                             protocol,
                                             rootUserMapping,
                                             endpoints,
                                             sharename, description,
                                             permission_type, sync, subdir,synctimeout)

    ''' export a snapshot of a volume to given host.
        parameters:
            otype            : Either file or block
            suri            : URI of snapshot
            protocol        : FC or iSCSI
            host_id         : Physical address of the host
            initiator       : Port of host (combination of WWNN
            and WWPN for FC and IQN for ISCSI)
            hlu             : HLU
            sync            : syncronize of a task
    '''

    def snapshot_export_volume_uri(self,
                                   otype,
                                   suri,
                                   host_id,
                                   protocol,
                                   initiatorPort,
                                   initiatorNode,
                                   hlu,
                                   sync,synctimeout):
        body = json.dumps({
            'host_id': host_id,
            'initiator_port': initiatorPort,
            'initiator_node': initiatorNode,
            'lun': hlu,
            'protocol': protocol
        })
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Snapshot.URI_SNAPSHOT_EXPORTS.format(otype, suri), body)
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o

    def snapshot_export_volume(
            self, storageresType, storageresTypename, resourceUri, name,
            host_id,
            protocol,
            initiatorPort,
            initiatorNode,
            hlu,
            sync,synctimeout):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.snapshot_export_volume_uri(
            storageresType, snapshotUri, host_id,
            protocol,
            initiatorPort,
            initiatorNode,
            hlu,
            sync,synctimeout)
    ''' Unexport a snapshot of a filesystem
        parameters:
            otype         : either file or block
            suri          : uri of snapshot
            perm          : Permission (root, rw, ro, etc..)
            securityType  : Security(sys, krb5, etc..)
            protocol      : protocol to be used (NFS, CIFS)
            root_user     : user name
            sync          : synchronous task
    '''

    def snapshot_unexport_file_uri(self,
                                   otype,
                                   suri,
                                   protocol,
                                   sharename,
                                   sync,synctimeout=0):

        o = None
        if(protocol == "NFS"):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "DELETE",
                Snapshot.URI_SNAPSHOT_UNEXPORTS_FILE.format(suri),
                None)
            o = commoncoprhdapi.json_decode(s)
        else:

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "DELETE",
                Snapshot.URI_FILE_SNAPSHOT_UNSHARE.format(suri, sharename),
                None)
            o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o

    def snapshot_unexport_file(
            self, storageresType, storageresTypename, resourceUri, name,
            protocol,
            sharename,
            sync,synctimeout=0):

        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.snapshot_unexport_file_uri(
            storageresType, snapshotUri, protocol,
            sharename, sync,synctimeout)

    '''
        Unexport a snapshot of a volume.
        parameters:
            otype        : either file or block
            suri         : uri of snapshot
            protocol     : protocol type
            initiator    : port of host (combination of
            WWNN and WWPN for FC and IQN for ISCSI)
            hlu          : logical unit number'
            sync         : synchronous task
        '''

    def snapshot_unexport_volume_uri(
            self, otype, suri, protocol, initiator, hlu, sync,synctimeout):
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "DELETE",
            Snapshot.URI_SNAPSHOT_UNEXPORTS_VOL.format(otype,
                                                       suri,
                                                       protocol,
                                                       initiator,
                                                       hlu),
            None)
        o = commoncoprhdapi.json_decode(s)
        if(sync):
            return self.block_until_complete(otype, suri, o["id"],synctimeout)
        else:
            return o

    def snapshot_unexport_volume(
            self, storageresType, storageresTypename, resourceUri, name,
            protocol,
            initiator_port,
            hlu, sync,synctimeout):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return (
            self.snapshot_unexport_volume_uri(
                storageresType,
                snapshotUri,
                protocol,
                initiator_port,
                hlu,
                sync,synctimeout)
        )

    def get_exports_by_uri(self, otype, uri, subDir=None, allDir=None):
        '''
        Get a snapshot exports based on snapshot uri
           Parameters:
              uri: uri of snapshot
        '''
        params = ''
        if (subDir):
            params += '&' if ('?' in params) else '?'
            params += "subDir=" + subDir
        elif (allDir):
            params += '&' if ('?' in params) else '?'
            params += "allDir=" + "true"

        (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port,
                    "GET",
                    Snapshot.URI_SNAPSHOT_SHOW_EXPORTS.format(uri) + params,
                    None)
        if(not s):
            return None
        else:
            return commoncoprhdapi.json_decode(s)

    def get_snapshot_exports(self, storageresType,
                        storageresTypename, resourceUri, name, subDir, allDir):
        '''
        Get a snapshot exports based on snapshot name
        Parameters:
            name: name of snapshot
        '''
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.get_exports_by_uri(storageresType, snapshotUri, subDir, allDir)

    def export_rule(self, storageresType, storageresTypename, resourceUri, name, operation, securityflavor, user=None, roothosts=None, readonlyhosts=None, readwritehosts=None, subDir=None):
        
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)

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
            request = {'add': exportRulerequest, 'subDir' : subDir}
        elif("delete" == operation):
            request = {'delete' : exportRulerequest, 'subDir' : subDir}
        else:
            request = {'modify' : exportRulerequest, 'subDir' : subDir}

            
        body = json.dumps(request)
	
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT", Snapshot.URI_SNAPSHOT_MODIFY_EXPORTS.format(snapshotUri), body)
        o = commoncoprhdapi.json_decode(s)
        return o


    def get_shares_by_uri(self, uri):
        '''
        Get a snapshot shares based on snapshot uri
           Parameters:
              uri: uri of snapshot
        '''
        (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port,
                    "GET",
                    Snapshot.URI_FILE_SNAPSHOT_SHARES.format(uri),
                    None)
        if(not s):
            return None
        else:
            return commoncoprhdapi.json_decode(s)

    def get_snapshot_shares(self, storageresType,
                        storageresTypename, resourceUri, name):
        '''
        Get a snapshot shares based on snapshot name
        Parameters:
            name: name of snapshot
        '''
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.get_shares_by_uri(snapshotUri)

    def snapshot_query(self, storageresType,
                       storageresTypename, resuri, snapshotName):
        if(resuri is not None):
            uris = self.snapshot_list_uri(
                storageresType,
                storageresTypename,
                resuri)
            for uri in uris:
                snapshot = self.snapshot_show_uri(
                    storageresType,
                    resuri,
                    uri['id'])
                if(False == (commoncoprhdapi.get_node_value(snapshot, 'inactive'))):
                    if (snapshot['name'] == snapshotName):
                        return snapshot['id']

        raise SOSError(
            SOSError.SOS_FAILURE_ERR,
            "snapshot with the name:" +
            snapshotName +
            " Not Found")

    def storageResource_query(self,
                              storageresType,
                              fileshareName,
                              volumeName,
                              cgName,
                              project,
                              tenant):
        resourcepath = "/" + project + "/"
        if(tenant is not None):
            resourcepath = tenant + resourcepath

        resUri = None
        resourceObj = None
        if(Snapshot.FILE == storageresType):
            resourceObj = fileshare.Fileshare(self.__ipAddr, self.__port)
            resUri = resourceObj.fileshare_query(resourcepath + fileshareName)
        elif(Snapshot.BLOCK == storageresType and volumeName is not None):
            resourceObj = volume.Volume(self.__ipAddr, self.__port)
            resUri = resourceObj.volume_query(resourcepath + volumeName)
        elif(Snapshot.BLOCK == storageresType and cgName is not None):
            resourceObj = consistencygroup.ConsistencyGroup(
                self.__ipAddr,
                self.__port)
            resUri = resourceObj.consistencygroup_query(
                cgName,
                project,
                tenant)
        else:
            resourceObj = None

        return resUri

    def get_storageAttributes(self, fileshareName, volumeName, cgName):
        storageresType = None
        storageresTypeName = None
        if(fileshareName is not None):
            storageresType = Snapshot.FILE
            storageresTypeName = Snapshot.SHARES
        elif(volumeName is not None):
            storageresType = Snapshot.BLOCK
            storageresTypeName = Snapshot.VOLUMES
        elif(cgName is not None):
            storageresType = Snapshot.BLOCK
            storageresTypeName = Snapshot.CG
        else:
            storageresType = None
            storageresTypeName = None
        return (storageresType, storageresTypeName)

    # Timeout handler for synchronous operations
    def timeout_handler(self):
        self.isTimeout = True

    # Blocks the opertaion until the task is complete/error out/timeout
    def block_until_complete(self, storageresType, resuri, task_id,synctimeout=0):
        if synctimeout:
            t = Timer(synctimeout, self.timeout_handler)
        else:
            t = Timer(self.timeout, self.timeout_handler)
        t.start()
        while(True):
            #out = self.show_by_uri(id)
            out = self.snapshot_show_task_opid(storageresType, resuri, task_id)

            if(out):
                if(out["state"] == "ready"):
                    # cancel the timer and return
                    t.cancel()
                    break
                # if the status of the task is 'error' then cancel the timer
                # and raise exception
                if(out["state"] == "error"):
                    # cancel the timer
                    t.cancel()
                    error_message = "Please see logs for more details"
                    if("service_error" in out and
                       "details" in out["service_error"]):
                        error_message = out["service_error"]["details"]
                    raise SOSError(
                        SOSError.VALUE_ERR,
                        "Task: " +
                        task_id +
                        " is failed with error: " +
                        error_message)

            if(self.isTimeout):
                print "Operation timed out"
                self.isTimeout = False
                break
        return

    def snapshot_tag(self, storageresType, storageresTypename,
                     resourceUri, name, add, remove):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        self.snapshot_tag_uri(
            storageresType,
            resourceUri,
            snapshotUri,
            add,
            remove)

    def snapshot_tag_uri(self, otype, resourceUri, suri, add, remove):
        if(otype == Snapshot.FILE):
            return (
                tag.tag_resource(
                    self.__ipAddr,
                    self.__port,
                    Snapshot.URI_FILE_SNAPSHOTS_TAG,
                    suri,
                    add,
                    remove)
            )

        elif(resourceUri.find("Volume") > 0 or resourceUri.find("BlockConsistencyGroup") > 0):
            return (
                tag.tag_resource(
                    self.__ipAddr,
                    self.__port,
                    Snapshot.URI_BLOCK_SNAPSHOTS_TAG,
                    suri,
                    add,
                    remove)
            )
            
    # update acl for given snapshot's cifs    
    def cifs_snapshot_acl(self, tenant, project, snapshotname, sharename, operation, resourceUri, user=None, permission=None, domain=None, group=None): 
        snapshotUri = self.snapshot_query(
            Snapshot.FILE,
            Snapshot.SHARES,
            resourceUri,
            snapshotname)
        
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
        else:
            request = {'modify' : acl_cifs_request}
    
        body = json.dumps(request)
        
        (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, 
                    "PUT", 
                    Snapshot.URI_CIFS_ACL.format(snapshotUri, sharename) , body)
        o = commoncoprhdapi.json_decode(s)
        return o
    
    # Deletes a snapshot share's acl given a snapshot share name
    def delete_acl(self, snapshotname, snapshotsharename, resourceUri):
        '''
        Deletes a snapshot share's acl based on  snapshot's share name
        Parameters:
            snapshotsharename: name of snapshot share
        
        '''
        
        snapshotUri = self.snapshot_query(
            Snapshot.FILE,
            Snapshot.SHARES,
            resourceUri,
            snapshotname)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Snapshot.URI_CIFS_ACL.format(snapshotUri, snapshotsharename),
            None)
        return 
    
    def list_acl(self, snapshotname, snapshotsharename, resourceUri):
        '''
        Lists the acl for a snapshot's share given the share name:
            snapshotsharename: name of snapshot share
        '''
        snapshotUri = self.snapshot_query(
            Snapshot.FILE,
            Snapshot.SHARES,
            resourceUri,
            snapshotname)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Snapshot.URI_CIFS_ACL.format(snapshotUri, snapshotsharename),
            None)
        o = commoncoprhdapi.json_decode(s)
        return o          
    

        

# Snapshot Create routines




def snapshot_create(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        if(storageresType == Snapshot.FILE and args.inactive is True):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                "-inactive option is used for block type only")
        if(args.consistencygroup and args.type):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                ' Parameter -type is not applicable ' +
                'for consistency group snapshot')
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        obj.snapshot_create(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.inactive,
            args.type,
            args.sync,
            args.readonly,args.synctimeout)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot: " +
                args.name +
                ", fshare Failed\n" +
                e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "create",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot List routines



def snapshot_list(args):

    obj = Snapshot(args.ip, args.port)
    try:
        resourcepath = "/" + args.project + "/"
        if(args.tenant is not None):
            resourcepath = args.tenant + resourcepath

        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        uris = obj.snapshot_list(
            storageresType,
            storageresTypename,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)

        records = []
        for uri in uris:
            snapshot_obj = obj.snapshot_show_uri(
                storageresType,
                resourceUri,
                uri['id'])
            if(False == (commoncoprhdapi.get_node_value(snapshot_obj, 'inactive'))):
                records.append(snapshot_obj)

        if(len(records) > 0):
            if(args.verbose is True):
                if(len(records) > 0):
                    return commoncoprhdapi.format_json_object(records)
                else:
                    return
            else:
                # name is displayed twice, so delete 'name' in other sections
                # of attribute
                for record in records:
                    if("fs_exports" in record):
                        del record["fs_exports"]

                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                if(args.long is True):
                    if(storageresType == Snapshot.FILE):
                        TableGenerator(
                            records,
                            ['name',
                             'mount_path',
                             'tags']).printTable()
                    else:  # table should updated
                        TableGenerator(
                            records,
                            ['name',
                             'is_sync_active',
                             'wwn',
                             'tags']).printTable()

                else:
                    if(storageresType == Snapshot.FILE):
                        TableGenerator(records, ['name']).printTable()
                    else:
                        TableGenerator(
                            records, ['name', 'is_sync_active']).printTable()
        else:
            return

    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise(
            "list",
            "snapshot",
            e.err_text,
            e.err_code)

# Snapshot Show routines





def snapshot_show(args):
    obj = Snapshot(args.ip, args.port)
    try:
        # get URI name
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        respContent = obj.snapshot_show(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.xml)

        if(args.xml):
            return commoncoprhdapi.format_xml(respContent)
        else:
            return commoncoprhdapi.format_json_object(respContent)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "snapshot " +
                args.name +
                ": Not Found")
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "show",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot Delete routines





def snapshot_delete(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        obj.snapshot_delete(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.sync,args.synctimeout)
        return
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": Delete Failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "delete",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot Export file routines





def snapshot_export_file(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:

        if(args.protocol == "CIFS"):
            if(args.share is None and args.description is None):
                print('-share, -description are required for CIFS protocol' +
                      '(smb share)')
                return
        else:
            if(args.permission is None or args.security is None
               or args.rootuser is None or args.endpoints is None):
                print('-endpoints, -permission, -security and -rootuser' +
                      ' are required for NFS protocol')
                return

        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, None, None)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            None,
            None,
            args.project,
            args.tenant)
        res = obj.snapshot_export_file(
            storageresType, storageresTypename, resourceUri, args.name,
            args.permission,
            args.security,
            args.protocol,
            args.rootuser,
            args.endpoints,
            args.share,
            args.description,
            args.permission_type,
            args.sync,
            args.subdir,args.synctimeout)
        if(args.sync is False):
            return
            # return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot: " +
                args.name +
                ", Export failed\n" +
                e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "export-file",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot volume file routines





def snapshot_export_volume(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            None, args.volume)
        resourceUri = obj.storageResource_query(
            storageresType,
            None,
            args.volume,
            args.project,
            args.tenant)
        res = obj.snapshot_export_volume(
            storageresType, storageresTypename, resourceUri, args.name,
            args.hostid,
            args.protocol,
            args.initiator_port,
            args.initiator_node,
            args.hlu,
            args.sync,args.synctimeout)
        if(args.sync is False):
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot: " +
                args.name +
                ", Export failed\n" +
                e.err_text)
        else:
            raise e

# Snapshot Unexport routines





def snapshot_unexport_file(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        if(args.protocol == "CIFS"):
            if(args.share is None):
                print '-share name is required for CIFS protocol.'
                return
        
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, None, None)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            None,
            None,
            args.project,
            args.tenant)

        res = obj.snapshot_unexport_file(
            storageresType, storageresTypename, resourceUri, args.name,
            args.protocol,
            args.share,
            args.sync,args.synctimeout)
        if(args.sync is False):
            return
            # return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name
                           + ", Unexport for file is failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "unexport-file",
                "snapshot",
                e.err_text,
                e.err_code)





def snapshot_unexport_volume(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            None, args.volume)
        resourceUri = obj.storageResource_query(
            storageresType,
            None,
            args.volume,
            args.project,
            args.tenant)
        res = obj.snapshot_unexport_volume(
            storageresType, storageresTypename, resourceUri, args.name,
            args.protocol,
            args.initiatorPort,
            args.hlu,
            args.sync,args.synctimeout)
        if(args.sync is False):
            return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name
                           + ", Unexport for volume  is failed\n" + e.err_text)
        else:
            raise e





def show_snapshot_exports(args):
    obj = Snapshot(args.ip, args.port)
    try:
        resourceUri = obj.storageResource_query(
            Snapshot.FILE,
            args.filesystem,
            None,
            None,
            args.project,
            args.tenant)
        res = obj.get_snapshot_exports(
            Snapshot.FILE,
            Snapshot.SHARES,
            resourceUri,
            args.name,
            args.subDir,
            args.allDir)
        if(res):
            return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": show-exports Failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "show-exports", "snapshot", e.err_text, e.err_code)






def snapshot_export_rule(args):
    obj = Snapshot(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.roothosts and not args.readonlyhosts and not args.readwritehosts):
            raise SOSError(
            SOSError.CMD_LINE_ERR, "At least one of the arguments : roothosts or readonlyhosts or readwritehosts should be provided to add/Update/delete the export rule")
        if(not args.user):
            raise SOSError(
            SOSError.CMD_LINE_ERR, "Anonymous user should be provided to add/update export rule")

        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, None, None)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            None,
            None,
            args.project,
            args.tenant)
        res = obj.export_rule(
            storageresType, storageresTypename, resourceUri, args.name,
            args.operation,
            args.securityflavor,
            args.user,
            args.roothosts,
            args.readonlyhosts, 
            args.readwritehosts,
            args.subdirectory)

        if(res):
            return commoncoprhdapi.format_json_object(res)

    
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": Delete Failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "export-rule",
                "snapshot",
                e.err_text,
                e.err_code)
    

def show_snapshot_shares(args):
    obj = Snapshot(args.ip, args.port)
    try:
        resourceUri = obj.storageResource_query(
            Snapshot.FILE,
            args.share,
            None,
            None,
            args.project,
            args.tenant)
        res = obj.get_snapshot_shares(
            Snapshot.FILE,
            Snapshot.SHARES,
            resourceUri,
            args.name)
        if(res):
            return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": show-shares Failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "show-shares", "snapshot", e.err_text, e.err_code)





def snapshot_activate(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            None, args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            None,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        snapshotUri = obj.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name)
        snapshotUri = snapshotUri.strip()
        start = time.time()
        obj.snapshot_activate_uri(
            storageresType,
            storageresTypename,
            resourceUri,
            snapshotUri,
            args.sync,args.synctimeout)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": activate Failed\n" +
                e.err_text)
        else:
            raise e
# Snapshot restore routines,





def snapshot_restore(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        obj.snapshot_restore(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.sync,args.synctimeout,
            args.syncdirection)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": Restore Failed\n" +
                e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "restore",
                "snapshot",
                e.err_text,
                e.err_code)
            

#SNAPSHOT RESYNC PARSER




def snapshot_resync(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            None , args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            None,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        obj.snapshot_resync(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.sync,args.synctimeout)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": Resyncronization Failed\n" +
                e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "resync",
                "snapshot",
                e.err_text,
                e.err_code) 
            





def import_snapshot_vplex(args):
    obj = Snapshot(args.ip, args.port)
    try:
        (storageresType, storageresTypename) = obj.get_storageAttributes(
            None, args.volume, None)
        resourceUri = obj.storageResource_query(
            storageresType,
            None,
            args.volume,
            None,
            args.project,
            args.tenant)
        obj.import_snapshot_vplex(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": Failed to Import to VPLEX\n" +
                e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "vplex import ",
                "snapshot",
                e.err_text,
                e.err_code)           
          



# Snapshot tasks routines


def snapshot_tasks(args):

    obj = Snapshot(args.ip, args.port)
    try:
        if(args.id is not None):
            if(args.name is None):
                print '-name <snapshotname> is required for opids'

        resourcepath = "/" + args.project + "/"
        if(args.tenant is not None):
            resourcepath = args.tenant + resourcepath

        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        uris = obj.snapshot_list(
            storageresType,
            storageresTypename,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        # for a given snapshot, get all actions

        all_tasks = []
        # get all snapshot opids(actions) under a volume or filesystem
        if(args.name is None and args.id is None):
            for suri in uris:
                taskslist = obj.snapshot_show_task(storageresType, suri['id'])
                if(taskslist and len(taskslist) > 0):
                    all_tasks += taskslist
        else:  # get all snapshot opids for a given snapshot name
            snapshot_ob = None
            resourceUri = obj.storageResource_query(
                storageresType,
                args.filesystem,
                args.volume,
                args.consistencygroup,
                args.project,
                args.tenant)
            for suri in uris:
                snapshot_ob = obj.snapshot_show_uri(
                    storageresType,
                    resourceUri,
                    suri['id'])
                if(snapshot_ob['name'] == args.name):
                    break
                else:
                    snapshot_ob = None

            # if snapshot object in found, then call for tasks
            if(snapshot_ob is not None):

                # get opids details, for a specific operation id
                if(args.id is not None):
                    # return task object in json format
                    return (
                        commoncoprhdapi.format_json_object(
                            obj.snapshot_show_task_opid(storageresType,
                                                        suri['id'],
                                                        args.id))
                    )
                else:  # get all opids for given snapshot name
                    taskslist = obj.snapshot_show_task(
                        storageresType,
                        suri['id'])
                    if(taskslist and len(taskslist) > 0):
                        all_tasks += taskslist
            else:
                raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot - " + str(args.name) + ": Not Found")

        if(args.verbose):
            return commoncoprhdapi.format_json_object(all_tasks)
        # display in table format (all task for given file share/volume or
        # snapshot name)
        else:
            if(all_tasks and len(all_tasks) > 0):
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(
                    all_tasks,
                    ["module/id",
                     "name",
                     "state"]).printTable()

    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise(
            "tasks",
            "snapshot",
            e.err_text,
            e.err_code)





def snapshot_tag(args):
    obj = Snapshot(args.ip, args.port)
    try:
        if(args.add is None and args.remove is None):
            raise SOSError(SOSError.CMD_LINE_ERR, 'viprcli snapshot tag:' +
                           ' error: at least one of ' +
                           "the arguments -add -remove is required")

        (storageresType, storageresTypename) = obj.get_storageAttributes(
            args.filesystem, args.volume, args.consistencygroup)
        resourceUri = obj.storageResource_query(
            storageresType,
            args.filesystem,
            args.volume,
            args.consistencygroup,
            args.project,
            args.tenant)
        obj.snapshot_tag(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.add,
            args.remove)
        return
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": Tag Failed\n" + e.err_text)
        else:
            commoncoprhdapi.format_err_msg_and_raise(
                "tag",
                "snapshot",
                e.err_text,
                e.err_code)
            

# cifs snapshot acl update parser


def acl_set(args):
    obj = Snapshot(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.user and not args.permission):
            raise SOSError(SOSError.CMD_LINE_ERR, "Anonymous user should be provided to add/update/delete acl rule")
        if(args.user and args.group):
            raise SOSError(SOSError.CMD_LINE_ERR, "User and Group cannot be specified together")    
        
        resourceUri = obj.storageResource_query(
            Snapshot.FILE,
            args.filesystemName,
            None,
            None,
            args.project,
            args.tenant)
        res = obj.cifs_snapshot_acl(args.tenant, args.project, 
                           args.snapshotname, 
                           args.share,
                           args.operation,
                           resourceUri,
                           args.user, 
                           args.permission,
                           args.domain,
                           args.group)


    except SOSError as e:
                
        commoncoprhdapi.format_err_msg_and_raise("modify-acl", "snapshot",
                                        e.err_text, e.err_code)



def fileshare_acl_delete(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Snapshot(args.ip, args.port)
    resourceUri = obj.storageResource_query(
            Snapshot.FILE,
            args.filesystemname,
            None,
            None,
            args.project,
            args.tenant)
    try:
        obj.delete_acl(args.snapshotname,
            args.share, resourceUri)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("delete-acl", "snapshot",
                                        e.err_text, e.err_code)
        
        
# routine to list the acls of a share .

def fileshare_acl_list(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Snapshot(args.ip, args.port)
    resourceUri = obj.storageResource_query(
            Snapshot.FILE,
            args.filesystemname,
            None,
            None,
            args.project,
            args.tenant)
    try:
        res = obj.list_acl(args.snapshotname,
            args.share, resourceUri)
        if ( len(res) == 0):
            print " No ACLs for the share"
        else:
            from manila.share.drivers.coprhd.helpers.common import TableGenerator
            TableGenerator(res['acl'], ['errorType','snapshot_id','permission','share_name','user','group']).printTable() 
        
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("list-acl", "snapshot",
                                        e.err_text, e.err_code)

#
# Snapshot Main parser routine
#

