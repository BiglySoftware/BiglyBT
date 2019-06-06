/*
 * Created on Feb 9, 2009
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.donations;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.shells.MessageBoxShell;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

/**
 * @author TuxPaper
 * @created Feb 9, 2009
 *
 */
public class DonationWindow
{
	public static boolean DEBUG = System.getProperty("donations.debug", "0").equals(
			"1");

	static int reAskEveryHours = 96;

	private static int initialAskHours = 48;

	static boolean pageLoadedOk = false;

	static Shell shell = null;

	static BrowserWrapper.BrowserFunction browserFunction;

	public static void 
	checkForDonationPopup() 
	{
		synchronized( DonationWindow.class ){
			
			if (shell != null) {
				if (DEBUG) {
					new MessageBoxShell(SWT.OK, "Donations Test", "Already Open").open(null);
				}
				return;
			}
	
			long maxDate = COConfigurationManager.getLongParameter("donations.maxDate", 0);
			
			boolean force = maxDate > 0 && SystemTime.getCurrentTime() > maxDate;
	
			if ( force ){
			
					// bump up max-date to avoid multiple 'concurrent' additions from triggering
					// multiple windows
				
				COConfigurationManager.setParameter("donations.maxDate", maxDate + 24*60*60*1000 );
			}
			
				//Check if user has already donated first
			
			boolean alreadyDonated = COConfigurationManager.getBooleanParameter("donations.donated", false);
			
			if (alreadyDonated && !force) {
				if (DEBUG) {
					new MessageBoxShell(SWT.OK, "Donations Test",
							"Already Donated! I like you.").open(null);
				}
				return;
			}
	
			OverallStats stats = StatsFactory.getStats();
			
			if (stats == null){
				
				return;
			}
	
			long upTime = stats.getTotalUpTime();
			
			int hours = (int) (upTime / (60 * 60)); //secs * mins
	
				//Ask every DONATIONS_ASK_AFTER hours.
			
			int nextAsk = COConfigurationManager.getIntParameter( "donations.nextAskHours", 0 );
	
			if ( nextAsk == 0 ){
				
					// First Time
				
				COConfigurationManager.setParameter( "donations.nextAskHours", hours	+ initialAskHours );
				
				COConfigurationManager.save();
				
				if (DEBUG) {
					new MessageBoxShell(SWT.OK, "Donations Test",
							"Newbie. You're active for " + hours + ".").open(null);
				}
				return;
			}
	
			if (hours < nextAsk && !force){
				
				if (DEBUG) {
					new MessageBoxShell(SWT.OK, "Donations Test", "Wait "
							+ (nextAsk - hours) + ".").open(null);
				}
				return;
			}
	
			long minDate = COConfigurationManager.getLongParameter("donations.minDate",	0);
			
			if (minDate > 0 && minDate > SystemTime.getCurrentTime()) {
				if (DEBUG) {
					new MessageBoxShell(SWT.OK, "Donation Test", "Wait "
							+ ((SystemTime.getCurrentTime() - minDate) / 1000 / 3600 / 24)
							+ " days").open(null);
				}
				return;
			}
	
			COConfigurationManager.setParameter("donations.nextAskHours", hours	+ reAskEveryHours);
			
			COConfigurationManager.save();
		}
		
		open(false, "check");
	}

	public static void open(final boolean showNoLoad, final String sourceRef) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				_open(showNoLoad, sourceRef);
			}
		});
	}

	public static void _open(final boolean showNoLoad, final String sourceRef) {
		if (shell != null && !shell.isDisposed()) {
			return;
		}
		final Shell parentShell = Utils.findAnyShell();
		shell = ShellFactory.createShell(parentShell,
				SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		shell.setLayout(new FillLayout());
		if (parentShell != null) {
			parentShell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		}

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					e.widget.dispose();
					e.doit = false;
				}
			}
		});

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (parentShell != null) {
					parentShell.setCursor(e.display.getSystemCursor(SWT.CURSOR_ARROW));
				}
				if (browserFunction != null && !browserFunction.isDisposed()) {
					browserFunction.dispose();
				}
				shell = null;
			}
		});

		BrowserWrapper browser = Utils.createSafeBrowser(shell, SWT.NONE);
		if (browser == null) {
			shell.dispose();
			return;
		}

		browser.addTitleListener(new TitleListener() {
			@Override
			public void changed(TitleEvent event) {
				if (shell == null || shell.isDisposed()) {
					return;
				}
				shell.setText(event.title);
			}
		});

		browserFunction = browser.addBrowserFunction(
			"sendDonationEvent",
			new BrowserWrapper.BrowserFunction()
			{
				@Override
				public Object function(Object[] arguments) {

					if (shell == null || shell.isDisposed()) {
						return null;
					}

					if (arguments == null) {
						Debug.out("Invalid sendDonationEvent null ");
						return null;
					}
					if (arguments.length < 1) {
						Debug.out("Invalid sendDonationEvent length " + arguments.length + " not 1");
						return null;
					}
					if (!(arguments[0] instanceof String)) {
						Debug.out("Invalid sendDonationEvent "
								+ (arguments[0] == null ? "NULL"
										: arguments.getClass().getSimpleName()) + " not String");
						return null;
					}

					String text = (String) arguments[0];
					if (text.contains("page-loaded")) {
						pageLoadedOk = true;
						COConfigurationManager.setParameter("donations.count",
								COConfigurationManager.getLongParameter("donations.count", 1) + 1);
						Utils.centreWindow(shell);
						if (parentShell != null) {
							parentShell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
						}
						shell.open();
					} else if (text.contains("reset-ask-time")) {
						int time = reAskEveryHours;
						String[] strings = text.split(" ");
						if (strings.length > 1) {
							try {
								time = Integer.parseInt(strings[1]);
							} catch (Throwable ignore) {
							}
						}
						resetAskTime(time);
					} else if (text.contains("never-ask-again")) {
						neverAskAgain();
					} else if (text.contains("close")) {
						Utils.execSWTThreadLater(0, new AERunnable() {
							@Override
							public void runSupport() {
								if (shell != null && !shell.isDisposed()) {
									shell.dispose();
								}
							}
						});
					} else if (text.startsWith("open-url")) {
						String url = text.substring(9);
						Utils.launch(url);
					} else if (text.startsWith("set-size")) {
						String[] strings = text.split(" ");
						if (strings.length > 2) {
							try {
								int w = Integer.parseInt(strings[1]);
								int h = Integer.parseInt(strings[2]);

								Rectangle computeTrim = shell.computeTrim(0, 0, w, h);
								shell.setSize(computeTrim.width, computeTrim.height);
							} catch (Exception ignore) {
							}
						}
					}
					return null;
				}
			});

		browser.addLocationListener(new LocationListener() {
			@Override
			public void changing(LocationEvent event) {
			}

			@Override
			public void changed(LocationEvent event) {
			}
		});

		long upTime = StatsFactory.getStats().getTotalUpTime();
		int upHours = (int) (upTime / (60 * 60)); //secs * mins
		final String url = Constants.URL_DONATION + "?locale="
				+ MessageText.getCurrentLocale().toString() + "&azv="
				+ Constants.BIGLYBT_VERSION + "&count="
				+ COConfigurationManager.getLongParameter("donations.count", 1)
				+ "&uphours=" + upHours + "&sourceref="
				+ UrlUtils.encode(sourceRef);

		if ( !browser.isFake()){

			SimpleTimer.addEvent("donation.pageload", SystemTime.getOffsetTime(5000),
					event -> {
						if (pageLoadedOk) {
							return;
						}
						Utils.execSWTThread(() -> {
							Debug.out("Page Didn't Load:" + url);
							shell.dispose();
							if (showNoLoad) {
								Utils.launch(Constants.URL_DONATION);
							}
						});
					});
		}

		browser.setUrl(url);

		if ( browser.isFake()){

			browser.setUrl( Constants.URL_DONATION );

			browser.setText( "Please follow the link to donate via an external browser" );

			shell.setSize( 400, 500 );

			Utils.centreWindow(shell);

			if (parentShell != null) {
				parentShell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}

			shell.open();
		}
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected static void  neverAskAgain() {
		COConfigurationManager.setParameter("donations.donated", true);
		updateMinDate();
		COConfigurationManager.save();
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	public static void resetAskTime() {
		resetAskTime(reAskEveryHours);
	}

	public static void resetAskTime(int askEveryHours) {
		long upTime = StatsFactory.getStats().getTotalUpTime();
		int hours = (int) (upTime / (60 * 60)); //secs * mins
		int nextAsk = hours + askEveryHours;
		COConfigurationManager.setParameter("donations.nextAskHours", nextAsk);
		COConfigurationManager.setParameter("donations.lastVersion", Constants.BIGLYBT_VERSION);
		updateMinDate();
		COConfigurationManager.save();
	}

	public static void updateMinDate() {
		COConfigurationManager.setParameter("donations.minDate", SystemTime.getOffsetTime(1000L * 3600 * 24 * 30));  //30d ahead
		COConfigurationManager.setParameter("donations.maxDate", SystemTime.getOffsetTime(1000L * 3600 * 24 * 120));  //4mo ahead
		//COConfigurationManager.save();
	}

   //unused
	//public static void setMinDate(long timestamp) {
	//	COConfigurationManager.setParameter("donations.minDate", timestamp);
	//	COConfigurationManager.save();
	//}

	public static int getInitialAskHours() {
		return initialAskHours;
	}

	public static void setInitialAskHours(int i) {
		initialAskHours = i;
	}
}