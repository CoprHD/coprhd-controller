@rem Copyright 2015 EMC Corporation
@rem All Rights Reserved
@echo off
:: Installation directory of VIPR CLI
set VIPR_CLI_INSTALL_DIR=C:\opt\storageos\cli

:: Installation directory of Python
set PYTHONHOME=C:\Python27

:: Add the SOS install directory, python to the PATH and PYTHONPATH
:: environment variables

set PYTHONPATH=%VIPR_CLI_INSTALL_DIR%;%PYTHONPATH%;
set PATH=%VIPR_CLI_INSTALL_DIR%\bin;%PYTHONHOME%;%PATH%;
doskey VIPRCLI=python %VIPR_CLI_INSTALL_DIR%\bin\viprcli.py $*

:: USER CONFIGURABLE SOS VARIABLES

:: VIPR Host fully qualified domain name
set VIPR_HOSTNAME=localhost

:: VIPR Port Numbers
set VIPR_PORT=4443
set VIPR_UI_PORT=443
