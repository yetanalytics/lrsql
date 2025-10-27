#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -Dfile.encoding=UTF-8 -server -cp lrsql.jar lrsql.mariadb.main $@
#COMMENT: SQL-LRS uses the same code to interface with MySQL as it uses for MariaDB; the above is intentional.
