#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


import common
import tag
import json

from common import SOSError
from threading import Timer
from virtualarray import VirtualArray
from storagesystem import StorageSystem
import consistencygroup




class Volume(object):

    '''
    The class definition for operations on 'Volume'.
    '''
    # Commonly used URIs for the 'Volume' module
    URI_SEARCH_VOLUMES = '/block/volumes/search?project={0}'
    URI_SEARCH_VOLUMES_BY_PROJECT_AND_NAME = \
        '/block/volumes/search?project={0}&name={1}'
    URI_SEARCH_VOLUMES_BY_TAG = '/block/volumes/search?tag={0}'
    URI_VOLUMES = '/block/volumes'
    URI_VOLUME = URI_VOLUMES + '/{0}'
    URI_VOLUME_CREATE = URI_VOLUMES + '?project={0}'
    URI_VOLUME_SNAPSHOTS = URI_VOLUME + '/snapshots'
    URI_VOLUME_RESTORE = URI_VOLUME + '/restore'
    URI_VOLUME_EXPORTS = URI_VOLUME + '/exports'
    URI_VOLUME_UNEXPORTS = URI_VOLUME_EXPORTS + '/{1},{2},{3}'
    URI_VOLUME_CONSISTENCYGROUP = URI_VOLUME + '/consistency-group'
    URI_PROJECT_RESOURCES = '/projects/{0}/resources'
    URI_VOLUME_TAGS = URI_VOLUME + '/tags'
    URI_BULK_DELETE = URI_VOLUMES + '/deactivate'
    URI_DEACTIVATE = URI_VOLUME + '/deactivate'
    URI_EXPAND = URI_VOLUME + '/expand'
    URI_TASK_LIST = URI_VOLUME + '/tasks'
    URI_TASK = URI_TASK_LIST + '/{1}'
    URI_TAG_VOLUME = URI_VOLUME + "/tags"
    URI_VOLUME_CHANGE_VPOOL = URI_VOLUMES + "/vpool-change"

    # Protection REST APIs
    URI_VOLUME_PROTECTION_CREATE = \
        '/block/volumes/{0}/protection/continuous'
    URI_VOLUME_PROTECTION_START = \
        '/block/volumes/{0}/protection/continuous/start'
    URI_VOLUME_PROTECTION_STOP = \
        '/block/volumes/{0}/protection/continuous/stop'
    URI_VOLUME_PROTECTION_PAUSE = \
        '/block/volumes/{0}/protection/continuous/pause'
    URI_VOLUME_PROTECTION_RESUME = \
        '/block/volumes/{0}/protection/continuous/resume'
    URI_VOLUME_PROTECTION_FAILOVER = \
        '/block/volumes/{0}/protection/continuous/failover'
    URI_VOLUME_PROTECTION_DELETE = \
        '/block/volumes/{0}/protection/continuous/deactivate'

    '''continuous copy or Mirror protection REST APIs '''
    URI_VOLUME_PROTECTION_MIRROR_LIST = \
        "/block/volumes/{0}/protection/continuous-copies"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_START = \
        "/block/volumes/{0}/protection/continuous-copies/start"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_STOP = \
        "/block/volumes/{0}/protection/continuous-copies/stop"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_PAUSE = \
        "/block/volumes/{0}/protection/continuous-copies/pause"
    URI_VOLUME_PROTECTION_MIRROR_RESUME = \
        "/block/volumes/{0}/protection/continuous-copies/resume"
    URI_VOLUME_PROTECTION_MIRROR_FAILOVER = \
        "/block/volumes/{0}/protection/continuous-copies/failover"
    URI_VOLUME_PROTECTION_MIRROR_FAILOVER_TEST = \
        "/block/volumes/{0}/protection/continuous-copies/failover-test"
    URI_VOLUME_PROTECTION_MIRROR_FAILOVER_CANCEL = \
        "/block/volumes/{0}/protection/continuous-copies/failover-test-cancel"
    URI_VOLUME_PROTECTION_MIRROR_FAILOVER_SWAP = \
        "/block/volumes/{0}/protection/continuous-copies/swap"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_DEACTIVATE = \
        "/block/volumes/{0}/protection/continuous-copies/deactivate"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_SYNC = \
        "/block/volumes/{0}/protection/continuous-copies/sync"
    URI_VOLUME_PROTECTION_MIRROR_INSTANCE_STOP_PID = \
        "/block/volumes/{0}/protection/native/continuous-copies/{1}/stop/"

    URI_VOLUME_PROTECTION_MIRROR_MID_SHOW = \
        "/block/volumes/{0}/protection/continuous-copies/{1}"
    URI_VOLUME_PROTECTION_MIRROR_FULLCOPY = \
        "/block/volumes/{0}/protection/full-copies"

    # Protection set REST APIs
    URI_VOLUME_PROTECTIONSET_INSTANCE = \
        "/block/volumes/{0}/protection/protection-sets/{1}"
    URI_VOLUME_PROTECTIONSET_RESOURCES = '/block/protection-sets/{0}/resources'
    URI_VOLUME_PROTECTIONSET_DISCOVER = '/block/protection-sets/{0}/discover'

    URI_UNMANAGED_VOLUMES_SHOW = '/vdc/unmanaged/volumes/{0}'
    URI_UNMANAGED_VOLUMES_INGEST = '/vdc/unmanaged/volumes/ingest'
    URI_UNMANAGED_EXPORTED_VOLUMES_INGEST = '/vdc/unmanaged/volumes/ingest-exported'

    # Protection REST APIs - clone
    URI_VOLUME_PROTECTION_FULLCOPIES = \
        '/block/volumes/{0}/protection/full-copies'
    URI_SNAPSHOT_PROTECTION_FULLCOPIES = \
        '/block/snapshots/{0}/protection/full-copies'
    
    #New URIs Supported 
   
    URI_VOLUME_CLONE_RESTORE = "/block/full-copies/{0}/restore"
    URI_VOLUME_CLONE_RESYNCRONIZE = "/block/full-copies/{0}/resynchronize"
    URI_VOLUME_CLONE_ACTIVATE = "/block/full-copies/{0}/activate"
    URI_VOLUME_CLONE_DETACH = "/block/full-copies/{0}/detach"
    URI_VOLUME_CLONE_CHECKPROGRESS = "/block/full-copies/{0}/check-progress"
    URI_VOLUME_CLONE_LIST = "/block/full-copies"
    
    
    #New CG URIs 
    URI_CG_CLONE = "/block/consistency-groups/{0}/protection/full-copies"
    URI_CG_CLONE_RESTORE = "/block/consistency-groups/{0}/protection/full-copies/{1}/restore"
    URI_CG_CLONE_RESYNCRONIZE = "/block/consistency-groups/{0}/protection/full-copies/{1}/resynchronize"
    URI_CG_CLONE_ACTIVATE = "/block/consistency-groups/{0}/protection/full-copies/{1}/activate"
    URI_CG_CLONE_DETACH = "/block/consistency-groups/{0}/protection/full-copies/{1}/detach"
    URI_CG_CLONE_DEACTIVATE = "/block/consistency-groups/{0}/protection/full-copies/{1}/deactivate"
    URI_CG_CLONE_LIST = "/block/consistency-groups/{0}/protection/full-copies"
    URI_CG_CLONE_GET= "/block/consistency-groups/{0}/protection/full-copies/{1}"
	
    #New Migration URIs
    URI_MIGRATION_LIST = "/block/migrations"
    URI_MIGRATION_SHOW = "/block/migrations/{0}"
    URI_MIGRATION_CANCEL = "/block/migrations/{0}/cancel"
    URI_MIGRATION_PAUSE = "/block/migrations/{0}/pause"
    URI_MIGRATION_RESUME = "/block/migrations/{0}/resume"
    URI_MIGRATION_DEACTIVATE = "/block/migrations/{0}/deactivate"
    
    #New API for adding volumes to RP Journal CG 
    URI_RP_JOURNAL_CAPACITY = "/block/volumes/protection/addJournalCapacity"
    
    VOLUME_PROTECTIONS = ['rp', 'native', 'srdf']
    VOLUME_PROTECTION_HELP = \
        'type of protection(rp or native or srdf) - default:native'

    VOLUME_PROTECTIONS_EX_SRDF = ['rp', 'native']
    VOLUME_PROTECTION_HELP_EX_SRDF = \
        'type of protection(rp or native) - default:native'

    VOLUMES = 'volumes'
    CG = 'consistency-groups'
    BLOCK = 'block'
    SNAPSHOTS = 'snapshots'
    
    isTimeout = False
    timeout = 300

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    # Lists volumes in a project
    def list_volumes(self, project):
        '''
        Makes REST API call to list volumes under a project
        Parameters:
            project: name of project
        Returns:
            List of volumes uuids in JSON response payload
        '''

        from project import Project

        proj = Project(self.__ipAddr, self.__port)
        project_uri = proj.project_query(project)

        volume_uris = self.search_volumes(project_uri)
        volumes = []
        for uri in volume_uris:
            volume = self.show_by_uri(uri)
            if(volume):
                volumes.append(volume)
        return volumes

    '''
    Given the project name and volume name, the search will be performed
    to find if the volume with the given name exists or not. If found,
    the uri of the volume will be returned
    '''

    def search_by_project_and_name(self, projectName, volumeName):

        return (
            common.search_by_project_and_name(
                projectName,
                volumeName,
                Volume.URI_SEARCH_VOLUMES_BY_PROJECT_AND_NAME,
                self.__ipAddr,
                self.__port)
        )

    def search_volumes(self, project_uri):

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             Volume.URI_SEARCH_VOLUMES.format(
                                                 project_uri),
                                             None)
        o = common.json_decode(s)
        if not o:
            return []

        volume_uris = []
        resources = common.get_node_value(o, "resource")
        for resource in resources:
            volume_uris.append(resource["id"])
        return volume_uris

    # Get the list of volumes given a project uri
    def list_by_uri(self, project_uri):
        '''
        Makes REST API call and retrieves volumes based on project UUID
        Parameters:
            project_uri: UUID of project
        Returns:
            List of volumes UUIDs in JSON response payload
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Volume.URI_PROJECT_RESOURCES.format(project_uri),
            None)
        o = common.json_decode(s)
        if not o:
            return []

        volume_uris = []
        resources = common.get_node_value(o, "project_resource")
        for resource in resources:
            if(resource["resource_type"] == "volume"):
                volume_uris.append(resource["id"])
        return volume_uris

    def protection_operations(self, volume, operation, local, remote):
        '''
        This function is to do different action on continuous protection
        for given volume
        Parameters:
            volume: Name of the volume
            operation: one of value in [ create, start, stop, pause, resume,
                                         failover or delete]
            local: true if we want local continuous protection
            remote: true, if we want rmote continuous protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)

        if("create" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_CREATE.format(vol_uri)
        elif("start" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_START.format(vol_uri)
        elif("stop" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_STOP.format(vol_uri)
        elif("pause" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_PAUSE.format(vol_uri)
        elif("resume" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_RESUME.format(vol_uri)
        elif("failover" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_FAILOVER.format(vol_uri)
        elif("delete" == operation):
            uri = Volume.URI_VOLUME_PROTECTION_DELETE.format(vol_uri)
        else:
            raise SOSError(
                SOSError.VALUE_ERR,
                "Invalid operation:" +
                operation)

        if(local):
            if ('?' in uri):
                uri += '&local=' + 'true'
            else:
                uri += '?local=' + 'true'

        if(remote):
            if ('?' in uri):
                uri += '&remote=' + 'true'
            else:
                uri += '?remote=' + 'true'

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             uri,
                                             None)

        o = common.json_decode(s)
        return o

    def mirror_protection_copyparam(
            self, volume, mirrorvol, copytype="native", sync='false'):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['type'] = copytype
        #true=split
        if(sync == 'true'):
            copy['sync'] = "true"
        else:
            copy['sync'] = "false"
        #for rp and srdf target volume should be provided
        if(copytype != 'native'):
            (pname, label) = common.get_parent_child_from_xpath(volume)
            copy['copyID'] = self.volume_query(pname + '/' + mirrorvol)
        else:
            copy['copyID'] = self.mirror_volume_query(volume, mirrorvol)
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries
        return json.dumps(copies_param)

    def mirror_protection_create(self, volume, mirrorvolname, count, copytype):
        '''
        Creating or attaching a name blockmirror volume to a volume name
        Parameters:
            volume: Name of the volume
            mirrorvolname: name of mirror volume or label
            count: Name of the mirror volume
        Returns:
            result of the action.
        '''

        vol_uri = self.volume_query(volume)

        copies_param = dict()
        copy = dict()
        copy_entries = []
        #for rp and srdf target volume should be provided
        if(copytype != 'native' and mirrorvolname):
            (pname, label) = common.get_parent_child_from_xpath(volume)
            copy['copyID'] = self.volume_query(pname + '/' + mirrorvolname)
        copy['name'] = mirrorvolname
        if(count and count > 0):
            copy['count'] = count
        copy['type'] = copytype
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries

        body = json.dumps(copies_param)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_START.format(
                vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_list(self, volume):
        '''
        block mirror list for a given volume name
        Parameters:
            volume: Name of the volume
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Volume.URI_VOLUME_PROTECTION_MIRROR_LIST.format(vol_uri),
            None)
        o = common.json_decode(s)

        mirrorlist = []
        for uri in common.get_node_value(o, 'mirror'):
            mirrorlist.append(uri['id'])
        return mirrorlist

    def mirror_protection_show(self, volume, mirrorvol):
        '''
        show name of blockmirror details'
        Parameters:
            volume   : Name of the volume
                        mirrorvol: Name of the mirror volume

        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        uris = self.mirror_protection_list(volume)
        for mirror_uri in uris:
            mirvol = self.get_uri_mirror_protection_vol(vol_uri, mirror_uri)
            if(mirvol is not None and mirvol["name"] == mirrorvol):
                return mirvol

        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            mirrorvol +
            " :Could not find the matching continuous_copy volume")
    '''
        get block mirror volume details by mirror volume uri
        Parameters:
            volume uri   : Name of the volume
            mirrorvol uri: Name of the mirror volume

        Returns:
            result of the action.
        '''

    def get_uri_mirror_protection_vol(self, vol_uri, mirror_uri):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Volume.URI_VOLUME_PROTECTION_MIRROR_MID_SHOW.format(
                vol_uri, mirror_uri),
            None)
        o = common.json_decode(s)
        if(o['inactive']):
            return None
        return o

    def mirror_protection_pause(self, volume, mirrorvol, type, sync):
        '''
        Pause continuous copies
        Parameters:
            volume    : Name of the source volume
                        mirrorvol : Name of the continous_copy or label
                        type      : type of protection
                        sync     : synchronize the mirror true=split false=fracture
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type, sync)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_PAUSE.format(vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_establish(self, volume, mirrorvol, type):
        '''
        Establish continuous copies
        Parameters:
            volume    : Name of the source volume
                        mirrorvol : Name of the continous_copy or label
                        type      : type of protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_SYNC.format(vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_restore(self, volume, mirrorvol, type):
        '''
        Restore continuous copies
        Parameters:
            volume    : Name of the source volume
                        mirrorvol : Name of the continous_copy or label
                        type      : type of protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_SYNC.format(vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_resume(self, volume, mirrorvol, type):
        '''
        Resume continuous copies
        Parameters:
            volume   : Name of the source volume
            mirrorvol: Name of the mirror volume
            type     : type of protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_RESUME.format(vol_uri),
            body)

        return common.json_decode(s)

    def mirror_protection_failover_ops(self, volume, mirrorvol,
                                type="native", op="failover"):
        '''
        Failover the volume protection
        Parameters:
            volume    : Source volume path
            mirrorvol : Name of the continous_copy
            type      : type of protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)

        uri = Volume.URI_VOLUME_PROTECTION_MIRROR_FAILOVER.format(vol_uri)
        if op == 'failover-test':
            uri = Volume.URI_VOLUME_PROTECTION_MIRROR_FAILOVER_TEST.format(
                  vol_uri)
        elif op == 'failover-test-cancel':
            uri = Volume.URI_VOLUME_PROTECTION_MIRROR_FAILOVER_CANCEL.format(
                  vol_uri)
        elif op == 'swap':
            uri = Volume.URI_VOLUME_PROTECTION_MIRROR_FAILOVER_SWAP.format(
                  vol_uri)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            uri,
            body)
        return common.json_decode(s)

    def mirror_protection_stop(self, volume, mirrorvol, type):
        '''
        Stop continuous copies.
        Parameters:
            volume    : Name of Source volume
            mirrorvol : Name of the continous_copy
            type      : type of protection
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_STOP.format(vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_delete(self, volume, mirrorvol, type="native"):
        '''
            Delete continuous copies
        Parameters:
            volume    : source volume
                        mirrorvol : Name of the continous_copy
                        type      : type of protection

        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        body = self.mirror_protection_copyparam(volume, mirrorvol, type)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_INSTANCE_DEACTIVATE.format(
                vol_uri),
            body)
        return common.json_decode(s)

    def mirror_protection_copy(self, volume, copyname, count):
        '''
        This function is to do different action on mirror protection for
        given volume.
        Parameters:
            volume: Name of the volume
            operation: one of value in [ create, pause, list, show ,
                       delete or copy]
            mirrorvol: Name of the mirror volume
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        if(copyname):
            parms = {
                'name': copyname,
            }
        if(count):
            parms['count'] = count
        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_VOLUME_PROTECTION_MIRROR_FULLCOPY.format(vol_uri),
            body)
        return common.json_decode(s)

    def mirror_volume_query(self, volume, mirrorvolname):
        if (common.is_uri(mirrorvolname)):
            return mirrorvolname

        vol_uri = self.volume_query(volume)
        uris = self.mirror_protection_list(volume)
        for uri in uris:
            mirvol = self.get_uri_mirror_protection_vol(vol_uri, uri)
            if(mirvol is not None and mirvol['name'] == mirrorvolname):
                return mirvol['id']
        raise SOSError(SOSError.NOT_FOUND_ERR, "MirrorVolumeName " +
                       mirrorvolname + ": not found")

    def protectionset_show(self, volume):
        '''
        This function is to do different action on protection set for
        given volume.
        Parameters:
            volume: Name of the volume
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        vol = self.show_by_uri(vol_uri)
        if(vol and 'protection' in vol and 'recoverpoint' in vol['protection'] and
           'protection_set' in vol['protection']['recoverpoint']):
            uri = vol['protection']['recoverpoint']['protection_set']['id']
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Volume.URI_VOLUME_PROTECTIONSET_INSTANCE.format(vol_uri,
                    uri),
                None)
            o = common.json_decode(s)
            if(o['inactive']):
                return None
            return o
        else:
            raise SOSError(
                SOSError.VALUE_ERR,
                "Volume does not have protection set Info")

    def protectionset_getresources(self, volume):
        '''
         This function is to do different action on protection set for
         given volume
        Parameters:
            volume: Name of the volume
        Returns:
            result of the action.
        '''
        vol_uri = self.volume_query(volume)
        vol = self.show_by_uri(vol_uri)
        if(vol and 'protection_set' in vol):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Volume.URI_VOLUME_PROTECTIONSET_RESOURCES.format(
                    vol_uri, vol['protection_set']['id']),
                None)
            if(not common.json_decode(s)):
                return None
            return common.json_decode(s)
        else:
            raise SOSError(
                SOSError.VALUE_ERR,
                "Volume does not have protection set Info")

    def protectionset_discover(self, volume):
        '''
         This function is to do different action on protection set for
         given volume
        Parameters:
            volume: Name of the volume
        Returns:
            result of the action.

        '''
        vol_uri = self.volume_query(volume)
        vol = self.show_by_uri(vol_uri)
        if(vol and 'protection' in vol):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_PROTECTIONSET_DISCOVER.format(
                    vol_uri, vol['protection']['id']),
                None)
            return common.json_decode(s)
        else:
            raise SOSError(
                SOSError.VALUE_ERR,
                "Volume does not have protection set Info")

    # Shows volume information given its name
    def show(self, name, show_inactive=False, xml=False):
        '''
        Retrieves volume details based on volume name
        Parameters:
            name: name of the volume. If the volume is under a project,
            then full XPath needs to be specified.
            Example: If VOL1 is a volume under project PROJ1, then the name
            of volume is PROJ1/VOL1
        Returns:
            Volume details in JSON response payload
        '''
        from project import Project

        if (common.is_uri(name)):
            return name
        (pname, label) = common.get_parent_child_from_xpath(name)
        if (pname is None):
            raise SOSError(SOSError.NOT_FOUND_ERR, "Volume " +
                           str(name) + ": not found")

        proj = Project(self.__ipAddr, self.__port)
        puri = proj.project_query(pname)
        puri = puri.strip()

        uris = self.search_volumes(puri)

        for uri in uris:
            volume = self.show_by_uri(uri, show_inactive)
            if (volume and 'name' in volume and volume['name'] == label):
                if(not xml):
                    return volume
                else:
                    return self.show_by_uri(volume['id'],
                                            show_inactive, xml)
        raise SOSError(SOSError.NOT_FOUND_ERR, "Volume " +
                       str(label) + ": not found")

    # Shows volume information given its uri
    def show_by_uri(self, uri, show_inactive=False, xml=False):
        '''
        Makes REST API call and retrieves volume details based on UUID
        Parameters:
            uri: UUID of volume
        Returns:
            Volume details in JSON response payload
        '''
        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                                 "GET",
                                                 Volume.URI_VOLUME.format(uri),
                                                 None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             Volume.URI_VOLUME.format(uri),
                                             None)
        o = common.json_decode(s)
        if(show_inactive):
            return o
        inactive = common.get_node_value(o, 'inactive')
        if(inactive is True):
            return None
        return o

    def unmanaged_volume_ingest(self, tenant, project,
                                varray, vpool, volumes, ingestmethod):
        '''
        This function is to ingest given unmanaged volumes
        into ViPR.
        '''
        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(tenant + "/" + project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "block")

        from virtualarray import VirtualArray
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)
        
        if(ingestmethod is None):
            ingestmethod = "Full"

        request = {
            'vpool': vpool_uri,
            'varray': varray_uri,
            'vplexIngestionMethod': ingestmethod,
            'project': project_uri,
            'unmanaged_volume_list': volumes
        }

        body = json.dumps(request)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_UNMANAGED_VOLUMES_INGEST,
            body)
        o = common.json_decode(s)
        return o

    def unmanaged_exported_volume_ingest(self, tenant, project,
                                varray, vpool, volumes, host, cluster, ingestmethod):
        '''
        This function is to ingest given unmanaged volumes
        into ViPR.
        '''
        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(tenant + "/" + project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "block")

        from virtualarray import VirtualArray
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)
        
        if(ingestmethod is None):
            ingestmethod = "Full"

        request = {
            'vpool': vpool_uri,
            'vplexIngestionMethod': ingestmethod, 
            'varray': varray_uri,
            'project': project_uri,
            'unmanaged_volume_list': volumes
        }
        if(host is not None):
            from host import Host
            host_obj = Host(self.__ipAddr, self.__port)
            host_uri = host_obj.query_by_name(host)
            request["host"] = host_uri

        if(cluster is not None):
            from cluster import Cluster
            cluster_obj = Cluster(self.__ipAddr, self.__port)
            cluster_uri = cluster_obj.cluster_query(cluster)
            request["cluster"] = cluster_uri

        body = json.dumps(request)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Volume.URI_UNMANAGED_EXPORTED_VOLUMES_INGEST,
            body)
        o = common.json_decode(s)
        return o

    def unmanaged_volume_show(self, volume):
        '''
        This function is to show the details of unmanaged volumes
        from  ViPR.
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Volume.URI_UNMANAGED_VOLUMES_SHOW.format(volume),
            None)
        o = common.json_decode(s)
        return o

    # Creates a volume given label, project, vpool and size
    def create(self, project, label, size, varray, vpool,
               protocol, sync, number_of_volumes, thin_provisioned,
               consistencygroup):
        '''
        Makes REST API call to create volume under a project
        Parameters:
            project: name of the project under which the volume will be created
            label: name of volume
            size: size of volume
            varray: name of varray
            vpool: name of vpool
            protocol: protocol used for the volume (FC or iSCSI)
        Returns:
            Created task details in JSON response payload
        '''

        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "block")

        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)

        request = {
            'name': label,
            'size': size,
            'varray': varray_uri,
            'project': project_uri,
            'vpool': vpool_uri
        }
        if(protocol):
            request["protocols"] = protocol
        if(number_of_volumes and number_of_volumes > 1):
            request["count"] = number_of_volumes
        if(thin_provisioned):
            request["thinly_provisioned"] = thin_provisioned
        from consistencygroup import ConsistencyGroup
        if(consistencygroup):

            cgobj = ConsistencyGroup(self.__ipAddr, self.__port)
            (tenant, project) = common.get_parent_child_from_xpath(project)
            consuri = cgobj.consistencygroup_query(
                consistencygroup,
                project,
                tenant)
            request['consistency_group'] = consuri

        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Volume.URI_VOLUMES,
                                             body)
        o = common.json_decode(s)

        if(sync):
            if(number_of_volumes < 2):
                # check task empty
                if (len(o["task"]) > 0):
                    task = o["task"][0]
                    return self.check_for_sync(task, sync)
                else:
                    raise SOSError(
                        SOSError.SOS_FAILURE_ERR,
                        "error: task list is empty, no task response found")
        else:
            return o
        
    #Routine to add additional journal capacity 
    def rp_journal_create(self,consistencygroup, number_of_volumes ,label,project,  size, varray, vpool, sync ):
        '''
        Makes REST API call to create additional journal space under a project
        Parameters:
            project: name of the project under which the volumes will be created
            label: name of volume
            size: size of volume
            varray: name of varray
            vpool: name of vpool
            protocol: protocol used for the volume (FC or iSCSI)
        Returns:
            Created task details in JSON response payload
        '''

        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "block")

        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)

        request = {
            'name': label,
            'size': size,
            'varray': varray_uri,
            'project': project_uri,
            'vpool': vpool_uri
        }
        
        if(number_of_volumes and number_of_volumes > 0):
            request["count"] = number_of_volumes
        
        from consistencygroup import ConsistencyGroup
        if(consistencygroup):

            cgobj = ConsistencyGroup(self.__ipAddr, self.__port)
            (tenant, project) = common.get_parent_child_from_xpath(project)
            consuri = cgobj.consistencygroup_query(
                consistencygroup,
                project,
                tenant)
            request['consistency_group'] = consuri

        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Volume.URI_RP_JOURNAL_CAPACITY,
                                             body)
        o = common.json_decode(s)

        if(sync):
            if(number_of_volumes < 2):
                # check task empty
                if (len(o["task"]) > 0):
                    task = o["task"][0]
                    return self.check_for_sync(task, sync)
                else:
                    raise SOSError(
                        SOSError.SOS_FAILURE_ERR,
                        "error: task list is empty, no task response found")
        else:
            return o
        


    # Update a volume information
    # Changed the volume vpool
    def update(self, prefix_path, name, vpool):
        '''
        Makes REST API call to update a volume information
        Parameters:
            name: name of the volume to be updated
            vpool: name of vpool
        Returns
            Created task details in JSON response payload
        '''
        namelist = []

        if type(name) is list:
            namelist = name
        else:
            namelist.append(name)

        volumeurilist = []

        for item in namelist:
            volume_uri = self.volume_query(prefix_path + "/" + item)
            volumeurilist.append(volume_uri)

        from virtualpool import VirtualPool

        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "block")

        params = {
            'vpool': vpool_uri,
            'volumes': volumeurilist
        }

        body = json.dumps(params)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Volume.URI_VOLUME_CHANGE_VPOOL,
            body)

        o = common.json_decode(s)
        return o

    # Gets the exports info given a volume uri
    def get_exports_by_uri(self, uri):
        '''
        Makes REST API call to get exports info of a volume
        Parameters:
            uri: URI of the volume
        Returns:
            Exports details in JSON response payload
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                             Volume.URI_VOLUME_EXPORTS.format(
                                                 uri),
                                             None)
        return common.json_decode(s)

    # Gets all tags of volume
    def getTags(self, name):
        '''
        Makes REST API call to update a volumes tags
        Parameters:
            name:       name of the volume
        Returns
            JSON response of current tags
        '''

        volume_uri = self.volume_query(name)
        requri = Volume.URI_VOLUME_TAGS.format(volume_uri)
        return tag.list_tags(self.__ipAddr, self.__port, requri)

    # Tag a volume information
    def tag(self, name, addtags, removetags):
        '''
        Makes REST API call to update volume tag information
        Parameters:
            name: name of the volume to be updated
            addtags : tags to be added to volume
            removetags: tags tp ne removed from volume
        '''
        volume_uri = self.volume_query(name)

        return (
            tag.tag_resource(
                self.__ipAddr,
                self.__port,
                Volume.URI_TAG_VOLUME,
                volume_uri,
                addtags,
                removetags)
        )

    # Exports a volume to a host given a volume name, initiator and hlu
    def export(self, name, protocol, initiator_port,
               initiator_node, hlu, host_id, sync):
        '''
        Makes REST API call to export volume to a host
        Parameters:
            name: Name of the volume
            protocol: Protocol used for export
            initiator_port: Port of host (WWPN for FC and IQN for ISCSI)
            initiator_node: Node of the host(WWNN for FC and IQN for ISCSI)
            hlu: host logical unit number -- should be unused on the host
            host_id: Physical address of the host
        Returns:
            Created Operation ID details in JSON response payload
        '''
        volume_uri = self.volume_query(name)
        body = json.dumps(
            {
                'protocol': protocol,
                'initiator_port': initiator_port,
                'initiator_node': initiator_node,
                'lun': hlu,
                'host_id': host_id
            }
        )

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Volume.URI_VOLUME_EXPORTS.format(
                                                 volume_uri),
                                             body)
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
        else:
            return o

    # Unexports a volume from a host given a volume name and the host name
    def unexport(self, name, initiator, protocol, hlu, sync):
        '''
        Makes REST API call to unexport volume from host
        Parameters:
            name: Name of the volume
            initiator: Port of host (combination of WWNN and WWPN for FC and
                       IQN for iSCSI)
            protocol: Protocol used for export
            hlu: host logical unit number -- should be unused on the host
        Returns:
            Created Operation ID details in JSON response payload
        '''
        volume_uri = self.volume_query(name)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Volume.URI_VOLUME_UNEXPORTS.format(
                volume_uri, protocol, initiator, hlu),
            None)
        o = common.json_decode(s)

        if(sync):
            return self.check_for_sync(o, sync)
        else:
            return o

    # Deletes a volume given a volume name
    def delete(self, name, volume_name_list=None, sync=False,
               forceDelete=False, vipronly=False):
        '''
        Deletes a volume based on volume name
        Parameters:
            name: name of volume if volume_name_list is None
                     otherwise it will be name of project
        '''
        if(volume_name_list is None):
            volume_uri = self.volume_query(name)
            return self.delete_by_uri(volume_uri, sync, forceDelete, vipronly)
        else:
            vol_uris = []
            invalid_vol_names = ""
            for vol_name in volume_name_list:
                try:
                    volume_uri = self.volume_query(name + '/' + vol_name)
                    vol_uris.append(volume_uri)
                except SOSError as e:
                    if(e.err_code == SOSError.NOT_FOUND_ERR):
                        invalid_vol_names += vol_name + " "
                        continue
                    else:
                        raise e

            if(len(vol_uris) > 0):
                self.delete_bulk_uris(vol_uris, forceDelete, vipronly)

            if(len(invalid_vol_names) > 0):
                raise SOSError(SOSError.NOT_FOUND_ERR, "Volumes: " +
                               str(invalid_vol_names) + " not found")

    # Deletes a volume given a volume uri
    def delete_by_uri(self, uri, sync=False,
                      forceDelete=False, vipronly=False):
        '''
        Deletes a volume based on volume uri
        Parameters:
            uri: uri of volume
        '''
        params = ''
        if (forceDelete):
            params += '&' if ('?' in params) else '?'
            params += "force=" + "true"
        if (vipronly == True):
            params += '&' if ('?' in params) else '?'
            params += "type=" + 'VIPR_ONLY'

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                "POST",
                                Volume.URI_DEACTIVATE.format(uri) + params,
                                None)
        if(not s):
            return None
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
        return o

    def delete_bulk_uris(self, uris, forceDelete, vipronly):
        '''
        Deletes a volume based on volume uri
        Parameters:
            uri: uri of volume
        '''
        params = ''
        if (forceDelete):
            params += '&' if ('?' in params) else '?'
            params += "force=" + "true"
        if (vipronly == True):
            params += '&' if ('?' in params) else '?'
            params += "type=" + 'VIPR_ONLY'

        body = json.dumps({'id': uris})

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Volume.URI_BULK_DELETE + params,
                                             body)
        o = common.json_decode(s)
        return o

    # Queries a volume given its name
    def volume_query(self, name):
        '''
        Makes REST API call to query the volume by name
        Parameters:
            name: name of volume
        Returns:
            Volume details in JSON response payload
        '''
        from project import Project

        if (common.is_uri(name)):
            return name

        (pname, label) = common.get_parent_child_from_xpath(name)
        if(not pname):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Project name  not specified")
        proj = Project(self.__ipAddr, self.__port)
        puri = proj.project_query(pname)
        puri = puri.strip()
        uris = self.search_volumes(puri)
        for uri in uris:
            volume = self.show_by_uri(uri)
            if (volume and 'name' in volume and volume['name'] == label):
                return volume['id']
        raise SOSError(SOSError.NOT_FOUND_ERR, "Volume " +
                       label + ": not found")

    # Timeout handler for synchronous operations
    def timeout_handler(self):
        self.isTimeout = True

    # Blocks the opertaion until the task is complete/error out/timeout
    def check_for_sync(self, result, sync):
        if(sync):
            if(len(result["resource"]) > 0):
                resource = result["resource"]
                return (
                    common.block_until_complete("volume", resource["id"],
                                                result["id"], self.__ipAddr,
                                                self.__port)
                )
            else:
                raise SOSError(
                    SOSError.SOS_FAILURE_ERR,
                    "error: task list is empty, no task response found")
        else:
            return result

    def list_tasks(self, project_name, volume_name=None, task_id=None):
        return (
            common.list_tasks(self.__ipAddr, self.__port, "volume",
                              project_name, volume_name, task_id)
        )

    def expand(self, name, new_size, sync=False):

        #volume_uri = self.volume_query(name)
        volume_detail = self.show(name)
        from decimal import Decimal
        new_size_in_gb = Decimal(Decimal(new_size) / (1024 * 1024 * 1024))
        current_size = Decimal(volume_detail["provisioned_capacity_gb"])
        if(new_size_in_gb <= current_size):
            raise SOSError(
                SOSError.VALUE_ERR,
                "error: Incorrect value of new size: " + str(new_size_in_gb) +
                " GB\nNew size must be greater than current size: " +
                str(current_size) + " GB")

        body = json.dumps({
            "new_size": new_size
        })

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             Volume.URI_EXPAND.format(
                                                 volume_detail["id"]),
                                             body)
        if(not s):
            return None
        o = common.json_decode(s)

        if(sync):
            return self.check_for_sync(o, sync)
        return o


    def storageResource_query(self,
                              storageresType,
                              volumeName,
                              cgName,
                              snapshotName,
                              project,
                              tenant):
        resourcepath = "/" + project + "/"
        if(tenant is not None):
            resourcepath = tenant + resourcepath

        resUri = None
        resourceObj = None
        
        if(Volume.BLOCK == storageresType and volumeName is not None):
            resUri = self.volume_query(resourcepath + volumeName)
            if(snapshotName is not None):
                
                from snapshot import Snapshot
                snapobj = Snapshot(self.__ipAddr, self.__port)
                resUri = snapobj.snapshot_query(storageresType,
                       Volume.VOLUMES, resUri, snapshotName)
                
        elif(Volume.BLOCK == storageresType and cgName is not None):
            resourceObj = consistencygroup.ConsistencyGroup(
                self.__ipAddr, self.__port)
            resUri = resourceObj.consistencygroup_query(
                cgName,
                project,
                tenant)
        else:
            resourceObj = None

        return resUri

    def get_storageAttributes(self, volumeName, cgName, snapshotName=None):
        storageresType = None
        storageresTypeName = None
        
        if(snapshotName is not None):
            storageresType = Volume.BLOCK
            storageresTypeName = Volume.SNAPSHOTS
        elif(volumeName is not None):
            storageresType = Volume.BLOCK
            storageresTypeName = Volume.VOLUMES
        elif(cgName is not None):
            storageresType = Volume.BLOCK
            storageresTypeName = Volume.CG
        else:
            storageresType = None
            storageresTypeName = None
        return (storageresType, storageresTypeName)

    def volume_clone_restore(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_CG_CLONE_RESTORE.format(
                    resourceUri,
                    volumeUri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_RESTORE.format(volumeUri), None)
            
        o = common.json_decode(s)
        if(sync):
            task = o["task"][0]
            return self.check_for_sync(task,sync)
        else:
            return o
        
    def volume_clone_resync(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_CG_CLONE_RESYNCRONIZE.format(
                    resourceUri,
                    volumeUri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_RESYNCRONIZE.format(volumeUri), None)
            
        o = common.json_decode(s)
        if(sync):
            task = o["task"][0]
            return self.check_for_sync(task,sync)
        else:
            return o   
 
    def volume_clone_activate(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_CG_CLONE_ACTIVATE.format(
                    resourceUri,
                    volumeUri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_ACTIVATE.format(volumeUri), None)
            
        o = common.json_decode(s)
        if(sync):
            task = o["task"][0]
            return self.check_for_sync(task,sync)
        else:
            return o 

    def volume_clone_detach(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_CG_CLONE_DETACH.format(
                    resourceUri,
                    volumeUri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_DETACH.format(volumeUri), None)
            
        o = common.json_decode(s)
        if(sync):
            task = o["task"][0]
            return self.check_for_sync(task,sync)
        else:
            return o    

        
    #To check whether a cloned volume is in detachable state or not
    def is_volume_detachable(self, name):
        
        volumeUri = self.volume_query(name)
        vol = self.show_by_uri(volumeUri)
        #Filtering based on "replicaState" attribute value of Cloned volume.
        #If "replicaState" value is "SYNCHRONIZED" then only Cloned volume would be in detachable state.
        if(vol and 'protection' in vol and
            'full_copies' in vol['protection'] and
            'replicaState' in vol['protection']['full_copies']):
            if(vol['protection']['full_copies']['replicaState'] == 'SYNCHRONIZED'):
                return True
            else:
                return False
        else:
            return False
        
        
    def volume_clone_deactivate(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_CG_CLONE_DEACTIVATE.format(
                    resourceUri,
                    volumeUri), None)
        else:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_DETACH.format(volumeUri), None)
            
        o = common.json_decode(s)
        if(sync):
            task = o["task"][0]
            return self.check_for_sync(task ,sync)
        else:
            return o  
        
    def volume_clone_checkprogress(self, resourceUri, name, sync):
        
        volumeUri = self.volume_query(name)
        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                Volume.URI_VOLUME_CLONE_CHECKPROGRESS.format(volumeUri), None)
            
        #o = common.json_decode(s)
        m = json.loads(s)
	return m['protection']['full_copies']
        
        
    
        
        #if(sync):
            #return self.block_until_complete(Volume.BLOCK , volumeUri, o["id"])
           
        
    def volume_clone_list(self, resourceUri):
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Volume.URI_CG_CLONE_LIST.format(
                    resourceUri), None)
        else:
            
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Volume.URI_VOLUME_CLONE_LIST, None)
            
        o = common.json_decode(s)
        return o      
    
    def volume_clone_get(self, name, resourceUri):
        
        volumeUri = self.volume_query(name)
        print "volume URI is " + volumeUri
        print "resource URI is " + resourceUri
        
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET",
                Volume.URI_CG_CLONE_GET.format(
                    resourceUri,volumeUri),None)
       
            
        o = common.json_decode(s)
        return o                            
        
     # Creates volume(s) from given source volume
    def clone(self, new_vol_name, number_of_volumes, resourceUri, sync):
        '''
        Makes REST API call to clone volume
        Parameters:
            project: name of the project under which the volume will be created
            new_vol_name: name of volume
            number_of_volumes: count of volumes
            src_vol_name: name of the source volume
            src_snap_name : name of the source snapshot
            sync: synchronous request
        Returns:
            Created task details in JSON response payload
        '''

        from snapshot import Snapshot
        snap_obj = Snapshot(self.__ipAddr, self.__port)
        is_snapshot_clone = False
        clone_full_uri = None
         
        # consistency group
        if(resourceUri.find("BlockConsistencyGroup") > 0):
            clone_full_uri = Volume.URI_CG_CLONE.format(resourceUri)
        elif(resourceUri.find("BlockSnapshot") > 0):
            is_snapshot_clone = True
            clone_full_uri = \
                    Volume.URI_SNAPSHOT_PROTECTION_FULLCOPIES.format(resourceUri)            
        else:
            clone_full_uri = \
                    Volume.URI_VOLUME_PROTECTION_FULLCOPIES.format(resourceUri)
                      
        request = {
            'name': new_vol_name,
            'type': None,
            'count': 1
        }

        if(number_of_volumes and number_of_volumes > 1):
            request["count"] = number_of_volumes

        body = json.dumps(request)
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                             clone_full_uri,
                                             body)
        o = common.json_decode(s)
        if(sync):
            if(number_of_volumes < 2):
                task = o["task"][0]

                if(is_snapshot_clone):
                    return (
                        snap_obj.block_until_complete(
                            "block",
                            task["resource"]["id"],
                            task["id"])
                    )
                else:
                    return self.check_for_sync(task, sync)
        else:
            return o   

    
    def migration_list(self):
        uri_migration_list = Volume.URI_MIGRATION_LIST
    	(s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET", uri_migration_list,
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o
		
	
    def migration_show(self, migration_id):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET",
                                              Volume.URI_MIGRATION_SHOW.format(
                                                  migration_id),
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o

    def migration_cancel(self, migration_id):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                              Volume.URI_MIGRATION_CANCEL.format(
                                                  migration_id),
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o

    def migration_pause(self, migration_id):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                              Volume.URI_MIGRATION_PAUSE.format(
                                                  migration_id),
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o

    def migration_resume(self, migration_id):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                              Volume.URI_MIGRATION_RESUME.format(
                                                  migration_id),
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o

    def migration_deactivate(self, migration_id):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                                              Volume.URI_MIGRATION_DEACTIVATE.format(
                                                  migration_id),
                                             None)
        if(not s):
            return None
    
        o = common.json_decode(s)
    
        return o		

# volume Create routines

def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Volume Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a volume')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of volume',
                                metavar='<volumename>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-size', '-s',
                                help='Size of volume: {number}[unit]. ' +
                                'A size suffix of K for kilobytes, ' +
                                ' M for megabytes, G for gigabytes, T for ' +
                                'terabytes is optional.' +
                                'Default unit is bytes.',
                                metavar='<volumesize[kKmMgGtT]>',
                                dest='size',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                help='Name of project',
                                metavar='<projectname>',
                                dest='project',
                                required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-vpool', '-vp',
                                help='Name of vpool',
                                metavar='<vpoolname>',
                                dest='vpool',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                help='Name of varray',
                                metavar='<varray>',
                                dest='varray',
                                required=True)
    create_parser.add_argument('-count', '-cu',
                               dest='count',
                               metavar='<count>',
                               type=int,
                               default=0,
                               help='Number of volumes to be created')
    create_parser.add_argument('-cg', '-consistencygroup',
                               help='The name of the consistency group',
                               dest='consistencygroup',
                               metavar='<consistentgroupname>')
    create_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    create_parser.set_defaults(func=volume_create)
    
    

#Parser function to add Journal Capacity 
def rp_journal_parser(subcommand_parsers, common_parser):
    rp_journal_parser = subcommand_parsers.add_parser(
        'add-journal-space',
        description='Creating Volumes to increase journal capacity CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='increase journal capacity')
    mandatory_args = rp_journal_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-copyname', '-n',
                                help='Name of copy volume',
                                metavar='<volumename>',
                                dest='copyname',
                                required=True)
    mandatory_args.add_argument('-size', '-s',
                                help='Size of volume: {number}[unit]. ' +
                                'A size suffix of K for kilobytes, ' +
                                ' M for megabytes, G for gigabytes, T for ' +
                                'terabytes is optional.' +
                                'Default unit is bytes.Minimum 10GB ',
                                metavar='<volumesize[kKmMgGtT]>',
                                dest='size',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                help='Name of project',
                                metavar='<projectname>',
                                dest='project',
                                required=True)
    rp_journal_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-vpool', '-vp',
                                help='Name of vpool',
                                metavar='<vpoolname>',
                                dest='vpool',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                help='Name of varray',
                                metavar='<varray>',
                                dest='varray',
                                required=True)
    mandatory_args.add_argument('-count', '-cu',
                               dest='count',
                               metavar='<count>',
                               type=int,
                               required=True,
                               help='Number of volumes to be created')
    mandatory_args.add_argument('-cg', '-consistencygroup',
                               help='The name of the consistency group',
                               dest='consistencygroup',
                               metavar='<consistencygroupname>',
                               required=True)
    rp_journal_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    rp_journal_parser.set_defaults(func=rp_journal_create)
    
# Common Parser for clone 
def volume_clone_list_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of consistencygroup',
                       required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    cc_common_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')    

# Common Parser for clone 
def volume_clone_get_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    
    mandatory_args.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of consistencygroup',
                       required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    mandatory_args.add_argument('-name', '-n',
                                metavar='<fullcopyname>',
                                dest='name',
                                help='Name of fullcopy ',
                                required=True)
    cc_common_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')

# Common Parser for clone 
def volume_clone_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    group = cc_common_parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-consistencygroup', '-cg',
                       metavar='<consistencygroup>',
                       dest='consistencygroup',
                       help='Name of consistencygroup')
    group.add_argument('-volume', '-vol',
                       metavar='<volumename>',
                       dest='volume',
                       help='Name of a volume , N/A for clone-deactivate')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    cc_common_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')  
    
    mandatory_args.add_argument('-name', '-n',
                                metavar='<fullcopyname>',
                                dest='name',
                                help='Name of fullcopy ',
                                required=True)
    cc_common_parser.add_argument('-synchronous', '-sync',
                       dest='sync',
                       action='store_true',
                       help='Synchronous mode enabled')

def get_clone_source_resource(volObj, args, snapshot=None): 
    (storageresType, storageresTypename) = volObj.get_storageAttributes(
            args.volume, args.consistencygroup, snapshot)
        
    return volObj.storageResource_query(
            storageresType,
            args.volume,
            args.consistencygroup,
            snapshot,
            args.project,
            args.tenant)
        
# volume clone routines
def clone_parser(subcommand_parsers, common_parser):
    clone_parser = subcommand_parsers.add_parser(
        'clone',
        description='ViPR Volume/Consistency group clone CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Clone a volume/consistency group')
    
     # Add parameter from common clone parser.
    volume_clone_common_parser(clone_parser)
    
    # Add other parameters
    clone_parser.add_argument('-source_snapshot', '-src_snap',
                             help='Name of source snapshot to ' +
                             'clone volume from it',
                             metavar='<source_snapshot>',
                             dest='source_snapshot')
    clone_parser.add_argument('-count', '-cu',
                              dest='count',
                              metavar='<count>',
                              type=int,
                              default=0,
                              help='Number of volumes to be cloned')
    
    clone_parser.set_defaults(func=volume_clone)


def volume_clone(args):
    
    obj = Volume(args.ip, args.port)
    if(args.count > 1 and args.sync):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            'error: Synchronous operation is not allowed for ' +
            'bulk clone of volumes')
    try:
        resourceUri = get_clone_source_resource(obj, args, args.source_snapshot)
        obj.clone(args.name, args.count, resourceUri, args.sync)
        return
    
    except SOSError as e:
        common.format_err_msg_and_raise(
            "clone",
            "volume",
            e.err_text,
            e.err_code)

    
#clone_restore_parser
def clone_restore_parser(subcommand_parsers, common_parser):
    clone_restore_parser = subcommand_parsers.add_parser(
        'clone-restore',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Restore Fullcopy of a volume/consistency group',
        description='ViPR Restore Fullcopy of a volume/consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_restore_parser)
    clone_restore_parser.set_defaults(func=volume_clone_restore)
    
# Restore Clone Function
def volume_clone_restore(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        obj.volume_clone_restore(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Restore: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "restore",
                e.err_text,
                e.err_code)
            
#clone_restore_parser
def clone_resync_parser(subcommand_parsers, common_parser):
    clone_resync_parser = subcommand_parsers.add_parser(
        'clone-resync',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Resynchronize the Fullcopy of a volume/consistency group',
        description='ViPR Resynchronize Fullcopy of a volume/consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_resync_parser)
    clone_resync_parser.set_defaults(func=volume_clone_resync)
    
# Restore Clone Function
def volume_clone_resync(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        obj.volume_clone_resync(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Resync: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "resync",
                e.err_text,
                e.err_code) 
              
#clone_activate_parser
def clone_activate_parser(subcommand_parsers, common_parser):
    clone_activate_parser = subcommand_parsers.add_parser(
        'clone-activate',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Activate the Fullcopy of a volume/consistency group',
        description='ViPR Activate Fullcopy of a volume/consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_activate_parser)
    clone_activate_parser.set_defaults(func=volume_clone_activate)
    
# Restore Clone Function
def volume_clone_activate(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        obj.volume_clone_activate(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Activate: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "activate",
                e.err_text,
                e.err_code)  
                                 
#clone_detach_parser
def clone_detach_parser(subcommand_parsers, common_parser):
    clone_detach_parser = subcommand_parsers.add_parser(
        'clone-detach',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Detach the Fullcopy of a volume/consistency group',
        description='ViPR Detach Fullcopy of a volume/consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_detach_parser)
    clone_detach_parser.set_defaults(func=volume_clone_detach)
    
# Restore Clone Function
def volume_clone_detach(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        obj.volume_clone_detach(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Detach: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "detach",
                e.err_text,
                e.err_code)     
            
            
def clone_deactivate_parser(subcommand_parsers, common_parser):
    clone_deactivate_parser = subcommand_parsers.add_parser(
        'clone-deactivate',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deactivate the Fullcopy of a consistency group',
        description='ViPR Deactivate Fullcopy of a consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_deactivate_parser)
    clone_deactivate_parser.set_defaults(func=volume_clone_deactivate)
    
# Restore Clone Function
def volume_clone_deactivate(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        obj.volume_clone_deactivate(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Deactivate: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "deactivate",
                e.err_text,
                e.err_code)     
            
def clone_checkprogress_parser(subcommand_parsers, common_parser):
    clone_checkprogress_parser = subcommand_parsers.add_parser(
        'clone-checkprogress',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Checkprogress of a Fullcopy of a volume',
        description='ViPR CheckProgress of a Fullcopy of a volume CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_common_parser(clone_checkprogress_parser)
    clone_checkprogress_parser.set_defaults(func=volume_clone_checkprogress)
    
# Restore Clone Function
def volume_clone_checkprogress(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = get_clone_source_resource(obj, args, None)
        
        res = obj.volume_clone_checkprogress(
            resourceUri,
            args.tenant + "/" + args.project + "/" + args.name,
            args.sync)
        
        from common import TableGenerator
        TableGenerator([res], ['is_sync_active','percent_synced','replicaState']).printTable()

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone Checkprogress: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "checkprogress",
                e.err_text,
                e.err_code)     
            
def clone_list_parser(subcommand_parsers, common_parser):
    clone_list_parser = subcommand_parsers.add_parser(
        'clone-list',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Get Clone list  of a consistency group',
        description='ViPR Clone List of a consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_list_parser(clone_list_parser)
    clone_list_parser.set_defaults(func=volume_clone_list)
    
# Restore Clone Function
def volume_clone_list(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = obj.storageResource_query(
            Volume.BLOCK, None, args.consistencygroup,
            None, args.project, args.tenant)
        
        res= obj.volume_clone_list(
            resourceUri)
        if('volume' in res):
            from common import TableGenerator
            TableGenerator(res['volume'], ['name']).printTable()
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone List: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "list",
                e.err_text,
                e.err_code)     

def clone_get_parser(subcommand_parsers, common_parser):
    clone_get_parser = subcommand_parsers.add_parser(
        'clone-show',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Get Clone show  of a consistency group',
        description='ViPR Clone show of a consistency group CLI usage.')
    
    # Add parameter from common clone parser.
    volume_clone_get_parser(clone_get_parser)
    clone_get_parser.set_defaults(func=volume_clone_get)
    
# Restore Clone Function
def volume_clone_get(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
        
    try:
        resourceUri = obj.storageResource_query(
            Volume.BLOCK, None, args.consistencygroup,
            None, args.project, args.tenant)
        
        res= obj.volume_clone_get(
            args.tenant + "/" + args.project + "/" + args.name,                      
            resourceUri)
        
        return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "Clone show: " +
                args.name +
                ", Failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                "clone",
                "show",
                e.err_text,
                e.err_code)   



def volume_create(args):
    obj = Volume(args.ip, args.port)
    size = common.to_bytes(args.size)
    if(not size):
        raise SOSError(SOSError.CMD_LINE_ERR, 'error: Invalid input for -size')
    if(args.count < 0):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            'error: Invalid input for -count')
    if(args.count > 1 and args.sync):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            'error: Synchronous operation is not allowed for' +
            ' bulk creation of volumes')
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.create(
            args.tenant + "/" + args.project, args.name, size,
            args.varray, args.vpool, None, args.sync,
            args.count, None, args.consistencygroup)
#        if(args.sync == False):
#            return common.format_json_object(res)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Create failed: " + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "create",
                "volume",
                e.err_text,
                e.err_code)
            

def rp_journal_create(args):
    obj = Volume(args.ip, args.port)
    size = common.to_bytes(args.size)
    if(not size):
        raise SOSError(SOSError.CMD_LINE_ERR, 'error: Invalid input for -size')
    if(args.count <= 0):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            'error: Invalid input for -count')
    if(args.count > 1 and args.sync):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            'error: Synchronous operation is not allowed for' +
            ' bulk creation of volumes')
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.rp_journal_create(
            args.consistencygroup , args.count , args.copyname, args.tenant + "/" + args.project, args.size,
            args.varray, args.vpool , args.sync)

    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Create failed: " + e.err_text)
        else:
            common.format_err_msg_and_raise(
                "create",
                "additional journal volumes",
                e.err_text,
                e.err_code)



# volume Update routines

def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Volume Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a volume')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='List of Volumes',
                                metavar='<volumename>',
                                nargs='+',
                                dest='name',
                                required=True)
    update_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-vpool', '-vp',
                                help='Name of New vpool',
                                metavar='<vpoolname>',
                                dest='vpool',
                                required=True)

    update_parser.set_defaults(func=volume_update)


def volume_update(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.update(args.tenant + "/" + args.project, args.name,
                         args.vpool)
        # return common.format_json_object(res)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Update failed: " + e.err_text)
        else:
            raise e

# Volume Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Volume Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a volume')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume(s)',
                                nargs='+',
                                required=True)
    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    delete_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    delete_parser.add_argument('-forceDelete', '-fd',
                            dest='forceDelete',
                            help='Delete volume forecibly',
                            action='store_true')
    delete_parser.add_argument('-vipronly', '-vo',
                            dest='vipronly',
                            help='Delete only from ViPR',
                            action='store_true')

    delete_parser.set_defaults(func=volume_delete)


def volume_delete(args):
    obj = Volume(args.ip, args.port)

    if(len(args.name) > 1 and args.sync):
        raise SOSError(
            SOSError.CMD_LINE_ERR,
            "error: Synchronous operation is not allowed for bulk " +
            "deletion of volumes")
    if(not args.tenant):
        args.tenant = ""
    try:
        if(len(args.name) < 2):
            obj.delete(
                args.tenant +
                "/" +
                args.project +
                "/" +
                args.name[
                    0],
                None,
                args.sync,
                args.forceDelete, args.vipronly)
        else:
            obj.delete(args.tenant + "/" + args.project, args.name,
                     args.forceDelete, args.vipronly)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Delete failed: " + e.err_text)
        else:
            raise common.format_err_msg_and_raise(
                "delete",
                "volume",
                e.err_text,
                e.err_code)


# Volume Export routines
def export_parser(subcommand_parsers, common_parser):
    export_parser = subcommand_parsers.add_parser(
        'export',
        description='ViPR Volume Export CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Export volume to a host')
    mandatory_args = export_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    export_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='Protocol',
                                choices=["FC", "iSCSI"],
                                dest='protocol',
                                required=True)
    mandatory_args.add_argument('-initiator_port', '-inp',
                                metavar='<initiator_port>',
                                dest='initiator_port',
                                help='Port of host ' +
                                '(WWPN for FC and IQN for ISCSI)',
                                required=True)
    mandatory_args.add_argument('-initiator_node', '-inn',
                                metavar='<initiator_node>',
                                dest='initiator_node',
                                help='Initiator\'s WWNN',
                                required=True)
    mandatory_args.add_argument('-hlu', '-hlu',
                                metavar='<lun>',
                                dest='hlu',
                                help='host logical unit number - ' +
                                'should be unused on the host',
                                required=True)
    mandatory_args.add_argument('-hostid', '-ho',
                                metavar='<hostid>',
                                dest='hostid',
                                help='Physical address of the host',
                                required=True)
    export_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    export_parser.set_defaults(func=volume_export)


def volume_export(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.export(args.tenant + "/" + args.project + "/" + args.name,
                         args.protocol, args.initiator_port,
                         args.initiator_node,
                         args.hlu, args.hostid, args.sync)
        if(args.sync is False):
            return common.format_json_object(res)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Export failed: " +
                e.err_text)
        else:
            raise e

# Volume UnExport routines


def unexport_parser(subcommand_parsers, common_parser):
    unexport_parser = subcommand_parsers.add_parser(
        'unexport',
        description='ViPR Volume Unexport CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Unexport volume from host')
    mandatory_args = unexport_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    unexport_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-initiator', '-in',
                                metavar='<initiator>',
                                dest='initiator',
                                help='Port of host (combination of WWNN and ' +
                                'WWPN for FC and IQN for ISCSI)',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='Protocol',
                                choices=["FC", "iSCSI"],
                                dest='protocol',
                                required=True)
    mandatory_args.add_argument('-hlu', '-hlu',
                                metavar='<lun>',
                                dest='hlu',
                                help='host logical unit number ' +
                                '(should be unused on the host)',
                                required=True)
    unexport_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Execute in synchronous mode',
                                 action='store_true')
    unexport_parser.set_defaults(func=volume_unexport)


def volume_unexport(args):
    obj = Volume(args.ip, args.port)
    if(not args.tenant):
        args.tenant = ""
    try:
        res = obj.unexport(args.tenant + "/" + args.project + "/" + args.name,
                           args.initiator, args.protocol, args.hlu, args.sync)
        if(args.sync is False):
            return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Unexport failed: " +
                e.err_text)
        else:
            raise e

# Volume Show routines


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show details of volume')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_parser.add_argument('-xml',
                             action="store_true",
                             dest='xml',
                             help='Display in XML format')
    show_parser.set_defaults(func=volume_show)


def volume_show(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.show(args.tenant + "/" + args.project + "/" + args.name,
                       False, args.xml)
        if(args.xml):
            return common.format_xml(res)
        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "volume",
            e.err_text,
            e.err_code)


# Volume ingest routines

def unmanaged_parser(subcommand_parsers, common_parser):
    unmanaged_parser = subcommand_parsers.add_parser(
        'unmanaged',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Unmanaged volume operations')
    subcommand_parsers = unmanaged_parser.add_subparsers(
        help='Use one of the commands')

    # ingest unmanaged volume
    ingest_parser = subcommand_parsers.add_parser(
        'ingest',
        parents=[common_parser],
        conflict_handler='resolve',
        help='ingest unmanaged volumes into ViPR')
    mandatory_args = ingest_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-vpool', '-vp',
                                metavar='<vpool>',
                                dest='vpool',
                                help='Name of vpool',
                                required=True)
    ingest_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                metavar='<varray>',
                                dest='varray',
                                help='Name of varray',
                                required=True)
    mandatory_args.add_argument('-volumes', '-vol',
                                metavar='<volumes>',
                                dest='volumes',
                                help='Name or id of volume',
                                nargs='+',
                                required=True)
    ingest_parser.add_argument('-hostlabel', '-hl',
                                metavar='<host label>',
                                dest='host',
                                help='Name of client host to which volume is exported')
    ingest_parser.add_argument('-cluster', '-cl',
                                metavar='<cluster name>',
                                dest='cluster',
                                help='Name of cluster')
    ingest_parser.add_argument('-ingestmethod', '-inmd',
                                metavar='<ingest method>',
                                dest='ingestmethod',
                                choices = ["Full" ,"VirtualVolumesOnly"],
                                default = "Full" ,
                                help='Ingest Method')

    # show unmanaged volume
    umshow_parser = subcommand_parsers.add_parser('show',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Show unmanaged volume')
    mandatory_args = umshow_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volume>',
                                dest='volume',
                                help='Name or id of volume',
                                required=True)

    ingest_parser.set_defaults(func=unmanaged_volume_ingest)

    umshow_parser.set_defaults(func=unmanaged_volume_show)


def unmanaged_volume_ingest(args):
    obj = Volume(args.ip, args.port)
    is_exported_vol_provided = False
    try:
        if(not args.tenant):
            args.tenant = ""

            areHostsSpecified = (args.host is not None)
            areClusterSpecified = (args.cluster is not None)

            if((areHostsSpecified or areClusterSpecified) == True):
                is_exported_vol_provided = True

            if(is_exported_vol_provided):
                obj.unmanaged_exported_volume_ingest(args.tenant, args.project,
                                          args.varray, args.vpool,
                                          args.volumes, args.host,
                                          args.cluster ,args.ingestmethod)
            else:
                obj.unmanaged_volume_ingest(args.tenant, args.project,
                                          args.varray, args.vpool,
                                          args.volumes,args.ingestmethod)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "ingest",
            "unmanaged",
            e.err_text,
            e.err_code)


def unmanaged_volume_show(args):
    obj = Volume(args.ip, args.port)
    try:
        res = obj.unmanaged_volume_show(args.volume)
        return common.format_json_object(res)
    except SOSError as e:
        raise common.format_err_msg_and_raise(
            "show",
            "unmanaged",
            e.err_text,
            e.err_code)


# Volume protection routines

def protect_parser(subcommand_parsers, common_parser):
    protect_parser = subcommand_parsers.add_parser(
        'protection',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Protect operation of the volume')
    subcommand_parsers = protect_parser.add_subparsers(
        help='Use one of the commands')

    # continuous protection start
    ptstart_parser = subcommand_parsers.add_parser(
        'start',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Start continuous protection for volume')
    mandatory_args = ptstart_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    protect_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mutex_group2 = ptstart_parser.add_mutually_exclusive_group(required=False)
    mutex_group2.add_argument(
        '-local',
        dest='local',
        action="store_true",
        help='Local continuous protection for the volume')
    mutex_group2.add_argument('-remote',
                              dest='remote',
                              action='store_true',
                              help='Remote continuous protection for volume')

    # continuous protection stop
    ptstop_parser = subcommand_parsers.add_parser(
        'stop',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Stop continuous protection for given volume')
    mandatory_args = ptstop_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    protect_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mutex_group2 = ptstop_parser.add_mutually_exclusive_group(required=False)
    mutex_group2.add_argument(
        '-local',
        dest='local',
        action="store_true",
        help='Local continuous protection for the volume')
    mutex_group2.add_argument('-remote',
                              dest='remote',
                              action='store_true',
                              help='Remote continuous protection for volume')

    # pause continuous protection
    ptpause_parser = subcommand_parsers.add_parser(
        'pause',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Pause continuous protection for volume')
    mandatory_args = ptpause_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    protect_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mutex_group2 = ptpause_parser.add_mutually_exclusive_group(required=False)
    mutex_group2.add_argument(
        '-local',
        dest='local',
        action="store_true",
        help='Local continuous protection for the volume')
    mutex_group2.add_argument('-remote',
                              dest='remote',
                              action='store_true',
                              help='Remote continuous protection for volume')

    # resume continuous protection
    ptresume_parser = subcommand_parsers.add_parser(
        'resume',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Resume continuous protection for given volume')
    mandatory_args = ptresume_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    protect_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mutex_group2 = ptresume_parser.add_mutually_exclusive_group(required=False)
    mutex_group2.add_argument(
        '-local',
        dest='local',
        action="store_true",
        help='Local continuous protection for the volume')
    mutex_group2.add_argument('-remote',
                              dest='remote',
                              action='store_true',
                              help='Remote continuous protection for volume')

    # failover continuous protection
    ptfailover_parser = subcommand_parsers.add_parser(
        'failover',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Failover continuous protection for volume')
    mandatory_args = ptfailover_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    protect_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mutex_group2 = ptfailover_parser.add_mutually_exclusive_group(
        required=False)
    mutex_group2.add_argument(
        '-local',
        dest='local',
        action="store_true",
        help='Local continuous protection for the volume')
    mutex_group2.add_argument('-remote',
                              dest='remote',
                              action='store_true',
                              help='Remote continuous protection for volume')

    ptstart_parser.set_defaults(func=volume_protect_start)

    ptstop_parser.set_defaults(func=volume_protect_stop)

    ptpause_parser.set_defaults(func=volume_protect_pause)

    ptresume_parser.set_defaults(func=volume_protect_resume)

    ptfailover_parser.set_defaults(func=volume_protect_failover)


def volume_protect_create(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "create",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_start(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "start",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "start",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_stop(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "stop",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "stop",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_pause(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "pause",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "pause",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_resume(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "resume",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "resume",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_failover(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "failover",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "failover",
            "protection",
            e.err_text,
            e.err_code)


def volume_protect_delete(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protection_operations(
            args.tenant + "/" + args.project + "/" + args.name,
            "delete",
            args.local, args.remote)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "delete",
            "protection",
            e.err_text,
            e.err_code)

# Common parameters for contineous copies parser.


def add_protection_sub_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume or Source volume name',
                                required=True)
    cc_common_parser.add_argument('-tenant', '-tn',
                                  metavar='<tenantname>',
                                  dest='tenant',
                                  help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-continuouscopyname', '-ccn',
                                metavar='<continuouscopyname>',
                                dest='continuouscopyname',
                                help='Protected volume name',
                                required=True)

# Common parameters for contineous copies parser.


def add_protection_common_parser(cc_common_parser):
    add_protection_sub_common_parser(cc_common_parser)
    cc_common_parser.add_argument('-type', '-t',
                                  help=Volume.VOLUME_PROTECTION_HELP,
                                  default='native',
                                  dest='type',
                                  metavar='<protectiontype>',
                                  choices=Volume.VOLUME_PROTECTIONS)

# Common parameters for contineous copies parser.


def add_protection_common_parser_nosrdf(cc_common_parser):
    add_protection_sub_common_parser(cc_common_parser)
    cc_common_parser.add_argument('-type', '-t',
                                  help=Volume.VOLUME_PROTECTION_HELP_EX_SRDF,
                                  default='native',
                                  dest='type',
                                  metavar='<protectiontype>',
                                  choices=Volume.VOLUME_PROTECTIONS_EX_SRDF)


# Volume protection routines
def mirror_protect_parser(subcommand_parsers, common_parser):
    mirror_protect_parser = subcommand_parsers.add_parser(
        'continuous_copies',
        description='ViPR continuous_copies CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Continuous copies operations of a volume')
    subcommand_parsers = mirror_protect_parser.add_subparsers(
        help='Use one of the commands')

    # mirror protection create
    mptcreate_parser = subcommand_parsers.add_parser(
        'start',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Start Continuous copies for given volume',
        description='ViPR continuous_copies create CLI usage.')
    # Add parameter from common protection parser.
    add_protection_common_parser_nosrdf(mptcreate_parser)
    mptcreate_parser.add_argument('-count', '-cu',
                                  metavar='<count>',
                                  dest='count',
                                  type=int,
                                  help='Maximum number of Continuous copies')

    mptcreate_parser.set_defaults(func=volume_mirror_protect_create)

    # mirror protection show
    mptshow_parser = subcommand_parsers.add_parser(
        'show',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies show CLI usage.',
        help='Show Continuous copy volume ,For Native and RP only')
    # Add parameter from common protection parser.
    add_protection_sub_common_parser(mptshow_parser)
    mptshow_parser.set_defaults(func=volume_mirror_protect_show)

    # mirror protection pause
    mptpause_parser = subcommand_parsers.add_parser(
        'pause',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies pause CLI usage.',
        help='Pause Continuous copy volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mptpause_parser)
    mptpause_parser.add_argument('-split', '-sp',
                dest='split',
                help='synchronize the mirror',
                choices=['true', 'false'],
                required=False,
                default='false')
    mptpause_parser.set_defaults(func=volume_mirror_protect_pause)

    # mirror protection stop
    mptstop_parser = subcommand_parsers.add_parser(
        'stop',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies stop CLI usage.',
        help='Stop Continuous copy volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mptstop_parser)
    mptstop_parser.set_defaults(func=volume_mirror_protect_stop)

    # mirror protection delete
    mptdelete_parser = subcommand_parsers.add_parser(
        'delete',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies delete CLI usage.',
        help='Deactivate Continuous copy volume')
    # Add parameter from common protection parser.
    add_protection_common_parser_nosrdf(mptdelete_parser)
    mptdelete_parser.set_defaults(func=volume_mirror_protect_delete)

    # failover continuous protection
    mpfailover_parser = subcommand_parsers.add_parser(
        'failover',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies failover CLI usage.',
        help='Failover continuous protection for volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mpfailover_parser)
    mpfailover_parser.set_defaults(op='failover')
    mpfailover_parser.set_defaults(func=volume_mirror_protect_failover_ops)

    # failover-test continuous protection
    mpfailovertest_parser = subcommand_parsers.add_parser(
        'failover-test',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies failover-test CLI usage.',
        help='Failover-test continuous protection for volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mpfailovertest_parser)
    mpfailovertest_parser.set_defaults(op='failover-test')
    mpfailovertest_parser.set_defaults(func=volume_mirror_protect_failover_ops)

    # failover-test-cancel continuous protection
    mpfailovertestcancel_parser = subcommand_parsers.add_parser(
        'failover-test-cancel',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies failover-test-cancel CLI usage.',
        help='Failover-test-cancel continuous protection for volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mpfailovertestcancel_parser)
    mpfailovertestcancel_parser.set_defaults(op='failover-test-cancel')
    mpfailovertestcancel_parser.set_defaults(
        func=volume_mirror_protect_failover_ops)

    # swap continuous protection
    mpswap_parser = subcommand_parsers.add_parser(
        'swap',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies swap CLI usage.',
        help='Swap continuous protection for volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mpswap_parser)
    mpswap_parser.set_defaults(op='swap')
    mpswap_parser.set_defaults(func=volume_mirror_protect_failover_ops)

    # mirror protection list
    mptlist_parser = subcommand_parsers.add_parser(
        'list',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies list CLI usage.',
        help='List continous copies for given volume , For Native and RP only')
    mandatory_args = mptlist_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    mptlist_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    mptlist_parser.set_defaults(func=volume_mirror_protect_list)

        # copy continuous protection
    mptcopy_parser = subcommand_parsers.add_parser(
        'copy',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies copy CLI usage.',
        help='Copy full copy of volume or local copy of a volume')
    mandatory_args = mptcopy_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume or Source volume name',
                                required=True)
    mptcopy_parser.add_argument('-tenant', '-tn',
                                metavar='<tenantname>',
                                dest='tenant',
                                help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    mandatory_args.add_argument('-copyname', '-cp',
                                metavar='<volumecopyname>',
                                dest='copyname',
                                help='Name of copy volume',
                                required=True)
    mptcopy_parser.add_argument('-count', '-cu',
                                metavar='<count>',
                                dest='count',
                                type=int,
                                help='Number of copies')

    mptcopy_parser.set_defaults(func=volume_mirror_protect_copy)

    # mirror protection resume
    mptresume_parser = subcommand_parsers.add_parser(
        'resume',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies resume CLI usage.',
        help='Resume Continuous copy volume')
    # Add parameter from common protection parser.
    add_protection_common_parser(mptresume_parser)
    mptresume_parser.set_defaults(func=volume_mirror_protect_resume)

    #mirror protection establish
    mptestablish_parser = subcommand_parsers.add_parser(
        'establish',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies establish CLI usage.',
        help='Establish Continuous copy volume')
    add_protection_common_parser(mptestablish_parser)
    mptestablish_parser.set_defaults(func=volume_mirror_protect_establish)

    #mirror protection restore
    mptrestore_parser = subcommand_parsers.add_parser(
        'restore',
        parents=[common_parser],
        conflict_handler='resolve',
        description='ViPR continuous_copies restore CLI usage.',
        help='Restore Continuous copy volume')
    add_protection_common_parser(mptrestore_parser)
    mptrestore_parser.set_defaults(func=volume_mirror_protect_restore)


def volume_mirror_protect_create(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        fullpathvol = args.tenant + "/" + args.project + "/" + args.name

        obj.mirror_protection_create(
            fullpathvol,
            args.continuouscopyname,
            args.count,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "start",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_delete(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_delete(
            fullpathvol,
            args.continuouscopyname,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "delete",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_failover_ops(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        fullpathvol = args.tenant + "/" + args.project + "/" + args.name

        obj.mirror_protection_failover_ops(
            fullpathvol,
            args.continuouscopyname,
            args.type,
            args.op)

    except SOSError as e:
        common.format_err_msg_and_raise(
            args.op,
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_stop(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        fullpathvol = args.tenant + "/" + args.project + "/" + args.name

        obj.mirror_protection_stop(
            fullpathvol,
            args.continuouscopyname,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "stop",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_list(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.mirror_protection_list(
            args.tenant +
            "/" +
            args.project +
            "/" +
            args.name)

        mirrorlist = []
        vol_uri = obj.volume_query(
            args.tenant +
            "/" +
            args.project +
            "/" +
            args.name)
        for mirr_uri in res:
            protectobj = obj.get_uri_mirror_protection_vol(vol_uri, mirr_uri)
            nhid = None
            if(protectobj is not None):
                if("source" in protectobj and "name" in protectobj["source"]):
                    del protectobj["source"]["name"]
                if(protectobj['varray']):
                    nh = VirtualArray(
                        args.ip,
                        args.port).varray_show(protectobj['varray']['id'])
                    if(nh is not None):
                        protectobj['varray_name'] = nh['name']
                if(protectobj['source']):
                    vol = obj.show_by_uri(protectobj['source']['id'])
                    if(vol is not None):
                        protectobj['source_volume'] = vol['name']
                if(protectobj['storage_controller']):
                    storagesys = StorageSystem(
                        args.ip,
                        args.port).show_by_uri(
                        protectobj['storage_controller'])
                    if(storagesys):
                        protectobj['storagesystem_name'] = storagesys['name']
                mirrorlist.append(protectobj)
        if(len(mirrorlist) > 0):
            from common import TableGenerator
            TableGenerator(
                mirrorlist,
                ['name',
                 'source_volume',
                 'varray_name',
                 'protocols',
                 'storagesystem_name']).printTable()

    except SOSError as e:
        raise common.format_err_msg_and_raise(
            "list",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_show(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        res = obj.mirror_protection_show(fullpathvol, args.continuouscopyname)
        return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_pause(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_pause(
            fullpathvol,
            args.continuouscopyname,
            args.type,
            args.split)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "pause",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_establish(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_establish(
            fullpathvol,
            args.continuouscopyname,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "establish",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_restore(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_restore(
            fullpathvol,
            args.continuouscopyname,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "restore",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_resume(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_resume(
            fullpathvol,
            args.continuouscopyname,
            args.type)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "resume",
            "continuous_copies",
            e.err_text,
            e.err_code)


def volume_mirror_protect_copy(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        fullpathvol = args.tenant + "/" + args.project + "/" + args.name
        obj.mirror_protection_copy(fullpathvol, args.copyname, args.count)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "copy",
            "continuous_copies",
            e.err_text,
            e.err_code)


# Volume protection set routines

def protectionset_parser(subcommand_parsers, common_parser):
    protectionset_parser = subcommand_parsers.add_parser(
        'protectionset',
        description='ViPR protectionset CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Protectionset operations of the volume')
    subcommand_parsers = protectionset_parser.add_subparsers(
        help='Use one of the commands')

    '''# protection set get resources
    psresources_parser = subcommand_parsers.add_parser(
        'get_resources',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Get the resources for volume protection set')

    mandatory_args = psresources_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    psresources_parser.add_argument('-tenant', '-tn',
                                    metavar='<tenantname>',
                                    dest='tenant',
                                    help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    psresources_parser.set_defaults(func=volume_protectionset_getresources)'''

    # protection set show
    psshow_parser = subcommand_parsers.add_parser(
        'show',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show volume protection set')

    mandatory_args = psshow_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    psshow_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    psshow_parser.set_defaults(func=volume_protectionset_show)

    '''# protection set discover
    psdiscover_parser = subcommand_parsers.add_parser(
        'discover',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover volume protection set')

    mandatory_args = psdiscover_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    psdiscover_parser.add_argument('-tenant', '-tn',
                                   metavar='<tenantname>',
                                   dest='tenant',
                                   help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    psdiscover_parser.set_defaults(func=volume_protectionset_discover)


def volume_protectionset_getresources(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protectionset_getresources(
            args.tenant +
            "/" +
            args.project +
            "/" +
            args.name)

        if(res is not None):
            return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "get_resources",
            "protectionset",
            e.err_text,
            e.err_code)


def volume_protectionset_discover(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protectionset_discover(
            args.tenant +
            "/" +
            args.project +
            "/" +
            args.name)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "discover",
            "protectionset",
            e.err_text,
            e.err_code)'''


def volume_protectionset_show(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.protectionset_show(
            args.tenant +
            "/" +
            args.project +
            "/" +
            args.name)
        return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "protectionset",
            e.err_text,
            e.err_code)


# Volume List routines

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Volume List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists volumes under a project')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    list_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List volumes with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List volumes having more headers',
                             action='store_true')
    list_parser.set_defaults(func=volume_list)


def volume_list(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        output = obj.list_volumes(args.tenant + "/" + args.project)
        if(len(output) > 0):
            if(args.verbose is False):
                for record in output:
                    if("project" in record and "name" in record["project"]):
                        del record["project"]["name"]
                    if("vpool" in record and "vpool_params" in record["vpool"]
                       and record["vpool"]["vpool_params"]):
                        for vpool_param in record["vpool"]["vpool_params"]:
                            record[vpool_param["name"]] = vpool_param["value"]
                        record["vpool"] = None
                # show a short table
                from common import TableGenerator
                if(not args.long):
                    TableGenerator(output, ['name', 'provisioned_capacity_gb',
                                            'protocols']).printTable()
                else:
                    TableGenerator(output, ['name', 'provisioned_capacity_gb',
                                            'protocols', 'thinly_provisioned',
                                            'tags']).printTable()
            else:
                return common.format_json_object(output)
        else:
            return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "list",
            "volume",
            e.err_text,
            e.err_code)


def task_parser(subcommand_parsers, common_parser):
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR Volume List tasks CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of volume tasks')
    mandatory_args = task_parser.add_argument_group('mandatory arguments')

    task_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    task_parser.add_argument('-name', '-n',
                             dest='name',
                             metavar='<volumename>',
                             help='Name of volume')
    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')
    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')

    task_parser.set_defaults(func=volume_list_tasks)


def volume_list_tasks(args):
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(args.id):
            res = obj.list_tasks(
                args.tenant +
                "/" +
                args.project,
                args.name,
                args.id)
            if(res):
                return common.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant + "/" + args.project, args.name)
            if(res and len(res) > 0):
                if(args.verbose):
                    return common.format_json_object(res)
                else:
                    from common import TableGenerator
                    TableGenerator(
                        res, ["module/id", "name", "state"]).printTable()
        else:
            res = obj.list_tasks(args.tenant + "/" + args.project)
            if(res and len(res) > 0):
                if(not args.verbose):
                    from common import TableGenerator
                    TableGenerator(
                        res, ["module/id", "name", "state"]).printTable()
                else:
                    return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "tasks",
            "volume",
            e.err_text,
            e.err_code)


def expand_parser(subcommand_parsers, common_parser):
    expand_parser = subcommand_parsers.add_parser('expand',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Expand volume')
    mandatory_args = expand_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<volumename>',
                                dest='name',
                                help='Name of Volume',
                                required=True)
    expand_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-size', '-s',
                                help='New size of volume: {number}[unit]. ' +
                                'A size suffix of K for kilobytes, ' +
                                'M for megabytes, G for gigabytes, ' +
                                'T for terabytes is optional.' +
                                'Default unit is bytes.',
                                metavar='<volumesize[kKmMgGtT]>',
                                dest='size',
                                required=True)
    expand_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    expand_parser.set_defaults(func=volume_expand)


def volume_expand(args):
    size = common.to_bytes(args.size)
    if(not size):
        raise SOSError(SOSError.CMD_LINE_ERR,
                       'error: Invalid input for -size')
    obj = Volume(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.expand(args.tenant + "/" + args.project +
                         "/" + args.name, size, args.sync)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "expand",
            "volume",
            e.err_text,
            e.err_code)


def tag_parser(subcommand_parsers, common_parser):
    tag_parser = subcommand_parsers.add_parser(
        'tag',
        description='ViPR Volume Tag CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Tag a volume')
    mandatory_args = tag_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of volume',
                                metavar='<volumename>',
                                dest='name',
                                required=True)
    tag_parser.add_argument('-tenant', '-tn',
                            metavar='<tenantname>',
                            dest='tenant',
                            help='Name of tenant')

    tag.add_mandatory_project_parameter(mandatory_args)

    tag.add_tag_parameters(tag_parser)

    tag_parser.set_defaults(func=volume_tag)


def volume_tag(args):
    obj = Volume(args.ip, args.port)
    try:
        if(args.add is None and args.remove is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "viprcli volume tag: error: at least one of " +
                           "the arguments -add -remove is required")

        if(not args.tenant):
            args.tenant = ""

        res = obj.tag(args.tenant + "/" + args.project +
                      "/" + args.name, args.add, args.remove)

    except SOSError as e:
        common.format_err_msg_and_raise(
            "volume",
            "tag",
            e.err_text,
            e.err_code)
			

def migration_list_parser(subcommand_parsers, common_parser):
    migration_list_parser = subcommand_parsers.add_parser(
        'migration-list',
        description='ViPR Volume Migrations list',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List all the migrations')
    mandatory_args = migration_list_parser.add_argument_group('mandatory arguments')
    migration_list_parser.set_defaults(func=migration_list)
	
def migration_list(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_list())
    except SOSError as e:
        common.format_err_msg_and_raise(
            "get",
            "migrations",
            e.err_text,
            e.err_code)
			
def migration_show_parser(subcommand_parsers, common_parser):
    migration_show_parser = subcommand_parsers.add_parser(
        'migration-show',
        description='ViPR Volume Migration show',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a migration')
    mandatory_args = migration_show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-migration_id','-id',
                                help='Migration Id',
                                dest='migration_id',
                                required='True',
                                metavar='<migration_id>')	
    migration_show_parser.set_defaults(func=migration_show)
	
def migration_show(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_show(args.migration_id))
    except SOSError as e:
        common.format_err_msg_and_raise(
            "get",
            "migration",
            e.err_text,
            e.err_code)

def migration_cancel_parser(subcommand_parsers, common_parser):
    migration_cancel_parser = subcommand_parsers.add_parser(
        'migration-cancel',
        description='ViPR Volume Migration cancel',
        parents=[common_parser],
        conflict_handler='resolve',
        help='cancel the in-progress data migration')
    mandatory_args = migration_cancel_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-migration_id','-id',
                                help='Migration Id',
                                dest='migration_id',
                                required='True',
                                metavar='<migration_id>')	
    migration_cancel_parser.set_defaults(func=migration_cancel)
	
def migration_cancel(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_cancel(args.migration_id))
    except SOSError as e:
        common.format_err_msg_and_raise(
            "post",
            "migration",
            e.err_text,
            e.err_code)
			
def migration_pause_parser(subcommand_parsers, common_parser):
    migration_pause_parser = subcommand_parsers.add_parser(
        'migration-pause',
        description='ViPR Volume Migration pause',
        parents=[common_parser],
        conflict_handler='resolve',
        help='pause the in-progress data migration')
    mandatory_args = migration_pause_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-migration_id','-id',
                                help='Migration Id',
                                dest='migration_id',
                                required='True',
                                metavar='<migration_id>')	
    migration_pause_parser.set_defaults(func=migration_pause)
	
def migration_pause(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_pause(args.migration_id))
    except SOSError as e:
        common.format_err_msg_and_raise(
            "post",
            "migration",
            e.err_text,
            e.err_code)
			
def migration_resume_parser(subcommand_parsers, common_parser):
    migration_resume_parser = subcommand_parsers.add_parser(
        'migration-resume',
        description='ViPR Volume Migration resume',
        parents=[common_parser],
        conflict_handler='resolve',
        help='resume paused data migration')
    mandatory_args = migration_resume_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-migration_id','-id',
                                help='Migration Id',
                                dest='migration_id',
                                required='True',
                                metavar='<migration_id>')	
    migration_resume_parser.set_defaults(func=migration_resume)
	
def migration_resume(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_resume(args.migration_id))
    except SOSError as e:
        common.format_err_msg_and_raise(
            "post",
            "migration",
            e.err_text,
            e.err_code)
			
def migration_deactivate_parser(subcommand_parsers, common_parser):
    migration_deactivate_parser = subcommand_parsers.add_parser(
        'migration-deactivate',
        description='ViPR Volume Migration deactivate',
        parents=[common_parser],
        conflict_handler='resolve',
        help='remove the finished migration in ViPR and VPLEX')
    mandatory_args = migration_deactivate_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-migration_id','-id',
                                help='Migration Id',
                                dest='migration_id',
                                required='True',
                                metavar='<migration_id>')	
    migration_deactivate_parser.set_defaults(func=migration_deactivate)
	
def migration_deactivate(args):
    obj = Volume(args.ip, args.port)
    try:
        return common.format_json_object(obj.migration_deactivate(args.migration_id))
    except SOSError as e:
        common.format_err_msg_and_raise(
            "post",
            "migration",
            e.err_text,
            e.err_code)
			
#
# Volume Main parser routine
#
def volume_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('volume',
                                         description='ViPR Volume CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Volume')
    subcommand_parsers = parser.add_subparsers(help='Use one of subcommands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)
    
    rp_journal_parser(subcommand_parsers, common_parser)

    # Clone command parser
    clone_parser(subcommand_parsers, common_parser)
    
     # Clone restore command parser
    clone_restore_parser(subcommand_parsers, common_parser)
    
     # Clone resync command parser
    clone_resync_parser(subcommand_parsers, common_parser)
    
     # Clone activate command parser
    clone_activate_parser(subcommand_parsers, common_parser)
    
     # Clone detach command parser
    clone_detach_parser(subcommand_parsers, common_parser)
    
     # GET full-copy volume of a consistency group command parser
    clone_list_parser(subcommand_parsers, common_parser)
    
    # GET full-copy volume of a consistency group command parser
    clone_get_parser(subcommand_parsers, common_parser)
    
    # Delete or Deactivate the 
    clone_deactivate_parser(subcommand_parsers, common_parser)
    
    # Check progress
    clone_checkprogress_parser(subcommand_parsers, common_parser)

     
    
    
    
    
    
    

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # export command parser
    # export_parser(subcommand_parsers, common_parser)

    # unexport command parser
    # unexport_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # expand volume parser
    expand_parser(subcommand_parsers, common_parser)

    # task list command parser
    task_parser(subcommand_parsers, common_parser)

    # protection  command parser
    #protect_parser(subcommand_parsers, common_parser)

    # mirror protection  command parser
    mirror_protect_parser(subcommand_parsers, common_parser)

    # protection set  command parser
    protectionset_parser(subcommand_parsers, common_parser)

    # unmanaged volume  command parser
    unmanaged_parser(subcommand_parsers, common_parser)

    tag_parser(subcommand_parsers, common_parser)
	
    # migration list command parser
    migration_list_parser(subcommand_parsers, common_parser)
	
    # migration list command parser
    migration_show_parser(subcommand_parsers, common_parser)
	
    # cancel migration command parser
    migration_cancel_parser(subcommand_parsers, common_parser)
	
    # pause migration command parser
    migration_pause_parser(subcommand_parsers, common_parser)
	
    # resume migration command parser
    migration_resume_parser(subcommand_parsers, common_parser)
	
    # deactivate migration command parser
    migration_deactivate_parser(subcommand_parsers, common_parser)
