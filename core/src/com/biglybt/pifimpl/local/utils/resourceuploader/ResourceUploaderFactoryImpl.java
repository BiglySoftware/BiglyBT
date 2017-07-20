/*
 * Created on 03-May-2004
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

package com.biglybt.pifimpl.local.utils.resourceuploader;

/**
 * @author parg
 *
 */

import java.io.InputStream;
import java.net.URL;

import com.biglybt.pif.utils.resourceuploader.ResourceUploader;
import com.biglybt.pif.utils.resourceuploader.ResourceUploaderFactory;

public class
ResourceUploaderFactoryImpl
	implements ResourceUploaderFactory
{
	private static ResourceUploaderFactory	singleton = new ResourceUploaderFactoryImpl();

	public static ResourceUploaderFactory
	getSingleton()
	{
		return( singleton );
	}

	@Override
	public ResourceUploader
	create(
		URL			url,
		InputStream	data )
	{
		return( create( url, data, null, null ));
	}

	@Override
	public ResourceUploader
	create(
		URL				url,
		InputStream		data,
		String			user_name,
		String			password )
	{
		return( new ResourceUploaderURLImpl( url, data, user_name, password ));
	}
}
