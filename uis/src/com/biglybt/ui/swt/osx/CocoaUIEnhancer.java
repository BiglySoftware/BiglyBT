/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.osx;

import java.lang.reflect.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.util.Debug;

/**
 * You can exclude this file (or this whole path) for non OSX builds
 *
 * Hook some Cocoa specific abilities:
 * - getFileIcon
 */
public class CocoaUIEnhancer
{
	private static Class<?> nsstringCls = classForName(
			"org.eclipse.swt.internal.cocoa.NSString");

	private static Class<?> nsautoreleasepoolCls = classForName(
			"org.eclipse.swt.internal.cocoa.NSAutoreleasePool");

	private static Class<?> nsworkspaceCls = classForName(
			"org.eclipse.swt.internal.cocoa.NSWorkspace");

	private static Class<?> nsimageCls = classForName(
			"org.eclipse.swt.internal.cocoa.NSImage");

	private static Class<?> nssizeCls = classForName(
			"org.eclipse.swt.internal.cocoa.NSSize");

	private static Class<?> osCls = classForName(
			"org.eclipse.swt.internal.cocoa.OS");

	private static Method method_os_sAppDarkAppearance;
	
	static {
		if ( SWT.getVersion() >= 4924 ) {
			
			try {
				
				method_os_sAppDarkAppearance = osCls.getMethod( "isAppDarkAppearance" );
				
			}catch( Throwable e ) {
				
			}
		}
	}
	private static Class<?> classForName(String classname) {
		try {
			return Class.forName(classname);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object invoke(Class<?> clazz, Object target, String methodName,
			Object... args) {
		try {
			Class<?>[] signature = new Class<?>[args.length];
			for (int i = 0; i < args.length; i++) {
				Class<?> thisClass = args[i].getClass();
				if (thisClass == Integer.class)
					signature[i] = int.class;
				else if (thisClass == Long.class)
					signature[i] = long.class;
				else if (thisClass == Byte.class)
					signature[i] = byte.class;
				else if (thisClass == Boolean.class)
					signature[i] = boolean.class;
				else
					signature[i] = thisClass;
			}
			Method method = clazz.getMethod(methodName, signature);
			return method.invoke(target, args);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object invoke(Class<?> clazz, Object target, String methodName,
			Class[] signature, Object... args) {
		try {
			Method method = clazz.getDeclaredMethod(methodName, signature);
			method.setAccessible(true);
			return method.invoke(target, args);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object invoke(Object obj, String methodName) {
		return invoke(obj, methodName, null, (Object[]) null);
	}

	private static Object invoke(Object obj, String methodName,
			Class<?>[] paramTypes, Object... arguments) {
		try {
			Method m = obj.getClass().getMethod(methodName, paramTypes);
			return m.invoke(obj, arguments);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	////////////////////////////////////////////////////////////

	// from Program.getImageData, except returns bigger images
	public static Image getFileIcon(String path, int imageWidthHeight) {
		Object pool = null;
		try {
			//NSAutoreleasePool pool = (NSAutoreleasePool) new NSAutoreleasePool().alloc().init();
			pool = nsautoreleasepoolCls.newInstance();
			Object delegateAlloc = invoke(pool, "alloc");
			invoke(delegateAlloc, "init");

			//NSWorkspace workspace = NSWorkspace.sharedWorkspace();
			Object workspace = invoke(nsworkspaceCls, null, "sharedWorkspace");
			//NSString fullPath = NSString.stringWith(path);
			Object fullPath = invoke(nsstringCls, null, "stringWith", path);
			if (fullPath != null) {
				// SWT also had a :
				// fullPath = workspace.fullPathForApplication(NSString.stringWith(name));
				// which might be handy someday, but for now, full path works

				//NSImage nsImage = workspace.iconForFile(fullPath);
				Object nsImage = invoke(workspace, "iconForFile", new Class[] {
					nsstringCls
				}, fullPath);
				if (nsImage != null) {
					//NSSize size = new NSSize();
					Object size = nssizeCls.newInstance();
					//size.width = size.height = imageWidthHeight;
					nssizeCls.getField("width").set(size, imageWidthHeight);
					nssizeCls.getField("height").set(size, imageWidthHeight);
					//nsImage.setSize(size);
					invoke(nsImage, "setSize", new Class[] {
						nssizeCls
					}, size);
					//nsImage.retain();
					invoke(nsImage, "retain");
					//Image image = Image.cocoa_new(Display.getCurrent(), SWT.BITMAP, nsImage);
					Image image = (Image) invoke(Image.class, null, "cocoa_new",
							new Class[] {
								Device.class,
								int.class,
								nsimageCls
							}, new Object[] {
								Display.getCurrent(),
								SWT.BITMAP,
								nsImage
							});
					return image;
				}
			}
		} catch (Throwable t) {
			Debug.printStackTrace(t);
		} finally {
			if (pool != null) {
				invoke(pool, "release");
			}
		}
		return null;
	}

	public static boolean
	isAppDarkAppearance()
	{
		try {
			if ( method_os_sAppDarkAppearance != null ) {
				
				return((Boolean)method_os_sAppDarkAppearance.invoke(null));
			}
		}catch( Throwable e ) {
			
		}
		
		return( false );
	}
	public static boolean isInitialized() {
		return true;
	}
}
