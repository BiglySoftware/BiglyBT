/*
 * File    : SystemTraySWT.java
 * Created : 2 avr. 2004
 * By      : Olivier
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
package com.biglybt.ui.swt.systray;


import java.io.File;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.widgets.*;
import org.json.simple.JSONObject;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;
import com.biglybt.core.tag.TagDownload;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.updater.UIUpdatableAlways;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.config.ConfigSectionFile;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.MenuBuildUtils.MenuBuilder;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.SelectableSpeedMenu;
import com.biglybt.ui.swt.views.configsections.ConfigSectionInterfaceSWT;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.util.JSONUtils;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.ui.config.ConfigSection;

/**
 * @author Olivier Chalouhi
 *
 */
public class SystemTraySWT
	implements UIUpdatableAlways, MessageTextListener
{
	private static SystemTraySWT	singleton;
	private final MenuBuilder menuBuilder;
	private final ParameterListener paramTooltipListener;
	private final ParameterListener paramToolipETAListener;
	private final String trayIconImageID;
	private long lastUnixVal = -1;

	public static synchronized boolean
	hasTray()
	{
		return singleton != null;
	}

	public static synchronized SystemTraySWT
	getTray()
	{
		if ( singleton == null ){

			singleton = new SystemTraySWT();
		}

		return( singleton );
	}

	protected static Core core = null;

	Display display;

	UIFunctionsSWT uiFunctions;

	TrayDelegate tray;

	TrayItemDelegate trayItem;

	Menu menu;

	protected GlobalManager gm = null;

	private String seedingKeyVal;
	private String downloadingKeyVal;
	private String etaKeyVal;
	private String dlAbbrKeyVal;
	private String ulAbbrKeyVal;
	private String alertsKeyVal;

	long interval = 0;

	protected boolean enableTooltip;
	protected boolean enableTooltipNextETA;

	private SystemTraySWT() {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				SystemTraySWT.core = core;
				gm = core.getGlobalManager();
			}
		});

		paramTooltipListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				enableTooltip = COConfigurationManager.getBooleanParameter(parameterName);
				if (enableTooltip) {
					MessageText.addAndFireListener(SystemTraySWT.this);
					interval = 0;
				} else {
					MessageText.removeListener(SystemTraySWT.this);
					if (trayItem != null && !trayItem.isDisposed()) {
						Utils.setTT(trayItem,null);
					}
				}
			}
		};

		paramToolipETAListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				enableTooltipNextETA = COConfigurationManager.getBooleanParameter(parameterName);
				interval = 0;
			}
		};

		COConfigurationManager.addAndFireParameterListener(
				"ui.systray.tooltip.enable", paramTooltipListener);
		COConfigurationManager.addAndFireParameterListener(
				"ui.systray.tooltip.next.eta.enable",
				paramToolipETAListener);

		uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		display = Utils.getDisplay();

		tray = TrayDelegateFactory.getTray(display);
		trayItem = TrayDelegateFactory.createTrayItem(tray);

		File imageFile = new File(SystemProperties.getApplicationPath(), "biglybt-lightgray.svg");
		trayIconImageID = Constants.isOSX ? "osx_tray" : Constants.isUnix ? "nix_tray" : "logo32";
		trayItem.setImage(trayIconImageID, imageFile);

		trayItem.setVisible(true);

		menu = new Menu(uiFunctions.getMainShell(), SWT.POP_UP);

		menuBuilder = new MenuBuilder() {
			@Override
			public void buildMenu(Menu root_menu, MenuEvent menuEvent) {
				fillMenu(menu);
			}
		};

		trayItem.setMenu(menu, menuBuilder);


		trayItem.addListener(SWT.DefaultSelection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				showMainWindow();
			}
		});


		trayItem.addListener(SWT.Selection, new Listener() {
			long lastTime = 0;

			@Override
			public void handleEvent(Event arg0) {
				// Bug in Windows (seems to have started around SWT 3.3 Release
				// Candidates) where double click isn't interpreted as DefaultSelection
				// Since we "know" SWT.Selection is actually a mouse down, check
				// if two mouse downs happen in a short timespan and fake a
				// DefaultSelection
				if (Constants.isWindows) {
					long now = SystemTime.getCurrentTime();
					if (now - lastTime < 200) {
						showMainWindow();
					} else {
						lastTime = now;
					}
				} else if (Constants.isOSX) {
					menu.setVisible(true);
				}
			}
		});

		trayItem.addListener(SWT.MenuDetect, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				menu.setVisible(true);
			}
		});

		uiFunctions.getUIUpdater().addUpdater(this);
	}

	public void fillMenu(final Menu menu) {

		final MenuItem itemShow = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemShow, "SystemTray.menu.show");

		new MenuItem(menu, SWT.SEPARATOR);

		final MenuItem itemAddTorrent = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemAddTorrent,
				"menu.open.torrent");

		new MenuItem(menu, SWT.SEPARATOR);

		final MenuItem itemCloseAll = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemCloseAll,
				"SystemTray.menu.closealldownloadbars");

		final MenuItem itemShowGlobalTransferBar = new MenuItem(menu, SWT.CHECK);
		Messages.setLanguageText(itemShowGlobalTransferBar,
			"SystemTray.menu.open_global_transfer_bar");

		new MenuItem(menu, SWT.SEPARATOR);

		com.biglybt.pif.ui.menus.MenuItem[] menu_items;
		menu_items = MenuItemManager.getInstance().getAllAsArray("systray");
		if (menu_items.length > 0) {
			MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true, MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
			new MenuItem(menu, SWT.SEPARATOR);
		}

		createUploadLimitMenu(menu);
		createDownloadLimitMenu(menu);

		new MenuItem(menu, SWT.SEPARATOR);

		final MenuItem itemStartAll = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemStartAll, "SystemTray.menu.startalltransfers");

		final MenuItem itemStopAll = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemStopAll, "SystemTray.menu.stopalltransfers");

		final MenuItem itemPause = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemPause, "SystemTray.menu.pausetransfers");

		final MenuItem itemResume = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemResume, "SystemTray.menu.resumetransfers");

		new MenuItem(menu, SWT.SEPARATOR);

		final Menu optionsMenu = new Menu(menu.getShell(), SWT.DROP_DOWN);

		final MenuItem optionsItem = new MenuItem(menu, SWT.CASCADE);

		Messages.setLanguageText( optionsItem, "tray.options" );

		optionsItem.setMenu(optionsMenu);

		final MenuItem itemShowToolTip = new MenuItem(optionsMenu, SWT.CHECK);
		Messages.setLanguageText(itemShowToolTip,"show.tooltip.label");

		final MenuItem itemMoreOptions = new MenuItem(optionsMenu, SWT.PUSH);
		Messages.setLanguageText(itemMoreOptions,"label.more.dot");

		new MenuItem(menu, SWT.SEPARATOR);

		final MenuItem itemExit = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(itemExit, "SystemTray.menu.exit");

		itemShow.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				showMainWindow();
			}
		});

		itemAddTorrent.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				uiFunctions.openTorrentWindow();
			}
		});

		itemStartAll.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if (gm == null) {
					return;
				}
				gm.startAllDownloads();
			}
		});

		itemStopAll.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				ManagerUtils.asyncStopAll();
			}
		});

		itemPause.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				ManagerUtils.asyncPause();
			}
		});

		itemResume.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if (gm == null) {
					return;
				}
				gm.resumeDownloads();
			}
		});

		itemPause.setEnabled(gm != null && gm.canPauseDownloads());
		itemResume.setEnabled(gm != null && gm.canResumeDownloads());

		itemCloseAll.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				uiFunctions.closeDownloadBars();
			}
		});

		itemShowGlobalTransferBar.setSelection(uiFunctions.isGlobalTransferBarShown());
		itemShowGlobalTransferBar.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				if (uiFunctions.isGlobalTransferBarShown()) {
					uiFunctions.closeGlobalTransferBar();
				}
				else {
					uiFunctions.showGlobalTransferBar();
				}
			}
		});

		itemShowToolTip.setSelection(enableTooltip);
		itemShowToolTip.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				COConfigurationManager.setParameter( "ui.systray.tooltip.enable", itemShowToolTip.getSelection());
			}
		});

		itemMoreOptions.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if (uif != null) {
					showMainWindow();
					
					JSONObject args = new JSONObject();

					args.put( "select", ConfigSectionInterfaceSWT.REFID_INTERFACE_SYSTRAY );
					
					String args_str = JSONUtils.encodeToJSON( args );
					
					uif.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG,
							ConfigSection.SECTION_INTERFACE + args_str );
				}
			}
		});

		itemMoreOptions.setEnabled( uiFunctions.getVisibilityState() != UIFunctions.VS_TRAY_ONLY );

		itemExit.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				// User got a stack overflow (all SWT code) because of this dispose,
				// so execute it outside of the selection trigger and hope it doesn't
				// overflow there.
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						uiFunctions.dispose(false);
					}
				});
			}
		});
	}

	/**
	 * Creates the global upload limit context menu item
	 * @param parent The system tray contextual menu
	 */
	private final void createUploadLimitMenu(final Menu parent) {
		if ( gm == null ){
			return;
		}
		final MenuItem uploadSpeedItem = new MenuItem(parent, SWT.CASCADE);
		uploadSpeedItem.setText(MessageText.getString("GeneralView.label.maxuploadspeed"));

		final Menu uploadSpeedMenu = new Menu(uiFunctions.getMainShell(),
				SWT.DROP_DOWN);

		uploadSpeedMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				SelectableSpeedMenu.generateMenuItems(uploadSpeedMenu, core, gm, true);
			}
		});

		uploadSpeedItem.setMenu(uploadSpeedMenu);
	}

	/**
	 * Creates the global download limit context menu item
	 * @param parent The system tray contextual menu
	 */
	private final void createDownloadLimitMenu(final Menu parent) {
		if ( gm == null ){
			return;
		}
		final MenuItem downloadSpeedItem = new MenuItem(parent, SWT.CASCADE);
		downloadSpeedItem.setText(MessageText.getString("GeneralView.label.maxdownloadspeed"));

		final Menu downloadSpeedMenu = new Menu(uiFunctions.getMainShell(),
				SWT.DROP_DOWN);

		downloadSpeedMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				SelectableSpeedMenu.generateMenuItems(downloadSpeedMenu, core, gm, false);
			}
		});

		downloadSpeedItem.setMenu(downloadSpeedMenu);
	}

	public void dispose() {
		COConfigurationManager.removeParameterListener(
				"ui.systray.tooltip.enable", paramTooltipListener);
		COConfigurationManager.removeParameterListener(
				"ui.systray.tooltip.next.eta.enable",
				paramToolipETAListener);

		uiFunctions.getUIUpdater().removeUpdater(this);
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (trayItem != null && !trayItem.isDisposed()) {
					trayItem.dispose();
				}

				if (trayIconImageID != null) {
					ImageLoader imageLoader = ImageLoader.getInstance();
					imageLoader.releaseImage(trayIconImageID);
				}
			}
		});

		synchronized( SystemTraySWT.class ){

			singleton = null;
		}
	}

	@Override
	public void updateUI(){
		updateUI(true);
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI(boolean is_visible) {
		if (interval++ % 10 > 0) {
			return;
		}
		if (trayItem.isDisposed()) {
			uiFunctions.getUIUpdater().removeUpdater(this);
			return;
		}
		if (core == null || !core.isStarted()) {
			return;
		}

		if (enableTooltip) {
	  		GlobalManagerStats stats = gm.getStats();

	  		StringBuilder toolTip = new StringBuilder();

	  		int seeding 	= 0;
	  		int downloading = 0;

  			DownloadManager	next_download 			= null;
  			long			next_download_eta	 	= Long.MAX_VALUE;

	  		TagManager tm = TagManagerFactory.getTagManager();

	  		if ( tm != null && tm.isEnabled()){

	  			TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_STATE );

	  			if ( tt != null ){

	  				TagDownload	dl_tag = (TagDownload)tt.getTag( 1 );

	  				downloading = dl_tag.getTaggedCount();
	  				seeding		= tt.getTag( 2 ).getTaggedCount();

	  				if ( enableTooltipNextETA && downloading > 0 ){

	  					for ( DownloadManager dl: dl_tag.getTaggedDownloads()){

	  						DownloadManagerStats	dl_stats = dl.getStats();

	  						long eta = dl_stats.getSmoothedETA();

	  						if ( eta < next_download_eta ){

	  							next_download_eta		= eta;
	  							next_download			= dl;
	  						}
	  					}
	  				}
	  			}
	  		}else{
	  				// OMG this must be slow on 10k lists

	  			/*
		  		List<?> managers = gm.getDownloadManagers();
		  		for (int i = 0; i < managers.size(); i++) {
		  			DownloadManager manager = (DownloadManager) managers.get(i);
		  			int state = manager.getState();
		  			if (state == DownloadManager.STATE_DOWNLOADING)
		  				downloading++;
		  			if (state == DownloadManager.STATE_SEEDING)
		  				seeding++;
		  		}
		  		*/
	  		}

	  		String seeding_text 	= seedingKeyVal.replaceAll("%1", "" + seeding);
	  		String downloading_text = downloadingKeyVal.replaceAll("%1", "" + downloading);

	  		toolTip.append(seeding_text).append(downloading_text).append("\n");

	  		if ( next_download != null ){

	  			String dl_name = next_download.getDisplayName();

	  			if ( dl_name.length() > 80 ){

	  				dl_name = dl_name.substring( 0,  77 ) + "...";
	  			}

	  			dl_name = dl_name.replaceAll( "&", "&&" );

	  			toolTip.append( "  " );
	  			toolTip.append( dl_name );
	  			toolTip.append( ": " );
	  			toolTip.append( etaKeyVal );
	  			toolTip.append( "=" );
	  			toolTip.append( DisplayFormatters.formatETA( next_download_eta ));
	  			toolTip.append( "\n" );
	  		}

	  		toolTip.append(dlAbbrKeyVal).append(" ");

	  		toolTip.append(DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(
	  				stats.getDataReceiveRate(), stats.getProtocolReceiveRate()));

	  		toolTip.append(", ").append(ulAbbrKeyVal).append(" ");
	  		toolTip.append(DisplayFormatters.formatDataProtByteCountToKiBEtcPerSec(
	  				stats.getDataSendRate(), stats.getProtocolSendRate()));

	  		int alerts = Alerts.getUnviewedLogAlertCount();

	  		if ( alerts > 0 ){

	  			toolTip.append( "\n" );
	  			toolTip.append( alertsKeyVal.replaceAll("%1", "" + alerts));
	  		}

	  		Utils.setTT(trayItem,toolTip.toString());
		}


		if (!(tray instanceof TraySWT)) {
			GlobalManagerStats stats = gm.getStats();

			long l = (stats.getDataReceiveRate() + stats.getDataSendRate()) / 1024;
			if (l != lastUnixVal) {
				lastUnixVal = l;
				trayItem.setMenu(menu, menuBuilder);
			}
		}
		//Why should we refresh the image? it never changes ...
		//and is a memory bottleneck for some non-obvious reasons.
		//trayItem.setImage(ImageLoader.getInstance().getImage("logo16"));
		trayItem.setVisible(true);
	}

	private void showMainWindow() {
		uiFunctions.bringToFront(false);
		uiFunctions.getMainShell().forceActive();
		uiFunctions.getMainShell().forceFocus();
	}

	public void updateLanguage() {
		if (menu != null) {
			Messages.updateLanguageForControl(menu);
		}

		updateUI();
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return "SystemTraySWT";
	}

	@Override
	public void localeChanged(Locale oldLocale, Locale newLocale) {
		seedingKeyVal = MessageText.getString("SystemTray.tooltip.seeding");
		downloadingKeyVal = MessageText.getString("SystemTray.tooltip.downloading");
		if (!downloadingKeyVal.startsWith(" ")) {
			downloadingKeyVal = " " + downloadingKeyVal;
		}
		etaKeyVal		= MessageText.getString("TableColumn.header.eta" );
		dlAbbrKeyVal 	= MessageText.getString("ConfigView.download.abbreviated");
		ulAbbrKeyVal 	= MessageText.getString("ConfigView.upload.abbreviated");

		alertsKeyVal 	= MessageText.getString("label.alertnum");
	}
}
