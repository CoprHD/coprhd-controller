
1. elasticMemory.xlsx

An Excel spreadsheet that calculates parameters for elastic memory scaling
for ViPR Controller services

2. elasticMemory.py

A simple python script that:
- lists current memory settings in build.gradle files for the ViPR Controller services
- prints the new settings based on the spreadsheet above
- prints the difference between settings in the build.gradle files and the spreadsheet

3. elasticMemory_check.sh

A simple shell script that fetches /proc/meminfo and the output of 'ps -ef' from
a ViPR Controller system and prints actual memeory settings for the Controller
services.

