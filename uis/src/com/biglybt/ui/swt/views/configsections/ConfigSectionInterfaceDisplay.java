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

import com.biglybt.core.CoreFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.core.proxy.AEProxyFactory;


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
		Utils.setLayoutData(cSection, new GridData(GridData.FILL_BOTH));
		layout = new GridLayout();
		layout.numColumns = 1;
		cSection.setLayout(layout);

			// various stuff

		Group gVarious = new Group(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 1;
		gVarious.setLayout(layout);
		Utils.setLayoutData(gVarious, new GridData(GridData.FILL_HORIZONTAL));

		gVarious.setText( MessageText.getString( "label.various" ));


		new BooleanParameter(gVarious, "Show Download Basket", "ConfigView.section.style.showdownloadbasket");

		if (!isAZ3) {
			new BooleanParameter(gVarious, "IconBar.enabled", "ConfigView.section.style.showiconbar");
		}

		new BooleanParameter(gVarious, "suppress_file_download_dialog", "ConfigView.section.interface.display.suppress.file.download.dialog");

		new BooleanParameter(gVarious, "Suppress Sharing Dialog", "ConfigView.section.interface.display.suppress.sharing.dialog");

		new BooleanParameter(gVarious, "show_torrents_menu", "Menu.show.torrent.menu");

		if ( !Constants.isUnix ){
				// TextWithHistory issues on Linux
			new BooleanParameter(gVarious, "mainwindow.search.history.enabled", "search.history.enable");
		}

		if (Constants.isWindowsXP) {
			final Button enableXPStyle = new Button(gVarious, SWT.CHECK);
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
			new BooleanParameter(gVarious, "enable_small_osx_fonts", "ConfigView.section.style.osx_small_fonts");
		}

		// Reuse the labels of the other menu actions.
		if (PlatformManagerFactory.getPlatformManager().hasCapability(PlatformManagerCapabilities.ShowFileInBrowser)) {
			BooleanParameter bp = new BooleanParameter(gVarious, "MyTorrentsView.menu.show_parent_folder_enabled", "ConfigView.section.style.use_show_parent_folder");
			Messages.setLanguageText(bp.getControl(), "ConfigView.section.style.use_show_parent_folder", new String[] {
				MessageText.getString("MyTorrentsView.menu.open_parent_folder"),
				MessageText.getString("MyTorrentsView.menu.explore"),
			});

			if (Constants.isOSX) {
				new BooleanParameter(gVarious, "FileBrowse.usePathFinder",
						"ConfigView.section.style.usePathFinder");
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



			// sidebar

		if ( isAZ3 ){

			Group gSideBar = new Group(cSection, SWT.NULL);
			Messages.setLanguageText(gSideBar, "v3.MainWindow.menu.view.sidebar" );
			layout = new GridLayout();
			layout.numColumns = 2;
			gSideBar.setLayout(layout);
			Utils.setLayoutData(gSideBar, new GridData(GridData.FILL_HORIZONTAL));

			new BooleanParameter(gSideBar, "Show Side Bar", "sidebar.show");
			label = new Label(gSideBar, SWT.NULL);

			label = new Label(gSideBar, SWT.NULL);
			Messages.setLanguageText(label, "sidebar.top.level.gap" );

			new IntParameter(gSideBar, "Side Bar Top Level Gap", 0, 5 );

			new BooleanParameter(gSideBar, "Show Options In Side Bar", "sidebar.show.options");
			label = new Label(gSideBar, SWT.NULL);

		}

			// status bar

		Group cStatusBar = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(cStatusBar, "ConfigView.section.style.status");
		layout = new GridLayout();
		layout.numColumns = 1;
		cStatusBar.setLayout(layout);
		Utils.setLayoutData(cStatusBar, new GridData(GridData.FILL_HORIZONTAL));

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
			Utils.setLayoutData(cUnits, new GridData(GridData.FILL_HORIZONTAL));

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
	        Utils.setLayoutData(formatters_group, new GridData(GridData.FILL_HORIZONTAL));
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
			Utils.setLayoutData(format_info, new GridData(GridData.FILL_HORIZONTAL));

	    	new LinkLabel(format_info, "ConfigView.label.general.formatters.link", MessageText.getString( "ConfigView.label.general.formatters.link.url" ));

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
			Utils.setLayoutData(gExternalBrowser, new GridData(GridData.FILL_HORIZONTAL));

			gExternalBrowser.setText( MessageText.getString( "config.external.browser" ));
			label = new Label(gExternalBrowser, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.info1");
			Utils.setLayoutData(label, Utils.getWrappableLabelGridData(1, 0));
			label = new Label(gExternalBrowser, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.info2");
			Utils.setLayoutData(label, Utils.getWrappableLabelGridData(1, 0));

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
			Utils.setLayoutData(cEBArea, gridData);
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			cEBArea.setLayout(layout);

			label = new Label(cEBArea, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.select");

			final Composite cEB = new Group(cEBArea, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(cEB, gridData);
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
			Utils.setLayoutData(manualArea, new GridData(GridData.FILL_HORIZONTAL));

			final Parameter manualProg = new FileParameter(manualArea, "browser.external.prog","", new String[]{});

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
				Utils.setLayoutData(nonPubArea, new GridData(GridData.FILL_HORIZONTAL));

				String temp = MessageText.getString( "config.external.browser.non.pub", new String[]{ pi_names });

				BooleanParameter non_pub = new BooleanParameter( nonPubArea, "browser.external.non.pub", true, "!" + temp + "!" );
			}

				// test launch

			Composite testArea = new Composite(gExternalBrowser,SWT.NULL);
			layout = new GridLayout(4,false);
			layout.marginHeight = 0;
			testArea.setLayout(layout);
			Utils.setLayoutData(testArea, new GridData(GridData.FILL_HORIZONTAL));

			label = new Label(testArea, SWT.WRAP);
			Messages.setLanguageText(label, "config.external.browser.test");

		    final Button test_button = new Button(testArea, SWT.PUSH);

		    Messages.setLanguageText(test_button, "configureWizard.nat.test");

		    final Text test_url = new Text( testArea, SWT.BORDER );

		    Utils.setLayoutData(test_url, new GridData(GridData.FILL_HORIZONTAL));

		    test_url.setText( "http://www.vuze.com/" );

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
			Utils.setLayoutData(label, new GridData(GridData.FILL_HORIZONTAL));

		}

			// internal browser

		if( userMode > 1) {
			Group gInternalBrowser = new Group(cSection, SWT.NULL);
			layout = new GridLayout();
			layout.numColumns = 1;
			gInternalBrowser.setLayout(layout);
			Utils.setLayoutData(gInternalBrowser, new GridData(GridData.FILL_HORIZONTAL));

			gInternalBrowser.setText( MessageText.getString( "config.internal.browser" ));

			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label, "config.internal.browser.info1");


			final BooleanParameter intbrow_disable = new BooleanParameter(gInternalBrowser, "browser.internal.disable", "config.browser.internal.disable");
			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 15;
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label, "config.browser.internal.disable.info");

			label = new Label(gInternalBrowser, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);
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
			Utils.setLayoutData(cIPArea, gridData);
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 0;
			cIPArea.setLayout(layout);

			label = new Label(cIPArea, SWT.WRAP);
			gridData = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label, "config.internal.browser.proxy.select");

			final Composite cIP = new Group(cIPArea, SWT.WRAP);
			gridData = new GridData( GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(cIP, gridData);
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
		Utils.setLayoutData(gRefresh, new GridData(GridData.FILL_HORIZONTAL));

		label = new Label(gRefresh, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.style.guiUpdate");
		int[] values = { 10, 25, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 15000 };
		String[] labels = { "10 ms", "25 ms", "50 ms", "100 ms", "250 ms", "500 ms", "1 s", "2 s", "5 s", "10 s", "15 s" };
		new IntListParameter(gRefresh, "GUI Refresh", 1000, labels, values);

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
