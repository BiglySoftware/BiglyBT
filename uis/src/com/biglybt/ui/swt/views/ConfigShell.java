/*
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
package com.biglybt.ui.swt.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;
import com.biglybt.ui.swt.shells.PopOutManager;

/**
 * A shell containing the <code>ConfigView</code>
 * This is used to pop-up the configs in a Shell as opposed to hosting it in the application
 * This class is used to ensure that only one shell is opened at any time.
 * @author khai
 *
 */
public class ConfigShell
{

	private static ConfigShell instance;

	private Shell shell;

	private UISWTViewImpl swtView;

	public static ConfigShell getInstance() {
		if (null == instance) {
			instance = new ConfigShell();
		}
		return instance;
	}

	private ConfigShell() {
	}

	/**
	 * Opens the <code>ConfigView</code> inside a pop-up <code>Shell</code>.
	 * If the Shell is opened already then just force it active
	 * @param width
	 * @param height
	 */
	public void open(final String section) {
		Utils.execSWTThread(new AERunnable() {

			@Override
			public void runSupport() {
				swt_open(section);
			}
		});
	}

	public void swt_open(String section) {
		if (null != shell && !shell.isDisposed()) {
			if (swtView != null) {
				swtView.setDatasource(section);
			}
			if (shell.getMinimized()) {
				shell.setMinimized(false);
			}
			shell.forceActive();
			shell.forceFocus();
		} else {
			shell = ShellFactory.createMainShell(SWT.SHELL_TRIM & ~SWT.MIN);
			shell.setLayout(new GridLayout());
			shell.setText(MessageText.getString("ConfigView.title.full"));
			Utils.setShellIcon(shell);
			try {
				UISWTViewBuilderCore builder = new UISWTViewBuilderCore(
						MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, null,
						ConfigView.class).setInitialDatasource(section);
				swtView = new UISWTViewImpl(builder, true);
				swtView.setDestroyOnDeactivate(false);
				swtView.initialize(shell);
			} catch (Exception e1) {
				Debug.out(e1);
			}

			/*
			 * Set default size and centers the shell if it's configuration does not exist yet
			 */
			if (null == COConfigurationManager.getStringParameter(
					"options.rectangle", null)) {
				Rectangle shellBounds = shell.getMonitor().getBounds();
				Point size = new Point(shellBounds.width * 10 / 11,
						shellBounds.height * 10 / 11);
				if (size.x > 1400) {
					size.x = 1400;
				}
				if (size.y > 700) {
					size.y = 700;
				}
				shell.setSize(size);
				Utils.centerWindowRelativeTo(shell, Utils.findAnyShell(true));
			}

			Utils.linkShellMetricsToConfig(shell, "options");

			shell.addTraverseListener(e -> {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			});

			shell.addDisposeListener(arg0 -> close());

			PopOutManager.registerSideBarSection( shell, MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG );
			
			shell.open();
		}
	}

	private void
	close()
	{
		// if (null != shell && false == shell.isDisposed()) {
		//	shell.close();
		// }
			// clear these down as view now dead

		if (swtView != null) {
  		swtView.triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
  		swtView = null;
		}

		shell		= null;
	}
}
