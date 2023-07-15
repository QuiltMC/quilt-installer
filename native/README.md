# Quilt Installer Native Launch 

This folder contains the rust project used to launch the installer on Windows and macOS.

Linux will receive a special launch process in the future which will be architecture and distro agnostic for publishing
on package managers, hence not using this rust project.
Compiling for a unix target will fail.

The purpose of this installer is to present the installer in the native form of the user's platform and display error
dialogs given issues such as failing to find a suitable JRE.

## Process

If the installer is run on a machine without a suitable JRE, then an error dialog will be displayed and opening a link
to help the user install a suitable JRE.
