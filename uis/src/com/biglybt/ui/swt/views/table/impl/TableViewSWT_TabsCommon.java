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

package com.biglybt.ui.swt.views.table.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTInstance.UISWTViewEventListenerWrapper;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCore;
import com.biglybt.ui.swt.views.table.TableViewSWT;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryCreationListener2;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.TabbedMdiInterface;
import com.biglybt.ui.swt.mdi.TabbedMdiMaximizeListener;

/**
 *
 * @author TuxPaper
 *
 */
public class TableViewSWT_TabsCommon implements SelectedContentListener
{
	UISWTView parentView;
	TableViewSWT<?> tv;

	/** Composite that stores the table (sometimes the same as mainComposite) */
	public Composite tableComposite;

	private TableView<?> tvOverride;
	private Sash sash;
	private TabbedMdiInterface tabbedMDI;
	private Composite cTabsHolder;
	private FormData fdHeightChanger;
	private MenuItem menuItemShowTabs;

	public TableViewSWT_TabsCommon(UISWTView parentView, TableViewSWT<?> tv) {
		this.parentView = parentView;
		this.tv = tv;
	}

	public void triggerTabViewsDataSourceChanged(TableView<?> tv) {
		if (tabbedMDI == null || tabbedMDI.isDisposed()) {
			return;
		}
		MdiEntry[] entries = tabbedMDI.getEntries();
		if (entries == null || entries.length == 0) {
			return;
		}

		Object[] ds = tv.getSelectedDataSources(true);

		for (MdiEntry entry : entries) {
			if (entry instanceof MdiEntrySWT) {
				triggerTabViewDataSourceChanged((MdiEntrySWT) entry, tv, ds);
			}
		}
	}

	public void setTvOverride(TableView<?> tvOverride) {
		this.tvOverride = tvOverride;
		selectedContent = SelectedContentManager.getCurrentlySelectedContent();
	}

	public void triggerTabViewDataSourceChanged(MdiEntrySWT view, TableView<?> tv,
			Object[] dataSourcesCore) {
		if (tvOverride != null) {
			tv = tvOverride;
		}
		if (view == null) {
			return;
		}

		// When there is not selected datasources in the TableView, send the
		// parent's datasource

		if (dataSourcesCore == null) {
			dataSourcesCore = tv.getSelectedDataSources(true);
		}
		if (tabbedMDI != null) {
			tabbedMDI.setMaximizeVisible(dataSourcesCore != null && dataSourcesCore.length == 1);
		}
		view.triggerEvent(UISWTViewEvent.TYPE_DATASOURCE_CHANGED,
				dataSourcesCore.length == 0 ? tv.getParentDataSource()
						: dataSourcesCore);
	}

	public void delete() {
		SelectedContentManager.removeCurrentlySelectedContentListener(this);

		if (menuItemShowTabs != null) {
			menuItemShowTabs.remove();
		}
		/* disposal of composite should take care of tabbedMDI
		if (tabbedMDI == null || tabbedMDI.isDisposed()) {
			return;
		}
		MdiEntry[] entries = tabbedMDI.getEntries();
		if (entries == null || entries.length == 0) {
			return;
		}
		for (MdiEntry entry : entries) {
			if (entry instanceof MdiEntrySWT) {
				((MdiEntrySWT) entry).triggerEvent(UISWTViewEvent.TYPE_DESTROY, null);
			}
		}
		*/
	}

	public void generate(IndentWriter writer) {
		writer.println("# of SubViews: "
				+ (tabbedMDI == null ? "null" : tabbedMDI.getEntriesCount()));
	}

	public void localeChanged() {
		if ( tabbedMDI == null ){
			return;
		}
		MdiEntry[] entries = tabbedMDI.getEntries();
		if (entries == null || entries.length == 0) {
			return;
		}
		for (MdiEntry entry : entries) {
			if (entry instanceof MdiEntrySWT) {
				((MdiEntrySWT) entry).triggerEvent(UISWTViewEvent.TYPE_LANGUAGEUPDATE, null);
			}
		}
	}

	public MdiEntrySWT getActiveSubView() {
		if (!tv.isTabViewsEnabled() || tabbedMDI == null || tabbedMDI.isDisposed()
				|| tabbedMDI.getMinimized()) {
			return null;
		}

		return tabbedMDI.getCurrentEntrySWT();
	}

		// TabViews Functions

	private MdiEntry addTabView(UISWTViewEventListenerWrapper listener,
			String afterID) {
		UISWTViewCore view = null;
		MdiEntrySWT entry = (MdiEntrySWT) tabbedMDI.createEntryFromEventListener(
				tv.getTableID(), listener, listener.getViewID(), true, null, afterID);
		if (entry instanceof UISWTViewCore) {
			view = (UISWTViewCore) entry;

		} else {
			return entry;
		}

		try {
			if (parentView != null) {
				view.setParentView(parentView);
			}

			triggerTabViewDataSourceChanged(entry, tv, null);

		} catch (Throwable e) {

			Debug.out(e);
		}

		return entry;
	}

	private void removeTabView(String id)
	{
		boolean exists = tabbedMDI.entryExists(id);
		if (!exists) {
			return;
		}
		MdiEntry entry = tabbedMDI.getEntry(id);

		// XXX
		//removedViews.add(((MdiEntrySWT) entry).getEventListener());
		tabbedMDI.removeItem(entry);
	}

	public Composite createSashForm(final Composite composite) {
		if (!tv.isTabViewsEnabled()) {
			tableComposite = tv.createMainPanel(composite);
			return tableComposite;
		}

		SelectedContentManager.addCurrentlySelectedContentListener(this);

		ConfigurationManager configMan = ConfigurationManager.getInstance();

		int iNumViews = 0;

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			if (pluginUI != null) {
				iNumViews += pluginUI.getViewListeners(tv.getTableID()).length;
			}
		}

		if (iNumViews == 0) {
			tableComposite = tv.createMainPanel(composite);
			return tableComposite;
		}

		final String	props_prefix = tv.getTableID() + "." + tv.getPropertiesPrefix();

		FormData formData;

		final Composite form = new Composite(composite, SWT.NONE);
		FormLayout flayout = new FormLayout();
		flayout.marginHeight = 0;
		flayout.marginWidth = 0;
		form.setLayout(flayout);
		GridData gridData;
		gridData = new GridData(GridData.FILL_BOTH);
		form.setLayoutData(gridData);

		// Create them in reverse order, so we can have the table auto-grow, and
		// set the tabFolder's height manually


		cTabsHolder = new Composite(form, SWT.NONE);
		tabbedMDI = uiFunctions.createTabbedMDI(cTabsHolder, props_prefix);
		tabbedMDI.setMaximizeVisible(true);
		tabbedMDI.setMinimizeVisible(true);

		tabbedMDI.setTabbedMdiMaximizeListener(new TabbedMdiMaximizeListener() {
			@Override
			public void maximizePressed() {
				TableView tvToUse = tvOverride == null ? tv : tvOverride;
				Object[] ds = tvToUse.getSelectedDataSources(true);

				if (ds.length == 1 && ds[0] instanceof DownloadManager) {

					UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

					if (uiFunctions != null) {

						uiFunctions.getMDI().showEntryByID(
								MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
								ds);
					}
				}
			}
		});

		final int SASH_WIDTH = 5;

		sash = Utils.createSash( form, SASH_WIDTH );

		tableComposite = tv.createMainPanel(form);
		Composite cFixLayout = tableComposite;
		while (cFixLayout != null && cFixLayout.getParent() != form) {
			cFixLayout = cFixLayout.getParent();
		}
		if (cFixLayout == null) {
			cFixLayout = tableComposite;
		}
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		cFixLayout.setLayout(layout);

		// FormData for Folder
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(100, 0);
		int iSplitAt = configMan.getIntParameter(props_prefix + ".SplitAt",
				3000);
		// Was stored at whole
		if (iSplitAt < 100) {
			iSplitAt *= 100;
		}

		// pct is % bottom
		double pct = iSplitAt / 10000.0;
		if (pct < 0.03) {
			pct = 0.03;
		} else if (pct > 0.97) {
			pct = 0.97;
		}

		// height will be set on first resize call
		sash.setData("PCT", new Double(pct));
		cTabsHolder.setLayout(new FormLayout());
		fdHeightChanger = formData;
		cTabsHolder.setLayoutData(formData);

		// FormData for Sash
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(cTabsHolder);
		formData.height = SASH_WIDTH;
		sash.setLayoutData(formData);

		// FormData for table Composite
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(0, 0);
		formData.bottom = new FormAttachment(sash);
		cFixLayout.setLayoutData(formData);



		// Listeners to size the folder
		sash.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				final boolean FASTDRAG = true;

				if (FASTDRAG && e.detail == SWT.DRAG) {
					return;
				}

				Rectangle area = form.getClientArea();

				int height = area.height - e.y - e.height;

				if ( !Constants.isWindows ){
					height -= SASH_WIDTH;
				}

					// prevent sash from being dragged too far up. In the worst case it ends up
					// overlaying the split my-torrents sash and the user can't easily separate the two...

				if ( area.height - height < 100 ){
					height = area.height - 100;
				}

				if ( height < 0 ){
					height = 0;
				}

				fdHeightChanger.height = height;

				Double l = new Double((double) height / area.height);
				sash.setData("PCT", l);
				if (e.detail != SWT.DRAG) {
					ConfigurationManager configMan = ConfigurationManager.getInstance();
					configMan.setParameter(props_prefix + ".SplitAt",
							(int) (l.doubleValue() * 10000));
				}
				form.layout();
				// sometimes sash cheese is left
				cTabsHolder.redraw();
			}
		});

		buildFolder(form, props_prefix);

		return form;
	}

	private void buildFolder(final Composite form, final String props_prefix) {
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();


		menuItemShowTabs = menuManager.addMenuItem(props_prefix + "._end_",
				"ConfigView.section.style.ShowTabsInTorrentView");
		menuItemShowTabs.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		menuItemShowTabs.setStyle(MenuItem.STYLE_CHECK);
		menuItemShowTabs.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(COConfigurationManager.getBooleanParameter(
						"Library.ShowTabsInTorrentView"));
			}
		});
		menuItemShowTabs.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				COConfigurationManager.setParameter("Library.ShowTabsInTorrentView",
						(Boolean) menu.getData());
			}
		});

		cTabsHolder.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (tabbedMDI.getMinimized()) {
					fdHeightChanger.height = tabbedMDI.getFolderHeight();
					cTabsHolder.getParent().layout();
					return;
				}

				Double l = (Double) sash.getData("PCT");
				if (l != null) {
					int newHeight = (int) (form.getBounds().height * l.doubleValue());
					if (newHeight != fdHeightChanger.height) {
						fdHeightChanger.height = newHeight;
						cTabsHolder.getParent().layout();
					}
				}
			}
		});

		String[] restricted_to = tv.getTabViewsRestrictedTo();

		Set<String> rt_set = new HashSet<>();

		if ( restricted_to != null ){

			rt_set.addAll( Arrays.asList( restricted_to ));
		}

		// Call plugin listeners

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {

			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			if (pluginUI != null) {

				UISWTViewEventListenerWrapper[] pluginViews = pluginUI.getViewListeners(
						tv.getTableID());
				if (pluginViews != null) {
					for (final UISWTViewEventListenerWrapper l : pluginViews) {
						if (l == null) {
							continue;
						}
						try {
							String view_id = l.getViewID();

							if (restricted_to != null && !rt_set.contains(view_id)) {
								continue;
							}

							tabbedMDI.registerEntry(view_id, new MdiEntryCreationListener2() {
								@Override
								public MdiEntry createMDiEntry(MultipleDocumentInterface mdi, String id,
								                               Object datasource, Map<?, ?> params) {
									return addTabView(l, null);
								}
							});

							tabbedMDI.loadEntryByID(view_id, false);

						} catch (Exception e) {
							// skip, plugin probably specifically asked to not be added
						}
					}
				}
			}
		}

		if (!tabbedMDI.getMinimized()) {
			MdiEntry[] entries = tabbedMDI.getEntries();
			if (entries.length > 0) {
				tabbedMDI.showEntry(entries[0]);
			}
		}
	}

	private ISelectedContent[] selectedContent;

	public void swt_refresh() {
		if (tv.isTabViewsEnabled() && tabbedMDI != null && !tabbedMDI.isDisposed()
				&& !tabbedMDI.getMinimized()){

			MdiEntry entry = tabbedMDI.getCurrentEntry();
			if (entry != null) {
				entry.updateUI();
			}
		}
	}

	// @see SelectedContentListener#currentlySelectedContentChanged(ISelectedContent[], java.lang.String)
	@Override
	public void currentlySelectedContentChanged(ISelectedContent[] currentContent,
	                                            String viewID) {
		TableView tvToUse = tvOverride == null ? tv : tvOverride;
		if (viewID != null && viewID.equals(tvToUse.getTableID())) {
			selectedContent = currentContent;
		}
		if (currentContent.length == 0 && tv.isVisible() && selectedContent != null
				&& selectedContent.length != 0) {
			SelectedContentManager.changeCurrentlySelectedContent(
					tvToUse.getTableID(), selectedContent, tvToUse);
		}
	}

	public void obfuscatedImage(Image image) {
		if (tabbedMDI instanceof ObfuscateImage) {
			ObfuscateImage o = (ObfuscateImage) tabbedMDI;
			image = o.obfuscatedImage(image);
		}
	}
}
