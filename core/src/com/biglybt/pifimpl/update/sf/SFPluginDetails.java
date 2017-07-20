/*
 * Created on 27-Apr-2004
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

package com.biglybt.pifimpl.update.sf;

/**
 * @author parg
 *
 */
public interface
SFPluginDetails
{
	public String
	getId();

	public String
	getName();

	public String
	getCategory();

		/**
		 *
		 * @return null if version number unknown
		 */

	public String
	getVersion();

	public String
	getDownloadURL()

		throws SFPluginDetailsException;


	public String
	getAuthor()

		throws SFPluginDetailsException;


	public String
	getCVSVersion()

		throws SFPluginDetailsException;


	public String
	getCVSDownloadURL()

		throws SFPluginDetailsException;


	public String
	getDescription()

		throws SFPluginDetailsException;


	public String
	getComment()

		throws SFPluginDetailsException;

	public String
	getRelativeURLBase();

	/**
	 * @return
	 *
	 * @since 3.0.1.7
	 */
	String
	getInfoURL();
}
