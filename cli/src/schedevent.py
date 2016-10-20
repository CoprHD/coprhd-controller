#!/usr/bin/python
# Copyright (c)2016 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import common

SCHEDULED_EVENT_BASE_URI = "/catalog/events"

def get_scheduled_event(args):
    (body, headers) = common.service_json_request(
        args.ip, args.port, "GET",
        "{}/{}".format(SCHEDULED_EVENT_BASE_URI, args.event_id),
        None, None)

    print(common.to_pretty_json(body))

def create_scheduled_event(args):
    """
    Create a scheduled event from a file in JSON format. For example:
    {
      "scheduleInfo": {
        "hourOfDay": 0,
        "minuteOfHour": 0,
        "durationLength": 3600,
        "cycleType": "DAILY",
        "cycleFrequency": 1,
        "sectionsInCycle": [

        ],
        "startDate": "2016-08-05",
        "reoccurrence": 10,
        "endDate": null,
        "dateExceptions": null
      },
      "orderCreateParam": {
        "parameters": [
          {
            "label": "name",
            "value": "\"app4\"",
            "friendlyLabel": null,
            "friendlyValue": null,
            "userInput": true,
            "encrypted": false
          },
          {
            "label": "description",
            "value": "\"asdfa\"",
            "friendlyLabel": null,
            "friendlyValue": null,
            "userInput": true,
            "encrypted": false
          }
        ],
        "catalogService": "urn:storageos:CatalogService:6a87c225-e043-4e94-b985-c6a09c151750:vdc1",
        "tenantId": "urn:storageos:TenantOrg:3942b27e-8629-4ac1-93c3-3549c245a1ed:global",
        "scheduledEventId": null,
        "scheduledTime": null
      }
    }

    Args:
        args:

    Returns:

    """

    create_event_payload = load_event_payload_from_file(args.event_file)

    (body, headers) = common.service_json_request(
        args.ip, args.port, "POST",
        "{}".format(SCHEDULED_EVENT_BASE_URI),
        create_event_payload, None)

    print("Scheduled Event is created successfully. The id is {}".format(json.loads(body)['id']))

def update_scheduled_event(args):
    """
    Update a scheduled event. The event is defined in a file in JSON format. Here is an example:
    {
      "scheduleInfo": {
        "hourOfDay": 0,
        "minuteOfHour": 0,
        "durationLength": 3600,
        "cycleType": "DAILY",
        "cycleFrequency": 1,
        "sectionsInCycle": [

        ],
        "startDate": "2016-08-05",
        "reoccurrence": 12,
        "endDate": null,
        "dateExceptions": null
      }
    }

    Args:
        args:

    Returns:

    """
    update_payload = load_event_payload_from_file(args.event_file)

    (body, headers) = common.service_json_request(
        args.ip, args.port, "PUT",
        "{}/{}".format(SCHEDULED_EVENT_BASE_URI, args.event_id),
        update_payload, None)

    print("Scheduled Event is updated successfully.")

def delete_scheduled_event(args):
    common.service_json_request(
        args.ip, args.port, "POST",
        "{}/{}/deactivate".format(SCHEDULED_EVENT_BASE_URI, args.event_id),
        None, None)

    print("Scheduled Event is deleted successfully.")

def cancel_scheduled_event(args):
    common.service_json_request(
        args.ip, args.port, "POST",
        "{}/{}/cancel".format(SCHEDULED_EVENT_BASE_URI, args.event_id),
        None, None)

    print("Scheduled Event is canceled successfully.")


# Private functions
def load_event_payload_from_file(path):
    with open(path) as event_file:
        return json.dumps(json.load(event_file))


# Sub command parsers
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


def create_event_parser(subcommand_parsers, common_parser):

    parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Create Scheduled Event CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a Scheduled Event from a file in JSON format')

    mandatory_args = parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-f',
                                help='The path of the file',
                                dest='event_file',
                                required=True)

    parser.set_defaults(func=create_scheduled_event)


def update_event_parser(subcommand_parsers, common_parser):
    parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Update Scheduled Event CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a Scheduled Event from a file in JSON format')

    mandatory_args = parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-id',
                                help='The Id of the event to be updated',
                                dest='event_id',
                                required=True)

    mandatory_args.add_argument('-f',
                                help='The path of the file',
                                dest='event_file',
                                required=True)

    parser.set_defaults(func=update_scheduled_event)


def delete_event_parser(subcommand_parsers, common_parser):
    parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Delete Scheduled Event CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a Scheduled Event by Id')

    mandatory_args = parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-id',
                                help='The Id of Event',
                                dest='event_id',
                                required=True)

    parser.set_defaults(func=delete_scheduled_event)

def cancel_event_parser(subcommand_parsers, common_parser):
    parser = subcommand_parsers.add_parser(
        'cancel',
        description='ViPR Cancel Scheduled Event CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Cancel a Scheduled Event by Id')

    mandatory_args = parser.add_argument_group(
        'mandatory arguments')

    mandatory_args.add_argument('-id',
                                help='The Id of Event',
                                dest='event_id',
                                required=True)

    parser.set_defaults(func=cancel_scheduled_event)

def schedule_event_parser(parent_subparser, common_parser):
    """
    Main parser routine
    Args:
        parent_subparser:
        common_parser:

    Returns:

    """

    # root parser of scheduled event
    parser = parent_subparser.add_parser(
        'scheduled_event',
        description='ViPR Scheduled Event CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Scheduled Event')

    # all sub parsers about scheduled event
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    get_event_parser(subcommand_parsers, common_parser)
    create_event_parser(subcommand_parsers, common_parser)
    update_event_parser(subcommand_parsers, common_parser)
    delete_event_parser(subcommand_parsers, common_parser)
    cancel_event_parser(subcommand_parsers, common_parser)

