#!/usr/bin/python

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


# import python system modules

import common
import tag
from volume import Volume
from snapshot import Snapshot
from common import SOSError
from project import Project
from cluster import Cluster
from hostinitiators import HostInitiator
from host import Host
from virtualarray import VirtualArray
import uuid
import json


class ExportGroup(object):

    '''
    The class definition for operations on 'Export group Service'.
    '''
    URI_EXPORT_GROUP = "/block/exports"
    URI_EXPORT_GROUPS_SHOW = URI_EXPORT_GROUP + "/{0}"
    URI_EXPORT_GROUP_LIST = '/projects/{0}/resources'
    URI_EXPORT_GROUP_SEARCH = '/block/exports/search'
    URI_EXPORT_GROUP_DEACTIVATE = URI_EXPORT_GROUPS_SHOW + '/deactivate'
    URI_EXPORT_GROUP_UPDATE = '/block/exports/{0}'
    URI_EXPORT_GROUP_TASKS_LIST = '/block/exports/{0}/tasks'
    URI_EXPORT_GROUP_TASK = URI_EXPORT_GROUP_TASKS_LIST + '/{1}'
    # 'Exclusive' is for backward compatibility only
    EXPORTGROUP_TYPE = ['Initiator', 'Host', 'Cluster', 'Exclusive']
    URI_EXPORT_GROUP_TAG = URI_EXPORT_GROUPS_SHOW + '/tags'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def exportgroup_list(self, project, tenant):
        '''
        This function will give us the list of export group uris
        separated by comma.
        prameters:
            project: Name of the project path.
        return
            returns with list of export group ids separated by comma.
        '''
        if(tenant is None):
            tenant = ""
        projobj = Project(self.__ipAddr, self.__port)
        fullproj = tenant + "/" + project
        projuri = projobj.project_query(fullproj)

        uri = self.URI_EXPORT_GROUP_SEARCH

        if ('?' in uri):
            uri += '&project=' + projuri
        else:
            uri += '?project=' + projuri

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             uri, None)
        o = common.json_decode(s)
        if not o:
            return []

        exportgroups = []
        resources = common.get_node_value(o, "resource")
        for resource in resources:
            exportgroups.append(resource["id"])

        return exportgroups

    def exportgroup_show(self, name, project, tenant, varray=None, xml=False):
        '''
        This function will take export group name and project name as input and
        It will display the Export group with details.
        parameters:
           name : Name of the export group.
           project: Name of the project.
        return
            returns with Details of export group.
        '''
        varrayuri = None
        if(varray):
            varrayObject = VirtualArray(self.__ipAddr, self.__port)
            varrayuri = varrayObject.varray_query(varray)
        uri = self.exportgroup_query(name, project, tenant, varrayuri)
        (s, h) = common.service_json_request(
            self.__ipAddr,
            self.__port,
            "GET",
            self.URI_EXPORT_GROUPS_SHOW.format(uri), None)
        o = common.json_decode(s)
        if(o['inactive']):
            return None

        if(not xml):
            return o

        (s, h) = common.service_json_request(
            self.__ipAddr,
            self.__port,
            "GET",
            self.URI_EXPORT_GROUPS_SHOW.format(uri),
            None, None, xml)

        return s

    def exportgroup_create(self, name, project, tenant, varray,
                           exportgrouptype, export_destination=None):
        '''
        This function will take export group name and project name  as input
        and it will create the Export group with given name.
        parameters:
           name : Name of the export group.
           project: Name of the project path.
           tenant: Container tenant name.
        return
            returns with status of creation.
        '''
        # check for existance of export group.
        try:
            status = self.exportgroup_show(name, project, tenant)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                if(tenant is None):
                    tenant = ""

                fullproj = tenant + "/" + project
                projObject = Project(self.__ipAddr, self.__port)
                projuri = projObject.project_query(fullproj)

                varrayObject = VirtualArray(self.__ipAddr, self.__port)
                nhuri = varrayObject.varray_query(varray)

                parms = {
                    'name': name,
                    'project': projuri,
                    'varray': nhuri,
                    'type': exportgrouptype
                }

                if(exportgrouptype and export_destination):
                    if (exportgrouptype == 'Cluster'):
                        cluster_obj = Cluster(self.__ipAddr, self.__port)
                        try:
                            cluster_uri = cluster_obj.cluster_query(
                                export_destination,
                                fullproj)
                        except SOSError as e:
                            raise e
                        parms['clusters'] = [cluster_uri]
                    elif (exportgrouptype == 'Host'):
                        host_obj = Host(self.__ipAddr, self.__port)
                        try:
                            host_uri = host_obj.query_by_name(
                                export_destination)
                        except SOSError as e:
                            raise e
                        parms['hosts'] = [host_uri]

                body = json.dumps(parms)
                (s, h) = common.service_json_request(self.__ipAddr,
                                                     self.__port, "POST",
                                                     self.URI_EXPORT_GROUP,
                                                     body)

                o = common.json_decode(s)
                return o
            else:
                raise e

        if(status):
            raise SOSError(
                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                "Export group with name " + name +
                " already exists")

    def exportgroup_delete(self, name, project, tenant, sync):
        '''
        This function will take export group name and project name as input and
        marks the particular export group as delete.
        parameters:
           name : Name of the export group.
           project: Name of the project.
        return
            return with status of the delete operation.
            false incase it fails to do delete.
        '''
        token = "cli_export_group_delete:" + str(uuid.uuid4())
        uri = self.exportgroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            self.URI_EXPORT_GROUP_DEACTIVATE.format(uri),
            None, token)

        output = common.json_decode(s)
        return self.check_for_sync(output, sync)

    def exportgroup_tag(self, name, project, tenant, add, remove):
        '''
        This function will tag export group name and project name as input and
        marks the particular export group for tagging.
        parameters:
           name : Name of the export group.
           project: Name of the project.
           tenant: Name of the tenant
           add:tags to be added
           remove: tags to be removed
        return
            return with result of the tag operation.
        '''
        uri = self.exportgroup_query(name, project, tenant)

        return (
            tag.tag_resource(self.__ipAddr, self.__port,
                             self.URI_EXPORT_GROUP_TAG, uri, add, remove)
        )

    def exportgroup_query(self, name, project, tenant, varrayuri=None):
        '''
        This function will take export group name/id and project name  as input
        and returns export group id.
        parameters:
           name : Name/id of the export group.
        return
            return with id of the export group.
         '''
        if (common.is_uri(name)):
            return name

        uris = self.exportgroup_list(project, tenant)
        for uri in uris:
            exportgroup = self.exportgroup_show(uri, project, tenant)
            if(exportgroup):
                if (exportgroup['name'] == name):
                    if(varrayuri):
                        varrayobj = exportgroup['varray']
                        if(varrayobj['id'] == varrayuri):
                            return exportgroup['id']
                        else:
                            continue
                    else:
                        return exportgroup['id']                            
        raise SOSError(
            SOSError.NOT_FOUND_ERR,
            "Export Group " + name + ": not found")

        '''
        function to validate input volumes/snapshots
        and return list of ids and luns
        input
            list of volumes/snapshots in the format name:lun
        '''

    def _get_resource_lun_tuple(self, resources, resType, baseResUri,
                                tenantname, projectname, blockTypeName):
        copyEntries = []
        snapshotObject = Snapshot(self.__ipAddr, self.__port)
        volumeObject = Volume(self.__ipAddr, self.__port)
        for copy in resources:
            copyParam = []
            try:
                copyParam = copy.split(":")
            except Exception as e:
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "Please provide valid format volume: lun for parameter " +
                    resType)
            copy = dict()
            if(not len(copyParam)):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    "Please provide atleast volume for parameter " + resType)
            if(resType == "volumes"):
                fullvolname = tenantname + "/" + projectname + "/"
                fullvolname += copyParam[0]
                copy['id'] = volumeObject.volume_query(fullvolname)
            if(resType == "snapshots"):
                copy['id'] = snapshotObject.snapshot_query(
                    'block', blockTypeName, baseResUri, copyParam[0])
            if(len(copyParam) > 1):
                copy['lun'] = copyParam[1]
            copyEntries.append(copy)
        return copyEntries

        '''
        add volume to export group
        parameters:
           exportgroupname : Name/id of the export group.
           tenantname      : tenant name
           projectname     : name of project
           volumename      : name of volume that needs
                             to be added to exportgroup
           lunid           : lun id
        return
            return action result
         '''

    def exportgroup_add_volumes(self, sync, exportgroupname, tenantname,
                                maxpaths, minpaths, pathsperinitiator,
                                projectname, volumenames, snapshots=None,
                                cg=None ):

        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)

        # get volume uri
        if(tenantname is None):
            tenantname = ""
        # List of volumes.
        # incase of snapshots from volume, this will hold the source volume
        # URI.
        volume_snapshots = []
        if(volumenames):
            volume_snapshots = self._get_resource_lun_tuple(
                volumenames, "volumes", None, tenantname,
                projectname, None)

        # if snapshot given then snapshot added to exportgroup
        if(snapshots and len(snapshots) > 0):
            resuri = None
            if(cg):
                blockTypeName = 'consistency-groups'
                from consistencygroup import ConsistencyGroup
                cgObject = ConsistencyGroup(self.__ipAddr, self.__port)
                resuri = cgObject.consistencygroup_query(cg, projectname,
                                                         tenantname)
            else:
                blockTypeName = 'volumes'
                if(len(volume_snapshots) > 0):
                    resuri = volume_snapshots[0]['id']

            volume_snapshots = self._get_resource_lun_tuple(
                snapshots, "snapshots", resuri, tenantname,
                projectname, blockTypeName)

        parms = {}
        # construct the body

        volChanges = {}
        volChanges['add'] = volume_snapshots
        if (maxpaths):
            volChanges['max_paths'] = maxpaths
        if (minpaths):
            volChanges['min_paths'] = minpaths
        if(pathsperinitiator is not None):
            volChanges['paths_per_initiator'] = pathsperinitiator
        parms['volume_changes'] = volChanges
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    '''
    Remove volume from the exportgroup, given the name of the volume
    '''

    def exportgroup_remove_volumes(self, sync, exportgroupname, tenantname,
                                   projectname, volumenames, snapshots=None,
                                   cg=None):

        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)

        volumeIdList = []
        # get volume uri
        if(tenantname is None):
            tenantname = ""
        volumeObject = Volume(self.__ipAddr, self.__port)
        for vol in volumenames:
            fullvolname = tenantname + "/" + projectname + "/" + vol
            volumeIdList.append(volumeObject.volume_query(fullvolname))

        return (
            self.exportgroup_remove_volumes_by_uri(
                exportgroup_uri, volumeIdList, sync, tenantname,
                projectname, snapshots, cg)
        )

    '''
    Remove volumes from the exportgroup, given the uris of volume.
    '''

    def exportgroup_remove_volumes_by_uri(self, exportgroup_uri, volumeIdList,
                                          sync=False, tenantname=None,
                                          projectname=None, snapshots=None,
                                          cg=None):
        # if snapshot given then snapshot added to exportgroup
        volume_snapshots = volumeIdList
        if(snapshots):
            resuri = None
            if(cg):
                blockTypeName = 'consistency-groups'
                from consistencygroup import ConsistencyGroup
                cgObject = ConsistencyGroup(self.__ipAddr, self.__port)
                resuri = cgObject.consistencygroup_query(cg, projectname,
                                                         tenantname)
            else:
                blockTypeName = 'volumes'
                if(len(volumeIdList) > 0):
                    resuri = volumeIdList[0]
            volume_snapshots = []
            snapshotObject = Snapshot(self.__ipAddr, self.__port)
            for snapshot in snapshots:
                volume_snapshots.append(
                    snapshotObject.snapshot_query(
                        'block', blockTypeName, resuri, snapshot))

        parms = {}

        parms['volume_changes'] = self._remove_list(volume_snapshots)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    # initator
        '''
        add initiator to export group
        parameters:
           exportgroupname     : Name/id of the export group.
           tenantname          : tenant name
           projectname         : name of project
           initator            : name of initiator
           hostlabel           : name of host or host label
        return
            return action result
         '''

    def exportgroup_add_initiator(self, exportgroupname, tenantname,
                                  projectname, initators, hostlabel, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)
        initiator_uris = []
        hiObject = HostInitiator(self.__ipAddr, self.__port)
        for initator in initators:
            initiator_uris.append(hiObject.query_by_portwwn(initator,
                                                            hostlabel))
        parms = {}
        # initiator_changes
        parms['initiator_changes'] = self._add_list(initiator_uris)

        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    def exportgroup_remove_initiator(self, exportgroupname, tenantname,
                                     projectname, initators, hostlabel, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname, projectname,
                                                 tenantname)
        initiator_uris = []
        hiObject = HostInitiator(self.__ipAddr, self.__port)
        for initator in initators:
            initiator_uris.append(hiObject.query_by_portwwn(initator,
                                                            hostlabel))
        parms = {}
        # initiator_changes
        parms['initiator_changes'] = self._remove_list(initiator_uris)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    # cluster
        '''
        add cluster to export group
        parameters:
           exportgroupname : Name/id of the export group.
           tenantname      : tenant name
           projectname     : name of project
           clustername     : name of cluster
        return
            return action result
         '''

    def exportgroup_add_cluster(self, exportgroupname, tenantname, projectname,
                                clusternames, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)
        cluster_uris = []
        clusterObject = Cluster(self.__ipAddr, self.__port)
        for clustername in clusternames:
            cluster_uris.append(clusterObject.cluster_query(clustername,
                                                            tenantname))
        parms = {}
        parms['cluster_changes'] = self._add_list(cluster_uris)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    def exportgroup_remove_cluster(self, exportgroupname, tenantname,
                                   projectname, clusternames, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)
        # cluster search API does not take project parameter.
        cluster_uris = []
        clusterObject = Cluster(self.__ipAddr, self.__port)
        for clustername in clusternames:
            cluster_uris.append(clusterObject.cluster_query(clustername,
                                                            tenantname))
        parms = {}
        parms['cluster_changes'] = self._remove_list(cluster_uris)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    # host
        '''
        add host to export group
        parameters:
           exportgroupname     : Name/id of the export group.
           tenantname          : tenant name
           projectname         : name of project
           hostlabel           : name of host
        return
            return action result
         '''

    def exportgroup_add_host(self, exportgroupname, tenantname,
                             projectname, hostlabels, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)
        host_uris = []
        hostObject = Host(self.__ipAddr, self.__port)
        for hostlabel in hostlabels:
            host_uris.append(hostObject.query_by_name(hostlabel, tenantname))

        parms = {}
        parms['host_changes'] = self._add_list(host_uris)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    def exportgroup_remove_host(self, exportgroupname, tenantname,
                                projectname, hostlabels, sync):
        exportgroup_uri = self.exportgroup_query(exportgroupname,
                                                 projectname, tenantname)
        host_uris = []
        hostObject = Host(self.__ipAddr, self.__port)
        for hostlabel in hostlabels:
            host_uris.append(hostObject.query_by_name(hostlabel, tenantname))
        parms = {}
        parms['host_changes'] = self._remove_list(host_uris)
        o = self.send_json_request(exportgroup_uri, parms)
        return self.check_for_sync(o, sync)

    # helper function
    def _add_list(self, uris):
        resChanges = {}
        if(not isinstance(uris, list)):
            resChanges['add'] = [uris]
        else:
            resChanges['add'] = uris
        return resChanges

    def _remove_list(self, uris):
        resChanges = {}
        if(not isinstance(uris, list)):
            resChanges['remove'] = [uris]
        else:
            resChanges['remove'] = uris
        return resChanges

    def send_json_request(self, exportgroup_uri, param):
        body = json.dumps(param)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            self.URI_EXPORT_GROUP_UPDATE.format(exportgroup_uri), body)
        return common.json_decode(s)

    def check_for_sync(self, result, sync):
        if(sync):
            if(len(result["resource"]) > 0):
                resource = result["resource"]
                return (
                    common.block_until_complete("export", resource["id"],
                                                result["id"], self.__ipAddr,
                                                self.__port)
                )
            else:
                raise SOSError(
                    SOSError.SOS_FAILURE_ERR,
                    "error: task list is empty, no task response found")
        else:
            return result

    '''
    show all tasks by export_group uri and
    '''

    def show_task_by_uri(self, exportgroup_uri, task_id=None):

        if(not task_id):
            return (
                common.get_tasks_by_resourceuri(
                    "export", exportgroup_uri, self.__ipAddr, self.__port)
            )
        else:
            return (
                common.get_task_by_resourceuri_and_taskId(
                    "export", exportgroup_uri, task_id,
                    self.__ipAddr, self.__port)
            )

    def list_tasks(self, project_name, export_group_name=None, task_id=None):
        return (
            common.list_tasks(self.__ipAddr, self.__port, "export",
                              project_name, export_group_name, task_id)
        )


# Export Group Create routines

def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Export Group Create cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create an Export group')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='container project name',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                metavar='<varray>',
                                dest='varray',
                                help='varray for export',
                                required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    create_parser.add_argument(
        '-type', '-t',
        help="Type of the ExportGroup: " +
        "Initiator|Host|Cluster(default:Initiator)",
        default='Initiator',
        dest='type',
        metavar='<exportgrouptype>',
        choices=ExportGroup.EXPORTGROUP_TYPE)
    create_parser.add_argument('-exportdestination', '-ed',
                               metavar='<exportdestination>',
                               dest='export_destination',
                               help='name of host or cluster')

    create_parser.set_defaults(func=exportgroup_create)


def exportgroup_create(args):
    try:
        obj = ExportGroup(args.ip, args.port)
        obj.exportgroup_create(args.name, args.project, args.tenant,
                               args.varray, args.type, args.export_destination)
    except SOSError as e:
        raise common.format_err_msg_and_raise("create", "exportgroup",
                                              e.err_text, e.err_code)

# Export group Delete routines


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Export group Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an Export group')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')

    delete_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    delete_parser.set_defaults(func=exportgroup_delete)


def exportgroup_delete(args):
    obj = ExportGroup(args.ip, args.port)
    try:
        obj.exportgroup_delete(args.name, args.project, args.tenant, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("delete", "exportgroup",
                                              e.err_text, e.err_code)

# Export group Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Export group Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show full details of an Export group')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')
    show_parser.add_argument('-varray', '-va',
                             metavar='<varray>',
                             dest='varray',
                             help='varray name')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=exportgroup_show)


def exportgroup_show(args):

    obj = ExportGroup(args.ip, args.port)
    try:
        res = obj.exportgroup_show(args.name, args.project,
                                   args.tenant, args.varray, args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        raise common.format_err_msg_and_raise("show", "exportgroup",
                                              e.err_text, e.err_code)

# Export Group List routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Export group List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List all Export groups')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    list_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')

    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='Export group list with details',
                             action='store_true')
    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List Export groups with details in table format',
        dest='long')
    list_parser.set_defaults(func=exportgroup_list)


def exportgroup_list(args):
    obj = ExportGroup(args.ip, args.port)
    try:
        uris = obj.exportgroup_list(args.project, args.tenant)
        output = []
        if(len(uris) > 0):
            for uri in uris:
                eg = obj.exportgroup_show(uri, args.project, args.tenant)
                # The following code is to get volume/snapshot name part of
                # export group list.
                if(eg):
                    if("project" in eg and "name" in eg["project"]):
                        del eg["project"]["name"]
                    volumeuris = common.get_node_value(eg, "volumes")
                    volobj = Volume(args.ip, args.port)
                    snapobj = Snapshot(args.ip, args.port)
                    volnames = []
                    strvol = ""
                    for volumeuri in volumeuris:
                        strvol = str(volumeuri['id'])
                        if(strvol.find('urn:storageos:Volume') >= 0):
                            vol = volobj.show_by_uri(strvol)
                            if(vol):
                                volnames.append(vol['name'])
                        elif(strvol.find('urn:storageos:BlockSnapshot') >= 0):
                            snapshot = snapobj.snapshot_show_uri('block', None,
                                                                 strvol)
                            if(snapshot):
                                volnames.append(snapshot['name'])
                    eg['volumes_snapshots'] = volnames
                    output.append(eg)

            if(args.verbose):
                return common.format_json_object(output)
            if(len(output) > 0):
                if(args.long):
                    from common import TableGenerator
                    TableGenerator(
                        output,
                        ['module/name',
                         'volumes_snapshots',
                         'type']).printTable()

                else:
                    from common import TableGenerator
                    TableGenerator(output, ['module/name']).printTable()

    except SOSError as e:
        raise common.format_err_msg_and_raise("list", "exportgroup",
                                              e.err_text, e.err_code)


# Export Group Add Volume routines

def add_volume_parser(subcommand_parsers, common_parser):
    # add command parser
    add_volume_parser = subcommand_parsers.add_parser(
        'add_vol',
        description='ViPR Export group Add volumes cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add a volume to an Export group')
    mandatory_args = add_volume_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-volume', '-v',
                                metavar='<Volume>', dest='volume', nargs='+',
                                help="List of volume lunId pair " +
                                     "in the format <volume_name>:<lun_id>",
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project ',
                                required=True)
    add_volume_parser.add_argument('-tenant', '-tn',
                                   metavar='<tenantname>',
                                   dest='tenant',
                                   help='container tenant name')
    add_volume_parser.add_argument('-snapshot', '-sh',
                                   metavar='<Snapshot for volume>',
                                   dest='snapshot', nargs='+',
                                   help="List of snapshot lunId pair in the " +
                                        "format <snapshot_name>:<lun_id>",
                                   default=None)
    add_volume_parser.add_argument('-consistencygroup', '-cg',
                                   metavar='<consistencygroup>',
                                   dest='consistencygroup',
                                   help='name of consistencygroup',
                                   default=None)
    add_volume_parser.add_argument(
        '-maxpaths', '-mxp',
        help='The maximum number of paths that can be ' +
        'used between a host and a storage volume',
        metavar='<MaxPaths>',
        dest='maxpaths',
        type=int)
    add_volume_parser.add_argument(
        '-minpaths', '-mnp',
        help='The minimum  number of paths that can be used ' +
        'between a host and a storage volume',
        metavar='<MinPaths>',
        dest='minpaths',
        type=int)
    add_volume_parser.add_argument('-pathsperinitiator', '-ppi',
                               help='The number of paths per initiator',
                               metavar='<PathsPerInitiator>',
                               dest='pathsperinitiator',
                               type=int)
    

    add_volume_parser.add_argument('-synchronous', '-sync',
                                   dest='sync',
                                   help='Execute in synchronous mode',
                                   action='store_true')

    add_volume_parser.set_defaults(func=exportgroup_add_volumes)


def exportgroup_add_volumes(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_add_volumes(
            args.sync, args.name, args.tenant,
            args.maxpaths,
            args.minpaths, args.pathsperinitiator,
            args.project, args.volume, args.snapshot, args.consistencygroup)
    except SOSError as e:
        raise common.format_err_msg_and_raise("add_vol", "exportgroup",
                                              e.err_text, e.err_code)

# Export Group Remove Volume routines


def remove_volume_parser(subcommand_parsers, common_parser):
    # remove command parser
    remove_volume_parser = subcommand_parsers.add_parser(
        'remove_vol',
        description="ViPR Export group Add volumes cli usage.",
        parents=[common_parser], conflict_handler='resolve',
        help='Remove a volume from Export group')
    mandatory_args = remove_volume_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group ',
                                required=True)
    mandatory_args.add_argument('-volume', '-v',
                                metavar='<Volume>',
                                dest='volume',
                                nargs='+',
                                help="List of volume names or Name of " +
                                      "source volume for snapshots",
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    remove_volume_parser.add_argument(
        '-snapshot', '-sh',
        metavar='<Snapshot for volume>',
        dest='snapshot',
        nargs='+',
        help='names of snapshots of a volume to unexport',
        default=None)
    remove_volume_parser.add_argument('-consistencygroup', '-cg',
                                      metavar='<consistencygroup>',
                                      dest='consistencygroup',
                                      help='name of consistencygroup',
                                      default=None)

    remove_volume_parser.add_argument('-tenant', '-tn',
                                      metavar='<tenantname>',
                                      dest='tenant',
                                      help='container tenant name')

    remove_volume_parser.add_argument('-synchronous', '-sync',
                                      dest='sync',
                                      help='Execute in synchronous mode',
                                      action='store_true')

    remove_volume_parser.set_defaults(func=exportgroup_remove_volumes)


def exportgroup_remove_volumes(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)

        objExGroup.exportgroup_remove_volumes(
            args.sync, args.name, args.tenant, args.project,
            args.volume, args.snapshot, args.consistencygroup)

    except SOSError as e:
        raise common.format_err_msg_and_raise("remove_vol", "exportgroup",
                                              e.err_text, e.err_code)


# Export Group Add Initiator routines

def add_initiator_parser(subcommand_parsers, common_parser):
    # add initiator command parser
    add_initiator_parser = subcommand_parsers.add_parser(
        'add_initiator',
        description="ViPR Export group  Add volumes cli usage.",
        parents=[common_parser], conflict_handler='resolve',
        help='Add an initiator to  Export group')
    mandatory_args = add_initiator_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-initiator', '-in',
                                metavar='<initiator>',
                                dest='initiator',
                                help='names of list of initiators',
                                nargs='+',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel', metavar='<hostlabel>',
        help='Host for which initiators to be searched',
        required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    add_initiator_parser.add_argument('-tenant', '-tn',
                                      metavar='<tenantname>',
                                      dest='tenant',
                                      help='container tenant name')

    add_initiator_parser.add_argument('-synchronous', '-sync',
                                      dest='sync',
                                      help='Execute in synchronous mode',
                                      action='store_true')

    add_initiator_parser.set_defaults(func=exportgroup_add_initiators)


def exportgroup_add_initiators(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_add_initiator(args.name, args.tenant,
                                             args.project, args.initiator,
                                             args.hostlabel, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("add_initiator", "exportgroup",
                                              e.err_text, e.err_code)

# Export Group Remove Initiators routines


def remove_initiator_parser(subcommand_parsers, common_parser):
    # create command parser
    remove_initiator_parser = subcommand_parsers.add_parser(
        'remove_initiator',
        description="ViPR Export group " +
        "Remove initiator port cli usage.",
        parents=[common_parser],
        conflict_handler='resolve',
        help="Remove an initiator port from Export group")
    mandatory_args = remove_initiator_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-initiator', '-in',
                                metavar='<initiator>',
                                dest='initiator',
                                nargs='+',
                                help='names of list of initiators',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel', metavar='<hostlabel>',
        help='Host for which initiators to be searched',
        required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project ',
                                required=True)
    remove_initiator_parser.add_argument('-tenant', '-tn',
                                         metavar='<tenantname>',
                                         dest='tenant',
                                         help='container tenant name')

    remove_initiator_parser.add_argument('-synchronous', '-sync',
                                         dest='sync',
                                         help='Execute in synchronous mode',
                                         action='store_true')

    remove_initiator_parser.set_defaults(func=exportgroup_remove_initiators)


def exportgroup_remove_initiators(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_remove_initiator(
            args.name, args.tenant, args.project, args.initiator,
            args.hostlabel, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise(
            "remove_initiator", "exportgroup", e.err_text, e.err_code)

# Export Group Add Volume routines


def add_cluster_parser(subcommand_parsers, common_parser):
    # add command parser
    add_cluster_parser = subcommand_parsers.add_parser(
        'add_cluster',
        description="ViPR Export group Add Cluster cli usage.",
        parents=[common_parser], conflict_handler='resolve',
        help='Add a Cluster to an Export group')

    mandatory_args = add_cluster_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-cluster', '-cl',
                                help='Name of the cluster',
                                dest='cluster',
                                nargs='+',
                                metavar='<cluster>',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project ',
                                required=True)
    add_cluster_parser.add_argument('-tenant', '-tn',
                                    metavar='<tenantname>',
                                    dest='tenant',
                                    help='container tenant name')

    add_cluster_parser.add_argument('-synchronous', '-sync',
                                    dest='sync',
                                    help='Execute in synchronous mode',
                                    action='store_true')

    add_cluster_parser.set_defaults(func=exportgroup_add_cluster)


def exportgroup_add_cluster(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_add_cluster(
            args.name, args.tenant, args.project, args.cluster, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("add_cluster", "exportgroup",
                                              e.err_text, e.err_code)


# Export Group Remove Volume routines

def remove_cluster_parser(subcommand_parsers, common_parser):
    # remove command parser
    remove_cluster_parser = subcommand_parsers.add_parser(
        'remove_cluster',
        description="ViPR Export group Remove cluster cli usage.",
        parents=[common_parser], conflict_handler='resolve',
        help='Remove a cluster from Export group')
    mandatory_args = remove_cluster_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument('-cluster', '-cl',
                                help='Name of the cluster',
                                dest='cluster',
                                nargs='+',
                                metavar='<cluster>',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project ',
                                required=True)
    remove_cluster_parser.add_argument('-tenant', '-tn',
                                       metavar='<tenantname>',
                                       dest='tenant',
                                       help='container tenant name')

    remove_cluster_parser.add_argument('-synchronous', '-sync',
                                       dest='sync',
                                       help='Execute in synchronous mode',
                                       action='store_true')

    remove_cluster_parser.set_defaults(func=exportgroup_remove_cluster)


def exportgroup_remove_cluster(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_remove_cluster(
            args.name, args.tenant, args.project, args.cluster, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("remove_cluster", "exportgroup",
                                              e.err_text, e.err_code)


def add_host_parser(subcommand_parsers, common_parser):
    # add command parser
    add_host_parser = subcommand_parsers.add_parser(
        'add_host',
        description="ViPR Export group Add Host cli usage.",
        parents=[common_parser], conflict_handler='resolve',
        help='Add a Host to an Export group')
    mandatory_args = add_host_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group',
                                required=True)
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        nargs='+',
        metavar='<hostlabel>',
        help='Host for which initiators to be searched',
        required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project ',
                                required=True)
    add_host_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='container tenant name')

    add_host_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Execute in synchronous mode',
                                 action='store_true')

    add_host_parser.set_defaults(func=exportgroup_add_host)


def exportgroup_add_host(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_add_host(
            args.name, args.tenant, args.project, args.hostlabel, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("add_host", "exportgroup",
                                              e.err_text, e.err_code)

# Export Group Remove Volume routines


def remove_host_parser(subcommand_parsers, common_parser):
    # remove command parser
    remove_host_parser = subcommand_parsers.add_parser(
        'remove_host',
        description="ViPR Export group Remove Host cli usage.",
        parents=[common_parser],
        conflict_handler='resolve',
        help='Remove a Host from Export group')
    mandatory_args = remove_host_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group ',
                                required=True)
    mandatory_args.add_argument('-hl', '-hostlabel',
                                dest='hostlabel',
                                nargs='+',
                                metavar='<hostlabel>',
                                help='Name of Host',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    remove_host_parser.add_argument('-tenant', '-tn',
                                    metavar='<tenantname>',
                                    dest='tenant',
                                    help='container tenant name')

    remove_host_parser.add_argument('-synchronous', '-sync',
                                    dest='sync',
                                    help='Execute in synchronous mode',
                                    action='store_true')

    remove_host_parser.set_defaults(func=exportgroup_remove_host)


def exportgroup_remove_host(args):
    try:
        objExGroup = ExportGroup(args.ip, args.port)
        objExGroup.exportgroup_remove_host(
            args.name, args.tenant, args.project, args.hostlabel, args.sync)
    except SOSError as e:
        raise common.format_err_msg_and_raise("remove_host", "exportgroup",
                                              e.err_text, e.err_code)


def task_parser(subcommand_parsers, common_parser):
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR ExportGroup List tasks CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of export group tasks')
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
                             metavar='<exportgroupname>',
                             help='Name of export group')
    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')
    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')

    task_parser.set_defaults(func=exportgroup_tasks_list)


def exportgroup_tasks_list(args):
    obj = ExportGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(args.id):
            res = obj.list_tasks(args.tenant + "/" + args.project,
                                 args.name, args.id)
            if(res):
                return common.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant + "/" + args.project, args.name)
            if(res and len(res) > 0):
                if(args.verbose):
                    return common.format_json_object(res)
                else:
                    from common import TableGenerator
                    TableGenerator(res, ["module/id", "name",
                                         "state"]).printTable()
        else:
            res = obj.list_tasks(args.tenant + "/" + args.project)
            if(res and len(res) > 0):
                if(not args.verbose):
                    from common import TableGenerator
                    TableGenerator(res, ["module/id", "name",
                                         "state"]).printTable()
                else:
                    return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise("get tasks list", "export",
                                        e.err_text, e.err_code)


def tag_parser(subcommand_parsers, common_parser):
    # tag command parser
    tag_parser = subcommand_parsers.add_parser(
        'tag',
        description='ViPR Export group Tag CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Tag an Export group')
    mandatory_args = tag_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<exportgroupname>',
                                dest='name',
                                help='name of Export Group ',
                                required=True)
    tag_parser.add_argument('-tenant', '-tn',
                            metavar='<tenantname>',
                            dest='tenant',
                            help='container tenant name')

    tag.add_mandatory_project_parameter(mandatory_args)

    tag.add_tag_parameters(tag_parser)

    tag_parser.set_defaults(func=exportgroup_tag)


def exportgroup_tag(args):
    obj = ExportGroup(args.ip, args.port)
    try:
        if(args.add is None and args.remove is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "viprcli exportgroup tag: error: at least one of " +
                           "the arguments -add -remove is required")

        obj.exportgroup_tag(args.name, args.project,
                            args.tenant, args.add, args.remove)
    except SOSError as e:
        raise common.format_err_msg_and_raise("tag", "exportgroup",
                                              e.err_text, e.err_code)


#
# ExportGroup Main parser routine
#
def exportgroup_parser(parent_subparser, common_parser):

    # main export group parser
    parser = parent_subparser.add_parser(
        'exportgroup',
        description='ViPR Export Group cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Export group')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # add volume to host command parser
    add_volume_parser(subcommand_parsers, common_parser)

    # remove volume from host command parser
    remove_volume_parser(subcommand_parsers, common_parser)

    # add initiator to host command parser
    add_initiator_parser(subcommand_parsers, common_parser)

    # remove initiator command parser
    remove_initiator_parser(subcommand_parsers, common_parser)

    # add cluster   command parser
    add_cluster_parser(subcommand_parsers, common_parser)

    # remove cluster command parser
    remove_cluster_parser(subcommand_parsers, common_parser)

    # add cluster   command parser
    add_host_parser(subcommand_parsers, common_parser)

    # remove cluster command parser
    remove_host_parser(subcommand_parsers, common_parser)

    task_parser(subcommand_parsers, common_parser)

    tag_parser(subcommand_parsers, common_parser)
