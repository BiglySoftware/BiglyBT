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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;


public class ConfigSectionInterfaceDisplay implements UISWTConfigSection {
	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_INTERFACE;
	}

	@Override
	public String configSectionGetName() {
		return "display";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public int maxUserMode() {
		return 2;
	}


	@Override
	public Composite configSectionCreate(final Composite parent) {
    int userMode = COConfigurationManager.getIntParameter("User Mode");
		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

		Label label;
		GridLayout layout;
		GridData gridData;
		Composite cSection = new Composite(parent, SWT.NULL);
		cSection.setLayoutData(new GridData(GridData.FILL_BOTH));
		layout = new GridLayout();
		layout.numColumns = 1;
		cSection.setLayout(layout);

			// various stuff

		Group gVarious = new Group(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 2;
		gVarious.setLayout(layout);
		gVarious.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		gVarious.setText( MessageText.getString( "label.various" ));


		BooleanParameter bp = new BooleanParameter(gVarious, "Show Download Basket", "ConfigView.section.style.showdownloadbasket");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		bp.setLayoutData(gridData);
		
		bp = new BooleanParameter(gVarious, "suppress_file_download_dialog", "ConfigView.section.interface.display.suppress.file.download.dialog");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		bp.setLayoutData(gridData);
		
		bp = new BooleanParameter(gVarious, "Suppress Sharing Dialog", "ConfigView.section.interface.display.suppress.sharing.dialog");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		bp.setLayoutData(gridData);
		
		bp = new BooleanParameter(gVarious, "show_torrents_menu", "Menu.show.torrent.menu");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		bp.setLayoutData(gridData);

		if ( !Constants.isUnix ){
				// TextWithHistory issues on Linux
			bp = new BooleanParameter(gVarious, "mainwindow.search.history.enabled", "search.history.enable");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			bp.setLayoutData(gridData);
		}

		if (Constants.isWindowsXP) {
			final Button enableXPStyle = new Button(gVarious, SWT.CHECK);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			enableXPStyle.setLayoutData(gridData);
			
			Messages.setLanguageText(enableXPStyle, "ConfigView.section.style.enableXPStyle");

			boolean enabled = false;
			boolean valid = false;
			try {
				File f = new File(System.getProperty("java.home")
						+ "\\bin\\javaw.exe.manifest");
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
			enableXPStyle.setSelection(enabled);
			enableXPStyle.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event arg0) {
					//In case we enable the XP Style
					if (enableXPStyle.getSelection()) {
						try {
							File fDest = new File(System.getProperty("java.home")
									+ "\\bin\\javaw.exe.manifest");
							File fOrigin = new File("javaw.exe.manifest");
							if (!fDest.exists() && fOrigin.exists()) {
								FileUtil.copyFile(fOrigin, fDest);
							}
						} catch (Exception e) {
							Debug.printStackTrace(e);
						}
					} else {
						try {
							File fDest = new File(System.getProperty("java.home")
									+ "\\bin\\javaw.exe.manifest");
							fDest.delete();
						} catch (Exception e) {
							Debug.printStackTrace(e);
						}
					}
				}
			});
		}

		if (Constants.isOSX) {
			bp = new BooleanParameter(gVarious, "enable_small_osx_fonts", "ConfigView.section.style.osx_small_fonts");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			bp.setLayoutData(gridData);
		}

		// Reuse the labels of the other menu actions.
		if (PlatformManagerFactory.getPlatformManager().hasCapability(PlatformManagerCapabilities.ShowFileInBrowser)) {
			bp = new BooleanParameter(gVarious, "MyTorrentsView.menu.show_parent_folder_enabled", "ConfigView.section.style.use_show_parent_folder");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			bp.setLayoutData(gridData);
			
			Messages.setLanguageText(bp.getControl(), "ConfigView.section.style.use_show_parent_folder", new String[] {
				MessageText.getString("MyTorrentsView.menu.open_parent_folder"),
				MessageText.getString("MyTorrentsView.menu.explore"),
			});

			if (Constants.isOSX) {
				bp = new BooleanParameter(gVarious, "FileBrowse.usePathFinder",
						"ConfigView.section.style.usePathFinder");
				gridData = new GridData();
				gridData.horizontalSpan = 2;
				bp.setLayoutData(gridData);
			}
		}

		if (userMode > 0) {
	  		final BooleanParameter paramEnableForceDPI = new BooleanParameter(
	  				gVarious, "enable.ui.forceDPI", "ConfigView.section.style.forceDPI");
	  		paramEnableForceDPI.setLayoutData(new GridData());
				IntParameter forceDPI = new IntParameter(gVarious, "Force DPI", 0,
						Integer.MAX_VALUE);
				forceDPI.setLayoutData(new GridData());
				paramEnableForceDPI.setAdditionalActionPerformer(
						new ChangeSelectionActionPerformer(forceDPI.getControl()));
		}
		
		bp = new BooleanParameter(gVarious, "Disable All Tooltips", "ConfigView.section.style.disable.all.tt");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		bp.setLayoutData(gridData);

			// toolbar
		
		{
			Group gToolBar = new Group(cSection, SWT.NULL);
			Messages.setLanguageText(gToolBar, "MainWindow.menu.view.iconbar" );
			
			int gToolBarSpan = 5 + (isAZ3?2:2);
			layout = new GridLayout();
			layout.numColumns = gToolBarSpan;
			gToolBar.setLayout(layout);
			gToolBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			BooleanParameter enabled = null;
			
				// row 1
			
			if (!isAZ3) {
				enabled = new BooleanParameter(gToolBar, "IconBar.enabled", "ConfigView.section.style.showiconbar");
				
				GridData gd = new GridData();
				gd.horizontalSpan = gToolBarSpan;
				
				enabled.setLayoutData( gd );
			}

				// row 2
			
			if ( isAZ3 ){
				new BooleanParameter(gToolBar, "IconBar.visible.play", "iconBar.stream");
			}
			
			new BooleanParameter(gToolBar, "IconBar.visible.run", "iconBar.run");
			
			if ( !isAZ3 ){
				new BooleanParameter(gToolBar, "IconBar.visible.top", "iconBar.top");
			}
			
			new BooleanParameter(gToolBar, "IconBar.visible.up", "iconBar.up");
			
			new BooleanParameter(gToolBar, "IconBar.visible.down", "iconBar.down");
			
			if ( !isAZ3 ){
				new BooleanParameter(gToolBar, "IconBar.visible.bottom", "iconBar.bottom");
			}
			
			new BooleanParameter(gToolBar, "IconBar.visible." + TorrentUtil.TU_ITEM_RECHECK, "MyTorrentsView.menu.recheck");
			
			new BooleanParameter(gToolBar, "IconBar.visible." + TorrentUtil.TU_ITEM_CHECK_FILES, "MyTorrentsView.menu.checkfilesexist");
			
			if ( isAZ3 ){
				new BooleanParameter(gToolBar, "IconBar.visible." + TorrentUtil.TU_ITEM_SHOW_SIDEBAR, "v3.MainWindow.menu.view.sidebar");
			}
			
				// row 3
			
			BooleanParameter sss = new BooleanParameter(gToolBar, "IconBar.start.stop.separate", "ConfigView.section.style.start.stop.separate");
			
			GridData gd = new GridData();
			gd.horizontalSpan = gToolBarSpan;
			
			sss.setLayoutData( gd );
		}

			// sidebar

		if ( isAZ3 ){

			Group gSideBar = new Group(cSection, SWT.NULL);
			Messages.setLanguageText(gSideBar, "v3.MainWindow.menu.view.sidebar" );
			layout = new GridLayout();
			layout.numColumns = 3;
			gSideBar.setLayout(layout);
			gSideBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			new BooleanParameter(gSideBar, "Show Side Bar", "sidebar.show");
			label = new Label(gSideBar, SWT.NULL);
			label = new Label(gSideBar, SWT.NULL);
			
			label = new Label(gSideBar, SWT.NULL);
			Messages.setLanguageText(label, "sidebar.top.level.gap" );

			new IntParameter(gSideBar, "Side Bar Top Level Gap", 0, 5 );
			label = new Label(gSideBar, SWT.NULL);
			
				// top level order
			
			label = new Label(gSideBar, SWT.NULL);
			GridData gd = new GridData();
			gd.horizontalSpan = 2;
			label.setLayoutData( gd );
			
			String orderStr = "";
			
			for (int i=0;i<MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT.length;i++){
				orderStr += (orderStr.isEmpty()?"":", ") + 
						(i+1) + "=" +
						MessageText.getString( "sidebar." + MultipleDocumentInterface.SIDEBAR_HEADER_ORDER_DEFAULT[i]);
			}
			
			Messages.setLanguageText(label, "sidebar.header.order", new String[]{ orderStr });
			
			StringParameter order = new StringParameter(gSideBar, "Side Bar Top Level Order", false);
			order.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ));
			
			BooleanParameter sso = new BooleanParameter(gSideBar, "Show Options In Side Bar", "sidebar.show.options");
			gd = new GridData();
			gd.horizontalSpan = 3;
			sso.setLayoutData( gd );
			
			BooleanParameter showNew = new BooleanParameter(gSideBar, "Show New In Side Bar", "sidebar.show.new");
			label = new Label(gSideBar, SWT.NULL);
			label = new Label(gSideBar, SWT.NULL);
			
			showNew.addChangeListener(
				new ParameterChangeAdapter(){
					
					@Override
					public void parameterChanged(Parameter p, boolean caused_internally){
						if ( showNew.isSelected()){
							UIFunctionsManager.getUIFunctions().getMDI().loadEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_UNOPENED, false );
						}else{
							UIFunctionsManager.getUIFunctions().getMDI().closeEntry(  MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_UNOPENED );
						}
					}
				});
			
			BooleanParameter showDL = new BooleanParameter(gSideBar, "Show Downloading In Side Bar", "sidebar.show.downloading");
			label = new Label(gSideBar, SWT.NULL);
			label = new Label(gSideBar, SWT.NULL);

			showDL.addChangeListener(
					new ParameterChangeAdapter(){
						
						@Override
						public void parameterChanged(Parameter p, boolean caused_internally){
							if ( showDL.isSelected()){
								UIFunctionsManager.getUIFunctions().getMDI().loadEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_DL, false );
							}else{
								UIFunctionsManager.getUIFunctions().getMDI().closeEntry(  MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY_DL );
							}
						}
					});
			
			BooleanParameter hideicon = new BooleanParameter(gSideBar, "Side Bar Hide Left Icon", "sidebar.hide.icon");
			gd = new GridData();
			gd.horizontalSpan = 3;
			hideicon.setLayoutData( gd );

			
			label = new Label(gSideBar, SWT.NULL);
			Messages.setLanguageText(label, "sb.close.icon.position" );
			
			String[] cp_labs = {
					MessageText.getString( "sb.close.indicator.left" ),
					MessageText.getString( "sb.close.right" ),
					MessageText.getString( "sb.close.never" ),
			};
		
		    new IntListParameter(gSideBar, "Side Bar Close Position", cp_labs, new int[]{ 0, 1, 2 });
		    
		    if ( !Utils.isGTK ){
		    	
				BooleanParameter indent = new BooleanParameter(gSideBar, "Side Bar Indent Expanders", "sidebar.indent.expanders");
				gd = new GridData();
				gd.horizontalSpan = 3;
				indent.setLayoutData( gd );
	
				BooleanParameter compact = new BooleanParameter(gSideBar, "Side Bar Compact View", "sidebar.compact.view");
				gd = new GridData();
				gd.horizontalSpan = 3;
				compact.setLayoutData( gd );
		    }
		}

			// status bar

		Group cStatusBar = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(cStatusBar, "ConfigView.section.style.status");
		layout = new GridLayout();
		layout.numColumns = 1;
		cStatusBar.setLayout(layout);
		cStatusBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		new BooleanParameter(cStatusBar, "Status Area Show SR", "ConfigView.section.style.status.show_sr");
		new BooleanParameter(cStatusBar, "Status Area Show NAT",  "ConfigView.section.style.status.show_nat");
		new BooleanParameter(cStatusBar, "Status Area Show DDB", "ConfigView.section.style.status.show_ddb");
		new BooleanParameter(cStatusBar, "Status Area Show IPF", "ConfigView.section.style.status.show_ipf");
		new BooleanParameter(cStatusBar, "status.rategraphs", "ConfigView.section.style.status.show_rategraphs");

			// display units

		if (userMode > 0) {
			Group cUnits = new Group(cSection, SWT.NULL);
			Messages.setLanguageText(cUnits, "ConfigView.section.style.units");
			layout = new GridLayout();
			layout.numColumns = 1;
			cUnits.setLayout(layout);
			cUnits.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			new BooleanParameter(cUnits, "config.style.useSIUnits", "ConfigView.section.style.useSIUnits");

			new BooleanParameter(cUnits, "config.style.forceSIValues", "ConfigView.section.style.forceSIValues");

			new BooleanParameter(cUnits, "config.style.useUnitsRateBits", "ConfigView.section.style.useUnitsRateBits");

			new BooleanParameter(cUnits, "config.style.doNotUseGB", "ConfigView.section.style.doNotUseGB");

			new BooleanParameter(cUnits, "config.style.dataStatsOnly", "ConfigView.section.style.dataStatsOnly");

			new BooleanParameter(cUnits, "config.style.separateProtDataStats",
					"ConfigView.section.style.separateProtDataStats");

			new BooleanParameter(cUnits, "ui.scaled.graphics.binary.based", "ConfigView.section.style.scaleBinary");
		}

      	// formatters

        if ( userMode > 0 ){
	        Group formatters_group = new Group(cSection, SWT.NULL);
	        Messages.setLanguageText(formatters_group, "ConfigView.label.general.formatters");
	        layout = new GridLayout();
	        formatters_group.setLayout(layout);
	        formatters_group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	        StringAreaParameter formatters = new StringAreaParameter(formatters_group, "config.style.formatOverrides" );
	        gridData = new GridData(GridData.FILL_HORIZONTAL);
	        gridData.heightHint = formatters.getPreferredHeight( 3 );
	        formatters.setLayoutData( gridData );

	        Composite format_info = new Composite(formatters_group, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			format_info.setLayout(layout);
			format_info.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			new LinkLabel(format_info, "ConfigView.label.general.formatters.link",
					MessageText.getString(
							"ConfigView.label.general.formatters.link.url"));

			label = new Label(format_info, SWT.NULL);
			Messages.setLanguageText(label, "GeneralView.label.status");

	        InfoParameter	info_param = new InfoParameter(format_info, "config.style.formatOverrides.status" );
	        gridData = new GridData(GridData.FILL_HORIZONTAL);

	        info_param.setLayoutData( gridData );
        }

			// external browser

		if( userMode > 0 ){

			Group gExternalBrowser = new Group(cSection, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 1;
			gExternalBrowser.setLayout(layout);
			gExternalBrowser.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			gExternalBrowser.setText( MessageText.getString( "config.external.browser" ));
			label = new Label(gExternalBrowser, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.info1");
			label.setLayoutData(Utils.getWrappableLabelGridData(1, 0));
			label = new Label(gExternalBrowser, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.info2");
			label.setLayoutData(Utils.getWrappableLabelGridData(1, 0));

			// browser selection

			final java.util.List<String[]> browser_choices = new ArrayList<>();

			browser_choices.add(
					new String[]{ "system",  MessageText.getString( "external.browser.system" ) });
			browser_choices.add(
					new String[]{ "manual",  MessageText.getString( "external.browser.manual" ) });

			java.util.List<PluginInterface> pis =
					CoreFactory.getSingleton().getPluginManager().getPluginsWithMethod(
						"launchURL",
						new Class[]{ URL.class, boolean.class, Runnable.class });

			String pi_names = "";

			for ( PluginInterface pi: pis ){

				String pi_name = pi.getPluginName();

				pi_names += ( pi_names.length()==0?"":"/") + pi_name;

				browser_choices.add(
						new String[]{ "plugin:" + pi.getPluginID(),  pi_name });
			}

			final Composite cEBArea = new Composite(gExternalBrowser, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			cEBArea.setLayoutData(gridData);
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			cEBArea.setLayout(layout);

			label = new Label(cEBArea, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.select");

			final Composite cEB = new Group(cEBArea, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			cEB.setLayoutData(gridData);
			layout = new GridLayout();
			layout.numColumns = browser_choices.size();
			layout.marginHeight = 0;
			cEB.setLayout(layout);

			java.util.List<Button> buttons = new ArrayList<>();

			for ( int i=0;i< browser_choices.size(); i++ ){
				Button button = new Button ( cEB, SWT.RADIO );
				button.setText( browser_choices.get(i)[1] );
				button.setData("index", String.valueOf(i));

				buttons.add( button );
			}

			String existing = COConfigurationManager.getStringParameter( "browser.external.id", browser_choices.get(0)[0] );

			int existing_index = -1;

			for ( int i=0; i<browser_choices.size();i++){

				if ( browser_choices.get(i)[0].equals( existing )){

					existing_index = i;

					break;
				}
			}

			if ( existing_index == -1 ){

				existing_index = 0;

				COConfigurationManager.setParameter( "browser.external.id", browser_choices.get(0)[0] );
			}

			buttons.get(existing_index).setSelection( true );

			Messages.setLanguageText(new Label(cEBArea,SWT.WRAP), "config.external.browser.prog" );

			Composite manualArea = new Composite(cEBArea,SWT.NULL);
			layout = new GridLayout(2,false);
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			manualArea.setLayout( layout);
			manualArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			final Parameter manualProg = new FileParameter(manualArea, "browser.external.prog", new String[]{});

			manualProg.setEnabled( existing_index == 1 );

		    Listener radioListener =
			    	new Listener ()
			    	{
			    		@Override
					    public void
			    		handleEvent(
			    			Event event )
			    		{
						    Button button = (Button)event.widget;

						    if ( button.getSelection()){
					    		Control [] children = cEB.getChildren ();

					    		for (int j=0; j<children.length; j++) {
					    			 Control child = children [j];
					    			 if ( child != button && child instanceof Button) {
					    				 Button b = (Button) child;

					    				 b.setSelection (false);
					    			 }
					    		}

							    int index = Integer.parseInt((String)button.getData("index"));

							    COConfigurationManager.setParameter( "browser.external.id", browser_choices.get(index)[0] );

							    manualProg.setEnabled( index == 1 );
						    }
					    }
			    	};

			for ( Button b: buttons ){

				b.addListener( SWT.Selection, radioListener );
			}

				// always use plugin for non-pub

			if ( pis.size() > 0 ){

				Composite nonPubArea = new Composite(gExternalBrowser,SWT.NULL);
				layout = new GridLayout(2,false);
				layout.marginHeight = 0;
				nonPubArea.setLayout(layout);
				nonPubArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

				String temp = MessageText.getString( "config.external.browser.non.pub", new String[]{ pi_names });

				BooleanParameter non_pub = new BooleanParameter( nonPubArea, "browser.external.non.pub", "!" + temp + "!" );
			}

				// test launch

			Composite testArea = new Composite(gExternalBrowser,SWT.NULL);
			layout = new GridLayout(4,false);
			layout.marginHeight = 0;
			testArea.setLayout(layout);
			testArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			label = new Label(testArea, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.test");

		    final Button test_button = new Button(testArea, SWT.PUSH);

		    Messages.setLanguageText(test_button, "configureWizard.nat.test");

		    final Text test_url = new Text( testArea, SWT.BORDER );

			test_url.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			test_url.setText( Constants.URL_CLIENT_HOME );

		    test_button.addListener(SWT.Selection,
		    		new Listener()
					{
				        @Override
				        public void
						handleEvent(Event event)
				        {
				        	test_button.setEnabled( false );

				        	final String url_str = test_url.getText().trim();

				        	new AEThread2( "async" )
				        	{
				        		@Override
						        public void
				        		run()
				        		{
				        			try{
				        				Utils.launch( url_str, true );

				        			}finally{

				        				Utils.execSWTThread(
				        					new Runnable()
				        					{
				        						@Override
										        public void
				        						run()
				        						{
				        							if (! test_button.isDisposed()){

				        								test_button.setEnabled( true );
				        							}
				        						}
				        					});
				        			}
				        		}
				        	}.start();
				        }
				    });

			label = new Label(testArea, SWT.NULL);
			label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		}

			// internal browser

		if( userMode > 1) {
			Group gInternalBrowser = new Group(cSection, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 1;
			gInternalBrowser.setLayout(layout);
			gInternalBrowser.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			gInternalBrowser.setText( MessageText.getString( "config.internal.browser" ));

			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "config.internal.browser.info1");


			final BooleanParameter intbrow_disable = new BooleanParameter(gInternalBrowser, "browser.internal.disable", "config.browser.internal.disable");
			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 15;
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "config.browser.internal.disable.info");

			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "config.internal.browser.info3");

			java.util.List<PluginInterface> pis = AEProxyFactory.getPluginHTTPProxyProviders( true );

			final java.util.List<String[]> proxy_choices = new ArrayList<>();

			proxy_choices.add(
					new String[]{ "none",  MessageText.getString("label.none") });

			for ( PluginInterface pi: pis ){

				proxy_choices.add(
						new String[]{ "plugin:" + pi.getPluginID(),  pi.getPluginName() });

			}

			final Composite cIPArea = new Composite(gInternalBrowser, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			cIPArea.setLayoutData(gridData);
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			cIPArea.setLayout(layout);

			label = new Label(cIPArea, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "config.internal.browser.proxy.select");

			final Composite cIP = new Group(cIPArea, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			cIP.setLayoutData(gridData);
			layout = new GridLayout();
			layout.numColumns = proxy_choices.size();
			layout.marginHeight = 0;
			cIP.setLayout(layout);

			java.util.List<Button> buttons = new ArrayList<>();

			for ( int i=0;i< proxy_choices.size(); i++ ){
				Button button = new Button ( cIP, SWT.RADIO );
				button.setText( proxy_choices.get(i)[1] );
				button.setData("index", String.valueOf(i));

				buttons.add( button );
			}

			String existing = COConfigurationManager.getStringParameter( "browser.internal.proxy.id", proxy_choices.get(0)[0] );

			int existing_index = -1;

			for ( int i=0; i<proxy_choices.size();i++){

				if ( proxy_choices.get(i)[0].equals( existing )){

					existing_index = i;

					break;
				}
			}

			if ( existing_index == -1 ){

				existing_index = 0;

				COConfigurationManager.setParameter( "browser.internal.proxy.id", proxy_choices.get(0)[0] );
			}

			buttons.get(existing_index).setSelection( true );


		    Listener radioListener =
			    	new Listener ()
			    	{
			    		@Override
					    public void
			    		handleEvent(
			    			Event event )
			    		{
						    Button button = (Button)event.widget;

						    if ( button.getSelection()){
					    		Control [] children = cIP.getChildren ();

					    		for (int j=0; j<children.length; j++) {
					    			 Control child = children [j];
					    			 if ( child != button && child instanceof Button) {
					    				 Button b = (Button) child;

					    				 b.setSelection (false);
					    			 }
					    		}

							    int index = Integer.parseInt((String)button.getData("index"));

							    COConfigurationManager.setParameter( "browser.internal.proxy.id", proxy_choices.get(index)[0] );
						    }
					    }
			    	};

			for ( Button b: buttons ){

				b.addListener( SWT.Selection, radioListener );
			}
		}

			// refresh

		Group gRefresh = new Group(cSection, SWT.NULL);
		gRefresh.setText( MessageText.getString( "upnp.refresh.button" ));

		layout = new GridLayout();
		layout.numColumns = 2;
		gRefresh.setLayout(layout);
		gRefresh.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		label = new Label(gRefresh, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.style.guiUpdate");
		int[] values = { 10, 25, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 15000 };
		String[] labels = { "10 ms", "25 ms", "50 ms", "100 ms", "250 ms", "500 ms", "1 s", "2 s", "5 s", "10 s", "15 s" };
		new IntListParameter(gRefresh, "GUI Refresh", labels, values);

		label = new Label(gRefresh, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.style.inactiveUpdate");
		gridData = new GridData();
		IntParameter inactiveUpdate = new IntParameter(gRefresh, "Refresh When Inactive", 1, Integer.MAX_VALUE);
		inactiveUpdate.setLayoutData(gridData);

		label = new Label(gRefresh, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.style.graphicsUpdate");
		gridData = new GridData();
		IntParameter graphicUpdate = new IntParameter(gRefresh, "Graphics Update", 1, Integer.MAX_VALUE);
		graphicUpdate.setLayoutData(gridData);

		return cSection;
	}
}
