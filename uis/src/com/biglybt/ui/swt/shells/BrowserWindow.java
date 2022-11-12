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

package com.biglybt.ui.swt.shells;

import java.util.Map;

import com.biglybt.ui.swt.browser.BrowserContext;
import com.biglybt.ui.swt.browser.listener.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.BrowserWrapper;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.biglybt.core.messenger.ClientMessageContext;
import com.biglybt.ui.swt.browser.OpenCloseSearchDetailsListener;

/**
 * @author TuxPaper
 * @created Oct 6, 2006
 *
 */
public class BrowserWindow
{

	private Shell shell;

	private ClientMessageContext context;

	private BrowserWrapper browser;

	public BrowserWindow(Shell parent, String url, double wPct, double hPct,
			boolean allowResize, boolean isModal) {
		if (parent == null) {
			init(parent, url, 0, 0, allowResize, isModal);
		} else {
			Rectangle clientArea = parent.getClientArea();
			init(parent, url, (int) (clientArea.width * wPct),
					(int) (clientArea.height * hPct), allowResize, isModal);
		}
	}

	/**
	 * @param url
	 * @param w
	 * @param h
	 * @param allowResize
	 */
	public BrowserWindow(Shell parent, String url, int w, int h,
			boolean allowResize, boolean isModal) {
		init(parent, url, w, h, allowResize, isModal);
	}

	public void init(Shell parent, String url, int w, int h,
			boolean allowResize, boolean isModal) {
		int style = SWT.DIALOG_TRIM;
		if (allowResize) {
			style |= SWT.RESIZE;
		}
		if (isModal) {
			style |= SWT.APPLICATION_MODAL;
		}
		shell = ShellFactory.createShell(parent, style);

		shell.setLayout(new FillLayout());

		Utils.setShellIcon(shell);

		shell.setText(url);

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
    		if (e.detail == SWT.TRAVERSE_ESCAPE) {
    			shell.dispose();
    			e.doit = false;
    		}
			}
		});

		final Listener escListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.keyCode == 27) {
					shell.dispose();
				}
			}
		};
		shell.getDisplay().addFilter(SWT.KeyDown, escListener);
		shell.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {
				event.display.removeFilter(SWT.KeyDown, escListener);
			}
		});


		browser = Utils.createSafeBrowser(shell, SWT.NONE);

		if (browser == null) {
			shell.dispose();
			return;
		}

		context = new BrowserContext("browser-window"
				+ Math.random(), browser, null, true);
		context.addMessageListener(new TorrentListener());
		context.addMessageListener(new VuzeListener());
		context.addMessageListener(new DisplayListener());
		context.addMessageListener(new ConfigListener(browser));
		context.addMessageListener(
				new MetaSearchListener(new OpenCloseSearchDetailsListener() {
					@Override
					public void resizeSecondaryBrowser() {
					}

					@Override
					public void resizeMainBrowser() {
					}

					@Override
					public void openSearchResults(Map params) {
					}

					@Override
					public void closeSearchResults(Map params) {
						if (browser.isDisposed() || browser.getShell().isDisposed()) {
							return;
						}
						shell.dispose();
					}
				} ));


		browser.addProgressListener(new ProgressListener() {
			@Override
			public void completed(ProgressEvent event) {
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				shell.open();
			}

			@Override
			public void changed(ProgressEvent event) {
			}
		});

		browser.addCloseWindowListener(new CloseWindowListener() {
			@Override
			public void close(WindowEvent event) {
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				context.debug("window.close called");
				shell.dispose();
			}
		});

		browser.addTitleListener(new TitleListener() {

			@Override
			public void changed(TitleEvent event) {
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				shell.setText(event.title);
			}

		});

		browser.addStatusTextListener(new StatusTextListener() {
			@Override
			public void changed(StatusTextEvent event) {
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				if(MessageBoxShell.STATUS_TEXT_CLOSE.equals(event.text)) {
					//For some reason disposing the shell / browser in the same Thread makes
					//ieframe.dll crash on windows.
					Utils.execSWTThreadLater(0, new Runnable() {
						@Override
						public void run() {
							if(!browser.isDisposed() && ! shell.isDisposed()) {
								shell.close();
							}
						}
					});
				}
			}

		});

		SimpleTimer.addEvent("showWin", SystemTime.getOffsetTime(3000), new TimerEventPerformer() {

			@Override
			public void perform(TimerEvent event) {
				Utils.execSWTThread(new AERunnable() {

					@Override
					public void runSupport() {
						if (shell != null && !shell.isDisposed()) {
							shell.open();
						}
					}
				});
			}
		});

		if (w > 0 && h > 0) {
			Rectangle computeTrim = shell.computeTrim(0, 0, w, h);
			shell.setSize(computeTrim.width, computeTrim.height);
			//shell.setSize(w, h);
		}

		Utils.centerWindowRelativeTo(shell, parent);
		browser.setUrl(url);
		browser.setData("StartURL", url);
	}

	public void waitUntilClosed() {
		Utils.readAndDispatchLoop( shell );
	}

	public ClientMessageContext
	getContext()
	{
		return( context );
	}

	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display, SWT.DIALOG_TRIM);

		new BrowserWindow(shell, "http://google.com", 500, 200, true, false);

		shell.pack();
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}
