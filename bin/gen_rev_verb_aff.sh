#!/bin/sh

#
# Generates reverse verb affixes from direct affixes
#

BIN=$(dirname $0)/../dict_uk/expand
AFF_DIR=../data/affix

$BIN/verb_reverse.py < $AFF_DIR/v.aff > $AFF_DIR/vr.aff
$BIN/verb_reverse.py < $AFF_DIR/v_advp.aff > $AFF_DIR/vr_advp.aff
