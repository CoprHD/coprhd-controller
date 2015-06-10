#!/usr/bin/sh


# Installation directory of VIPR CLI
VIPR_CLI_INSTALL_DIR=/opt/storageos/cli

# Add the ViPR install directory to the PATH and PYTHONPATH env variables
if [ -n $VIPR_CLI_INSTALL_DIR ]
then
	export PATH=$VIPR_CLI_INSTALL_DIR/bin:$PATH
	export PYTHONPATH=$VIPR_CLI_INSTALL_DIR/bin:$PYTHONPATH
fi

# USER CONFIGURABLE VIPR VARIABLES

# VIPR Host fully qualified domain name
VIPR_HOSTNAME=<ViPR_Appliance_Host_Name>

# VIPR Port Numbers
VIPR_PORT=4443
VIPR_UI_PORT=443
