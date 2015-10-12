#!/usr/bin/python

# Copyright (c) 2013-14 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

'''
This module contains the computeimage implementation
'''

import common
import json
from common import SOSError
from urihelper import singletonURIHelperInstance


class ComputeImage(object):

    URI_COMPUTE_IMAGE = "/compute/images"
    URI_COMPUTE_IMAGE_ID = URI_COMPUTE_IMAGE + "/{0}"
    URI_COMPUTE_IMAGE_DELETE = URI_COMPUTE_IMAGE_ID + "/deactivate"

    IMAGE_TYPE_LIST = ['esx', 'linux']
    '''
    Constructor: takes IP address and port of the ViPR instance. These are
    needed to make http requests for REST API
    '''

    def __init__(self, ipAddr, port):
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Create compute image from image URL or existing installable image URN.
        name    : compute imagename
        imageurl: url path of the image
    '''

    def create_computeimage(self, imagename, imageurl):

        parms = {
                'name': imagename,
                'image_url': imageurl
                }
        body = json.dumps(parms)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "POST",
                                self.URI_COMPUTE_IMAGE,
                                body)

        return common.json_decode(s)
    '''
        get compute image object detail by uri
        uri: compute image uri
    '''

    def computeimage_show_by_uri(self, uri, xml=False):

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            ComputeImage.URI_COMPUTE_IMAGE_ID.format(uri),
            None, None, xml)
        if(not xml):
            o = common.json_decode(s)

            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o
    '''
    Show the details of compute image
        imagename :compute image name
        return : computeimage details.
    '''
    def show_computeimage(self, imagename, xml=False):
        #currently search is not implemented in apisvc
        uri = self.query_computeimage(imagename)
        return self.computeimage_show_by_uri(uri, xml)

    '''
    Updates an already present compute image.
        imagename :new compute image name
        imageurl  :compute image URN.
    '''
    def update_computeimage(self, imagename, imageurl, newlabel):
        params = {}
        #This tweak is done in order sync the behavior with UI	
        params['name']=imagename	
        
        if(newlabel):
            params['name'] = newlabel
        if(imageurl):
            params['image_url'] = imageurl

        uri = self.query_computeimage(imagename)
        body = json.dumps(params)
        (s, h) = common.service_json_request(self.__ipAddr,
                                self.__port, "PUT",
                                self.URI_COMPUTE_IMAGE_ID.format(uri),
                                body)
        return common.json_decode(s)
    '''
    Returns a list of all compute images.
        imagetype: type of image or iso (esx, linux)
    '''
    def list_computeimage(self, imagetype=None):
        (s, h) = common.service_json_request(
                            self.__ipAddr, self.__port,
                            "GET",
                            ComputeImage.URI_COMPUTE_IMAGE,
                            None)

        o = common.json_decode(s)
        return o['compute_image']

    '''
    Delete compute image attribute.
        imagename: name of computeimage
    '''
    def delete_computeimage(self, imagename, forcedelete):
        uri = self.query_computeimage(imagename)
        ci_restapi = ComputeImage.URI_COMPUTE_IMAGE_DELETE.format(uri)
        if(forcedelete):
            ci_restapi = ci_restapi + "?force=true"

        (s, h) = common.service_json_request(self.__ipAddr,
                                             self.__port,
                        "POST", ci_restapi, None)
        return common.json_decode(s)

    def query_computeimage(self, imagename):
        '''
        Returns the UID of the Computesystem specified by the name
        '''

        computeimages = self.list_computeimage()

        for computeimage in computeimages:
            if (computeimage['name'] == imagename):
                return computeimage['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "computeimage " + imagename + ": not found")
        return
    #CLASS - END


#Common parameters for san_fabric parser.
def compute_image_sub_common_parser(cc_common_parser):
    mandatory_args = cc_common_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimagename>',
                                dest='name',
                                help='Name of computeimage',
                                required=True)
    mandatory_args.add_argument('-imageurl', '-url',
                                  metavar='<imageurl>',
                                  dest='imageurl',
                                  help='url path of the image',
                                  required=True)


def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Compute Image Create cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create an computeimage')
    compute_image_sub_common_parser(create_parser)
    create_parser.set_defaults(func=computeimage_create)


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Compute Image show cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an computeimage')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimagename>',
                                dest='name',
                                help='Name of computeimage',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')
    show_parser.set_defaults(func=computeimage_show)


def update_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Compute Image update cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update an computeimage')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimagename>',
                                dest='name',
                                help='Name of computeimage',
                                required=True)
    update_parser.add_argument('-imageurl', '-url',
                                  metavar='<imageurl>',
                                  dest='imageurl',
                                  help='url path of the image')
    update_parser.add_argument('-label', '-l',
                                metavar='<label>',
                                dest='label',
                                help='new name to ComputerImage',
                                required=False)
    update_parser.set_defaults(func=computeimage_update)


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Compute Image list cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='return list of an computeimage')
    list_parser.add_argument('-imagetype', '-t',
                                  metavar='<imagetype>',
                                  dest='imagetype',
                                  choices=ComputeImage.IMAGE_TYPE_LIST,
                                  help='type of the image')
    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List ComputeSystems with details',
                             dest='verbose')
    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List ComputeSystems with more details in tabular form',
        dest='long')
    list_parser.set_defaults(func=computeimage_list)


def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Compute Image delete cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete an computeimage')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<computeimagename>',
                                dest='name',
                                help='Name of computeimage',
                                required=True)
    delete_parser.add_argument('-forcedelete', '-fd',
                             action='store_true',
                             help='Delete computeImage forecibly',
                             dest='forcedelete')

    delete_parser.set_defaults(func=computeimage_delete)


def computeimage_create(args):
    try:
        obj = ComputeImage(args.ip, args.port)
        obj.create_computeimage(args.name, args.imageurl)

    except SOSError as e:
        raise common.format_err_msg_and_raise("create", "computeimage",
                                              e.err_text, e.err_code)


def computeimage_show(args):
    try:
        obj = ComputeImage(args.ip, args.port)
        res = obj.show_computeimage(args.name, args.xml)
        if(args.xml):
            return common.format_xml(res)
        return common.format_json_object(res)
    except SOSError as e:
        raise common.format_err_msg_and_raise("show", "computeimage",
                                              e.err_text, e.err_code)


def computeimage_update(args):
    try:
        obj = ComputeImage(args.ip, args.port)
        obj.update_computeimage(args.name, args.imageurl, args.label)

    except SOSError as e:
        raise common.format_err_msg_and_raise("update", "computeimage",
                                              e.err_text, e.err_code)


def computeimage_list(args):
    try:
        obj = ComputeImage(args.ip, args.port)
        uris = obj.list_computeimage(args.imagetype)

        output = []
        for uri in uris:

            temp = obj.computeimage_show_by_uri(uri['id'], False)
            if(temp):
                output.append(temp)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(output,
                               ['module/name', 'image_name',
                                'image_type',
                                'compute_image_status',
                                'image_url']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['module/name','image_name',
                                        'image_type',
                                        'compute_image_status',
                                        'last_import_status_message'
                                        ]).printTable()
    except SOSError as e:
        raise common.format_err_msg_and_raise("list", "computeimage",
                                              e.err_text, e.err_code)


def computeimage_delete(args):
    try:
        obj = ComputeImage(args.ip, args.port)
        obj.delete_computeimage(args.name, args.forcedelete)

    except SOSError as e:
        raise common.format_err_msg_and_raise("delete", "computeimage",
                                              e.err_text, e.err_code)


#
# ComputeImage Main parser routine
#
def computeimage_parser(parent_subparser, common_parser):

    # main export group parser
    parser = parent_subparser.add_parser(
        'computeimage',
        description='ViPR Compute Image cli usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Compute Image')
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
    update_parser(subcommand_parsers, common_parser)
