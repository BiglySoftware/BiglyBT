/*
 * Created on Apr 30, 2004
 * Created by Olivier Chalouhi
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
package com.biglybt.ui.swt.mainwindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.biglybt.core.Core;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.IUIIntializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UserPrompterResultListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.util.*;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.swt.UISwitcherListener;
import com.biglybt.ui.swt.UISwitcherUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.MessageBoxShell;

import com.biglybt.core.CoreFactory;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;

/**
 * The main SWT Thread, the only one that should run any GUI code.
 */
public class SWTThread implements AEDiagnosticsEvidenceGenerator {
  private static SWTThread instance;

  public static SWTThread getInstance() {
    return instance;
  }

  public static void createInstance(IUIIntializer initializer) throws SWTThreadAlreadyInstanciatedException {
    if(instance != null) {
      throw new SWTThreadAlreadyInstanciatedException();
    }

    	//Will only return on termination

    new SWTThread(initializer);

  }


  Display display;
  private boolean sleak;
  private boolean terminated;
  private Thread runner;
  private final IUIIntializer initializer;

	private Monitor primaryMonitor;
	protected boolean displayDisposed;

  private
  SWTThread(
  	final IUIIntializer app )
  {

    this.initializer = app;
		instance = this;
    Display.setAppName(Constants.APP_NAME);

    try {
      display = Display.getCurrent();
	  if ( display == null ){
		  if (System.getProperty("SWT.Device.DEBUG", "0").equals("1")) {
		  	Device.DEBUG = true;
		  }
		  display = new Display();
	      sleak = false;
	  }else{
		  sleak = true;
	  }
    } catch(Exception e) {
      display = new Display();
      sleak = false;
    } catch (UnsatisfiedLinkError ue) {
    	String sMin = "3.4";
			try {
				sMin = "" +  (((int)(SWT.getVersion() / 100)) / 10.0);
			} catch (Throwable t) {
			}
			try{
				String tempDir = System.getProperty ("swt.library.path");
				if (tempDir == null) {
					tempDir = System.getProperty ("java.io.tmpdir");
				}
				Debug.out("Loading SWT Libraries failed. "
						+ "Typical causes:\n\n"
						+ "(1) swt.jar is not for your os architecture ("
						+ System.getProperty("os.arch") + ").  "
						+ "You can get a new swt.jar (Min Version: " + sMin + ") "
						+ "from http://eclipse.org/swt"
						+ "\n\n"
						+ "(2) No write access to '" + tempDir
						+ "'. SWT will extract libraries contained in the swt.jar to this dir.\n", ue);

				app.stopIt(false, false);
				terminated = true;

				PlatformManagerFactory.getPlatformManager().dispose();
			} catch (Throwable t) {
			}
			return;
		}
    Thread.currentThread().setName("SWT Thread");

	Utils.initialize( display );

    primaryMonitor = display.getPrimaryMonitor();
    AEDiagnostics.addEvidenceGenerator(this);

    UISwitcherUtil.addListener(new UISwitcherListener() {
			@Override
			public void uiSwitched(String ui) {
				MessageBoxShell mb = new MessageBoxShell(
						MessageText.getString("dialog.uiswitcher.restart.title"),
						MessageText.getString("dialog.uiswitcher.restart.text"),
						new String[] {
							MessageText.getString("UpdateWindow.restart"),
							MessageText.getString("UpdateWindow.restartLater"),
						}, 0);
				mb.open(new UserPrompterResultListener() {
					@Override
					public void prompterClosed(int result) {
						if (result != 0) {
							return;
						}
						UIFunctions uif = UIFunctionsManager.getUIFunctions();
						if (uif != null) {
							uif.dispose(true, false);
						}
					}
				});
			}
		});

    // SWT.OpenDocument is only available on 3637
		try {
			Field fldOpenDoc = SWT.class.getDeclaredField("OpenDocument");
			int SWT_OpenDocument = fldOpenDoc.getInt(null);

			display.addListener(SWT_OpenDocument, new Listener() {
				@Override
				public void handleEvent(final Event event) {
					CoreFactory.addCoreRunningListener(new CoreRunningListener() {
						@Override
						public void coreRunning(Core core) {
							UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentOpenOptions(
									Utils.findAnyShell(), null, new String[] {
										event.text
									}, false, false);
						}
					});
				}
			});
		} catch (Throwable t) {
		}


		Listener lShowMainWindow = new Listener() {
			@Override
			public void handleEvent(Event event) {
				if ( event.type == SWT.Activate ){

					if ( AERunStateHandler.isDelayedUI()){

						Debug.out( "Ignoring activate event as delay start" );

						return;
					}
				}
				Shell as = Display.getDefault().getActiveShell();
				if (as != null) {
					as.setVisible(true);
					as.forceActive();
					return;
				}
				UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uif != null) {
					Shell mainShell = uif.getMainShell();
					if (mainShell == null || !mainShell.isVisible() || mainShell.getMinimized()) {
						if ( !COConfigurationManager.getBooleanParameter( "Reduce Auto Activate Window" )){

							uif.bringToFront(false);
						}
					}
				}
			}
		};
		display.addListener(SWT.Activate, lShowMainWindow);
		display.addListener(SWT.Selection, lShowMainWindow);

		display.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				displayDisposed = true;
			}
		});

		if (Constants.isOSX) {

			// On Cocoa, we get a Close trigger on display.  Need to check if all
			// platforms send this.
			display.addListener(SWT.Close, new Listener() {
				@Override
				public void handleEvent(Event event) {
					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
					if (uiFunctions != null) {
						event.doit = uiFunctions.dispose(false, false);
					}
				}
			});

			MenuFactory.initSystemMenu();
		}

		if (app != null) {
			app.runInSWTThread();
			runner = new Thread(new AERunnable() {
				@Override
				public void runSupport() {
					app.run();
				}
			}, "Main Thread");
			runner.start();
		}


    if(!sleak) {
      while(display != null && !display.isDisposed() && !terminated) {
        try {
            if (display != null && !display.readAndDispatch())
              display.sleep();
        }
        catch (Throwable e) {
					if (terminated) {
						Logger.log(new LogEvent(LogIDs.GUI,
								"Weird non-critical error after terminated in readAndDispatch: "
										+ e.toString()));
					} else {
						String stackTrace = Debug.getStackTrace(e);
						if (Constants.isOSX
								&& stackTrace.indexOf("Device.dispose") > 0
								&& stackTrace.indexOf("DropTarget") > 0) {
							Logger.log(new LogEvent(LogIDs.GUI,
									"Weird non-critical display disposal in readAndDispatch"));
						} else {
  						// Must use printStackTrace() (no params) in order to get
  						// "cause of"'s stack trace in SWT < 3119
  						if (SWT.getVersion() < 3119)
  							e.printStackTrace();
  						if (Constants.isCVSVersion()) {
  							Logger.log(new LogAlert(LogAlert.UNREPEATABLE,MessageText.getString("SWT.alert.erroringuithread"),e));
  						} else {
  							Debug.out(MessageText.getString("SWT.alert.erroringuithread"), e);
  						}
						}

					}
				}
      }

      if (instance != null) {
	      if (!terminated) {

		      // if we've falled out of the loop without being explicitly terminated then
		      // this appears to have been caused by a non-specific exit/restart request (as the
		      // specific ones should terminate us before disposing of the window...)
		      if (app != null) {
			      app.stopIt(false, false);
		      }

		      terminate();
	      }

	      // dispose platform manager here
	      PlatformManagerFactory.getPlatformManager().dispose();
      }

      // Could still be disposing.. wait to be sure
      try {
	      while (display != null && !display.isDisposed()) {
	        if (!display.readAndDispatch() && !display.isDisposed()) {
	          display.sleep();
		      }
	      }
      } catch (Exception e) {
      	e.printStackTrace();
      }
    }
  }

  public void terminate() {
    terminated = true;
    Utils.setTerminated();
    // must dispose here in case another window has take over the
    // readAndDispatch/sleep loop
    if (!display.isDisposed()) {
    	
    	Runnable disposer = 
    		new Runnable() {
			    @Override
			    public void run() {
				    try {
				    	if ( !display.isDisposed()){
						    Shell[] shells = display.getShells();
						    for (int i = 0; i < shells.length; i++) {
							    try {
								    Shell shell = shells[i];
								    shell.dispose();
							    } catch (Throwable t) {
								    Debug.out(t);
							    }
						    }
				    	}
				    } catch (Throwable t) {
					    Debug.out(t);
				    }
				    
				    	// while crash is occurring we can just avoid the dispose completely and let the VM death trash things
				    
				    if ( !Constants.isWindows8OrHigher ){
				    	if ( !display.isDisposed()){
				    		try{
				    			display.dispose();
				    		}catch( Throwable e ){
				    		}
				    	}
				    }
			    }
    	};

    		// for 1.2 there is a bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=526758 causing crash on exit
    	
		if ( Constants.isWindows8OrHigher ){
			
			display.asyncExec(
				new Runnable()
				{
					public void
					run()
					{
						if ( !display.isDisposed()){
							
							display.timerExec( 2500,disposer );
						}
					}
				});
		}else{
			
			display.asyncExec( disposer );
    	}
    }
  }

  public Display getDisplay() {
    return display;
  }

  public boolean isTerminated() {
  	//System.out.println("isTerm" + terminated + ";" + display.isDisposed() + Debug.getCompressedStackTrace(3));
  	return terminated || displayDisposed || display == null || display.isDisposed();
  }

	public IUIIntializer getInitializer() {
		return initializer;
	}

	public Monitor getPrimaryMonitor() {
		return primaryMonitor;
	}

	public void terminateSWTOnly() {
  	if (!displayDisposed) {
  		displayDisposed = true;
  		instance = null;

  		// Shutdown shells first, so they can access static instances and
  		// dispose of things
		  try {
			  Shell[] shells = display.getShells();
			  for (int i = 0; i < shells.length; i++) {
				  try {
					  Shell shell = shells[i];
					  shell.dispose();
				  } catch (Throwable t) {
					  Debug.out(t);
				  }
			  }
		  } catch (Throwable t) {
			  Debug.out(t);
		  }

		  initializer.shutdownUIOnly();
		  // dispose display after shutdownUIOnly, because it handles image/color
		  // disposal which still requires display
		  display.setData("Disposing", true);
		  display.dispose();

		  System.err.println("Bad things will probably happen until we do a better job at disposing the UI");
	  }
	}

	@Override
	public void generate(IndentWriter writer) {
		writer.println("SWT");

		try {
			writer.indent();

			writer.println("SWT Version:" + SWT.getVersion() + "/"
					+ SWT.getPlatform());

			writer.println("org.eclipse.swt.browser.XULRunnerPath: "
					+ System.getProperty("org.eclipse.swt.browser.XULRunnerPath", ""));
			writer.println("MOZILLA_FIVE_HOME: "
					+ SystemProperties.getEnvironmentalVariable("MOZILLA_FIVE_HOME"));

		} finally {

			writer.exdent();
		}
	}
}
