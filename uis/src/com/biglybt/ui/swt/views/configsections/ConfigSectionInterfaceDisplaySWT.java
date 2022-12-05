/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.configsections;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionInterfaceDisplaySWT
	extends ConfigSectionImpl
{
	public static final String REFID_SECTION_SIDEBAR = "section-sidebar";
	public static final String REFID_SECTION_TOOLBAR = "section-toolbar";

	public static final String SECTION_ID = "display";

	public ConfigSectionInterfaceDisplaySWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);
		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals(
				"az3");

		// Group: various stuff
		/////////

		List<Parameter> listVarious = new ArrayList<>();

		add(new BooleanParameterImpl("Show Download Basket",
				"ConfigView.section.style.showdownloadbasket"), listVarious);

		add(new BooleanParameterImpl("suppress_file_download_dialog",
				"ConfigView.section.interface.display.suppress.file.download.dialog"),
				listVarious);
		
		add(new BooleanParameterImpl("Suppress File Move Dialog",
				"ConfigView.section.interface.display.suppress.file.move.dialog"),
				listVarious);

		add(new BooleanParameterImpl("Suppress Sharing Dialog",
				"ConfigView.section.interface.display.suppress.sharing.dialog"),
				listVarious);

		add(new BooleanParameterImpl("show_torrents_menu",
				"Menu.show.torrent.menu"), listVarious);

		if (!Constants.isUnix) {
			// TextWithHistory issues on Linux
			add(new BooleanParameterImpl("mainwindow.search.history.enabled",
					"search.history.enable"), listVarious);
		}

		if (Constants.isWindowsXP) {
			BooleanParameterImpl enableXPStyle = new BooleanParameterImpl(
					"windows.enable.xpstyle", "ConfigView.section.style.enableXPStyle");
			add(enableXPStyle, listVarious);

			boolean enabled = false;
			boolean valid = false;
			try {
				File f = new File(
						System.getProperty("java.home") + "\\bin\\javaw.exe.manifest");
				if (f.exists()) {
					enabled = true;
				}
				f = FileUtil.getApplicationFile("javaw.exe.manifest");
				if (f.exists()) {
					valid = true;
				}
			} catch (Exception e) {
				Debug.printStackTrace(e);
				valid = false;
			}
			enableXPStyle.setEnabled(valid);
			enableXPStyle.setValue(enabled);
			enableXPStyle.addListener(param -> {
				//In case we enable the XP Style
				if (enableXPStyle.getValue()) {
					try {
						File fDest = new File(
								System.getProperty("java.home") + "\\bin\\javaw.exe.manifest");
						File fOrigin = new File("javaw.exe.manifest");
						if (!fDest.exists() && fOrigin.exists()) {
							FileUtil.copyFile(fOrigin, fDest);
						}
					} catch (Exception e) {
						Debug.printStackTrace(e);
					}
				} else {
					try {
						File fDest = new File(
								System.getProperty("java.home") + "\\bin\\javaw.exe.manifest");
						fDest.delete();
					} catch (Exception e) {
						Debug.printStackTrace(e);
					}
				}
			});
		}

		if (Constants.isOSX) {
			add(new BooleanParameterImpl("enable_small_osx_fonts",
					"ConfigView.section.style.osx_small_fonts"), listVarious);
		}

		// Reuse the labels of the other menu actions.
		if (PlatformManagerFactory.getPlatformManager().hasCapability(
				PlatformManagerCapabilities.ShowFileInBrowser)) {
			BooleanParameterImpl paramShowParentFolder = new BooleanParameterImpl(
					"MyTorrentsView.menu.show_parent_folder_enabled", "");
			add(paramShowParentFolder, listVarious);
			paramShowParentFolder.setLabelText(MessageText.getString(
					"ConfigView.section.style.use_show_parent_folder", new String[] {
						MessageText.getString("MyTorrentsView.menu.open_parent_folder"),
						MessageText.getString("MyTorrentsView.menu.explore"),
					}));

			if (Constants.isOSX) {
				add(new BooleanParameterImpl("FileBrowse.usePathFinder",
						"ConfigView.section.style.usePathFinder"), listVarious);
			}
		}

		BooleanParameterImpl paramEnableForceDPI = new BooleanParameterImpl(
				"enable.ui.forceDPI", "ConfigView.section.style.forceDPI");
		add(paramEnableForceDPI, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl forceDPI = new IntParameterImpl("Force DPI", null, 0,
				Integer.MAX_VALUE);
		add(forceDPI, Parameter.MODE_INTERMEDIATE);

		add("pgForceDPI", new ParameterGroupImpl(null, paramEnableForceDPI,
				forceDPI).setNumberOfColumns2(2), listVarious);

		paramEnableForceDPI.addEnabledOnSelection(forceDPI);

		if (Constants.isWindows) {
			add(new StringParameterImpl("Ignore Icon Exts",
					"ConfigView.label.ignore.icon.exts"), Parameter.MODE_INTERMEDIATE,
					listVarious);
		}

		add(new BooleanParameterImpl("Disable All Tooltips",
				"ConfigView.section.style.disable.all.tt"), listVarious);
		
		add(new BooleanParameterImpl("Tidy Close With Progress",
				"ConfigView.section.style.tidy.close.with.progress"), listVarious);
	
		add(new ParameterGroupImpl("label.various", listVarious));

		// Group: toolbar
		/////////

		List<Parameter> listToolbar = new ArrayList<>();

		// row 1

		if (!isAZ3) {
			add(new BooleanParameterImpl("IconBar.enabled",
					"ConfigView.section.style.showiconbar"), listToolbar);
		}

		// row 2
		List<Parameter> listToolbarItems = new ArrayList<>();

		if (isAZ3) {
			add(new BooleanParameterImpl("IconBar.visible.play", "iconBar.stream"),
					listToolbarItems);
		}

		add(new BooleanParameterImpl("IconBar.visible.run", "iconBar.run"),
				listToolbarItems);

		if (!isAZ3) {
			add(new BooleanParameterImpl("IconBar.visible.top", "iconBar.top"),
					listToolbarItems);
		}

		add(new BooleanParameterImpl("IconBar.visible.up", "iconBar.up"),
				listToolbarItems);

		add(new BooleanParameterImpl("IconBar.visible.down", "iconBar.down"),
				listToolbarItems);

		if (!isAZ3) {
			add(new BooleanParameterImpl("IconBar.visible.bottom", "iconBar.bottom"),
					listToolbarItems);
		}
		
			// back forward
		
		add(new BooleanParameterImpl(
				"IconBar.visible." + TorrentUtil.BF_ITEM_BACK,
				"label.back"), listToolbarItems);
		
		add(new BooleanParameterImpl(
				"IconBar.visible." + TorrentUtil.BF_ITEM_FORWARD,
				"label.forward"), listToolbarItems);
	
			// other toolbar items

		add(new BooleanParameterImpl(
				"IconBar.visible." + TorrentUtil.TU_ITEM_RECHECK,
				"MyTorrentsView.menu.recheck"), listToolbarItems);

		add(new BooleanParameterImpl(
				"IconBar.visible." + TorrentUtil.TU_ITEM_CHECK_FILES,
				"MyTorrentsView.menu.checkfilesexist"), listToolbarItems);

		if (isAZ3) {
			add(new BooleanParameterImpl(
					"IconBar.visible." + TorrentUtil.TU_ITEM_SHOW_SIDEBAR,
					"v3.MainWindow.menu.view.sidebar"), listToolbarItems);
		}

		ParameterGroupImpl pgToolbarItems = new ParameterGroupImpl(null,
				listToolbarItems).setNumberOfColumns2(0);
		add("pgToolbarItems", pgToolbarItems, listToolbar);
		
		// row 3

		add(new BooleanParameterImpl("IconBar.start.stop.separate",
				"ConfigView.section.style.start.stop.separate"), listToolbar);

		ParameterGroupImpl pgToolbar = new ParameterGroupImpl(
				"MainWindow.menu.view.iconbar", listToolbar);
		add(pgToolbar);

		pgToolbar.setReferenceID( REFID_SECTION_TOOLBAR );

		// Group: sidebar
		/////////

		if (isAZ3) {

			List<Parameter> listSideBar = new ArrayList<>();

			add(new BooleanParameterImpl("Show Side Bar", "sidebar.show"),
					listSideBar);

			add(new IntParameterImpl("Side Bar Top Level Gap",
					"sidebar.top.level.gap", 0, 5), listSideBar);

			// top level order

			String orderStr = "";

			for (int i = 0; i < MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT.length; i++) {
				orderStr += (orderStr.isEmpty() ? "" : ", ") + (i + 1) + "="
						+ MessageText.getString("sidebar."
								+ MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT[i]);
			}

			StringParameterImpl order = new StringParameterImpl(
					"Side Bar Top Level Order", "");
			add(order, listSideBar);
			order.setSuffixLabelText(orderStr);
			order.setLabelText(
					MessageText.getString("sidebar.header.order", new String[] {
						""
					}));

			BooleanParameterImpl sso = new BooleanParameterImpl(
					"Show Options In Side Bar", "sidebar.show.options");
			add(sso, listSideBar);

			BooleanParameterImpl showNew = new BooleanParameterImpl(
					"Show New In Side Bar", "sidebar.show.new");
			add(showNew, listSideBar);

			showNew.addListener(p -> {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				if (showNew.getValue()) {
					mdi.loadEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_UNOPENED,
							false);
				} else {
					mdi.closeEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_UNOPENED);
				}
			});

			BooleanParameterImpl showDL = new BooleanParameterImpl(
					"Show Downloading In Side Bar", "sidebar.show.downloading");
			add(showDL, listSideBar);

			showDL.addListener(p -> {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				if (showDL.getValue()) {
					mdi.loadEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_DL, false);
				} else {
					mdi.closeEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_DL);
				}
			});

			BooleanParameterImpl hideicon = new BooleanParameterImpl(
					"Side Bar Hide Left Icon", "sidebar.hide.icon");
			add(hideicon, listSideBar);

			String[] cp_labs = {
				MessageText.getString("sb.close.indicator.left"),
				MessageText.getString("sb.close.right"),
				MessageText.getString("sb.close.never"),
			};

			add(new IntListParameterImpl("Side Bar Close Position",
					"sb.close.icon.position", new int[] {
						0,
						1,
						2
					}, cp_labs), listSideBar);

			if (!Utils.isGTK) {

				BooleanParameterImpl indent = new BooleanParameterImpl(
						"Side Bar Indent Expanders", "sidebar.indent.expanders");
				add(indent, listSideBar);

				BooleanParameterImpl compact = new BooleanParameterImpl(
						"Side Bar Compact View", "sidebar.compact.view");
				add(compact, listSideBar);
			}

			ParameterGroupImpl group = add(new ParameterGroupImpl("v3.MainWindow.menu.view.sidebar", listSideBar));
			
			group.setReferenceID( REFID_SECTION_SIDEBAR );
		}

		// Group: status bar
		/////////

		List<Parameter> listStatusBar = new ArrayList<>();

		add(new BooleanParameterImpl("Status Area Show SR",
				"ConfigView.section.style.status.show_sr"), listStatusBar);
		add(new BooleanParameterImpl("Status Area Show NAT",
				"ConfigView.section.style.status.show_nat"), listStatusBar);
		add(new BooleanParameterImpl("Status Area Show DDB",
				"ConfigView.section.style.status.show_ddb"), listStatusBar);
		add(new BooleanParameterImpl("Status Area Show IPF",
				"ConfigView.section.style.status.show_ipf"), listStatusBar);
		add(new BooleanParameterImpl("status.rategraphs",
				"ConfigView.section.style.status.show_rategraphs"), listStatusBar);

		add(new ParameterGroupImpl("ConfigView.section.style.status",
				listStatusBar));

		// Group: Views
		
		
		List<Parameter> listViews = new ArrayList<>();

		List<Parameter> blocksView = new ArrayList<>();

		add(new IntParameterImpl("blocks.view.max.active","blocks.view.max.active",100, Integer.MAX_VALUE ),
				Parameter.MODE_INTERMEDIATE, blocksView);

		ParameterGroupImpl blocksViewGroup = new ParameterGroupImpl("Pieces.column.blocks", blocksView);
		add( blocksViewGroup, listViews );
				 
		add( new ParameterGroupImpl("label.views", listViews));
		
		// Group: display units
		/////////

		List<Parameter> listUnits = new ArrayList<>();

		add(new BooleanParameterImpl("config.style.useSIUnits",
				"ConfigView.section.style.useSIUnits"), Parameter.MODE_INTERMEDIATE,
				listUnits);

		add(new BooleanParameterImpl("config.style.forceSIValues",
				"ConfigView.section.style.forceSIValues"), Parameter.MODE_INTERMEDIATE,
				listUnits);

		add(new BooleanParameterImpl("config.style.useUnitsRateBits",
				"ConfigView.section.style.useUnitsRateBits"),
				Parameter.MODE_INTERMEDIATE, listUnits);

		add(new BooleanParameterImpl("config.style.doNotUseGB",
				"ConfigView.section.style.doNotUseGB"), Parameter.MODE_INTERMEDIATE,
				listUnits);

		add(new BooleanParameterImpl("config.style.dataStatsOnly",
				"ConfigView.section.style.dataStatsOnly"), Parameter.MODE_INTERMEDIATE,
				listUnits);

		add(new BooleanParameterImpl("config.style.separateProtDataStats",
				"ConfigView.section.style.separateProtDataStats"),
				Parameter.MODE_INTERMEDIATE, listUnits);

		add(new BooleanParameterImpl("ui.scaled.graphics.binary.based",
				"ConfigView.section.style.scaleBinary"), Parameter.MODE_INTERMEDIATE,
				listUnits);

		add(new ParameterGroupImpl("ConfigView.section.style.units", listUnits));

		// Group: formatters
		/////////

		List<Parameter> listFormatters = new ArrayList<>();

		StringParameterImpl formatters = new StringParameterImpl(
				"config.style.formatOverrides", null);
		add(formatters, listFormatters);
		formatters.setMultiLine(3);

		add(new HyperlinkParameterImpl("ConfigView.label.general.formatters.link",
				Wiki.INTERFACE_FORMAT_OVERRIDES),
				Parameter.MODE_INTERMEDIATE, listFormatters);

		add(new InfoParameterImpl("config.style.formatOverrides.status",
				"GeneralView.label.status", null), Parameter.MODE_INTERMEDIATE,
				listFormatters);

		add(new ParameterGroupImpl("ConfigView.label.general.formatters",
				listFormatters), Parameter.MODE_INTERMEDIATE);

		// Group: external browser
		/////////

		List<Parameter> listExtBrowser = new ArrayList<>();

		add(new LabelParameterImpl("config.external.browser.info1"),
				Parameter.MODE_INTERMEDIATE, listExtBrowser);

		add(new LabelParameterImpl("config.external.browser.info2"),
				Parameter.MODE_INTERMEDIATE, listExtBrowser);

		// browser selection

		List<String> listBrowserKeys = new ArrayList<>();
		List<String> listBrowserLabels = new ArrayList<>();

		listBrowserKeys.add("system");
		listBrowserLabels.add(MessageText.getString("external.browser.system"));

		listBrowserKeys.add("manual");
		listBrowserLabels.add(MessageText.getString("external.browser.manual"));

		java.util.List<PluginInterface> pis = CoreFactory.getSingleton().getPluginManager().getPluginsWithMethod(
				"launchURL", new Class[] {
					URL.class,
					boolean.class,
					Runnable.class
				});

		String pi_names = ""; // for "Always use the %s for non-public content"

		for (PluginInterface pi : pis) {

			String pi_name = pi.getPluginName();

			pi_names += (pi_names.length() == 0 ? "" : "/") + pi_name;

			listBrowserKeys.add("plugin:" + pi.getPluginID());
			listBrowserLabels.add(pi_name);
		}

		StringListParameterImpl paramExtBrowserList = new StringListParameterImpl(
				"browser.external.id", "config.external.browser.select",
				listBrowserKeys.toArray(new String[0]),
				listBrowserLabels.toArray(new String[0]));
		add(paramExtBrowserList, Parameter.MODE_INTERMEDIATE, listExtBrowser);
		//paramExtBrowserList.setListType(StringListParameter.TYPE_RADIO);

		// Reset value if current no longer exists
		// This logic might be better in another location. If this class
		// is run before all plugins are added, we might inadvertently flip it back
		// to system
		String curBrowserChoice = paramExtBrowserList.getValue();
		boolean existingExists = false;
		for (String key : listBrowserKeys) {
			if (key.equals(curBrowserChoice)) {
				existingExists = true;
				break;
			}
		}
		if (!existingExists) {
			paramExtBrowserList.setValue(listBrowserKeys.get(0));
		}

		FileParameterImpl manualProg = new FileParameterImpl(
				"browser.external.prog", "config.external.browser.prog");
		add(manualProg, Parameter.MODE_INTERMEDIATE, listExtBrowser);

		paramExtBrowserList.addListener(param -> manualProg.setEnabled(
				paramExtBrowserList.getValue().equalsIgnoreCase(
						listBrowserKeys.get(1))));
		paramExtBrowserList.fireParameterChanged();

		// always use plugin for non-pub

		if (pis.size() > 0) {

			BooleanParameterImpl non_pub = new BooleanParameterImpl(
					"browser.external.non.pub", "");
			add(non_pub, Parameter.MODE_INTERMEDIATE, listExtBrowser);

			non_pub.setLabelText(MessageText.getString(
					"config.external.browser.non.pub", new String[] {
						pi_names
					}));
		}

		// test launch

		ActionParameterImpl paramTestExtBrowser = new ActionParameterImpl(
				"config.external.browser.test", "configureWizard.nat.test");
		add(paramTestExtBrowser, Parameter.MODE_INTERMEDIATE);

		StringParameterImpl paramTestURL = new StringParameterImpl(
				"browser.external.test.url", null);
		add(paramTestURL, Parameter.MODE_INTERMEDIATE);

		paramTestExtBrowser.addListener(param -> {
			paramTestExtBrowser.setEnabled(false);

			final String url_str = paramTestURL.getValue().trim();

			new AEThread2("async") {
				@Override
				public void run() {
					try {
						Utils.launch(url_str, true);

					} finally {
						paramTestExtBrowser.setEnabled(true);
					}
				}
			}.start();
		});

		add("pgExtBrowerInner",
				new ParameterGroupImpl(null, paramTestExtBrowser,
						paramTestURL).setNumberOfColumns2(2),
				Parameter.MODE_INTERMEDIATE, listExtBrowser);

		add(new ParameterGroupImpl("config.external.browser", listExtBrowser),
				Parameter.MODE_INTERMEDIATE);

		// Group: internal browser
		/////////

		List<Parameter> listIntBrowser = new ArrayList<>();

		add(new LabelParameterImpl("config.internal.browser.info1"),
				Parameter.MODE_ADVANCED, listIntBrowser);

		BooleanParameterImpl intbrow_disable = new BooleanParameterImpl(
				"browser.internal.disable", "config.browser.internal.disable");
		add(intbrow_disable, Parameter.MODE_ADVANCED, listIntBrowser);
		intbrow_disable.setSuffixLabelKey("config.browser.internal.disable.info");

		java.util.List<PluginInterface> httpProxyProviders = AEProxyFactory.getPluginHTTPProxyProviders(
				true);

		List<String> listProxyKeys = new ArrayList<>();
		List<String> listProxyLabels = new ArrayList<>();

		listProxyKeys.add("none");
		listProxyLabels.add(MessageText.getString("label.none"));

		for (PluginInterface pi : httpProxyProviders) {

			listProxyKeys.add("plugin:" + pi.getPluginID());
			listProxyLabels.add(pi.getPluginName());
		}

		StringListParameterImpl paramProxyChoices = new StringListParameterImpl(
				"browser.internal.proxy.id", "config.internal.browser.proxy.select",
				listProxyKeys.toArray(new String[0]),
				listProxyLabels.toArray(new String[0]));
		add(paramProxyChoices, Parameter.MODE_ADVANCED, listIntBrowser);

		paramProxyChoices.setSuffixLabelKey("config.internal.browser.info3");

		// Reset Proxy choice if current value isn't in list
		String curProxyChoice = paramProxyChoices.getValue();
		boolean existingProxyExists = false;
		for (String key : listProxyKeys) {
			if (key.equals(curProxyChoice)) {
				existingProxyExists = true;
				break;
			}
		}
		if (!existingProxyExists) {
			paramProxyChoices.setValue(listProxyKeys.get(0));
		}

		add(new ParameterGroupImpl("config.internal.browser", listIntBrowser),
				Parameter.MODE_ADVANCED);

		// Group: refresh
		/////////

		List<Parameter> listRefresh = new ArrayList<>();

		int[] values = {
			10,
			25,
			50,
			100,
			250,
			500,
			1000,
			2000,
			5000,
			10000,
			15000
		};
		
		String ms	= TimeFormatter.MS_SUFFIX;
		
		String secs = " " + TimeFormatter.getShortSuffix( TimeFormatter.TS_SECOND );
		
		String[] labels = {
			"10" + ms,
			"25" + ms,
			"50" + ms,
			"100" + ms,
			"250" + ms,
			"500" + ms,
			"1" + secs,
			"2" + secs,
			"5" + secs,
			"10" + secs,
			"15" + secs
		};
		
		add(new IntListParameterImpl("GUI Refresh",
				"ConfigView.section.style.guiUpdate", values, labels), listRefresh);

		add(new IntParameterImpl("Refresh When Inactive",
				"ConfigView.section.style.inactiveUpdate", 1, Integer.MAX_VALUE),
				listRefresh);

		add(new IntParameterImpl("Graphics Update",
				"ConfigView.section.style.graphicsUpdate", 1, Integer.MAX_VALUE),
				listRefresh);

		add(new ParameterGroupImpl("upnp.refresh.button", listRefresh));

	}

}
