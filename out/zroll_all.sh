#!/bin/sh

# Rotates all newely generated files into prev/ directory for the following regression checking

FILES="dict_corp_vis.txt dict_corp_lt.txt"

#mv -f *.dups *.uniq prev/
mv -f $FILES prev/
mv dict_stats.txt stats/
