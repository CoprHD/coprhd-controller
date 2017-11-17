#!/usr/bin/env python
import sys
import platform
import os
import shutil
import glob
from time import sleep

global inst_dir
global host_name
global vipr_port
global uninst_dir

INST_ERROR = 1

# import setup tools only for installation.
if 'install' in sys.argv:
    try:
        from ez_setup import use_setuptools
        use_setuptools()
    except KeyboardInterrupt:
        print "...Interrupted"
        sys.exit();
    except Exception as e:
        pass    
    
    usingsetuptools = False
    try:
        from setuptools import setup
        usingsetuptools = True
    except ImportError as e:
        from distutils.core import setup
    except KeyboardInterrupt:
        print "...Interrupted"
        sys.exit()

version = '3.6.1.2'
# Find the CLI version from version file.
# This ver.txt generated while build process.
# contains string storageos-cli-2.2.0.0.1
verfile = os.path.join("viprcli", "ver.txt")
if(os.path.isfile(verfile)):
    with open(verfile, 'rb+') as filedesc:
        lines = filedesc.readlines()
        for line in lines:
            ver = line[14:3]
            if(len(ver) > 0):
                version = ver
                break
            
# CLI supported files.            
supportfiles = ['viprcli.bat', 'viprcli.pth']
if platform.system() == "Windows":
    supportfiles.append('viprcli.profile.bat')
else:
    supportfiles.append('viprcli.profile') 
    
    
# .pth file is needed to identify the viprcli package.    
def _update_copy_path_file(filename):
    with open(filename, 'rb+') as filedesc:
        lines = filedesc.readlines()
        filedesc.seek(0)
        filedesc.truncate()
        first_line = True
        line = None
        for line in lines:
            if( first_line):
                filedesc.write(line)
                clieggpath = "./bin/viprcli-%s-py%s.egg" % (version,sys.version[:3])
                filedesc.write(clieggpath + "\n")
                filedesc.write(clieggpath +"/viprcli\n")
                first_line = False
        filedesc.write(line)            
        filedesc.close()
    # create the directory if not exists and copy the .pth file    
    
    dstdir = os.path.join(inst_dir, "bin")
    if not os.path.exists(dstdir):
        os.makedirs(dstdir)
    shutil.copy(filename, inst_dir)
    # Update the PYTHONPATH with destination directoty
    os.environ['PYTHONPATH'] = dstdir


# viprcli.profile script file contains, environment setting for ViPR-C.
# VIPR_CLI_INSTALL_DIR changed to user directory, as it used for cookie file folder.
def _update_profile_file(filename, clipath, hostname, port):
    dstdir = os.path.join(inst_dir, "bin")
    if platform.system() == "Windows":
        filename = filename+".bat"
        firstline = "@ECHO OFF\n"
        cmd ="SET "
    else:
        firstline = "#!/usr/bin/sh\n"
        cmd =""
    with open(filename, 'wb+') as file:
        file.seek(0)
        file.truncate()
        file.write(firstline)
        # Installation directory of VIPR CLI
        # If we install CLI in python directory, 
        # cookie file creation fails due to insufficient privileges.
        from os.path import expanduser
        userhome = expanduser("~")
        file.write(cmd+"VIPR_CLI_INSTALL_DIR="+userhome+"\n")
        if platform.system() == "Windows":
            file.write("SET PATH="+clipath+";%PATH%\n")
            file.write("SET PYTHONPATH="+clipath+";%PYTHONPATH%\n")
            file.write("SET PATH="+dstdir+";"+inst_dir+";%PATH%\n")
            file.write("SET PYTHONPATH="+dstdir+";"+inst_dir+";%PYTHONPATH%\n")
        else:
            file.write("export PATH="+clipath+":$PATH\n")
            file.write("export PYTHONPATH="+clipath+":$PYTHONPATH\n")
            file.write("export PATH="+dstdir+":"+inst_dir+":$PATH\n")
            file.write("export PYTHONPATH="+dstdir+":"+inst_dir+":$PYTHONPATH\n")         

        # VIPR Host fully qualified domain name
        file.write(cmd+"VIPR_HOSTNAME="+hostname+"\n")
        file.write(cmd+"VIPR_PORT="+port+"\n")        
        file.write(cmd+"VIPR_UI_PORT="+"443\n")

        file.write(cmd+"VIPR_CONTROL_API_VERSION=\n")
        file.close()
        
    # create the directory if not exists and copy the .pth file            
    if not os.path.exists(inst_dir):
        os.makedirs(inst_dir)
    shutil.copy(filename, inst_dir)
        
def _update_config_file(filename):
    configfilename = filename
    import ConfigParser
    config = ConfigParser.ConfigParser()
    with open(configfilename) as configfile:
        config.readfp(configfile, configfilename)
    config.set('install', 'prefix', inst_dir)
    with open(configfilename, 'wb') as configfile:
        config.write(configfile)
    
def _get_install_location():
    if platform.system() == "Windows":
        CLI_INSTALL_DIR = os.path.abspath('/EMC/ViPR/cli')
    else:    
        CLI_INSTALL_DIR = os.path.abspath('/opt/storageos/cli')
    VIPR_HOSTNAME = 'localhost'    
    VIPR_PORT = '4443'
    print "Please specify the directory where ViPR Commands will be installed."
    print "Installation Directory [" + CLI_INSTALL_DIR +"] : "
    global inst_dir
    inst_dir = sys.stdin.readline().strip("\r\n")
    if(len(str(inst_dir)) < 2):
        inst_dir = CLI_INSTALL_DIR    
    inst_dir = os.path.abspath(inst_dir)
    
    # Get the host name/ip from the installation path.
    global host_name
    for arg in sys.argv:
        if('http://' in arg or 'https://' in arg):
            beg=arg.find("://")+3
            if( arg.find("@") > 0):
                beg=arg.find("@")+1
            host_name= arg[beg:arg.find(":", beg)]
            VIPR_HOSTNAME = host_name
            print "host name " , VIPR_HOSTNAME
    print "Please specify the ViPR HOSTNAME (Fully Qualified Domain name)."    
    print "ViPR host FQDN/IP [" + VIPR_HOSTNAME +"] : "
    host_name = sys.stdin.readline().strip("\r\n")
    if(len(str(host_name)) < 2):
        host_name = VIPR_HOSTNAME

    print "Please specify the ViPR PORT."    
    print "ViPR port [" + VIPR_PORT +"] : "
    global vipr_port
    vipr_port = sys.stdin.readline().strip("\r\n")
    if(len(vipr_port) < 2):
        vipr_port = VIPR_PORT

def _get_uninstal_location():
    if platform.system() == "Windows":
        CLI_UNINSTALL_DIR = os.path.abspath('/EMC/ViPR/cli')
    else:    
        CLI_UNINSTALL_DIR = os.path.abspath('/opt/storageos/cli')
        
    print "Please specify the directory where ViPR Commands installed."
    print "Installation Directory [" + CLI_UNINSTALL_DIR +"] : "
    global uninst_dir
    uninst_dir = sys.stdin.readline().strip("\r\n")
    if(len(str(uninst_dir)) < 2):
        uninst_dir = CLI_UNINSTALL_DIR    
    uninst_dir = os.path.abspath(uninst_dir) 

# This function will copy support files from installed location to backup location.
def _copy_support_files(srcdir, dstdir):
    try:
        for supfile in  supportfiles:
            if os.path.isfile(os.path.join(srcdir, supfile)):
                shutil.copyfile(os.path.join(srcdir, supfile),
                            os.path.join(dstdir, supfile))
    except OSError as e:
        pass
              
# other than vipr cli binaries(.py) files, cli uses other files
# support files too should be deleted.
def _remove_cli_support_files(clidir):
    try:
        for supfile in  supportfiles:
            if os.path.isfile(os.path.join(clidir, supfile)):
                os.remove(os.path.join(clidir, supfile))
    except OSError as e:
        pass

def _remove_backup_dir(dirname):
    try:
        if os.path.isdir(dirname):
            shutil.rmtree(dirname)
    except OSError:
        pass        
        
# un-install ViPR cli; 
# Remove all CLI files from the installed directory; 
# Remove both old version and new version installed files.
# In new installation process, cli files will be present under folder with package name(viprcli-2.2-py2.6.egg)
# In older installation, the files present in bin folder only;
# The following is folder structure, in new installation. 
#-----cli
#        viprcli.bat
#        viprcli.profile.bat
#        viprcli.pth
#    
#---------bin
#            viprcli-2.2-py2.7.egg
#            ----config
#            ----docs
#            ----EGG-INFO
#            ----viprcli
#                    approval.py
#                    approval.pyc
#                    assetoptions.py
#                    assetoptions.pyc
# The following is folder structure, in old installation. 
#-----cli
#        viprcli.bat
#        viprcli.profile.bat    
#---------bin           
#             approval.py
#             approval.pyc
#             assetoptions.py
#             assetoptions.pyc
     
def uninstall_cli(uninstall_dir):
    if( not (uninstall_dir.endswith("bin") or 
       uninstall_dir.endswith("bin/") or 
       uninstall_dir.endswith("bin\\")) ):
        uninstall_dir = os.path.join(uninstall_dir, "bin")
    # True - indiacates cli installation with setuptools.    
    viprcli2_1_or_plus_install = False     
    havefolders = False  
    
    if not os.path.exists(uninstall_dir):
        print uninstall_dir + " is not a ViPR cli installation directory"
        return INST_ERROR
    
    # Backup the cli files; use this backup copy in-case of failure in un-install.
    from random import randint
    cli_installed_base_dir = os.path.dirname(uninstall_dir)
    cli_backup_dir=os.path.join(cli_installed_base_dir, 
                                "viprclibackup-"+str(randint(1, 1000)))
    cli_backup_dir_bin=os.path.join(cli_backup_dir, "bin")
    
    try:
        # Copy the bin directory to backup bin directory.
        shutil.copytree(uninstall_dir, cli_backup_dir_bin)
        #Copy support files in installed dir to backup dir.
        _copy_support_files(cli_installed_base_dir, cli_backup_dir)
    except OSError as ex:
        print "Un-installation could not proceed - " + ex
        sys.exit()
    except KeyboardInterrupt:
        print "User interrupted..."
        _remove_backup_dir(cli_backup_dir)
        sys.exit()
        
    uninstallationfailed = False
    removed_package_folders = []            
    # Remove all vipr package directories starts with viprcli-    
    for rootpath, subdirs, filenames in os.walk(uninstall_dir):
        # print path to all subdirectories first.
        if len(subdirs) > 0:
            havefolders = True
        # Remove all CLI files along with folder.   
        for subdirname in subdirs:
            if subdirname.startswith("viprcli-"):
                viprcli2_1_or_plus_install = True;
                try:
                    shutil.rmtree(os.path.join(rootpath, subdirname))
                    # Can be used to restore incase of un-install fail.
                    removed_package_folders.append(subdirname)
                except OSError as e:
                    print "Un-installation failed: " + e
                    uninstallationfailed=True
                    break
                except KeyboardInterrupt:
                    print "User interrupted..."
                    uninstallationfailed=True
                    break
                                                
    if (viprcli2_1_or_plus_install is False and 
        not os.path.isfile(os.path.join(uninstall_dir, "viprcli.py"))):
        print uninstall_dir + " is not a ViPR cli installation directory"
        _remove_backup_dir(cli_backup_dir)
        return
    
    try:
        # old installation bin folder has to be removed.
        if viprcli2_1_or_plus_install is False:
            shutil.rmtree(uninstall_dir)
        # remove the support files.
        _remove_cli_support_files(uninst_dir)
    except OSError as e:
        print "Un-installation failed: " + e
        uninstallationfailed=True
        pass
    except KeyboardInterrupt:
        print "User interrupted..."
        uninstallationfailed=True
        pass
               
    if uninstallationfailed is True:
        try:
            # New installation
            if viprcli2_1_or_plus_install is True:
                # copy back the vipr cli package folder to original location.
                for dirname in removed_package_folders:
                    shutil.copytree(os.path.join(cli_backup_dir_bin, dirname),
                                    os.path.join(uninstall_dir, dirname))
                    
            else:
                shutil.copytree(cli_backup_dir_bin, uninstall_dir )
                
            # Support files are common in old and new installations.            
            _copy_support_files(cli_backup_dir, cli_installed_base_dir)
            
        except OSError as e:
            print "Failed to copy files from backup location " + e
            print "Please re-install the ViPR cli."
            _remove_backup_dir(cli_backup_dir)
            sys.exit()
        
    # Remove the backup directory.     
    _remove_backup_dir(cli_backup_dir)
    print "Successfully un-installed ViPR cli"        
            
# This function actually do installation by calling setup() 
# with give parameters.                
def _install_cli():
    docfiles = glob.glob("docs/*")
    confiles = glob.glob("config/*")      
    
    setupargs = dict (
        name='viprcli',
        description='ViPR command line interface',
        classifiers=[
            'Development Status :: Production/Stable',
            'Environment :: Console',
            'Intended Audience :: All Users',
            'License :: EMC license',
            'Operating System :: OS Independent',
            'Programming Language :: Python',
            'Topic :: Storage Management',
        ],
        version=version,
        packages= ['viprcli', 'docs', 'config'],
        include_package_data = True,
        package_dir= { 
            'viprcli': 'viprcli',
            'docs': 'docs',
            'config': 'config'},             
        data_files=[('viprcli', ['viprcli/ver.txt']),
                    ('docs', docfiles ),
                    ('config', confiles)],
        scripts=['viprcli/viprcli.bat']
        )         
    if usingsetuptools: 
        setupargs.update(dict(install_requires=[ 'setuptools', 'argparse==1.2.1', 'requests==2.8.1']))
        setupargs.update(zip_safe=False) 
    else: 
        setupargs.update(dict(requires=[ 'setuptools', 'argparse', 'requests']))
    try:       
        s = setup(**setupargs)
    except KeyboardInterrupt:
        print "...Interrupted"
        sys.exit()

def _install_vipr_cli_post_processing():
    installation_path = os.path.join(inst_dir, "bin")
    if usingsetuptools:
        egg_name = "viprcli-%s-py%s.egg/viprcli" % (version,sys.version[:3])
    else: 
        egg_name = "viprcli"    
    clipath = os.path.join(installation_path, egg_name)
    clipath = os.path.abspath(clipath)
    # Create a symbolic link  for viprcl script.
    viprsciptfile = os.path.join(clipath, "viprcli.py")
    viprlinkfile = os.path.join("viprcli", "viprcli")
    verfile = os.path.join("viprcli", "ver.txt")
    try:
        os.chmod(viprsciptfile,0o777)  # read/write by everyone
        shutil.copy(viprlinkfile, clipath)
        shutil.copy(verfile, clipath)
        # Update the configuration file
        _update_profile_file("viprcli.profile", clipath, host_name, vipr_port)
    except KeyboardInterrupt:
        print "...Interrupted"
        sys.exit()
    except AttributeError as e:
        pass
    except OSError as e:
        pass
    except IOError as e:
        pass    

    # delete the generated file.
    try:
        filename = os.path.join(installation_path, "viprcli.py")
        os.remove(filename)  # read/write by everyone
    except KeyboardInterrupt:
        print "...Interrupted"
        sys.exit()
    except OSError as e:
        pass
    
        
def _uninstall_vipr_cli():
    _get_uninstal_location()
    uninstall_cli(uninst_dir)
        
    
def _install_vipr_cli():
    _get_install_location()
    _update_config_file("setup.cfg")
    _update_copy_path_file("viprcli.pth")  
    _install_cli()
    _install_vipr_cli_post_processing()

class Logger(object):
    def __init__(self):
        self.terminal = sys.stdout
        self.log = open("install-log.txt", "w")

    def write(self, message):
        self.terminal.write(message)
        self.log.write(message)
    def flush(self):
      pass

sys.stdout = Logger()
sys.stderr =Logger()

try:
    if "install" in sys.argv :
        _install_vipr_cli()
    elif "uninstall" in sys.argv:
        _uninstall_vipr_cli()     
except KeyboardInterrupt:
    print "...Interrupted"
    sys.exit()
    

