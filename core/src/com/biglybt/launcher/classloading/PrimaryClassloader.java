/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.launcher.classloading;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Aaron Grunthal
 * @create 28.12.2007
 */
public class PrimaryClassloader extends URLClassLoader{

	// ************************************************************************************************
	// ON OSX THIS CLASS IS EXPLICITLY SET AS THE SYSTEM CLASS LOADER IN THE PLIST
	// CURRENTLY THIS HAS BEEN REDUCED FROM INITIAL FUNCTIONALITY TO A STUB SO THAT
	// EXISTING INSTALLS CONTINUE TO WORK
	// THEREFORE DO NOT DELETE!
	// ************************************************************************************************
	
	private final ClassLoader packageLoader;

	private static final String packageName = PrimaryClassloader.class.getPackage().getName();

	/**
	 * initialization path when loaded via
	 * <code>-Djava.system.class.loader=com.biglybt.launcher.classloading.PrimaryClassloader</code>
	 * instead of bootstrapping, has the advantage that this gets registered as system classloader
	 *
	 * @deprecated DO NOT INVOKE MANUALLY
	 */
	public PrimaryClassloader(ClassLoader parent)
	{
		super(generateURLs(),parent.getParent());
		this.packageLoader = parent;
	}

	private static URL[] generateURLs()
	{
		String classpath = System.getProperty("java.class.path");

		String[] paths = classpath.split(File.pathSeparator);
		URL[] urls = new URL[paths.length+1];
		try
		{
			for(int i=0;i<paths.length;i++)
			{
				urls[i] = new File(paths[i]).getCanonicalFile().toURI().toURL();
			}

			urls[urls.length-1] = new File(".").getCanonicalFile().toURI().toURL();
		} catch (Exception e)
		{
			System.err.println("Invalid classpath detected\n");
			e.printStackTrace();
			System.exit(1);
		}

		return urls;
	}

	@Override
	protected Class 
	loadClass(final String name, boolean resolve) throws ClassNotFoundException 
	{	
		if (!name.startsWith(packageName)){
			
			return( super.loadClass(name, resolve));
			
		}else{
			
			return( packageLoader.loadClass(name));
		}
	}
}
