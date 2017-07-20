/*
 * Created on 18-Apr-2004
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

package com.biglybt.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.platform.PlatformManagerException;

/**
 * @author parg
 *
 */
public class
PlatformManagerFactory
{
	protected static PlatformManager		platform_manager;
	protected static AEMonitor				class_mon	= new AEMonitor( "PlatformManagerFactory");

	public static PlatformManager
	getPlatformManager()
	{
		try{
			boolean force_dummy = System.getProperty(SystemProperties.SYSPROP_PLATFORM_MANAGER_DISABLE, "false" ).equals( "true" );

			class_mon.enter();

			if ( platform_manager == null && !force_dummy ){

				try{
					String cla = System.getProperty( "az.factory.platformmanager.impl", "" );

					boolean	explicit_class = cla.length() > 0;

					if ( !explicit_class ){
						int platformType = getPlatformType();
						switch (platformType) {
							case PlatformManager.PT_WINDOWS:
								cla = "com.biglybt.platform.win32.PlatformManagerImpl";
								break;
							case PlatformManager.PT_MACOSX:
								cla = "com.biglybt.platform.macosx.PlatformManagerImpl";
								break;
							case PlatformManager.PT_UNIX:
								cla = "com.biglybt.platform.unix.PlatformManagerImpl";
								break;
							default:
								cla = "com.biglybt.platform.dummy.PlatformManagerImpl";
								break;
						}
					}

					Class<?> platform_manager_class = Class.forName( cla );
					try {
						Method methGetSingleton = platform_manager_class.getMethod("getSingleton");
						platform_manager = (PlatformManager) methGetSingleton.invoke(null);
					} catch (NoSuchMethodException e) {
					} catch (SecurityException e) {
					} catch (IllegalAccessException e) {
					} catch (IllegalArgumentException e) {
					} catch (InvocationTargetException e) {
					}

					if ( explicit_class ){
							// try default constructor as getSingleton method missing (but guaranteed
							// to be there for built-in platform managers )

						if (platform_manager == null) {
							platform_manager = (PlatformManager)Class.forName( cla ).newInstance();
						}
					}

				}catch( Throwable e ){
					// exception will already have been logged

					if (!(e instanceof PlatformManagerException)) {
						Debug.printStackTrace(e);
					}
				}
			}

			if ( platform_manager == null ){

				platform_manager = com.biglybt.platform.dummy.PlatformManagerImpl.getSingleton();
			}

			return( platform_manager );

		}finally{

			class_mon.exit();
		}
	}

	public static int
	getPlatformType()
	{
		if (Constants.isWindows) {

			return (PlatformManager.PT_WINDOWS );

		} else if (Constants.isOSX) {

			return (PlatformManager.PT_MACOSX );

		} else if (Constants.isUnix) {

			return (PlatformManager.PT_UNIX );

		} else {
			return (PlatformManager.PT_OTHER );
		}
	}
}
