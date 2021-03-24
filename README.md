# Quilt Installer for Official Minecraft Launcher

This repository contains the source code for the quilt installer targeting the official Minecraft launcher.

# Using the installer

This needs to be written or link to a page somewhere on the site :)

# Behind the covers

The core of the installer is written in java, this was chosen for the moment due to swing having a wide range of support.

For Windows and macOS, there is a native launch facility in the `native` subfolder which will display an error dialog if
the user does not have a suitable JRE installed. The native launch facility will be built into the corresponding `.exe`
and macOS application bundles.

For Linux there is currently no native solution as the native launch is intended only for Windows and macOS.
Linux will receive a special launch process in the future which will be architecture and distro agnostic for publishing
on package managers.
