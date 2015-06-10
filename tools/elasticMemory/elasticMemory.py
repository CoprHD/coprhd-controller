#!/usr/bin/python

import sys
import os
import os.path
import re

KEYS = ( "maxMemory", "maxMemoryFactor", "minMemory", "minMemoryFactor", "youngGenMemory", "youngGenMemoryFactor", "maxPermMemory", "maxPermMemoryFactor", )

RE_SCRIPTS = re.compile(r'^scripts\s+{\s*\n(.*?)\s*^}', re.MULTILINE|re.DOTALL)
RE_ITEM    = re.compile(r'^    (\S+)\s+{\s*\n(.*?)\s*^    }', re.MULTILINE|re.DOTALL)
RE_KEY     = re.compile(r'^\s*(' + r'|'.join(KEYS) + r')\s*=\s*(\S+)\s*$', re.MULTILINE)

def find_files(topdir):
    for (dirpath, dirnames, filenames) in os.walk(topdir):
        for filename in filenames:
            if filename == "build.gradle":
                yield os.path.join(dirpath, filename)

def match_scripts(file):
    f = open(file)
    s = f.read()
    f.close()
    m = RE_SCRIPTS.search(s)
    if m:
        return m.group(1)
    
def params_read(path):
    f = os.popen("xlsx2csv " + path, "r");
    lines = f.read().splitlines()
    f.close()

    params = {}
    for line in lines:
        tokens = line.split(",")
        if len(tokens) == 11 and all(tokens):
            dct = { tokens[1] : '"%.1fm"' % float(tokens[4]), tokens[1] + "Factor" : '"%.4f"' % float(tokens[5]) }
            if not tokens[0] in params:
                params[tokens[0]] = dct
            else:
                params[tokens[0]].update(dct)
    return params

def params_scan(topdir):
    params = {}
    for (dirpath, dirnames, filenames) in os.walk(topdir):
        for filename in filenames:
            if filename == "build.gradle":
                filepath = os.path.join(dirpath, filename)
                f = open(filepath)
                s = f.read()
                f.close()
                m = RE_SCRIPTS.search(s)
                if m:
                     print "*", filepath
                     for g in RE_ITEM.findall(m.group(1)):
                         for k in RE_KEY.findall(g[1]):
                             if not g[0] in params:
                                 params[g[0]] = {}
                             params[g[0]][k[0]] = k[1]
    return params

def params_print(params):
    for p in sorted(params.keys()):
        print "    " + p + " {"
        dct = params[p]
        for q in sorted(dct.keys()):
            print "        " + q + " = " + dct[q]
        print "    }"
    
def params_printdiff(old, new):
    for p in sorted(new.keys()):
        if not p in old:
            print "+   " + p + " {"
            newdct = new[p]
            for q in sorted(newdct.keys()):
                print "+       " + q + " = " + newdct[q]
            print "+   }"
        else:
            title = True
            newdct = new[p]
            olddct = old[p]
            for q in sorted(KEYS):
                if not q in olddct and not q in newdct:
                    continue
                elif q in olddct and q in newdct:
                    if olddct[q] == newdct[q]: continue
                    if title: print "    " + p + " {"; title = False
                    print "-       " + q + " = " + olddct[q]
                    print "+       " + q + " = " + newdct[q]
                elif q in olddct and not q in newdct:
                    if title: print "    " + p + " {"; title = False
                    print "-       " + q + " = " + olddct[q]
                else:
                    if title: print "    " + p + " {"; title = False
                    print "+       " + q + " = " + newdct[q]
            if not title:
                print "    }"


print "*** Current parameters"
params_cur = params_scan("../..")
params_print(params_cur)

print "*** New parameters"
params_new = params_read("elasticMemory.xlsx");
params_print(params_new)

print "*** Diff"
params_printdiff(params_cur, params_new)
