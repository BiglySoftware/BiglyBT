/*
 * Created on 13-Mar-2004
 * Created by James Yeh
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.platform;

/**
 * Enum for a PlatformManager's reported capabilities
 * @version 1.0 Initial Version
 * @since 1.4
 * @author James Yeh
 */
public final class PlatformManagerCapabilities
{
    public static final PlatformManagerCapabilities GetVersion 				= new PlatformManagerCapabilities("getVersion");
    public static final PlatformManagerCapabilities CreateCommandLineProcess 	= new PlatformManagerCapabilities("CreateCommandLineProcess");
    public static final PlatformManagerCapabilities UseNativeNotification 	= new PlatformManagerCapabilities("UseNativeNotification");
    public static final PlatformManagerCapabilities UseNativeScripting 		= new PlatformManagerCapabilities("UseNativeScripting");

    public static final PlatformManagerCapabilities PlaySystemAlert 		= new PlatformManagerCapabilities("PlaySystemAlert");

    public static final PlatformManagerCapabilities GetUserDataDirectory 	= new PlatformManagerCapabilities("GetUserDataDirectory");

    public static final PlatformManagerCapabilities RecoverableFileDelete 	= new PlatformManagerCapabilities("RecoverableFileDelete");
    public static final PlatformManagerCapabilities RegisterFileAssociations 	= new PlatformManagerCapabilities("RegisterFileAssociations");
    public static final PlatformManagerCapabilities ShowFileInBrowser 		= new PlatformManagerCapabilities("ShowFileInBrowser");
    public static final PlatformManagerCapabilities ShowPathInCommandLine 	= new PlatformManagerCapabilities("ShowPathInCommandLine");

    public static final PlatformManagerCapabilities SetTCPTOSEnabled	 	= new PlatformManagerCapabilities("SetTCPTOSEnabled");
    public static final PlatformManagerCapabilities CopyFilePermissions 	= new PlatformManagerCapabilities("CopyFilePermissions");
    public static final PlatformManagerCapabilities TestNativeAvailability 	= new PlatformManagerCapabilities("TestNativeAvailability");
    public static final PlatformManagerCapabilities TraceRouteAvailability 	= new PlatformManagerCapabilities("TraceRoute");
    public static final PlatformManagerCapabilities PingAvailability 		= new PlatformManagerCapabilities("Ping");

    public static final PlatformManagerCapabilities ComputerIDAvailability = new PlatformManagerCapabilities("CID");

    public static final PlatformManagerCapabilities RequestUserAttention 	= new PlatformManagerCapabilities("RequestUserAttention");

    public static final PlatformManagerCapabilities AccessExplicitVMOptions = new PlatformManagerCapabilities("AccessExplicitVMOptions");

    public static final PlatformManagerCapabilities RunAtLogin				= new PlatformManagerCapabilities("RunAtLogin");
    public static final PlatformManagerCapabilities GetMaxOpenFiles			= new PlatformManagerCapabilities("GetMaxOpenFiles");
    public static final PlatformManagerCapabilities PreventComputerSleep	= new PlatformManagerCapabilities("PreventComputerSleep");

    public static final PlatformManagerCapabilities UseSystemTheme			= new PlatformManagerCapabilities("UseSystemTheme");

    private final String myName; // for debug only

    private PlatformManagerCapabilities(String name)
    {
        myName = name;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        return myName;
    }
}
