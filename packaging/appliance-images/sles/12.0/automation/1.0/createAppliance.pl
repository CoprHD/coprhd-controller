#!/usr/bin/perl
#
#--------------------------------------------------------------------
# Copyright (c) 2013, EMC Corporation. All Rights Reserved.
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.
# This software is protected, without limitation, by copyright law and
# international treaties.
# Use of this software and the intellectual property contained therein
# is expressly limited to the terms and conditions of the License
# Agreement under which it is provided by or on behalf of EMC.
#--------------------------------------------------------------------
#
# Created 8/16/13 : michael.cartwright@emc.com
# Modified 05/28/2014: Padmakumar.Pillai@emc.com
# - Introduced profiles & types as optional command line parameters
# - Enabled multi-image creation
# - Introduced the handling of the new conf variable - CATALOG_FILES
# Modified 07/02/2014: Padmakumar.Pillai@emc.com
# - Enabled multi-list generation based on multiple profiles
# - Enabled creation of multiple root folders during a debug build
# - Preservation of image log files (prepare step and create step) in a separate log folder
# Modified 07/25/2014: Padmakumar.Pillai@emc.com
# - Implemented naming convention standards for same type of images created from different profiles.
# Modified 07/31/2014: Padmakumar.Pillai@emc.com
# - Cleaned up the $jobDir after the images are created and moved into $finishedImagesDir.
# - $imageTransferred is changed into $imageBlessed which takes effect when an image is blessed.
# Modified 08/04/2014: Rodrigo.Oshiro@emc.com
# - Implemented naming convention standards for different Linux distributions.
# Modified 08/26/2014: Padmakumar.Pillai@emc.com
# - Implemented multiple manifest files and multiple manifest directories support based on profiles.
# Modified 09/04/2014: Rodrigo.Oshiro@emc.com
# - Logging files into a product/build folder under log directory.
# Modified 09/18/2014: Rodrigo.Oshiro@emc.com
# - Adding cmd line switches to address applianceUpdate.zip media creation.
# Modified 10/16/2014: Rodrigo.Oshiro@emc.com
# - Enhancing usage text with details with command and argument descriptions.
# Modified 13/11/2014: Rodrigo.Oshiro@emc.com
# - Adding image base support for archiving filesystem trees and fixing applianceUpdate for multiple profiles.
# Modified 11/14/2014: Padmakumar.Pillai@emc.com
# - Added the -f optional parameter to convert the image to multiple formats.
# Modified 01/16/2015: Padmakumar.Pillai@emc.com
# - Changed the permission set for ova file generated to 644 (default was 600),
# - which was making it difficult to download from the web share


use strict;
use Cwd qw(cwd abs_path);
use File::Basename qw(dirname basename);
use File::Path qw(mkpath rmtree);
use File::Find qw(find);
use Getopt::Long qw(GetOptions);
Getopt::Long::Configure ("bundling");
use Config::General qw (ParseConfig);
use Config::IniFiles;

################################################################################
# Hardcoded/default values.
################################################################################
my $toolName = "createAppliance";
my $kiwiExec = "PATH=\$PATH:/sbin:/usr/sbin; sudo /usr/sbin/kiwi";
my $rpmExec = "/bin/rpm";
my $findExec = "/usr/bin/find";
my $copyExec = "/bin/cp";
my $moveExec = "/bin/mv";
my $wgetExec = "/usr/bin/wget";
my $svnExec = "/usr/bin/svn";
my $checksumExec = "/usr/bin/md5sum";
my $antExec = "/usr/bin/ant";
my $ovftoolExec ="/usr/bin/ovftool";
my $rmExec = "sudo /bin/rm";
my $hostname = `hostname`;
my $user = `whoami`;
my $zipExec = "/usr/bin/zip";
my $gzipExec = "/usr/bin/gzip";
my $tarExec = "/bin/tar";
my $createRepoExec = "/usr/bin/createrepo";

my $profileStartString = "## START PROFILE:";
my $profileEndString = "## END PROFILE:";
my $isDefaultProfile = 0;
my $multiList = 0;

chomp($user, $hostname);

########################################
# Logging values
my $debug = "DEBUG";
my $error = "ERROR";
my $warning = "WARN";
my $info = "INFO";

# TODO - we can probably make this smarter, instead of grabbing all catalog files...
my $iccPath = "https://rmsgsvn.lss.emc.com/svn/devsvcs/adg/common/main/integration/ICC";
my @catalogFiles = (join("/", $iccPath, "ADG_ICC.xml"),
                    join("/", $iccPath, "ADG_ICC_SLES11SP1.xml"),
                    join("/", $iccPath, "ADG_ICC_SLES11SP2.xml"),
                    join("/", $iccPath, "ADG_ICC_SLES11SP3.xml")
                   );

my $rc = 0;
my %errorCodes =
  (
    USAGE_ERROR => 1,    # associated with the input parameters
    IO_ERROR => 2,       # associated with system interaction (checksums, cp/mv, wgets, etc)
    SVN_ERROR => 3,      # associated with svn actions
    LIST_ERROR => 4,     # associated with the list file (parsing)
    ANT_ERROR => 5,      # associated with the ant process for generating list files
    CONFIG_ERROR => 6,   # associated with config validation actions
    KIWI_ERROR => 7,     # associated with KIWI actions
    OVF_ERROR => 8,      # associated with OVF processing (ovftool, etc)
    UPDATE_ERROR => 9    # associated with UPDATE media generation
  );

########################################
# Global flags.
# $imageBlessed lets the rest of the tool know that the image files have
# been transferred successfully to the product share.
# It is important for the exitClean function to know when it's ok to clean up the $imageDir.
my $imageBlessed = 0;

# $imageMoved lets the rest of the tool know that the image files have
# been transferred successfully to the $finishedImagesDir after the build process.
# It is important for the exitClean function to know when it's ok to clean up the $jobDir.
my $imageMoved = 0;

########################################
# Global Variables
my %conf = undef;

my $productName = undef;
my $productDisplayName = undef;
my $versionNumber = undef;

my $workDir = undef;
my $logDir = undef;
my $runDir = undef;
my $jobDir = undef;
my $srcDir = undef;
my $prepDir = undef;
my $repoDir = undef;
my $buildDir = undef;
my $imageDir = undef;
my $imageLogDir = undef;
my $updateSrcDir = undef;
my $updateMediaDir = undef;
my $logFile = undef;
my $errLogFile = undef;
my $localListFile = undef;
my $localConfigFile = undef;
my $localConfigFileOriginal = undef;
my @packageNames = ();
my @isoList = ();
my @localPackageNames = ();
my @imageFiles = ();

my $finishedImagesDir = undef;
my $finishedImagesURL = undef;

################################################################################
# Process command line.
################################################################################
my $forceDebug = undef;
my $actionCreate = undef;
my $actionUpdate = undef;
my $actionUpdateMedia = undef;
my $actionBless = undef;
my $genChecksums = undef;
my $actionList = undef;
my $svnComment = undef;
my $showUsage = undef;
my @profiles = ();
my @types = ();
my @formats = ();
my $ovaConversion = undef;
my $finishedImagesArgDir = undef;
my $workArgDir = undef;
my $verboseOutput = undef;
my %profileTypes = ();
my %profileDelPackages = ();
my $confFile = undef;
my $jobID = undef;
my $logLevel = undef;
my $imageFileName = undef;

GetOptions("d|debug" => \$forceDebug,
           "v|verbose" => \$verboseOutput,
           "c|create" => \$actionCreate,
           "u|updatemedia:s" => \$actionUpdate,
           "l|createlist" => \$actionList,
           "b|bless" => \$actionBless,
           "h|help" => \$showUsage,
           "s|checksums" => \$genChecksums,
           "p|profile=s" => \@profiles,
           "t|type=s" => \@types,
           "f|format=s" => \@formats,
           "a|ova" => \$ovaConversion,
           "o|output=s" => \$finishedImagesArgDir,
           "w|workspace=s" => \$workArgDir) or die(exitUsage("[$error]: Invalid arguments"));

# The var $actionUpdate will only be set if the argument u/updatemedia was set in command line
($actionUpdateMedia = $actionUpdate) if (defined $actionUpdate);

########################################
# Optional help menu.
exitUsage() if ($showUsage);

# Getting the conf file and job id from the command line parameters
$confFile = shift;
$jobID = shift;

# This is to unshift the optional value of -u, that was indented to be blank
# but could receive the value of confFile instead
if (defined $actionUpdateMedia && ! defined $jobID && ! -d $actionUpdateMedia)
{
  $jobID = $confFile;
  $confFile = $actionUpdateMedia;
  $actionUpdateMedia = "";
}

# Calling the main function here.
main();


# The main function
# This forms the crux of the whole program workflow.
# The workflow can be summarized as below:
# 1. Initialization of variables and paths. Creation of job directories.
# 2. Creates the list file(s) based on the packages used from the ADG ICC Catalog
# 3. Creates the image(s) and generates the update media based on the list file
#    and the kiwi config file
# 4. Move the images to a web-browsable location in the DevKit (/opt/downloads/images)
# 5. Finally, copy the images to a product share directory, outside the DevKit,
#    which can be accessed by the product teams
sub main()
{
  init();
  createList() if ($actionList);
  create() if ($actionCreate);
  updateMedia() if (defined $actionUpdateMedia);
  moveImages() if ($actionCreate || defined $actionUpdateMedia);
  blessImages() if ($actionBless);

  logger($info, "COMPLETE");
  exitClean(0);
}

# This function setups the directory and creates the folders that will be used
# by the tool. It also validates the path by checking if it is valid and exits with errors
# when the folder cannot be created, if it is a file, etc.
sub initDirOrDefault
{
  my ($initDir, $default) = @_;
  my $returnValue = ( defined $initDir ? $initDir : $default );

  # This is to remove trailing end-slashes if any like: /opt/ADG/createAppliance////
  $returnValue =~ s/\/+$//;

  # There might be issues when special characters are used such as whitespaces, accents,
  # dots, etc. Even though they might be valid, it will be safer to allow only alphanumeric
  exitUsage("[$error]: Only paths containing alphanumeric characters are supported: $returnValue.") if ($returnValue !~ m/^[a-zA-Z0-9_\/\.\-]+$/);
  mkpath ($returnValue, {error => \my $err});
  for my $diagnostic (@$err) {
    my ($file, $message) = each %$diagnostic;
    exitUsage("[$error]: $file: $message.");
  }

  return $returnValue;
}

# This function initializes the global variables declared at the start of the program
# It sets up some path variables which the script uses across its functions and
# creates certain job directories which will be used for the staging, image creation and
# update media generation.
sub init()
{
  ################################################################################
  # Set up logging level
  ################################################################################
  $logLevel = ($forceDebug || $verboseOutput ? $debug : $error);

  ################################################################################
  # Validate arguments.
  ################################################################################
  exitUsage("[$error]: Invalid number of arguments") if (!$confFile || !$jobID);
  logger($warning, "Checksum generation only supported with bless.  Ignoring.") if ($genChecksums && !$actionBless);

  ################################################################################
  # Check ovftool installation.
  ################################################################################
  my @ovfMatches = grep(/^vmx$/, @types);
  if (scalar(@types) <= 0 || scalar(@ovfMatches) > 0)
  {
    unless (-e $ovftoolExec)
    {
      logger($error, "ovftool needs to be installed to run this script.");
      logger($error, "Operation aborted.  Exiting with error code: $errorCodes{IO_ERROR}");
      exitClean($errorCodes{IO_ERROR});
    }
  }

  ################################################################################
  # Set up variables available to all conf files.
  ################################################################################
  $ENV{BUILD_NUM} = $jobID;
  $runDir = `pwd`;
  chomp $runDir;
  $ENV{RUN_DIR} = $runDir;

  ################################################################################
  # Parse conf file.
  ################################################################################
  logger($info, "* Reading conf file * ...");
  my $tempConfFile = join("/", "/tmp", join("", grabDate(), grabTime()), basename($confFile));
  getFiles(dirname($tempConfFile), $confFile);
  %conf = ParseConfig( -ConfigFile => $tempConfFile, -InterPolateVars => 1, -InterPolateEnv => 1);

  ################################################################################
  # Validate conf file values.
  ################################################################################
  if ($actionList && !$conf{LIST_SCRIPTS})
  {
    logger($error, "Unable to process list generation without LIST_SCRIPTS in conf file");
    logger($error, "Operation aborted.  Exiting with error code: $errorCodes{LIST_ERROR}");
    exitClean($errorCodes{LIST_ERROR});
  }

  ################################################################################
  # Update scripts won't work without update media files.
  ################################################################################
  if ($conf{UPDATE_SCRIPTS} && !$conf{UPDATE_FILES})
  {
    logger($error, "Unable to generate update media without UPDATE_FILES in conf file");
    logger($error, "Operation aborted.  Exiting with error code: $errorCodes{UPDATE_ERROR}");
    exitClean($errorCodes{UPDATE_ERROR});
  }

  ################################################################################
  # When no specific actions are set, the default is to execute all actions except bless
  ################################################################################
  if (!$actionCreate && !$actionList && !(defined $actionUpdateMedia) && !$actionBless)
  {
    ($actionCreate = 1 && $actionList = 1);
  }

  ################################################################################
  # When actionCreate is set, also set update media if needed
  ################################################################################
  if ($actionCreate && ! defined $actionUpdateMedia)
  {
    ($actionUpdateMedia = "") if ($conf{UPDATE_SCRIPTS} && $conf{UPDATE_FILES});
  }

  ################################################################################
  # Construct dynamic values.
  ################################################################################
  $productName = ($conf{PRODUCT_NAME} =~ /[a-z]+/i ? $conf{PRODUCT_NAME} : "unknown");
  $productDisplayName = ($conf{PRODUCT_DISPLAY_NAME} =~ /[a-z]+/i ? $conf{PRODUCT_DISPLAY_NAME} : $productName);
  $versionNumber = join(".", $conf{PRODUCT_VERSION}, $jobID);
  $imageFileName = "$productDisplayName.x86_64-$versionNumber";

  ################################################################################
  # Set up directory structure.
  ################################################################################
  $workDir = initDirOrDefault($workArgDir, join("/", "/opt", "ADG", $toolName));
  $logDir = join("/", $workDir, "log", join("-", $productDisplayName, $versionNumber));
  $jobDir =  join("/", $workDir, "jobs", join("-", $productDisplayName, $versionNumber));
  $srcDir = join("/", $jobDir, "src");
  $repoDir = join("/", $jobDir, "repo");
  $imageDir = join("/", $jobDir, "image");
  $imageLogDir = join("/", $logDir);
  $buildDir = join("/", $imageDir, "build");
  $prepDir = join("/", $buildDir, "prep");

  $finishedImagesDir = initDirOrDefault($finishedImagesArgDir, join("/", "/opt", "downloads", "images", join("-", $productDisplayName, $versionNumber)));
  $finishedImagesURL = join("/", "http:/", $hostname, "downloads", "images", join("-", $productDisplayName, $versionNumber));

  validateUpdateMedia() if (defined $actionUpdateMedia);

  mkpath $jobDir;
  mkpath $srcDir;
  mkpath $prepDir;
  mkpath $repoDir;
  mkpath $imageDir;
  mkpath $imageLogDir;
  mkpath $logDir unless (-d $logDir);

  if (defined $actionUpdateMedia)
  {
    $updateSrcDir = "$srcDir/applianceUpdate";
    $updateMediaDir = "$srcDir/updateMedia";

    runSystemCmd(join(" ", "$rmExec -rf", $updateMediaDir), $errorCodes{IO_ERROR});
    runSystemCmd(join(" ", "$rmExec -rf", $updateSrcDir), $errorCodes{IO_ERROR});
    mkpath $updateMediaDir;
    mkpath $updateSrcDir;
  }

  ################################################################################
  # Initialize local file paths and logs.
  ################################################################################
  $logFile = join("/", $logDir, "$productDisplayName-$versionNumber.log");
  $errLogFile = join("/", $logDir, "$productDisplayName-$versionNumber.err");
  $localListFile = join("/", $srcDir, basename($conf{LIST_FILE}));
  $localConfigFile = join("/", $srcDir, basename($conf{CONFIG_FILE}));
  $localConfigFileOriginal = "${localConfigFile}.orig";

  loggersOpen();

  ################################################################################
  # Now that we have proper staging dirs, save a copy of our working conf file.
  ################################################################################
  logger($debug, "* Saving conf file * ...");
  getFiles($srcDir, $tempConfFile);
  rmtree(dirname($tempConfFile));

  ################################################################################
  # Stage needed files.
  ################################################################################
  logger($info, "* Staging appliance source files * ...");
  unless ((defined $actionUpdateMedia) && !$actionList && !$actionCreate)
  {
    ##############################################################################
    # Skip the download of LIST and BUILD_SCRIPTS when ONLY update media was set
    ##############################################################################
    getFiles($srcDir, $conf{LIST_FILE}) unless ($actionList);
    getFiles($srcDir, split(" ", $conf{BUILD_SCRIPTS})) if ($conf{BUILD_SCRIPTS});
  }

  ################################################################################
  # Stage update media files.
  ################################################################################
  if (defined $actionUpdateMedia)
  {
    my $updateFile = basename($conf{UPDATE_SCRIPTS});

    if ( -f join("/", $actionUpdateMedia, $updateFile) )
    {
      # Copy the update script from path of -u if has the same name from the one in CONF file
      getFiles($updateSrcDir, join("/", $actionUpdateMedia, $updateFile));
    }
    else
    {
      # Copy the update script from CONF file location
      getFiles($updateSrcDir, $conf{UPDATE_SCRIPTS});
    }
    # Copy the required media files
    getFiles($updateMediaDir, split(" ", $conf{UPDATE_FILES}));
  }

  ################################################################################
  # Get the build configuration file (config.xml)
  ################################################################################
  logger($debug, "Config File is: $conf{CONFIG_FILE}");
  logger($debug, "Local Config File will be: $localConfigFile");
  getFiles($srcDir, $conf{CONFIG_FILE});

  # Take a backup of the original config file.
  runSystemCmd("$moveExec $localConfigFile $localConfigFileOriginal", $errorCodes{IO_ERROR});

  ################################################################################
  # Processing the optional parameters - profiles and types
  # If profiles are specified, the types will associate to profiles
  # If profiles are not specified, take the default profile from config.xml file
  ################################################################################
  if (scalar(@profiles) <= 0)
  {
    push(@profiles, getDefaultProfile());
    $isDefaultProfile = 1;
  }

  foreach my $item (@profiles)
  {
    chomp($item);

    my @imageTypes = ();
    my $profile = $item;

    if ($item =~ /\[/)        # Enter the condition if image types specified along with profile names.
    {
      $profile = substr($item, 0, index($item, "["));
      chomp($profile);

      $item =~ /\[([^\]]*)\]/x;
      my $types = $1;
      chomp($types);

      if ($types ne "")
      {
        if ($types =~ /,/ || $types =~ / /)
        {
          $types =~ s/,/ /g;
          foreach my $type (split(" ", $types))
          {
            chomp($type);
            push(@imageTypes, $type) if ($type ne "");
          }
        }
        else
        {
          chomp($types);
          push(@imageTypes, $types) if ($types ne "");
        }
      }
    }

    foreach my $type (@types)
    {
      chomp($type);
      push(@imageTypes, $type);
    }

    $profileTypes{$profile} = [ @imageTypes ];    # Associate images types with profile

    ################################################################################
    # Checking whether any profile-specific ICC variable is defined in the conf file
    # If it is defined, then setting the flag variable $multiList to 1.
    ################################################################################
    $profileDelPackages{$profile} = [];
    my $iccProfile = "ICC_$profile";

    if ($conf{$iccProfile})
    {
      $multiList = 1;
      my @delPackages = ();
      foreach my $pkg (split(" ", $conf{$iccProfile}))
      {
        chomp($pkg);
        if ($pkg =~ /^-/)
        {
          $pkg =~ s/^-//g;
          push(@delPackages, $pkg);
        }
      }
      $profileDelPackages{$profile} = [ @delPackages ];
    }

    my $profileManifestFile = "MANIFEST_FILE_$profile";
    my $profileManifestDir = "MANIFEST_DIR_$profile";
    $multiList = 1 if ($conf{$profileManifestFile} || $conf{$profileManifestDir});

    $item = $profile;
  }

  ################################################################################
  # Display job info.
  ################################################################################
  logger($info, "* Displaying job info * ...");
  logger($info, "* This job dir is: $jobDir *");
  logger($info, "* Logs can be found here: $logDir *");
  logger($debug, "Repo dir is: $repoDir");
  logger($debug, "Source dir is: $srcDir");
  logger($debug, "Image dir is: $imageDir");

  logger($info, "* Using product name: $productDisplayName * ...");
  logger($info, "* Using Version number: $versionNumber * ...");
}

sub createList()
{
  logger($debug, "List File is: $conf{LIST_FILE}");
  logger($debug, "Local List File will be: $localListFile");

  ################################################################################
  # Stage needed list generation files and process individual lists.
  ################################################################################
  logger($info, "* Staging and processing list generation files * ...");

  ################################################################################
  # As we go along, fill in this array for which lists to combine for the final list.
  ################################################################################
  my @catLists = ();
  my @manifestLists = ();

  ################################################################################
  # First, make a list out of the MANIFEST_DIR, if specified.
  if ($conf{MANIFEST_DIR} && -d $conf{MANIFEST_DIR})
  {
    my $localManifestDirList = getManifestDirFile($conf{MANIFEST_DIR}, "");
    push(@manifestLists, $localManifestDirList);
  }

  ################################################################################
  # Second, retrieve the MANIFEST_FILE, is specified.
  if ($conf{MANIFEST_FILE})
  {
    my $localManifestFile = getManifestFile($conf{MANIFEST_FILE});
    push(@manifestLists, $localManifestFile);
  }

  ################################################################################
  # Third, process the product list using ant.
  ################################################################################
  my ($antFile, $lockFile) = split(" ", $conf{LIST_SCRIPTS});
  logger($debug, "Ant File is: $antFile");

  @catalogFiles = split(" ", $conf{CATALOG_FILES}) if $conf{CATALOG_FILES};
  getFiles($srcDir, @catalogFiles, $antFile);

  my $localAntFile = join("/", $srcDir, basename($antFile));
  logger($debug, "Local Ant File is: $localAntFile");

  if ($lockFile) # Optional
  {
    logger($debug, "Lock File is: $lockFile");
    getFiles($srcDir, $lockFile);
    logger($debug, join(" ", "Local Lock File is: ", join("/", $srcDir, basename($lockFile))));
  }
  else # By default always look in ${HOME} for a lock file to stage
  {
    logger($debug, "Looking for default Lock File in $ENV{HOME}");
    if ( -f join("/", $ENV{HOME}, "LOCK_ICC.xml") )
    {
      getFiles($srcDir, join("/", $ENV{HOME}, "LOCK_ICC.xml"));
    }
    else
    {
      logger($debug, "Default Lock File not found.  Skipping.");
    }
  }

  my $iccTarget = ($conf{ICC} ? $conf{ICC} : "");
  my $platformTarget = ($conf{PLATFORM} ? $conf{PLATFORM} : "");

  ################################################################################
  # Last, combine all lists into one final list file and save it.
  ################################################################################
  logger($info, "* Generating icc list * ...");
  open(OUTPUT, ">$localListFile") or die("Unable to open $localListFile for writing");

  if ($multiList == 1)
  {
    foreach my $profile (@profiles)
    {
      my $actualICCTarget = $iccTarget;
      my $iccProfile = "ICC_$profile";

      if ($conf{$iccProfile})
      {
        my @iccProfileTargets = split(" ", $conf{$iccProfile});
        foreach my $target (@iccProfileTargets)
        {
          chomp($target);
          if ($target =~ /^-/)
          {
            $target =~ s/^-//g;
            $actualICCTarget =~ s/[\s]$target[\s]/ /g;
            $actualICCTarget =~ s/^$target[\s]/ /g;
            $actualICCTarget =~ s/[\s]$target$/ /g;
          }
          else
          {
            $actualICCTarget = "$actualICCTarget $target";
          }
        }
      }

      my $antTarget = "clean prep $platformTarget $actualICCTarget sort_icc_list";
      logger($debug, "Using $localAntFile with target string: $antTarget");
      runSystemCmd(join(" ", $antExec, "-f", $localAntFile, $antTarget), $errorCodes{ANT_ERROR});
      push(@catLists, join("/", $srcDir, "adg_icc_packages.list"));

      my @profileManifestLists = ();
      ################################################################################
      # Make a list out of the MANIFEST_DIR_<profile name>, if specified.
      my $profileManifestDir = "MANIFEST_DIR_$profile";
      if ($conf{$profileManifestDir} && -d $conf{$profileManifestDir})
      {
        my $localManifestDirList = getManifestDirFile($conf{$profileManifestDir}, ".$profile");
        push(@profileManifestLists, $localManifestDirList);
      }

      ################################################################################
      # Retrieve the MANIFEST_FILE_<profile name>, if specified.
      my $profileManifestFile = "MANIFEST_FILE_$profile";
      if ($conf{$profileManifestFile})
      {
        my $localManifestFile = getManifestFile($conf{$profileManifestFile});
        push(@profileManifestLists, $localManifestFile);
      }

      push(@catLists, @manifestLists);
      if (scalar(@profileManifestLists) > 0)
      {
        push(@catLists, @profileManifestLists);
      }
      else
      {
        logger($debug, "No profile-specific manifest packages exist for the build.");
      }

      print OUTPUT "$profileStartString $profile ##\n";
      foreach my $list (@catLists)
      {
        open(INPUT, "$list") or die("Unable to open $list for reading");
        while (<INPUT>)
        {
          my $pkg = $_;
          print OUTPUT $pkg if ($pkg ne "\n");
        }
        close(INPUT);
      }

      print OUTPUT "$profileEndString $profile ##\n";
      @catLists = ();
    }
  }
  else
  {
    my $antTarget = "clean prep $platformTarget $iccTarget sort_icc_list";

    logger($debug, "Using $localAntFile with target string: $antTarget");
    runSystemCmd(join(" ", $antExec, "-f", $localAntFile, $antTarget), $errorCodes{ANT_ERROR});
    push(@catLists, join("/", $srcDir, "adg_icc_packages.list"));
    push(@catLists, @manifestLists);

    foreach my $list (@catLists)
    {
      open(INPUT, "$list") or die("Unable to open $list for reading");
      print OUTPUT $_ while (<INPUT>);
      close(INPUT);
    }
  }
  close(OUTPUT);

  logger($info, "* Saving final list file * ...");
  putFiles(dirname($conf{LIST_FILE}), $localListFile);
}


# This subroutine gets the list of RPMs inside the manifest directory supplied to it as a parameter
# and generates a local temporary list file containing the absolute path of the package name.
# Returns the absolute path of the local temporary file created.
sub getManifestDirFile()
{
  my ($manifestDir, $profile) = @_;
  chomp ($manifestDir);

  logger($info, "* Generating manifest list from the specified directory * ...");
  my $localManifestDirList = join("/", $srcDir, "local_manifest_dir" . ".$profile" . ".list");
  logger($debug, "Manifest Dir - $manifestDir found.  Generating $localManifestDirList file.");
  open(MANIFEST_DIR_LIST, ">$localManifestDirList") or die("Unable to open $localManifestDirList for writing");
  print MANIFEST_DIR_LIST "** LOCAL - $_\n" while (<$manifestDir/*.rpm>);
  close(MANIFEST_DIR_LIST);
  return $localManifestDirList;
}

# This subroutine downloads the manifest file to the local build directory and returns the
# absolute local path of the file.
sub getManifestFile()
{
  my $manifestFile = shift;

  logger($info, "* Processing manifest file * ...");
  logger($debug, "Manifest File is: $manifestFile");
  getFiles($srcDir, $manifestFile);
  my $localManifestFile = join("/", $srcDir, basename($manifestFile));
  logger($debug, "Local Manifest File is: $localManifestFile");
  return $localManifestFile;
}

# This function encapsulates all the logic which starts from picking the packages
# mentioned in the list file(s) and the manifest file(s), staging them, creating the
# appropriate local repositories and creating the image(s) using kiwi
sub create()
{
  my $profile = "";
  my $type = "";
  my @profileTypes = undef;

  logger($debug, "List File is: $conf{LIST_FILE}");
  logger($debug, "Local List File will be: $localListFile");
  logger($debug, "Config File is: $conf{CONFIG_FILE}");
  logger($debug, "Local Config File will be: $localConfigFile");

  ################################################################################
  # Initiate image creation using the validated config file.
  ################################################################################
  foreach my $profile (@profiles)
  {
    chomp($profile);
    next if ($profile eq "");

    logger($info, "* Processing profile: $profile * ...");

    stage($profile);
    modifyAndValidateConfig($profile);

    my @imageTypes = @{$profileTypes{$profile}};
    if (scalar(@imageTypes) > 0)
    {
      foreach my $type (@imageTypes)
      {
        prepareImage($profile, $type);
        createImage($profile, $type);
      }
    }
    else
    {
      prepareImage($profile, "");
      createImage($profile, "");
    }
  }
}

# This function validates the required input to create update media files. It will be usefull to validate these values
# before appliance creation, so if there is a situation when the user request to update media, but no scripts
# are provided, the user would not have to wait the appliance to be created to find out there was an error.
sub validateUpdateMedia()
{
  ################################################################################
  # Update scripts won't work without update script/media files.
  ################################################################################
  if (!$conf{UPDATE_SCRIPTS} || !$conf{UPDATE_FILES})
  {
    logger($error, "Unable to generate update media without UPDATE_SCRIPTS and UPDATE_FILES in conf file");
    logger($error, "Operation aborted.  Exiting with error code: $errorCodes{UPDATE_ERROR}");
    exitClean($errorCodes{UPDATE_ERROR});
  }

  ################################################################################
  # Update scripts won't work without RPM files on update location.
  ################################################################################
  if ($actionUpdateMedia)
  {
    if (! -d $actionUpdateMedia)
    {
      logger($error, "Unable to locate directory with RPM packages");
      exitUsage("[$error]: The directory with RPMs could not be located or it does not have read permissions.");
    }

    my $packages = `$findExec $actionUpdateMedia -iname *.rpm -exec $rpmExec -qp --nosignature --qf '%{n}\\n' {} \\;`;
    if (! $packages)
    {
      logger($error, "Could not find any package on update folder.");
      exitUsage("[$error]: The directory with RPMs does not contain any valid RPM file.");
    }
  }
  ################################################################################
  # Verify if the image directory of the job exists.
  ################################################################################
  else
  {
    my $packages = `$findExec $imageDir -iname *.rpm -exec $rpmExec -qp --nosignature --qf '%{n}\\n' {} \\;` if (-d $imageDir);
    exitUsage("[$error]: Appliance image directory does not contain RPMs to create the updateMedia files.") if ( ! $packages && !$actionCreate);
  }
}

# This function creates the updateMedia files from the scripts and files found on conf for each
# type/profile requested
sub updateMedia()
{
  my $profile = "";

  ################################################################################
  # Initiate update media creation.
  ################################################################################
  foreach my $profile (@profiles)
  {
    chomp($profile);
    next if ($profile eq "");

    logger($info, "* Processing profile: $profile * ...");

    my @filter = @types;
    @filter = grep(!/base/, @filter);
    @filter = grep(!/root/, @filter);

    # Update media is created when:
    # 1) no types are provided (default type used then)
    # 2) provided types filtered contain at least a non base/root type
    if (scalar(@types) == 0 || scalar(@filter) > 0)
    {
      createUpdateMedia($profile);
    }
  }
}

# This function stages the packages and the source files locally for creating the image.
sub stage()
{
  my $profile = shift;

  ################################################################################
  # Process the list file.
  ################################################################################
  # Read in and populate packageList array
  my @packageList = ();
  my @localPackageList = ();
  my @lines = ();

  @packageNames = ();
  @isoList = ();
  @localPackageNames = ();

  open(INPUT, $localListFile) or die ("Unable to open $localListFile for reading");
  while(<INPUT>)
  {
    if (/^$profileStartString\s$profile\s##$/../^$profileEndString\s$profile\s##$/)
    {
      next if /^$profileStartString\s$profile\s##$/ || /^$profileEndString\s(.*)\s##$/;
      push(@lines, $_);
    }
  }
  close(INPUT);

  if (scalar(@lines) == 0)
  {
    open(INPUT, $localListFile) or die ("Unable to open $localListFile for reading");
    @lines = (<INPUT>);
    close(INPUT);
  }

  foreach my $line (@lines)
  {
    chomp($line);

    if ($line =~ /^http:.*\.rpm$/)
    {
      # If it starts with http and ends with .rpm then this is a full URL entry and should be staged to a local repository
      push(@packageList, $line);
      logger($debug, "--> Adding to list: $line");
    }
    elsif ($line =~ /^\*\*\sISO\s-\s(.*)\s-(.*)/)
    {
      # Anything marked as ** ISO should be referenced by name from the ISO repository
      my $isoPackage = $1;
      chomp $isoPackage;
      push(@isoList, $isoPackage);
    }
    elsif ($line =~ /^\*\*\sLOCAL\s-\s(.*)/)
    {
      # Anything marked as ** LOCAL should be staged to a local repository
      my $localPackage = $1;
      chomp $localPackage;
      push(@localPackageList, $localPackage);
      logger($debug, "--> Adding to list: $localPackage");
    }
    elsif ($line =~ /^\*\*\sJPP/)
    {
      # Ignore these for now.
      # These are the old JPP packages that needed to be hardcoded into the build profile specifically to order the dependencies (tomcat's ecj).
    }
    elsif ($line =~ /^\*\*\sJeOS/)
    {
      # Ignore these for now.
      # This is how we keep JeOS packages in ADG_ICC.xml without ever adding them to OSPackages in build profile.
      # Because these packages are already added in the JeOS OSPackages section of the build profile (the bottom section).
    }
    elsif ($line =~ /\.rpm$/)
    {
      # If it ends with .rpm but doesn't start with http then it's not a full URL entry.
      # Ignore these.
      # Legacy.
    }
    else
    {
      logger($warning, "Un-parse-able line: $line") unless (/^\s*$/);
    }
  }

  ################################################################################
  # Stage the rpms.
  ################################################################################
  logger($info, "* Staging application packages * ...");
  my $profileRepoDir = "$repoDir/$profile";
  mkpath $profileRepoDir;

  foreach my $currentPackage (@packageList)
  {
    my $currentName = basename($currentPackage);
    logger($debug, "\n"); # for readability
    logger($debug, "Processing $currentName");
    runSystemCmd(join(" ", "$wgetExec -q", $currentPackage, "-P", $profileRepoDir), $errorCodes{LIST_ERROR});
    push(@packageNames, `$rpmExec -qp --nosignature --qf='%{n}' $profileRepoDir/$currentName`);
  }

  foreach my $currentPackage (@localPackageList)
  {
    my $currentName = basename($currentPackage);
    logger($debug, "\n"); # for readability
    logger($debug, "Processing $currentName");
    getFiles($profileRepoDir, $currentPackage);
    push(@localPackageNames, `$rpmExec -qp --qf='%{n}' $profileRepoDir/$currentName`);
  }

  ################################################################################
  # Generate repo metadata.
  ################################################################################
  logger($info, "* Generating application repository * ...");
  runSystemCmd("$createRepoExec $profileRepoDir", $errorCodes{IO_ERROR});
}


# This function pulls up all the package names required for a particular image
# and populates the <packages> section in the config.xml with the package names.
# It then validates the config.xml file before triggering the kiwi build process.
sub modifyAndValidateConfig()
{
  ################################################################################
  # Fill in config.xml.
  #
  # Add the local repo.
  # Dump in package names.
  ################################################################################
  my $profile = shift;

  # Write the new/working config file.
  logger($info, "* Populating config file * ...");
  open(BEFORE, "$localConfigFileOriginal") or die ("Unable to open $localConfigFileOriginal for reading");
  open(AFTER, ">$localConfigFile")  or die ("Unable to open $localConfigFile for writing");
  while (<BEFORE>)
  {
    my $line = $_;
    chomp $line;
    if ($line =~ /(\s*)<repository.*type=.yast2.*/)
    {
      # Inserting before the distribution repository.
      # That specific element should be unique and always present.
      print AFTER "$1<repository type=\"rpm-md\" priority=\"1\">\n";
      print AFTER "$1        <source path=\"$repoDir/$profile\"/>\n";
      print AFTER "$1</repository>\n";
      print AFTER "$line\n";
    }
    elsif ($line =~ /(\s*)<packages type=.image. profiles=.$profile.>.*/)
    {
      # Insert package names into the corresponding profile packages element.
      # That specific element should be unique and typically left empty in the template.
      print AFTER "$line\n";
      foreach my $packageName (@packageNames, @isoList, @localPackageNames)
      {
        print AFTER "$1\t<package name=\"$packageName\"/>\n";
      }
    }
    elsif ($line =~ /(\s*)<packages type=.delete. profiles=.$profile.>.*/)
    {
      # Insert package names into the corresponding profile packages element
      # to delete them while creating the image.
      print AFTER "$line\n";
      foreach my $packageName (@{$profileDelPackages{$profile}})
      {
        print AFTER "$1\t<package name=\"$packageName\"/>\n";
      }
    }
    else
    {
      $line = replaceTokens($line);
      print AFTER "$line\n";
    }
  }
  close(AFTER);
  close(BEFORE);

  # Copy the modified config for debugging purposes suffixed with the profile name
  runSystemCmd(join(" ", $copyExec, "-f", $localConfigFile, "$localConfigFile.$profile"), $errorCodes{IO_ERROR});

  ################################################################################
  # Validate the modified config file a kiwi command.
  ################################################################################
  logger($info, "* Validating config * ...");
  runSystemCmd(join(" ", "$kiwiExec --check-config", $localConfigFile), $errorCodes{CONFIG_ERROR});
}

# This function reads the config.xml file and gets the default profile.
sub getDefaultProfile()
{
  my @configProfiles = ();
  open(CONFIG, "$localConfigFileOriginal") or die ("Unable to open $localConfigFileOriginal for reading");
  while(<CONFIG>)
  {
    if (/<profiles>/../<\/profiles>/)
    {
      next if /<profiles>/ || /<\/profiles>/;

      push(@configProfiles, $_);
    }
  }

  foreach my $configProfile (@configProfiles)
  {
    if ($configProfile =~ /import="true"/)
    {
      my @attribs=split(" ", $configProfile);
      foreach my $attrib (@attribs)
      {
        if ($attrib =~ /^name=/)
        {
          $attrib =~ s/name="//g;
          $attrib =~ s/"//g;
          chomp($attrib);

          return $attrib;
        }
      }
    }
  }

  close(CONFIG);
  return "";
}

# This function creates the root overlay structure based on the profile and type of the image
# to be created. Kiwi, then installs the packages on to this root folder and runs the config.sh
# script (post install script). It uses the "kiwi --prepare" command to create the root tree image.
sub prepareImage()
{
  my ($profile, $type) = @_;
  my $profileCmd = "";
  my $typeCmd = "";
  my $actualPrepDir = $prepDir;

  logger($info, "* Preparing image * ...");
  $profileCmd = "--add-profile $profile";

  if ($type ne "" && $type ne "root" && $type ne "base")
  {
    $typeCmd = "--type $type";
    logger($info, "* Using image type: $type * ...");
  }

  #############################################################################################
  # Copy root folder to overlay any custom files on the system. (specifically for Oracle Linux)
  #############################################################################################
  if ($conf{ROOT_DIR})
  {
    logger($info, "* Copying root overlay * ...");
    getFiles($srcDir, $conf{ROOT_DIR});
  }

  if ($forceDebug)
  {
    $actualPrepDir = "$actualPrepDir/$profile";
    $actualPrepDir = "$actualPrepDir/$type" if ($type ne "");
    mkpath $actualPrepDir;
  }

  ################################################################################
  # Make config.sh inactive, so the script is never called twice.
  ################################################################################
  if ($type eq "root") {
    my $configFilePre = "${srcDir}/config.sh";
    my $configFilePos = "${srcDir}/config.sh.orig";

    logger($debug, "* De-activating the config.sh file...");
    runSystemCmd("$moveExec $configFilePre $configFilePos", $errorCodes{IO_ERROR});
  }

  ################################################################################
  # Calling the kiwi command to prepare the image
  ################################################################################
  runSystemCmd(join(" ", $kiwiExec, "-p", $srcDir, "--root", $actualPrepDir, $profileCmd, $typeCmd, "--force-new-root"), $errorCodes{KIWI_ERROR});

  ################################################################################
  # Restoring config.sh back, so it can be used later.
  ################################################################################
  if ($type eq "root") {
    my $configFilePre = "${srcDir}/config.sh";
    my $configFilePos = "${srcDir}/config.sh.orig";

    logger($debug, "* Re-activating the config.sh file...");
    runSystemCmd("$moveExec $configFilePos $configFilePre", $errorCodes{IO_ERROR});
  }
}


# From the root folder structure "prepared", Kiwi generates the final image file.
# This function creates the image file based on the profile and the image type.
# passed to the function. It uses the "kiwi --create" command to create the image file.
sub createImage()
{
  my ($profile, $imageType) = @_;
  my $profileCmd = "";
  my $typeCmd = "";
  my $currImageDir = $imageDir;
  my $actualPrepDir = $prepDir;
  $actualPrepDir = "$actualPrepDir/$profile" if ($forceDebug);

  logger($info, "* Creating image * ...");

  $currImageDir = join("/", $currImageDir, $profile);
  $profileCmd = "--add-profile $profile";

  if ($imageType ne "")
  {
    $actualPrepDir = "$actualPrepDir/$imageType" if ($forceDebug);
    $currImageDir = join("/", $currImageDir, $imageType);
    $typeCmd = "--type $imageType";
  }
  else
  {
    my $kiwiProfileEnv = "$actualPrepDir/.profile";
    my %kiwiProfile = ParseConfig(-ConfigFile => $kiwiProfileEnv, -InterPolateVars => 1, -InterPolateEnv => 1);

    $imageType = $kiwiProfile{kiwi_type};
  }

  $imageType =~ s/'//g;

  # Moving the prep.log created in the prepare step to the image log directory
  runSystemCmd(join(" ", $moveExec, "$actualPrepDir/../*.log", "$imageLogDir/prep-$profile-$imageType.log"));

  logger($info, "* Using image type: $imageType * ...");
  mkpath $currImageDir;

  if ($imageType eq "root" || $imageType eq "base") {
    # root/base are ADG compressed image trees that replace kiwi create step call, creating tbz files:
    # the command in KIWITarArchiveBuilder.pm is "cd $origin && $tar -cjf $tarDestDir/$imgFlName --exclude=./image . 2>&1"
    runSystemCmd(join(" ", "cd", "$actualPrepDir", "&&", "$tarExec", "-cjf", "$currImageDir/$imageFileName.$imageType.tbz", "."), $errorCodes{IO_ERROR});
  }
  else
  {
    runSystemCmd(join(" ", $kiwiExec, "-c", $actualPrepDir, "-d", $currImageDir, $profileCmd, $typeCmd), $errorCodes{KIWI_ERROR});

    # Moving the create.log created in the create step to the image log directory
    runSystemCmd(join(" ", $moveExec, "$actualPrepDir/../*.log", "$imageLogDir/create-$profile-$imageType.log"));
  }

  my $imageFile = "";
  my $imageFilePrefix = "";
  my $imageFileSuffix = "";
  my $imageFileExt = "";
  my $currImageFormat = "";

  my $buildInfo = getBuildInfo($currImageDir);
  $currImageFormat = $buildInfo->val("main", "image.format") if (defined $buildInfo);

  if ($imageType eq "vmx" && ($currImageFormat eq "vmdk" || $currImageFormat eq ""))
  {
    my @ovfVmdkPair = processVMX($currImageDir, $profile);
    push(@imageFiles, @ovfVmdkPair);
  }
  elsif ($imageType eq "oem")
  {
    $imageFilePrefix = $imageFileName;
    $imageFileSuffix = ".install";
    $imageFileExt = "iso";
  }
  elsif ($imageType eq "docker")
  {
    $imageFilePrefix = $productDisplayName;
    $imageFileSuffix = "-docker.x86_64-$versionNumber";
    $imageFileExt = "tbz";
  }
  elsif ($imageType eq "root" || $imageType eq "base")
  {
    $imageFilePrefix = $imageFileName;
    $imageFileExt = join(".", $imageType, "tbz");
  }
  else
  {
    $imageFilePrefix = $imageFileName;
    $imageFileExt = $imageType;
  }

  if ($currImageFormat ne "vmdk")
  {
    $imageFile = join("/", $currImageDir, "$imageFilePrefix$imageFileSuffix.$imageFileExt");
    my $fileName = basename($imageFile);

    if (scalar(grep(/$fileName$/, @imageFiles)) > 0)
    {
      my $modifiedImageFile = join("/", $currImageDir, "$imageFilePrefix$imageFileSuffix-$profile.$imageFileExt");
      runSystemCmd(join(" ", $moveExec, $imageFile, $modifiedImageFile), $errorCodes{IO_ERROR});
      push(@imageFiles, $modifiedImageFile);
    }
    else
    {
      push(@imageFiles, $imageFile);
    }
  }

  convertToFormats($currImageDir, $currImageFormat, $profile, $imageType) if ($#formats > 0);

  logger($info, "* Image(s) created successfully * ...");
}

# This function reads the whole kiwi.buildinfo file created during the kiwi image creation process
# and returns it as a hash object
sub getBuildInfo()
{
  my $currImageDir = shift;
  my $kiwiBuildInfoFile = "$currImageDir/kiwi.buildinfo";
  my $buildInfo = undef;

  if (-f $kiwiBuildInfoFile)
  {
    $buildInfo = Config::IniFiles->new(-file => $kiwiBuildInfoFile, -allowedcommentchars => '#');
  }
  else
  {
    logger($debug, "Kiwi build information file not found. You may be using an older version of Kiwi.");
  }

  return $buildInfo;
}

# This subroutine gets the raw image from the image output directory and converts
# it into the other formats requested by the user
sub convertToFormats()
{
  my ($currImageDir, $currImageFormat, $currProfile, $currImageType) = @_;
  my $rawImageFile = "$currImageDir/$imageFileName.raw";

  if (-f $rawImageFile)
  {
    foreach my $format (@formats)
    {
      chomp($format);
      if ($format ne $currImageFormat)
      {
        logger($info, "* Generating $format image * ...");
        runSystemCmd(join(" ", $kiwiExec, "--convert", $rawImageFile, "--format", $format), $errorCodes{KIWI_ERROR});

        $format =~ s/-//g;
        my $convertedFileName = "$imageFileName.$format";
        if (scalar(grep(/$convertedFileName$/, @imageFiles)) > 0)
        {
          $convertedFileName = "$currImageDir/$imageFileName-$currProfile-$currImageType.$format";
          runSystemCmd(join(" ", $moveExec, "$currImageDir/$imageFileName.$format", $convertedFileName), $errorCodes{IO_ERROR});
        }
        else
        {
          $convertedFileName = "$currImageDir/$imageFileName.$format";
        }

        push(@imageFiles, $convertedFileName);
      }
    }
  }
  else
  {
    logger($debug, "* No raw file found for profile: $currProfile and type: $currImageType. * ...");
    logger($debug, "* Unable to convert to other formats. * ...");
  }
}

# This function is used to create the update media from the values parsed from conf.
sub createUpdateMedia()
{
  my $profile = shift;
  my $currImageDir = $imageDir;
  my $applianceUpdateFileName = "applianceUpdate";

  logger($info, "* Creating update media * ...");
  runSystemCmd(join(" ", "$rmExec -rf", "$currImageDir/applianceUpdate"), $errorCodes{IO_ERROR});

  my $updateRepoDir = "$currImageDir/applianceUpdate/repo";
  my $applianceUpdateFile = "$applianceUpdateFileName.zip";

  if (scalar(grep(/$applianceUpdateFile$/, @imageFiles)) > 0)
  {
    $applianceUpdateFile = "$applianceUpdateFileName-$profile.zip";
  }
  createUpdateRepo($profile, $updateRepoDir);
  my $applianceUpdateZipFile = generateUpdateMedia($currImageDir, $applianceUpdateFile);
  push(@imageFiles, $applianceUpdateZipFile);

  logger($info, "* Update media created successfully * ...");
}

# This function processes the vmx image created out of the kiwi build process
# to generate the ovf/vmdk pair.
sub processVMX()
{
  my ($currImageDir, $profile) = @_;
  my @tmpImageFiles = ();

  my $imageFile = join("/", $currImageDir, "$imageFileName.vmx");
  my $ovfDir = join("/", $currImageDir, "ovf");
  my $ovfFile = "$imageFileName.ovf";
  my $ovaFile = "";
  my $mfFile = "";

  if (scalar(grep(/$ovfFile$/, @imageFiles)) > 0)
  {
    $ovfFile = join("/", $ovfDir, "$imageFileName-$profile.ovf");
    $ovaFile = join("/", $ovfDir, "$imageFileName-$profile.ova");
    $mfFile = join("/", $ovfDir, "$imageFileName-$profile.mf");
  }
  else
  {
    $ovfFile = join("/", $ovfDir, "$imageFileName.ovf");
    $ovaFile = join("/", $ovfDir, "$imageFileName.ova");
    $mfFile = join("/", $ovfDir, "$imageFileName.mf");
  }

  mkpath $ovfDir;

  getFiles($srcDir, $conf{OVF_TEMPLATE});
  my $localOvfTemplateFile = join("/", $srcDir, basename($conf{OVF_TEMPLATE}));

  ################################################################################
  # ovf generation using ovftool
  ################################################################################
  logger($info, "* Generating OVF file * ...");
  runSystemCmd(join(" ", $ovftoolExec, $imageFile, $ovfFile), $errorCodes{OVF_ERROR});

  ################################################################################
  # Parsing the ovf file generated from ovftool
  ################################################################################
  logger($debug, "Reading ovf file generated from ovftool");
  my($references, $diskSection, $networkSection, $virtualHardwareSection);
  open(OVFFILE, "$ovfFile") or die ("Unable to open $ovfFile for reading: $!");
  while(<OVFFILE>)
  {
    my $line = $_;
    chomp $line;
    if (/<References>/../<\/References>/)
    {
      next if /<References>/ || /<\/References>/;
      $references =  $references . $_;
    }
    if (/<DiskSection>/../<\/DiskSection>/)
    {
      next if /<DiskSection>/ || /<\/DiskSection>/;
      $diskSection = $diskSection . $_;
    }
    if (/<NetworkSection>/../<\/NetworkSection>/)
    {
      next if /<NetworkSection>/ || /<\/NetworkSection>/;
      $networkSection = $networkSection . $_;
    }
    if (/<VirtualHardwareSection>/../<\/VirtualHardwareSection>/)
    {
      next if /<VirtualHardwareSection>/ || /<\/VirtualHardwareSection>/;
      $virtualHardwareSection = $virtualHardwareSection . $_;
    }
  }
  chomp($references, $diskSection, $networkSection, $virtualHardwareSection);
  close(OVFFILE);

  # Save/move the ovf from the tool, in the next step it will be rewritten
  runSystemCmd(join(" ", $moveExec, $ovfFile, join("/", $currImageDir, basename($ovfFile))), $errorCodes{IO_ERROR});

  ##############################################################################
  # Write to the ovf template file
  ##############################################################################
  open(OVFTEMPLATE, "$localOvfTemplateFile") or die("Can't open $localOvfTemplateFile for reading: $!");
  open(OVFOUT, ">$ovfFile") or die("Can't open $ovfFile for writing: $!");
  for (<OVFTEMPLATE>)
  {
    s/__REFERENCES__/$references/;
    s/__DISKSECTION__/$diskSection/;
    s/__NETWORKSECTION__/$networkSection/;
    s/__VIRTUALHARDWARE__/$virtualHardwareSection/;
    s/__NAME__/$productName/;
    s/__VERSION__/$versionNumber/;
    print OVFOUT;
  }
  close(OVFOUT);
  close(OVFTEMPLATE);

  ################################################################################
  # Converting the ovf file to ova using ovftool
  ################################################################################
  if ($ovaConversion)
  {
    my $cmd = join(" ", $ovftoolExec, join("/", $currImageDir, basename($ovfFile)), $ovaFile);
    logger($info, "* Generating OVA file * ... $cmd");
    runSystemCmd(join(" ", "$rmExec -rf", $mfFile), $errorCodes{IO_ERROR});
    runSystemCmd(join(" ", $ovftoolExec, $ovfFile, $ovaFile), $errorCodes{OVF_ERROR});
    runSystemCmd(join(" ", "chmod", "644", $ovaFile), $errorCodes{IO_ERROR});
    
    @tmpImageFiles = (<$ovfDir/*.ova>);
  }
  else
  {
    @tmpImageFiles = (<$ovfDir/*.ovf>, <$ovfDir/*.vmdk>);
  }

  return @tmpImageFiles;
}


sub createUpdateRepo()
{
  my ($profile, $updateRepoDir) = @_;

  runSystemCmd(join(" ", "$rmExec -rf", $updateRepoDir), $errorCodes{IO_ERROR});
  mkpath $updateRepoDir;

  my $contentTemplate = "$updateMediaDir/contentTemplate";
  my $patternTemplate = "$updateMediaDir/patternTemplate.pat";
  my $passphraseFile = "$updateMediaDir/passphrase";
  my $pubKeyFile = "$updateMediaDir/pubring.gpg";
  my $secKeyFile = "$updateMediaDir/secring.gpg";

  my $mediaDir = "$updateRepoDir/media.1";
  my $suseDescrDir = "$updateRepoDir/suse/setup/descr";
  my $contentFile = "$updateRepoDir/content";
  my $patternsFile = "$suseDescrDir/patterns";
  my $patternFile = "$suseDescrDir/$productDisplayName-update-$versionNumber-0.x86_64.pat";
  my $pkgCacheDir = "";

  if ($actionUpdateMedia)
  {
    $pkgCacheDir = $actionUpdateMedia;
  }
  elsif ($forceDebug)
  {
    $pkgCacheDir = join("/", $prepDir, "packages");
  }
  else
  {
    $pkgCacheDir = join("/", $buildDir, "packages");
  }

  logger($debug, "Started creating update repo.");
  logger($debug, "Creating the directory structure on $pkgCacheDir...");

  mkpath $mediaDir;
  mkpath $suseDescrDir;

  runSystemCmd("touch $mediaDir/media", $errorCodes{IO_ERROR});

  logger($debug, "Creating the content file...");
  open(CONTENTTEMPLATE, "$contentTemplate") or die("Can't open $contentTemplate for reading: $!");
  open(CONTENTOUT, ">$contentFile") or die("Can't open $contentFile for writing: $!");
  for (<CONTENTTEMPLATE>)
  {
    s/__LABEL__/$productName/;
    s/__SHORTLABEL__/$productName/;
    s/__NAME__/$productName/;
    s/__VERSION__/$versionNumber/;
    s/__RELEASE__/0/;
    print CONTENTOUT;
  }
  close(CONTENTOUT);
  close(CONTENTTEMPLATE);

  foreach my $arch (("x86_64", "noarch", "i386", "i586", "i686"))
  {
    my $archDir = "$updateRepoDir/suse/$arch";
    my @rpmList = ();
    my @actualRPMList = ();
    my @delPackages = ();

    @delPackages = @{$profileDelPackages{$profile}} if (%profileDelPackages);

    find sub {push @rpmList,$File::Find::name if /\.$arch\.rpm/}, $pkgCacheDir;
    if (scalar @rpmList > 0)
    {
      logger($debug, "Copying the $arch based packages...");
      mkpath $archDir;

      foreach my $rpm (@rpmList)
      {
        my $rpmName = `$rpmExec -qp --nosignature --qf '%{n}' $rpm`;

        if (scalar(grep(/$rpmName$/, @delPackages)) <= 0)
        {
          push(@actualRPMList, $rpm) unless ($rpm eq "");
        }
      }
      getFiles($archDir, @actualRPMList);
    }
  }

  logger($debug, "Creating the pattern file...");
  my $packages = `$rpmExec -qp --nosignature --qf '%{n}\n' $updateRepoDir/suse/*/*.rpm | sort`;

  open(PATTERNTEMPLATE, "$patternTemplate") or die("Can't open $patternTemplate for reading: $!");
  open(PATTERNOUT, ">$patternFile") or die("Can't open $patternFile for writing: $!");
  for (<PATTERNTEMPLATE>)
  {
    s/__NAME__/$productName-update/;
    s/__VERSION__/$versionNumber/;
    s/__RELEASE__/0/;
    s/__ARCH__/x86_64/;
    s/__PACKAGES__/$packages/;
    print PATTERNOUT;
  }
  close(PATTERNOUT);
  close(PATTERNTEMPLATE);

  logger($debug, "Creating the package descriptions...");
  runSystemCmd(join(" ", "/usr/bin/create_package_descr", "-V", "-d", "$updateRepoDir/suse/", "-o", $suseDescrDir), $errorCodes{IO_ERROR});

  chdir($suseDescrDir);

  logger($debug, "Zipping the pattern files...");
  runSystemCmd(join(" ", "$gzipExec", $patternFile), $errorCodes{IO_ERROR});
  runSystemCmd(join(" ", "ls", "-A1", "*.pat.gz", ">", $patternsFile), $errorCodes{IO_ERROR});

  logger($debug, "Compressing the package files...");
  runSystemCmd(join(" ", $gzipExec, "packages"), $errorCodes{IO_ERROR});
  runSystemCmd(join(" ", $gzipExec, "packages.en"), $errorCodes{IO_ERROR});
  runSystemCmd(join(" ", $gzipExec, "packages.DU"), $errorCodes{IO_ERROR});

  logger($debug, "Generating the SHA1 Checksums...");
  open(CONTENT, ">>$contentFile") or die("Can't open $contentFile for writing: $!");
  foreach my $sha1Value (`sha1sum *.pat.gz packages* patterns`)
  {
    print CONTENT join(" ", "META SHA1", $sha1Value);
  }
  close(CONTENT);

  chdir($runDir);
  runSystemCmd(join(" ", $copyExec, $pubKeyFile, $updateRepoDir), $errorCodes{IO_ERROR});

  logger($debug, "Signing the repo...");
  my $keyString = "--no-default-keyring --secret-keyring $secKeyFile --keyring $pubKeyFile";

  my $signKey=`gpg $keyString --list-secret-keys | grep '^sec' | sed -e 's/.*\\///;s/ .*//g;' | tail -n 1`;
  chomp($signKey);
  my $result=`gpg $keyString --passphrase-fd 0 --detach-sign -u "$signKey" --batch --yes -o "$updateRepoDir/content.asc" -a "$updateRepoDir/content" < $passphraseFile`;
  $result=`gpg $keyString --export -a -u "$signKey" > "$updateRepoDir/content.key"`;

  runSystemCmd(join(" ", "ls", "-A1", "-p", "$suseDescrDir", "|", "grep", "-v", "'directory.yast'", ">", "$suseDescrDir/directory.yast"), $errorCodes{IO_ERROR});
  runSystemCmd(join(" ", "ls", "-A1", "-p", "$updateRepoDir", "|", "grep", "-v", "'directory.yast'", ">", "$updateRepoDir/directory.yast"), $errorCodes{IO_ERROR});

  logger($debug, "Update repo created.");
}


# This function generates the update media in the form of a zip file.
sub generateUpdateMedia()
{
  my ($currImageDir, $applianceUpdateFileName) = @_;

  getFiles("$currImageDir/applianceUpdate", glob "$updateSrcDir/*");

  logger($debug, "* Creating archive from Update repository * ...");
  chdir($currImageDir) or die "$!";

  $applianceUpdateFileName = join("/", $currImageDir, $applianceUpdateFileName);
  runSystemCmd(join(" ", "$zipExec -r", $applianceUpdateFileName, "applianceUpdate/"), $errorCodes{IO_ERROR});

  chdir($runDir) or die "$!";
  return $applianceUpdateFileName;
}


# This function moves the image files to a local web-share folder in the DevKit
sub moveImages()
{
  my $count = @imageFiles;
  if ($count > 0)
  {
    logger($info, "* Transferring image(s) locally to $finishedImagesDir * ...");
    getFiles($finishedImagesDir, @imageFiles);

    $imageMoved = 1;
  }
}


# This function moves the image files to a product share location outside the DevKit.
sub blessImages()
{
  my $blame = join(" ", " $productDisplayName-$versionNumber was blessed by $user on $hostname at", join("-", grabDate(), grabTime()));
  my $blameFile = "/disks/adgbuild/blame/blame"; # Super secret!

  if ( -w $blameFile )
  {
    open(BLAME, ">>$blameFile") or warn("Unable to open blame file for writing!");
    print BLAME "BLESS: $blame\n";
    print BLAME "BLESS: $conf{PRODUCT_SHARE_DIR}\n";
    close(BLAME);
  }
  logger($debug, "BLESS: $blame");
  logger($debug, "BLESS: $conf{PRODUCT_SHARE_DIR}");

  ################################################################################
  # Bless the image by transferring it to a designated location.
  ################################################################################
  logger($info, "* Transferring image(s) to product share - $conf{PRODUCT_SHARE_DIR} * ...");

  @imageFiles = (<$finishedImagesDir/*.*>);

  ################################################################################
  # Abort if unable to find the image to bless.
  ################################################################################
  unless (@imageFiles)
  {
    logger($error, "Unable to find image files.  Nothing to bless.");
    logger($error, "Operation aborted.  Exiting with error code: $errorCodes{IO_ERROR}");
    exitClean($errorCodes{IO_ERROR});
  }

  ################################################################################
  # If checksums are requested, generate them now on the designated location.
  ################################################################################
  if ($genChecksums)
  {
    logger($info, "* Generating checksum files * ...");
    my @checksumFiles = ();
    foreach my $file (@imageFiles)
    {
      my $checksumFile = join(".", $file, "md5");
      logger($debug, "Generating md5sum of $file");
      runSystemCmd(join(" ", $checksumExec, $file, ">", $checksumFile), $errorCodes{IO_ERROR});
      push(@checksumFiles, $checksumFile);
    }
    push(@imageFiles, @checksumFiles);
  }

  ################################################################################
  # Transfer the image files.
  # This should be done entirely locally or the genChecksum code below will fail.
  ################################################################################
  putFiles($conf{PRODUCT_SHARE_DIR}, @imageFiles);

  ################################################################################
  # Let the rest of the tool know the image is safe.
  ################################################################################
  $imageBlessed = 1;

  ################################################################################
  # Auto-deploy stuff
  ################################################################################
  my $autoDeployBaseDir = "/share/ovf_release/autoDeploy";
  if ($conf{OVF_URL_PREFIX} && -w $autoDeployBaseDir)
  {
    createAutoDeployFile($autoDeployBaseDir);
  }
}

sub createAutoDeployFile()
{
  my $autoDeployBaseDir = shift;
  logger($info, "* Creating autoDeploy files * ...");

  # Add release tag if specified
  my $autoDeployDir;
  my $productOvf;
  if ($conf{RELEASABLE_BUILD} eq "yes")
  {
    $autoDeployDir = "$autoDeployBaseDir/$conf{PRODUCT_NAME}-release";
  }
  else
  {
    $autoDeployDir = "$autoDeployBaseDir/$conf{PRODUCT_NAME}";
  }

  # Add version group if specified
  $autoDeployDir = join("/" , $autoDeployDir, $conf{PRODUCT_VERSION_GROUP}) if ($conf{PRODUCT_VERSION_GROUP});
  logger($debug, "Creating autoDeploy direcotory $autoDeployDir.");
  mkpath $autoDeployDir;

  # Determine ovf location
  if ($conf{OVF_FILENAME})
  {
    $productOvf = join("/", $conf{OVF_URL_PREFIX}, $conf{OVF_FILENAME});
  }
  else
  {
    $productOvf = join("/", $conf{OVF_URL_PREFIX}, "$imageFileName.ovf");
  }

  my $ovfText = "$autoDeployDir/ovf.txt";
  my $ovfBuildText = "$autoDeployDir/ovf_"."$conf{PRODUCT_VERSION}"."\.$jobID\.txt";

  open(OVF_TXT, ">$ovfText") or die("Unable to open $ovfText file for writing: $!");
  print OVF_TXT $productOvf;
  close(OVF_TXT);

  open(OVF_BLD_TXT, ">$ovfBuildText") or die("Unable to open $ovfBuildText file for writing: $!");
  print OVF_BLD_TXT $productOvf;
  close(OVF_BLD_TXT);
}


# Subroutine section...
################################################################################
# This will attempt to run a command (string) and abort on error.
################################################################################
sub runSystemCmd
{
  my ($cmd, $code) = @_;
  logger($debug, "Command in use is: $cmd");
  $rc = 0;

  ########################################
  # If the logs are open and the command doesn't already contain a redirect,
  #  then direct the output to the logfile.
  my $logRedirect = ((loggerStatusOpen() && !($cmd =~ />/)) ? ">> $logFile" : "");

  ########################################
  # Use system to make the call, and then check the return code and
  #  abort on non-zero with the error code provided.
  $rc = system(join(" ", $cmd, $logRedirect, "2>&1")) / 256;
  logger($debug, "Return from command is: $rc");
  if ($rc ne 0)
  {
    logger($error, "A problem was encountered.  Command retured: $rc");
    logger($error, "Operation aborted.  Exiting with error code: $code");
    print "**************************************************\n";
    system("tail -50 $logFile") if (loggerStatusOpen());
    print "**************************************************\n";
    exitClean($code);
  }
}

################################################################################
# This will attempt to retrieve/copy a file or list of files.
################################################################################
# NOTE: when working entirely with local files, putFiles and getFiles are interchangeable.
sub getFiles
{
  my $destDir = shift;
  foreach my $filePath (@_)
  {
    logger($debug, "Retrieving $filePath.");
    my $fileName = basename($filePath);
    my $localFilePath = join("/", $destDir, $fileName);
    mkpath($destDir) unless ( -d $destDir);
    logger($debug, "New location will be: $localFilePath");

    if ($filePath =~ /^https/)  # Assume any secure http connection is a Subversion URL
    {
      logger($debug, "$fileName found in SVN");
      runSystemCmd(join(" ", "$svnExec export --force", $filePath, $localFilePath), $errorCodes{SVN_ERROR});
      logger($debug, "Successfully exported $fileName from SVN.");
    }
    elsif ($filePath =~ /^http/)  # Assume any non-secure http connection is a wget-able URL
    {
      logger($debug, "$fileName found remote");
      runSystemCmd(join(" ", "$wgetExec -q", $filePath, "-P", $destDir), $errorCodes{IO_ERROR});
      logger($debug, "Successfully retrieved $fileName from remote URL.");
    }
    elsif ( -d $filePath )
    {
      logger($debug, "$fileName is a directory found locally");
      runSystemCmd(join(" ", "$copyExec -rf", $filePath, $destDir), $errorCodes{IO_ERROR});
      logger($debug, "Successfully transferred $fileName from local copy.");
    }
    elsif ( -f $filePath )
    {
      logger($debug, "$fileName found locally");
      runSystemCmd(join(" ", "$copyExec -f", $filePath, $localFilePath), $errorCodes{IO_ERROR});
      logger($debug, "Successfully transferred $fileName from local copy.");
    }
    else
    {
      logger($error, "File not found:  $fileName");
      exitClean($errorCodes{IO_ERROR});
    }
  }
}

################################################################################
# This will attempt to save/copy a file or list of files.
################################################################################
# NOTE: when working entirely with local files, putFiles and getFiles are interchangeable.
sub putFiles
{
  my $destDir = shift;
  foreach my $localFilePath (@_)
  {
    logger($debug, "Putting $localFilePath.");
    my $fileName = basename($localFilePath);
    my $putFilePath = join("/", $destDir, $fileName);
    logger($debug, "New location will be: $putFilePath");

    if ($destDir =~ /^https:\/\/(.*)/)  # Assume any secure http connection is a Subversion URL
    {
      my $checkoutDir = join("/", $srcDir, "svn", $1);
      logger($debug, "Attempting to check $fileName into SVN");
      runSystemCmd(join(" ", "$svnExec co --non-interactive", $destDir, $checkoutDir), $errorCodes{SVN_ERROR});
      putFiles($checkoutDir, $localFilePath);
      runSystemCmd(join(" ", "$svnExec ci --non-interactive", $checkoutDir, "-m \"$svnComment $toolName: $productName list for job $versionNumber\""), $errorCodes{SVN_ERROR});
      logger($debug, "Successfully checked $fileName into SVN.");
    }
    elsif ($destDir =~ /^http/)  # Assume any non-secure http connection is a put-able URL (REST)
    {
#      TODO - maybe make this work later, but will need user creds and that may be hard to take in easily
#      logger($warning, "Attempting to put $fileName remote");
#      runSystemCmd(join(" ", $curlExec, "--upload-file", $localFilePath, $putFilePath), $errorCodes{IO_ERROR});
#      logger($debug, "Successfully put $fileName to remote URL.");
      logger($warning, "Put to remote URL currently unsupported. Skipping $localFilePath to $destDir");
    }
    else
    {
      mkpath($destDir) unless ( -d $destDir);
      logger($debug, "Attempting to put $fileName locally");
      runSystemCmd(join(" ", "$copyExec -f", $localFilePath, $putFilePath), $errorCodes{IO_ERROR});
      logger($debug, "Successfully put $fileName from local copy.");
    }
  }
}

################################################################################
# This will act recursively to replace all tokens in a given line.
################################################################################
sub replaceTokens
{
  my $line = shift;

  ($line = "$1$versionNumber$2") && ($line = replaceTokens($line)) if ($line =~ /(.*)__VERSION__(.*)/);
  ($line = "$1$productDisplayName$2") && ($line = replaceTokens($line)) if ($line =~ /(.*)__NAME__(.*)/);

  return $line;
}

################################################################################
# This will clean up the rpm stage directory, then exit with given return code.
################################################################################
sub exitClean
{
  my $returnCode = shift;

  # Cleanup - this should remove everything in and including the $jobDir ... unless debug mode is active.
  #         - As time goes on we need to update this code to remove new things staged/added.
  #         - Careful not to automatically clean up the image if it was never stored/transferred elsewhere.
  unless ($forceDebug)
  {
    if ($imageMoved && !(defined $actionUpdate))
    {
      runSystemCmd(join(" ", "$rmExec -rf", $jobDir), $errorCodes{IO_ERROR});
    }
    else # This means the image is still only guaranteed to exist in the $imageDir, so leave it.
    {
      runSystemCmd(join(" ", "$rmExec -rf", $srcDir), $errorCodes{IO_ERROR});
      runSystemCmd(join(" ", "$rmExec -rf", $repoDir), $errorCodes{IO_ERROR});
    }

    if ($imageBlessed)
    {
      runSystemCmd(join(" ", "$rmExec -rf", $finishedImagesDir), $errorCodes{IO_ERROR}) if ( -d $finishedImagesDir && $actionBless);
    }
  }

  loggersClose();
  exit($returnCode);
}

################################################################################
# Usage statement.
################################################################################
sub exitUsage
{
  my $msg = shift;
  print "$msg\n\n" if ($msg);
  print "USAGE:\t$toolName [ACTIONS] <conf file> <job id> [OPTIONS]\n" .
        "\n\tREQUIRED:\n" .
        "\t<conf file> is the absolute path (or Subversion URL) of the conf file to use for this job.\n" .
        "\t<job id> is any number that uniquely represents this appliance.\n" .
        "\n\tACTIONS:\n" .
        "\tIf ACTIONS are ommitted, the default '-cl' will be used.\n" .
        "\t-c|--create\t\t=>  Create appliance image.\n" .
        "\t-u|--updatemedia\t=>  Create appliance update file.\n" .
        "\t-l|--createlist\t\t=>  Generate appliance RPMs list.\n" .
        "\n\tOPTIONS:\n" .
        "\t-h|--help\t\t=>  Display this usage statement.\n" .
        "\t-v|--verbose\t\t=>  Enable debug logging. \n" .
        "\t-d|--debug\t\t=>  Enable debug logging and preserve image debug files.\n" .
        "\t-p|--profile\t\t=>  The profile name to be built.\n" .
        "\t-t|--type\t\t=>  The type of image to be created. \n" .
        "\t-f|--format\t\t=>  Converts the image created (except tbz & iso) to other formats. \n" .
        "\t-a|--ova\t\t=>  Creates an OVA image by converting the OVF/VMDK.\n" .
        "\t-o|--output\t\t=>  Overrides the directory where the images are created.\n" .
        "\t-w|--workspace\t\t=>  Overrides the workspace directory of the tool. \n" .
        "\nImage creation in one step:\n" .
        "createAppliance <conf file> <job id>\n" .
        "\nImage creation options:\n" .
        "\tcreateAppliance -c | --create [-u | --updatemedia [<update path>]] <conf file> <job id>\n" .
        "\t\t[ -t | --type <vmx|tbz|oem|iso> ]\n" .
        "\t\t[ -f | --format <vhd|vhd-fixed|qcow2|vmdk> ]\n" .
        "\t\t[ -a | --ova ]\n" .
        "\t\t[ -o | --output <output path> ]\n" .
        "\t\t[OPTIONS]\n" .
        "\tIf updatemedia -u is set, the workspace directory will not be erased and the RPMs files can be added/removed from the workspace to create the update media file later.\n" .
        "\tWhen ova output -a is set, the ovf/vmdk pair will be converted into a single ova file when the image is created.\n" .
        "\tThe type -t requires one or more formats for the image output(s). The type must be properly defined by the templates used by <conf file>.\n" .
        "\tThe output argument -o expects the path of the destination folder to store the images files.\n" .
        "\nImage update options:\n" .
        "\tcreateAppliance -u | --updatemedia [<update path>] <conf file> <job id>\n" .
        "\t\t[ -o | --output <output path> ]\n" .
        "\t\t[OPTIONS]\n" .
        "\tThe <update path> is an optional input argument that must refer to a folder containing RPM files organized in a single or multiple directories.\n" .
        "\tIf <update path> is omitted, an image created with -cu | --create --updatemedia action must have been already created with the same <job id>.\n" .
        "\tThe output argument -o expects the path of the destination folder to store the update media file.\n" .
        "\nList creation options:\n" .
        "\tcreateAppliance -l | --createlist <conf file> <job id>\n" .
        "\t\t[OPTIONS]\n" .
        "\tThis action creates a list file with RPMs to be installed by create action. The output location and creation scripts are defined in <conf file>.\n" .
        "\nGlobal options [OPTIONS]:\n" .
        "\t[ -h | --help ]\n" .
        "\tShows usage help text.\n" .
        "\t[ -w | --workspace <workspace path> ]\n" .
        "\tThe workspace sets the destination path for the intermediate files created (log, repo, images, etc.) by the invoked actions.\n" .
        "\t[ -v | --verbose ]\n" .
        "\tThe verbose flag enables the console output of all internal log messages.\n" .
        "\t[ -d | --debug ]\n" .
        "\tThe debug flag automatically sets the verbose mode and preserve any file created in the workspace for debugging purposes.\n" .
        "\t[ -p | --profile <name> ]\n" .
        "\tThe profile requires a <name> defined in <conf file> and allows the selection of one or more profiles to complete the action of the job.\n";

  exit 0 if ($showUsage); # Stay calm...
  logger($error, "Operation aborted.  Exiting with error code: " . $errorCodes{USAGE_ERROR});
  exit($errorCodes{USAGE_ERROR});
}

################################################################################
# Time stamp.
################################################################################
sub grabTime
{
  my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst)=localtime(time);
  my $fullTime = sprintf("%02d%02d%02d",$hour,$min,$sec);
  chomp($fullTime);
  return "$fullTime";
}

################################################################################
# Date stamp.
################################################################################
sub grabDate
{
  my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst)=localtime(time);
  my $_year = $year+1900;
  my $_month = $mon+1;
  my $_day = $mday;
  my $date = sprintf("%04d%02d%02d",$_year,$_month,$_day);
  chomp($date);
  return "$date";
}

################################################################################
# Logging subroutines and helper functions.
################################################################################
sub loggersOpen
{
  logger($debug, "* Starting log files * ...");
  open(LOGGER, ">>$logFile") or die("Unable to open $logFile for writing: ", $errorCodes{IO_ERROR});
  open(ERROR_LOGGER, ">>$errLogFile") or die("Unable to open $errLogFile for writing: ", $errorCodes{IO_ERROR});
  logger($debug, join(" ", "Logging started at", grabTime(), "on", grabDate()));
}

sub loggersClose
{
  logger($debug, "* Stopping log files * ...");
  close(ERROR_LOGGER);
  close(LOGGER);
}

sub logger
{
  my ($level, $message) = @_;

  $message = grabTime . " [$level]: " . $message;
  if ($logLevel eq "DEBUG")
  {
    print "$message\n";
    print LOGGER "$message\n" if (loggerStatusOpen());
    print ERROR_LOGGER "$message\n" if (($level eq $error) && fileno ERROR_LOGGER);
  }
  else
  {
    print "$message\n" unless ($level eq $debug);
    print LOGGER "$message\n" if (loggerStatusOpen());
    print ERROR_LOGGER "$message\n" if (($level eq $error) && fileno ERROR_LOGGER);
  }
}

sub loggerStatusOpen { return (fileno LOGGER ? 1 : 0); }
