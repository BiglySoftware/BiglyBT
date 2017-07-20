/*
 * Created on Sep 13, 2004
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
package com.biglybt.ui.swt.views.stats;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.TabbedMdiInterface;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTInstance.UISWTViewEventListenerWrapper;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;

/**
 * aka "Statistics View" that contains {@link ActivityView},
 * {@link TransferStatsView}, {@link CacheView}, {@link DHTView},
 * {@link VivaldiView}
 */
public class StatsView
	implements IViewAlwaysInitialize, UISWTViewCoreEventListenerEx
{
	public static String VIEW_ID = UISWTInstance.VIEW_STATISTICS;

	public static final int EVENT_PERIODIC_UPDATE = 0x100;

	private TabbedMdiInterface tabbedMDI;

	private UpdateThread updateThread;

	private Object dataSource;

	private UISWTView swtView;

	private Composite parent;

	private static boolean registeredCoreSubViews;

	private class UpdateThread
		extends Thread
	{
		boolean bContinue;

		public UpdateThread() {
			super("StatsView Update Thread");
		}

		@Override
		public void run() {
			bContinue = true;

			while (bContinue) {

				MdiEntry[] entries = tabbedMDI.getEntries();
				for (MdiEntry entry : entries) {
					try {
						((MdiEntrySWT) entry).triggerEvent(EVENT_PERIODIC_UPDATE, null);
					} catch (Exception e) {
						Debug.printStackTrace(e);
					}
				}

				try {
					Thread.sleep(1000);
				} catch (Throwable e) {

					Debug.out(e);
					break;
				}
			}
		}

		public void stopIt() {
			bContinue = false;
		}
	}

	public
	StatsView()
	{
	}

	@Override
	public boolean
	isCloneable()
	{
		return( true );
	}

	@Override
	public UISWTViewCoreEventListener
	getClone()
	{
		return( new StatsView());
	}

	private void initialize(Composite composite) {
		parent = composite;

    // Call plugin listeners
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			tabbedMDI = uiFunctions.createTabbedMDI(composite, VIEW_ID);

			CTabFolder folder = tabbedMDI.getTabFolder();
			Label lblClose = new Label(folder, SWT.WRAP);
			lblClose.setText("x");
			lblClose.addListener(SWT.MouseUp, new Listener() {
				@Override
				public void handleEvent(Event event) {
					delete();
				}
			});
			folder.setTopRight(lblClose);


			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			registerPluginViews(pluginUI);

			if ( pluginUI != null ){
				UISWTViewEventListenerWrapper[] pluginViews = pluginUI.getViewListeners(UISWTInstance.VIEW_STATISTICS);
				for (int i = 0; i < pluginViews.length; i++) {
					UISWTViewEventListenerWrapper l = pluginViews[i];
					String name = l.getViewID();

					try {
						MdiEntrySWT entry = (MdiEntrySWT) tabbedMDI.createEntryFromEventListener(
								UISWTInstance.VIEW_STATISTICS, l, name, false, null, null);
						entry.setDestroyOnDeactivate(false);
						if ((dataSource == null && i == 0) || name.equals(dataSource)) {
							tabbedMDI.showEntry(entry);
						}
					} catch (Exception e) {
						// skip
					}
				}
			}
		}

		updateThread = new UpdateThread();
		updateThread.setDaemon(true);
		updateThread.start();

		dataSourceChanged(dataSource);
	}

	private void registerPluginViews(final UISWTInstance pluginUI) {
		if (pluginUI == null || registeredCoreSubViews) {
			return;
		}
		pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
				ActivityView.MSGID_PREFIX, ActivityView.class, null);

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
				TransferStatsView.MSGID_PREFIX, TransferStatsView.class, null);

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS, CacheView.MSGID_PREFIX,
				CacheView.class, null);

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS, DHTView.MSGID_PREFIX,
				DHTView.class, DHTView.DHT_TYPE_MAIN);

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
				DHTOpsView.MSGID_PREFIX, DHTOpsView.class,
				DHTOpsView.DHT_TYPE_MAIN);

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
				VivaldiView.MSGID_PREFIX, VivaldiView.class,
				VivaldiView.DHT_TYPE_MAIN);

		if (NetworkAdmin.getSingleton().hasDHTIPV6()) {
			pluginUI.addView(UISWTInstance.VIEW_STATISTICS, DHTView.MSGID_PREFIX
					+ ".6", DHTView.class, DHTView.DHT_TYPE_MAIN_V6);
			pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
					VivaldiView.MSGID_PREFIX + ".6", VivaldiView.class,
					VivaldiView.DHT_TYPE_MAIN_V6);
		}

		if (Constants.isCVSVersion()) {
			pluginUI.addView(UISWTInstance.VIEW_STATISTICS, DHTView.MSGID_PREFIX
					+ ".cvs", DHTView.class, DHTView.DHT_TYPE_CVS);
			pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
					VivaldiView.MSGID_PREFIX + ".cvs", VivaldiView.class,
					VivaldiView.DHT_TYPE_CVS);
		}

		pluginUI.addView(UISWTInstance.VIEW_STATISTICS,
				TagStatsView.MSGID_PREFIX, TagStatsView.class,
				null );

		registeredCoreSubViews = true;

		final UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		uiManager.addUIListener(new UIManagerListener() {

			@Override
			public void UIAttached(UIInstance instance) {

			}

			@Override
			public void UIDetached(UIInstance instance) {
				if (!(instance instanceof UISWTInstance)) {
					return;
				}

				registeredCoreSubViews = false;

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						ActivityView.MSGID_PREFIX);

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						TransferStatsView.MSGID_PREFIX);

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						CacheView.MSGID_PREFIX);

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						DHTView.MSGID_PREFIX);

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						DHTOpsView.MSGID_PREFIX);

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						VivaldiView.MSGID_PREFIX);

				if (NetworkAdmin.getSingleton().hasDHTIPV6()) {
					pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
							DHTView.MSGID_PREFIX + ".6");
					pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
							VivaldiView.MSGID_PREFIX + ".6");
				}

				if (Constants.isCVSVersion()) {
					pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
							DHTView.MSGID_PREFIX + ".cvs");
					pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
							VivaldiView.MSGID_PREFIX + ".cvs");
				}

				pluginUI.removeViews(UISWTInstance.VIEW_STATISTICS,
						TagStatsView.MSGID_PREFIX);

				uiManager.removeUIListener(this);
			}
		});
	}

	// Copied from ManagerView
	private void refresh() {
		if (tabbedMDI == null || tabbedMDI.isDisposed())
			return;

		MdiEntrySWT entry = tabbedMDI.getCurrentEntrySWT();
		if (entry != null) {
			entry.updateUI();
		}
	}

	private String getFullTitle() {
		return MessageText.getString("Stats.title.full");
	}

	private void delete() {
		if (updateThread != null) {
			updateThread.stopIt();
		}

		Utils.disposeSWTObjects(new Object[] {
			parent
		});
	}

	private void dataSourceChanged(Object newDataSource) {
		dataSource = newDataSource;


		if (tabbedMDI == null) {
			return;
		}

		if (newDataSource instanceof String) {
			tabbedMDI.showEntryByID((String) newDataSource);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.pif.UISWTViewEventListener#eventOccurred(com.biglybt.ui.swt.pif.UISWTViewEvent)
	 */
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setTitle(getFullTitle());
				swtView.setDestroyOnDeactivate(false);
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite) event.getData());
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				dataSourceChanged(event.getData());
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
		}

		return true;
	}

}
