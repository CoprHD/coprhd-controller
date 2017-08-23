#!/usr/bin/python

# Copyright (c) 2017 DELL EMC
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
from common import SOSError
import json
from storagesystem import StorageSystem

class Migration(object):

    '''
    The class definition for operations on 'Migration'.
    '''

    SYSTEM_TYPE="vmax"

    # Commonly used URIs for the 'Migration' module
    URI_MIGRATION = '/block/migrations'
    URI_MIGRATION_CREATE = URI_MIGRATION + '/create-environment'
    URI_MIGRATION_REMOVE = URI_MIGRATION + '/remove-environment'
    URI_MIGRATION_GET = URI_MIGRATION + '/{0}'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def run_post(self, source, target, uri):
        '''
        Makes REST API call to create/remove migration environment
        Parameters:
            source: serial number of source system
            source: serial number of target system
			uri: uri
        Returns:
            JSON response payload
        '''

        storage_system = StorageSystem(self.__ipAddr, self.__port)
        source_system_uri = storage_system.query_by_serial_number_and_type(source, self.SYSTEM_TYPE)
        target_system_uri = storage_system.query_by_serial_number_and_type(target, self.SYSTEM_TYPE)

        request = dict()
        request["source_storage_system"] = source_system_uri
        request["target_storage_system"] = target_system_uri
        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "POST", uri, body)
        o = common.json_decode(s)
        return o

    def create(self, source, target):
        '''
        Makes REST API call to create migration environment
        Parameters:
            source: serial number of source system
            source: serial number of target system
        Returns:
            Created migration environment details in JSON response payload
        '''

        return self.run_post(source, target, self.URI_MIGRATION_CREATE)

    def remove(self, source, target):
        '''
        Makes REST API call to remove migration environment
        Parameters:
            source: serial number of source system
            source: serial number of target system
        Returns:
            JSON response payload
        '''

        return self.run_post(source, target, self.URI_MIGRATION_REMOVE)

    def list(self):
        '''
        Makes REST API call and retrieves migrations
        Parameters: None
        Returns:
            List of migration UUIDs in JSON response payload
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    self.URI_MIGRATION, None)
        o = common.json_decode(s)

        if("migration" in o):
            return common.get_list(o, 'migration')
        return []

    def query(self, name):
        '''
        This function will take migration name/id as input, return migration id.
        parameters:
           name : Name/id of the migratoin.
        return
            return with id of the migratoin.
         '''
        if (common.is_uri(name)):
            return name

        migrationlist = self.list()
        for migration in migrationlist:
            if (migration['name'] == name):
                return migration['id']
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Migration " + name + ": not found")

    def show(self, name, xml=False):
        '''
        Makes REST API call and retrieves migration details based on UUID
        Parameters:
            uri: UUID of migration
        Returns:
            migration details in JSON response payload
        '''
        uri = self.query(name)
        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "GET", self.URI_MIGRATION_GET.format(uri),
                             None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET", self.URI_MIGRATION_GET.format(uri), None)
        o = common.json_decode(s)
        return o

# parser
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
                    description='ViPR migration environment create CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Create migration environment')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-s', '-source',
                                metavar='<source>',
                                dest='source',
                                help='Serial number of the source storage system',
                                required=True)
    mandatory_args.add_argument('-t', '-target',
                                metavar='<target>',
                                dest='target',
                                help='Serial number of the target storage system',
                                required=True)
    create_parser.set_defaults(func=create)

def create(args):
    obj = Migration(args.ip, args.port)
    try:
        obj.create(args.source, args.target)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                            SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code,
                           "Migration environment create failed: " + e.err_text)
        else:
            raise e

def remove_parser(subcommand_parsers, common_parser):
    # remove command parser
    remove_parser = subcommand_parsers.add_parser('remove',
                description='ViPR migration environment remove CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Remove migration environment')
    mandatory_args = remove_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-s', '-source',
                                metavar='<source>',
                                dest='source',
                                help='Serial number of the source storage system',
                                required=True)
    mandatory_args.add_argument('-t', '-target',
                                metavar='<target>',
                                dest='target',
                                help='Serial number of the target storage system',
                                required=True)
    remove_parser.set_defaults(func=remove)

def remove(args):
    obj = Migration(args.ip, args.port)

    try:
        obj.remove(args.source, args.target)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Migration environment remove failed: " + e.err_text)
        else:
            raise e

# list command parser
def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
                        description='ViPR migration list CLI usage.',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='List all migrations')
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List migrations with details',
                             action='store_true')
    list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             help='List migrations in table format',
                             action='store_true')
    list_parser.set_defaults(func=list)

def list(args):
    obj = Migration(args.ip, args.port)

    try:
        from common import TableGenerator
        migrations = obj.list()
        records = []
        for migration in migrations:
            migration_uri = migration['id']
            migration_detail = obj.show(migration_uri)
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
        raise e


# show command parser
def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                        description='ViPR migration show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show migration details')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<id>',
                                dest='name',
                                help='Name or ID of migration',
                                required=True)
    show_parser.set_defaults(func=show)

def show(args):
    obj = Migration(args.ip, args.port)
    try:
        res = obj.show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e

# Migration main parser routine
def migration_parser(parent_subparser, common_parser):
    # main migration parser

    parser = parent_subparser.add_parser('migration',
                                         description='ViPR migration CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Migration')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # remove command parser
    remove_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)
