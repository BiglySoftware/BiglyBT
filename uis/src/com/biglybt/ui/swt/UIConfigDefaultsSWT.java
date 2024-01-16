/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt;

import java.io.File;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.mdi.MultipleDocumentInterface;

/**
 * @author TuxPaper
 * @created Nov 3, 2006
 *
 */
public class UIConfigDefaultsSWT
{

	/**
	 *
	 */
	public static void initialize() {
		ConfigurationDefaults def = ConfigurationDefaults.getInstance();
		def.addParameter("useCustomTab", true);
		def.addParameter("GUI Refresh", 500);
		def.addParameter("Graphics Update", 4);
		def.addParameter("ReOrder Delay", 4);
		def.addParameter("Refresh When Inactive", 2);
		def.addParameter("GUI Refresh Disable When Minimized", false );
		def.addParameter("Send Version Info", true);
		def.addParameter("Show Download Basket", false);
		def.addParameter("config.style.refreshMT", 0);
		def.addParameter("Open Details", false);
		def.addParameter("Open Seeding Details", false);
		def.addParameter("IconBar.enabled", true);
		
		def.addParameter("IconBar.visible.play", true);
		def.addParameter("IconBar.visible.run", true);
		def.addParameter("IconBar.visible.top", true);
		def.addParameter("IconBar.visible.up", true);
		def.addParameter("IconBar.visible.down", true);
		def.addParameter("IconBar.visible.bottom", true);
		def.addParameter("IconBar.start.stop.separate", false );
		
		def.addParameter("DefaultDir.BestGuess", false);
		def.addParameter("DefaultDir.AutoUpdate", true);
		def.addParameter("DefaultDir.AutoSave.AutoRename", true);
		def.addParameter("GUI_SWT_bFancyTab", true);
		def.addParameter("Colors.progressBar.override", false);
		def.addParameter("GUI_SWT_DisableAlertSliding", false);
		def.addParameter("NameColumn.showProgramIcon", !Constants.isWindowsVista);
		def.addParameter("RankColumn.showUpDownIcon.big", true );
		def.addParameter("RankColumn.showUpDownIcon.small", false );
		def.addParameter("SeedsColumn.showNetworkIcon", true );
		def.addParameter("PeersColumn.showNetworkIcon", true );

		def.addParameter("DND Always In Incomplete", false);

		def.addParameter("Message Popup Autoclose in Seconds", 15);

		//def.addParameter("Add URL Silently", false);	not used 11/30/2015 - see "Activate Window On External Download"

		def.addParameter("Reduce Auto Activate Window", false );

		def.addParameter("MyTorrents.SplitAt", 30);

		def.addParameter("Wizard Completed", false);
		def.addParameter("SpeedTest Completed", false);
		def.addParameter("Color Scheme.red", 0);
		def.addParameter("Color Scheme.green", 128);
		def.addParameter("Color Scheme.blue", 255);
		def.addParameter("Show Splash", true);
		def.addParameter("window.maximized", true);
		def.addParameter("window.rectangle", "");
		def.addParameter("Start Minimized", false);
		def.addParameter("Open Transfer Bar On Start", false);
		def.addParameter("Transfer Bar Show Icon Area", true );

        def.addParameter("Stats Graph Dividers", false);

		def.addParameter("Open Bar Incomplete", false);
		def.addParameter("Open Bar Complete", false);

		def.addParameter("Close To Tray", Constants.isLinux?false:true);
		def.addParameter("Minimize To Tray", false);

		def.addParameter("Status Area Show SR", true);
		def.addParameter("Status Area Show NAT", true);
		def.addParameter("Status Area Show DDB", true);
		def.addParameter("Status Area Show IPF", true);
		def.addParameter("Status Area Show RIP", true);

		def.addParameter("status.rategraphs", Utils.getUserMode() > 0);

		def.addParameter("GUI_SWT_share_count_at_close", 0 );

		def.addParameter("GUI_SWT_bOldSpeedMenu", false);

		def.addParameter("ui.toolbar.uiswitcher", false);
		def.addParameter("ui.systray.tooltip.enable", false);
		def.addParameter("ui.systray.tooltip.next.eta.enable", false);

		def.addParameter("Remember transfer bar location", true);

		if ( COConfigurationManager.getBooleanParameter( "Open Bar" )){

			COConfigurationManager.setParameter( "Open Bar Incomplete", true );
			COConfigurationManager.setParameter( "Open Bar Complete", true );

			COConfigurationManager.setParameter( "Open Bar", false );
		}

		def.addParameter("suppress_file_download_dialog", false);
		def.addParameter("Suppress File Move Dialog", false);
		def.addParameter("Suppress Sharing Dialog", false);
		def.addParameter("auto_remove_inactive_items", false);
		def.addParameter("show_torrents_menu", true);
		def.addParameter("mainwindow.search.history.enabled", true);
		def.addParameter("Disable All Tooltips", false);
		def.addParameter("Tidy Close With Progress", true );
		
		def.addParameter("Ignore Icon Exts", "" );
		
		if ( Constants.isWindows ){
			
			try{
				if ( Constants.getCurrentVersion().equals( "1.8.0.1_B35" )){
					
					COConfigurationManager.removeParameter( "Ignore Icon Exts" );
				}
				
				boolean found = false;
				
				File parent = new File( SystemProperties.getApplicationPath()).getParentFile();
				
				String test = "Alcohol Soft";
				
				if ( new File( parent, test ).exists()){
					
					found = true;
			
				}else{
					
					String str = parent.getAbsolutePath();
										
					if ( str.endsWith( "(x86)")){
						
						str = str.substring( 0, str.length() - 5  ).trim();
						
					}else{
						
						str += " (x86)";
					}
					
					if ( new File( str, test ).exists()){
						
						found = true;
					}
				}
				
				if ( !found ){
					
					PlatformManager	p_man = PlatformManagerFactory.getPlatformManager();
		
					if ( 	p_man.getPlatformType() == PlatformManager.PT_WINDOWS &&
							p_man.hasCapability( PlatformManagerCapabilities.TestNativeAvailability )){
						
						if ( p_man.testNativeAvailability( Constants.is64Bit?"AxShlex64.dll":"AxShlex.dll" )){
							
							found = true;
						}
					}
				}
				
				if ( found ){
					
					def.addParameter("Ignore Icon Exts", ".img;.mds;.iso;.bwt;.b5t;.b6t;.ccd;.isz;.cue;.cdi;.pdi;.nrg" );
				}
			}catch( Throwable e ){
				
			}
		}
		
		def.addParameter("MyTorrentsView.table.style", 0);

		def.addParameter("v3.topbar.height", 60);
		def.addParameter("v3.rightbar.width", 150);
		def.addParameter("v3.topbar.show.plugin", false);
		def.addParameter("pluginbar.visible", false);
		def.addParameter("rightbar.visible", false);
		def.addParameter("quick-links.visible", true);
		def.addParameter("ui.toolbar.uiswitcher", false);
		def.addParameter("Table.extendedErase", false);
		def.addParameter("Table.useTree", false);
		def.addParameter("Table.tooltip.disable", false);
		def.addParameter("Table.tooltip.truncate", true);
		def.addParameter("Table.sort.intuitive", false);
		def.addParameter("Table.filter.confusable", true );
		def.addParameter("Table Header Gradient Fill", true);
		def.addParameter("Dark Table Colors", false );
		def.addParameter("Dark Misc Colors", false );
		def.addParameter("Gradient Fill Selection", true );
		
		if ("az2".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui", "az3"))) {
			def.addParameter("v3.Show Welcome", false);

			def.addParameter("list.dm.dblclick", "1");
			def.addParameter(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY + ".viewmode", 1);
			def.addParameter(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_DL + "DL.viewmode", 1);
			def.addParameter(MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_CD + ".viewmode", 1);
			
			def.addParameter("Library.ShowTabsInTorrentView", 0 );
		}

		def.addParameter( "list.dm.dblclick", "-1" );
		
		def.addParameter( "browser.external.id", "system" );
		def.addParameter( "browser.internal.disable", false );
		def.addParameter( "browser.internal.proxy.id", "none" );

		def.addParameter( "Bar Transparency", 0 );

		def.addParameter( "Low Resource Silent Update Restart Enabled", true );

		def.addParameter( "Library.ShowTitle", true );
		def.addParameter( "Library.ShowCatButtons", false );
		def.addParameter( "Library.ShowCatButtons.CompOnly", false );
		def.addParameter( "Library.ShowTagButtons", true );
		def.addParameter( "Library.ShowTagButtons.FiltersOnly", false );
		def.addParameter( "Library.ShowTagButtons.ImageOverride", false );
		def.addParameter( "Library.ShowTagButtons.Align", 0);
		def.addParameter( "Library.ShowTagButtons.CompOnly", false );
		def.addParameter( "Library.ShowTagButtons.Inclusive", true );
		
		def.addParameter( "Library.showFancyMenu", true );
		def.addParameter( "Library.EnableSepColConfig", false );
		def.addParameter( "open.torrent.window.rename.on.tlf.change", true );
		
		// def.addParameter( "Library.TorrentViewSplitHorizontal", true );
		
		def.addParameter( "Library.TorrentViewSplitMode", 0 );
		
		if ( COConfigurationManager.hasParameter("Library.TorrentViewSplitHorizontal", true )){
			
			boolean old = COConfigurationManager.getBooleanParameter( "Library.TorrentViewSplitHorizontal");
			
			COConfigurationManager.setParameter( "Library.TorrentViewSplitMode", old?0:1 );
			
			COConfigurationManager.removeParameter( "Library.TorrentViewSplitHorizontal" );
		}
		
		def.addParameter( "Library.LaunchWebsiteInBrowser", true );
		def.addParameter( "Library.LaunchWebsiteInBrowserAnon", false );
		def.addParameter( "Library.LaunchWebsiteInBrowserDirList", false );

		def.addParameter( "ui.scaled.graphics.binary.based", false );

		def.addParameter( "Search Subs Row Height", 20 );
		def.addParameter( "Search View Is Web View", true );
		def.addParameter( "Search View Switch Hidden", false );

		def.addParameter( "tag.add.customize.default.checked", true );
		
		def.addParameter( "MyTorrents.status.sortorder", 0 );
		def.addParameter( "ui.forceDorkTray", false);
		
		if ( COConfigurationManager.hasParameter("PeersView.status.prioritysort", true )){
			
			boolean priority_sort = COConfigurationManager.getBooleanParameter("PeersView.status.prioritysort");
			
			COConfigurationManager.removeParameter( "PeersView.status.prioritysort" );
			
			if ( priority_sort ){
				
				COConfigurationManager.setParameter("MyTorrents.status.sortorder", 1 );
			}
		}
		
		int tid_def = 0;
		
		if ( COConfigurationManager.hasParameter("MyTorrents.status.sortorder", true )){
			
			tid_def = COConfigurationManager.getIntParameter("MyTorrents.status.sortorder");
			
			COConfigurationManager.removeParameter( "MyTorrents.status.sortorder" );
		}
			
		def.addParameter( "MyTorrents.status.change.fg", true );
		
		for ( String tid: TableManager.TABLE_MYTORRENTS_ALL ){
			def.addParameter( "MyTorrents.status.sortorder." + tid, 0 );
			
			if ( tid_def != 0 ){
				COConfigurationManager.setParameter( "MyTorrents.status.sortorder." + tid, tid_def );
			}
		}
		
		def.addParameter( "XferStats.show.samples", true );
		def.addParameter( "XferStats.local.show.rates", true );
		def.addParameter( "browser.external.non.pub", true );
		def.addParameter( "browser.external.test.url", Constants.URL_CLIENT_HOME );

	    def.addParameter( "Show Side Bar", true );
	    def.addParameter( "Side Bar Top Level Gap", 1 );
	    def.addParameter( "Show Options In Side Bar", false );
	    def.addParameter( "Show New In Side Bar", true );
	    def.addParameter( "Show Downloading In Side Bar", true );
	    def.addParameter( "Side Bar Close Position", 0 );
	    def.addParameter( "Side Bar Indent Expanders", true );
	    def.addParameter( "Side Bar Compact View", false );
	    def.addParameter( "Side Bar Hide Left Icon", false );
	    
		String orderDef = "";
		for (int i=0;i<MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT.length;i++){
			orderDef += (orderDef.isEmpty()?"":", ") + (i+1);
		}
		def.addParameter( "Side Bar Top Level Order", orderDef );
		def.addParameter( "Side Bar Double Click Action", 0 );
		
		def.addParameter( "Library.TagInTabBar", 1 );
		
		def.addParameter( "Peers View Show Local Peer", false);
		def.addParameter( "Pieces View Show Uploading", false);
		
		def.addParameter( ConfigKeys.File.ICFG_UI_ADDTORRENT_OPENOPTIONS_AUTO_CLOSE_SECS, 0 );
		
		def.addParameter( "DownloadActivity.show.eta", true );
		
		def.addParameter( "blocks.view.max.active", 500 );
	}
}
