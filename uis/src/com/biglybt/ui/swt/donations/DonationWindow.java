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

import java.util.Calendar;
import java.util.Date;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.stats.transfer.LongTermStats;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.utils.FontUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
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

	private static int extraHeight;
	private static GridLayout shellLayout;

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
	
			if ( alreadyDonated ){
				
					// we don't want to re-ask users that have already donated very frequently
				
				updateMinDate( false );
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

	private static void _open(final boolean showNoLoad, final String sourceRef) {
		if (shell != null && !shell.isDisposed()) {
			return;
		}
		extraHeight = 0;
		final Shell parentShell = Utils.findAnyShell();
		shell = ShellFactory.createShell(parentShell,SWT.DIALOG_TRIM | SWT.RESIZE);
		shellLayout = Utils.getSimpleGridLayout(1);
		shellLayout.marginWidth = 10;
		shellLayout.marginHeight = 10;
		shell.setLayout(shellLayout);

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

		LongTermStats lt_stats = StatsFactory.getLongTermStats();
		if (lt_stats != null) {
			Label label = new Label(shell, SWT.NONE);
			FontUtils.setFontHeight(label, 13, SWT.BOLD);
			label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
			extraHeight = label.computeSize(-1, -1).y;
			new AEThread2("YearStats", true) {
				@Override
				public void run() {
					Calendar calendar = Calendar.getInstance();
					// Show previous year for Jan, Feb
					calendar.add(Calendar.MONTH, -2);
					int year = calendar.get(Calendar.YEAR);
					calendar.set(year, Calendar.JANUARY, 1, 0, 0, 0);
					calendar.set(Calendar.MILLISECOND, 0);
					Date startDate = calendar.getTime();
					calendar.set(Calendar.YEAR, year + 1);
					calendar.add(Calendar.SECOND, -1);
					Date endDate = calendar.getTime();
					long[] stats = lt_stats.getTotalUsageInPeriod(startDate, endDate);
					long ulBytes = stats[0] + stats[1] + stats[4];
					long dlBytes = stats[2] + stats[3] + stats[5];
					Utils.execSWTThread(() -> {
						if (label.isDisposed()) {
							return;
						}
						Messages.setLanguageText(label, "DonateWindow.YearStats", "" + year,
								DisplayFormatters.formatByteCountToKiBEtc(dlBytes),
								DisplayFormatters.formatByteCountToKiBEtc(ulBytes));
						label.requestLayout();
					});
				}
			}.start();
		}

		BrowserWrapper browser = Utils.createSafeBrowser(shell, SWT.NONE);
		if (browser == null) {
			shell.dispose();
			return;
		}
		browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		browser.addTitleListener(event -> {
			if (shell == null || shell.isDisposed()) {
				return;
			}
			shell.setText(event.title);
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
								// no idea if dpi adjustment needed for non-windows
								Point dpi = Constants.isWindows ? Utils.getDisplay().getDPI() : new Point(96, 96);
								int nw = Integer.parseInt(strings[1]);
								int nh = Integer.parseInt(strings[2]);
								int w = (int) (nw * (dpi.x / 96.0)) + (shellLayout.verticalSpacing * 2) + (shellLayout.marginWidth * 2);
								int h = (int) (nh * (dpi.y / 96.0)) + extraHeight + (shellLayout.verticalSpacing * 2)  + (shellLayout.marginWidth * 2);

								Rectangle computeTrim = shell.computeTrim(0, 0, w, h);
								//System.out.println("trim " + shell.computeTrim(0,0,0,0) + " filled: " + computeTrim + "; dpi=" + dpi + "; s=" + w + ", " + h);
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
	private static void  neverAskAgain() {
		COConfigurationManager.setParameter("donations.donated", true);
		updateMinDate( true );
		COConfigurationManager.save();
	}

	private static void resetAskTime(int askEveryHours) {
		long upTime = StatsFactory.getStats().getTotalUpTime();
		int hours = (int) (upTime / (60 * 60)); //secs * mins
		int nextAsk = hours + askEveryHours;
		COConfigurationManager.setParameter("donations.nextAskHours", nextAsk);
		COConfigurationManager.setParameter("donations.lastVersion", Constants.BIGLYBT_VERSION);
		updateMinDate( false );
		COConfigurationManager.save();
	}

	private static void updateMinDate( boolean isNever ) {
		long min_days;
		long max_days;
		
		if ( isNever ){
			min_days = 90;
			max_days = 365;
		}else{
			min_days = 30;
			max_days = 120;
		}
		
		COConfigurationManager.setParameter("donations.minDate", SystemTime.getOffsetTime( 1000*3600*24*min_days ));
		COConfigurationManager.setParameter("donations.maxDate", SystemTime.getOffsetTime( 1000*3600*24*max_days ));
		//COConfigurationManager.save();
	}

	public static int getInitialAskHours() {
		return initialAskHours;
	}

	public static void setInitialAskHours(int i) {
		initialAskHours = i;
	}
}