README-ssh.txt
-----------------

1. Why are root's keys in the authorized users files for svcuser & storageos
- diagtool is invoked by syssvc (running as user storageos) and interactively by svcuser
- diagtool requires privilege escalation, thus it is invoked through sudo
- diagtool, running as root, has to make SSH calls to other nodes. It makes these calls using the root account on the client side (thus root SSH keys are indeed required), and using the svcuser account on the server side (thus the corresponding public keys must be in svcuser's authorized_keys2).
- powerofftool is also invoked by syssvc. 
- powerofftool, running as root, has to make SSH calls to other nodes as storageos user. Thus the root's public key must be in storageos's authorized_keys2.

