/*
 * Created on 12 May 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.ui.swt.minibar;

import java.util.List;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.DoubleBufferedLabel;
import com.biglybt.ui.swt.mainwindow.SelectableSpeedMenu;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.views.utils.ManagerUtils;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;

/**
 * @author Allan Crooks
 *
 */
public class AllTransfersBar extends MiniBar {

	private static MiniBarManager manager;
	static {
		manager = new MiniBarManager("AllTransfersBar");
	}

	public static MiniBarManager getManager() {
		return manager;
	}

	public static AllTransfersBar getBarIfOpen(GlobalManager g_manager) {
		return (AllTransfersBar)manager.getMiniBarForObject(g_manager);
	}

	public static void open(final Shell main) {
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				GlobalManager g_manager = core.getGlobalManager();
				AllTransfersBar result = getBarIfOpen(g_manager);
				if (result == null) {
					result = new AllTransfersBar(g_manager, main);
				}
			}
		});
	}

	public static void closeAllTransfersBar() {
		CoreWaiterSWT.waitForCoreRunning(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				GlobalManager g_manager = core.getGlobalManager();
				AllTransfersBar result = getBarIfOpen(g_manager);
				if (result != null) {
					result.close();
				}
			}
		});
	}

	private GlobalManager g_manager;
	private DoubleBufferedLabel down_speed;
	private DoubleBufferedLabel up_speed;
	private DoubleBufferedLabel next_eta;
	private Label				icon_label;

	private AllTransfersBar(GlobalManager gmanager, Shell main) {
		super(manager);
		this.g_manager = gmanager;
		this.construct(main);
	}

	@Override
	public Object getContextObject() {return this.g_manager;}

	@Override
	public void beginConstruction() {
		this.createFixedTextLabel("MinimizedWindow.all_transfers", false, true);
		this.createGap(40);

			// Download speed.

		Label dlab = this.createFixedTextLabel("ConfigView.download.abbreviated", false, false);
		this.down_speed = this.createSpeedLabel();

		final Menu downloadSpeedMenu = new Menu(getShell(),	SWT.POP_UP);

		downloadSpeedMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if ( CoreFactory.isCoreRunning()){
					SelectableSpeedMenu.generateMenuItems(
						downloadSpeedMenu, CoreFactory.getSingleton(),
						g_manager, false);
				}
			}
		});

		dlab.setMenu(downloadSpeedMenu);
		down_speed.setMenu(downloadSpeedMenu);

			// Upload speed.

		Label ulab = this.createFixedTextLabel("ConfigView.upload.abbreviated", false, false);
		this.up_speed = this.createSpeedLabel();

		final Menu uploadSpeedMenu = new Menu(getShell(),	SWT.POP_UP);

		uploadSpeedMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if ( CoreFactory.isCoreRunning()){
					SelectableSpeedMenu.generateMenuItems(
						uploadSpeedMenu, CoreFactory.getSingleton(),
						g_manager, true);
				}
			}
		});

		ulab.setMenu(uploadSpeedMenu);
		up_speed.setMenu(uploadSpeedMenu);

			// next eta

		this.createFixedTextLabel("TableColumn.header.eta_next", true, false);
		this.next_eta = this.createDataLabel(65);

			// options icon area

		if ( COConfigurationManager.getBooleanParameter( "Transfer Bar Show Icon Area" )){

			icon_label = createFixedLabel(16);
		}
	}

	public void
	setIconImage(
		Image		image )
	{
		if ( 	icon_label != null &&
				image != icon_label.getImage()){

			icon_label.setImage( image );
			icon_label.pack();
			icon_label.redraw();
		}
	}

	@Override
	protected void
	doubleClick()
	{
		UIFunctionsSWT functionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (functionsSWT != null) {
			functionsSWT.bringToFront();
		}
	}

	@Override
	public void buildMenu(Menu menu, MenuEvent menuEvent) {

		// Start All
		MenuItem start_all = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(start_all, "MainWindow.menu.transfers.startalltransfers");
		Utils.setMenuItemImage(start_all, "start");
		start_all.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				ManagerUtils.asyncStartAll();
			}
		});
		start_all.setEnabled(true);

		// Stop All
		MenuItem stop_all = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(stop_all, "MainWindow.menu.transfers.stopalltransfers");
		Utils.setMenuItemImage(stop_all, "stop");
		stop_all.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				ManagerUtils.asyncStopAll();
			}
		});
		stop_all.setEnabled(true);

		// Pause All
		MenuItem pause_all = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(pause_all, "MainWindow.menu.transfers.pausetransfers");
		Utils.setMenuItemImage(pause_all, "pause");
		pause_all.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				ManagerUtils.asyncPause();
			}
		});
		pause_all.setEnabled(g_manager.canPauseDownloads());

		// Resume All
		MenuItem resume_all = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(resume_all, "MainWindow.menu.transfers.resumetransfers");
		Utils.setMenuItemImage(resume_all, "resume");
		resume_all.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				ManagerUtils.asyncResume();
			}
		});
		resume_all.setEnabled(g_manager.canResumeDownloads());

		new MenuItem(menu, SWT.SEPARATOR);
		super.buildMenu(menu);
	}

	@Override
	protected void refresh0() {
		GlobalManagerStats stats = g_manager.getStats();
		this.updateSpeedLabel(down_speed, stats.getDataReceiveRate(),stats.getProtocolReceiveRate());
		this.updateSpeedLabel(up_speed, stats.getDataSendRate(),stats.getProtocolSendRate());

		long	min_eta = Long.MAX_VALUE;
		int		num_downloading = 0;

		List<DownloadManager> dms = g_manager.getDownloadManagers();
		for ( DownloadManager dm: dms ){
			if ( dm.getState() == DownloadManager.STATE_DOWNLOADING ){

				num_downloading++;

				long eta = dm.getStats().getSmoothedETA();

				if ( eta < min_eta ){

					min_eta = eta;
				}
			}
		}

		if ( min_eta == Long.MAX_VALUE ){

			min_eta = Constants.CRAPPY_INFINITE_AS_LONG;
		}
		next_eta.setText(num_downloading==0?"":DisplayFormatters.formatETA(min_eta));
	}

	@Override
	public String[] getPluginMenuIdentifiers(Object[] context) {
		if (context == null) {
			return null;
		}
		return new String[] {
			"transfersbar"
		};
	}

	@Override
	protected void storeLastLocation(Point location) {
		COConfigurationManager.setParameter("transferbar.x", location.x);
		COConfigurationManager.setParameter("transferbar.y", location.y);
	}

	@Override
	protected Point getInitialLocation() {
		if (!COConfigurationManager.getBooleanParameter("Remember transfer bar location")) {
			return null;
		}
		if (!COConfigurationManager.hasParameter("transferbar.x", false)) {
			return null;
		}
		int x = COConfigurationManager.getIntParameter("transferbar.x");
		int y = COConfigurationManager.getIntParameter("transferbar.y");
		return new Point(x, y);
	}

}
