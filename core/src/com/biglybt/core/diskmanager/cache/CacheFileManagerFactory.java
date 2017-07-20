/*
 * Created on 03-Aug-2004
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

package com.biglybt.core.diskmanager.cache;

/**
 * @author parg
 *
 */

import com.biglybt.core.util.AEMonitor;

public class
CacheFileManagerFactory
{
	public static final String	DEFAULT_MANAGER = "com.biglybt.core.diskmanager.cache.impl.CacheFileManagerImpl";

	private static CacheFileManager	manager;
	private static final AEMonitor		class_mon	= new AEMonitor("CacheFileManagerFactory");


	public static CacheFileManager
	getSingleton()

		throws CacheFileManagerException
	{
		return( getSingleton( null ));
	}

	public static CacheFileManager
	getSingleton(
		String	explicit_implementation )

		throws CacheFileManagerException
	{
		try{
			class_mon.enter();

			if ( manager == null ){

				String	impl = explicit_implementation;

				if ( impl == null ){

					impl = System.getProperty( "com.biglybt.core.diskmanager.cache.manager");
				}

				if ( impl == null ){

					impl	= DEFAULT_MANAGER;
				}

				try{
					Class impl_class = CacheFileManagerFactory.class.getClassLoader().loadClass( impl );

					manager = (CacheFileManager)impl_class.newInstance();

				}catch( Throwable e ){

					throw( new CacheFileManagerException( null, "Failed to instantiate manager '" + impl + "'", e ));
				}
			}

			return( manager );

		}finally{

			class_mon.exit();
		}
	}
}
