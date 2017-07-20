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

package com.biglybt.pifimpl.update.sf.impl2;

/**
 * @author parg
 *
 */

import com.biglybt.pifimpl.update.sf.SFPluginDetails;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsException;

public class
SFPluginDetailsImpl
	implements SFPluginDetails
{
	private SFPluginDetailsLoaderImpl		loader;
	private boolean							fully_loaded;

	private String		id;
	private String		name;
	private String		version;
	private String		category;
	private String		download_url;
	private String		author;
	private String		cvs_version;
	private String		cvs_download_url;

	private String		desc;
	private String		comment;
	private String		info_url;

	protected
	SFPluginDetailsImpl(
		SFPluginDetailsLoaderImpl	_loader,
		String						_id,
		String						_version,
		String						_cvs_version,
		String						_name,
		String						_category )
	{
		loader				= _loader;
		id					= _id;
		version				= _version;
		cvs_version			= _cvs_version;
		name				= _name;
		category			= _category;
	}

	protected void
	setDetails(
		String	_download_url,
		String	_author,
		String	_cvs_download_url,
		String	_desc,
		String	_comment,
		String	_info_url)
	{
		fully_loaded		= true;

		download_url		= _download_url;
		author				= _author;
		cvs_download_url	= _cvs_download_url;
		desc				= _desc;
		comment				= _comment;
		info_url = _info_url;
	}

	protected boolean
	isFullyLoaded()
	{
		return( fully_loaded );
	}

	protected void
	checkLoaded()

		throws SFPluginDetailsException
	{
		if ( !fully_loaded ){

			loader.loadPluginDetails( this );
		}
	}

	@Override
	public String
	getId()
	{
		return( id );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String
	getCategory()
	{
		return( category );
	}

	@Override
	public String
	getVersion()
	{
		return( version );
	}

	@Override
	public String
	getDownloadURL()

		throws SFPluginDetailsException
	{
		checkLoaded();

		return( download_url );
	}

	@Override
	public String
	getAuthor()

		throws SFPluginDetailsException
	{
		checkLoaded();

		return( author );
	}

	@Override
	public String
	getCVSVersion()

		throws SFPluginDetailsException
	{
		return( cvs_version );
	}

	@Override
	public String
	getCVSDownloadURL()

		throws SFPluginDetailsException
	{
		checkLoaded();

		return( cvs_download_url );
	}

	@Override
	public String
	getDescription()

		throws SFPluginDetailsException
	{
		checkLoaded();

		return( desc );
	}

	@Override
	public String
	getComment()

		throws SFPluginDetailsException
	{
		checkLoaded();

		return( comment );
	}

	@Override
	public String
	getRelativeURLBase()
	{
		return( loader.getRelativeURLBase());
	}

	@Override
	public String getInfoURL() {
		return info_url;
	}
}
