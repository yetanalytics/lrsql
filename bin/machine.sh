#!/bin/sh

#TODO: Add windows, possibly better macos targeting

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=linux;;
    *)          machine=macos;;
esac

echo ${machine}
