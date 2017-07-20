package com.biglybt.platform.macosx.access.cocoa;

/*
 * Created on 27-Mar-2005
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.Debug;
import com.biglybt.platform.macosx.NativeInvocationBridge;

/**
 * NOTE: This is only used for OSX 10.4 and non-up-to-date 10.5,10.6.
 * OSX 10.5 Update 6 and OSX 10.6 Update 1 provides revealInFinder and moveToTrash
 * functionality.
 * <p>
 * <p>Performs PlatformManager tasks using Cocoa-Java (FoundationKit only)</p>
 * <p>For now, operations are performed using NSAppleScript, rather than using NSWorkspace.
 * This is still significantly faster than calling the cmd-line osascript.</p>
 * @version 2.1 Apr 2, 2005
 */
public final class CocoaJavaBridge extends NativeInvocationBridge
{
    /**
     * The path the Cocoa-Java class files are located at
     */
    protected static final String CLASS_PATH = "/system/library/java";

    private static final String REVEAL_SCRIPT_FORMAT = "tell application \"System Events\"\ntell application \"{0}\"\nactivate\nreveal (posix file \"{1}\" as alias)\nend tell\nend tell";

    private static final String DEL_SCRIPT_FORMAT = "tell application \"Finder\" to move (posix file \"{0}\" as alias) to the trash";

    /**
     * Main NSAutoreleasePool
     */
    private int mainPool;

    protected AEMonitor classMon = new AEMonitor("CocoaJavaBridge:C");
    private AEMonitor scriptMon = new AEMonitor("CocoaJavaBridge:S");

    protected boolean isDisposed = false;

    protected RunnableDispatcher scriptDispatcher;

    private Class claNSAppleEventDescriptor;

		private Class<?> claNSAutoreleasePool;

		private Method methPush;

		private Method methPop;

		private Method methNSAppleEventDescriptor_descriptorWithBoolean;

		private Class<?> claNSAppleScript;

		private Class<?> claNSMutableDictionary;

		private Method methNSAppleScript_execute;

		private String NSAppleScript_AppleScriptErrorMessage;

		private Method methNSMutableDictionary_objectForKey;

    public CocoaJavaBridge() throws Throwable
    {
        try
        {
            classMon.enter();

            claNSMutableDictionary = Class.forName("com.apple.cocoa.foundation.NSMutableDictionary");
            methNSMutableDictionary_objectForKey = claNSMutableDictionary.getMethod("objectForKey", Object.class);

            claNSAppleEventDescriptor = Class.forName("com.apple.cocoa.foundation.NSAppleEventDescriptor");
            methNSAppleEventDescriptor_descriptorWithBoolean = claNSAppleEventDescriptor.getMethod("descriptorWithBoolean", new Class [] { boolean.class });

            claNSAutoreleasePool = Class.forName("com.apple.cocoa.foundation.NSAutoreleasePool");
            methPush = claNSAutoreleasePool.getMethod("push", new Class [0]);
            methPop = claNSAutoreleasePool.getMethod("pop", new Class [] { int.class });

            claNSAppleScript = Class.forName("com.apple.cocoa.foundation.NSAppleScript");
            methNSAppleScript_execute = claNSAppleScript.getMethod("execute", new Class[] { claNSMutableDictionary });
            NSAppleScript_AppleScriptErrorMessage = (String) claNSAppleScript.getField("AppleScriptErrorMessage").get(null);


            //mainPool = NSAutoreleasePool.push();
            mainPool = NSAutoreleasePool_push();

            scriptDispatcher = new RunnableDispatcher();
        }
        finally
        {
            classMon.exit();
        }
    }

    private int NSAutoreleasePool_push() throws Throwable
    {
      return ((Number) methPush.invoke(null)).intValue();
    }

    private void NSAutoreleasePool_pop(int i) throws Throwable
    {
      methPop.invoke(null, i);
    }

    private Object new_NSAppleScript(String s) throws Throwable {
    	return claNSAppleScript.getConstructor(new Class[] { String.class }).newInstance(s);
    }

    private Object NSAppleScript_execute(Object NSAppleScript, Object NSMutableDictionary) throws Throwable {
    	return methNSAppleScript_execute.invoke(NSAppleScript, NSMutableDictionary);
    }

    private Object new_NSMutableDictionary() throws Throwable {
    	return claNSMutableDictionary.newInstance();
    }

    private Object NSMutableDictionary_objectForKey(Object NSMutableDictionary, String s) throws Throwable {
    	return methNSMutableDictionary_objectForKey.invoke(NSMutableDictionary, s);
    }

    // interface implementation

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean performRecoverableFileDelete(File path)
    {
        if(!path.exists()){
            return false;
        }

        Object result;
				try {
					result = executeScriptWithAsync(DEL_SCRIPT_FORMAT, new Object[]{path.getAbsolutePath()});
				} catch (Throwable t) {
					Debug.out(t);
					return false;
				}

        	// quick hack here for people where things take a while - too scared to make it a
        	// sync call as I don't know the code...

        if ( result != null ){

            final int sleep = 25;

            int sleep_to_go = 2500;

        	while( path.exists()){

        		if ( sleep_to_go <= 0 ){

        			break;
        		}

        		try{
        			Thread.sleep( sleep );

        			sleep_to_go -= sleep;

        		}catch( Throwable e ){
        			break;
        		}
        	}

        	if ( path.exists()){

        		Debug.outNoStack( "Gave up waiting for delete to complete for " + path );
        	}
        }

        return( result != null );
    }

    /**
     * {@inheritDoc}
     */
	@Override
	protected boolean showInFinder(File path, String fileBrowserApp) {
		if (!path.exists())
			return false;

		Object /*NSAppleEventDescriptor*/ result = null;
		try {
			int pool = NSAutoreleasePool_push();
			try {
				result = executeScriptWithAsync(REVEAL_SCRIPT_FORMAT, new Object[] {
					fileBrowserApp,
					path.getAbsolutePath()
				});
			} finally {
				NSAutoreleasePool_pop(pool);
			}
		} catch (Throwable t) {
			return false;
		}
		return (result != null);
	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isEnabled()
    {
    	return claNSAutoreleasePool != null;
    }

    // class utility methods

    /**
     * <p>Executes a new instance of NSAppleScript</p>
     * <p>The method is wrapped in an autorelease pool and an AEMonitor. If there are
     * no format parameters, MessageFormat is not used to parse the format string, and
     * the format string will be treated as the source itself.</p>
     * @see MessageFormat#format(String, Object...)
     * @see NSAppleScript#execute(com.apple.cocoa.foundation.NSMutableDictionary)
     */
    protected final Object /*NSAppleEventDescriptor*/ executeScript(String scriptFormat, Object[] params) throws Throwable
    {
        try
        {
            scriptMon.enter();

            int pool = NSAutoreleasePool_push();
            long start = System.currentTimeMillis();

            String src;
            if(params == null || params.length == 0)
            {
                src = scriptFormat;
            }
            else
            {
                src = MessageFormat.format(scriptFormat, params);
            }

            Debug.outNoStack("Executing: \n" + src);

            Object /*NSAppleScript*/ scp = new_NSAppleScript(src);
            Object /*NSAppleEventDescriptor*/ result =  NSAppleScript_execute(scp, new_NSMutableDictionary());

            Debug.outNoStack(MessageFormat.format("Elapsed time: {0}ms\n", new Object[]{new Long(System.currentTimeMillis() - start)}));
            NSAutoreleasePool_pop(pool);
            return result;
        }
        finally
        {
            scriptMon.exit();
        }
    }

    /**
     * <p>Executes a new instance of NSAppleScript in a forked AEThread</p>
     * <p>This method always returns a "true" event descriptor. Callbacks are currently unsupported
     * , so in the event of an error, the logger is autuomatically notified.</p>
     * <p>The thread's runSupport method is wrapped in an autorelease pool. If there are
     * no format parameters, MessageFormat is not used to parse the format string, and
     * the format string will be treated as the source itself.</p>
     * @see com.biglybt.core.util.AEThread#runSupport()
     * @see MessageFormat#format(String, Object...)
     * @see NSAppleScript#execute(com.apple.cocoa.foundation.NSMutableDictionary)
     * @return NSAppleEventDescriptor.descriptorWithBoolean(true)
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
  protected final Object /*NSAppleEventDescriptor*/ executeScriptWithNewThread(
		final String scriptFormat, final Object[] params) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
  {
		Thread worker = new AEThread("ScriptObject", true) {
			@Override
			public void runSupport() {
				try {
					int pool = NSAutoreleasePool_push();
					long start = System.currentTimeMillis();

					String src;
					if (params == null || params.length == 0) {
						src = scriptFormat;
					} else {
						src = MessageFormat.format(scriptFormat, params);
					}

					Debug.outNoStack("Executing: \n" + src);

					Object /*NSMutableDictionary*/ errorInfo = new_NSMutableDictionary();
					if (NSAppleScript_execute(new_NSAppleScript(src), errorInfo) == null) {
						Debug.out(String.valueOf(NSMutableDictionary_objectForKey(errorInfo, NSAppleScript_AppleScriptErrorMessage)));
						//logWarning(String.valueOf(errorInfo.objectForKey(NSAppleScript.AppleScriptErrorBriefMessage)));
					}

					Debug.outNoStack(MessageFormat.format("Elapsed time: {0}ms\n",
							new Object[] {
								new Long(System.currentTimeMillis() - start)
							}));
					NSAutoreleasePool_pop(pool);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		};

		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();

		return methNSAppleEventDescriptor_descriptorWithBoolean.invoke(null, true);
		//return NSAppleEventDescriptor.descriptorWithBoolean(true);
	}

    /**
     * <p>Asynchronously executes a new instance of NSAppleScript</p>
     * <p>This method always returns a "true" event descriptor. Callbacks are currently unsupported
     * , so in the event of an error, the logger is autuomatically notified.</p>
     * <p>The thread's runSupport method is wrapped in an autorelease pool. If there are
     * no format parameters, MessageFormat is not used to parse the format string, and
     * the format string will be treated as the source itself.</p>
     * @see com.biglybt.core.util.AEThread#runSupport()
     * @see MessageFormat#format(String, Object...)
     * @see NSAppleScript#execute(com.apple.cocoa.foundation.NSMutableDictionary)
     * @return NSAppleEventDescriptor.descriptorWithBoolean(true)
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    protected final Object /*NSAppleEventDescriptor*/ executeScriptWithAsync(final String scriptFormat, final Object[] params) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        final AERunnable worker = new AERunnable()
        {
			@Override
			public void runSupport() {
				try {
					int pool = NSAutoreleasePool_push();
					long start = System.currentTimeMillis();

					String src;
					if (params == null || params.length == 0) {
						src = scriptFormat;
					} else {
						src = MessageFormat.format(scriptFormat, params);
					}

					Debug.outNoStack("Executing: \n" + src);

					Object /*NSMutableDictionary*/ errorInfo = new_NSMutableDictionary();
					if (NSAppleScript_execute(new_NSAppleScript(src), errorInfo) == null) {
						Debug.out(String.valueOf(NSMutableDictionary_objectForKey(errorInfo, NSAppleScript_AppleScriptErrorMessage)));
						//logWarning(String.valueOf(errorInfo.objectForKey(NSAppleScript.AppleScriptErrorBriefMessage)));
					}

					Debug.outNoStack(MessageFormat.format("Elapsed time: {0}ms\n",
							new Object[] {
								new Long(System.currentTimeMillis() - start)
							}));
					NSAutoreleasePool_pop(pool);
				} catch (Throwable t) {
					Debug.out(t);
				}
			}
		};

        AEThread t = new AEThread("ScriptObject", true)
        {
            @Override
            public void runSupport()
            {
                scriptDispatcher.exec(worker);
            }
        };
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();

    		return methNSAppleEventDescriptor_descriptorWithBoolean.invoke(null, true);
    		//return NSAppleEventDescriptor.descriptorWithBoolean(true);
    }

    /**
     * Logs a warning message to Logger. The class monitor is used.
     * @param message A warning message
     */
    private void logWarning(String message)
    {
        try
        {
            classMon.enter();
            Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING, message));
        }
        finally
        {
            classMon.exit();
        }
    }

    // disposal

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispose()
    {
        try
        {
            classMon.enter();
            if(!isDisposed)
            {
                Debug.outNoStack("Disposing Native PlatformManager...");
                try {
									NSAutoreleasePool_pop(mainPool);
								} catch (Throwable e) {
								}
                isDisposed = true;
                Debug.outNoStack("Done");
            }
        }
        finally
        {
            classMon.exit();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize() throws Throwable
    {
        dispose();
        super.finalize();
    }

    /**
     * A dispatch object to help facilitate asychronous script execution (from the main thread) in a more
     * predictable fashion.
     */
    private static class RunnableDispatcher
    {
        /**
         * Executes a Runnable object while synchronizing the RunnableDispatcher instance.
         * @param runnable A Runnable
         */
        private void exec(Runnable runnable)
        {
            synchronized(this)
            {
                runnable.run();
            }
        }
    }
}
