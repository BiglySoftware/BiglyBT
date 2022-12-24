/*
 * Created on Jul 3, 2008
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

package com.biglybt.ui.swt.views.skin;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableSelectionAdapter;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.ISelectedVuzeFileContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.utils.TableColumnCreatorV3;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import com.biglybt.ui.swt.views.MyTorrentsSuperView;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.util.DLReferals;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.PlayUtils;

import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.ui.tables.TableRowRefreshListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;

/**
 * Classic My Torrents view wrapped in a SkinView
 *
 * @author TuxPaper
 * @created Jul 3, 2008
 *
 */
public class SBC_LibraryTableView
	extends SkinView
	implements UIUpdatable, ObfuscateImage, UIPluginViewToolBarListener
{
	private final static String ID = "SBC_LibraryTableView";

	private Composite viewComposite;

	private TableViewSWT<?> tv;

	protected int 		torrentFilterMode = SBC_LibraryView.TORRENTS_ALL;
	protected Object	initialDataSource;
	
	private SWTSkinObject soParent;

	private MyTorrentsView torrentView;

	private UISWTViewEventListener swtViewListener;

	private UISWTViewImpl view;

	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {
		soParent = skinObject.getParent();

  	CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (soParent == null || soParent.isDisposed()) {
							return;
						}
						initShow(core);
					}
				});
			}
  	});

		return null;
	}

	public void initShow(Core core) {
		Object tfm = soParent.getControl().getData("TorrentFilterMode");
		if (tfm instanceof Long) {
			torrentFilterMode = (int) ((Long) tfm).longValue();
		}

		initialDataSource = soParent.getControl().getData("DataSource");

		boolean useBigTable = useBigTable();

		SWTSkinObjectTextbox soFilter = (SWTSkinObjectTextbox) skin.getSkinObject(
				"library-filter", soParent.getParent());
		BubbleTextBox txtFilter = soFilter == null ? null : soFilter.getBubbleTextBox();

		if (useBigTable) {
			if (torrentFilterMode == SBC_LibraryView.TORRENTS_COMPLETE
					|| torrentFilterMode == SBC_LibraryView.TORRENTS_INCOMPLETE
					|| torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED) {

				swtViewListener = torrentView = new MyTorrentsView_Big(core, torrentFilterMode, initialDataSource, getColumnsSupport(), txtFilter);

			} else {
				swtViewListener = torrentView = new MyTorrentsView_Big(core, torrentFilterMode, initialDataSource, getColumnsSupport(), txtFilter);
			}

		} else {
			String tableID = SB_Transfers.getTableIdFromFilterMode(	torrentFilterMode, false, initialDataSource );
			if (torrentFilterMode == SBC_LibraryView.TORRENTS_COMPLETE) {
				swtViewListener = torrentView = new MyTorrentsView(core, tableID, true, getColumnsSupport(), txtFilter, true);

			} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_INCOMPLETE) {
				swtViewListener = torrentView = new MyTorrentsView(core, tableID, false, getColumnsSupport(), txtFilter, true);

			} else if (torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED) {
				swtViewListener = torrentView = new MyTorrentsView(core, tableID, true, getColumnsSupport(), txtFilter,	 true){
					@Override
					public boolean isOurDownloadManager(DownloadManager dm) {
						if (PlatformTorrentUtils.getHasBeenOpened(dm)) {
							return false;
						}
						return super.isOurDownloadManager(dm);
					}
				};
			} else {
				if ( Utils.getBaseViewID( tableID ).equals( TableManager.TABLE_MYTORRENTS_ALL_SMALL )){
					
					swtViewListener = torrentView = new MyTorrentsView_Small( core, torrentFilterMode, initialDataSource, getColumnsSupport(), txtFilter );
					
				}else{
					swtViewListener = new MyTorrentsSuperView(txtFilter, initialDataSource ) {
						@Override
						public void initializeDone() {
							MyTorrentsView seedingview = getSeedingview();
							if (seedingview != null) {
								seedingview.overrideDefaultSelected(new TableSelectionAdapter() {
									@Override
									public void defaultSelected(TableRowCore[] rows, int stateMask, int origin ) {
										doDefaultClick(rows, stateMask, false, origin );
									}
								});
								MyTorrentsView torrentview = getTorrentview();
								if (torrentview != null) {
									torrentview.overrideDefaultSelected(new TableSelectionAdapter() {
										@Override
										public void defaultSelected(TableRowCore[] rows, int stateMask, int origin ) {
											doDefaultClick(rows, stateMask, false, origin );
										}
									});
								}
							}
						}
					};
				}
			}

			if (torrentView != null) {
				torrentView.overrideDefaultSelected(new TableSelectionAdapter() {
					@Override
					public void defaultSelected(TableRowCore[] rows, int stateMask, int origin) {
						doDefaultClick(rows, stateMask, false, origin );
					}
				});
			}
		}

		if (torrentView != null) {
			tv = torrentView.getTableView();
			if (torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED) {
				torrentView.setRebuildListOnFocusGain(true);
			}
		}

		try {
			UISWTViewBuilderCore builder = new UISWTViewBuilderCore(
					ID + torrentFilterMode, null, swtViewListener).setInitialDatasource(
							initialDataSource);
			view = new UISWTViewImpl(builder, true);
			view.setDestroyOnDeactivate(false);
		} catch (Exception e) {
			Debug.out(e);
		}

		SWTSkinObjectContainer soContents = new SWTSkinObjectContainer(skin,
				skin.getSkinProperties(), getUpdateUIName(), "", soMain);

		skin.layout();

		viewComposite = soContents.getComposite();
		viewComposite.setLayoutData(Utils.getFilledFormData());
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
		viewComposite.setLayout(gridLayout);

		view.initialize(viewComposite);


		if (torrentFilterMode == SBC_LibraryView.TORRENTS_ALL
				&& tv != null) {
			tv.addRefreshListener(new TableRowRefreshListener() {
				@Override
				public void rowRefresh(TableRow row) {
					TableRowSWT rowCore = (TableRowSWT)row;
					Object ds = rowCore.getDataSource(true);
					if (!(ds instanceof DownloadManager)) {
						return;
					}
					DownloadManager dm = (DownloadManager) ds;
					boolean changed = false;
					boolean assumedComplete = dm.getAssumedComplete();
					if (!assumedComplete) {
						changed |= rowCore.setAlpha(160);
					} else if (!PlatformTorrentUtils.getHasBeenOpened(dm)) {
						changed |= rowCore.setAlpha(255);
					} else {
						changed |= rowCore.setAlpha(255);
					}
				}
			});
		}

		viewComposite.getParent().layout(true);
	}

	public static void
	doDefaultClick(
		final TableRowCore[] 	rows,
		final int 				stateMask,
		final boolean 			neverPlay,
		int						origin )
	{
		if ( rows == null || rows.length != 1 ){
			return;
		}

		final Object ds = rows[0].getDataSource(true);

		boolean webInBrowser = COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowser" );

		if ( webInBrowser ){

			DiskManagerFileInfo fileInfo = DataSourceUtils.getFileInfo(ds);

			if ( fileInfo != null ){

				if ( ManagerUtils.browseWebsite( fileInfo )){

					return;
				}
			}else{

				DownloadManager dm = DataSourceUtils.getDM( ds);

				if ( dm != null ){

					if ( ManagerUtils.browseWebsite( dm )){

						return;
					}
				}
			}
		}

		String mode = COConfigurationManager.getStringParameter("list.dm.dblclick");

		if ( origin == 1 ){
			String enter_mode = COConfigurationManager.getStringParameter("list.dm.enteraction");
			if ( !enter_mode.equals( "-1" )){
				mode = enter_mode;
			}
		}

		if (mode.equals("1")) {

				// show detailed view

			if ( UIFunctionsManager.getUIFunctions().getMDI().showEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, ds)){

				return;
			}
		}else if (mode.equals("2")) {

				// Show in explorer

			boolean openMode = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");
			DiskManagerFileInfo file = DataSourceUtils.getFileInfo(ds);
			if (file != null) {
				ManagerUtils.open(file, openMode);
				return;
			}
			DownloadManager dm = DataSourceUtils.getDM(ds);
			if (dm != null) {
				ManagerUtils.open(dm, openMode);
				return;
			}
		}else if (mode.equals("3") || mode.equals("4") || mode.equals( "7" )){

			// Launch
			DiskManagerFileInfo file = DataSourceUtils.getFileInfo(ds);
			if (file != null) {
				if (	mode.equals("4") &&
						file.getDownloaded() == file.getLength() &&
						Utils.isQuickViewSupported( file )){

					Utils.setQuickViewActive( file, true );
				}else{
					TorrentUtil.runDataSources(new Object[]{ file });
				}
				return;
			}
			DownloadManager dm = DataSourceUtils.getDM(ds);
			if (dm != null) {
				if ( mode.equals( "7" )){
					DiskManagerFileInfo best = PlayUtils.getBestPlayableFile( dm );
					
					if ( best != null ){
						
						TorrentUtil.runDataSources(new Object[]{ best });
						
						return;
					}
				}
					
				TorrentUtil.runDataSources(new Object[]{ dm });
				
				return;
			}
		}else if (mode.equals("5")) {
			DiskManagerFileInfo fileInfo = DataSourceUtils.getFileInfo(ds);
			if ( fileInfo != null ){
				ManagerUtils.browse( fileInfo );
				return;
			}
			DownloadManager dm = DataSourceUtils.getDM(ds);
			if (dm != null) {
				ManagerUtils.browse( dm );
				return;
			}
		}else if (mode.equals("6")) {

				// pop out detailed view
	
			if ( UIFunctionsManager.getUIFunctions().getMDI().popoutEntryByID( 
					MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS, 
					ds, PopOutManager.OPT_MAP_ON_TOP)){
	
				return;
			}
		}

		if (neverPlay) {
			return;
		}

			// fallback

		if (PlayUtils.canPlayDS(ds, -1,true) || (stateMask & SWT.CONTROL) != 0) {
			TorrentListViewsUtils.playOrStreamDataSource(ds,
					DLReferals.DL_REFERAL_DBLCLICK, false, true );
			return;
		}

		if (PlayUtils.canStreamDS(ds, -1,true)) {
			TorrentListViewsUtils.playOrStreamDataSource(ds,
					DLReferals.DL_REFERAL_DBLCLICK, true, false );
			return;
		}

		DownloadManager dm = DataSourceUtils.getDM(ds);
		DiskManagerFileInfo file = DataSourceUtils.getFileInfo(ds);
		TOTorrent torrent = DataSourceUtils.getTorrent(ds);
		if (torrent == null && file != null) {
			DownloadManager dmFile = file.getDownloadManager();
			if (dmFile != null) {
				torrent = dmFile.getTorrent();
			}
		}
		if (file != null && file.getDownloaded() == file.getLength()) {
			TorrentUtil.runDataSources(new Object[] { file });
		} else if (dm != null) {
			TorrentUtil.runDataSources(new Object[] { dm });
		}
	}

	// @see com.biglybt.ui.swt.utils.UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return ID;
	}

	// @see com.biglybt.ui.swt.utils.UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (viewComposite == null || viewComposite.isDisposed()
				|| !viewComposite.isVisible() || view == null) {
			return;
		}
		view.triggerEvent(UISWTViewEvent.TYPE_REFRESH, null);
	}

	// @see SkinView#skinObjectShown(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		super.skinObjectShown(skinObject, params);

		if (view != null) {
			view.triggerEvent(UISWTViewEvent.TYPE_FOCUSGAINED, null);
		}

		Utils.execSWTThreadLater(0, new AERunnable() {

			@Override
			public void runSupport() {
				updateUI();
			}
		});

		return null;
	}

	// @see SkinView#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		if (view != null) {
			view.triggerEvent(UISWTViewEvent.TYPE_FOCUSLOST, null);
		}

		return super.skinObjectHidden(skinObject, params);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		if (!isVisible()) {
			return;
		}
		if (view != null) {
			view.refreshToolBarItems(list);
		}
		if (tv == null) {
			return;
		}
		ISelectedContent[] currentContent = SelectedContentManager.getCurrentlySelectedContent();
		boolean has1Selection = currentContent.length == 1;
		list.put(
				"play",
				has1Selection
						&& (!(currentContent[0] instanceof ISelectedVuzeFileContent))
						&& PlayUtils.canPlayDS(currentContent[0],
								currentContent[0].getFileIndex(),false)
						? UIToolBarItem.STATE_ENABLED : 0);
		list.put(
				"stream",
				has1Selection
						&& (!(currentContent[0] instanceof ISelectedVuzeFileContent))
						&& PlayUtils.canStreamDS(currentContent[0],
								currentContent[0].getFileIndex(),false)
						? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType, Object datasource) {
		// currently stream and play are handled by ToolbarView..
		if (isVisible() && view != null) {
			return view.toolBarItemActivated(item, activationType, datasource);
		}
		return false;
	}

	/**
	 * Return either MODE_SMALLTABLE or MODE_BIGTABLE
	 * Subclasses may override
	 * @return
	 */
	protected int getTableMode() {
		return SBC_LibraryView.MODE_SMALLTABLE;
	}

	/**
	 * Returns whether the big version of the tables should be used
	 * Subclasses may override
	 * @return
	 */
	protected boolean useBigTable() {
		return false;
	}

	private TableColumnCore[]
	getColumnsSupport()
	{
		TableColumnCore[] columns = getColumns();

		if ( columns != null ){
			
			TableColumnManager tcManager = TableColumnManager.getInstance();
			
			tcManager.addColumns(columns);
		}

		return( columns );
	}
	/**
	 * Returns the appropriate set of columns for the completed or incomplete torrents views
	 * Subclasses may override to return different sets of columns
	 * @return
	 */
	protected TableColumnCore[] getColumns() {
		String tableID = SB_Transfers.getTableIdFromFilterMode(torrentFilterMode, false, initialDataSource);
		
		if ( tableID != null ){
			
			TableColumnCore[] columns = null;
			
			if (torrentFilterMode == SBC_LibraryView.TORRENTS_COMPLETE ){
				
				columns = TableColumnCreator.createCompleteDM( tableID );
				
			} else if ( torrentFilterMode == SBC_LibraryView.TORRENTS_INCOMPLETE ){
				
				return TableColumnCreator.createIncompleteDM( tableID );
				
			} else if ( torrentFilterMode == SBC_LibraryView.TORRENTS_UNOPENED ){
				
				return TableColumnCreatorV3.createUnopenedDM( tableID, false);
				
			} else if ( torrentFilterMode == SBC_LibraryView.TORRENTS_ALL ){
				
				return TableColumnCreator.createAllDM( tableID );
			}
			
			return( columns );
			
		}else{
			
			return( null );
		}
	}

	// @see SWTSkinObjectAdapter#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		if (view != null) {
  		view.triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
		}
		return super.skinObjectDestroyed(skinObject, params);
	}

	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image, org.eclipse.swt.graphics.Point)
	@Override
	public Image obfuscatedImage(Image image) {
		if (view instanceof ObfuscateImage) {
			ObfuscateImage oi = (ObfuscateImage) view;
			return oi.obfuscatedImage(image);
		}
		return image;
	}
}
