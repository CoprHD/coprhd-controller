Description of viprcli Interfaces
-------------------------------- 
This module provides the command line interface (CLI) for accessing
the ViPR appliance. It consists of utilities to handle block volumes
(for block data), fileshares (for file data) and keypools (for
object data).
 
Requirement & Specification
---------------------------
(1) The Python having versions 2.7.3 should be installed on
the system prior to the installation of vipr cli.

(2) The argparse and requests modules should be installed on the system prior to the
installation of vipr cli. 

(3) By default, the vipr cli commands are getting installed in the
directory "/opt/vipr/cli". User can specify any directory during
the installation of the vipr cli commands.

(4) The installation directory contains a file named with "viprcli.profile".
   - By default, the vipr appliance ip is set to localhost and port number
   is set to 4443. The user can specify the vipr appliance's hostname by
   modifying the values of environment variable "VIPR_HOSTNAME" in "viprcli.profile"
   file. The default location of profile file is "/opt/vipr/cli/viprcli.profile".

   - Before using the vipr commands, the user has to run the above profile file
   using the command "source /opt/vipr/cli/viprcli.profile" on its current shell.

