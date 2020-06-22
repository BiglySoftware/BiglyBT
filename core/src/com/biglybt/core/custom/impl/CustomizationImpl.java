/*
 * Created on Sep 22, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.custom.impl;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.biglybt.core.custom.Customization;
import com.biglybt.core.custom.CustomizationException;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.vuzefile.VuzeFileHandler;

public class
CustomizationImpl
	implements Customization
{
	private final CustomizationManagerImpl		manager;

	private final String		name;
	private final String		version;
	private final File		contents;

	protected
	CustomizationImpl(
		CustomizationManagerImpl	_manager,
		String						_name,
		String						_version,
		File						_contents )

		throws CustomizationException
	{
		manager		= _manager;
		name		= _name;
		version		= _version;
		contents	= _contents;

		if ( !contents.exists()){

			throw( new CustomizationException( "Content file '" + contents + " not found" ));
		}
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String
	getVersion()
	{
		return( version );
	}

	protected File
	getContents()
	{
		return( contents );
	}

	@Override
	public Object
	getProperty(
		String		name )
	{
		return( null );
	}

	@Override
	public boolean
	isActive()
	{
		return( true );
	}

	@Override
	public void
	setActive(
		boolean		active )
	{
		// TODO:
	}

	@Override
	public InputStream
	getResource(
		String		resource_name )
	{
		return( null );
	}

	@Override
	public InputStream[]
   	getResources(
   		String		resource_name )
	{
		List	result = new ArrayList();

		ZipInputStream	zis = null;

		try{
			zis = new ZipInputStream(
					new BufferedInputStream( FileUtil.newFileInputStream( contents ) ));

			while( true ){

				ZipEntry	entry = zis.getNextEntry();

				if ( entry == null ){

					break;
				}

				String	name = entry.getName();

				int pos = name.indexOf( resource_name + "/" );

				if ( pos != -1 ){

					if ( VuzeFileHandler.isAcceptedVuzeFileName( name )){

						ByteArrayOutputStream baos = new ByteArrayOutputStream( 16*1024 );

						byte[]	buffer = new byte[16*1024];

						while( true ){

							int	len = zis.read( buffer );

							if ( len <= 0 ){

								break;
							}

							baos.write( buffer, 0, len );
						}

						result.add( new ByteArrayInputStream( baos.toByteArray()));
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );

		}finally{

			if ( zis != null ){

				try{
					zis.close();

				}catch( Throwable e ){
				}
			}
		}

		return((InputStream[])result.toArray( new InputStream[result.size()]));
	}

	@Override
	public void
	exportToVuzeFile(
		File 		file )

		throws CustomizationException
	{
		manager.exportCustomization( this, file );
	}
}
