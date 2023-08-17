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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.mdi.*;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.util.DataSourceUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.*;

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
	private TabbedMDI tabbedMDI;
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

		TableView tvToUse = tvOverride != null ? tvOverride : tv;

		Object[] dataSourcesCore = tvToUse.getSelectedDataSources(true);

		// When there is not selected datasources in the TableView, send the
		// parent's datasource
		// DISABLED now that some views can handle multiple types.
		//Object ds = dataSourcesCore.length == 0 ? tvToUse.getParentDataSource() : dataSourcesCore;
		Object ds = dataSourcesCore;

		if (tabbedMDI != null) {
			DownloadManager[] dms = DataSourceUtils.getDMs(dataSourcesCore);
			tabbedMDI.setMaximizeVisible(dms.length == 1);
			tabbedMDI.setEntriesDataSource(ds);
		}
	}

	public void setTvOverride(TableView<?> tvOverride) {
		if (this.tvOverride == tvOverride) {
			return;
		}
		this.tvOverride = tvOverride;
		selectedContent = SelectedContentManager.getCurrentlySelectedContent();
		triggerTabViewsDataSourceChanged(tv);
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
		MdiEntrySWT[] entries = tabbedMDI.getEntries();
		if (entries.length == 0) {
			return;
		}
		for (MdiEntrySWT entry : entries) {
			entry.triggerEvent(UISWTViewEvent.TYPE_LANGUAGEUPDATE, null);
		}
	}

	public MdiEntrySWT getActiveSubView() {
		if (!tv.isTabViewsEnabled() || tabbedMDI == null || tabbedMDI.isDisposed()
				|| tabbedMDI.getMinimized()) {
			return null;
		}

		return tabbedMDI.getCurrentEntry();
	}

		// TabViews Functions

	public Composite createSashForm(final Composite composite) {
		if (!tv.isTabViewsEnabled()) {
			tableComposite = tv.createMainPanel(composite);
			return tableComposite;
		}
		
		if (parentView instanceof TabbedEntry) {
			if (!((TabbedEntry) parentView).getMDI().getAllowSubViews()) {
				tableComposite = tv.createMainPanel(composite);
				return tableComposite;
			}
		}

		SelectedContentManager.addCurrentlySelectedContentListener(this);

		ConfigurationManager configMan = ConfigurationManager.getInstance();

		ViewManagerSWT vm = ViewManagerSWT.getInstance();
		int iNumViews = vm.getBuilders(tv.getTableID()).size()
				+ vm.getBuilders(tv.getDataSourceType()).size();

		if (iNumViews == 0) {
			tableComposite = tv.createMainPanel(composite);
			return tableComposite;
		}

		final String	props_prefix = tv.getTableID() + "." + tv.getTextPrefixID();

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
		tabbedMDI = new TabbedMDI(tv.getDataSourceType(), tv.getTableID(),
				props_prefix, parentView, null);
		tabbedMDI.setAllowSubViews(false);
		tabbedMDI.setMinimizeVisible(true);
		tabbedMDI.buildMDI(cTabsHolder);
		if (tabbedMDI.isEmpty()) {
			// All views said no, undo our tab creation
			// TODO: If a new view is registered for this table id, we should
			// create the tabbedMDI.  But we don't even do that yet when there
			// are 0 subviews and one is added.. so, don't worry about it
			form.dispose();
			tableComposite = tv.createMainPanel(composite);
			return tableComposite;
		}

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
		
		int iSplitAt = configMan.getIntParameter(props_prefix + ".SplitAt2", 300000 );

		if ( iSplitAt == 300000 ){
			
				// was stored with less precision
			
			String legacy_key = props_prefix + ".SplitAt";
			
			if ( configMan.hasParameter( legacy_key, false )){
				
				iSplitAt = configMan.getIntParameter( legacy_key,	3000);
				
				configMan.removeParameter( legacy_key );
				
					// Was stored at whole
				
				if ( iSplitAt < 100 ){
					
					iSplitAt *= 100;
				}
				
				iSplitAt *= 100;
			}
		}

		// pct is % bottom
		double pct = iSplitAt / 1000000.0;
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
					double d_split_at = l.doubleValue() * 1000000;
					
					int split_at = (int)d_split_at;
					
					if ( d_split_at - split_at > 0 ){
						split_at++;
					}

					configMan.setParameter(props_prefix + ".SplitAt2", split_at);
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

		/* TabbedMDI now keeps track of last selected tab so don't force.
		if (!tabbedMDI.getMinimized()) {
			MdiEntry[] entries = tabbedMDI.getEntries();
			if (entries.length > 0) {
				tabbedMDI.showEntry(entries[0]);
			}
		}
		*/
	}

	private ISelectedContent[] selectedContent;

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
	
	public MultipleDocumentInterfaceSWT getMDI() {
		return tabbedMDI;
	}
}
