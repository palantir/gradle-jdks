#!/bin/sh

set -e

# we need to check before setting the option if curl/wget are installed, otherwise the commands will just
# look as if they are installed, but they won't actually work

if command -v curl > /dev/null 2>&1; then
  function curl () { command curl -k "$@"; }
  export curl
fi

if command -v wget > /dev/null 2>&1; then
  function wget () { command wget --no-check-certificate "$@" ; }
  export wget
fi
