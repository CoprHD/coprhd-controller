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
from volume import Volume


class VolumeGroup(object):

    '''
    The class definition for operations on 'VolumeGroup'.
    '''

    # Commonly used URIs for the 'VolumeGroup' module
    URI_VOLUME_GROUP_LIST = '/volume-groups/block'
    URI_VOLUME_GROUP = '/volume-groups/block/{0}'
    URI_VOLUME_GROUP_VOLUMES = '/volume-groups/block/{0}/volumes'
    URI_VOLUME_GROUP_CHILDREN = '/volume-groups/block/{0}/volume-groups'
    URI_DEACTIVATE = URI_VOLUME_GROUP + '/deactivate'
    URI_TAG_VOLUME_GROUP = URI_VOLUME_GROUP + "/tags"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port
        
    def create(self, name, description, roles, parent):
        '''
        Makes REST API call to create volume group
        Parameters:
            name: name of volume group
            description: description for the volume group
            roles: comma separated list of roles for the volume group
        Returns:
            Created volume group details in JSON response payload
        '''
        request = dict()
        request["name"] = name
        request["description"] = description
        request["roles"] = roles.split(',')
        request["parent"] = parent

        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "POST",
                    VolumeGroup.URI_VOLUME_GROUP_LIST, body)
        o = common.json_decode(s)
        return o


    def list(self):
        '''
        Makes REST API call and retrieves volume groups 
        Parameters: None
        Returns:
            List of volume group UUIDs in JSON response payload
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    VolumeGroup.URI_VOLUME_GROUP_LIST, None)
        o = common.json_decode(s)

        if("volume_group" in o):
            return common.get_list(o, 'volume_group')
        return []

    def show_by_uri(self, uri, xml=False):
        '''
        Makes REST API call and retrieves volume group details based on UUID
        Parameters:
            uri: UUID of volume group
        Returns:
            volume group details in JSON response payload
        '''
        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                            "GET", VolumeGroup.URI_VOLUME_GROUP.format(uri),
                             None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET", VolumeGroup.URI_VOLUME_GROUP.format(uri), None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')
        if(inactive == True):
            return None

        return o

    def show(self, name, xml=False):
        '''
        Retrieves volume group details based on volume group name
        Parameters:
            name: name of the volume group
        Returns:
            volume group details in JSON response payload
        '''
        volume_group_uri = self.query_by_name(name)
        volume_group_detail = self.show_by_uri(volume_group_uri, xml)
        return volume_group_detail
    
    
    def query_by_name(self, name):
        '''
        Retrieves UUID of volume group based on its name
        Parameters:
            name: name of volume group
        Returns: UUID of volume group
        Throws:
            SOSError - when volume group name is not found
        '''
        if (common.is_uri(name)):
            return name
        
        try:
            volume_groups = self.list()
            for app in volume_groups:
                app_detail = self.show_by_uri(app['id']);
                if(app_detail and app_detail['name'] == name):
                    return app_detail['id']
            raise SOSError(SOSError.NOT_FOUND_ERR,
                            'VolumeGroup: ' + name + ' not found')
        except SOSError as e:
            raise e
            

    def delete_by_uri(self, uri):
        '''
        Deletes a volume group based on volume group UUID
        Parameters:
            uri: UUID of volume group
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "POST", VolumeGroup.URI_DEACTIVATE.format(uri), None)
        return

    def delete_by_name(self, name):
        '''
        Deletes a volume group based on volume group name
        Parameters:
            name: name of volume group
        '''
        volume_group_uri = self.query_by_name(name)
        return self.delete_by_uri(volume_group_uri)

    def update(self, name, new_name, new_description, add_volumes, cg_id, rg_name, remove_volumes, parent):
        '''
        Makes REST API call and updates volume group name and description
        Parameters:
            name: name of volume group
            new_name: new name of the volume group
            new_description: new description of the volume group

        Returns:
            List of volume group resources in response payload
        '''
        volume_group_uri = self.query_by_name(name)

        request = dict()
        if(new_name and len(new_name) > 0):
            request["name"] = new_name
        if(new_description and len(new_description) > 0):
            request["description"] = new_description
        if(parent and len(parent) > 0):
            request["parent"] = parent
        if(add_volumes and len(add_volumes) > 0):
            add_vols = dict()
            add_vols["volume"] = add_volumes.split(',')
            if(cg_id and len(cg_id) > 0):
                add_vols["consistency_group"] = cg_id
            if(rg_name and len(rg_name) > 0):
                add_vols["replication_group_name"] = rg_name
            request["add_volumes"] = add_vols
        if(remove_volumes and len(remove_volumes) > 0):
            remove_vols = dict()
            remove_vols["volume"] = remove_volumes.split(',')
            request["remove_volumes"] = remove_vols

        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                    VolumeGroup.URI_VOLUME_GROUP.format(volume_group_uri), body)


    def tag(self, name, addtags, removetags):
        '''
        Makes REST API call and tags volume group
        Parameters:
            name: name of volume group
            addtags : tags to be added
            removetags : tags to be removed

        Returns:
            response of the tag operation
        '''
        volume_group_uri = self.query_by_name(name)

        return (
            tag.tag_resource(self.__ipAddr, self.__port,
                              VolumeGroup.URI_TAG_VOLUME_GROUP,
                               volume_group_uri, addtags, removetags)
        )
    
    #Routine for volume group volumes 
    def volume_show(self, name ,xml=False):
        
        volume_group_uri = self.query_by_name(name)
        
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                        VolumeGroup.URI_VOLUME_GROUP_VOLUMES.format(volume_group_uri), None)
        o = common.json_decode(s)
     
        
        return o
    
    #Routine for children volume groups for a volume group 
    def volume_group_children_show(self, name ,xml=False):
        
        volume_group_uri = self.query_by_name(name)
        
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                        VolumeGroup.URI_VOLUME_GROUP_CHILDREN.format(volume_group_uri), None)
        o = common.json_decode(s)
     
        
        return o


#SHOW resource parser

def show_volume_group_volume_parser(subcommand_parsers, common_parser):
    volume_group_volume_parser = subcommand_parsers.add_parser('show-volumes',
                        description='ViPR Project Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show volume group volumes')
    volume_group_volume_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = volume_group_volume_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of volume group',
                                required=True)
    volume_group_volume_parser.set_defaults(func=volume_group_volume_show)

def volume_group_volume_show(args):
    obj = VolumeGroup(args.ip, args.port)
    try:
        res = obj.volume_show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e

def show_volume_group_children_parser(subcommand_parsers, common_parser):
    volume_group_volume_parser = subcommand_parsers.add_parser('show-children',
                        description='ViPR Project Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show volume group child volume groups')
    volume_group_volume_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = volume_group_volume_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of volume group',
                                required=True)
    volume_group_volume_parser.set_defaults(func=volume_group_children_show)

def volume_group_children_show(args):
    obj = VolumeGroup(args.ip, args.port)
    try:
        res = obj.volume_group_children_show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e


def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser('create',
                    description='ViPR VolumeGroup Create CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Create a volume group')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of VolumeGroup',
                                required=True)
    mandatory_args.add_argument('-r', '-roles',
                               metavar='<roles>',
                               dest='roles',
                               help='[COPY | DR]',
                                required=True)
    create_parser.add_argument('-d', '-description',
                               metavar='<description>',
                               dest='description',
                               help='description for volume group')
    create_parser.add_argument('-pa', '-parent',
                               metavar='<parent>',
                               dest='parent',
                               help='parent volume group for volume group')
    create_parser.set_defaults(func=create)


def create(args):
    obj = VolumeGroup(args.ip, args.port)
    try:
        obj.create(args.name, args.description, args.roles, args.parent)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                            SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code,
                           "VolumeGroup create failed: " + e.err_text)
        else:
            raise e


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser('delete',
                description='ViPR VolumeGroup Delete CLI usage.',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Delete a volume group')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of VolumeGroup',
                                required=True)
    delete_parser.set_defaults(func=delete_by_name)


def delete_by_name(args):
    obj = VolumeGroup(args.ip, args.port)
    
    try:
        obj.delete_by_name(args.name)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "VolumeGroup delete failed: " + e.err_text)
        else:
            raise e


# show command parser
def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
                        description='ViPR VolumeGroup Show CLI usage.',
                                                parents=[common_parser],
                                                conflict_handler='resolve',
                                                help='Show volume group details')
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of volume group',
                                required=True)
    show_parser.set_defaults(func=show)

def show(args):
    obj = VolumeGroup(args.ip, args.port)
    try:
        res = obj.show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e
    
# list command parser


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
                        description='ViPR VolumeGroup List CLI usage.',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='Lists volume groups')
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List volume groups with details',
                             action='store_true')
    list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             help='List volume groups in table format',
                             action='store_true')
    list_parser.set_defaults(func=list)


def list(args):
    obj = VolumeGroup(args.ip, args.port)

    try:
        from common import TableGenerator
        volume_groups = obj.list()
        records = []
        for volume_group in volume_groups:
            volume_group_uri = volume_group['id']
            app_detail = obj.show_by_uri(volume_group_uri)
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


# update volume group command parser
def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('update',
                        description='ViPR update volume group CLI usage',
                        parents=[common_parser],
                        conflict_handler='resolve',
                        help='Update volume group properties')
    mandatory_args = update_parser.add_argument_group(
                                            'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of existing volume group',
                                required=True)
    update_parser.add_argument('-nn', '-newname',
                                       metavar='<newname>',
                                       dest='newname',
                                       help='New name of volume group')
    update_parser.add_argument('-d', '-description',
                                       metavar='<description>',
                                       dest='description',
                                       help='New description of volume group')
    update_parser.add_argument('-r', '-remove_volumes',
                                       metavar='<tenant/project/volume_label | volume_uid,...>',
                                       dest='remove_volumes',
                                       help='A list of volumes to remove from the volume group')
    update_parser.add_argument('-a', '-add_volumes',
                                       metavar='<tenant/project/volume_label | volume_uid,...>',
                                       dest='add_volumes',
                                       help='A list of volumes to add to the volume group')
    update_parser.add_argument('-cg', '-consistency_group',
                                       metavar='<consistency_group>',
                                       dest='consistency_group',
                                       help='A consistency group for adding volumes to the volume group')
    update_parser.add_argument('-rg', '-replication_group',
                                       metavar='<replication_group>',
                                       dest='replication_group',
                                       help='A replication group name on the array where volumes will be added to')
    update_parser.add_argument('-pa', '-parent',
                                       metavar='<parent>',
                                       dest='parent',
                                       help='A parent volume group for the volume group')

    update_parser.set_defaults(func=update)


def update(args):

    if(args.newname is None and args.description is None and args.add_volumes is None and args.remove_volumes is None and args.parent is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
            "viprcli volume group update: error: at least one of " +
            "the arguments -np/-newname -d/-description -a/-add_volumes " +
            " -r/-remove_volumes is required")
     
    add_vols = []
    if(args.add_volumes and len(args.add_volumes) > 0):
        for item in args.add_volumes.split(','):
            if (common.is_uri(item)):
                add_vols.append(item)
            else:
                vol = Volume(args.ip, args.port)
                volid = vol.show(item,  False, False)['id']
                add_vols.append(volid)
                    
    rem_vols = []
    if(args.remove_volumes and len(args.remove_volumes) > 0):
        for item in args.remove_volumes.split(','):
            if (common.is_uri(item)):
                rem_vols.append(item)
            else:
                vol = Volume(args.ip, args.port)
                try:
                    volid = vol.show(item,  False, False)['id']
                    rem_vols.append(volid)
                except:
                    continue
                    
    obj = VolumeGroup(args.ip, args.port)
    try:
        obj.update(args.name, args.newname,
                    args.description, ",".join(add_vols), args.consistency_group, args.replication_group, ",".join(rem_vols), args.parent)
    except SOSError as e:
        raise e

def tag_parser(subcommand_parsers, common_parser):
    tag_parser = subcommand_parsers.add_parser('tag',
                    description='ViPR Tag volume group CLI usage',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Update Tags of volume group')
    mandatory_args = tag_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of existing volume group',
                                required=True)

    tag.add_tag_parameters(tag_parser)

    tag_parser.set_defaults(func=tag_volume_group)


def tag_volume_group(args):

    if(args.add is None and args.remove is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
                        "viprcli volume group tag: error: at least one of " +
                       "the arguments -add -remove is required")
    obj = VolumeGroup(args.ip, args.port)
    try:
        obj.tag(args.name,
                args.add, args.remove)
    except SOSError as e:
        common.format_err_msg_and_raise("volumegroup", "tag", e.err_text,
                                         e.err_code)


# VolumeGroup Main parser routine
def volume_group_parser(parent_subparser, common_parser):
    # main volume group parser

    parser = parent_subparser.add_parser('volumegroup',
                                         description='ViPR VolumeGroup CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on VolumeGroup')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # create command parser
    update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # volume group tag parser
    tag_parser(subcommand_parsers, common_parser)
    
    # list volumes parser
    show_volume_group_volume_parser(subcommand_parsers, common_parser)
    
    # list child volume groups parser
    show_volume_group_children_parser(subcommand_parsers, common_parser)
