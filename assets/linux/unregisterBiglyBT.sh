#!/bin/sh
# This file removed the .desktop file registration from your Desktop Environment

[ -x "$(command -v xdg-desktop-menu)" ] && xdg-desktop-menu uninstall --novendor biglybt.desktop
# if not executed as root will probably silently fail
rm -f "/usr/share/applications/biglybt.desktop"

exit 0
