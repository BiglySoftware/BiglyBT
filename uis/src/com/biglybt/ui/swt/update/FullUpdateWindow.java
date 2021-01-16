/*
 * Created on June 29th, 2009
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

package com.biglybt.ui.swt.update;


import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.biglybt.ui.UIFunctions;


public class FullUpdateWindow
{
	static Shell current_shell = null;

	static BrowserWrapper.BrowserFunction browserFunction;

	public static void
	handleUpdate(
		final String						url,
		final UIFunctions.actionListener	listener )
	{
		try{
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					open( url, listener );
				}
			});

		}catch( Throwable e ){

			Debug.out( e );

			listener.actionComplete( false );
		}
	}

	public static void
	open(
		final String 						url,
		final UIFunctions.actionListener	listener )
	{
		boolean	ok = false;

		final boolean[] listener_informed = { false };

		try{
			if ( current_shell != null && !current_shell.isDisposed()){

				return;
			}

			final Shell parentShell = Utils.findAnyShell();

			final Shell shell = current_shell =
				ShellFactory.createShell(parentShell, SWT.BORDER | SWT.APPLICATION_MODAL | SWT.TITLE | SWT.RESIZE | SWT.DIALOG_TRIM );

			if (shell == null) {
				return;
			}

			shell.setLayout(new FillLayout());

			if (parentShell != null) {
				parentShell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
			}

			shell.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					try{
						if (parentShell != null) {
							parentShell.setCursor(e.display.getSystemCursor(SWT.CURSOR_ARROW));
						}
						if (browserFunction != null && !browserFunction.isDisposed()) {
							browserFunction.dispose();
						}
						current_shell = null;

					}finally{

						if ( !listener_informed[0] ){

							try{
								listener.actionComplete( false );

							}catch( Throwable f ){

								Debug.out( f );
							}
						}
					}
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
					if (shell.isDisposed()) {
						return;
					}
					shell.setText(event.title);
				}
			});

			browser.addOpenWindowListener(new BrowserWrapper.OpenWindowListener() {
				@Override
				public void open(BrowserWrapper.WindowEvent event) {
					final BrowserWrapper subBrowser = Utils.createSafeBrowser(shell,
							SWT.NONE);
					if (subBrowser == null) {
						return;
					}
					subBrowser.addLocationListener(new LocationListener() {
						@Override
						public void changed(LocationEvent arg0) {
						}
						@Override
						public void changing(LocationEvent event) {
							if (event.location == null || !event.location.startsWith("http")) {
								return;
							}
							event.doit = false;
							Utils.launch(event.location);

							Utils.execSWTThreadLater(1000, new AERunnable() {
								@Override
								public void runSupport() {
									subBrowser.dispose();
								}
							});
						}
					});
					subBrowser.setBrowser( event );
				}
			});

			browserFunction = browser.addBrowserFunction(
				"sendVuzeUpdateEvent",
				new BrowserWrapper.BrowserFunction()
				{
					private String last = null;

					@Override
					public Object function(Object[] arguments) {

						if (shell.isDisposed()) {
							return null;
						}

						if (arguments == null) {
							Debug.out("Invalid sendVuzeUpdateEvent null ");
							return null;
						}
						if (arguments.length < 1) {
							Debug.out("Invalid sendVuzeUpdateEvent length " + arguments.length + " not 1");
							return null;
						}
						if (!(arguments[0] instanceof String)) {
							Debug.out("Invalid sendVuzeUpdateEvent "
									+ (arguments[0] == null ? "NULL"
											: arguments.getClass().getSimpleName()) + " not String");
							return null;
						}

						String text = ((String) arguments[0]).toLowerCase();
						if (last  != null && last.equals(text)) {
							return null;
						}
						last = text;
						if ( text.contains("page-loaded")) {

							Utils.centreWindow(shell);
							if (parentShell != null) {
								parentShell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
							}
							shell.open();

						} else if (text.startsWith("set-size")){

							String[] strings = text.split(" ");

							if (strings.length > 2){
								try {

									int w = Integer.parseInt(strings[1]);
									int h = Integer.parseInt(strings[2]);

									Rectangle computeTrim = shell.computeTrim(0, 0, w, h);
									shell.setSize(computeTrim.width, computeTrim.height);

								} catch (Exception ignored) {
								}
							}
						}else if ( text.contains( "decline" ) || text.contains( "close" )){

							Utils.execSWTThreadLater(0, new AERunnable() {
								@Override
								public void runSupport() {
									shell.dispose();
								}
							});

						}else if ( text.contains("accept")){

							Utils.execSWTThreadLater(0, new AERunnable() {
								@Override
								public void runSupport(){

									listener_informed[0] = true;

									try{
										listener.actionComplete( true );

									}catch( Throwable e ){

										Debug.out( e );
									}

									shell.dispose();
								}
							});
						}
						return null;
					}
				});

			browser.addStatusTextListener(new StatusTextListener() {
				@Override
				public void changed(StatusTextEvent event) {
					if (browserFunction != null) {
						browserFunction.function(new Object[]{
								event.text
						});
					}
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

			String final_url = url + ( url.indexOf('?')==-1?"?":"&") +
						"locale=" + MessageText.getCurrentLocale().toString() +
						"&azv=" + Constants.BIGLYBT_VERSION;

			SimpleTimer.addEvent(
				"fullupdate.pageload",
				SystemTime.getOffsetTime(5000),
				new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if ( !shell.isDisposed()){

									shell.open();
								}
							}
						});
					}
				});

			browser.setUrl(final_url);

			if ( browser.isFake()){

				shell.setSize( 400, 500 );

				Utils.centreWindow(shell);

				browser.setUrl( Constants.URL_CLIENT_HOME );

				browser.setText( "Update available, please go to www.biglybt.com to update." );

				shell.open();
			}

			ok = true;

		}finally{

			if ( !ok ){

				try{
					listener.actionComplete( false );

				}catch( Throwable f ){

					Debug.out( f );
				}
			}
		}
	}
}