#!/bin/sh
# This file installs the .desktop file into your Desktop Environment
# Should be able to run as user as sudo (sudo will install it globally)

[ -x "$(command -v desktop-file-install)" ] && desktop-file-install biglybt.desktop
[ -x "$(command -v xdg-desktop-menu)" ] && xdg-desktop-menu install --novendor biglybt.desktop

exit 0
