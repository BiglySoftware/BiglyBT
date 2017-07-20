/*
 * Created on 16-May-2004
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

package com.biglybt.pif.update;

/**
 * @author parg
 *
 */

import java.io.InputStream;

public interface
UpdateInstaller
{
		/**
		 * Add a resource to the installation. The file will be saved away for later use.
		 *
		 * @param resource_name non-qualified name for the resource - i.e. not an absolute file
		 * 						name but rather something local like "fred". This can then be used
		 * 						later in actions
		 * @param is
		 */

	public void
	addResource(
		String			resource_name,
		InputStream		is )

		throws UpdateException;

  /**
   * Add a resource to the installation. The file will be saved away for later use.
   *
   * @param resource_name non-qualified name for the resource - i.e. not an absolute file
   *            name but rather something local like "fred". This can then be used
   *            later in actions
   * @param is
   * @param closeInputStream if false, the InputStream is won't be closed
   */

  public void
  addResource(
    String      resource_name,
    InputStream   is,
    boolean closeInputStream)

    throws UpdateException;

		/**
		 * Returns the absolute path of the app install dir (i.e. where .jar etc is located)
		 * @return
		 */

	public String
	getInstallDir();

		/**
		 * Returns the absolute path of the user dir (i.e. where config is stored)
		 * @return
		 */

	public String
	getUserDir();

		/**
		 * Add an installation "move" action to move either an absolute file or resource
		 * @param from_file	either absolute or relative
		 * @param to_file absolute
		 */

	public void
	addMoveAction(
		String		from_file_or_resource,
		String		to_file )

		throws UpdateException;


  /**
   * Add an installation "change rights" action to change a file rights
   * @param rights the rights, for example "776"
   * @param to_file absolute
   */
	public void
	addChangeRightsAction(
		String    rights,
		String    to_file )

    	throws UpdateException;

		/**
		 * Adds an action to remove either a directory (recursively delete) or file
		 * @param file
		 * @throws UpdateException
		 */

	public void
	addRemoveAction(
		String    file )

    	throws UpdateException;

		/**
		 * Runs the action now, not as part of a shutdown/restart of Vuze
		 * @throws UpdateException
		 */

	public void
	installNow(
		UpdateInstallerListener		listener )

		throws UpdateException;

	public void
	destroy();
}
