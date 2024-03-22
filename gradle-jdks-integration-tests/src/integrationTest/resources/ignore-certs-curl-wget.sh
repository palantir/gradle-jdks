#!/bin/sh

set -e

if command -v curl > /dev/null 2>&1; then
  function curl () { command curl -k "$@"; }
  export curl
fi

if command -v wget > /dev/null 2>&1; then
  function wget () { command wget --no-check-certificate "$@" ; }
  export wget
fi
