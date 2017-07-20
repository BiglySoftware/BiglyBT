package com.biglybt.platform.macosx;

/*
 * Created on 26-Mar-2005
 * Created by James Yeh
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

import java.io.File;

/**
 * <p>Performs PlatformManager and platform-specific tasks using bridges like Cocoa-Java -> ObjC</p>
 * <p>The methods supplied are intended to reflect those that can be dealt with a way other than
 * OSAScript. Ensure that the method signatures match those of PlatformManagerImpl, but
 * they should generally return a boolean (false for failure).</p>
 * @version 1.0
 */
public abstract class NativeInvocationBridge
{
    private static NativeInvocationBridge instance;

    protected NativeInvocationBridge(){}

  /**
   * Gets the singleton
   * @return The NativeInvocationBridge singleton
   */
	protected static NativeInvocationBridge sharedInstance() {
		if (instance == null) {
			try {
				Object newInstance = Class.forName(
                        "com.biglybt.platform.macosx.access.cocoa.CocoaJavaBridge").getConstructor().newInstance();
				instance = (NativeInvocationBridge) newInstance;
			} catch (Throwable e) {
				//Debug.out(e);
				instance = new DummyBridge();
			}
		}
		return instance;
	}

	protected static boolean hasSharedInstance() {
		return instance != null;
	}

     /**
     * @see PlatformManager#performRecoverableFileDelete(java.io.File)
     */
     protected boolean performRecoverableFileDelete(File path) {return false;}

    /**
     * @see PlatformManagerImpl#showInFinder(java.io.File)
     */
    protected boolean showInFinder(File path, String fb) {return false;}

    /**
     * <p>Gets whether the invocation bridge is available for use</p>
     * <p>This method is used to anticipate scenarios such as where the bridge will fail due to missing classpaths</p>
     */
    protected abstract boolean isEnabled();

    /**
     * Disposes system resources
     */
    protected void dispose(){}

    /**
     * A NativeInvocationBridge that does nothing; isEnabled() always returns false.
     */
    private static class DummyBridge extends NativeInvocationBridge
    {
        @Override
        public boolean isEnabled()
        {
            return false;
        }
    }
}
