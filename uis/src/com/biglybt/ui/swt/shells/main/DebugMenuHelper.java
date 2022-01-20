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

package com.biglybt.ui.swt.shells.main;

import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.views.skin.VuzeMessageBox;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.logging.*;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.debug.ObfuscateShell;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.donations.DonationWindow;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.MessageBoxShell;

import com.biglybt.core.Core;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.skin.VuzeMessageBoxListener;

/**
 * A convenience class for creating the Debug menu
 * <p>
 * This has been extracted out into its own class since it does not really belong to production code
 * @author knguyen
 *
 */
public class DebugMenuHelper
{
	/**
	 * Creates the Debug menu and its children
	 * NOTE: This is a development only menu and so it's not modularized into separate menu items
	 * because this menu is always rendered in its entirety
	 * @param menu
	 * @param mainWindow
	 * @return
	 */
	public static MenuItem createDebugMenuItem(final Menu menuDebug) {
		MenuItem item;

		final UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (null == uiFunctions) {
			throw new IllegalStateException(
					"UIFunctionsManagerSWT.getUIFunctionsSWT() is returning null");
		}

		item = new MenuItem(menuDebug, SWT.PUSH);
		item.setText("Run GC");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				System.gc();
			}
		});

		item = new MenuItem(menuDebug, SWT.PUSH);
		item.setText("&CoreReq");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						new MessageBoxShell(0, "Done", "Core Now Avail").open(null);
					}
				});
			}
		});



		/*
		item = new MenuItem(menuDebug, SWT.CASCADE);
		item.setText("Subscriptions");
		Menu menuSubscriptions = new Menu(menuDebug.getParent(), SWT.DROP_DOWN);
		item.setMenu(menuSubscriptions);

		item = new MenuItem(menuSubscriptions, SWT.NONE);
		item.setText("Create RSS Feed");
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				final Shell shell = new Shell(uiFunctions.getMainShell());
				shell.setLayout(new FormLayout());

				Label label = new Label(shell,SWT.NONE);
				label.setText("RSS Feed URL :");
				final Text urlText = new Text(shell,SWT.BORDER);
				urlText.setText(Utils.getLinkFromClipboard(shell.getDisplay(),false));
				Label separator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
				Button cancel = new Button(shell,SWT.PUSH);
				cancel.setText("Cancel");
				Button ok = new Button(shell,SWT.PUSH);
				ok.setText("Ok");

				FormData data;

				data = new FormData();
				data.left = new FormAttachment(0,5);
				data.right = new FormAttachment(100,-5);
				data.top = new FormAttachment(0,5);
				label.setLayoutData(data);

				data = new FormData();
				data.left = new FormAttachment(0,5);
				data.right = new FormAttachment(100,-5);
				data.top = new FormAttachment(label);
				data.width = 400;
				urlText.setLayoutData(data);

				data = new FormData();
				data.left = new FormAttachment(0,5);
				data.right = new FormAttachment(100,-5);
				data.top = new FormAttachment(urlText);
				separator.setLayoutData(data);

				data = new FormData();
				data.right = new FormAttachment(ok);
				data.width = 100;
				data.top = new FormAttachment(separator);
				cancel.setLayoutData(data);

				data = new FormData();
				data.right = new FormAttachment(100,-5);
				data.width = 100;
				data.top = new FormAttachment(separator);
				ok.setLayoutData(data);

				cancel.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event arg0) {
						shell.dispose();
					}
				});

				ok.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event arg0) {
						String url_str = urlText.getText();
						shell.dispose();

						try{
							URL	url = new URL( url_str );

							SubscriptionManagerFactory.getSingleton().createSingletonRSS( url_str, url, 120, true );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				});

				shell.pack();


				Utils.centerWindowRelativeTo(shell, uiFunctions.getMainShell());

				shell.open();
				shell.setFocus();
				urlText.setFocus();


			}
		});
		 */

		item = new MenuItem(menuDebug, SWT.CASCADE);
		item.setText("DW");
		Menu menuBrowserTB = new Menu(menuDebug.getParent(), SWT.DROP_DOWN);
		item.setMenu(menuBrowserTB);

		item = new MenuItem(menuBrowserTB, SWT.NONE);
		item.setText("popup check");
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean oldDebug = DonationWindow.DEBUG;
				DonationWindow.DEBUG = true;
				DonationWindow.checkForDonationPopup();
				DonationWindow.DEBUG = oldDebug;
			}
		});
		item = new MenuItem(menuBrowserTB, SWT.NONE);
		item.setText("show");
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean oldDebug = DonationWindow.DEBUG;
				DonationWindow.DEBUG = true;
				DonationWindow.open(true, "debug");
				DonationWindow.DEBUG = oldDebug;
			}
		});

		item = new MenuItem(menuDebug, SWT.NONE);
		item.setText("Alerts");
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String text = "This is a  long message with lots of information and "
						+ "stuff you really should read.  Are you still reading? Good, because "
						+ "reading <a href=\"http://moo.com\">stimulates</a> the mind.\n\nYeah Baby.";

				LogAlert logAlert = new LogAlert(true, LogAlert.AT_INFORMATION, "Simple");
				Logger.log(logAlert);
				logAlert = new LogAlert(true, LogAlert.AT_WARNING, text);
				logAlert.details = "Details: \n\n" + text;
				Logger.log(logAlert);
				logAlert = new LogAlert(true, LogAlert.AT_ERROR, "ShortText");
				logAlert.details = "Details";
				Logger.log(logAlert);

				logAlert = new LogAlert(true, LogAlert.AT_WARNING, "Forced Alert");
				logAlert.forceNotify = true;
				logAlert.details = "Details: \n\n" + text;
				Logger.log(logAlert);
			}
		});

		item = new MenuItem(menuDebug, SWT.NONE);
		item.setText("MsgBox");
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				VuzeMessageBox box = new VuzeMessageBox("Title", "Text", new String[] { "Ok", "Cancel" }, 0);
				box.setListener(new VuzeMessageBoxListener() {
					@Override
					public void shellReady(Shell shell, SWTSkinObjectContainer soExtra) {
						SWTSkin skin = soExtra.getSkin();
						skin.createSkinObject("dlg.generic.test", "dlg.generic.test", soExtra);
						skin.layout(soExtra);
						shell.layout(true, true);
					}
				});
				box.open(null);
			}
		});

		item = new MenuItem(menuDebug, SWT.CASCADE);
		item.setText("Size");
		Menu menuSize = new Menu(menuDebug.getParent(), SWT.DROP_DOWN);
		item.setMenu(menuSize);

		int[] sizes = {
			640, 430,
			800, 550,
			1024, 718,
			1280, 700,
			1440, 850,
			1600, 1050,
			1920, 1150
		};
		for (int i = 0; i < sizes.length; i += 2) {
			final int x = sizes[i];
			final int y = sizes[i + 1];
			item = new MenuItem(menuSize, SWT.NONE);
			item.setText("" + x + "," + y);
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell().setSize(x, y);
				}
			});
		}

		item = new MenuItem(menuDebug, SWT.PUSH );
		item.setText("Dump UI");
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Utils.dump( uiFunctions.getMainShell());
			}
		});
		
		item = new MenuItem(menuDebug, SWT.NONE);
		item.setText("Obfuscated Shell Image");
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent ev) {
				
				Display display = Display.getCurrent();
				
				java.util.List<Image> shell_images = UIDebugGenerator.getShellImages();
				
				Image 	biggest_image = null;
				long	biggest_area = 0;
				
				for ( Image image: shell_images ){
					Shell shell2 = new Shell(display);
					Rectangle bounds = image.getBounds();
					long area = bounds.width * bounds.height;
					if ( area > biggest_area ){
						biggest_image = image;
					}
					Point size = shell2.computeSize(bounds.width, bounds.height);
					shell2.setSize(size);
					shell2.setBackgroundImage(image);
					shell2.open();
				}
				
				if ( biggest_image != null ){
					new Clipboard(display).setContents(new Object[] {
							biggest_image.getImageData()
					}, new Transfer[] { ImageTransfer.getInstance() });
				}
			}
		});


		return item;
	}
}
