#!/usr/bin/python
# Copyright (c)2016 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common

SCHEDULED_EVENT_BASE_URI = "/catalog/events"

def get_scheduled_event(args):
    (body, headers) = common.service_json_request(
        args.ip, args.port, "GET",
        "{}/{}".format(SCHEDULED_EVENT_BASE_URI, args.event_id),
        None, None)

    print(common.to_pretty_json(body))

def get_event_parser(subcommand_parsers, common_parser):

    parser = subcommand_parsers.add_parser(
        'get',
        description='ViPR Get Scheduled Event CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Get a Scheduled Event by Id')

    mandatory_args = parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-id',
                                help='The Id of Event',
                                dest='event_id',
                                required=True)

    parser.set_defaults(func=get_scheduled_event)

def schedule_event_parser(parent_subparser, common_parser):
    """
    truststore Main parser routine
    Args:
        parent_subparser:
        common_parser:

    Returns:

    """

    # root parser of scheduled event
    parser = parent_subparser.add_parser(
        'schedevent',
        description='ViPR Scheduled Event CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Scheduled Event')

    # all sub parsers about scheduled event
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    get_event_parser(subcommand_parsers, common_parser)

