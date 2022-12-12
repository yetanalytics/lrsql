#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -J-Dfile.encoding=UTF-8 -server -cp lrsql.jar lrsql.h2.main --persistent true $@
