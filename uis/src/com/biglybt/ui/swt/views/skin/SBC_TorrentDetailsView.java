/*
 * Created on 2 juil. 2003
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
package com.biglybt.ui.swt.views.skin;

import java.util.Map;

import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.*;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateTab;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mdi.*;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectTabFolder;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.PeersView;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;
import com.biglybt.util.DataSourceUtils;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;

/**
 * Torrent download view, consisting of several information tabs
 *
 * @author Olivier
 *
 */
public class SBC_TorrentDetailsView
	extends SkinView
	implements DownloadManagerListener,
	UIPluginViewToolBarListener, SelectedContentListener
{
	private static final String VIEW_ID = UISWTInstance.VIEW_TORRENT_DETAILS;

	private DownloadManager manager;

	private TabbedMdiInterface tabbedMDI;

	private Object dataSource;

	/**
	 *
	 */
	public SBC_TorrentDetailsView() {
	}

	private void dataSourceChanged(Object newDataSource) {
		this.dataSource = newDataSource;

		if (manager != null) {
			manager.removeListener(this);
		}

		manager = DataSourceUtils.getDM(newDataSource);

		if (tabbedMDI != null && newDataSource instanceof Object[]
				&& ((Object[]) newDataSource)[0] instanceof PEPeer) {
			tabbedMDI.showEntryByID(PeersView.MSGID_PREFIX);
		}

		if (manager != null) {
			manager.addListener(this);
		}

		if (tabbedMDI != null) {
			tabbedMDI.setEntriesDataSource(manager);
		}
	}

	private void delete() {
		if (manager != null) {
			manager.removeListener(this);
		}

		SelectedContentManager.removeCurrentlySelectedContentListener(this);
	}

	private void initialize(SWTSkinObjectTabFolder soTabFolder) {

		MyTorrentsView.registerPluginViews();

		if (tabbedMDI == null) {
			tabbedMDI = new TabbedMDI(Download.class, VIEW_ID,
					"detailsview", getMdiEntry(), manager == null ? dataSource : manager);
			tabbedMDI.buildMDI(soTabFolder);
		} else {
			System.out.println("ManagerView::initialize : folder isn't null !!!");
		}

		SelectedContentManager.addCurrentlySelectedContentListener(this);

		tabbedMDI.addListener(new MdiSWTMenuHackListener() {

			@Override
			public void menuWillBeShown(MdiEntry entry, Menu menuTree) {
				menuTree.setData("downloads", new DownloadManager[] {
					manager
				});
				menuTree.setData("is_detailed_view", true);

				MenuFactory.buildTorrentMenu(menuTree);
			}
		});

		if (dataSource instanceof Object[]
				&& ((Object[]) dataSource)[0] instanceof PEPeer) {
			tabbedMDI.showEntryByID(PeersView.MSGID_PREFIX);
		} else {
			/* TabbedMDI now remembers tab order
	  		MdiEntry[] entries = tabbedMDI.getEntries();
	  		if (entries.length > 0) {
	  			tabbedMDI.showEntry(entries[0]);
	  		}
	  		*/
		}
	}

	@Override
	public void currentlySelectedContentChanged(
            ISelectedContent[] currentContent, String viewId) {
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		BaseMdiEntry activeView = getActiveView();
		if (activeView == null) {
			return;
		}
		activeView.refreshToolBarItems(list);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		BaseMdiEntry activeView = getActiveView();
		if (activeView == null) {
			return false;
		}
		return activeView.toolBarItemActivated(item, activationType, datasource);
	}

	@Override
	public void downloadComplete(DownloadManager manager) {
	}

	@Override
	public void completionChanged(DownloadManager manager, boolean bCompleted) {
	}

	@Override
	public void filePriorityChanged(DownloadManager download,
	                                com.biglybt.core.disk.DiskManagerFileInfo file) {
	}

	@Override
	public void stateChanged(DownloadManager manager, int state) {
		if (tabbedMDI == null || tabbedMDI.isDisposed()) {
			return;
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.refreshIconBar();
				}
			}
		});
	}

	@Override
	public void positionChanged(DownloadManager download, int oldPosition,
	                            int newPosition) {
	}

	public DownloadManager getDownload() {
		return manager;
	}

	// @see SkinView#skinObjectInitialShow(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		SWTSkinObject soListArea = getSkinObject("torrentdetails-list-area");
		if (!(soListArea instanceof SWTSkinObjectTabFolder)) {
			return null;
		}

		/*
		MultipleDocumentInterfaceSWT mdi = UIFunctionsManagerSWT.getUIFunctionsSWT().getMDISWT();

		if (mdi != null) {

			mdi_entry = mdi.getEntryFromSkinObject(skinObject);

			if ( mdi_entry == null ){

					// We *really* need to not use 'current' here as it is inaccurate (try opening multiple torrent details view
					// at once to see this)

				Debug.out( "Failed to get MDI entry from skin object, reverting to using 'current'" );

				mdi_entry = mdi.getCurrentEntrySWT();
			}

		}
		*/
		
		initialize((SWTSkinObjectTabFolder) soListArea);
		return null;
	}

	// @see SkinView#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		delete();
		return super.skinObjectDestroyed(skinObject, params);
	}

	// @see SWTSkinObjectAdapter#dataSourceChanged(SWTSkinObject, java.lang.Object)
	@Override
	public Object dataSourceChanged(SWTSkinObject skinObject, Object params) {
		dataSourceChanged(params);
		return null;
	}

	private BaseMdiEntry getActiveView() {
		if (tabbedMDI == null || tabbedMDI.isDisposed()) {
			return null;
		}
		return (BaseMdiEntry) tabbedMDI.getCurrentEntry();
	}

	public static class TorrentDetailMdiEntry
		implements MdiSWTMenuHackListener, MdiCloseListener,
            MdiEntryDatasourceListener, UIUpdatable, ViewTitleInfo, ObfuscateTab
	{
		int lastCompleted = -1;

		protected GlobalManagerAdapter gmListener;

		private BaseMdiEntry entry;

		public static void register(MultipleDocumentInterface mdi) {
			mdi.registerEntry(SideBar.SIDEBAR_SECTION_TORRENT_DETAILS + ".*",
					new MdiEntryCreationListener2() {
						@Override
						public MdiEntry createMDiEntry(MultipleDocumentInterface mdi,
						                               String id, Object datasource, Map<?, ?> params) {
							String hash = DataSourceUtils.getHash(datasource);
							if (hash != null) {
								id = MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS
										+ "_" + hash;

								// If we check if the hash exists in GlobalManager here,
								// the GM may now have finished loading/adding all torrents!
							}
							return new TorrentDetailMdiEntry().createTorrentDetailEntry(mdi,
									id, datasource);
						}
					});
		}

		public MdiEntry createTorrentDetailEntry(MultipleDocumentInterface mdi,
				String id, Object ds) {
			if (ds == null) {
				return null;
			}
			
			entry = (BaseMdiEntry)mdi.getEntry( id );
			
			if ( entry == null ){
			
				entry = (BaseMdiEntry) mdi.createEntryFromSkinRef(SideBar.SIDEBAR_HEADER_TRANSFERS, id,
							"torrentdetails", "", null, ds, true, null);

				entry.addListeners(this);
				entry.setViewTitleInfo(this);
			
				CoreFactory.addCoreRunningListener(
						new CoreRunningListener() {
							@Override
							public void coreRunning(Core core) {
								GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
								gmListener = new GlobalManagerAdapter() {
									@Override
									public void downloadManagerRemoved(DownloadManager dm) {
										Object ds = entry.getDatasourceCore();
										DownloadManager manager = DataSourceUtils.getDM(ds);
										if (dm.equals(manager)) {
											mdi.closeEntry(entry,false);
										}else if ( manager == null ){
											try{
													// if we've been restored after a restart then the ds is base32 hash - here the dm has been removed
													// so getDM(ds) returns null - check hash instead
												
												String ds_hash = DataSourceUtils.getHash( ds );
												
												String dm_hash = Base32.encode( dm.getTorrent().getHash());
												
												if ( ds_hash.equals( dm_hash )){
													
													mdi.closeEntry(entry,false);
												}
											}catch( Throwable e ){
											}											
										}
									}
								};
								gm.addListener(gmListener, false);
							}
						});
	
				UIUpdater updater = UIUpdaterSWT.getInstance();
				if (updater != null) {
					updater.addUpdater(this);
				}
			}
			
			return entry;
		}

		// @see ViewTitleInfo#getTitleInfoProperty(int)
		@Override
		public Object getTitleInfoProperty(int propertyID) {
			Object ds = entry.getDatasourceCore();
			if (propertyID == TITLE_EXPORTABLE_DATASOURCE) {
				return DataSourceUtils.getHash(ds);
			} else if (propertyID == TITLE_IMAGEID) {
				return "image.sidebar.details";
			}

			DownloadManager manager = DataSourceUtils.getDM(ds);
			if (manager == null) {
				return null;
			}

			if (propertyID == TITLE_TEXT) {
				return manager.getDisplayName();
			}

			if (propertyID == TITLE_INDICATOR_TEXT) {
				int completed = manager.getStats().getPercentDoneExcludingDND();
				if (completed != 1000) {
					return (completed / 10) + "%";
				}
			} else if (propertyID == TITLE_INDICATOR_TEXT_TOOLTIP) {
				String s = "";
				int completed = manager.getStats().getPercentDoneExcludingDND();
				if (completed != 1000) {
					s = (completed / 10) + "% Complete\n";
				}
				String eta = DisplayFormatters.formatETA(
						manager.getStats().getSmoothedETA());
				if (eta.length() > 0) {
					s += MessageText.getString("TableColumn.header.eta") + ": " + eta
							+ "\n";
				}

				return manager.getDisplayName() + (s.length() == 0 ? "" : (": " + s));
			}
			return null;
		}

		// @see UIUpdatable#updateUI()
		@Override
		public void updateUI() {
			DownloadManager manager = DataSourceUtils.getDM(entry.getDatasourceCore());
			int completed = manager == null ? -1
					: manager.getStats().getPercentDoneExcludingDND();
			if (lastCompleted != completed) {
				ViewTitleInfoManager.refreshTitleInfo(this);
				if ( entry != null ){
					entry.redraw();
				}
				lastCompleted = completed;
			}
		}

		// @see UIUpdatable#getUpdateUIName()
		@Override
		public String getUpdateUIName() {
			return entry == null ? "DMD" : entry.getViewID();
		}

		// @see MdiCloseListener#mdiEntryClosed(MdiEntry, boolean)
		@Override
		public void mdiEntryClosed(MdiEntry entry, boolean userClosed) {
			UIUpdater updater = UIUpdaterSWT.getInstance();
			if (updater != null) {
				updater.removeUpdater(this);
			}
			try {
				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
				gm.removeListener(gmListener);
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		@Override
		public void mdiEntryDatasourceChanged(final MdiEntry entry) {
			Object newDataSource = ((BaseMdiEntry) entry).getDatasourceCore();
			if (newDataSource instanceof String) {
				final String s = (String) newDataSource;
				if (!CoreFactory.isCoreRunning()) {
					CoreFactory.addCoreRunningListener(
							new CoreRunningListener() {
								@Override
								public void coreRunning(Core core) {
									entry.setDatasource(DataSourceUtils.getDM(s));
								}
							});
				}
			}

			ViewTitleInfoManager.refreshTitleInfo(this);
		}

		@Override
		public void menuWillBeShown(MdiEntry entry, Menu menuTree) {
			// todo: This even work?
			TableView<?> tv = SelectedContentManager.getCurrentlySelectedTableView();
			menuTree.setData("TableView", tv);
			DownloadManager manager = DataSourceUtils.getDM(((BaseMdiEntry) entry).getDatasourceCore());
			if (manager != null) {
				menuTree.setData("downloads", new DownloadManager[] {
					manager
				});
			}
			menuTree.setData("is_detailed_view", Boolean.TRUE);

			MenuFactory.buildTorrentMenu(menuTree);
		}

		// @see com.biglybt.ui.swt.debug.ObfuscateTab#getObfuscatedHeader()
		@Override
		public String getObfuscatedHeader() {
			Object ds = entry.getDatasourceCore();
			DownloadManager manager = DataSourceUtils.getDM(ds);
			if (manager == null) {
				return null;
			}
			int completed = manager.getStats().getCompleted();
			return DisplayFormatters.formatPercentFromThousands(completed) + " : "
					+ manager.toString().replaceFirst("DownloadManagerImpl", "DM");
		}
	}
}
