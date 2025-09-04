#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -Dfile.encoding=UTF-8 -server -cp lrsql.jar lrsql.maria.main $@
