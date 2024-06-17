/*
 * Created on 23-Sep-2004
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

package com.biglybt.core.util;

/**
 * @author parg
 *
 */

public abstract class
AERunnable
	implements Runnable
{
	public String
	getName()
	{
		return( null );
	}
	
	@Override
	public void
	run()
	{
		try{
			runSupport();

		}catch( Throwable e ){

			DebugLight.printStackTrace(e);
		}
	}

	public abstract void
	runSupport();
	
	public static AERunnable
	create(
		Runnable	r )
	{
		return(
			new AERunnable()
			{
				public void
				runSupport()
				{
					r.run();
				}
			});
	}
	
	public abstract static class
	AERunnableNamed
		extends AERunnable
	{
		final private String		name;
		
		public
		AERunnableNamed(
			String		_name )
		{
			name = _name;
		}
		
		@Override
		public String
		getName()
		{
			return( name );
		}
	}
}
