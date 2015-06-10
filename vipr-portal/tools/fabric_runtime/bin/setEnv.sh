#!/bin/sh

# This replaces /var and means runtime files are better contained
RUNTIME_ROOT=$(cd `dirname "${BASH_SOURCE[0]}"` && pwd)/..

if [ -z "${JAVA_HOME}" ] ; then
	echo "JAVA_HOME must be set and point to a JDK 7 installation"
	exit 1
fi

if [ -f ~/.storageos ] ; then
	. ~/.storageos
else
    echo ERROR:
	echo "~/.storageos configuration file not found, example storageos configuration file:"
	cat $RUNTIME_ROOT/bin/storageos.example	
fi
