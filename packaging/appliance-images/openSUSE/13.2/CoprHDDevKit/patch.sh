#!/bin/bash
#
# Copyright 2015 EMC Corporation
# All Rights Reserved
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
# Deactivate non-distribution packages from initrd
#######################################################
if [ ! -f /usr/share/kiwi/image/vmxboot/suse-13.2/config.xml.bak ]; then
  cp /usr/share/kiwi/image/vmxboot/suse-13.2/config.xml /usr/share/kiwi/image/vmxboot/suse-13.2/config.xml.bak
fi
sed -i /fribidi/d /usr/share/kiwi/image/vmxboot/suse-13.2/config.xml
sed -i /fbiterm/d /usr/share/kiwi/image/vmxboot/suse-13.2/config.xml

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
