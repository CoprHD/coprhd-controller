#!/usr/bin/python
#
# Copyright (c) 2012-2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#
# This python test is for testing the functionality of systool merge and migration
#

import sys
import os
import subprocess
import filecmp
import difflib

SYSTOOL_CMD = '/etc/systool'
SYSTOOL_GET_PROPS = '--getprops'
OVF_PROPS = 'systool_test_files/ovf.props.test'
MUTATED_PROPS = 'systool_test_files/config-override.properties.test'
DEFAULT_PROPS = 'systool_test_files/config.defaults.test'
RESULT_FILE = '/tmp/result.test'
EXPECTED_MERGE_RESULTS_FILE = 'systool_test_files/getprops.result.test'
ERROR_LOG = '/tmp/error_log'

def verify_systool_get_props():
    test_name = "Verify /etc/systool --getprops (properties merge)"
    test_start(test_name)
    subprocess.check_call(SYSTOOL_CMD + ' ' + SYSTOOL_GET_PROPS + ' ' + OVF_PROPS + ' ' + MUTATED_PROPS + ' ' + DEFAULT_PROPS + ' >' + RESULT_FILE,
                          stderr=subprocess.STDOUT, shell=True)
    result_cmp = filecmp.cmp(RESULT_FILE, EXPECTED_MERGE_RESULTS_FILE)
    file1 = open(EXPECTED_MERGE_RESULTS_FILE, 'r')
    file2 = open(RESULT_FILE, 'r')
    diff = difflib.ndiff(file1.readlines(), file2.readlines())
    delta = ''.join(x[2:] for x in diff if x.startswith('- '))
    fo = open(ERROR_LOG, 'w')
    fo.write("Diff between expected results and results\n\n" + delta)
    file1.close()
    file2.close()
    fo.close()
    if filecmp.cmp(RESULT_FILE, EXPECTED_MERGE_RESULTS_FILE) == False:
        raise Exception('/etc/systool --getprops failed. The merge did not produce expected results. Look at test files, /tmp/result.test, and /tmp/error_log for more details.')
    test_done(test_name)

def test_start(name):
    global test_name
    test_name = name
    print " >> "+name+" start."

def test_done(name):
    print " << "+name+" stop."

def test_fail(name):
    print " << "+name+" failed."

#----------------------------------------------------------------------
# Main script
#----------------------------------------------------------------------
try:
    verify_systool_get_props()
except Exception, e:
    print e
    test_fail(test_name)
    sys.exit(1)

