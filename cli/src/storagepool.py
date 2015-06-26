#!/usr/bin/python
'''
 Copyright (c) 2012 EMC Corporation
 All Rights Reserved

 This software contains the intellectual property of EMC Corporation
 or is licensed to EMC Corporation from third parties.  Use of this
 software and the intellectual property contained therein is expressly
 limited to the terms and conditions of the License Agreement under which
 it is provided by or on behalf of EMC.
'''
import common
import sys
import json
from common import SOSError
from storagesystem import StorageSystem


class StoragePool(object):

    '''
    The class definition for operations on 'Storage Pool'.
    '''

    # Commonly used URIs for the 'Storage Pool' module

    URI_SERVICES_BASE = ''
    URI_STORAGEDEVICES = '/vdc/storage-systems'
    URI_STORAGEDEVICE = URI_STORAGEDEVICES + '/{0}'
    URI_STORAGEPOOLS = URI_STORAGEDEVICE + '/storage-pools'
    URI_STORAGEPOOL_SHOW = URI_STORAGEPOOLS + '/{1}'
    URI_STORAGEPOOL = '/vdc/storage-pools/{0}'
    URI_STORAGEPOOL_REGISTER = URI_STORAGEPOOLS + '/{1}/register'
    URI_STORAGEPOOL_DEREGISTER = URI_STORAGEPOOLS + '/{1}/deregister'
    URI_DEREGISTER = URI_STORAGEPOOL + '/deregister'
    URI_STORAGEPOOL_UPDATE = '/vdc/storage-pools/{0}'
    URI_STORAGEPOOL_TIERS = '/vdc/storage-pools/{0}/storage-tiers'
    URI_STORAGEPOOL_DEACTIVATE = '/vdc/storage-pools/{0}/deactivate'

    #SYSTEM_TYPE_LIST = ['isilon', 'vnxblock',
    #'vnxfile', 'vmax', 'netapp', 'vplex']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def storagepool_list_by_uri(self, uri):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                StoragePool.URI_STORAGEPOOLS.format(uri),
                None)
        o = common.json_decode(s)

        returnlst = []
        for iter in o['storage_pool']:
            returnlst.append(iter['id'])

        return returnlst
        # print o
        # return common.get_object_id(o)

    def storagesystem_query(self, devicename, devicetype, serialnumber=None):
        '''
        Returns the URI of the storage system given either name or serialnumber
        Parameters:
            devicename:name of the system
            devicetype: type of system
            serialnumber: serial number
        Returns:
            System URI
        '''

        from storagesystem import StorageSystem
        if (not common.is_uri(devicename)):
            obj = StorageSystem(self.__ipAddr, self.__port)
            if(serialnumber and len(serialnumber) > 0):
                device_id = obj.query_by_serial_number_and_type(
                                        serialnumber,
                                        devicetype)
            else:
                device = obj.show(name=devicename, type=devicetype)
                device_id = device['id']
        else:
            device_id = devicename
        return device_id

    # UNUSED FUNCTION
    def storagepool_list(self, devicename, devicetype, serialnumber=None):
        '''
        Returns the list of storage pools under a particular device
        Parameters:
            devicename:name of the device
        Returns:
            JSON payload
        '''

        device_id = self.storagesystem_query(devicename,
                                             devicetype,
                                              serialnumber)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
        StoragePool.URI_STORAGEPOOLS.format(device_id), None)

        o = common.json_decode(s)

        returnlst = []
        for iter in o['storage_pool']:
            returnlst.append(iter['id'])

        return returnlst
        # return common.get_object_id(o)

    def storagepool_show_by_uri(self, systemuri, pooluri, xml=False):
        '''
        Makes a REST api call to show the storagepool information
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
        StoragePool.URI_STORAGEPOOL_SHOW.format(systemuri,
                                                 pooluri),
        None,
        None,
        xml)

        if(xml == False):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive'] == True):
                    return None
        else:
            return s

        return o
    # get the list of storage tier for a given storagepool

    def storagepool_get_tiers_by_uri(self, pooluri):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    StoragePool.URI_STORAGEPOOL_TIERS.format(pooluri), None)

        o = common.json_decode(s)
        return o['storage_tier']

    #
    # Encode HashSet as an array
    #
    def __encode_map(self, stringmap):
        entry = dict()
        for mapentry in stringmap:
            (key, value) = mapentry.split('=', 1)
            entry[key] = value
        return entry

    def storagepool_create(self, storagename, poolname,
                           protocol, maxSnapshots, consistency,
                           freeCapacity, totalCapacity,
                           extensions,
                           deviceType):
        '''
        Creates a storagepool with specified parameters
        Parameters:
            storagename:           name of the storage system
            poolname:               name of the storage pool
            protocol:        protocols supported by pool
            nativeId:        native ID fothe pool
            extentions:        extended parameters and attributes
            resiliency:        resiliency assosiated with pool
            performance:    performance associated with the storage pool
            efficiency:        efficiency associated with the storage pool
            allocation        allocation of storage pool
            maxSnapshots:    maxSnapshots permitted on the storage pool
            consistency:    consistency details of storage pool
            resiliencymap:    resiliencymap of the storage pool
            freeCapacity:    freeCapacity of the storage pool
            totalCapacity:    totalCapacity of  the storage pool
        returns:
            JSON payload of the created storagepool
        '''

        uri = None
        sstype = None

        if (not common.is_uri(storagename)):
            from storagesystem import StorageSystem
            obj = StorageSystem(self.__ipAddr, self.__port)
            device = obj.show(name=storagename, type=deviceType)
            uri = device['id']

        sstype = deviceType

        checklist = []
        for iter in extensions:
            (key, value) = iter.split('=', 1)
            checklist.append(key)

        if((sstype == 'vnxblock') or
           (sstype == 'vnxfile') or
           (sstype == 'vmax')):
            if('NativeId' not in checklist)or ('PoolType' not in checklist):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               "error: For device type " + sstype +
                               " -nativeid, -pooltype are required")

        if(sstype == 'vnxfile'):
            if('Name' not in checklist):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               "error: For device type " + sstype +
                               " -controllerpoolname is required")

        '''check for storage pool with existing name'''
        storage_pool_exists = True

        try:
            self.storagepool_show(poolname, storagename, None, deviceType)

        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                storage_pool_exists = False
            else:
                raise e

        if(storage_pool_exists):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "Storage pool with name: " +
                           poolname + " already exists")

        parms = dict()

        if (poolname):
            parms['name'] = poolname

        if (protocol):
            parms['protocols'] = protocol

        if (maxSnapshots):
            parms['max_snapshots'] = maxSnapshots

        if (consistency):
            parms['multi_volume_consistency'] = consistency

        if (extensions):
            parms['controller_params'] = self.__encode_map(extensions)

        if (freeCapacity):
            parms['free_capacity'] = freeCapacity

        if (totalCapacity):
            parms['total_capacity'] = totalCapacity

        body = None

        if (parms):
            body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                        StoragePool.URI_STORAGEPOOLS.format(uri), body)

        o = common.json_decode(s)

        return o

    def storagepool_update_by_uri(self, pooluri, varrays,
                                  maxresources,
                                  maxpoolutilization,
                                  maxthinpoolsubscription):
        '''
        Updates a storagepool
        '''
        parms = dict()
        body = None

        if (maxresources):
            parms['max_resources'] = maxresources

        if (maxpoolutilization):
            parms['max_pool_utilization_percentage'] = maxpoolutilization

        if (maxthinpoolsubscription):
            parms[
            'max_thin_pool_subscription_percentage'] = maxthinpoolsubscription

        if(varrays):
            parms['varray_assignment_changes'] = varrays
        body = json.dumps(parms)

        #myuri = '/vdc/storage-pools/'+ pooluri +'/matched-vpool'
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                StoragePool.URI_STORAGEPOOL_UPDATE.format(pooluri), body)

        o = common.json_decode(s)
        return o

    def storagepool_update(self, storagesystem, serialnumber, devicetype,
                           poolname,
                           nhadds, nhrems,
                           volumetype, maxresources,
                           maxpoolutilization,
                           maxthinpoolsubscription):

        nhassignments = dict()
        #parms['varray_assignment_changes'] = nhassignments
        if (nhadds):
            nhlst = []
            for iter in nhadds:
                from virtualarray import VirtualArray
                obj = VirtualArray(self.__ipAddr, self.__port)

                nbhuri = obj.varray_query(iter)
                if(nbhuri):
                    nhlst.append(nbhuri)

            if(nhlst):
                nh = dict()
                nh['varrays'] = nhlst
                nhassignments['add'] = nh
        if (nhrems):
            nhlst = []
            for iter in nhrems:
                from virtualarray import VirtualArray
                obj = VirtualArray(self.__ipAddr, self.__port)

                nbhuri = obj.varray_query(iter)
                if(nbhuri):
                    nhlst.append(nbhuri)

            if(nhlst):
                nh = dict()
                nh['varrays'] = nhlst
                nhassignments['remove'] = nh

        if(storagesystem):
            device_id = self.storagesystem_query(storagesystem,
                                                 devicetype,
                                                 None)
        else:
            device_id = self.storagesystem_query(None, devicetype,
                                                 serialnumber)

        storagepool_ids = self.storagepool_list_by_uri(device_id)
        not_found=1
        for uri in storagepool_ids:
            storpool = self.storagepool_show_by_uri(device_id, uri)
            if(storpool['pool_name'])==poolname:
                not_found=0
            if(poolname):
                if(storpool['pool_name'] == poolname):
                    if ((volumetype) and
                        (storpool['supported_resource_types'] == volumetype)):
                        self.storagepool_update_by_uri(storpool['id'],
                                                       nhassignments,
                                                       maxresources,
                                                       maxpoolutilization,
                                                       maxthinpoolsubscription)
                    if (not volumetype):
                        self.storagepool_update_by_uri(storpool['id'],
                                                       nhassignments,
                                                       maxresources,
                                                       maxpoolutilization,
                                                       maxthinpoolsubscription)

            else:
                if (not volumetype):
                    self.storagepool_update_by_uri(storpool['id'],
                                                   nhassignments,
                                                   maxresources,
                                                   maxpoolutilization,
                                                   maxthinpoolsubscription)
                if ((volumetype) and
                    (storpool['supported_resource_types'] == volumetype)):
                    self.storagepool_update_by_uri(storpool['id'],
                                                   nhassignments,
                                                    maxresources,
                                                    maxpoolutilization,maxthinpoolsubscription)
        if(poolname is not None):
            if(not_found==1):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                "Storagepool with name "
                + str(poolname) + " not found\n")
    def storagepool_register_main(self, serialnumber,deviceType, poolName):
        '''
        Registers a storagepool with specified parameters
        Parameters:
            serialnumber:   serial number of the storage system
            protocol:        (optional) protocols supported by pool
            maxSnapshots:
            (optional) maxSnapshots permitted on the storage pool
            consistency:    (optional) consistency details of storage pool
            deviceType:    storage system type
            poolName:    (optional) specific pool name to be registered
        returns:
            JSON payload of the created storagepool
    '''
        obj_ss = StorageSystem(self.__ipAddr, self.__port)

        #uri = obj_ss.query_storagesystem_by_serial_number(serialnumber)
        systemuri = obj_ss.query_by_serial_number_and_type(serialnumber,
                                                           deviceType)

        pooluris = self.storagepool_list_by_uri(systemuri)

        if(poolName):
            # Need to check if pool is found
            found = False
        else:
            found = True
        for pooluri in pooluris:
            if(poolName):
                storpool = self.storagepool_show_by_uri(systemuri, pooluri)

                compval = None

                compval = storpool['pool_name']

                if(poolName == compval):

                    found = True
                    self.storagepool_register(systemuri, pooluri)
                else:
                    self.storagepool_register(systemuri, pooluri)

        # Print error if named pool is not found
        if (not (found)):
            raise SOSError(SOSError.NOT_FOUND_ERR,
            "Storagepool with name "+ poolName + " not found\n")

    def storagepool_register(self, systemuri, pooluri):
        '''
        Creates a storagepool with specified parameters
        Parameters:
            storagename:           name of the storage system
            protocol:        protocols supported by pool
            maxSnapshots:    maxSnapshots permitted on the storage pool
            consistency:    consistency details of storage pool
        returns:
            JSON payload of the created storagepool
        '''

        parms = dict()

        body = None

        if (parms):
            body = json.dumps(parms)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "POST",
                StoragePool.URI_STORAGEPOOL_REGISTER.format(systemuri,
                                                            pooluri),
                body)

        o = common.json_decode(s)
        return o

    def storagepool_delete_by_uri(self, uri):
        '''
        Deletes a storagepool identifited by the given URI
        '''
        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port,
                    "POST",
                    StoragePool.URI_STORAGEPOOL_DEACTIVATE.format(uri), None)
        return str(s) + " ++ " + str(h)

    def storagepool_delete(self, qualifiedname, storagesystem,
                           serialnumber, devicetype):

        (device_id, pooluri) = self.storagepool_query(qualifiedname,
                                                      storagesystem,
                                                      serialnumber,
                                                      devicetype)
        return self.storagepool_delete_by_uri(pooluri)

    def storagepool_deregister_by_uri(self, deviceid, poolid):
        '''
        Deletes a storagepool identifited by the given URI
        '''
        (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "POST",
                StoragePool.URI_DEREGISTER.format(poolid), None)
        return str(s) + " ++ " + str(h)

    def storagepool_deregister(self, qualifiedname, storagesystem,
                               serialnumber,
                               devicetype):

        (device_id, pool_id) = self.storagepool_query(qualifiedname,
                                                      storagesystem,
                                                      serialnumber,
                                                      devicetype)
        return self.storagepool_deregister_by_uri(device_id, pool_id)

    def storagepool_query(self, qualifiedname, storagesystem, serialnumber,
                          devicetype):

        try:
            if(storagesystem):
                device_id = self.storagesystem_query(storagesystem,
                                                     devicetype,
                                                     None)
            else:
                device_id = self.storagesystem_query(None, devicetype,
                                                     serialnumber)

         # storagesystem_query(self, devicename, devicetype,
         # serialnumber=None):
        except SOSError as e:
            raise e

        storagepool_ids = self.storagepool_list_by_uri(device_id)

        for uri in storagepool_ids:
            storpool = self.storagepool_show_by_uri(device_id, uri)
            if(storpool):
                try:
                    if(storpool['pool_name'] == qualifiedname):
                        return (device_id, storpool['id'])

                except KeyError as e:
                    continue

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Storagepool of name " + qualifiedname + " not found")

    def storagepool_show(self, qualifiedname, storagesystem, serialnumber,
                         devicetype,
                         xml=False):
        (device_id, pooluri) = self.storagepool_query(
                            qualifiedname,
                            storagesystem,
                            serialnumber,
                            devicetype)
        return self.storagepool_show_by_uri(device_id, pooluri, xml)


# STORAGEPOOL Create routines

def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
                    description='ViPR Storagepool Create CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-storagesystem', '-ss',
                                help='Name of Storage System',
                                metavar='storagesystem',
                                dest='storagesystem',
                                required=True)

    mandatory_args.add_argument('-protocol', '-pl',
                                help='Protocol',
                                dest='protocol',
                                nargs="+",
                                choices=['NFS', 'CIFS', 'FC', 'iSCSI'],
                                required=True)

    mandatory_args.add_argument('-name',
                                help='Name of Pool',
                                dest='name',
                                metavar='name',
                                required=True)

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=['isilon', 'vnxfile'],
                                required=True)

    create_parser.add_argument('-maxsnapshots', '-msnp',
                               help='maximum number of snapshots supported',
                               default=0,
                               dest='maxsnapshots',
                               metavar='maxsnapshots')

    create_parser.add_argument('-consistency', '-con',
                help='multi-volume consistent snapshot capability',
                dest='consistency',
                metavar='consistency',
                default='False')

    '''Extentions'''
    create_parser.add_argument('-nativeid', '-nid',
                               help='Native ID of the pool to be created',
                               default=None,
                               dest='nativeid',
                               metavar='nativeid')

    create_parser.add_argument('-worm', '-w',
                               help='Is there WORM support or not',
                               dest='worm',
                               choices=['enabled', 'disabled'])

    create_parser.add_argument('-filesystemtype', '-f',
                               help='File system type',
                               default=None,
                               dest='filesystemtype',
                               metavar='filesystemtype')

    create_parser.add_argument('-thinprovisioned', '-thin',
                               help='Provisioning type flag',
                               dest='thinprovisioned',
                               choices=['true', 'false'])

    create_parser.add_argument('-autoextend', '-a',
                               help='Auto Extend enable flag',
                               dest='autoextendenabled',
                               choices=['true', 'false'])

    create_parser.add_argument('-pooltype', '-pty',
                               help='type of Pool',
                               dest='pooltype',
                               metavar='pooltype')

    create_parser.add_argument('-controllerpoolname',
                               help='Name of pool name param to controller',
                               dest='controllerpoolname',
                               metavar='controllerpoolname')

    '''end of Extentions'''

    create_parser.add_argument('-freecapacity',
        help='Total bytes of free storage space available in the pool',
        dest='freecapacity',
        metavar='freecapacity')

    create_parser.add_argument('-totalcapacity',
                               help='Total bytes of storage space in the pool',
                               dest='totalcapacity',
                               metavar='totalcapacity')

    create_parser.set_defaults(func=storagepool_create)


def storagepool_create(args):
    obj = StoragePool(args.ip, args.port)

    STORAGEPOOL_EXTENSION = '{0}={1}'

    try:

        extensions = []

        if(args.worm):
            extensions.append(STORAGEPOOL_EXTENSION.format('WORM', args.worm))
        if(args.filesystemtype):
            extensions.append(
                STORAGEPOOL_EXTENSION.format('FileSystemType',
                                             args.filesystemtype))
        if(args.thinprovisioned):
            extensions.append(STORAGEPOOL_EXTENSION.format(
                'ThinProvisioned',
                args.thinprovisioned))
        if(args.autoextendenabled):
            extensions.append(STORAGEPOOL_EXTENSION.format(
                'AutoExtendEnabled',
                args.autoextendenabled))
        if(args.pooltype):
            extensions.append(STORAGEPOOL_EXTENSION.format('PoolType',
                                                           args.pooltype))
        if(args.nativeid):
            extensions.append(STORAGEPOOL_EXTENSION.format('NativeId',
                                                           args.nativeid))
        if(args.controllerpoolname):
            extensions.append(STORAGEPOOL_EXTENSION.format(
                'Name',
                args.controllerpoolname))

        res = obj.storagepool_create(
            args.storagesystem, args.name, args.protocol,
            args.maxsnapshots, args.consistency,
            args.freecapacity, args.totalcapacity,
            extensions, args.type)

        # return common.format_json_object(res)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool create failed: "
                           + e.err_text)
        else:
            raise e


def register_parser(subcommand_parsers, common_parser):
    # register command parser
    register_parser = subcommand_parsers.add_parser('register',
                    description='ViPR Storagepool Register CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve')

    mandatory_args = register_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-serialnumber', '-sn',
                                help='Native GUID of Storage System',
                                metavar='serialnumber',
                                dest='serialnumber',
                                required=True)

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    register_parser.add_argument('-name',
                                 help='name of pool',
                                 dest='name',
                                 metavar='name')

    register_parser.set_defaults(func=storagepool_register)


def storagepool_register(args):
    obj = StoragePool(args.ip, args.port)

    try:
        res = obj.storagepool_register_main(args.serialnumber,
                                            args.type, args.name)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool register failed: "
                           + e.err_text)
        else:
            raise e


# STORAGEPOOL Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                    description='ViPR Storagepool Delete CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name',
                                help='Name of storagepool',
                                required=True,
                                metavar='poolname',
                                dest='name')

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    arggroup = delete_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    delete_parser.set_defaults(func=storagepool_delete)


def storagepool_delete(args):
    obj = StoragePool(args.ip, args.port)
    try:
        res = obj.storagepool_delete(args.name,
                                     args.storagesystem,
                                      args.serialnumber,
                                       args.type)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool delete failed: Storagepool " +
                           args.name + " : Not Found\n" + e.err_text)
        else:
            raise e


def deregister_parser(subcommand_parsers, common_parser):
    # delete command parser
    deregister_parser = subcommand_parsers.add_parser('deregister',
        description='ViPR Storagepool Deregister CLI usage',
        parents=[common_parser],
        conflict_handler='resolve')

    mandatory_args = deregister_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-name',
                                help='Name of storagepool',
                                required=True,
                                metavar='poolname',
                                dest='name')

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    arggroup = deregister_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    deregister_parser.set_defaults(func=storagepool_deregister)


def storagepool_deregister(args):
    obj = StoragePool(args.ip, args.port)
    try:
        obj.storagepool_deregister(args.name, args.storagesystem,
                                         args.serialnumber,
                                         args.type)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool deregister failed: Storagepool " +
                           args.name + " : Not Found\n" + e.err_text)
        else:
            raise e


# STORAGEPOOL Show routines

def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser('show',
                description='ViPR Storagepool Show CLI usage.',
                parents=[common_parser],
                conflict_handler='resolve')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name',
                                help='Name of storagepool',
                                required=True,
                                metavar='poolname',
                                dest='name')

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    arggroup = show_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')
    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    show_parser.set_defaults(func=storagepool_show)


def storagepool_show(args):
    obj = StoragePool(args.ip, args.port)
    try:
        res = obj.storagepool_show(args.name, args.storagesystem,
                                   args.serialnumber,
                                   args.type,
                                   args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool show failed: Storagepool "
                           + args.name + " : Not Found\n" + e.err_text)
        else:
            raise e

# STORAGEPOOL Query routines


def query_parser(subcommand_parsers, common_parser):
    # query command parser
    query_parser = subcommand_parsers.add_parser('query',
                description='ViPR Storagepool Query CLI usage.',
                parents=[common_parser],
                conflict_handler='resolve')

    mandatory_args = query_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name',
    help='Name of storagepool(storage system name/storage pool name)',
    required=True,
    metavar='poolname',
    dest='name')

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    query_parser.set_defaults(func=storagepool_query)


def storagepool_query(args):
    obj = StoragePool(args.ip, args.port)
    try:
        res = obj.storagepool_query(args.name)
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool query failed: " + e.err_text)
        else:
            raise e

# STORAGEPOOL List routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser('list',
        description='ViPR Storagepool List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List storagepools with details',
                             dest='verbose')

    list_parser.add_argument('-long', '-l',
            action='store_true',
            help='List storagepools with more details in tabular form',
            dest='long')

    mandatory_args.add_argument('-type', '-t',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                help='device type',
                                dest='type',
                                required=True)

    arggroup = list_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-serialnumber', '-sn',
        help='List storagepools with more details in tabular form',
        dest='serialnumber',
        metavar='serialnumber')

    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storage System',
                          metavar='storagesystem',
                          dest='storagesystem')

    list_parser.set_defaults(func=storagepool_list)


def storagepool_list(args):
    obj = StoragePool(args.ip, args.port)
    try:
        from storagesystem import StorageSystem
        sys = StorageSystem(args.ip, args.port)
        if(args.serialnumber):
            device_id = sys.query_by_serial_number_and_type(args.serialnumber,
                                                            args.type)
        else:
            device = sys.show(name=args.storagesystem, type=args.type)
            device_id = device['id']

        uris = obj.storagepool_list_by_uri(device_id)
        output = []
        for uri in uris:
            result = obj.storagepool_show_by_uri(device_id, uri)

             # adding new column storage tier names assicated with pools
            if("tier_utilization_percentages" in result and
               args.long == True and
               (result['registration_status'] == 'REGISTERED')):
                tierUtilizationPercentages = result[
                                    'tier_utilization_percentages']
    # Go and fetch storage-tiers only if the utilization value is present
    # Assumption here is, if there some utilization value, then
    # such a pool has storage tiers
                if(tierUtilizationPercentages is not None and
                   len(tierUtilizationPercentages) > 0):
                    tiernamelst = []
                    returnlst = []
                    # get list of storage tier objects
                    returnlst = obj.storagepool_get_tiers_by_uri(uri)
                    for item in returnlst:
                        tiernamelst.append(item['name'])
                    result["storage_tiers"] = tiernamelst

            if(result):
                output.append(result)

        if(len(output) > 0):
            if(args.verbose == True):
                return common.format_json_object(output)

            for iter in output:
                from decimal import Decimal
                if(iter['free_gb'] / (1024 * 1024) >= 1):
                    iter['free'] = Decimal(Decimal(iter['free_gb']) / (1024 * 1024))
                    iter['free'] = str(iter['free']) + ' PB'
                elif(iter['free_gb'] / (1024) >= 1):
                    iter['free'] = Decimal(Decimal(iter['free_gb']) / (1024))
                    iter['free'] = str(iter['free']) + ' TB'
                else:
                    iter['free'] = str(iter['free_gb']) + ' GB'

                if(iter['used_gb'] / (1024 * 1024) >= 1):
                    iter['used'] = Decimal(Decimal(iter['used_gb']) / (1024 * 1024))
                    iter['used'] = str(iter['used']) + ' PB'
                elif(iter['used_gb'] / (1024) >= 1):
                    iter['used'] = Decimal(Decimal(iter['used_gb']) / (1024))
                    iter['used'] = str(iter['used']) + ' TB'
                else:
                    iter['used'] = str(iter['used_gb']) + ' GB'

            if(args.long == True):
                for iter in output:
                    if('vpool_set' in iter):
                        vpoolnames = ''
                        for vpooliter in iter['vpool_set']:
                            from virtualpool import VirtualPool
                            obj = VirtualPool(args.ip, args.port)

                            if(args.type in ['isilon', 'vnxfile', 'netapp']):
                                vpooltype = 'file'
                            else:
                                vpooltype = 'block'

                            vpool = obj.vpool_show_uri(vpooltype,
                                                       vpooliter['id'])
                            if(vpool):
                                vpoolnames = vpoolnames + vpool['name'] + ','

                        if(len(vpoolnames) > 0):
                            vpoolnames = vpoolnames[0:len(vpoolnames) - 1]
                        if(vpoolnames):
                            iter['vpool_set'] = vpoolnames
                        else:
                            iter['vpool_set']= 'NULL'

                    if('tagged_varrays' in iter):
                        nbhnames = ''
                        for nbhiter in iter['tagged_varrays']:
                            from virtualarray import VirtualArray
                            obj = VirtualArray(args.ip, args.port)

                            nbh = obj.varray_show(nbhiter)
                            if(nbh):
                                nbhnames = nbhnames + nbh['name'] + ','

                        if(len(nbhnames) > 0):
                            nbhnames = nbhnames[0:len(nbhnames) - 1]

                        if(nbhnames):
                            iter['tagged_varrays'] = nbhnames
                        else:
                            iter['tagged_varrays']= 'NULL'

                from common import TableGenerator
                TableGenerator(output, ['pool_name', 'registration_status',
                                        'free', 'used',
                                        'vpool_set',
                                        'tagged_varrays',
                                        'storage_tiers']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['pool_name',
                                        'registration_status',
                                        'free', 'used']).printTable()

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool list failed: "
                           + e.err_text)
        else:
            raise e


def update_parser(subcommand_parsers, common_parser):
    # register command parser
    update_parser = subcommand_parsers.add_parser('update',
                    description='ViPR Storagepool update CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-type', '-t',
                                help='device type',
                                dest='type',
                                choices=StorageSystem.SYSTEM_TYPE_LIST,
                                required=True)

    arggroup = update_parser.add_mutually_exclusive_group(required=True)

    arggroup.add_argument('-storagesystem', '-ss',
                          help='Name of Storagesystem',
                          dest='storagesystem',
                          metavar='<storagesystemname>')

    arggroup.add_argument('-serialnumber', '-sn',
                          metavar="<serialnumber>",
                          help='Serial Number of the storage system',
                          dest='serialnumber')

    update_parser.add_argument('-name',
                               help='name of pool',
                               dest='name',
                               metavar='name')

    '''update_parser.add_argument('-vpool',
                                help = 'name of vpool to be associated',
                                dest='vpool',
                                metavar='vpool')'''

    update_parser.add_argument('-vaadd',
                               help='name of varrays to be associated',
                               dest='nhadd',
                               metavar='nhadd',
                               nargs="+")

    update_parser.add_argument('-varem',
                               help='name of varrays to be dis-associated',
                               dest='nhrem',
                               metavar='nhrem',
                               nargs="+")

    update_parser.add_argument('-maxresources', '-mr',
                               help='Maximum number of resources',
                               dest='maxresources',
                               metavar='maxresources')

    update_parser.add_argument('-maxpoolutilization', '-mpu',
                               help='Maximum pool utilization',
                               dest='maxpoolutilization',
                               metavar='<maxpoolutilization>')

    update_parser.add_argument('-maxthinpoolsubscription', '-mtps',
                               help='Maximum thin pool subscription',
                               dest='maxthinpoolsubscription',
                               metavar='<maxthinpoolsubscription>')

    update_parser.add_argument('-volumetype',
            help='volume types to be associated with',
            dest='volumetype',
            metavar='volumetype',
            choices=['THIN_AND_THICK', 'THICK_ONLY', 'THIN_ONLY'])

    update_parser.set_defaults(func=storagepool_update)


def storagepool_update(args):
    obj = StoragePool(args.ip, args.port)

    try:
        if(args.maxpoolutilization):
            if ((int(args.maxpoolutilization) > 100) or
                (int(args.maxpoolutilization) < 0)):
                raise SOSError(SOSError.CMD_LINE_ERR,
                "Please ensure max pool utilization is >=0 and <=100")
        if(args.maxthinpoolsubscription):
            if(0 > int(args.maxthinpoolsubscription)):
                raise SOSError(SOSError.CMD_LINE_ERR,
            "Please ensure max thin pool subscription is >=0")
        if(args.maxresources):
            if(0 > int(args.maxresources)):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               "Please ensure max resources is >=0")

        res = obj.storagepool_update(args.storagesystem, args.serialnumber,
                                     args.type, args.name,
                                     args.nhadd, args.nhrem,
                                     args.volumetype, args.maxresources,
                                     args.maxpoolutilization,
                                     args.maxthinpoolsubscription)

    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Storagepool update failed: "
                           + e.err_text)
        else:
            raise e

#
# Storagepool Main parser routine
#


def storagepool_parser(parent_subparser, common_parser):
    # main storagepool parser
    parser = parent_subparser.add_parser('storagepool',
                description='ViPR Storagepool CLI usage',
                parents=[common_parser],
                conflict_handler='resolve',
                help='Operations on Storagepool')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Command')

    # create command parser
    #create_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # deregister command parser
    deregister_parser(subcommand_parsers, common_parser)

    # list command parser
    show_parser(subcommand_parsers, common_parser)

    # register command parser
    register_parser(subcommand_parsers, common_parser)

    update_parser(subcommand_parsers, common_parser)
