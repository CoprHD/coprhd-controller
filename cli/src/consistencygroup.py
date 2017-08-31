#!/usr/bin/python

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.


#import python system modules

import common
import argparse
import sys
import os
import volume
#from volume import Volume
#from snapshot import Snapshot
from common import SOSError
from project import Project
import uuid
import json
from common import TableGenerator
from virtualarray import VirtualArray


class ConsistencyGroup(object):
    '''
    The class definition for operations on 'Consistency group Service'.
    '''
    URI_CONSISTENCY_GROUP = "/block/consistency-groups"
    URI_CONSISTENCY_GROUPS_INSTANCE = URI_CONSISTENCY_GROUP + "/{0}"
    URI_CONSISTENCY_GROUPS_DEACTIVATE = URI_CONSISTENCY_GROUPS_INSTANCE + \
        "/deactivate"
    URI_CONSISTENCY_GROUPS_SNAPSHOT = URI_CONSISTENCY_GROUP + \
        "/{0}/protection/snapshots"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE = URI_CONSISTENCY_GROUP + \
        "/{0}/protection/snapshots/{1}"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_ACTIVATE = \
        URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/activate"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE = \
        URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/deactivate"
    URI_CONSISTENCY_GROUPS_SNAPSHOT_RESTORE = \
        URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE + "/restore"
    URI_CONSISTENCY_GROUPS_SEARCH = \
        '/block/consistency-groups/search?project={0}'
    URI_SEARCH_CONSISTENCY_GROUPS_BY_TAG = \
        '/block/consistency-groups/search?tag={0}'
    URI_CONSISTENCY_GROUP_TAGS = \
        '/block/consistency-groups/{0}/tags'
        
    URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE = \
        URI_CONSISTENCY_GROUPS_INSTANCE + "/protection/continuous-copies"
    URI_BLOCK_CONSISTENCY_GROUP_SWAP = \
        URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/swap"
    URI_BLOCK_CONSISTENCY_GROUP_FAILOVER = \
        URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/failover"
    URI_BLOCK_CONSISTENCY_GROUP_FAILOVER_CANCEL = \
        URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/failover-cancel"
    URI_BLOCK_CONSISTENCY_GROUP_ACCESS_MODE = \
        URI_BLOCK_CONSISTENCY_GROUP_PROTECTION_BASE + "/accessmode"

    '''
    Migration operations
    '''
    URI_CONSISTENCY_GROUPS_MIGRATION_GET = URI_CONSISTENCY_GROUPS_INSTANCE + "/migrations"
    URI_CONSISTENCY_GROUPS_MIGRATION = URI_CONSISTENCY_GROUPS_INSTANCE + "/migration"
    URI_CONSISTENCY_GROUPS_MIGRATION_CREATE = URI_CONSISTENCY_GROUPS_MIGRATION + "/create"
    URI_CONSISTENCY_GROUPS_MIGRATION_CUTOVER = URI_CONSISTENCY_GROUPS_MIGRATION + "/cutover"
    URI_CONSISTENCY_GROUPS_MIGRATION_COMMIT = URI_CONSISTENCY_GROUPS_MIGRATION + "/commit"
    URI_CONSISTENCY_GROUPS_MIGRATION_CANCEL = URI_CONSISTENCY_GROUPS_MIGRATION + "/cancel"
    URI_CONSISTENCY_GROUPS_MIGRATION_REFRESH = URI_CONSISTENCY_GROUPS_MIGRATION + "/refresh"
    URI_CONSISTENCY_GROUPS_MIGRATION_RECOVER = URI_CONSISTENCY_GROUPS_MIGRATION + "/recover"
    URI_CONSISTENCY_GROUPS_MIGRATION_SYNC_START = URI_CONSISTENCY_GROUPS_MIGRATION + "/sync-start"
    URI_CONSISTENCY_GROUPS_MIGRATION_SYNC_STOP = URI_CONSISTENCY_GROUPS_MIGRATION + "/sync-stop"
    URI_CONSISTENCY_GROUPS_MIGRATION_CREATE_ZONES = URI_CONSISTENCY_GROUPS_MIGRATION + "/create-zones"
    URI_CONSISTENCY_GROUPS_MIGRATION_RESCAN_HOSTS = URI_CONSISTENCY_GROUPS_MIGRATION + "/rescan-hosts"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def list(self, project, tenant):
        '''
        This function will give us the list of consistency group uris
        separated by comma.
        prameters:
            project: Name of the project path.
        return
            returns with list of consistency group ids separated by comma.
        '''
        if(tenant is None):
            tenant = ""
        projobj = Project(self.__ipAddr, self.__port)
        fullproj = tenant + "/" + project
        projuri = projobj.project_query(fullproj)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_SEARCH.format(projuri), None)
        o = common.json_decode(s)
        if not o:
            return []

        congroups = []
        resources = common.get_node_value(o, "resource")
        for resource in resources:
            congroups.append(resource["id"])

        return congroups

    def show(self, name, project, tenant, xml=False):
        '''
        This function will take consistency group name and project name
        as input and It will display the consistency group with details.
        parameters:
           name : Name of the consistency group.
           project: Name of the project.
        return
            returns with Details of consistency group.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_INSTANCE.format(uri), None)
        o = common.json_decode(s)
        if(o['inactive']):
            return None

        if(xml is False):
            return o

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_INSTANCE.format(uri), None, None, xml)

        if not s:
            return None
        return s

    def create(self, name, project, tenant, arrayconsistency):
        '''
        This function will take consistency group name and project name
        as input and It will create the consistency group with given name.
        parameters:
           name : Name of the consistency group.
           project: Name of the project path.
           tenant: Container tenant name.
        return
            returns with status of creation.
        '''
        # check for existance of consistency group.
        try:
            status = self.show(name, project, tenant)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                if(tenant is None):
                    tenant = ""
                fullproj = tenant + "/" + project
                projobj = Project(self.__ipAddr, self.__port)
                projuri = projobj.project_query(fullproj)

                parms = {'name': name, 'project': projuri, 'array_consistency': arrayconsistency.lower()}
                body = json.dumps(parms)

                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    self.URI_CONSISTENCY_GROUP, body, None, None)

                o = common.json_decode(s)
                return o
            else:
                raise e
        if(status):
            common.format_err_msg_and_raise(
                "create", "consistency group",
                "consistency groupwith name: " + name + " already exists",
                SOSError.ENTRY_ALREADY_EXISTS_ERR)

    def update(self, name, project, tenant, add_volumes, remove_volumes, sync,synctimeout=0):
        '''
        This function is used to add or remove volumes from consistency group
        It will update the consistency  group with given volumes.
        parameters:
           name : Name of the consistency group.
           project: Name of the project path.
           tenant: Container tenant name.
           add_volumes : volumes to be added to the consistency group
           remove_volumes: volumes to be removed from CG.
        return
            returns with status of creation.
        '''
        if(tenant is None):
            tenant = ""
        fullproj = tenant + "/" + project
        projobj = Project(self.__ipAddr, self.__port)
        projuri = projobj.project_query(fullproj)

        parms = []
        add_voluris = []
        remove_voluris = []
        #volumes = None
        from volume import Volume
        volobj = Volume(self.__ipAddr, self.__port)
        if(add_volumes):
            for volname in add_volumes:
                fullvolname = tenant + "/" + project + "/" + volname
                add_voluris.append(volobj.volume_query(fullvolname))
            volumes = {'volume': add_voluris}
            parms = {'add_volumes': volumes}

        if(remove_volumes):
            for volname in remove_volumes:
                fullvolname = tenant + "/" + project + "/" + volname
                remove_voluris.append(volobj.volume_query(fullvolname))
            volumes = {'volume': remove_voluris}
            parms = {'remove_volumes': volumes}

        body = json.dumps(parms)
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            self.URI_CONSISTENCY_GROUPS_INSTANCE.format(uri),
            body, None, None)

        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync,synctimeout)
        else:
            return o

    def delete(self, name, project, tenant, vipronly=False):
        '''
        This function will take consistency group name and project name
        as input and marks the particular consistency group as delete.
        parameters:
           name : Name of the consistency group.
           project: Name of the project.
        return
            return with status of the delete operation.
            false incase it fails to do delete.
        '''
	params = ''
        if (vipronly == True):
            params += "?type=" + 'VIPR_ONLY'
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            self.URI_CONSISTENCY_GROUPS_DEACTIVATE.format(uri) + params,
            None, None)
        return

    def consistencygroup_query(self, name, project, tenant):
        '''
        This function will take consistency group name/id and project name
        as input and returns consistency group id.
        parameters:
           name : Name/id of the consistency group.
        return
            return with id of the consistency group.
         '''
        if (common.is_uri(name)):
            return name

        uris = self.list(project, tenant)
        for uri in uris:
            congroup = self.show(uri, project, tenant)
            if(congroup):
                if (congroup['name'] == name):
                    return congroup['id']
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Consistency Group " + name + ": not found")

    # Blocks the opertaion until the task is complete/error out/timeout
    def check_for_sync(self, result, sync,synctimeout=0):
        if(len(result["resource"]) > 0):
            resource = result["resource"]
            return (
                common.block_until_complete("consistencygroup", resource["id"],
                                            result["id"], self.__ipAddr,
                                            self.__port,synctimeout)
            )
        else:
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                "error: task list is empty, no task response found")

    def snapshot_create(self, name, project, tenant, snapshotname,
                        createinactive):
        '''
        This function will create snapshot for volumes in consistency group.
        return the status of the command.
        parameters:
             name : name of the consistency group
             project: name of the project name
             snapshot : name of the snapshot to be created.
             createinactive: create the snapshot in instactive state.
        return
             status of the command.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        parms = {'name': snapshotname, 'create_inactive': createinactive}

        body = json.dumps(parms)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT.format(uri), body, None)
        o = common.json_decode(s)
        return o

    def snapshot_list(self, name, project, tenant):
        '''
        This function will list all the snapshot in the given
        consistency group.
        return the status of the command.
        parameters:
             name : name of the consistency group
             project: name of the project name
        return
             list of snapshots under CG.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT.format(uri), None, None)

        o = common.json_decode(s)
        if('snapshot' in o):
            return o['snapshot']
        else:
            return []

    def snapshot_show(self, name, project, tenant, snapshotname):
        '''
        This function will show the details of given snapshot under
        a consistency group.
        return the details of snapshot.
        parameters:
             name : name of the consistency group
             project: name of the project name
             snapshot : name of the snapshot to be created.
        return
             details of snapshots under CG.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        ssuri = self.snapshot_query(name, project, tenant, snapshotname)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT_INSTANCE.format(uri, ssuri),

            None, None)

        o = common.json_decode(s)
        return o

    def snapshot_query(self, name, project, tenant, snapshotname):
        '''
        This function will take the snapshot name and consistency group name
        as input and get uri of the first occurance of snapshot.
        paramters:
             name : Name of consistency group.
             snapshot name: Name of the snapshot
        return
            return with uri of the given snapshot.
        '''
        if (common.is_uri(name)):
            return name

        uris = self.snapshot_list(name, project, tenant)
        for ss in uris:
            if (ss['name'] == snapshotname):
                return ss['id']
        raise SOSError(SOSError.SOS_FAILURE_ERR, "Snapshot " + snapshotname +
                       ": not found")

    def snapshot_activate(self, name, project, tenant, snapshotname):
        '''
        This function will take consistency group parametes and
        snaphsot name  to be created.
        Function will activate consistency snapshot and
        return the status of the command.
        parameters:
             name : name of the consistency group
             project: name of the project name
             snapshot : name of the snapshot to be created.
        return
             activate snapshots under CG.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        ssuri = self.snapshot_query(name, project, tenant, snapshotname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT_ACTIVATE.format(uri, ssuri),
            None, None)

        o = common.json_decode(s)
        return o

    def snapshot_deactivate(self, name, project, tenant, snapshotname):
        '''
        This function will take consistency group parametes and
        snaphsot name  to be created.
        Function will deactivate consistency snapshot and
        return the status of the command.
        parameters:
             name : name of the consistency group
             project: name of the project name
             snapshot : name of the snapshot to be created.
        return
             deactivate the snapshots under CG.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        ssuri = self.snapshot_query(name, project, tenant, snapshotname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE.format(uri, ssuri),
            None, None)

        o = common.json_decode(s)
        return o

    def snapshot_restore(self, name, project, tenant, snapshotname):
        '''
        This function will take consistency group parametes and
        snaphsot name  to be created.
        Function will restore consistency snapshot and
        return the status of the command.
        parameters:
             name : name of the consistency group
             project: name of the project name
             snapshot : name of the snapshot to be created.
        return
             restore the snapshots under CG.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        ssuri = self.snapshot_query(name, project, tenant, snapshotname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_CONSISTENCY_GROUPS_SNAPSHOT_DEACTIVATE.format(uri, ssuri),
            None, None)

        o = common.json_decode(s)
        return o       
        
    def consitencygroup_protection_failover_ops(self, name, project, tenant, copyvarray,
                                    pit, accessmode, type="native", op="failover"):
        '''
        Failover the consistency group protection
        Parameters:
            name        : name of the consistency group
            project     : name of the project
            copyvarray  : name of the copy target virtual array
            pit         : any UTC point-in-time formatted as "yyyy-MM-dd_HH:mm:ss" or datetime in milliseconds
            accessmode  : access mode
            type        : type of protection
        Returns:
            result of the action.
        '''
        group_uri = self.consistencygroup_query(name, project, tenant)
        body = self.protection_copyparam(copyvarray, pit, accessmode, type)

        uri = self.URI_BLOCK_CONSISTENCY_GROUP_FAILOVER.format(group_uri)
        if op == 'failover_cancel':
            uri = self.URI_BLOCK_CONSISTENCY_GROUP_FAILOVER_CANCEL.format(
                     group_uri)
        elif op == 'swap':
            uri = self.URI_BLOCK_CONSISTENCY_GROUP_SWAP.format(
                     group_uri)
        elif op == 'update-access-mode':
            uri = self.URI_BLOCK_CONSISTENCY_GROUP_ACCESS_MODE.format(
                     group_uri)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            uri,
            body)
        return common.json_decode(s)   
        
    def protection_copyparam(
            self, copyvarray, pit, accessmode, type="native", sync='false'):
        copies_param = dict()
        copy = dict()
        copy_entries = []

        copy['type'] = type
        
        if(pit != ""):
            copy['pointInTime'] = pit
        if(accessmode != ""):
            copy['accessMode'] = accessmode
        #true=split
        if(sync == 'true'):
            copy['sync'] = "true"
        else:
            copy['sync'] = "false"
        #for rp and srdf target virtual array should be provided
        from virtualarray import VirtualArray
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(copyvarray)
    
        copy['copyID'] = varray_uri
        copy_entries.append(copy)
        copies_param['copy'] = copy_entries
        return json.dumps(copies_param)               

    '''
        Makes a REST API call to create migration
    '''
    def migration_create(self, name, project, tenant,
                                              target,
                                              poolname,
                                              compression):
        parms = {}
        storage_id = self.get_storage_id(target)
        parms['target_storage_system'] = storage_id

        if (poolname):
			from storagepool import StoragePool
			obj = StoragePool(self.__ipAddr, self.__port)
			(device_id, pooluri) = obj.storagepool_query(
                        poolname,
                        None,
                        target,
                        "vmax")
			parms['srp'] = pooluri

        if (compression):
		    parms['compression_enabled'] = True

        body = json.dumps(parms)
        uri = self.consistencygroup_query(name, project, tenant)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                self.URI_CONSISTENCY_GROUPS_MIGRATION_CREATE.format(uri), body)
        return common.json_decode(s)

    def migration_list(self, name, project, tenant, xml=False):
        '''
        This function will take consistency group name and project name
        as input and It will return all migrations of the consistency group.
        parameters:
           name : Name of the consistency group.
           project: Name of the project.
        return
            returns all migrations of consistency group.
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_CONSISTENCY_GROUPS_MIGRATION_GET.format(uri), None)
        o = common.json_decode(s)
        if(not o or "migration" not in o):
            return []

        return common.get_node_value(o, 'migration')

    def migration_operation(self, name, project, tenant, operation_uri):
        '''
        Execute migration operation
        as input and It will return all migrations of the consistency group.
        parameters:
           name : Name of the consistency group.
           project: Name of the project.
		   uri : URI of the operation
        '''
        uri = self.consistencygroup_query(name, project, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", operation_uri.format(uri), None)
        return common.json_decode(s)

    '''
        Query compute Id
    '''
    def get_compute_id(self, compute,
                                     type,
                                     datacenter,
                                     vcenter,
                                     tenantname):
        if (common.is_uri(compute)):
            return compute

        if (type != None):
            from host import Host
            hostObject = Host(self.__ipAddr, self.__port)
            return hostObject.query_by_name_and_type(compute, type, tenantname)
        elif (datacenter != None):
			from cluster import Cluster
			clusterObject = Cluster(self.__ipAddr, self.__port)
			return clusterObject.cluster_query(compute, datacenter, vcenter, tenantname)
        else:
            raise SOSError(SOSError.VALUE_ERR,
                           "host type or datacenter is not provided")

    '''
        Query storage system Id
    '''
    def get_storage_id(self, storage):
        if (common.is_uri(storage)):
            return storage

        from storagesystem import StorageSystem
        storage_system = StorageSystem(self.__ipAddr, self.__port)
        return storage_system.query_by_serial_number_and_type(storage, "vmax")

    '''
        Makes a REST API call to create zones
    '''
    def migration_create_zones(self, name, project, tenant, target, compute,
                                              type,
                                              datacenter,
                                              vcenter,
                                              minpaths,
                                              maxpaths,
                                              pathsperinitiator,
											  maxinitiatorsperport,
											  varray):
        parms = {}
        target_id = self.get_storage_id(target)
        parms['target_storage_system'] = target_id

        if (compute):
            compute_id = self.get_compute_id(compute, type, datacenter, vcenter, tenant)
            parms['compute'] = compute_id

        path_parameters = {}
        if (maxpaths):
            path_parameters['max_paths'] = maxpaths
        if (minpaths):
            path_parameters['min_paths'] = minpaths
        if (pathsperinitiator is not None):
            path_parameters['paths_per_initiator'] = pathsperinitiator
        if (maxinitiatorsperport):
            path_parameters['max_initiators_per_port'] = maxinitiatorsperport
        parms['path_param'] = path_parameters

        if (varray):
			from virtualarray import VirtualArray
			varrayObject = VirtualArray(self.__ipAddr, self.__port)
			varrayuri = varrayObject.varray_query(varray)
			parms = {'target_virtual_array': varrayuri}

        body = json.dumps(parms)
        uri = self.consistencygroup_query(name, project, tenant)

        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                self.URI_CONSISTENCY_GROUPS_MIGRATION_CREATE_ZONES.format(uri), body)
        return common.json_decode(s)

# Consistency Group Create routines

def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Consistency group Create cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a consistency group')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='container project name',
                                required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    create_parser.add_argument('-array-consistent', '-ac',
                               metavar='<arrayconsistency>',
                               dest='arrayconsistency',
                               default='true',
                               help='for RP and VPLEX volumes determines if volumes in the CG should be consistent on the array; default to true')
    create_parser.set_defaults(func=consistencygroup_create)


def consistencygroup_create(args):
    try:
        obj = ConsistencyGroup(args.ip, args.port)
        res = obj.create(args.name, args.project, args.tenant, args.arrayconsistency)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "consistency group",
                                        e.err_text, e.err_code)
# Consistency Group Updates routines

def update_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Consistency group Update cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a consistency group')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='container project name',
                                required=True)
    update_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    update_parser.add_argument('-add_volumes', '-av',
                               metavar='<add_volumes>',
                               dest='add_volumes',
                               nargs='+',
                               help='Volumes to be added to consistency group')
    update_parser.add_argument(
        '-remove_volumes', '-rv',
        metavar='<remove_volumes>',
        dest='remove_volumes',
        nargs='+',
        help='Volumes to be remove from consistency group')
    update_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')

    update_parser.add_argument('-synctimeout',
                               dest='synctimeout',
                               help='Synchronous timeout in Seconds',
                               default=0, type=int)
    

    update_parser.set_defaults(func=consistencygroup_update)


def consistencygroup_update(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    try:
        obj = ConsistencyGroup(args.ip, args.port)
        res = obj.update(args.name, args.project, args.tenant,
                         args.add_volumes, args.remove_volumes, args.sync,args.synctimeout)
    except SOSError as e:
        raise SOSError(SOSError.SOS_FAILURE_ERR, "Consistency Group " +
                       args.name + ": Update failed:\n" + e.err_text)

# consistency group Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Consistency group Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a Consistency group')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
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
    delete_parser.add_argument('-vipronly', '-vo',
                            dest='vipronly',
                            help='Delete only from ViPR',
                            action='store_true')

    delete_parser.set_defaults(func=consistencygroup_delete)


def consistencygroup_delete(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        res = obj.delete(args.name, args.project, args.tenant, args.vipronly)
        return res
    except SOSError as e:
        raise SOSError(SOSError.SOS_FAILURE_ERR, "Consistency Group " +
                       args.name + ": Delete failed:\n" + e.err_text)

# consistency group List routines

def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Consistency group List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List all Consistency groups')
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
                             help='List consistency groups with details',
                             action='store_true')
    list_parser.set_defaults(func=consistencygroup_list)


def consistencygroup_list(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        uris = obj.list(args.project, args.tenant)
        output = []
        rawoutput = []
        for uri in uris:
            cg = obj.show(uri, args.project, args.tenant)
            if(cg):
                rawoutput.append(cg)
                from volume import Volume
                from storagesystem import StorageSystem
                cg["system_consistency_groups"] = " "
                if("volumes" in cg):
                    volumeuris = common.get_node_value(cg, "volumes")
                    volobj = Volume(args.ip, args.port)
                    volumenames = []
                    for volume in volumeuris:
                        vol = volobj.show_by_uri(volume['id'])
                        if(vol):
                            volumenames.append(vol['name'])
                            
                    cg['volumes'] = volumenames
                    volumenames = []
                output.append(cg)
        if(not args.verbose):        
            if(len(output)):
                TableGenerator(output, ['name', 'volumes']).printTable()
        else:
            if(len(rawoutput)):
                return common.format_json_object(rawoutput) 
    except SOSError as e:
        raise SOSError(SOSError.SOS_FAILURE_ERR,
                       "Consistency Group  List failed:\n" + e.err_text)

# consistency group Show routines

def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Consistency group Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show full details of a Consistency group')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group',
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
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=consistencygroup_show)


def consistencygroup_show(args):

    obj = ConsistencyGroup(args.ip, args.port)
    try:
        res = obj.show(args.name, args.project, args.tenant, args.xml)
        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        raise e

# Consistency group snapshot commands
def snapshot_parser(subcommand_parsers, common_parser):
    snapshot_parser = subcommand_parsers.add_parser(
        'snapshot', parents=[common_parser],
        conflict_handler='resolve',
        help='Snapshot operation through consistency group')
    subcommand_parsers = snapshot_parser.add_subparsers(
        help='Use one of the commands')

    #snapshot create
    sscreate_parser = subcommand_parsers.add_parser(
        'create', parents=[common_parser], conflict_handler='resolve',
        help='create snapshot for consistency group')
    mandatory_args = sscreate_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name', '-n', metavar='<consistencygroupname>', dest='name',
        help='Name of Consistency Group', required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-snapshotname', '-ssn',
                                metavar='<snapshotname>',
                                dest='snapshotname',
                                help='Name of Snapshot',
                                required=True)
    sscreate_parser.add_argument('-createinactive', '-ci',
                                 dest='createinactive',
                                 action='store_true',
                                 help='Create snapshot with inactive state')

    #snapshot list
    sslist_parser = subcommand_parsers.add_parser(
        'list',
        parents=[common_parser],
        conflict_handler='resolve',
        help='list snapshots for consistency group')
    mandatory_args = sslist_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='Name of Consistency Group',
                                required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    #snapshot show
    ssshow_parser = subcommand_parsers.add_parser(
        'show',
        parents=[common_parser],
        conflict_handler='resolve',
        help='showe snapshot in consistency group')
    mandatory_args = ssshow_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='Name of Consistency Group',
                                required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-snapshotname', '-ssn',
                                metavar='<snapshotname>',
                                dest='snapshotname',
                                help='Name of Snapshot',
                                required=True)
    #snapshot activate
    ssactivate_parser = subcommand_parsers.add_parser(
        'activate',
        parents=[common_parser],
        conflict_handler='resolve',
        help='activate snapshot in consistency group')
    mandatory_args = ssactivate_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='Name of Consistency Group',
                                required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-snapshotname', '-ssn',
                                metavar='<snapshotname>',
                                dest='snapshotname',
                                help='Name of Snapshot',
                                required=True)

    #snapshot deactivate
    ssdeactivate_parser = subcommand_parsers.add_parser(
        'deactivate',
        parents=[common_parser],
        conflict_handler='resolve',
        help='deactivatee snapshot in consistency group')
    mandatory_args = ssdeactivate_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='Name of Consistency Group',
                                required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-snapshotname', '-ssn',
                                metavar='<snapshotname>',
                                dest='snapshotname',
                                help='Name of Snapshot',
                                required=True)

    #snapshot restore
    ssrestore_parser = subcommand_parsers.add_parser(
        'restore', parents=[common_parser], conflict_handler='resolve',
        help='restore snapshot in consistency group')
    mandatory_args = ssrestore_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='Name of Consistency Group',
                                required=True)
    snapshot_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-snapshotname', '-ssn',
                                metavar='<snapshotname>',
                                dest='snapshotname',
                                help='Name of Snapshot',
                                required=True)

    sscreate_parser.set_defaults(func=snapshot_create)
    ssshow_parser.set_defaults(func=snapshot_show)
    sslist_parser.set_defaults(func=snapshot_list)
    ssactivate_parser.set_defaults(func=snapshot_activate)
    ssdeactivate_parser.set_defaults(func=snapshot_deactivate)
    ssrestore_parser.set_defaults(func=snapshot_restore)


def snapshot_create(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_create(args.name, args.project, args.tenant,
                                  args.snapshotname, args.createinactive)
    except SOSError as e:
        raise e


def snapshot_list(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_list(args.name, args.project, args.tenant)
        if(len(res)):
            TableGenerator(res, ['name']).printTable()
    except SOSError as e:
        raise e


def snapshot_show(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_show(args.name, args.project, args.tenant,
                                args.snapshotname)
        return common.format_json_object(res)
    except SOSError as e:
        raise e


def snapshot_activate(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_activate(args.name, args.project, args.tenant,
                                    args.snapshotname)
    except SOSError as e:
        raise e


def snapshot_deactivate(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_deactivate(args.name, args.project, args.tenant,
                                      args.snapshotname)
    except SOSError as e:
        raise e


def snapshot_restore(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.snapshot_restore(args.name, args.project, args.tenant,
                                   args.snapshotname)
    except SOSError as e:
        raise e

# consistency group Protection routines

def failover_parser(subcommand_parsers, common_parser):
    # consistency group failover parser
    failover_parser = subcommand_parsers.add_parser(
        'failover',
        description='ViPR Consistency group failover CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='failover consistency group')
    mandatory_args = failover_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    failover_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    mandatory_args.add_argument('-copyvarray', '-cv',
                               metavar='<copyvarray>',
                               dest='copyvarray',
                               help='copy virtual array name',
                               required=True)
    failover_parser.add_argument('-pit', '-p',
                               metavar='<pit>',
                               dest='pit',
                               help='any UTC point-in-time formatted as "yyyy-MM-dd_HH:mm:ss" or datetime in milliseconds')
    failover_parser.add_argument('-type', '-t',
                               metavar='<type>',
                               dest='type',
                               help='type of protection - native, rp, srdf')
                               
    failover_parser.set_defaults(func=failover)
                         
def failover_cancel_parser(subcommand_parsers, common_parser):                          
    # cancel consistency group failover
    failover_cancel_parser = subcommand_parsers.add_parser(
        'failover_cancel',
        description='ViPR Consistency group failover cancel CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='cancel consistency group failover')
    mandatory_args = failover_cancel_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    failover_cancel_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    mandatory_args.add_argument('-copyvarray', '-cv',
                               metavar='<copyvarray>',
                               dest='copyvarray',
                               help='copy virtual array name',
                               required=True)
    failover_cancel_parser.add_argument('-type', '-t',
                               metavar='<type>',
                               dest='type',
                               help='type of protection - native, rp, srdf')   
                               
    failover_cancel_parser.set_defaults(func=failover_cancel)                                 

def swap_parser(subcommand_parsers, common_parser): 
    # swap consistency group protection
    swap_parser = subcommand_parsers.add_parser(
        'swap',
        description='ViPR Consistency group swap protection CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='swap consistency group protection')
    mandatory_args = swap_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    swap_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    mandatory_args.add_argument('-copyvarray', '-cv',
                               metavar='<copyvarray>',
                               dest='copyvarray',
                               help='copy virtual array name',
                               required=True)
    swap_parser.add_argument('-type', '-t',
                               metavar='<type>',
                               dest='type',
                               help='type of protection - native, rp, srdf')     
                                  
    swap_parser.set_defaults(func=swap)

def update_access_mode_parser(subcommand_parsers, common_parser):
    # change access mode on a consistency group copy
    update_access_mode_parser = subcommand_parsers.add_parser(
        'update-access-mode',
        description='ViPR consistency roup update access mode CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='update access mode')
    mandatory_args = update_access_mode_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group ',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    update_access_mode_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='container tenant name')
    mandatory_args.add_argument('-copyvarray', '-cv',
                               metavar='<copyvarray>',
                               dest='copyvarray',
                               help='copy virtual array name',
                               required=True)
    mandatory_args.add_argument('-accessmode', '-am',
                               metavar='<accessmode>',
                               dest='accessmode',
                               help='access mode - DIRECT_ACCESS',
                               required=True)
    update_access_mode_parser.add_argument('-type', '-t',
                               metavar='<type>',
                               dest='type',
                               help='type of protection - native, rp, srdf')

    update_access_mode_parser.set_defaults(func=update_access_mode)

def failover(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.pit):
            args.pit = ""
        res = obj.consitencygroup_protection_failover_ops(args.name, args.project, args.tenant,
                                   args.copyvarray, args.pit, "", args.type, "failover")
    except SOSError as e:
        raise e
        
def failover_cancel(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.consitencygroup_protection_failover_ops(args.name, args.project, args.tenant,
                                   args.copyvarray, "", "", args.type, "failover_cancel")
    except SOSError as e:
        raise e   
        
def swap(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.consitencygroup_protection_failover_ops(args.name, args.project, args.tenant,
                                   args.copyvarray, "", "", args.type, "swap")
    except SOSError as e:
        raise e                    

def update_access_mode(args):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.accessmode):
            args.accessmode = ""
        res = obj.consitencygroup_protection_failover_ops(args.name, args.project, args.tenant,
                                   args.copyvarray, "", args.accessmode, args.type, "update-access-mode")
    except SOSError as e:
        raise e

# consistency group migration routines

def migration_create_parser(subcommand_parsers, common_parser):
    # migration create command parser
    migration_create_parser = subcommand_parsers.add_parser(
        'migration-create',
        description='ViPR Consistency group migration Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create migration of a consistency group')
    mandatory_args = migration_create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    mandatory_args.add_argument('-target', '-tgt',
                                metavar="<target>",
                                help='serial number of the target storage system',
                                dest='target',
                                required=True)
    migration_create_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')
    migration_create_parser.add_argument('-compression', '-cp',
                             dest='compression',
                             help='enable compression',
                             action='store_true')
    migration_create_parser.add_argument('-poolname', '-pn',
                                metavar='poolname',
                                dest='poolname',
                                help='name of storage pool')
    migration_create_parser.set_defaults(func=migration_create)

def migration_create(args):
    try:
        obj = ConsistencyGroup(args.ip, args.port)
        res = obj.migration_create(args.name, args.project, args.tenant, args.target, args.poolname, args.compression)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "migration", e.err_text, e.err_code)

def migration_list_parser(subcommand_parsers, common_parser):
    # migration list command parser
    migration_list_parser = subcommand_parsers.add_parser(
        'migration-list',
        description='ViPR Consistency group migration List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List all migrations of a consistency group')
    mandatory_args = migration_list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    migration_list_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')
    migration_list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List migrations with details',
                             action='store_true')
    migration_list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             help='List migrations in table format',
                             action='store_true')
    migration_list_parser.set_defaults(func=migration_list)

def migration_list(args):
    obj = ConsistencyGroup(args.ip, args.port)
    records = []
    try:
        migration_list = obj.migration_list(args.name, args.project, args.tenant)
        if(len(migration_list) > 0):
            from migration import Migration
            migrationobj = Migration(args.ip, args.port)
            for migration in migration_list:
                migration_uri = migration['id']
                migration_detail = migrationobj.show(migration_uri)
                if(migration_detail):
                    records.append(migration_detail)

        if(len(records) > 0):
            if(args.verbose == True):
                return common.format_json_object(records)

            elif(args.largetable == True):
                TableGenerator(records, ['name', 'job_status']).printTable()
            else:
                TableGenerator(records, ['name']).printTable()

        else:
            return
    except SOSError as e:
        raise SOSError(SOSError.SOS_FAILURE_ERR,
                       "Consistency group migration list failed:\n" + e.err_text)

# Common parser for migration operations
def migration_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    cc_common_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')

def migration_operation(args, operation, uri):
    obj = ConsistencyGroup(args.ip, args.port)
    try:
        obj.migration_operation(
            args.name,
            args.project,
            args.tenant,
            uri)
        return

    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(
                SOSError.SOS_FAILURE_ERR,
                operation + " migration of " +
                args.name +
                " failed\n" +
                e.err_text)
        else:
            common.format_err_msg_and_raise(
                operation,
                "migration",
                e.err_text,
                e.err_code)

# migration cutover parser
def migration_cutover_parser(subcommand_parsers, common_parser):
    migration_cutover_parser = subcommand_parsers.add_parser(
        'migration-cutover',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Run migration cutover',
        description='ViPR Consistency group migration Cutover CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_cutover_parser)
    migration_cutover_parser.set_defaults(func=migration_cutover)

# migration cutover
def migration_cutover(args):
    migration_operation(args, "create", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_CUTOVER)

# migration commit parser
def migration_commit_parser(subcommand_parsers, common_parser):
    migration_commit_parser = subcommand_parsers.add_parser(
        'migration-commit',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Commit migration',
        description='ViPR Consistency group migration Commit CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_commit_parser)
    migration_commit_parser.set_defaults(func=migration_commit)

# migration commit
def migration_commit(args):
    migration_operation(args, "commit", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_COMMIT)

# migration cancel parser
def migration_cancel_parser(subcommand_parsers, common_parser):
    migration_cancel_parser = subcommand_parsers.add_parser(
        'migration-cancel',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Cancel migration',
        description='ViPR Consistency group migration Cancel CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_cancel_parser)
    migration_cancel_parser.add_argument('-revert', '-r',
                             dest='revert',
                             help='cancel with revert',
                             action='store_true')
    migration_cancel_parser.set_defaults(func=migration_cancel)

# migration cancel
def migration_cancel(args):
    restapi = ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_CANCEL
    if(args.revert):
        restapi += "?revert=true"
    migration_operation(args, "cancel", restapi)

# migration recover parser
def migration_recover_parser(subcommand_parsers, common_parser):
    migration_recover_parser = subcommand_parsers.add_parser(
        'migration-recover',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Recover migration',
        description='ViPR Consistency group migration Recover CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_recover_parser)
    migration_recover_parser.add_argument('-force', '-f',
                             dest='force',
                             help='force to recover',
                             action='store_true')
    migration_recover_parser.set_defaults(func=migration_recover)

# migration recover
def migration_recover(args):
    restapi = ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_RECOVER
    if(args.force):
        restapi += "?force=true"
    migration_operation(args, "recover", restapi)

# migration refresh parser
def migration_refresh_parser(subcommand_parsers, common_parser):
    migration_refresh_parser = subcommand_parsers.add_parser(
        'migration-refresh',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Refresh migration',
        description='ViPR Consistency group migration Refresh CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_refresh_parser)
    migration_refresh_parser.set_defaults(func=migration_refresh)

# migration refresh
def migration_refresh(args):
    migration_operation(args, "refresh", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_REFRESH)

# migration sync start parser
def migration_sync_start_parser(subcommand_parsers, common_parser):
    migration_sync_start_parser = subcommand_parsers.add_parser(
        'migration-sync-start',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Start migration synchronization',
        description='ViPR Consistency group migration Sync Start CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_sync_start_parser)
    migration_sync_start_parser.set_defaults(func=migration_sync_start)

# migration sync start
def migration_sync_start(args):
    migration_operation(args, "sync_start", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_SYNC_START)

# migration sync stop parser
def migration_sync_stop_parser(subcommand_parsers, common_parser):
    migration_sync_stop_parser = subcommand_parsers.add_parser(
        'migration-sync-stop',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Stop migration synchronization',
        description='ViPR Consistency group migration Sync Stop CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_sync_stop_parser)
    migration_sync_stop_parser.set_defaults(func=migration_sync_stop)

# migration sync stop
def migration_sync_stop(args):
    migration_operation(args, "sync_stop", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_SYNC_STOP)

# migration rescan hosts parser
def migration_rescan_hosts_parser(subcommand_parsers, common_parser):
    migration_rescan_hosts_parser = subcommand_parsers.add_parser(
        'migration-rescan-hosts',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Rescan hosts',
        description='ViPR Consistency group migration Rescan Hosts CLI usage.')
    # Add parameter from common parser
    migration_common_parser(migration_rescan_hosts_parser)
    migration_rescan_hosts_parser.set_defaults(func=migration_rescan_hosts)

# migration rescan hosts
def migration_rescan_hosts(args):
    migration_operation(args, "rescan_hosts", ConsistencyGroup.URI_CONSISTENCY_GROUPS_MIGRATION_RESCAN_HOSTS)

def migration_create_zones_parser(subcommand_parsers, common_parser):
    from host import Host
    # migration create command parser
    migration_create_zones_parser = subcommand_parsers.add_parser(
        'migration-create-zones',
        description='ViPR Consistency group migration Create Zones CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create zones for migration')
    mandatory_args = migration_create_zones_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<consistencygroupname>',
                                dest='name',
                                help='name of Consistency Group',
                                required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='name of Project',
                                required=True)
    mandatory_args.add_argument('-target', '-tgt',
                                         help='serial number or ID of the target storage system',
                                         dest='target',
                                         metavar='<target>',
                                         required=True)
    mandatory_args.add_argument('-compute', '-com',
                                         help='name or ID of the compute system',
                                         dest='compute',
                                         metavar='<compute>',
                                         required=True)
    migration_create_zones_parser.add_argument('-type', '-t',
                                         choices=Host.HOST_TYPE_LIST,
                                         dest='type',
                                         help='type of host (needed for host)',
                                         default=None)
    migration_create_zones_parser.add_argument('-datacenter', '-dc',
                                         metavar='<datacentername>',
                                         dest='datacenter',
                                         help='name of datacenter (needed for cluster)',
                                         default=None)
    migration_create_zones_parser.add_argument('-vcenter', '-vc',
                                         help='name of a vcenter ',
                                         dest='vcenter',
                                         metavar='<vcentername>',
                                         default=None)
    migration_create_zones_parser.add_argument('-maxpaths', '-mxp',
                                         help='The maximum number of paths that can be ' +
                                         'used between a host and a storage volume',
                                         metavar='<MaxPaths>',
                                         dest='maxpaths',
                                         type=int)
    migration_create_zones_parser.add_argument('-minpaths', '-mnp',
                                         help='The minimum  number of paths that can be used ' +
                                         'between a host and a storage volume',
                                         metavar='<MinPaths>',
                                         dest='minpaths',
                                         type=int)
    migration_create_zones_parser.add_argument('-pathsperinitiator', '-ppi',
                                         help='The number of paths per initiator',
                                         metavar='<PathsPerInitiator>',
                                         dest='pathsperinitiator',
                                         type=int)
    migration_create_zones_parser.add_argument('-maxinitiatorsperport', '-maxipp',
                                         metavar='<maxinitiatorsperport>',
                                         dest='maxinitiatorsperport',
                                         help=argparse.SUPPRESS,
                                         type=int)
    migration_create_zones_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='container tenant name')
    migration_create_zones_parser.add_argument('-varray', '-va',
                                metavar='<varray>',
                                dest='varray',
                                help='name of varray')
    migration_create_zones_parser.set_defaults(func=migration_create_zones)

def migration_create_zones(args):
    try:
        obj = ConsistencyGroup(args.ip, args.port)
        res = obj.migration_create_zones(args.name, args.project, args.tenant, args.target, args.compute, args.type, args.datacenter, args.vcenter,
            args.minpaths, args.maxpaths, args.pathsperinitiator, args.maxinitiatorsperport, args.varray)
    except SOSError as e:
        common.format_err_msg_and_raise("create zones", "for migration", e.err_text, e.err_code)

#
# consistency Group Main parser routine
#


def consistencygroup_parser(parent_subparser, common_parser):

    # main consistency group parser
    parser = parent_subparser.add_parser(
        'consistencygroup',
        description='ViPR Consistency Group cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Consistency group')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # failover parser
    failover_parser(subcommand_parsers, common_parser)
    
    # cancel failover parser    
    failover_cancel_parser(subcommand_parsers, common_parser)
    
    # swap parser    
    swap_parser(subcommand_parsers, common_parser)

    # access mode parser
    update_access_mode_parser(subcommand_parsers, common_parser)

    # snapshot command parser
    #snapshot_parser(subcommand_parsers, common_parser)

    # migration command parser
    migration_list_parser(subcommand_parsers, common_parser)
    migration_create_parser(subcommand_parsers, common_parser)
    migration_cutover_parser(subcommand_parsers, common_parser)
    migration_commit_parser(subcommand_parsers, common_parser)
    migration_cancel_parser(subcommand_parsers, common_parser)
    migration_refresh_parser(subcommand_parsers, common_parser)
    migration_recover_parser(subcommand_parsers, common_parser)
    migration_sync_start_parser(subcommand_parsers, common_parser)
    migration_sync_stop_parser(subcommand_parsers, common_parser)
    migration_create_zones_parser(subcommand_parsers, common_parser)
    migration_rescan_hosts_parser(subcommand_parsers, common_parser)
