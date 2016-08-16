#!/usr/bin/python
__author__ = 'stanislav.belenitsky@emc.com'

#
# This program can update ViPR Controller database
#
# !!! normal execution of this program may cause ViPR C instability !!!
# !!! exercise extreme caution. know EXACTLY what you are doing !!!
#
# LIMITATIONS:
# this program can update simple XML
#   "for a field of name=parameter, assign value=parameter"
# configurations, example:
#      <field name="accessState" type="java.lang.String" value="READWRITE"/>
#
# OPERATIONS:
#   to be executed locally on ViPR C node (any node), from any path
#   dependencies:
#       must be able to find & execute /opt/storageos/bin/dbcli
#       enough space to execute
#       enough permissions to execute
#   requires following input:
#       ColumnFamily; Resource URI; Field Name; Field New Value
#       offers optional parameters
#       see -h option (or read the code...)
#   allows for execution of 2 modes:
#       - (default) planning: all steps except for updating database
#       - execution: includes updating the database
#   v1.0 is meant to be executed once per field update per record
#

import argparse
import os
import datetime
import shutil
import sys
import subprocess

try:
    import xml.etree.cElementTree as eTree
    from xml.etree.cElementTree import ParseError as pE
except ImportError:
    import xml.etree.ElementTree as eTree
    from xml.etree.ElementTree import ParseError as pE

EXEC_MODE_PLANNING = 'planning'
EXEC_MODE_EXECUTE = 'execute'
EXEC_MODE_DEFAULT = EXEC_MODE_PLANNING

# path to DBCLI file on ViPR VM
# this is the script that will dump XML structure and load it back
PATH_VIPRC_DBCLI = r'/opt/storageos/bin/dbcli'
# /opt/storageos/bin/dbcli dump -i "id1,id2,..." -f <file name> <column family>
CMD_DBCLI_DUMP = "{0} dump -i {1} -f {2} {3}"
# /opt/storageos/bin/dbcli load -f <file name>
CMD_DBCLI_LOAD = "{0} load -f {1}"

# helpful to search XML structure
XML_WHAT_TO_LOOK_FOR = 'field'
XML_WHAT_TO_IDENTIFY_BY = 'name'
XML_WHAT_TO_UPDATE = 'value'


def print_xml_e(e):
    print "------------------------"
    print "Element Tag       : " + str(e.tag)
    print "Element Attributes: " + str(e.attrib)
    print "Element Value     : " + str(e)
    pass


def parse_cli_input():
    parser = argparse.ArgumentParser(
        description="%(prog)s will update a field in ViPR Controller "
                    "database.\n!!! ATTENTION: NORMAL OPERATION OF THIS "
                    "PROGRAM "
                    "MAY LEAD TO VIPR CONTROLLER LOSING STABILITY OR "
                    "BECOMING INOPERABLE. BACKING UP VIPR C DATABASE PRIOR "
                    "TO OPERATING IN EXECUTE MODE IS RECOMMENDED !!!\n")
    r_args = parser.add_argument_group('Required Arguments')
    r_args.add_argument('-column_family', '-cf',
                        required=True,
                        help='Specify column family of the record to modify')
    r_args.add_argument('-uniform_resource_identifier', '-uri',
                        required=True,
                        help='Specify identifier of resource to modify')
    r_args.add_argument('-field_name', '-fn',
                        required=True,
                        help='Specify name of the field to modify')
    r_args.add_argument('-new_value', '-nv',
                        required=True,
                        help='Specify new value for the field')

    o_args = parser.add_argument_group('Optional Arguments')
    o_args.add_argument('-exe_mode', '-em',
                        required=False,
                        default=EXEC_MODE_DEFAULT,
                        choices=[EXEC_MODE_PLANNING, EXEC_MODE_EXECUTE],
                        help="Specify execution mode, default is " +
                             EXEC_MODE_DEFAULT)
    o_args.add_argument('-set',
                        required=False,
                        help="Assigns a 'set' prefix to a bunch of changes "
                             "if desired. Helpful for effort tracking."
                             "Will be part of session name (and directory "
                             "name created with each session). Defaults "
                             "to nothing.")
    o_args.add_argument('-simulated_input', '-si',
                        required=False,
                        help="Path to file simulating script input (for "
                             "execution/testing without access to ViPR C db")

    return parser.parse_args()


def get_time_stamp():
    dt = datetime.datetime.today()
    date = dt.date().isoformat()

    full_ts = dt.time().isoformat().replace(':', '-', 2)
    if "." in full_ts:
        hr_mins_secs, milli_secs = full_ts.rsplit('.', 1)
    else:
        hr_mins_secs = full_ts
        milli_secs = '000000'

    return date, hr_mins_secs, milli_secs


def set_session_name(args):
    date, hrs_mins_secs, milli_secs = get_time_stamp()
    session_time_stamp = date + '_' + hrs_mins_secs

    if args.set is not None:
        session_time_stamp = args.set + '_' + session_time_stamp

    return session_time_stamp


#
# check if input is simulated - if it is then copy it, else get a new dump
#
def vipr_c_record_dump(log, ori_path, args):
    msg = \
        "Attempting to dump:\n" \
        "\tColumnFamily : " + args.column_family + "\n " \
                                                   "\tURI          : " + args.uniform_resource_identifier + "\n"
    my_print(log, msg)

    if args.simulated_input is not None:
        if not os.path.isfile(args.simulated_input):
            my_print(log, "ERROR: input is simulated, but file [" +
                     args.simulated_input + "] does not exist. Exiting...")
            sys.exit(-1)

        my_print(log, "Copying simulated input [" + args.simulated_input +
                 "] to [" + ori_path + "]...")
        shutil.copy(args.simulated_input, ori_path)

    else:
        my_print(log, "Calling [" + PATH_VIPRC_DBCLI + "] to get data...")

        if not os.path.isfile(PATH_VIPRC_DBCLI):
            my_print(log, "ERROR: file [" + PATH_VIPRC_DBCLI +
                     "] does not exist. Exiting...")
            sys.exit(-1)
        cmd = CMD_DBCLI_DUMP.format(
            PATH_VIPRC_DBCLI,
            args.uniform_resource_identifier,
            ori_path,
            args.column_family
        )

        try:
            code = subprocess.call(cmd, shell=True)
            if code != 0:
                my_print(log, "ERROR: [" + cmd + "] terminated with code - ["
                                                 "" + str(code) + "].")
                sys.exit(-1)
            else:
                my_print(log, "Command [" + cmd + "] executed successfully "
                                                  "- [" + str(code) + "].")
        except OSError as e:
            my_print(log, "ERROR: cmd [" + cmd + "] execution failed: " +
                     e.message)
            sys.exit(-1)


#
# check if input is simulated - if it is then copy it, else get a new dump
#
def vipr_c_record_load(log, upd_path, args):
    if args.exe_mode == EXEC_MODE_PLANNING:
        msg = \
            "Exe mode is: " + EXEC_MODE_PLANNING + ", not going to upload!\n" \
            "\tColumnFamily : " + args.column_family + "\n " \
            "\tURI          : " + args.uniform_resource_identifier + "\n"
        my_print(log, msg)

    else:
        if not os.path.isfile(upd_path):
            my_print(log, "ERROR: Unable to find file [" + upd_path + "]." +
                     "File to upload is not available. Exiting...")
            sys.exit(-1)

        msg = \
            "Attempting to upload:\n" \
            "\tColumnFamily : " + args.column_family + "\n " \
            "\tURI          : " + args.uniform_resource_identifier + "\n"
        my_print(log, msg)


        my_print(log, "Calling [" + PATH_VIPRC_DBCLI + "] to upload data...")

        if not os.path.isfile(PATH_VIPRC_DBCLI):
            my_print(log, "ERROR: file [" + PATH_VIPRC_DBCLI +
                     "] does not exist. Exiting...")
            sys.exit(-1)

        cmd = CMD_DBCLI_LOAD.format(
            PATH_VIPRC_DBCLI,
            upd_path
        )

        try:
            code = subprocess.call(cmd, shell=True)
            if code != 0:
                my_print(log, "ERROR: [" + cmd + "] terminated with code - ["
                                                 "" + str(code) + "].")
                sys.exit(-1)
            else:
                my_print(log, "Command [" + cmd + "] executed successfully "
                                                  "- [" + str(code) + "].")
        except OSError as e:
            my_print(log, "ERROR: cmd [" + cmd + "] execution failed: " +
                     e.message)
            sys.exit(-1)


def vipr_c_record_update(log, ori_path, upd_path, args):
    msg = "Attempting to update record:\n" \
          "\tColumnFamily : " + args.column_family + "\n " \
          "\tURI          : " + args.uniform_resource_identifier + "\n" \
          "\tField        : " + args.field_name + "\n" \
                                                                                                                                                    "\tNew Value    : " + args.new_value + "\n"
    my_print(log, msg)

    # check if original XML file exists
    if not os.path.isfile(ori_path):
        my_print(log, "ERROR: file [" + ori_path +
                 "] does not exist. Exiting...")
        sys.exit(-1)

    # parse the XML file
    try:
        doc_tree = eTree.parse(ori_path)

        # find all leaves that are fields with requested name
        # only continue if exactly 1 leaf found
        # print it out
        leaves = []
        for element in doc_tree.iter(XML_WHAT_TO_LOOK_FOR):
            if element.attrib[XML_WHAT_TO_IDENTIFY_BY] == args.field_name:
                leaves.append(element)

        if len(leaves) > 1:
            my_print(log, "ERROR: more than 1 leaf found: " + str(leaves))
            sys.exit(-1)

        if len(leaves) <= 0:
            my_print(log, "ERROR: no leaves found: " + str(leaves))
            sys.exit(-1)

        leaf = leaves[0]
        msg = "FOUND Element for update: \n" \
              "\tTag       : " + str(leaf.tag) + "\n" \
              "\tAttributes: " + str(
            leaf.attrib) + "\n" \
                           "\tValue     : " + str(leaf) + "\n"
        my_print(log, msg)

        # update leaf 'value' to args.new_value
        my_print(log, "updating " + XML_WHAT_TO_LOOK_FOR + ", found by " +
                 XML_WHAT_TO_IDENTIFY_BY + "=" + args.field_name + ", " +
                 "old value was: [" +
                 str(leaf.get(XML_WHAT_TO_UPDATE)) + "], new value will " +
                 "be set: [" + args.new_value + "].")
        leaf.set(XML_WHAT_TO_UPDATE, args.new_value)


        # write it out to upd_path file
        # TODO: how to preserve XML header?
        doc_tree.write(r'./' + upd_path, xml_declaration=True)
        my_print(log, "written changed XML structure out to file [" +
                 str(upd_path) + "].")

    except pE as parse_exc:
        my_print(log, "ERROR: xml parsing error on file [" + ori_path +
                 "], execution failed: " + parse_exc.message)
        sys.exit(-1)

    except IOError as io_exc:
        my_print(log, "ERROR: writing out to file [" + upd_path +
                 "], execution failed: " + io_exc.message)
        sys.exit(-1)


def my_print(log, msg):
    msg = "---------------------------\n" + msg
    print msg
    log.write(msg)


def main():
    args = parse_cli_input()

    session_name = set_session_name(args)
    os.makedirs(r'./' + session_name)

    log_path = r'./' + session_name + '/log_' + session_name + '.txt'
    log = open(log_path, 'w')

    my_print(log, 'Execution starting...')

    ori_path = r'./' + session_name + '/original_' + session_name + '.xml'
    upd_path = r'./' + session_name + '/updated_' + session_name + '.xml'

    vipr_c_record_dump(log, ori_path, args)
    vipr_c_record_update(log, ori_path, upd_path, args)
    vipr_c_record_load(log, upd_path, args)

    my_print(log, 'Execution completed.')
    sys.exit(0)


if __name__ == '__main__':
    main()


