/*
 * Created on Jul 2, 2008
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

import java.util.*;

import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.category.Category;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagGroup;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.InitializerListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.Initializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.ViewUtils;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;

/**
 * @author TuxPaper
 * @created Jul 2, 2008
 *
 */
public class SBC_LibraryView
	extends SkinView implements UIPluginViewToolBarListener
{
	private final static String ID = "library-list";

	public final static int MODE_BIGTABLE = 0;

	public final static int MODE_SMALLTABLE = 1;

	public static final int TORRENTS_ALL = 0;

	public static final int TORRENTS_COMPLETE = 1;

	public static final int TORRENTS_INCOMPLETE = 2;

	public static final int TORRENTS_UNOPENED = 3;

	private final static String[] modeViewIDs = {
		SkinConstants.VIEWID_SIDEBAR_LIBRARY_BIG,
		SkinConstants.VIEWID_SIDEBAR_LIBRARY_SMALL
	};

	private final static String[] modeIDs = {
		"library.table.big",
		"library.table.small"
	};

	private static volatile int					selection_count;
	private static volatile long				selection_size;
	private static volatile long				selection_done;
	private static volatile DownloadManager[]	selection_dms = {};


	private SelectedContentListener selectedContentListener;

	private int viewMode = -1;

	private SWTSkinButtonUtility btnSmallTable;

	private SWTSkinButtonUtility btnBigTable;

	private SWTSkinObject soListArea;

	private int torrentFilterMode = TORRENTS_ALL;

	private String torrentFilter;

	private SWTSkinObject soWait;

	private SWTSkinObject soWaitProgress;

	private SWTSkinObjectText soWaitTask;

	private int waitProgress = 0;

	private SWTSkinObjectText soLibraryInfo;

	private Object datasource;

	public void setViewMode(int viewMode, boolean save) {
		if (viewMode >= modeViewIDs.length || viewMode < 0
				|| viewMode == this.viewMode) {
			return;
		}

		if ( !COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" )){

			viewMode = MODE_SMALLTABLE;
		}

		int oldViewMode = this.viewMode;

		this.viewMode = viewMode;

		if (oldViewMode >= 0 && oldViewMode < modeViewIDs.length) {
			SWTSkinObject soOldViewArea = getSkinObject(modeViewIDs[oldViewMode]);
			//SWTSkinObject soOldViewArea = skin.getSkinObjectByID(modeIDs[oldViewMode]);
			if (soOldViewArea != null) {
				soOldViewArea.setVisible(false);
			}
		}

		SelectedContentManager.clearCurrentlySelectedContent();

		SWTSkinObject soViewArea = getSkinObject(modeViewIDs[viewMode]);
		if (soViewArea == null) {
			soViewArea = skin.createSkinObject(modeIDs[viewMode] + torrentFilterMode,
					modeIDs[viewMode], soListArea);
			soViewArea.getControl().setData( "SBC_LibraryView:ViewMode", viewMode );
			skin.layout();
			soViewArea.setVisible(true);
			soViewArea.getControl().setLayoutData(Utils.getFilledFormData());
		} else {
			soViewArea.setVisible(true);
		}

		if (save) {
			COConfigurationManager.setParameter(torrentFilter + ".viewmode", viewMode);
		}

		SB_Transfers sb_t = MainMDISetup.getSb_transfers();
		
		if ( sb_t != null ) {
			
			sb_t.triggerCountRefreshListeners();
		}
	}


	// @see SkinView#showSupport(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(final SWTSkinObject skinObject, Object params) {
		selectedContentListener = new SelectedContentListener() {
			@Override
			public void
			currentlySelectedContentChanged(
					ISelectedContent[] currentContent,
					String viewId) {
				selection_count = currentContent.length;

				long total_size = 0;
				long total_done = 0;

				List<DownloadManager> dms = new ArrayList<>(currentContent.length);

					// doesn't make sense to double count a download's files if it also has its own selection
				
				Set<DownloadManager> dms_only = new IdentityHashSet<>();
				
				for (ISelectedContent sc : currentContent) {

					DownloadManager dm = sc.getDownloadManager();

					if ( dm != null ){

						int file_index = sc.getFileIndex();

						if (file_index == -1) {
							
							dms_only.add( dm );
						}
					}
				}
						
				for (ISelectedContent sc : currentContent) {

					DownloadManager dm = sc.getDownloadManager();

					if (dm != null) {

						dms.add(dm);

						int file_index = sc.getFileIndex();

						if (file_index == -1) {
								
							DiskManagerFileInfo[] file_infos = dm.getDiskManagerFileInfoSet().getFiles();

							for (DiskManagerFileInfo file_info : file_infos) {

								if (!file_info.isSkipped()) {

									total_size += file_info.getLength();
									total_done += file_info.getDownloaded();
								}
							}
						} else {

							if ( !dms_only.contains( dm )){
								
								DiskManagerFileInfo file_info = dm.getDiskManagerFileInfoSet().getFiles()[file_index];
	
								if (!file_info.isSkipped()) {
	
									total_size += file_info.getLength();
									total_done += file_info.getDownloaded();
								}
							}
						}
					}
				}

				selection_size = total_size;
				selection_done = total_done;

				selection_dms = dms.toArray(new DownloadManager[dms.size()]);

				SB_Transfers transfers = MainMDISetup.getSb_transfers();
				if (transfers != null) {
					transfers.triggerCountRefreshListeners();
				}
			}
		};
		SelectedContentManager.addCurrentlySelectedContentListener(
				selectedContentListener);

		soWait = null;
		try {
			soWait = getSkinObject("library-wait");
			soWaitProgress = getSkinObject("library-wait-progress");
			soWaitTask = (SWTSkinObjectText) getSkinObject("library-wait-task");
			if (soWaitProgress != null) {
				soWaitProgress.getControl().addPaintListener(new PaintListener() {
					@Override
					public void paintControl(PaintEvent e) {
						assert e != null;
						Control c = (Control) e.widget;
						Point size = c.getSize();
						e.gc.setBackground(ColorCache.getColor(e.display, "#23a7df"));
						int breakX = size.x * waitProgress / 100;
						e.gc.fillRectangle(0, 0, breakX, size.y);
						e.gc.setBackground(ColorCache.getColor(e.display, "#cccccc"));
						e.gc.fillRectangle(breakX, 0, size.x - breakX, size.y);
					}
				});
			}

			soLibraryInfo = (SWTSkinObjectText) getSkinObject("library-info");

			if (soLibraryInfo != null) {

				MainMDISetup.getSb_transfers().addCountRefreshListener(
					new SB_Transfers.countRefreshListener()
					{
						final Map<Composite,ExtraInfoProvider>	extra_info_map = new HashMap<>();

						{
							soLibraryInfo.getControl().getParent().setData( "ViewUtils:ViewTitleExtraInfo",
									new ViewUtils.ViewTitleExtraInfo()
									{
										@Override
										public void 
										setCountProvider(
											Composite 		reporter, 
											CountProvider 	cp)
										{
											ExtraInfoProvider	provider = getProvider( reporter );

											if ( provider == null ){

												return;
											}
											
											provider.countProvider = cp;
										}
										
										@Override
										public void
										searchUpdate(
											Composite	reporter,
											int			count,
											int			active )
										{
											ExtraInfoProvider	provider = getProvider( reporter );

											if ( provider == null ){

												return;
											}

											if ( provider.value != count || provider.active != active){

												provider.value 	= count;
												provider.active	= active;
												
												if ( viewMode == provider.view_mode && provider.search_active ){

													SB_Transfers xfers = MainMDISetup.getSb_transfers();
													
													if ( xfers != null ){
														
														xfers.triggerCountRefreshListeners();
													}
												}
											}
										}

										@Override
										public void
										setSearchActive(
											Composite	reporter,
											boolean		active )
										{
											ExtraInfoProvider	provider = getProvider( reporter );

											if ( provider == null ){

												return;
											}

											if ( provider.search_active != active ){

												provider.search_active = active;

												if ( viewMode == provider.view_mode ){

													MainMDISetup.getSb_transfers().triggerCountRefreshListeners();
												}
											}
										}

										private ExtraInfoProvider
										getProvider(
											Composite	reporter )
										{
											synchronized( extra_info_map ){

												ExtraInfoProvider provider = extra_info_map.get( reporter );

												if ( provider != null ){

													return( provider );
												}

												Composite temp = reporter;

												while( temp != null ){

													Integer vm = (Integer)temp.getData( "SBC_LibraryView:ViewMode" );

													if ( vm != null ){

														provider = new ExtraInfoProvider( vm );

														extra_info_map.put( reporter, provider );

														return( provider );
													}

													temp = temp.getParent();
												}

												Debug.out( "No view mode found for " + reporter );

												return( null );
											}
										}
									});
						}

						// @see SBC_LibraryView.countRefreshListener#countRefreshed(SBC_LibraryView.stats, SBC_LibraryView.stats)
						@Override
						public void
						countRefreshed(
								SB_Transfers.stats statsWithLowNoise,
								SB_Transfers.stats statsNoLowNoise)
						{
							SB_Transfers.stats stats = viewMode == MODE_SMALLTABLE? statsWithLowNoise : statsNoLowNoise;

							String s;
														
							List<ViewUtils.ViewTitleExtraInfo.CountProvider> count_provs = new ArrayList<>();
							
							int		filter_total 	= 0;
							int		filter_active	= 0;

							boolean	filter_enabled 	= false;

							synchronized( extra_info_map ){

								for ( ExtraInfoProvider provider: extra_info_map.values()){

									if ( viewMode == provider.view_mode ){

										if ( provider.search_active ){

											filter_enabled = true;
											filter_total	+= provider.value;
											filter_active	+= provider.active;
										}
										
										ViewUtils.ViewTitleExtraInfo.CountProvider cp = provider.countProvider;
										
										if ( cp != null ){
											
											count_provs.add( cp );
										}
									}
								}
							}
							
							int	extra_total 	= 0;
							int extra_active	= 0;
							int extra_queued	= 0;

							for ( ViewUtils.ViewTitleExtraInfo.CountProvider cp: count_provs ){
								
								int[]	counts = cp.getCounts();
								
								extra_total += counts[0];
								extra_active += counts[1];
								extra_queued += counts[2];
							}
							
							String extra_search = null;

							if ( filter_enabled ){

								extra_search =
									MessageText.getString(
											"filter.header.matches2",
											new String[]{ String.valueOf( filter_total ), String.valueOf( filter_active )});
								extra_search = extra_search.toLowerCase(Locale.US);
							}
							
								// seeding and downloading Tag views were changed to filter appropriately
								// but that broke the header display - fixed by forcing through the 'TORRENTS_ALL'
								// leg for Tag based views

							if ( torrentFilterMode == TORRENTS_ALL || (datasource instanceof Tag)){
								
								boolean addExtra = false;
								
								if (datasource instanceof Category) {
									Category cat = (Category) datasource;

									String id = "library.category.header";

									s = MessageText.getString(id,
											new String[] {
											(cat.getType() != Category.TYPE_USER)
													? MessageText.getString(cat.getName())
													: cat.getName()
									});

									addExtra = true;
									
								}else if (datasource instanceof Tag) {

									Tag tag = (Tag) datasource;

									String id = "library.tag.header";

									s = MessageText.getString(id,
											new String[] {
												tag.getTagName( true ) });

									String desc = tag.getDescription();

									if ( desc != null ){

										s += " - " + desc;
									}

									addExtra = true;
									
								}else if (datasource instanceof TagGroup ) {
									
									String id = "library.taggroup.header";

									TagGroup tg = (TagGroup)datasource;
									
									s = MessageText.getString(id,
											new String[] {
												tg.getName(),
												String.valueOf( tg.getTags().size())});
									
									addExtra= true;
									
								} else {
									String id = "library.all.header";
									if (stats.numComplete + stats.numIncomplete != 1) {
										id += ".p";
									}
									s = MessageText.getString(id,
											new String[] {
											String.valueOf(stats.numComplete + stats.numIncomplete),
											String.valueOf(stats.numSeeding + stats.numDownloading),
									});
									s = s.toLowerCase(Locale.US);

									if ( stats.numQueued > 0 ){

										s += ", " +
										MessageText.getString(
												"label.num_queued", new String[]{ String.valueOf( stats.numQueued )});
									}
								}
								
								if ( addExtra ){
									String id = "library.all.header";
									if ( extra_total != 1 ) {
										id += ".p";
									}
									s += " - " + 
											MessageText.getString(id,
											new String[] {
											String.valueOf(extra_total),
											String.valueOf(extra_active),
									});
									s = s.toLowerCase(Locale.US);
									
									if ( extra_queued > 0 ){

										s += ", " +
										MessageText.getString(
												"label.num_queued", new String[]{ String.valueOf( extra_queued )});
									}
								}

							}else if (torrentFilterMode == TORRENTS_INCOMPLETE) {
								String id = "library.incomplete.header";
								if (stats.numDownloading != 1) {
									id += ".p";
								}
								int numWaiting = Math.max( stats.numIncomplete - stats.numDownloading, 0 );
								s = MessageText.getString(id,
										new String[] {
										String.valueOf(stats.numDownloading),
										String.valueOf(numWaiting),
								});

							} else if ( torrentFilterMode == TORRENTS_UNOPENED ||  torrentFilterMode == TORRENTS_COMPLETE ) {
									// complete filtering currently uses same display text as unopened
								String id = "library.unopened.header";
								if (stats.numUnOpened != 1) {
									id += ".p";
								}
								s = MessageText.getString(id,
										new String[] {
										String.valueOf(stats.numUnOpened),
								});
							}else{

								s = "";
							}

							if ( extra_search != null ){

								s += " " + extra_search;
							}
							
							SB_Transfers transfers = MainMDISetup.getSb_transfers();
							
							if ( selection_count > 0 ){	// used to be 1 but why not

								s += ", " +
										MessageText.getString(
										"label.num_selected", new String[]{ String.valueOf( selection_count )});

								String	size_str = null;
								String	rate_str = null;

								if ( selection_size > 0 ){

									if ( selection_size == selection_done ){

										size_str = DisplayFormatters.formatByteCountToKiBEtc( selection_size );

									}else{

										size_str = DisplayFormatters.formatByteCountToKiBEtc( selection_done ) + "/" + DisplayFormatters.formatByteCountToKiBEtc( selection_size );

									}
								}

								DownloadManager[] dms = selection_dms;

								if ( transfers.header_show_rates && dms.length > 0 ){	// used to be 1 but why not

									long	total_data_up 		= 0;
									long	total_prot_up 		= 0;
									long	total_data_down		= 0;
									long	total_prot_down		= 0;

									for ( DownloadManager dm: dms ){

										DownloadManagerStats dm_stats = dm.getStats();

										total_prot_up += dm_stats.getProtocolSendRate();
										total_data_up += dm_stats.getDataSendRate();
										total_prot_down += dm_stats.getProtocolReceiveRate();
										total_data_down += dm_stats.getDataReceiveRate();
									}

									rate_str =
											MessageText.getString( "ConfigView.download.abbreviated") + DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(total_data_down, total_prot_down) + " " +
											MessageText.getString( "ConfigView.upload.abbreviated") + DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(total_data_up, total_prot_up);
								}

								if ( size_str != null || rate_str != null ){

									String temp;

									if ( size_str == null ){

										temp = rate_str;

									}else if ( rate_str == null ){

										temp = size_str;

									}else{

										temp = size_str + "; " + rate_str;
									}

									s += " (" + temp + ")";
								}
							}

							if ( transfers.header_show_uptime && transfers.totalStats != null ){

								long up_secs = (transfers.totalStats.getSessionUpTime()/60)*60;

								String	op;

								if ( up_secs < 60 ){

									up_secs = 60;

									op = "<";

								}else{

									op = " ";
								}

								String up_str = TimeFormatter.format2( up_secs, false );

								if ( s.equals( "" )){
									Debug.out( "eh" );
								}
								s += "; " +
									MessageText.getString(
										"label.uptime_coarse",
										new String[]{ op, up_str } );
							}

							soLibraryInfo.setText(s);
						}

						class
						ExtraInfoProvider
						{
							int											view_mode;
							ViewUtils.ViewTitleExtraInfo.CountProvider	countProvider;
							boolean										search_active;
							int											value;
							int											active;
							
							private
							ExtraInfoProvider(
								int	vm )
							{
								view_mode	= vm;
							}
						}
					});

			}
		} catch (Exception ignored) {
		}

		//Core core = CoreFactory.getSingleton();
		if (!CoreFactory.isCoreRunning()) {
			if (soWait != null) {
				soWait.setVisible(true);
				//soWait.getControl().getParent().getParent().getParent().layout(true, true);
			}
			final Initializer initializer = Initializer.getLastInitializer();
			if (initializer != null) {
				initializer.addListener(new InitializerListener() {
					@Override
					public void reportPercent(final int percent) {
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if (soWaitProgress != null && !soWaitProgress.isDisposed()) {
									waitProgress = percent;
									soWaitProgress.getControl().redraw();
									soWaitProgress.getControl().update();
								}
							}
						});
						if (percent > 100) {
							initializer.removeListener(this);
						}
					}

					@Override
					public void reportCurrentTask(String currentTask) {
						if (soWaitTask != null && !soWaitTask.isDisposed()) {
							soWaitTask.setText(currentTask);
						}
					}
				});
			}
		}

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				PluginInterface pi = PluginInitializer.getDefaultInterface();
				final UIManager uim = pi.getUIManager();
				uim.addUIListener(new UIManagerListener() {
					@Override
					public void UIDetached(UIInstance instance) {
					}

					@Override
					public void UIAttached(UIInstance instance) {
						if (instance instanceof UISWTInstance) {
							uim.removeUIListener(this);
							Utils.execSWTThread(new AERunnable() {
								@Override
								public void runSupport() {
									if (soWait != null) {
										soWait.setVisible(false);
									}
									if ( !skinObject.isDisposed()){

										setupView(core, skinObject);
									}
								}
							});
						}
					}
				});
			}
		});

		return null;
	}


	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		SelectedContentManager.removeCurrentlySelectedContentListener(
				selectedContentListener);

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void setupView(Core core, SWTSkinObject skinObject) {
		torrentFilter = skinObject.getSkinObjectID();
		if (torrentFilter.equalsIgnoreCase(SideBar.SIDEBAR_SECTION_LIBRARY_DL)) {
			torrentFilterMode = TORRENTS_INCOMPLETE;
		} else if (torrentFilter.equalsIgnoreCase(SideBar.SIDEBAR_SECTION_LIBRARY_CD)) {
			torrentFilterMode = TORRENTS_COMPLETE;
		} else if (torrentFilter.equalsIgnoreCase(SideBar.SIDEBAR_SECTION_LIBRARY_UNOPENED)) {
			torrentFilterMode = TORRENTS_UNOPENED;
		}

		if (datasource instanceof Tag) {
			Tag tag = (Tag) datasource;
			TagType tagType = tag.getTagType();
			if (tagType.getTagType() == TagType.TT_DOWNLOAD_STATE) {
				int tagID = tag.getTagID(); // see GlobalManagerImp.tag_*
				if (tagID == 1 || tagID == 3 || tagID == 11) {
					torrentFilterMode = TORRENTS_INCOMPLETE;
				} else if (tagID == 2 || tagID == 4 || tagID == 10) {
					torrentFilterMode = TORRENTS_COMPLETE;
				}
			}
		}

		soListArea = getSkinObject(ID + "-area");

		soListArea.getControl().setData("TorrentFilterMode",
				new Long(torrentFilterMode));
		soListArea.getControl().setData("DataSource", datasource);

		setViewMode(
				COConfigurationManager.getIntParameter(torrentFilter + ".viewmode"),
				false);

		SWTSkinObject so;
		so = getSkinObject(ID + "-button-smalltable");
		if (so != null) {
			btnSmallTable = new SWTSkinButtonUtility(so);
			btnSmallTable.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					setViewMode(MODE_SMALLTABLE, true);
				}
			});
		}

		so = getSkinObject(ID + "-button-bigtable");
		if (so != null) {
			btnBigTable = new SWTSkinButtonUtility(so);
			btnBigTable.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility,
				                    SWTSkinObject skinObject, int stateMask) {
					setViewMode(MODE_BIGTABLE, true);
				}
			});
		}

		SB_Transfers sb_t = MainMDISetup.getSb_transfers();
		
		if ( sb_t != null ) {
			
			sb_t.setupViewTitleWithCore(core);
		}
	}


	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		if ( isVisible()){
			long stateSmall = UIToolBarItem.STATE_ENABLED;
			long stateBig = UIToolBarItem.STATE_ENABLED;
			if (viewMode == MODE_BIGTABLE) {
				stateBig |= UIToolBarItem.STATE_DOWN;
			} else {
				stateSmall |= UIToolBarItem.STATE_DOWN;
			}
			list.put("modeSmall", stateSmall);
			list.put("modeBig", stateBig);
		}
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		String itemKey = item.getID();

		if (itemKey.equals("modeSmall")) {
			if (isVisible()) {
				setViewMode(MODE_SMALLTABLE, true);
				return true;
			}
		}
		if (itemKey.equals("modeBig")) {
			if (isVisible()) {
				setViewMode(MODE_BIGTABLE, true);
				return true;
			}
		}
		return false;
	}

	// @see SWTSkinObjectAdapter#skinObjectHidden(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		return super.skinObjectHidden(skinObject, params);
	}

	@Override
	public Object dataSourceChanged(SWTSkinObject skinObject, Object params) {
		datasource = params;
		if (soListArea != null) {
			Control control = soListArea.getControl();

			if ( !control.isDisposed()){

				control.setData("DataSource", params);
			}
		}

		return null;
	}

	public int getViewMode() {
		return viewMode;
	}

	protected void
	addHeaderInfoExtender(
		HeaderInfoExtender	extender )
	{

	}

	protected void
	removeHeaderInfoExtender(
		HeaderInfoExtender	extender )
	{

	}

	protected void
	refreshHeaderInfo()
	{
		MainMDISetup.getSb_transfers().triggerCountRefreshListeners();
	}

	protected interface
	HeaderInfoExtender
	{

	}
}
