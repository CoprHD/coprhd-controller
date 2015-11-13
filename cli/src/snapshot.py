# Copyright (c)2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
import fileshare
import tag
import volume
import consistencygroup
import json
import time
from threading import Timer
from common import SOSError


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
    
    URI_VPLEX_SNAPSHOT_IMPORT = '/block/snapshots/{0}/create-vplex-volume'
    

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
            common.search_by_project_and_name(
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
            common.search_by_project_and_name(
                projectName,
                snapshotName,
                Snapshot.URI_FILE_SNAPSHOTS_SEARCH_BY_PROJECT_AND_NAME,
                self.__ipAddr,
                self.__port)
        )

    def snapshot_create(self, otype, typename, ouri,
                        snaplabel, inactive, rptype, sync ,readonly=False):
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Snapshot.URI_SNAPSHOT_LIST.format(otype, typename, ouri), body)
        o = common.json_decode(s)

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
                    task["id"])
            )
        else:
            return o

    def snapshot_show_task_opid(self, otype, snap, taskid):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_SNAPSHOT_TASKS_BY_OPID.format(taskid),
            None)
        if (not s):
            return None
        o = common.json_decode(s)
        return o

    def snapshot_show_task(self, otype, suri):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_FILE_SNAPSHOT_TASKS.format(otype, suri),
            None)
        if (not s):
            return []
        o = common.json_decode(s)
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Snapshot.URI_SNAPSHOT_LIST.format(otype, otypename, ouri), None)
        o = common.json_decode(s)
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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE.format(
                    resourceUri,
                    suri),
                None,
                None, xml)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Snapshot.URI_SNAPSHOTS.format(otype, suri), None, None, xml)

        if(xml is False):
            return common.json_decode(s)
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

    def snapshot_delete_uri(self, otype, resourceUri, suri, sync):
        s = None
        if(otype == Snapshot.FILE):

            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_RESOURCE_DEACTIVATE.format(
                    Snapshot.URI_FILE_SNAPSHOTS.format(suri)),
                None)
        elif(resourceUri.find("Volume") > 0):

            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_RESOURCE_DEACTIVATE.format(
                    Snapshot.URI_BLOCK_SNAPSHOTS.format(suri)),
                None)
        elif(resourceUri.find("BlockConsistencyGroup") > 0):

            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE.format(
                    resourceUri,
                    suri),
                None)
        o = common.json_decode(s)
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
                    task["id"])
            )
        else:
            return o

    def snapshot_delete(self, storageresType,
                        storageresTypename, resourceUri, name, sync):
        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        self.snapshot_delete_uri(
            storageresType,
            resourceUri,
            snapshotUri,
            sync)

    def snapshot_restore(self, storageresType,
                         storageresTypename, resourceUri, name, sync):
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
                sync)
        )

    def snapshot_restore_uri(self, otype, typename, resourceUri, suri, sync):
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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE.format(
                    resourceUri,
                    suri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_RESTORE.format(otype, suri), None)
        o = common.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
        else:
            return o
        
    
    
    def snapshot_resync(self, storageresType,
                         storageresTypename, resourceUri, name, sync):
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
                sync)
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

    def snapshot_resync_uri(self, otype, typename, resourceUri, suri, sync):
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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_RESYNC.format(
                    resourceUri,
                    suri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_RESYNC.format(otype, suri), None)
        o = common.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
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
        
        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_VPLEX_SNAPSHOT_IMPORT.format(suri), None)
        o = common.json_decode(s)

  
        return o
    
    
    
        

    def snapshot_activate_uri(self, otype, typename, resourceUri, suri, sync):

        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_CONSISTENCY_GROUPS_SNAPSHOT_ACTIVATE.format(
                    resourceUri,
                    suri),
                None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_BLOCK_SNAPSHOTS_ACTIVATE.format(otype, suri),
                None)
        o = common.json_decode(s)
        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
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
            sync, subdir):

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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Snapshot.URI_SNAPSHOT_EXPORTS.format(otype, suri),
                body)
            o = common.json_decode(s)

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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Snapshot.URI_FILE_SNAPSHOT_SHARES.format(suri), body)
            o = common.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
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
            sync, subdir):
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
                                             permission_type, sync, subdir)

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
                                   sync):
        body = json.dumps({
            'host_id': host_id,
            'initiator_port': initiatorPort,
            'initiator_node': initiatorNode,
            'lun': hlu,
            'protocol': protocol
        })
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Snapshot.URI_SNAPSHOT_EXPORTS.format(otype, suri), body)
        o = common.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
        else:
            return o

    def snapshot_export_volume(
            self, storageresType, storageresTypename, resourceUri, name,
            host_id,
            protocol,
            initiatorPort,
            initiatorNode,
            hlu,
            sync):
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
            sync)
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
                                   sync):

        o = None
        if(protocol == "NFS"):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "DELETE",
                Snapshot.URI_SNAPSHOT_UNEXPORTS_FILE.format(suri),
                None)
            o = common.json_decode(s)
        else:

            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "DELETE",
                Snapshot.URI_FILE_SNAPSHOT_UNSHARE.format(suri, sharename),
                None)
            o = common.json_decode(s)

        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
        else:
            return o

    def snapshot_unexport_file(
            self, storageresType, storageresTypename, resourceUri, name,
            protocol,
            sharename,
            sync):

        snapshotUri = self.snapshot_query(
            storageresType,
            storageresTypename,
            resourceUri,
            name)
        return self.snapshot_unexport_file_uri(
            storageresType, snapshotUri, protocol,
            sharename, sync)

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
            self, otype, suri, protocol, initiator, hlu, sync):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "DELETE",
            Snapshot.URI_SNAPSHOT_UNEXPORTS_VOL.format(otype,
                                                       suri,
                                                       protocol,
                                                       initiator,
                                                       hlu),
            None)
        o = common.json_decode(s)
        if(sync):
            return self.block_until_complete(otype, suri, o["id"])
        else:
            return o

    def snapshot_unexport_volume(
            self, storageresType, storageresTypename, resourceUri, name,
            protocol,
            initiator_port,
            hlu, sync):
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
                sync)
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

        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port,
                    "GET",
                    Snapshot.URI_SNAPSHOT_SHOW_EXPORTS.format(uri) + params,
                    None)
        if(not s):
            return None
        else:
            return common.json_decode(s)

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
	
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT", Snapshot.URI_SNAPSHOT_MODIFY_EXPORTS.format(snapshotUri), body)
        o = common.json_decode(s)
        return o


    def get_shares_by_uri(self, uri):
        '''
        Get a snapshot shares based on snapshot uri
           Parameters:
              uri: uri of snapshot
        '''
        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port,
                    "GET",
                    Snapshot.URI_FILE_SNAPSHOT_SHARES.format(uri),
                    None)
        if(not s):
            return None
        else:
            return common.json_decode(s)

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
                if(False == (common.get_node_value(snapshot, 'inactive'))):
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
    def block_until_complete(self, storageresType, resuri, task_id):
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

        

# Snapshot Create routines

def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser('create',
                                                  description='ViPR Snapshot' +
                                                  'Create CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='create a snapshot')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    create_parser.add_argument('-inactive', '-ci',
                               dest='inactive',
                               help='This option allows the snapshot to be ' +
                               'create by without activating the ' +
                               'synchronization',
                               action='store_true')
    create_parser.add_argument('-type', '-t',
                               help='This option creates a bookmark of a ' +
                               'specific type, such as RP, SRDF, NATIVE',
                               dest='type',
                               choices=Snapshot.TYPE_REPLIC_LIST,
                               metavar='<type>')
    create_parser.add_argument('-readonly', '-ro',
                               help='This option creates a snapshot in Read Only mode ' ,
                               dest='readonly',
                               choices=Snapshot.BOOLEAN_TYPE)

    create_parser.add_argument('-synchronous', '-sync',
                               dest='synchronous',
                               help='Synchronous snapshot create',
                               action='store_true')

    group = create_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-filesystem', '-fs',
                       metavar='<filesystemname>',
                       dest='filesystem',
                       help='Name of filesystem')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of a consistencygroup')

    create_parser.set_defaults(func=snapshot_create)


def snapshot_create(args):

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
            args.synchronous,
            args.readonly)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot: " +
                args.name +
                ", Create Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "create",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot List routines


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
                                                description='ViPR Snapshot' +
                                                'List CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='list a snapshots')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    list_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant',
                             required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    arggroup = list_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-filesystem', '-fs',
                          metavar='<filesystemname>',
                          dest='filesystem',
                          help='Name of filesystem')
    arggroup.add_argument('-volume', '-vol',
                          metavar='<volumename>',
                          dest='volume',
                          help='Name of a volume')
    arggroup.add_argument('-consistencygroup', '-cg',
                          metavar='<consistencygroup>',
                          dest='consistencygroup',
                          help='Name of a consistencygroup')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List snapshots with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List snapshots in table with details',
                             action='store_true')

    list_parser.set_defaults(func=snapshot_list)


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
            if(False == (common.get_node_value(snapshot_obj, 'inactive'))):
                records.append(snapshot_obj)

        if(len(records) > 0):
            if(args.verbose is True):
                if(len(records) > 0):
                    return common.format_json_object(records)
                else:
                    return
            else:
                # name is displayed twice, so delete 'name' in other sections
                # of attribute
                for record in records:
                    if("fs_exports" in record):
                        del record["fs_exports"]

                from common import TableGenerator
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
        common.format_err_msg_and_raise(
            "list",
            "snapshot",
            e.err_text,
            e.err_code)

# Snapshot Show routines


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                                                description='ViPR Snapshot' +
                                                ' Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='show a snapshot details')

    group = show_parser.add_argument_group('mandatory arguments')
    group.add_argument('-name', '-n',
                       metavar='<snapshotname>',
                       dest='name',
                       help='Name of Snapshot',
                       required=True)
    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant',
                             required=False)
    group.add_argument('-project', '-pr',
                       metavar='<projectname>',
                       dest='project',
                       help='Name of project',
                       required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    mutex_group = show_parser.add_mutually_exclusive_group(required=True)

    mutex_group.add_argument('-volume', '-vol',
                             metavar='<volumename>',
                             dest='volume',
                             help='Name of a volume')
    mutex_group.add_argument('-filesystem', '-fs',
                             metavar='<filesystemname>',
                             dest='filesystem',
                             help='Name a filesystem')
    mutex_group.add_argument('-consistencygroup', '-cg',
                             metavar='<consistencygroup>',
                             dest='consistencygroup',
                             help='Name of a consistencygroup')

    show_parser.set_defaults(func=snapshot_show)


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
            return common.format_xml(respContent)
        else:
            return common.format_json_object(respContent)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "snapshot " +
                args.name +
                ": Not Found")
        else:
            common.format_err_msg_and_raise(
                "show",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot Delete routines


def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser('delete',
                                                  description='ViPR Snapshot' +
                                                  ' Delete CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='delete a snapshot')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)

    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    group = delete_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-filesystem', '-fs',
                       metavar='<filesystemsname>',
                       dest='filesystem',
                       help='Name of filesystem')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of a consistencygroup')

    delete_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Synchronous snapshot delete',
                               action='store_true')

    delete_parser.set_defaults(func=snapshot_delete)


def snapshot_delete(args):
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
            args.sync)
        return
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": Delete Failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "delete",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot Export file routines


def export_file_parser(subcommand_parsers, common_parser):
    export_parser = subcommand_parsers.add_parser('export-file',
                                                  description='ViPR Snapshot' +
                                                  ' Export CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='export a snapshot of' +
                                                  ' filesystem')

    mandatory_args = export_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of snapshot for export/share',
                                required=True)
    mandatory_args.add_argument('-filesystem', '-fs',
                                metavar='<filesystemname>',
                                dest='filesystem',
                                help='Name of filesystem',
                                required=True)
    export_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='access protocol for' +
                                'this export ( NFS | CIFS )',
                                choices=["NFS", "CIFS"],
                                dest='protocol',
                                required=True)

    export_parser.add_argument('-security', '-sec',
                               metavar='<securitytype>',
                               dest='security',
                               help='security type' +
                               '(sys | krb5 | krb5i | krb5p)',
                               required=False)
    export_parser.add_argument('-permission', '-pe',
                               metavar='<permission>',
                               dest='permission',
                               help='file share access permission',
                               required=False)
    export_parser.add_argument('-rootuser', '-ru',
                               metavar='<root_user>',
                               dest='rootuser',
                               help='root user mapping for anonymous accesses',
                               required=False)
    export_parser.add_argument('-endpoints', '-ep',
                               metavar='<endpoint>',
                               dest='endpoints',
                               help='list of client endpoints' +
                               ' (ip|net|netgroup)',
                               nargs='+',
                               required=False)
    export_parser.add_argument('-share', '-sh',
                               help='Share Name(should be used for CIFS' +
                               ' protocol only)',
                               metavar='<sharename>',
                               dest='share',
                               required=False)
    export_parser.add_argument('-description', '-desc',
                               help='Description of the share(should be used' +
                               ' for CIFS protocol only))',
                               metavar='<description>',
                               dest='description',
                               required=False)
    export_parser.add_argument('-subdir', 
                               help='Name of the Subdirectory',
                               metavar='<subdir>',
                               dest='subdir',
                               required=False)
    export_parser.add_argument('-permission_type', '-pt',
        choices=['allow', 'deny'],
        help='Type of permission of SMB share, Default is allow',
        dest='permission_type',
        default='allow')
    export_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Synchronous snapshot export file',
                               action='store_true')

    mandatory_args.set_defaults(func=snapshot_export_file)


def snapshot_export_file(args):

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
            args.subdir)
        if(args.sync is False):
            return
            # return common.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot: " +
                args.name +
                ", Export failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "export-file",
                "snapshot",
                e.err_text,
                e.err_code)

# Snapshot volume file routines


def export_volume_parser(subcommand_parsers, common_parser):
    export_parser = subcommand_parsers.add_parser('export-volume',
                                                  description='ViPR Snapshot' +
                                                  ' Export volume CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='export a snapshot of' +
                                                  ' volume')

    mandatory_args = export_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    export_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volumename>',
                                dest='volume',
                                help='Name of a volume',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='access protocol for this export' +
                                ' (FC | iSCSI)',
                                choices=["FC", "iSCSI"],
                                dest='protocol',
                                required=True)
    mandatory_args.add_argument('-initiator_port', '-inp',
                                metavar='<initiator_port>',
                                dest='initiator_port',
                                help='Initiator port name  (WWPN for FC and' +
                                ' IQN for ISCSI)',
                                required=True)
    mandatory_args.add_argument('-initiator_node', '-inn',
                                metavar='<initiator_node>',
                                dest='initiator_node',
                                help='Initiator node name (WWNN)',
                                required=True)
    mandatory_args.add_argument('-hlu', '-hl',
                                metavar='<lun>',
                                dest='hlu',
                                help='host logical unit number -- should be' +
                                ' unused on the host',
                                required=False)
    mandatory_args.add_argument('-hostid', '-ho',
                                metavar='<hostid>',
                                dest='hostid',
                                help='Physical address of the host',
                                required=True)

    export_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Synchronous snapshot export',
                               action='store_true')

    mandatory_args.set_defaults(func=snapshot_export_volume)


def snapshot_export_volume(args):

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
            args.sync)
        if(args.sync is False):
            return common.format_json_object(res)
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


def unexport_file_parser(subcommand_parsers, common_parser):
    unexport_parser = subcommand_parsers.add_parser('unexport-file',
                                                    description='ViPR' +
                                                    'Snapshot Unexport' +
                                                    ' file CLI usage.',
                                                    parents=[common_parser],
                                                    conflict_handler='resolve',
                                                    help='unexport a' +
                                                    ' snapshot of filesystem')

    mandatory_args = unexport_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of snapshot for unshare/unexport',
                                required=True)
    mandatory_args.add_argument('-filesystem', '-fs',
                                metavar='<filesystemname>',
                                dest='filesystem',
                                help='Name of filesystem',
                                required=True)
    unexport_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant',
                                 required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='access protocol for' +
                                ' this export (NFS | CIFS) ',
                                choices=["NFS", "CIFS"],
                                dest='protocol',
                                required=True)

    unexport_parser.add_argument('-share', '-sh',
                                 help='Sharename to unshare',
                                 metavar='<sharename>',
                                 dest='share',
                                 required=False)

    unexport_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Synchronous snapshot unexport file',
                                 action='store_true')

    unexport_parser.set_defaults(func=snapshot_unexport_file)


def snapshot_unexport_file(args):

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
            args.sync)
        if(args.sync is False):
            return
            # return common.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name
                           + ", Unexport for file is failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "unexport-file",
                "snapshot",
                e.err_text,
                e.err_code)


def unexport_volume_parser(subcommand_parsers, common_parser):
    unexport_parser = subcommand_parsers.add_parser('unexport-volume',
                                                    description='ViPR' +
                                                    ' Snapshot Unexport' +
                                                    ' volume CLI usage.',
                                                    parents=[common_parser],
                                                    conflict_handler='resolve',
                                                    help='unexport a' +
                                                    ' snapshot of volume')

    mandatory_args = unexport_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    mandatory_args.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant',
                                required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volumename>',
                                dest='volume',
                                help='Name of a volume',
                                required=True)

    mandatory_args.add_argument('-initiatorPort', '-inp',
                                metavar='<initiatorPort>',
                                dest='initiatorPort',
                                help='Port of host (combination of WWNN and ' +
                                'WWPN for FC and IQN for ISCSI)',
                                required=True)
    mandatory_args.add_argument('-hlu', '-hl',
                                metavar='<hlu>',
                                dest='hlu',
                                help='Host Logical Unit',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='block protocols (FC | iSCSI)',
                                choices=["FC", "iSCSI"],
                                dest='protocol',
                                required=True)

    unexport_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Synchronous snapshot unexport',
                                 action='store_true')

    unexport_parser.set_defaults(func=snapshot_unexport_volume)


def snapshot_unexport_volume(args):

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
            args.sync)
        if(args.sync is False):
            return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name
                           + ", Unexport for volume  is failed\n" + e.err_text)
        else:
            raise e


def show_snapshot_exports_parser(subcommand_parsers, common_parser):
    show_exports_parser = subcommand_parsers.add_parser(
        'show-exports',
        description='ViPR Snapshot Show exports CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show export details of snapshot')
    mandatory_args = show_exports_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    mandatory_args.add_argument('-filesystem', '-fs',
                            metavar='<filesystemsname>',
                            dest='filesystem',
                            help='Name of filesystem for snapshot',
                            required=True)
    show_exports_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant',
                                     required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_exports_parser.add_argument('-subDir', 
                                     metavar='<subDirectory>',
                                     dest='subDir',
                                     help='Name of the Sub Directory')
    show_exports_parser.add_argument('-allDir',
                                     dest='allDir',
                                     action='store_true',
                                     help='Show File Sysem exports for All Directories')
    show_exports_parser.set_defaults(func=show_snapshot_exports)


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
            return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": show-exports Failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "show-exports", "snapshot", e.err_text, e.err_code)

def export_rule_parser(subcommand_parsers, common_parser):
    export_rule_parser = subcommand_parsers.add_parser(
        'export-rule',
        description='ViPR Snapshot Export rule CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add/Update/Delete Export rules for FileSystem Snapshots')
    mandatory_args = export_rule_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<snapshotname>',
                                help='Name of Snapshot',
                                required=True)
    mandatory_args.add_argument('-operation', '-op',
                                choices=["add", "update", "delete"],
                                dest='operation',
                                metavar='<exportruleoperation>',
                                help='Export rule operation',
                                required=True)
    mandatory_args.add_argument('-filesystem', '-fs',
                                dest='filesystem',
                                metavar='<fielsystem>',
                                help='Export rule operation',
                                required=True)
    export_rule_parser.add_argument('-roothosts', '-rhosts',
                                    dest='roothosts',
                                    nargs = '+',
                                    metavar='<roothosts>',
                                    help='Root host names')
    export_rule_parser.add_argument('-readonlyhosts', '-rohosts',
                                    dest='readonlyhosts',
                                    nargs = '+',
                                    metavar='<readonlyhosts>',
                                    help='Read only host names')
    export_rule_parser.add_argument('-readwritehosts', '-rwhosts',
                                    dest='readwritehosts',
                                    nargs = '+',
                                    metavar='<readwritehosts>',
                                    help='Read write host names')
    export_rule_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-securityflavor', '-sec',
                                choices=["sys", "krb5", "krb5i", "krb5p"],
                                metavar='<securityflavor>',
                                dest='securityflavor',
                                help='Name of Security flavor',
                                required=True)  
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    export_rule_parser.add_argument('-user', '-u',
                                    dest='user',
                                    metavar='<user>',
                                    help='User')
    export_rule_parser.add_argument('-subdirectory', '-subDir',
                                    dest='subdirectory',
                                    metavar='<subdirectory>',
                                    help='Name of the Subdirectory')
    
    export_rule_parser.set_defaults(func=snapshot_export_rule)




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
            return common.format_json_object(res)

    
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": Delete Failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "export-rule",
                "snapshot",
                e.err_text,
                e.err_code)
    



def show_snapshot_shares_parser(subcommand_parsers, common_parser):
    show_exports_parser = subcommand_parsers.add_parser(
        'show-shares',
        description='ViPR Snapshot Show shares CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show share details of snapshot')
    mandatory_args = show_exports_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    mandatory_args.add_argument('-share', '-sh',
                                 help='Name of fileshare for snapshot',
                                 metavar='<sharename>',
                                 dest='share',
                                 required=True)
    show_exports_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant',
                                required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_exports_parser.set_defaults(func=show_snapshot_shares)


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
            return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + args.name +
                           ": show-shares Failed\n" + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "show-shares", "snapshot", e.err_text, e.err_code)


def activate_parser(subcommand_parsers, common_parser):
    activate_parser = subcommand_parsers.add_parser('activate',
                                                    description='ViPR' +
                                                    ' Snapshot activate' +
                                                    ' CLI usage.',
                                                    parents=[common_parser],
                                                    conflict_handler='resolve',
                                                    help='active a snapshot')

    mandatory_args = activate_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    activate_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant',
                                 required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    group = activate_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of consistencygroup')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')

    '''
    group = activate_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-filesystem', '-fs',
                                metavar='<filesystemname>',
                                dest = 'filesystem',
                                help = 'Name of filesystem')
    group.add_argument('-volume', '-vol',
                                metavar = '<volumename>',
                                dest = 'volume',
                                help = 'Name of a volume')
    '''

    activate_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Synchronous snapshot activate',
                                 action='store_true')

    mandatory_args.set_defaults(func=snapshot_activate)


def snapshot_activate(args):
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
            args.sync)

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


def restore_parser(subcommand_parsers, common_parser):
    restore_parser = subcommand_parsers.add_parser('restore',
                                                   description='ViPR' +
                                                   ' Snapshot restore' +
                                                   ' CLI usage.',
                                                   parents=[common_parser],
                                                   conflict_handler='resolve',
                                                   help='restore a snapshot')

    mandatory_args = restore_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    restore_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant',
                                required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    group = restore_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-filesystem', '-fs',
                       metavar='<filesystemname>',
                       dest='filesystem',
                       help='Name of filesystem')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of a consistencygroup')

    restore_parser.add_argument('-synchronous', '-sync',
                                dest='sync',
                                help='Synchronous snapshot restore',
                                action='store_true')

    mandatory_args.set_defaults(func=snapshot_restore)


def snapshot_restore(args):
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
            args.sync)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": Restore Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "restore",
                "snapshot",
                e.err_text,
                e.err_code)
            

#SNAPSHOT RESYNC PARSER

def resync_parser(subcommand_parsers, common_parser):
    resync_parser = subcommand_parsers.add_parser('resync',
                                                   description='ViPR' +
                                                   ' Snapshot resync' +
                                                   ' CLI usage.',
                                                   parents=[common_parser],
                                                   conflict_handler='resolve',
                                                   help='resynchronizes a snapshot')

    mandatory_args = resync_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    resync_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant',
                                required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    group = resync_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of a consistencygroup')

    resync_parser.add_argument('-synchronous', '-sync',
                                dest='sync',
                                help='Synchronous snapshot restore',
                                action='store_true')

    mandatory_args.set_defaults(func=snapshot_resync)


def snapshot_resync(args):
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
        obj.snapshot_resync(
            storageresType,
            storageresTypename,
            resourceUri,
            args.name,
            args.sync)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Snapshot " +
                args.name +
                ": Resyncronization Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "resync",
                "snapshot",
                e.err_text,
                e.err_code) 
            


def import_to_vplex_parser(subcommand_parsers, common_parser):
    import_to_vplex_parser = subcommand_parsers.add_parser('import-to-vplex',
                                                   description='ViPR' +
                                                   ' Imports snapshot as Volume' +
                                                   ' CLI usage.',
                                                   parents=[common_parser],
                                                   conflict_handler='resolve',
                                                   help='imports a snapshot as a vplex volume')

    mandatory_args = import_to_vplex_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)
    import_to_vplex_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant',
                                required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    group = import_to_vplex_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    
    

    mandatory_args.set_defaults(func=import_snapshot_vplex)


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
            common.format_err_msg_and_raise(
                "vplex import ",
                "snapshot",
                e.err_text,
                e.err_code)           
          



# Snapshot tasks routines


def tasks_parser(subcommand_parsers, common_parser):
    tasks_parser = subcommand_parsers.add_parser('tasks',
                                                 description='ViPR Snapshot' +
                                                 ' tasks CLI usage.',
                                                 parents=[common_parser],
                                                 conflict_handler='resolve',
                                                 help='tasks of a snapshot')

    mandatory_args = tasks_parser.add_argument_group('mandatory arguments')
    tasks_parser.add_argument('-tenant', '-tn',
                              metavar='<tenantname>',
                              dest='tenant',
                              help='Name of tenant',
                              required=False)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    arggroup = tasks_parser.add_mutually_exclusive_group(required=True)
    arggroup.add_argument('-filesystem', '-fs',
                          metavar='<filesystemname>',
                          dest='filesystem',
                          help='Name of filesystem')
    arggroup.add_argument('-volume', '-vol',
                          metavar='<volumename>',
                          dest='volume',
                          help='Name of a volume')
    arggroup.add_argument('-consistencygroup', '-cg',
                          metavar='<consistencygroup>',
                          dest='consistencygroup',
                          help='Name of a consistencygroup')

    tasks_parser.add_argument('-name', '-n',
                              metavar='<snapshotname>',
                              dest='name',
                              help='Name of snapshot',
                              required=False)

    tasks_parser.add_argument('-id',
                              dest='id',
                              metavar='<id>',
                              help='Task ID')
    tasks_parser.add_argument('-v', '-verbose',
                              dest='verbose',
                              action="store_true",
                              help='List all tasks')

    tasks_parser.set_defaults(func=snapshot_tasks)


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
                        common.format_json_object(
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
            return common.format_json_object(all_tasks)
        # display in table format (all task for given file share/volume or
        # snapshot name)
        else:
            if(all_tasks and len(all_tasks) > 0):
                from common import TableGenerator
                TableGenerator(
                    all_tasks,
                    ["module/id",
                     "name",
                     "state"]).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise(
            "tasks",
            "snapshot",
            e.err_text,
            e.err_code)


def tag_parser(subcommand_parsers, common_parser):
    tag_parser = subcommand_parsers.add_parser('tag',
                                               description='ViPR' +
                                               ' Snapshot Tag CLI usage.',
                                               parents=[common_parser],
                                               conflict_handler='resolve',
                                               help='tag a snapshot')

    mandatory_args = tag_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<snapshotname>',
                                dest='name',
                                help='Name of Snapshot',
                                required=True)

    tag_parser.add_argument('-tenant', '-tn',
                            metavar='<tenantname>',
                            dest='tenant',
                            help='Name of tenant',
                            required=False)
    tag.add_mandatory_project_parameter(mandatory_args)

    group = tag_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-filesystem', '-fs',
                       metavar='<filesystemsname>',
                       dest='filesystem',
                       help='Name of filesystem')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume')
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of a consistencygroup')

    tag_parser.add_argument('-synchronous', '-sync',
                            dest='sync',
                            help='Synchronous snapshot export',
                            action='store_true')

    tag.add_tag_parameters(tag_parser)

    tag_parser.set_defaults(func=snapshot_tag)


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
            common.format_err_msg_and_raise(
                "tag",
                "snapshot",
                e.err_text,
                e.err_code)




#
# Snapshot Main parser routine
#
def snapshot_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('snapshot',
                                         description='ViPR Snapshot CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Snapshot')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # export-file command parser
    export_file_parser(subcommand_parsers, common_parser)

    # export-volume command parser
    #export_volume_parser(subcommand_parsers, common_parser)

    # unexport-file command parser
    unexport_file_parser(subcommand_parsers, common_parser)

    # unexport-volume command parser
    #unexport_volume_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # activate command parser
    activate_parser(subcommand_parsers, common_parser)

    # restore command parser
    restore_parser(subcommand_parsers, common_parser)
    
    #resyn command parser
    resync_parser(subcommand_parsers, common_parser)


    # tasks command parser
    tasks_parser(subcommand_parsers, common_parser)

    tag_parser(subcommand_parsers, common_parser)

    #show exports command parser
    show_snapshot_exports_parser(subcommand_parsers, common_parser)

    #show share command parser
    show_snapshot_shares_parser(subcommand_parsers, common_parser)
    
    #Export-rule command parser
    export_rule_parser(subcommand_parsers, common_parser)  
    
    #vplex import parser
    import_to_vplex_parser(subcommand_parsers, common_parser)
