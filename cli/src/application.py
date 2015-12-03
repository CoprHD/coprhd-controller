#!/usr/bin/python

# Copyright (c) 2012-15 EMC Corporation
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
import tag


class Application(object):

    '''
    The class definition for operations on 'Application'.
    '''

    # Commonly used URIs for the 'Application' module
    URI_APPLICATION_LIST = '/applications/block'
    URI_APPLICATION = '/applications/block/{0}'
    URI_DEACTIVATE = URI_APPLICATION + '/deactivate'
    URI_TAG_APPLICATION = URI_APPLICATION + "/tags"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        
    def application_create(self, name, description, roles):
        '''
        Makes REST API call to create application
        Parameters:
            name: name of application
            description: description for the application
            roles: comma separated list of roles for the application
        Returns:
            Created application details in JSON response payload
        '''
        request = dict()
        request["name"] = name
        request["description"] = description
        request["roles"] = roles.split(',')

        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "POST",
                    Application.URI_APPLICATION_LIST, body)
        o = common.json_decode(s)
        return o


    def application_list(self):
        '''
        Makes REST API call and retrieves applications 
        Parameters: None
        Returns:
            List of application UUIDs in JSON response payload
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    Application.URI_APPLICATION_LIST, None)
        o = common.json_decode(s)

        if("application" in o):
            return common.get_list(o, 'application')
        return []

    def application_show_by_uri(self, uri, xml=False):
        '''
        Makes REST API call and retrieves application details based on UUID
        Parameters:
            uri: UUID of application
        Returns:
            application details in JSON response payload
        '''
        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "GET", Application.URI_APPLICATION.format(uri),
                             None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET", Application.URI_APPLICATION.format(uri), None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')
        if(inactive == True):
            return None

        return o

    def application_show(self, name, xml=False):
        '''
        Retrieves application details based on application name
        Parameters:
            name: name of the application
        Returns:
            application details in JSON response payload
        '''
        application_uri = self.application_query(name)
        application_detail = self.application_show_by_uri(application_uri, xml)
        return application_detail
    
    
    def application_query(self, name):
        '''
        Retrieves UUID of application based on its name
        Parameters:
            name: name of application
        Returns: UUID of application
        Throws:
            SOSError - when application name is not found
        '''
        if (common.is_uri(name)):
            return name
        
        try:
            applications = self.application_list()
            for app in applications:
                app_detail = self.application_show_by_uri(app['id']);
                if(app_detail and app_detail['name'] == name):
                    return app_detail['id']
            raise SOSError(SOSError.NOT_FOUND_ERR,
                            'Application: ' + name + ' not found')
        except SOSError as e:
            raise e
            

    def application_delete_by_uri(self, uri):
        '''
        Deletes a application based on application UUID
        Parameters:
            uri: UUID of application
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "POST", Application.URI_DEACTIVATE.format(uri), None)
        return

    def application_delete(self, name):
        '''
        Deletes a application based on application name
        Parameters:
            name: name of application
        '''
        application_uri = self.application_query(name)
        return self.application_delete_by_uri(application_uri)

    def update(self, name, new_name, new_description):
        '''
        Makes REST API call and updates application name and description
        Parameters:
            name: name of application
            new_name: new name of the application
            new_description: new description of the application

        Returns:
            List of application resources in response payload
        '''
        application_uri = self.application_query(name)

        request = dict()
        if(new_name and len(new_name) > 0):
            request["name"] = new_name
        if(new_description and len(new_description) > 0):
            request["description"] = new_description

        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                    Application.URI_APPLICATION.format(application_uri), body)


    def tag(self, name, addtags, removetags):
        '''
        Makes REST API call and tags application
        Parameters:
            name: name of application
            addtags : tags to be added
            removetags : tags to be removed

        Returns:
            response of the tag operation
        '''
        application_uri = self.application_query(name)

        return (
            tag.tag_resource(self.__ipAddr, self.__port,
                              Application.URI_TAG_APPLICATION,
                               application_uri, addtags, removetags)
        )



def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
                    description='ViPR Application Create CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Create an application')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Application',
                                required=True)
    mandatory_args.add_argument('-r', '-roles',
                               metavar='<roles>',
                               dest='roles',
                               help='[COPY | DR]',
                                required=True)
    create_parser.add_argument('-d', '-description',
                               metavar='<description>',
                               dest='description',
                               help='description for application')
    create_parser.set_defaults(func=application_create)


def application_create(args):
    obj = Application(args.ip, args.port)
    try:
        obj.application_create(args.name, args.description, args.roles)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                            SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code,
                           "Application create failed: " + e.err_text)
        else:
            raise e


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                description='ViPR Application Delete CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Delete an application')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Application',
                                required=True)
    delete_parser.set_defaults(func=application_delete)


def application_delete(args):
    obj = Application(args.ip, args.port)
    
    try:
        obj.application_delete(args.name)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Application delete failed: " + e.err_text)
        else:
            raise e


# show command parser
def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                        description='ViPR Application Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show application details')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of application',
                                required=True)
    show_parser.set_defaults(func=application_show)


def application_show(args):
    obj = Application(args.ip, args.port)
    try:
        res = obj.application_show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e
    



# list command parser


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
                        description='ViPR Application List CLI usage.',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='Lists applications')
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List applications with details',
                             action='store_true')
    list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             help='List applications in table format',
                             action='store_true')
    list_parser.set_defaults(func=application_list)


def application_list(args):
    obj = Application(args.ip, args.port)

    try:
        from common import TableGenerator
        applications = obj.application_list()
        records = []
        for application in applications:
            application_uri = application['id']
            app_detail = obj.application_show_by_uri(application_uri)
            if(app_detail):
                records.append(app_detail)

        if(len(records) > 0):
            if(args.verbose == True):
                return common.format_json_object(records)

            elif(args.largetable == True):
                TableGenerator(records, ['name', 'description',
                                          'roles', 'tags']).printTable()
            else:
                TableGenerator(records, ['name']).printTable()

        else:
            return

    except SOSError as e:
        raise e


# update application command parser
def update_application_parser(subcommand_parsers, common_parser):
    update_application_parser = subcommand_parsers.add_parser('update',
                        description='ViPR update application CLI usage',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='Show application details')
    mandatory_args = update_application_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of existing application',
                                required=True)
    update_application_parser.add_argument('-np', '-newname',
                                       metavar='<newname>',
                                       dest='newname',
                                       help='New name of application')
    update_application_parser.add_argument('-o', '-description',
                                       metavar='<newowner>',
                                       dest='description',
                                       help='New description of application')

    update_application_parser.set_defaults(func=update_application)


def update_application(args):

    if(args.newname is None and args.description is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
            "viprcli application update: error: at least one of " +
            "the arguments -np/-newname -o/-description is required")
    obj = Application(args.ip, args.port)
    try:
        obj.update(args.name, args.newname,
                    args.description)
    except SOSError as e:
        raise e

def tag_application_parser(subcommand_parsers, common_parser):
    tag_application_parser = subcommand_parsers.add_parser('tag',
                    description='ViPR Tag application CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Update Tags of application')
    mandatory_args = tag_application_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of existing application',
                                required=True)

    tag.add_tag_parameters(tag_application_parser)

    tag_application_parser.set_defaults(func=tag_application)


def tag_application(args):

    if(args.add is None and args.remove is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
                        "viprcli application tag: error: at least one of " +
                       "the arguments -add -remove is required")
    obj = Application(args.ip, args.port)
    try:
        obj.tag(args.name,
                args.add, args.remove)
    except SOSError as e:
        common.format_err_msg_and_raise("application", "tag", e.err_text,
                                         e.err_code)


# Application Main parser routine
def application_parser(parent_subparser, common_parser):
    # main application parser

    parser = parent_subparser.add_parser('application',
                                         description='ViPR Application CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Application')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # create command parser
    update_application_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # application tag parser
    tag_application_parser(subcommand_parsers, common_parser)
