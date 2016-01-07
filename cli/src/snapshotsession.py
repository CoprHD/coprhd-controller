# Copyright (c)2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
import volume
import snapshot
import consistencygroup
import json
from common import SOSError


class SnapshotSession(object):
    URI_BLOCK_SNAPSHOT_SESSION = '/block/volumes/{id}/protection/snapshot-sessions'
    

    # The class definition for operations on 'Snapshot-Session'.

    # Commonly used URIs for the 'Snapshot-Session' module
    URI_SNAPSHOT_SESSION_LIST = '/block/volumes/{0}/protection/snapshot-sessions'
    URI_SNAPSHOTS_SESSION = '/block/snapshot-sessions/{0}'
    URI_LINK_TARGET_TO_SNAPSHOT_SESSION = '/block/snapshot-sessions/{0}/link-targets'
    URI_RELINK_TARGET_TO_SNAPSHOT_SESSION = '/block/snapshot-sessions/{0}/relink-targets'
    URI_UNLINK_TARGET_TO_SNAPSHOT_SESSION = '/block/snapshot-sessions/{0}/unlink-targets'
    URI_DEACTIVATE_SNAPSHOT_SESSION = '/block/snapshot-sessions/{0}/deactivate'
    URI_RESTORE_SNAPSHOT_SESSION = '/block/snapshot-sessions/{0}/restore'
    URI_BULK_SNAPSHOT_SESSION = '/block/snapshot-sessions/bulk'
    
    COPY_MODE = ["copy" ,"nocopy"]
    VOLUMES = 'volumes'
    CG = 'consistency-groups'
    BLOCK = 'block'
    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        
    def snapshotsession_query(self, resuri, snapshotsessionName):
        if(resuri is not None):
            uris = self.snapshotsession_list_uri(resuri)
            for uri in uris:
                snapshotsession = self.snapshotsession_show_uri(uri['id'])
                if(False == (common.get_node_value(snapshotsession, 'inactive'))):
                    if (snapshotsession['name'] == snapshotsessionName):
                        return snapshotsession['id']

        raise SOSError(
            SOSError.SOS_FAILURE_ERR,
            "snapshot Session with the name:" +
            snapshotsessionName +
            " Not Found")
    
    def snapshotsession_show_uri(self, snapsessionuri, xml=False):
        '''
        Retrieves snapshot session details based on snapshot Name or Label
        Parameters:
            snapsessionuri : uri of the snapshot session
        Returns:
            Snapshot Session details in JSON response payload
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            SnapshotSession.URI_SNAPSHOTS_SESSION.format(snapsessionuri), None, None, xml)

        if(xml is False):
            return common.json_decode(s)
        return s
    
    
    def snapshotsession_list_uri(self, ouri):
        '''
        Makes REST API call to list snapshot sessions under a volume
         parameters:
            ouri      : uri of volumes or consistency-group
        Returns:
            return list of snapshots sessions
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            SnapshotSession.URI_SNAPSHOT_SESSION_LIST.format(ouri), None)
        o = common.json_decode(s)
        return o['snapshot_session']
        
    def storageResource_query(self,
                              storageresType,
                              volumeName,
                              project,
                              tenant):
        resourcepath = "/" + project + "/"
        if(tenant is not None):
            resourcepath = tenant + resourcepath

        resUri = None
        resourceObj = None
        
        if(SnapshotSession.BLOCK == storageresType and volumeName is not None):
            resourceObj = volume.Volume(self.__ipAddr, self.__port)
            resUri = resourceObj.volume_query(resourcepath + volumeName)
        else:
            resourceObj = None

        return resUri
    
    def snapshotsession_create(self, otype, typename, snapsessionlabel, tenant, project, volume, count, target_name, copymode, resourceUri):
        '''new snapshot session is created, for a given volume
            parameters:
                otype      : block should be provided
                typename   : either volume
                or consistency-groups should be provided
                ouri       : uri of volume or consistency group
                snapsessionlabel  : name of the snapshot session
        '''

        # check snapshot session is already exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resourceUri, snapsessionlabel)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(is_snapshot_exist):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Snapshot Session with name " +
                snapsessionlabel +
                " already exists under " +
                typename)

        body = None
        new_linked_targets_dict = None
        if (count or target_name or copymode):
            new_linked_targets_dict = {
                'count' : count,
                'target_name' : target_name,
                'copy_mode' : copymode
                }
        
        parms = {
            'name': snapsessionlabel,
            'new_linked_targets': new_linked_targets_dict
        }
        body = json.dumps(parms)
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_SNAPSHOT_SESSION_LIST.format(resourceUri), body)
        o = common.json_decode(s)
        return o
    
    def snapshotsession_list(self, resourceUri):
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            SnapshotSession.URI_SNAPSHOT_SESSION_LIST.format(resourceUri), None)
        o = common.json_decode(s)
        return o['snapshot_session']
    
    
    def snapshotsession_name_to_uri(self, resuri, snapsession_label):
        if(resuri is not None):
            uris = self.snapshotsession_list_uri(resuri)
            for uri in uris:
                snapshotsession = self.snapshotsession_show_uri(uri['id'])
                if(False == (common.get_node_value(snapshotsession, 'inactive'))):
                    if (snapshotsession['name'] == snapsession_label):
                        return snapshotsession['id']
        return None
    
    def snapshotsession_link_target(self, snapsession_label, resuri, count, target_name, copymode):
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
            
        snapshotsession_uri = self.snapshotsession_name_to_uri(resuri, snapsession_label)

        body = None
        new_linked_targets_dict = {
            'count' : count,
            'target_name' : target_name,
            'copy_mode' : copymode
            }
        
        parms = {
            'new_linked_targets': new_linked_targets_dict
        }
        body = json.dumps(parms)
        
        
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_LINK_TARGET_TO_SNAPSHOT_SESSION.format(snapshotsession_uri), body)
        o = common.json_decode(s)
        return o
    
    def get_target_ids_from_names(self, storageresType, storageresTypeName, resuri, target_names):
        target_ids = []
        for name in target_names:
            snapshotResourceObj = snapshot.Snapshot(self.__ipAddr, self.__port)
            uri = snapshotResourceObj.snapshot_query(storageresType, storageresTypeName, resuri, name)
            if(uri is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "-Invalid target name :" + name)
            target_ids.append(uri)
        return target_ids
    
    def snapshotsession_relink_target(self, snapsession_label, storageresType, storageresTypeName, resuri, target_names):
        
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
        
        snapshotsession_uri = self.snapshotsession_name_to_uri(resuri, snapsession_label)
        target_ids = self.get_target_ids_from_names(storageresType, storageresTypeName, resuri, target_names)
        body = None
        parms = {
            'ids' : target_ids
            }
        
        body = json.dumps(parms)
        
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_RELINK_TARGET_TO_SNAPSHOT_SESSION.format(snapshotsession_uri), body)
        o = common.json_decode(s)
        return o
    
    
    def _get_target_delete_tuple(self, resources, resuri, storageresType, storageresTypeName):
        copyEntries = []
        for copy in resources:
            copyParam = []
            copyParam = copy.split(":")
            copydict = dict()
            snapshotResourceObj = snapshot.Snapshot(self.__ipAddr, self.__port)
            uri = snapshotResourceObj.snapshot_query(storageresType, storageresTypeName, resuri, copyParam[0])
            copydict['id'] = uri
            if(len(copyParam) > 1):
                if(copyParam[1] == "delete"):
                    copydict['delete_target'] = True
                else:
                    raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "Please specify :delete if the target volume is to be deleted")
            else:
                copydict['delete_target'] = False
            copyEntries.append(copydict)
        return copyEntries

    
    def snapshotsession_unlink_target(self, snapsession_label, resuri, target_names, storageresType, storageresTypeName):
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
        
        snapshotsession_uri = self.snapshotsession_name_to_uri(resuri, snapsession_label)
        linked_target_list = self._get_target_delete_tuple(target_names, resuri, storageresType, storageresTypeName)
        body = None
        parms = {
            'linked_targets' : linked_target_list
            }
        body = json.dumps(parms)
        
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_UNLINK_TARGET_TO_SNAPSHOT_SESSION.format(snapshotsession_uri), body)
        o = common.json_decode(s)
        return o

    
    def snapshotsession_show(self, snapsession_label, resuri):
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
        
        snapshotsession_uri = self.snapshotsession_name_to_uri(resuri, snapsession_label)
        return self.snapshotsession_show_uri(snapshotsession_uri)
    
    def snapshotsession_deactivate(self, snapsession_label, resuri):
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
            
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_DEACTIVATE_SNAPSHOT_SESSION.format(id), None)
        o = common.json_decode(s)
        return o
    
    def snapshotsession_restore(self, snapsession_label, resuri):
        # check if snapshot session does'nt exist
        is_snapshot_exist = True
        try:
            id = self.snapshotsession_query(resuri, snapsession_label)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                is_snapshot_exist = False
            else:
                raise e

        if(not is_snapshot_exist):
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Snapshot Session with name " +
                snapsession_label +
                " does not exist")
            
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_RESTORE_SNAPSHOT_SESSION.format(id), None)
        o = common.json_decode(s)
        return o
    
    def get_snapshotsession_ids_from_names(self, resuri, snapshot_session_names):
        snapshot_session_ids = []
        for name in snapshot_session_names:
            uri = self.snapshotsession_name_to_uri(resuri, name)
            if(uri is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "-Invalid snapshot session name :" + name)
            snapshot_session_ids.append(uri)
        return snapshot_session_ids
    

    def snapshotsession_bulk(self, resuri, snapshot_session_names):
        snapshot_session_ids = self.get_snapshotsession_ids_from_names(resuri, snapshot_session_names)
        body = None
        parms = {
                 'id' : snapshot_session_ids
        }
        body = json.dumps(parms)
        # REST api call
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            SnapshotSession.URI_BULK_SNAPSHOT_SESSION, body)
        o = common.json_decode(s)
            
        return o

# Snapshot Session Create routines

def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser('create',
                                                  description='ViPR Snapshot Session' +
                                                  'Create CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='create a snapshot session')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
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
    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volumename>',
                                dest='volume',
                                help='Name of a volume',
                                required=True)
    create_parser.add_argument('-count', '-ct',
                               dest='count',
                               help='Number of target volumes')
    create_parser.add_argument('-targetname', '-tgn',
                               help='This option specifies the target name',
                               dest='target_name',
                               metavar='<target_name>')
    create_parser.add_argument('-copymode', '-cm',
                               help='whether to create in copy or nocopy mode' ,
                               dest='copymode',
                               choices=SnapshotSession.COPY_MODE)

    create_parser.set_defaults(func=snapshotsession_create)


def snapshotsession_create(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        if(args.count or args.target_name or args.copymode):
            if (not args.count or not args.target_name or not args.copymode):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "-Please specify all the three : count, target_name and copymode")
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_create(storageresType, storageresTypeName, args.name, args.tenant, args.project, args.volume, args.count, args.target_name, args.copymode, resourceUri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create",
            "snapshot session ",
            e.err_text,
            e.err_code)


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
                                                  description='ViPR Snapshot Session' +
                                                  'List CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='list volume snapshot sessions')

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
    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volumename>',
                                dest='volume',
                                help='Name of a volume',
                                required=True)
    list_parser.set_defaults(func=snapshotsession_list)


def snapshotsession_list(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        records = obj.snapshotsession_list(resourceUri)
        
        from common import TableGenerator
        TableGenerator(records, ['name']).printTable()
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "list",
            "snapshot session",
            e.err_text,
            e.err_code)
        

def link_target_parser(subcommand_parsers, common_parser):
    link_target_parser = subcommand_parsers.add_parser('linktarget',
                                                  description='ViPR Snapshot Session' +
                                                  'Link target CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='link target to a snapshot session')

    mandatory_args = link_target_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
                                required=True)
    link_target_parser.add_argument('-tenant', '-tn',
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
    mandatory_args.add_argument('-count', '-ct',
                               dest='count',
                               help='Number of target volumes ',
                               required=True)
    mandatory_args.add_argument('-targetname', '-tgn',
                               help='This option specifies the target name',
                               dest='target_name',
                               metavar='<target_name>',
                               required=True)
    mandatory_args.add_argument('-copymode', '-cm',
                               help='whether to create in copy or nocopy mode' ,
                               dest='copymode',
                               choices=SnapshotSession.COPY_MODE,
                               required=True)

    link_target_parser.set_defaults(func=snapshotsession_link_target)


def snapshotsession_link_target(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_link_target(args.name, resourceUri, args.count, args.target_name, args.copymode)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "link target",
            "snapshot session",
            e.err_text,
            e.err_code)

def relink_target_parser(subcommand_parsers, common_parser):
    relink_target_parser = subcommand_parsers.add_parser('relinktargets',
                                                  description='ViPR Snapshot Session' +
                                                  'reLink targets CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='relink target to a snapshot session')

    mandatory_args = relink_target_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
                                required=True)
    relink_target_parser.add_argument('-tenant', '-tn',
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
    mandatory_args.add_argument('-targetvolumes', '-tgnames',
                               dest='target_names', nargs='+',
                               help='List of target volumes',
                               required=True)

    relink_target_parser.set_defaults(func=snapshotsession_relink_target)


def snapshotsession_relink_target(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_relink_target(args.name, storageresType, storageresTypeName, resourceUri, args.target_names)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "relink target",
            "snapshot session",
            e.err_text,
            e.err_code)
        
def unlink_target_parser(subcommand_parsers, common_parser):
    unlink_target_parser = subcommand_parsers.add_parser('unlinktargets',
                                                  description='ViPR Snapshot Session' +
                                                  'unLink targets CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='unlink target from a snapshot session')

    mandatory_args = unlink_target_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
                                required=True)
    unlink_target_parser.add_argument('-tenant', '-tn',
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
    mandatory_args.add_argument('-targetvolumes', '-tgnames',
                               dest='target_names', nargs='+',
                               help='List of target volumes in the format <target_name>:delete',
                               required=True)
    unlink_target_parser.set_defaults(func=snapshotsession_unlink_target)


def snapshotsession_unlink_target(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_unlink_target(args.name, resourceUri, args.target_names, storageresType, storageresTypeName)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "unlink target",
            "snapshot session",
            e.err_text,
            e.err_code)

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                                                  description='ViPR Snapshot Session' +
                                                  'show snapshot session CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='show a snapshot session')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
                                required=True)
    show_parser.add_argument('-tenant', '-tn',
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
    show_parser.set_defaults(func=snapshotsession_show)


def snapshotsession_show(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        respContent = obj.snapshotsession_show(args.name, resourceUri)
        return common.format_json_object(respContent)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "show",
            "snapshot session",
            e.err_text,
            e.err_code)
        
def deactivate_parser(subcommand_parsers, common_parser):
    deactivate_parser = subcommand_parsers.add_parser('deactivate',
                                                  description='ViPR Snapshot Session' +
                                                  'deactivate snapshot session CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='deactivate a snapshot session')

    mandatory_args = deactivate_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
                                required=True)
    deactivate_parser.add_argument('-tenant', '-tn',
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
    deactivate_parser.set_defaults(func=snapshotsession_deactivate)


def snapshotsession_deactivate(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_deactivate(args.name, resourceUri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "deactivate",
            "snapshot session",
            e.err_text,
            e.err_code)
        
def restore_parser(subcommand_parsers, common_parser):
    restore_parser = subcommand_parsers.add_parser('restore',
                                                  description='ViPR Snapshot Session' +
                                                  'restore snapshot session CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='restore a snapshot session')

    mandatory_args = restore_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='Name of Snapshot Session',
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
    mandatory_args.add_argument('-volume', '-vol',
                                metavar='<volumename>',
                                dest='volume',
                                help='Name of a volume',
                                required=True)
    restore_parser.set_defaults(func=snapshotsession_restore)


def snapshotsession_restore(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        
        obj.snapshotsession_restore(args.name, resourceUri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise(
            "restore",
            "snapshot session",
            e.err_text,
            e.err_code)
        
def bulk_parser(subcommand_parsers, common_parser):
    bulk_parser = subcommand_parsers.add_parser('bulk',
                                                  description='ViPR Snapshot Session' +
                                                  'bulk snapshot session CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Gets the details of the BlockSnapshotSession instances with the names specified')

    mandatory_args = bulk_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-SnapshotSessionNames', '-ns',
                                metavar='<SnapshotSessionNames>',
                                dest='names',
                                nargs='+',
                                help='Names of Snapshot Sessions',
                                required=True)
    bulk_parser.add_argument('-tenant', '-tn',
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
    bulk_parser.set_defaults(func=snapshotsession_bulk)


def snapshotsession_bulk(args):

    obj = SnapshotSession(args.ip, args.port)
    try:
        storageresType = SnapshotSession.BLOCK
        storageresTypeName = SnapshotSession.VOLUMES
        resourceUri = obj.storageResource_query(
            storageresType,
            args.volume,
            args.project,
            args.tenant)
        respContent = obj.snapshotsession_bulk(resourceUri, args.names)
        return common.format_json_object(respContent)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "bulk",
            "snapshot session",
            e.err_text,
            e.err_code)
#
# Snapshot Session Main parser routine
#
def snapshotsession_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('snapshotsession',
                                         description='ViPR Snapshot Session CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Snapshot Session')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)
    
    # list command parser
    list_parser(subcommand_parsers, common_parser)
    
    #link target command parser
    link_target_parser(subcommand_parsers, common_parser)
    
    #relink target command parser
    relink_target_parser(subcommand_parsers, common_parser)
    
    #unlink target command parser
    unlink_target_parser(subcommand_parsers, common_parser)
    
    #show command parser
    show_parser(subcommand_parsers, common_parser)
    
    #deactivate command parser
    deactivate_parser(subcommand_parsers, common_parser)
    
    #restore command parser
    restore_parser(subcommand_parsers, common_parser)
    
    #bulk command parser
    bulk_parser(subcommand_parsers, common_parser)