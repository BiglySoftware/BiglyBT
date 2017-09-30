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

package com.biglybt.ui.swt.mdi;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.MenuBuildUtils.MenuBuilder;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateImage;
import com.biglybt.ui.swt.pif.PluginUISWTSkinObject;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventCancelledException;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventListenerHolder;
import com.biglybt.ui.swt.shells.main.MainMDISetup;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.IViewAlwaysInitialize;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;

public class TabbedMDI
	extends BaseMDI
	implements TabbedMdiInterface, AEDiagnosticsEvidenceGenerator,
	ParameterListener, ObfuscateImage
{
	private CTabFolder tabFolder;

	private LinkedList<MdiEntry>	select_history = new LinkedList<>();

	protected boolean minimized;

	private int iFolderHeightAdj;

	private final String props_prefix;

	private DownloadManager		maximizeTo;

	private int minimumCharacters = 25;

	protected boolean isMainMDI;

	private Map mapUserClosedTabs;

	private boolean maximizeVisible = false;

	private boolean minimizeVisible = false;

	private TabbedMdiMaximizeListener maximizeListener;
	private ParameterListener paramFancyTabListener;

	/** Called from MainWindowImpl via reflection for main UI */
	public TabbedMDI() {
		super();
		AEDiagnostics.addWeakEvidenceGenerator(this);
		mapUserClosedTabs = new HashMap();
		isMainMDI = true;
		this.props_prefix = "sidebar";
	}

	/**
	 * @param parent
	 */
	public TabbedMDI(Composite parent, String id) {
		this.props_prefix = id;
		minimumCharacters = 0;
		isMainMDI = false;
		setCloseableConfigFile(null);

		SWTSkin skin = SWTSkinFactory.getInstance();
		SWTSkinObjectTabFolder soFolder = new SWTSkinObjectTabFolder(skin,
				skin.getSkinProperties(), id, "tabfolder.fill", parent);
		setMainSkinObject(soFolder);
		soFolder.addListener(this);
		skin.addSkinObject(soFolder);

		String key = props_prefix + ".closedtabs";

		mapUserClosedTabs = COConfigurationManager.getMapParameter(key, new HashMap());
		COConfigurationManager.addWeakParameterListener(this, false, key);
	}


	@Override
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		super.skinObjectCreated(skinObject, params);

		creatMDI();

		return null;
	}

	/* (non-Javadoc)
	 * @see BaseMDI#skinObjectDestroyed(SWTSkinObject, java.lang.Object)
	 */
	@Override
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {

		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			entry.close(true);
		}

		String key = props_prefix + ".closedtabs";
		COConfigurationManager.removeWeakParameterListener(this, key);
		COConfigurationManager.removeParameterListener("GUI_SWT_bFancyTab",
				paramFancyTabListener);

		return super.skinObjectDestroyed(skinObject, params);
	}

	private void creatMDI() {
		if (soMain instanceof SWTSkinObjectTabFolder) {
			tabFolder = ((SWTSkinObjectTabFolder) soMain).getTabFolder();
		} else {
			tabFolder = new CTabFolder((Composite) soMain.getControl(), SWT.TOP
					| SWT.BORDER | SWT.CLOSE);
		}

		iFolderHeightAdj = tabFolder.computeSize(SWT.DEFAULT, 0).y;

		if (isMainMDI) {
			paramFancyTabListener = new ParameterListener() {
				@Override
				public void parameterChanged(String parameterName) {
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							boolean simple = !COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab");
							tabFolder.setSimple(simple);
						}
					});
				}
			};
			COConfigurationManager.addAndFireParameterListener("GUI_SWT_bFancyTab",
					paramFancyTabListener);
  		tabFolder.setSimple(!COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab"));
		} else {
			tabFolder.setSimple(true);
			tabFolder.setMaximizeVisible(maximizeVisible);
			tabFolder.setMinimizeVisible(minimizeVisible);
			tabFolder.setUnselectedCloseVisible(false);
		}

		Display display = tabFolder.getDisplay();

		float[] hsb = tabFolder.getBackground().getRGB().getHSB();
		hsb[2] *= (Constants.isOSX) ? 0.9 : 0.97;
		tabFolder.setBackground(ColorCache.getColor(display, hsb));

		hsb = tabFolder.getForeground().getRGB().getHSB();
		hsb[2] *= (Constants.isOSX) ? 1.1 : 0.03;
		tabFolder.setForeground(ColorCache.getColor(display, hsb));

		tabFolder.setSelectionBackground(new Color[] {
			Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND),
			Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND),
			Colors.getSystemColor(display, SWT.COLOR_WIDGET_BACKGROUND)
		}, new int[] {
			10,
			90
		}, true);
		tabFolder.setSelectionForeground(Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));

		if (minimumCharacters > 0) {
			tabFolder.setMinimumCharacters(minimumCharacters);
		}

		// XXX TVSWT_Common had SWT.Activate too
		tabFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TabbedEntry entry = (TabbedEntry) event.item.getData("TabbedEntry");
				showEntry(entry);
			}
		});

		tabFolder.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				if (tabFolder.getMinimized()) {
					restore();
					// If the user clicked down on the restore button, and we restore
					// before the CTabFolder does, CTabFolder will minimize us again
					// There's no way that I know of to determine if the mouse is
					// on that button!

					// one of these will tell tabFolder to cancel
					e.button = 0;
					tabFolder.notifyListeners(SWT.MouseExit, null);
				}
			}
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				if (!tabFolder.getMinimized() && tabFolder.getMaximizeVisible()) {
					minimize();
				}
			}
		});

		tabFolder.addCTabFolder2Listener(new CTabFolder2Adapter() {
			@Override
			public void minimize(CTabFolderEvent event) {
				TabbedMDI.this.minimize();
			}


			@Override
			public void maximize(CTabFolderEvent event) {
				if (maximizeListener != null) {
					maximizeListener.maximizePressed();
				}
			}


			@Override
			public void restore(CTabFolderEvent event_maybe_null) {
				TabbedMDI.this.restore();
			}


			@Override
			public void close(CTabFolderEvent event) {
				final TabbedEntry entry = (TabbedEntry) event.item.getData(
						"TabbedEntry");

				if (select_history.remove(entry)) {

					if (select_history.size() > 0) {

						final MdiEntry next = select_history.getLast();

						if (!next.isDisposed() && next != entry) {

							// If tabfolder's selected entry is the one we are closing,
							// CTabFolder will try to move to next CTabItem.  Disable
							// this feature by moving tabfolder's selection away from us
							CTabItem[] items = tabFolder.getItems();
							for (int i = 0; i < items.length; i++) {
								CTabItem item = items[i];
								TabbedEntry scanEntry = getEntryFromTabItem(item);
								if (scanEntry == next) {
									tabFolder.setSelection(item);
									break;
								}
							}

							showEntry(next);
						}
					}
				}

				// since showEntry is slightly delayed, we must slightly delay
				// the closing of the entry the user clicked.  Otherwise, it would close
				// first, and the first tab would auto-select (on windows), and then
				// the "next" tab would select.
				if (props_prefix != null) {
  				Utils.execSWTThreadLater(0, new AERunnable() {

  					@Override
  					public void runSupport() {
  						String view_id = entry.getViewID();
  						String key = props_prefix + ".closedtabs";

  						Map closedtabs = COConfigurationManager.getMapParameter(key,
  								new HashMap());

  						if (!closedtabs.containsKey(view_id)) {

  							closedtabs.put(view_id, entry.getTitle());

  							// this will trigger listener which will remove the tab
  							COConfigurationManager.setParameter(key, closedtabs);
  						}
  					}
  				});
				}
			}
		});

		if (isMainMDI) {
  		tabFolder.getDisplay().addFilter(SWT.KeyDown, new Listener() {
  			@Override
			  public void handleEvent(Event event) {
  				if ( tabFolder.isDisposed()){
  					return;
  				}
  				// Another window has control, skip filter
  				Control focus_control = tabFolder.getDisplay().getFocusControl();
  				if (focus_control != null
  						&& focus_control.getShell() != tabFolder.getShell()) {
  					return;
  				}

  				int key = event.character;
  				if ((event.stateMask & SWT.MOD1) != 0 && event.character <= 26
  						&& event.character > 0)
  					key += 'a' - 1;

  				// ESC or CTRL+F4 closes current Tab
  				if (key == SWT.ESC
  						|| (event.keyCode == SWT.F4 && event.stateMask == SWT.CTRL)) {
  					MdiEntry entry = getCurrentEntry();
  					if (entry != null) {
  						entry.close(false);
  					}
  					event.doit = false;
  				} else if (event.keyCode == SWT.F6
  						|| (event.character == SWT.TAB && (event.stateMask & SWT.CTRL) != 0)) {
  					// F6 or Ctrl-Tab selects next Tab
  					// On Windows the tab key will not reach this filter, as it is
  					// processed by the traversal TRAVERSE_TAB_NEXT.  It's unknown
  					// what other OSes do, so the code is here in case we get TAB
  					if ((event.stateMask & SWT.SHIFT) == 0) {
  						event.doit = false;
  						selectNextTab(true);
  						// Shift+F6 or Ctrl+Shift+Tab selects previous Tab
  					} else if (event.stateMask == SWT.SHIFT) {
  						selectNextTab(false);
  						event.doit = false;
  					}
  				}
  			}
  		});
		}

		tabFolder.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				saveCloseables();
			}
		});

		tabFolder.getTabHeight();
		final Menu menu = new Menu( tabFolder );
		tabFolder.setMenu(menu);
		MenuBuildUtils.addMaintenanceListenerForMenu(menu, new MenuBuilder() {
			@Override
			public void buildMenu(Menu root_menu, MenuEvent event) {
				Point cursorLocation = event.display.getCursorLocation();
				Point ptOnControl = tabFolder.toControl(cursorLocation.x,
						cursorLocation.y);
				if (ptOnControl.y > tabFolder.getTabHeight()) {
					return;
				}

				final CTabItem item = tabFolder.getItem(
						tabFolder.toControl(cursorLocation.x, cursorLocation.y));

				boolean need_sep = false;

				if (item == null) {

					need_sep = mapUserClosedTabs.size() > 0;
					if (need_sep) {
  					for (Object id : mapUserClosedTabs.keySet()) {
  						final String view_id = (String) id;

  						MenuItem mi = new MenuItem(menu, SWT.PUSH);

  						String title;

  						Object oTitle = mapUserClosedTabs.get(id);
  						if (oTitle instanceof String && ((String) oTitle).length() > 0) {
								title = (String) oTitle;
							} else {
								title = MessageText.getString(getViewTitleID(view_id));
							}
  						mi.setText(title);

  						mi.addListener(SWT.Selection, new Listener() {
  							@Override
							  public void handleEvent(Event event) {
  								String key = props_prefix + ".closedtabs";

  								Map closedtabs = COConfigurationManager.getMapParameter(key,
  										new HashMap());

  								if (closedtabs.containsKey(view_id)) {

  									closedtabs.remove(view_id);

  									COConfigurationManager.setParameter(key, closedtabs);
  								}

  								showEntryByID(view_id);
  							}
  						});

  					}
					}
				}

				if (need_sep) {
					new MenuItem(menu, SWT.SEPARATOR);
				}


				TabbedEntry entry = null;
				if (item != null) {
					entry = getEntryFromTabItem(item);


					showEntry(entry);
				}

				fillMenu(menu, entry, props_prefix);

			}
		});

		if (SWT.getVersion() > 3600) {
			TabbedMDI_Ren.setupTabFolderRenderer(this, tabFolder);
		}

		if (minimizeVisible) {
			boolean toMinimize = ConfigurationManager.getInstance().getBooleanParameter(props_prefix + ".subViews.minimized");
			setMinimized(toMinimize);
		}
	}

	private String
	getViewTitleID(
		String	view_id )
	{
		String history_key = "swt.ui.table.tab.view.namecache." + view_id;

		String id = COConfigurationManager.getStringParameter( history_key, "" );

		if ( id.length() == 0 ){

			String test = view_id + ".title.full";

			if ( MessageText.keyExists( test )){

				return( test );
			}

			id = "!" + view_id + "!";
		}

		return( id );
	}



	private void minimize() {
		minimized = true;

		tabFolder.setMinimized(true);
		CTabItem[] items = tabFolder.getItems();
		String tt = MessageText.getString("label.click.to.restore");
		for (int i = 0; i < items.length; i++) {
			CTabItem tabItem = items[i];
			tabItem.setToolTipText(tt);
			Control control = tabItem.getControl();
			if (control != null && !control.isDisposed()) {
				tabItem.getControl().setVisible(false);
			}
		}

		tabFolder.getParent().notifyListeners(SWT.Resize, null);

		showEntry(null);

		ConfigurationManager configMan = ConfigurationManager.getInstance();
		configMan.setParameter(props_prefix + ".subViews.minimized", true);
	}

	private void restore() {

		minimized = false;
		tabFolder.setMinimized(false);
		CTabItem selection = tabFolder.getSelection();
		if (selection != null) {
			TabbedEntry tabbedEntry = getEntryFromTabItem(selection);

			showEntry(tabbedEntry);

			/* Already done by TabbedEntry.swt_build
			Control control = selection.getControl();
			if (control == null || control.isDisposed()) {
				selectedView.initialize(tabFolder);
				selection.setControl(selectedView.getComposite());
				control = selection.getControl();
				triggerTabViewDataSourceChanged(selectedView, tv, new Object[][] {
					null,
					null
				});
			}
			selection.getControl().setVisible(true);
			*/
			tabbedEntry.updateUI();
		}

		if (tabFolder.getMaximizeVisible()) {
			CTabItem[] items = tabFolder.getItems();
			String tt = MessageText.getString("label.dblclick.to.min");

			for (int i = 0; i < items.length; i++) {
				CTabItem tabItem = items[i];
				tabItem.setToolTipText(tt);
			}
		}

		tabFolder.getParent().notifyListeners(SWT.Resize, null);

		ConfigurationManager configMan = ConfigurationManager.getInstance();
		configMan.setParameter(props_prefix + ".subViews.minimized", false);
	}



	private void selectNextTab(boolean selectNext) {
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}

		final int nextOrPrevious = selectNext ? 1 : -1;
		int index = tabFolder.getSelectionIndex() + nextOrPrevious;
		if (index == 0 && selectNext || index == -2 || tabFolder.getItemCount() < 2) {
			return;
		}
		if (index == tabFolder.getItemCount()) {
			index = 0;
		} else if (index < 0) {
			index = tabFolder.getItemCount() - 1;
		}

		// instead of .setSelection, use showEntry which will ensure view de/activations
		CTabItem item = tabFolder.getItem(index);
		MdiEntry entry = getEntryFromTabItem(item);

		if (entry != null) {
			showEntry(entry);
		}
	}

	@Override
	protected boolean wasEntryLoadedOnce(String id) {
		@SuppressWarnings("deprecation")
		boolean loadedOnce = COConfigurationManager.getBooleanParameter("tab.once."
				+ id, false);
		return loadedOnce;
	}

	@Override
	protected void setEntryLoadedOnce(String id) {
		COConfigurationManager.setParameter("tab.once." + id, true);
	}

	@Override
	public void showEntry(final MdiEntry newEntry) {
		if (newEntry == null) {
			return;
		}

		if (newEntry != null) {
  		select_history.remove( newEntry );

  		select_history.add( newEntry );

  		if ( select_history.size() > 64 ){

  			select_history.removeFirst();
  		}
		}

		MdiEntry oldEntry = currentEntry;
		if (newEntry == oldEntry && oldEntry != null) {
			((BaseMdiEntry) newEntry).show();
			triggerSelectionListener(newEntry, newEntry);
			return;
		}

		if (oldEntry != null) {
			oldEntry.hide();
		}

		currentEntry = (MdiEntrySWT) newEntry; // assumed MdiEntrySWT

		if (currentEntry instanceof BaseMdiEntry) {
			((BaseMdiEntry) newEntry).show();
		}

		triggerSelectionListener(newEntry, oldEntry);
	}

	private MdiEntry createEntryFromSkinRef(String parentID, String id,
			String configID, String title, ViewTitleInfo titleInfo, Object params,
			boolean closeable, int index) {
		MdiEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		TabbedEntry entry = new TabbedEntry(this, skin, id, null);
		entry.setTitle(title);
		entry.setSkinRef(configID, params);
		entry.setViewTitleInfo(titleInfo);

		setupNewEntry(entry, id, index, closeable);
		return entry;
	}

	// @see BaseMDI#createEntryFromSkinRef(java.lang.String, java.lang.String, java.lang.String, java.lang.String, ViewTitleInfo, java.lang.Object, boolean, java.lang.String)
	@Override
	public MdiEntry createEntryFromSkinRef(String parentID, String id,
	                                       String configID, String title, ViewTitleInfo titleInfo, Object params,
	                                       boolean closeable, String preferedAfterID) {
		// afterid not fully supported yet
		return createEntryFromSkinRef(parentID, id, configID, title, titleInfo,
				params, closeable, "".equals(preferedAfterID) ? 0 : -1);
	}

	@Override
	public MdiEntry createEntryFromEventListener(String parentEntryID,
	                                             String parentViewID, UISWTViewEventListener l, String id,
	                                             boolean closeable, Object datasource, String preferredAfterID) {
		if (isEntryClosedByUser(id)) {
			return null;
		}
		MdiEntry oldEntry = getEntry(id);
		if (oldEntry != null) {
			return oldEntry;
		}

		TabbedEntry entry = new TabbedEntry(this, skin, id, parentViewID);

		if ( datasource == null && l instanceof UISWTViewEventListenerHolder ) {
			datasource = ((UISWTViewEventListenerHolder)l).getInitialDataSource();
		}
		try {
			entry.setEventListener(l, true);
		} catch (UISWTViewEventCancelledException e) {
			entry.close(true);
			return null;
		}
		entry.setDatasource(datasource);
		entry.setPreferredAfterID(preferredAfterID);

		setupNewEntry(entry, id, -1, closeable);

		addMenus( entry, id );
		
		if (l instanceof IViewAlwaysInitialize) {
			entry.build();
		}

		return entry;
	}

	private boolean isEntryClosedByUser(String id) {

		if (mapUserClosedTabs.containsKey(id)) {
			return true;
		}
		// TODO Auto-generated method stub
		return false;
	}

	private void setupNewEntry(final TabbedEntry entry, final String id,
			final int index, boolean closeable) {
		addItem( entry );

		entry.setCloseable(closeable);

		Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				swt_setupNewEntry(entry, id, index);
			}
		});
	}

	private void swt_setupNewEntry(TabbedEntry entry, String id, int index) {
		if (tabFolder == null || tabFolder.isDisposed()) {
			return;
		}
		if (index < 0 || index >= tabFolder.getItemCount()) {
			index = tabFolder.getItemCount();
		}
		CTabItem cTabItem = new CTabItem(tabFolder, SWT.NONE, index);
		cTabItem.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (tabFolder.getItemCount() == 0) {
					currentEntry = null;
				}
			}
		});
		cTabItem.setData("TabbedEntry", entry);
		entry.setSwtItem(cTabItem);

		if (tabFolder.getItemCount() == 1) {
  		Utils.execSWTThreadLater(0, new AERunnable() {

  			@Override
  			public void runSupport() {
  				if (currentEntry != null || tabFolder.isDisposed()) {
  					return;
  				}
  				CTabItem selection = tabFolder.getSelection();
  				if (selection == null) {
  					return;
  				}
  				TabbedEntry entry = getEntryFromTabItem(selection);
  				showEntry(entry);
  			}
  		});
		}
	}

	protected TabbedEntry getEntryFromTabItem(CTabItem item) {
		if (item.isDisposed()) {
			return null;
		}
		return (TabbedEntry) item.getData("TabbedEntry");
	}

	@Override
	public String getUpdateUIName() {
		String name = "MDI";
		MdiEntry entry = getCurrentEntry();
		if (entry != null) {
			name += "-" + entry.getId();
		}
		return name;
	}

	@Override
	public void generate(IndentWriter writer) {
		MdiEntrySWT[] entries = getEntriesSWT();
		for (MdiEntrySWT entry : entries) {
			if (entry == null) {
				continue;
			}


			if (!(entry instanceof AEDiagnosticsEvidenceGenerator)) {
				writer.println("TabbedMdi View (No Generator): " + entry.getId());
				try {
					writer.indent();

					writer.println("Parent: " + entry.getParentID());
					writer.println("Title: " + entry.getTitle());
				} catch (Exception e) {

				} finally {

					writer.exdent();
				}
			}
		}
	}

	// @see MultipleDocumentInterfaceSWT#getEntryFromSkinObject(com.biglybt.ui.swt.pif.PluginUISWTSkinObject)
	@Override
	public MdiEntrySWT getEntryFromSkinObject(PluginUISWTSkinObject pluginSkinObject) {
		if (pluginSkinObject instanceof SWTSkinObject) {
			Control control = ((SWTSkinObject) pluginSkinObject).getControl();
			while (control != null && !control.isDisposed()) {
				Object entry = control.getData("BaseMDIEntry");
				if (entry instanceof BaseMdiEntry) {
					BaseMdiEntry mdiEntry = (BaseMdiEntry) entry;
					return mdiEntry;
				}
				control = control.getParent();
			}
		}
		return null;
	}

	@Override
	public MdiEntry createHeader(String id, String title, String preferredAfterID) {
		return null;
	}

	@Override
	public CTabFolder getTabFolder() {
		return tabFolder;
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMaximizeVisible(boolean)
	 */
	@Override
	public void setMaximizeVisible(final boolean visible) {
		maximizeVisible = visible;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}
				tabFolder.setMaximizeVisible(visible);
			}
		});
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMinimizeVisible(boolean)
	 */
	@Override
	public void setMinimizeVisible(final boolean visible) {
		minimizeVisible = visible;
		if (minimizeVisible) {
			boolean toMinimize = ConfigurationManager.getInstance().getBooleanParameter(props_prefix + ".subViews.minimized");
			setMinimized(toMinimize);
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}
				tabFolder.setMinimizeVisible(visible);
			}
		});
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#getMinimized()
	 */
	@Override
	public boolean getMinimized() {
		return minimized;
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setMinimized(boolean)
	 */
	@Override
	public void setMinimized(final boolean minimized) {
		this.minimized = minimized;
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}

				if (minimized) {
					minimize();
				} else {
					restore();
				}
			}
		});
	}

	@Override
	public int getFolderHeight() {
		return iFolderHeightAdj;
	}


	/* (non-Javadoc)
	 * @see SWTSkinObjectAdapter#dataSourceChanged(SWTSkinObject, java.lang.Object)
	 */
	@Override
	public Object dataSourceChanged(SWTSkinObject skinObject, final Object ds) {
		Utils.execSWTThread(new Runnable() {
			@Override
			public void run() {
				if (tabFolder == null || tabFolder.isDisposed()) {
					return;
				}

				if (ds instanceof Object[]) {
					Object[] temp = (Object[]) ds;
					if (temp.length == 1) {
						Object obj = temp[0];

						if (obj instanceof DownloadManager) {
							maximizeTo = (DownloadManager) obj;
						} else if (obj instanceof Download) {
							maximizeTo = PluginCoreUtils.unwrap((Download) obj);
						}
					}
				}

				setMaximizeVisible(maximizeTo != null);

			}
		});

		return super.dataSourceChanged(skinObject, ds);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.config.ParameterListener#parameterChanged(java.lang.String)
	 */
	@Override
	public void parameterChanged(String parameterName) {
		if (isDisposed()) {
			return;
		}

		mapUserClosedTabs = COConfigurationManager.getMapParameter(parameterName, new HashMap());

		for (Object id : mapUserClosedTabs.keySet()) {
			String view_id = (String) id;
			if (entryExists(view_id)) {
				closeEntry(view_id);
			}
		}
	}

	/* (non-Javadoc)
	 * @see TabbedMdiInterface#setTabbedMdiMaximizeListener(TabbedMdiMaximizeListener)
	 */
	@Override
	public void setTabbedMdiMaximizeListener(TabbedMdiMaximizeListener l) {
		maximizeListener = l;
	}

	// @see com.biglybt.ui.swt.debug.ObfuscateImage#obfuscatedImage(org.eclipse.swt.graphics.Image)
	@Override
	public Image obfuscatedImage(Image image) {
		MdiEntry[] entries = getEntries();
		for (MdiEntry entry : entries) {
			if (entry instanceof ObfuscateImage) {
				ObfuscateImage oi = (ObfuscateImage) entry;
				image = oi.obfuscatedImage(image);
			}
		}
		return image;
	}

	@Override
	protected MdiEntry
	createEntryByCreationListener(String id, Object ds, Map<?, ?> autoOpenMap)
	{
		final TabbedEntry result = (TabbedEntry)super.createEntryByCreationListener(id, ds, autoOpenMap);

		if ( result != null ){
			
			addMenus( result, id );

		}

		return( result );
	}

	private void
	addMenus(
		TabbedEntry		entry,
		String			id )
	{
		PluginManager pm = CoreFactory.getSingleton().getPluginManager();
		PluginInterface pi = pm.getDefaultPluginInterface();
		UIManager uim = pi.getUIManager();
		MenuManager menuManager = uim.getMenuManager();
		
		{
			if ( !Utils.isAZ2UI()){
				
				com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem( id + "._end_", "menu.add.to.dashboard");
				menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);
		
				menuItem.addFillListener(
					new MenuItemFillListener() {
		
						@Override
						public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {
		
								// pick up the right target - due to the confusion of multiple tab instances registering
								// the same menu entries the original registerer may well not be the one that should receive the event,
								// rather the one specified in the event is
							
							TabbedEntry	target = entry;
							
							if ( data instanceof Object[]) {
								Object[] odata = (Object[])data;
								if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
									target = (TabbedEntry)odata[0];
								}
							}
							
							menu.setVisible(target.canBuildStandAlone());
						}
					});
		
				menuItem.addListener(new MenuItemListener() {
					@Override
					public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data ) {
							
						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}else if ( data instanceof TabbedEntry ){
							target = (TabbedEntry)data;
						}
						
						MainMDISetup.getSb_dashboard().addItem( target );
					}
				});
			}
		}
		
		{
			com.biglybt.pif.ui.menus.MenuItem menuItem = menuManager.addMenuItem(id + "._end_", "menu.pop.out");
			menuItem.setDisposeWithUIDetach(UIInstance.UIT_SWT);

			menuItem.addFillListener(
				new com.biglybt.pif.ui.menus.MenuItemFillListener() {

					@Override
					public void menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {

						TabbedEntry	target = entry;
						
						if ( data instanceof Object[]) {
							Object[] odata = (Object[])data;
							if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
								target = (TabbedEntry)odata[0];
							}
						}
						
						menu.setVisible( target.canBuildStandAlone());
					}
				});

			menuItem.addListener(new com.biglybt.pif.ui.menus.MenuItemListener() {
				@Override
				public void selected(com.biglybt.pif.ui.menus.MenuItem menu, Object data) {

					TabbedEntry	target = entry;
					
					if ( data instanceof Object[]) {
						Object[] odata = (Object[])data;
						if ( odata.length == 1 && odata[0] instanceof TabbedEntry ) {
							target = (TabbedEntry)odata[0];
						}
					}else if ( data instanceof TabbedEntry ){
						target = (TabbedEntry)data;
					}
					
					SkinnedDialog skinnedDialog =
							new SkinnedDialog(
									"skin3_dlg_sidebar_popout",
									"shell",
									null,	// standalone
									SWT.RESIZE | SWT.MAX | SWT.DIALOG_TRIM);

					SWTSkin skin = skinnedDialog.getSkin();

					SWTSkinObjectContainer cont = target.buildStandAlone((SWTSkinObjectContainer)skin.getSkinObject( "content-area" ));

					if ( cont != null ){

						Object ds = target.getDatasource();

						if ( ds instanceof Object[]){

							Object[] temp = (Object[])ds;

							if ( temp.length > 0 ){

								ds = temp[0];
							}
						}

						String ds_str = "";

						if ( ds instanceof Download ){

							ds_str = ((Download)ds).getName();

						}else if ( ds instanceof DownloadManager ){

							ds_str = ((DownloadManager)ds).getDisplayName();
						}

						skinnedDialog.setTitle( target.getTitle() + (ds_str.length()==0?"":(" - " + ds_str )));

						skinnedDialog.open();

					}else{

						skinnedDialog.close();
					}
				}
			});
		}
	}
	
	
	@Override
	public void fillMenu(Menu menu, final MdiEntry entry, String menuID) {

		super.fillMenu(menu, entry, menuID);

		if ( entry != null ){
			com.biglybt.pif.ui.menus.MenuItem[] menu_items = MenuItemManager.getInstance().getAllAsArray(entry.getId() + "._end_");

			if ( menu_items.length > 0 ){

				MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
						new MenuBuildUtils.MenuItemPluginMenuControllerImpl(new Object[] {
							entry
						}));
			}
		}
	}


	@Override
	public String getMenuIdPrefix() {
		return props_prefix + ".";
	}
}
