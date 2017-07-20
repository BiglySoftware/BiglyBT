/*
 * Created on Sep 25, 2008
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.activities.ActivitiesConstants;
import com.biglybt.activities.ActivitiesEntry;
import com.biglybt.activities.ActivitiesListener;
import com.biglybt.activities.ActivitiesManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UserPrompterResultListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfoManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryCreationListener;
import com.biglybt.ui.mdi.MdiListener;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.columns.utils.TableColumnCreatorV3;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class SBC_ActivityTableView
	extends SkinView
	implements UIUpdatable, UIPluginViewToolBarListener, ActivitiesListener
{
	private static int[] COLOR_UNVIEWED_ENTRIES = { 132, 16, 58 };
	private static ActivitiesListener activitiesListener;

	private TableViewSWT<ActivitiesEntry> view;

	private String tableID;

	private Composite viewComposite;

	private int viewMode = SBC_ActivityView.MODE_SMALLTABLE;

	// @see SkinView#skinObjectInitialShow(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectInitialShow(SWTSkinObject skinObject, Object params) {

		skinObject.addListener(new SWTSkinObjectListener() {
			@Override
			public Object eventOccured(SWTSkinObject skinObject, int eventType,
			                           Object params) {
				if (eventType == SWTSkinObjectListener.EVENT_SHOW) {
					SelectedContentManager.changeCurrentlySelectedContent(tableID,
							getCurrentlySelectedContent(), view);
				} else if (eventType == SWTSkinObjectListener.EVENT_HIDE) {
					SelectedContentManager.changeCurrentlySelectedContent(tableID, null,
							view);
				}
				return null;
			}
		});

		SWTSkinObject soParent = skinObject.getParent();

		Object data = soParent.getControl().getData("ViewMode");
		if (data instanceof Long) {
			viewMode = (int) ((Long) data).longValue();
		}

		boolean big = viewMode == SBC_ActivityView.MODE_BIGTABLE;

		tableID = big ? TableManager.TABLE_ACTIVITY_BIG
				: TableManager.TABLE_ACTIVITY;
		TableColumnCore[] columns = big
				? TableColumnCreatorV3.createActivityBig(tableID)
				: TableColumnCreatorV3.createActivitySmall(tableID);

		view = TableViewFactory.createTableViewSWT(ActivitiesEntry.class,
				tableID, tableID, columns, "name", SWT.MULTI | SWT.FULL_SELECTION
						| SWT.VIRTUAL);

		view.setRowDefaultHeightEM(big ? 3 : 2);

		view.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.DEL) {
					removeSelected();
				} else if (e.keyCode == SWT.F5) {
					if ((e.stateMask & SWT.SHIFT) != 0) {
						ActivitiesManager.resetRemovedEntries();
					}
					if ((e.stateMask & SWT.CONTROL) != 0) {
						System.out.println("pull all vuze news entries");
						ActivitiesManager.clearLastPullTimes();
						ActivitiesManager.pullActivitiesNow(0, "^F5", true);
					} else {
						System.out.println("pull latest vuze news entries");
						ActivitiesManager.pullActivitiesNow(0, "F5", true);
					}
				}
			}
		});

		view.addSelectionListener(new TableSelectionAdapter() {
			// @see TableSelectionAdapter#selected(TableRowCore[])
			@Override
			public void selected(TableRowCore[] rows) {
				selectionChanged();
				for (int i = 0; i < rows.length; i++) {
					ActivitiesEntry entry = (ActivitiesEntry) rows[i].getDataSource(true);
					if (entry != null && !entry.isRead() && entry.canFlipRead()) {
						entry.setRead(true);
					}
				}
			}

			@Override
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				if (rows.length == 1) {

					ActivitiesEntry ds = (ActivitiesEntry)rows[0].getDataSource();

					if ( ds.getTypeID().equals( ActivitiesConstants.TYPEID_LOCALNEWS )){

						String[] actions = ds.getActions();

						if ( actions.length == 1 ){

							ds.invokeCallback( actions[0] );
						}
					}else{

						TorrentListViewsUtils.playOrStreamDataSource( ds, false );
					}
				}
			}

			@Override
			public void deselected(TableRowCore[] rows) {
				selectionChanged();
			}

			public void selectionChanged() {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						ISelectedContent[] contents = getCurrentlySelectedContent();
						if (soMain.isVisible()) {
							SelectedContentManager.changeCurrentlySelectedContent(tableID,
									contents, view);
						}
					}
				});
			}

		}, false);

		view.addLifeCycleListener(new TableLifeCycleListener() {
			@Override
			public void tableLifeCycleEventOccurred(TableView tv, int eventType,
					Map<String, Object> data) {
				switch (eventType) {
					case EVENT_TABLELIFECYCLE_INITIALIZED:
						view.addDataSources(ActivitiesManager.getAllEntries().toArray(
								new ActivitiesEntry[0]));
						ActivitiesManager.addListener(SBC_ActivityTableView.this);
						break;

					case EVENT_TABLELIFECYCLE_DESTROYED:
						ActivitiesManager.removeListener(SBC_ActivityTableView.this);
						break;
				}
			}

		});

		SWTSkinObjectContainer soContents = new SWTSkinObjectContainer(skin,
				skin.getSkinProperties(), getUpdateUIName(), "", soMain);

		skin.layout();

		viewComposite = soContents.getComposite();
		viewComposite.setBackground(viewComposite.getDisplay().getSystemColor(
				SWT.COLOR_WIDGET_BACKGROUND));
		viewComposite.setForeground(viewComposite.getDisplay().getSystemColor(
				SWT.COLOR_WIDGET_FOREGROUND));
		viewComposite.setLayoutData(Utils.getFilledFormData());
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = gridLayout.marginHeight = gridLayout.marginWidth = 0;
		viewComposite.setLayout(gridLayout);

		view.initialize(viewComposite);

		return null;
	}

	// @see SWTSkinObjectAdapter#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		if (view != null) {
			view.delete();
		}
		return super.skinObjectDestroyed(skinObject, params);
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return tableID;
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (view != null) {
			view.refreshTable(false);
		}
	}

	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		list.put("remove",
				isVisible() && view != null && view.getSelectedRowsSize() > 0
						? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		if (item.getID().equals("remove")) {
			removeSelected();
			return true;
		}

		return false;
	}

	public ISelectedContent[] getCurrentlySelectedContent() {
		if (view == null) {
			return null;
		}
		List listContent = new ArrayList();
		Object[] selectedDataSources = view.getSelectedDataSources(true);
		for (int i = 0; i < selectedDataSources.length; i++) {

			ActivitiesEntry ds = (ActivitiesEntry) selectedDataSources[i];
			if (ds != null) {
				ISelectedContent currentContent;
				try {
					currentContent = ds.createSelectedContentObject();
					if (currentContent != null) {
						listContent.add(currentContent);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return (ISelectedContent[]) listContent.toArray(new ISelectedContent[listContent.size()]);
	}

	@Override
	public void vuzeNewsEntriesAdded(ActivitiesEntry[] entries) {
		if (view != null) {
			view.addDataSources(entries);
		}
	}

	@Override
	public void vuzeNewsEntriesRemoved(ActivitiesEntry[] entries) {
		if (view != null) {
			view.removeDataSources(entries);
			view.processDataSourceQueue();
		}
	}

	@Override
	public void vuzeNewsEntryChanged(ActivitiesEntry entry) {
		if (view == null) {
			return;
		}
		TableRowCore row = view.getRow(entry);
		if (row != null) {
			row.invalidate();
		}
	}

	private void removeEntries(final ActivitiesEntry[] toRemove,
			final int startIndex) {
		final ActivitiesEntry entry = toRemove[startIndex];
		if (entry == null
				|| ActivitiesConstants.TYPEID_HEADER.equals(entry.getTypeID())) {
			int nextIndex = startIndex + 1;
			if (nextIndex < toRemove.length) {
				removeEntries(toRemove, nextIndex);
			}
			return;
		}

		MessageBoxShell mb = new MessageBoxShell(
				MessageText.getString("v3.activity.remove.title"),
				MessageText.getString("v3.activity.remove.text", new String[] {
					entry.getText()
				}));
		mb.setRemember(tableID + "-Remove", false,
				MessageText.getString("MessageBoxWindow.nomoreprompting"));

		if (startIndex == toRemove.length - 1) {
			mb.setButtons(0, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no"),
			}, new Integer[] {
				0,
				1
			});
			mb.setRememberOnlyIfButton(0);
		} else {
			mb.setButtons(1, new String[] {
				MessageText.getString("Button.removeAll"),
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no"),
			}, new Integer[] {
				2,
				0,
				1
			});
			mb.setRememberOnlyIfButton(1);
		}

		mb.setHandleHTML(false);
		mb.open(new UserPrompterResultListener() {
			@Override
			public void prompterClosed(int result) {
				if (result == 2) {
					int numToRemove = toRemove.length - startIndex;
					ActivitiesEntry[] toGroupRemove = new ActivitiesEntry[numToRemove];
					System.arraycopy(toRemove, startIndex, toGroupRemove, 0, numToRemove);
					ActivitiesManager.removeEntries(toGroupRemove);
					return;
				} else if (result == 0) {
					ActivitiesManager.removeEntries(new ActivitiesEntry[] {
						entry
					});
				}

				int nextIndex = startIndex + 1;
				if (nextIndex < toRemove.length) {
					removeEntries(toRemove, nextIndex);
				}
			}
		});
	}

	protected void removeSelected() {
		if (view == null) {
			return;
		}
		ActivitiesEntry[] selectedEntries = view.getSelectedDataSources().toArray(
				new ActivitiesEntry[0]);

		if ( selectedEntries.length > 0 ){

			removeEntries(selectedEntries, 0);
		}
	}

	public TableViewSWT getView() {
		return view;
	}

	public static void setupSidebarEntry(final MultipleDocumentInterface mdi) {
		// Put TitleInfo in another class
		final ViewTitleInfo titleInfoActivityView = new ViewTitleInfo() {
			boolean	had_unviewed = false;
			@Override
			public Object getTitleInfoProperty(int propertyID) {
				if (propertyID == TITLE_INDICATOR_TEXT) {
					int num_unread		= 0;
					int num_unviewed	= 0;
					List<ActivitiesEntry> allEntries = ActivitiesManager.getAllEntries();

					for (ActivitiesEntry entry: allEntries ){

						if ( !entry.isRead()){

							num_unread++;
						}

						if ( !entry.getViewed()){

							num_unviewed++;
						}
					}

					if ( num_unread == 0 ){

						num_unviewed = 0;
					}

					boolean has_unviewed = num_unviewed > 0;

					if ( has_unviewed != had_unviewed ){

						if ( has_unviewed ){

							MdiEntry parent = mdi.getEntry( MultipleDocumentInterface.SIDEBAR_HEADER_VUZE );

							if ( parent != null && !parent.isExpanded()){

								parent.setExpanded( true );
							}
						}

						had_unviewed = has_unviewed;
					}

					if ( num_unviewed > 0 ){

						return( String.valueOf( num_unviewed ) + ( num_unread==0?"":(":"+num_unread)));

					}else if ( num_unread > 0 ){

						return( String.valueOf( num_unread ));
					}

					return null;

				}else if ( propertyID == TITLE_IMAGEID ){

					return "image.sidebar.activity";

				}else if ( propertyID == TITLE_INDICATOR_COLOR){

					boolean has_unread		= false;
					boolean has_unviewed 	= false;

					List<ActivitiesEntry> allEntries = ActivitiesManager.getAllEntries();

					for ( ActivitiesEntry entry: allEntries ){

						if ( !entry.isRead()){

							has_unread = true;
						}

						if ( !entry.getViewed()){

							has_unviewed = true;
						}
					}

					if ( has_unread && has_unviewed ){

						return( COLOR_UNVIEWED_ENTRIES );
					}
				}

				return null;
			}
		};
		activitiesListener = new ActivitiesListener() {
			@Override
			public void vuzeNewsEntryChanged(ActivitiesEntry entry) {
				ViewTitleInfoManager.refreshTitleInfo(titleInfoActivityView);
			}

			@Override
			public void vuzeNewsEntriesRemoved(ActivitiesEntry[] entries) {
				ViewTitleInfoManager.refreshTitleInfo(titleInfoActivityView);
			}

			@Override
			public void vuzeNewsEntriesAdded(ActivitiesEntry[] entries) {
				ViewTitleInfoManager.refreshTitleInfo(titleInfoActivityView);
			}
		};
		ActivitiesManager.addListener(activitiesListener);

		MdiEntryCreationListener creationListener = new MdiEntryCreationListener() {
			@Override
			public MdiEntry createMDiEntry(String id) {
				return mdi.createEntryFromSkinRef(MultipleDocumentInterface.SIDEBAR_HEADER_VUZE,
						MultipleDocumentInterface.SIDEBAR_SECTION_ACTIVITIES, "activity",
						"{sidebar." + MultipleDocumentInterface.SIDEBAR_SECTION_ACTIVITIES
								+ "}", titleInfoActivityView, null, false, null);
			}
		};
		mdi.registerEntry(MultipleDocumentInterface.SIDEBAR_SECTION_ACTIVITIES, creationListener);
		mdi.registerEntry("activities", creationListener);

		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();

		final MenuItem menuItem = menuManager.addMenuItem("sidebar."
				+ MultipleDocumentInterface.SIDEBAR_SECTION_ACTIVITIES,
				"v3.activity.button.readall");
		menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItem.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				List<ActivitiesEntry> allEntries = ActivitiesManager.getAllEntries();
				for (ActivitiesEntry entry: allEntries ){
					entry.setRead(true);
				}
			}
		});

		mdi.addListener(new MdiListener() {
			@Override
			public void mdiEntrySelected(MdiEntry newEntry, MdiEntry oldEntry) {

			}

			@Override
			public void mdiDisposed(MultipleDocumentInterface mdi) {
				if (activitiesListener != null) {
					ActivitiesManager.removeListener(activitiesListener);
					activitiesListener = null;
				}

				menuItem.remove();
			}
		});
	}
}
