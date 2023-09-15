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
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.TabbedMDI;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.ViewManagerSWT;

/**
 * aka "Statistics View" that contains {@link ActivityView},
 * {@link TransferStatsView}, {@link CacheView}, {@link DHTView},
 * {@link VivaldiView}
 */
public class StatsView
	implements IViewAlwaysInitialize, UISWTViewCoreEventListener
{
	public static String VIEW_ID = UISWTInstance.VIEW_STATISTICS;

	public static final int EVENT_PERIODIC_UPDATE = 0x100;

	private TabbedMDI tabbedMDI;

	private UpdateThread updateThread;

	private Object dataSource;

	private UISWTView swtView;

	private Composite parent;

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

	private void initialize(Composite composite) {
		parent = composite;

		registerPluginViews();

		tabbedMDI = new TabbedMDI(null, VIEW_ID, VIEW_ID, swtView, dataSource);
		tabbedMDI.setDestroyEntriesOnDeactivate(true);
		tabbedMDI.buildMDI(composite);

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

		updateThread = new UpdateThread();
		updateThread.setDaemon(true);
		updateThread.start();

		dataSourceChanged(dataSource);
	}

	private static void registerPluginViews() {
		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		if (vm.areCoreViewsRegistered(VIEW_ID)) {
			return;
		}

		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				ActivityView.MSGID_PREFIX, null, ActivityView.class));
		
		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				TransferStatsView.MSGID_PREFIX, null, TransferStatsView.class));

		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				CacheView.MSGID_PREFIX, null, CacheView.class));

		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				TrackerActivityView.MSGID_PREFIX, null, TrackerActivityView.class));

		boolean hasV4 = NetworkAdmin.getSingleton().hasDHTIPV4();

		if ( hasV4 ){
			
			vm.registerView(VIEW_ID,
					new UISWTViewBuilderCore(DHTView.MSGID_PREFIX, null,
							DHTView.class).setInitialDatasource(DHTView.DHT_TYPE_MAIN));
	
			vm.registerView(VIEW_ID,
					new UISWTViewBuilderCore(DHTOpsView.MSGID_PREFIX, null,
							DHTOpsView.class).setInitialDatasource(DHTOpsView.DHT_TYPE_MAIN));
	
			vm.registerView(VIEW_ID,
					new UISWTViewBuilderCore(VivaldiView.MSGID_PREFIX, null,
							VivaldiView.class).setInitialDatasource(VivaldiView.DHT_TYPE_MAIN));
		}
		
		if ( NetworkAdmin.getSingleton().hasDHTIPV6()){
			
			vm.registerView(VIEW_ID,
					new UISWTViewBuilderCore(DHTView.MSGID_PREFIX + ".6", null,
							DHTView.class).setInitialDatasource(
									VivaldiView.DHT_TYPE_MAIN_V6));
			
			if ( !hasV4 ){
				
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(DHTOpsView.MSGID_PREFIX + ".6", null,
								DHTOpsView.class).setInitialDatasource(DHTOpsView.DHT_TYPE_MAIN_V6));
			}
			
			vm.registerView(VIEW_ID,
					new UISWTViewBuilderCore(VivaldiView.MSGID_PREFIX + ".6",
							null, VivaldiView.class).setInitialDatasource(
									VivaldiView.DHT_TYPE_MAIN_V6));
		}

		if ( hasV4 ){
			
			if ( Constants.isCVSVersion()){
	
				/* disabled as no use
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(DHTView.MSGID_PREFIX + ".cvs", null,
								DHTView.class).setInitialDatasource(DHTView.DHT_TYPE_CVS));
	
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(VivaldiView.MSGID_PREFIX + ".cvs",
								null, VivaldiView.class).setInitialDatasource(
										VivaldiView.DHT_TYPE_CVS));
				*/
				
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(DHTView.MSGID_PREFIX + ".biglybt",
								null, DHTView.class).setInitialDatasource(
										DHTView.DHT_TYPE_BIGLYBT));
	
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(
								VivaldiView.MSGID_PREFIX + ".biglybt", null,
								VivaldiView.class).setInitialDatasource(
										VivaldiView.DHT_TYPE_BIGLYBT));
			}else{
				
				vm.registerView(VIEW_ID,
						new UISWTViewBuilderCore(DHTView.MSGID_PREFIX + ".biglybt",
								null, DHTView.class).setInitialDatasource(
										DHTView.DHT_TYPE_BIGLYBT));
			}
		}
		
		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				TagStatsView.MSGID_PREFIX, null, TagStatsView.class));

		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				XferStatsView.MSGID_PREFIX, null, XferStatsView.class));

		vm.registerView(VIEW_ID, new UISWTViewBuilderCore(
				CountersView.MSGID_PREFIX, null, CountersView.class));

		vm.setCoreViewsRegistered(VIEW_ID);
	}

	// Copied from ManagerView
	private void refresh() {
		if (tabbedMDI == null || tabbedMDI.isDisposed())
			return;

		MdiEntrySWT entry = tabbedMDI.getCurrentEntry();
		if (entry != null) {
			entry.updateUI( false );
		}
	}

	public static String getFullTitle() {
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
