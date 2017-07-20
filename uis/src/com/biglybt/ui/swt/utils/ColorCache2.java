/*
 * Created on Nov 10, 2011
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


package com.biglybt.ui.swt.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;
import com.biglybt.core.util.Debug;

public class
ColorCache2
{
	private final static Map<RGB,CachedColorManaged>	color_map = new HashMap<>();

	public static void dispose() {
		ArrayList<CachedColorManaged> colors;
		synchronized (color_map) {
			colors = new ArrayList<>(color_map.values());
			color_map.clear();
		}
		for (CachedColorManaged colorManaged : colors) {
			colorManaged.color.dispose();
		}
	}

	public static CachedColor
	getColor(
		Color		c )
	{
		return( new CachedColorUnmanaged( c ));
	}

	public static CachedColor
	getColor(
		Device		device,
		RGB			rgb )
	{
		synchronized( color_map ){

			CachedColorManaged entry = color_map.get( rgb );

			if ( entry == null ){

				entry = new CachedColorManaged( new Color( device, rgb ));

				color_map.put( rgb, entry );

			}else{

				entry.addRef();
			}

			return( new CachedColorManagedFacade( entry ));
		}
	}

	private static class
	CachedColorManaged
	{
		private Color	color;
		private int		ref_count;

		private
		CachedColorManaged(
			Color	_color )
		{
			color		= _color;
			ref_count	= 1;
		}

		public Color
		getColor()
		{
			return( color );
		}

		private void
		addRef()
		{
			ref_count++;

			//System.out.println( "cc ++: color=" + color + ", refs=" + ref_count );
		}

		private void
		dispose()
		{
			ref_count--;

			//System.out.println( "cc --: color=" + color + ", refs=" + ref_count );

			if ( ref_count == 0 ){

				color_map.remove( color.getRGB());

				color.dispose();

			}else if ( ref_count < 0 ){

				Debug.out( "already disposed" );
			}
		}
	}

	private static class
	CachedColorManagedFacade
		implements CachedColor
	{
		private	CachedColorManaged	delegate;
		private boolean				disposed;

		private
		CachedColorManagedFacade(
			CachedColorManaged	_delegate )
		{
			delegate = _delegate;
		}

		@Override
		public Color
		getColor()
		{
			return( delegate.getColor());
		}

		@Override
		public boolean
		isDisposed()
		{
			synchronized( color_map ){

				return( disposed );
			}
		}

		@Override
		public void
		dispose()
		{
			synchronized( color_map ){

				if ( !disposed ){

					disposed = true;

					delegate.dispose();
				}
			}
		}
	}

	private static class
	CachedColorUnmanaged
		implements CachedColor
	{
		private Color	color;

		private
		CachedColorUnmanaged(
			Color	_color )
		{
			color	= _color;
		}

		@Override
		public Color
		getColor()
		{
			return( color );
		}

		@Override
		public boolean
		isDisposed()
		{
			return( color.isDisposed());
		}

		@Override
		public void
		dispose()
		{
			color.dispose();
		}
	}

	public interface
	CachedColor
	{
		public Color
		getColor();

		public boolean
		isDisposed();

		public void
		dispose();
	}
}
