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
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;


/**
 * @author Aaron Grunthal
 * @create 28.12.2007
 */
public class PrimaryClassloader extends URLClassLoader implements com.biglybt.launcher.classloading.PeeringClassloader {

	private final ArrayList peersLoaders = new ArrayList();
	private final ClassLoader packageLoader;

	private static final String packageName = PrimaryClassloader.class.getPackage().getName();

	/**
	 * initialization path when loaded through bootstrapping
	 */
	private PrimaryClassloader()
	{
		super(generateURLs(),getSystemClassLoader().getParent());
		this.packageLoader = getSystemClassLoader();
	}

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

	/**
	 * altered class lookup
	 * <ol>
	 * <li>follow normal delegation, circumventing the system classloader as we bootstraped it away</li>
	 * <li>OR delegate to the system classloader iff it is for classes from this package, this allows us to rebootstrap and discard other branches in the hierarchy</li>
	 * <li>check for loaded by peers</li>
	 * <li>try to load from peers</li>
	 * </ol>
	 */
	@Override
	protected Class loadClass(final String name, boolean resolve) throws ClassNotFoundException {
		//System.out.println(this+" loading "+name);
		Class c;
		try
		{
			if (!name.startsWith(packageName))
				c = super.loadClass(name, resolve);
			else
				c = packageLoader.loadClass(name);
		} catch (ClassNotFoundException e)
		{
			c = peerFindLoadedClass(name);
			if (c == null)
				c = peerLoadClass(name);
			if (c == null)
				throw e;
			if (resolve)
				resolveClass(c);
		}
		return c;
	}

	private Class peerFindLoadedClass(String className)
	{
		Class c = null;
		synchronized (peersLoaders)
		{
			for (int i = 0; i < peersLoaders.size() && c == null; i++)
			{
				WeakReference ref = (WeakReference) peersLoaders.get(i);
				SecondaryClassLoader loader = (SecondaryClassLoader) ref.get();
				if (loader != null)
					c = loader.findLoadedClassHelper(className);
				else
					peersLoaders.remove(i--);
			}
		}
		return c;
	}

	private Class peerLoadClass(String className)
	{
		Class c = null;
		synchronized (peersLoaders)
		{
			for(int i=0;i<peersLoaders.size()&&c==null;i++)
			{
				WeakReference ref = (WeakReference)peersLoaders.get(i);
				SecondaryClassLoader loader = (SecondaryClassLoader)ref.get();
				if(loader != null) // no removal here, peerFindLoadedClass should take care of that anyway
					c = loader.findClassHelper(className);
			}

		}
		return c;
	}

	void registerSecondaryClassloader(SecondaryClassLoader loader)
	{
		synchronized (peersLoaders)
		{
			peersLoaders.add(new WeakReference(loader));
		}

	}



	/**
	 *
	 * @param toRun
	 */
	public static ClassLoader getBootstrappedLoader()
	{
		ClassLoader loader = ClassLoader.getSystemClassLoader();

		try
		{
			return (ClassLoader) loader.loadClass(PrimaryClassloader.class.getName()).newInstance();
		} catch (Exception e)
		{
			System.err.println("Could not instantiate Classloader\n");
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}
}
