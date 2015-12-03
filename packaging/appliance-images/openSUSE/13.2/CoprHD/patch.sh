#!/bin/bash
# Copyright (c) 2015, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#

#######################################################
# KIWI Modifications
#######################################################
kiwiPath="/usr/share/kiwi/modules"

#######################################################
# Skip this configuration if KIWI is not installed
#######################################################
rpm -q --quiet kiwi
if [ $? -ne 0 ]; then
  exit 0
fi

#######################################################
# Changes for 3 digits to 5 digits version
#######################################################
FileValidator="$kiwiPath/KIWIXMLValidator.pm"
FilePreference="$kiwiPath/KIWIXMLPreferenceData.pm"
# Verify first if 3 digits mod is needed again
if [ -f $FileValidator ] && [ -f $FileValidator.bak ]; then
  grep --quiet '\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+' $FileValidator
  if [ $? -ne 0 ]; then
    rm $FileValidator.bak
  fi
fi
if [ -f $FilePreference ] && [ -f $FilePreference.bak ]; then
  grep --quiet '\\d+?\\.\\d+?\\.\\d+?\\.\\d+?\\.\\d+?' $FilePreference
  if [ $? -ne 0 ]; then
    rm $FilePreference.bak
  fi
fi
# Apply modifications when needed
if [ -f $FileValidator ] && [ ! -f $FileValidator.bak ]; then
 echo "Modifying to fix version check for $FileValidator"
 sed -i."bak" 's/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\)/\($version !~ \/^\\d+\\.\\d+\\.\\d+$\/\) \&\& \($version !~ \/^\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+$\/\)/g' $FileValidator
fi
if [ -f $FilePreference ] && [ ! -f $FilePreference.bak ]; then
 echo "Modifying to fix version check for $FilePreference"
 sed -i."bak" 's/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \)/\( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?$\/smx \) \&\& \( $ver !~ \/^\\d+?\\.\\d+?\\.\\d+?\\.\\d+?\\.\\d+?$\/smx \)/g' $FilePreference
fi
