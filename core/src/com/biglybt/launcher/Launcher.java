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
package com.biglybt.launcher;

import java.lang.reflect.Method;
import java.net.URL;

import com.biglybt.launcher.classloading.PeeringClassloader;
import com.biglybt.launcher.classloading.PrimaryClassloader;
import com.biglybt.launcher.classloading.SecondaryClassLoader;

/**
 * This will (hopefully) become a unified launching pathway covering everything
 * from the bare core over UIs to Launchable Plugins in the future
 *
 * @author Aaron Grunthal
 * @create 28.12.2007
 */
public class Launcher {

	private final static String  OSName 		= System.getProperty("os.name");
	private final static boolean isOSX			= OSName.toLowerCase().startsWith("mac os");
	private final static boolean LOADER_ENABLED = System.getProperty("USE_OUR_PRIMARYCLASSLOADER", isOSX ? "0" : "1").equals("1");

	/**
	 * Bootstraps a new {@link PrimaryClassloader} from the system class loader,
	 * creates a new thread, instantiates MainClass and invokes its main method
	 * with the provided arguments.
	 *
	 *
	 * @param MainClass
	 *            class implementing the main() method which will be invoked
	 * @param args
	 *            arguments to the main method
	 */
	public static void launch(Class MainClass,String[] args)
	{
		ClassLoader primaryloader = PrimaryClassloader.getBootstrappedLoader();
		try
		{
			Method mainWrapper = primaryloader.loadClass(MainExecutor.class.getName()).getDeclaredMethod("load", new Class[] {ClassLoader.class,String.class,String[].class});
			mainWrapper.setAccessible(true);
			mainWrapper.invoke(null, new Object[] {primaryloader,MainClass.getName(),args});
		} catch (Exception e)
		{
			System.err.println("Bootstrapping failed");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Checks if the current classloader is not part of the {@link PeeringClassloader} hierarchy and performs {@link #launch(Class, String[])} if so<br>
	 * <br>
	 *
	 * This example shows how to implement a main class that ensures that it is
	 * called through the primary class loader: <code><pre>
	 * MyClass {
	 * 	public static void main(String[] args) {
	 * 		if(Launcher.checkAndLaunch(MyClass.class,args))
	 * 			return;
	 * 		... // normal main code
	 * 	}
	 * }
	 * </pre></code>
	 *
	 *
	 * @param MainClass
	 * @param args
	 * @return true if bootstrapping was necessary and the main method was
	 *         called through a new classloader, false otherwise
	 */
	public static boolean checkAndLaunch(Class MainClass,String[] args)
	{
		if(isBootStrapped())
			return false;
		launch(MainClass, args);
		return true;
	}

	/**
	 *
	 * @return true if the current classloader is part of the {@link PeeringClassloader} hierarchy
	 */
	public static boolean isBootStrapped()
	{
		if(!LOADER_ENABLED || ClassLoaderWitness.class.getClassLoader() instanceof PeeringClassloader)
			return true;
		return false;
	}

	public static SecondaryClassLoader getComponentLoader(URL[] urls)
	{
		if(!isBootStrapped())
			throw new IllegalStateException("Current Classloader is not part of the peering hierarchy!");
		ClassLoader primary = ClassLoaderWitness.class.getClassLoader();
		while(!(primary instanceof PrimaryClassloader))
			primary = primary.getParent();
		return new SecondaryClassLoader(urls,(PrimaryClassloader)primary);
	}

	public static void main(String[] args) {
		// TODO unify commandline processing and GUI selection code here
	}

}
