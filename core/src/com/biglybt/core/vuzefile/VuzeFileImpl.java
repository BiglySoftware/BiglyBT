/*
 * Created on May 18, 2008
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


package com.biglybt.core.vuzefile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.FileUtil;

public class
VuzeFileImpl
	implements VuzeFile
{
	private final VuzeFileHandler			handler;
	private VuzeFileComponent[]		components;

	protected
	VuzeFileImpl(
		VuzeFileHandler		_handler )
	{
		handler = _handler;

		components = new VuzeFileComponent[0];
	}

	protected
	VuzeFileImpl(
		VuzeFileHandler		_handler,
		Map					map )
	{
		handler = _handler;

		List	l_comps = (List)map.get( "components" );

		components = new VuzeFileComponent[l_comps.size()];

		for (int i=0;i<l_comps.size();i++){

			Map	comp = (Map)l_comps.get(i);

			int	type 	= ((Long)comp.get( "type" )).intValue();
			Map	content	= (Map)comp.get( "content" );

			components[i] = new comp( type, content );
		}
	}

	@Override
	public String
	getName()
	{
		String str = "";

		for ( VuzeFileComponent comp: components ){

			str += (str.length()==0?"":",") + comp.getTypeName();
		}

		return( str );
	}

	@Override
	public VuzeFileComponent[]
	getComponents()
	{
		return( components );
	}

	@Override
	public VuzeFileComponent
	addComponent(
		int		type,
		Map		content )
	{
		VuzeFileComponent comp = new comp( type, content );

		int	old_len = components.length;

		VuzeFileComponent[] res = new VuzeFileComponent[old_len+1];

		System.arraycopy( components, 0, res, 0, old_len );

		res[ old_len ] = comp;

		components = res;

		return( comp );
	}

	@Override
	public void 
	addComponents(
		VuzeFile vf )
	{
		VuzeFileComponent[] comps = vf.getComponents();
		
		int num = comps.length;
		
		if ( num > 0 ){
			
			int	old_len = components.length;

			VuzeFileComponent[] res = new VuzeFileComponent[old_len+num];

			System.arraycopy( components, 0, res, 0, old_len );

			System.arraycopy( comps, 0, res, old_len, num );

			components = res;
		}
	}
	
	@Override
	public Map
	exportToMap()

		throws IOException
	{
		Map	map = new HashMap();

		Map vuze_map = new HashMap();

		map.put( "vuze", vuze_map );

		List	list = new ArrayList();

		vuze_map.put( "components", list );

		for (int i=0;i<components.length;i++){

			VuzeFileComponent comp = components[i];

			Map	entry = new HashMap();

			entry.put( "type", new Long( comp.getType()));

			entry.put( "content", comp.getContent());

			list.add( entry );
		}

		return( map );
	}

	@Override
	public byte[]
	exportToBytes()

		throws IOException
	{
		return( BEncoder.encode( exportToMap()));
	}

	@Override
	public String
	exportToJSON()

		throws IOException
	{
		return( BEncoder.encodeToJSON( exportToMap()));
	}

	@Override
	public void
	write(
		File target )

		throws IOException
	{
		FileOutputStream	fos = FileUtil.newFileOutputStream( target );

		try{
			fos.write( exportToBytes());

		}finally{

			fos.close();
		}
	}

	protected static class
	comp
		implements VuzeFileComponent
	{
		private final int			type;
		private final Map			contents;
		private boolean		processed;

		private Map			user_data;

		protected
		comp(
			int		_type,
			Map		_contents )
		{
			type		= _type;
			contents	= _contents;
		}

		@Override
		public int
		getType()
		{
			return( type );
		}

		@Override
		public String
		getTypeName()
		{
			switch( type ){
				case COMP_TYPE_NONE:
					return( "None" );
				case COMP_TYPE_METASEARCH_TEMPLATE:
					return( "Search Template" );
				case COMP_TYPE_V3_NAVIGATION:
					return( "Navigation" );
				case COMP_TYPE_V3_CONDITION_CHECK:
					return( "Condition Check" );
				case COMP_TYPE_PLUGIN:
					return( "Plugin" );
				case COMP_TYPE_SUBSCRIPTION:
					return( "Subscription" );
				case COMP_TYPE_SUBSCRIPTION_SINGLETON:
					return( "Subscription" );
				case COMP_TYPE_CUSTOMIZATION:
					return( "Customization" );
				case COMP_TYPE_CONTENT_NETWORK:
					return( "Content Network" );
				case COMP_TYPE_METASEARCH_OPERATION:
					return( "Search Operation" );
				case COMP_TYPE_DEVICE:
					return( "Device" );
				case COMP_TYPE_CONFIG_SETTINGS:
					return( "Config Settings" );
				default:
					return( "Unknown" );
			}
		}

		@Override
		public Map
		getContent()
		{
			return( contents );
		}

		@Override
		public void
		setProcessed()
		{
			processed	= true;
		}

		@Override
		public boolean
		isProcessed()
		{
			return( processed );
		}

		@Override
		public synchronized void
		setData(
			Object	key,
			Object	value )
		{
			if ( user_data == null ){

				user_data = new HashMap();
			}

			user_data.put( key, value );
		}

		@Override
		public synchronized Object
		getData(
			Object	key )
		{
			if ( user_data == null ){

				return( null );
			}

			return( user_data.get( key ));
		}
	}
}
