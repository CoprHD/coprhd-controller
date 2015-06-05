#!/bin/sh

# Copyright 2015 EMC Corporation
# All Rights Reserved

# This replaces /var and means runtime files are better contained
RUNTIME_ROOT=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)/..

JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Home

if [ -f ~/.storageos ] ; then
	. ~/.storageos
else
    echo ERROR:
	echo "~/.storageos configuration file not found, example storageos configuration file:"
	cat $RUNTIME_ROOT/bin/storageos.example	
fi

VERSION=`cat $INSTALL_HOME/storageos/version.txt`
echo "StorageOS Version: $VERSION"
