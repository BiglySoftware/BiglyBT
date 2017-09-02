/*
 * Created on 12-Sep-2005
 * Created by Paul Gardner
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

package com.biglybt.pif.platform;

import java.io.File;


public interface
PlatformManager
{
	public static final int	LOC_USER_DATA		= 1;
	public static final int	LOC_MUSIC			= 2;
	public static final int	LOC_DOCUMENTS			= 3;
	public static final int	LOC_VIDEO			= 4;
	public static final int	LOC_DOWNLOADS			= 5;

		/**
		 * Checks to see if the supplied file type is registered with this application
		 * @param name
		 * @param type
		 * @return
		 * @throws PlatformManagerException
		 */

	public boolean
	isAdditionalFileTypeRegistered(
		String		name,				// e.g. "Wibble"
		String		type )				// e.g. ".wib"

		throws PlatformManagerException;

		/**
		 * Registers a file type with this application
		 * @param name
		 * @param description
		 * @param type
		 * @param content_type
		 * @throws PlatformManagerException
		 */

	public void
	registerAdditionalFileType(
		String		name,				// e.g. "Wibble"
		String		description,		// e.g. "Wibble File"
		String		type,				// e.g. ".wib"
		String		content_type )		// e.g. "application/x-wibble"

		throws PlatformManagerException;

		/**
		 * Removes a file-type registration
		 * @param name
		 * @param type
		 * @return
		 * @throws PlatformManagerException
		 */

	public void
	unregisterAdditionalFileType(
		String		name,				// e.g. "Wibble"
		String		type )				// e.g. ".wib"

		throws PlatformManagerException;

	   /**
	    * Reveals the file or directory with the platform's default browser
	    * @param file_name The full path to a file or directory
	    * @throws PlatformManagerException If this operation fails
	    */

	public void
    showFile(
		String	file_name )

		throws PlatformManagerException;

		/**
		 * Get a well-known location, if defined for the platform
		 * @param location_id from above LOC_constants
		 * @return
		 * @since 2.3.0.6
		 */

	public File
	getLocation(
		long	location_id )

		throws PlatformManagerException;


		/**
		 *
		 * @return	null if can't be found
		 */

	public String
	getComputerName();
}
