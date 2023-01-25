/*
 * Created on Sep 13, 2004
 * Created by Olivier Chalouhi
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package com.biglybt.ui.swt.views.stats;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Monitor;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.impl.DownloadManagerRateController;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.speedmanager.*;
import com.biglybt.core.stats.transfer.LongTermStats;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.graphics.MultiPlotGraphic;
import com.biglybt.ui.swt.components.graphics.PingGraphic;
import com.biglybt.ui.swt.components.graphics.Plot3D;
import com.biglybt.ui.swt.components.graphics.Scale;
import com.biglybt.ui.swt.components.graphics.SpeedGraphic;
import com.biglybt.ui.swt.components.graphics.ValueFormater;
import com.biglybt.ui.swt.components.graphics.ValueSource;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.TransportStartpoint;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxySelector;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;

/**
 *
 */
public class TransferStatsView
	implements UISWTViewCoreEventListener, IViewRequiresPeriodicUpdates
{
	public static final String MSGID_PREFIX = "TransferStatsView";

	private static final int MAX_DISPLAYED_PING_MILLIS		= 1199;	// prevents us hitting 1200 and resulting in graph expanding to 1400
	private static final int MAX_DISPLAYED_PING_MILLIS_DISP	= 1200;	// tidy display

	private GlobalManager		global_manager;
	private GlobalManagerStats 	stats;
	private SpeedManager 		speedManager;

	private OverallStats totalStats;

	private CTabFolder mainPanel;
	private Composite currentPanel;
	private Composite historyPanel;

	private MultiPlotGraphic	history_mpg;
	
	private Composite blahPanel;
	private BufferedLabel asn,estUpCap,estDownCap;
	private BufferedLabel uploadBiaser;
	private BufferedLabel currentIP;

	private Composite 		connectionPanel;
	private BufferedLabel	upload_label, connection_label;
	private SpeedGraphic	upload_graphic;
	private SpeedGraphic	connection_graphic;

	private CTabFolder 			con_folder;
	private long				last_route_update;
	private Composite 			route_comp;
	private BufferedLabel[][]	route_labels 	= new BufferedLabel[0][0];
	private Map<String,Long>	route_last_seen	= new HashMap<>();

	private Composite generalPanel;
	private BufferedLabel totalLabel;
	private BufferedLabel nowUp, nowDown, sessionDown, sessionUp, session_ratio, sessionTime, totalDown, totalUp, total_ratio, totalTime;

	private Label socksState;
	private BufferedLabel socksCurrent, socksFails;
	private Label socksMore;

	private Group autoSpeedPanel;
	private StackLayout autoSpeedPanelLayout;
	private Composite autoSpeedInfoPanel;
	private Composite autoSpeedDisabledPanel;
	private PingGraphic pingGraph;

	private plotView[]	plot_views;
	private zoneView[] 	zone_views;

	private LimitToTextHelper limit_to_text = new LimitToTextHelper();

	private final DecimalFormat formatter = new DecimalFormat( "##.#" );

	private boolean	initialised;

	private UISWTView swtView;


  public TransferStatsView() {
	  CoreFactory.addCoreRunningListener(new CoreRunningListener() {
		  @Override
		  public void coreRunning(Core core) {
			  global_manager = core.getGlobalManager();
			  stats = global_manager.getStats();
			  speedManager = core.getSpeedManager();
			  totalStats = StatsFactory.getStats();
		  }
	  });
	  
	  pingGraph = PingGraphic.getInstance();
    
	  connection_graphic =
			  SpeedGraphic.getInstance(
				new Scale( false ),
				new ValueFormater()
				{
				    @Override
				    public String
				    format(int value)
				    {
				         return( String.valueOf( value ));
				    }
				});

	  upload_graphic =
			  SpeedGraphic.getInstance(
				new ValueFormater()
				{
				    @Override
				    public String
				    format(int value)
				    {
				         return DisplayFormatters.formatByteCountToKiBEtc(value);
				    }
				});
  }
  
  private void initialize(Composite composite) {

	mainPanel = new CTabFolder(composite,SWT.FLAT);
	GridLayout folderLayout = Utils.getSimpleGridLayout(1);
	mainPanel.setLayout(folderLayout);  
	
	CTabItem currentItem = new CTabItem( mainPanel, SWT.NULL );
	
	Messages.setLanguageText(currentItem, "label.current");
	
    currentPanel = new Composite(mainPanel,SWT.NULL);
    GridLayout currentLayout = Utils.getSimpleGridLayout(1);
    currentPanel.setLayout(currentLayout);
    
    currentItem.setControl( currentPanel);

    
	CTabItem historyItem = new CTabItem( mainPanel, SWT.NULL );
	
	Messages.setLanguageText(historyItem, "label.history");
	
    historyPanel = new Composite(mainPanel,SWT.NULL);
    GridLayout historyLayout = Utils.getSimpleGridLayout(1);
    historyPanel.setLayout(historyLayout);
    
    historyItem.setControl( historyPanel);
   
    mainPanel.setSelection( currentItem );
    
    CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (mainPanel == null || mainPanel.isDisposed()) {
							return;
						}
						createGeneralPanel();
						createConnectionPanel();
						createCapacityPanel();
						createAutoSpeedPanel();

						createHistoryPanel();
						
						initialised	= true;
					}
				});
			}
		});
  }

  private void createGeneralPanel() {
	generalPanel = new Composite(currentPanel, SWT.NULL);
	GridLayout outerLayout = Utils.getSimpleGridLayout(2);
	generalPanel.setLayout(outerLayout);
	GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
	generalPanel.setLayoutData(gridData);

	GridData generalStatsPanelGridData = new GridData(GridData.FILL_HORIZONTAL);
	generalStatsPanelGridData.grabExcessHorizontalSpace = true;

	Composite generalStatsPanel = Utils.createSkinnedComposite(generalPanel,SWT.BORDER, generalStatsPanelGridData );

    GridLayout panelLayout = new GridLayout();
    panelLayout.numColumns = 5;
    panelLayout.makeColumnsEqualWidth = false;
    generalStatsPanel.setLayout(panelLayout);


    Label lbl = new Label(generalStatsPanel,SWT.NULL);

    lbl = new Label(generalStatsPanel,SWT.NULL);
    Messages.setLanguageText(lbl,"SpeedView.stats.downloaded");

    lbl = new Label(generalStatsPanel,SWT.NULL);
    Messages.setLanguageText(lbl,"SpeedView.stats.uploaded");

    lbl = new Label(generalStatsPanel,SWT.NULL);
    Messages.setLanguageText(lbl,"SpeedView.stats.ratio");

    lbl = new Label(generalStatsPanel,SWT.NULL);
    Messages.setLanguageText(lbl,"SpeedView.stats.uptime");

    lbl = new Label(generalStatsPanel,SWT.NULL);
    lbl = new Label(generalStatsPanel,SWT.NULL);
    lbl = new Label(generalStatsPanel,SWT.NULL);
    lbl = new Label(generalStatsPanel,SWT.NULL);
    lbl = new Label(generalStatsPanel,SWT.NULL);

    /////// NOW /////////
    Label nowLabel = new Label(generalStatsPanel,SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    nowLabel.setLayoutData(gridData);
    Messages.setLanguageText(nowLabel,"SpeedView.stats.now");

    nowDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    nowDown.setLayoutData(gridData);

    nowUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    nowUp.setLayoutData(gridData);

    lbl = new Label(generalStatsPanel,SWT.NULL);
    lbl = new Label(generalStatsPanel,SWT.NULL);


    //////// SESSION ////////
    Label sessionLabel = new Label(generalStatsPanel,SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    sessionLabel.setLayoutData(gridData);

    Messages.setLanguageText(sessionLabel,"SpeedView.stats.session");
    sessionDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    sessionDown.setLayoutData(gridData);

    sessionUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    sessionUp.setLayoutData(gridData);

    session_ratio = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    session_ratio.setLayoutData(gridData);

    sessionTime = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    sessionTime.setLayoutData(gridData);


    ///////// TOTAL ///////////
    totalLabel = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalLabel.setLayoutData(gridData);
    Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total");

    totalDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalDown.setLayoutData(gridData);

    totalUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalUp.setLayoutData(gridData);

    total_ratio = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    total_ratio.setLayoutData(gridData);

    totalTime = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalTime.setLayoutData(gridData);

    for ( Object obj: new Object[]{ nowLabel, sessionLabel, totalLabel }){

    	Control control;

    	if ( obj instanceof BufferedLabel ){

    		control = ((BufferedLabel)obj).getControl();

    	}else{

    		control = (Label)obj;
    	}
    	final Menu menu = new Menu(control.getShell(), SWT.POP_UP );
    	control.setMenu( menu );
    	MenuItem   item = new MenuItem( menu,SWT.NONE );
    	Messages.setLanguageText( item, "MainWindow.menu.view.configuration" );
    	item.addSelectionListener(
    		new SelectionAdapter()
    		{
    			@Override
			    public void
    			widgetSelected(
    				SelectionEvent e)
    			{
    				UIFunctions uif = UIFunctionsManager.getUIFunctions();

    				if (uif != null) {

							uif.getMDI().showEntryByID(
									MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, "Stats");
    				}
    			}
    		});
    }

    	// SOCKS area

    GridData generalSocksPanelGridData = new GridData();
    generalSocksPanelGridData.horizontalIndent=2;
	Composite[] generalSocksPanels = Utils.createSkinnedCompositeEx(generalPanel, SWT.BORDER, generalSocksPanelGridData);

	Composite generalSocksPanel = generalSocksPanels[1];	// inner when hacked by theme
    GridLayout socksLayout = new GridLayout();
    socksLayout.numColumns = 2;
	generalSocksPanel.setLayout(socksLayout);

    lbl = new Label(generalSocksPanel,SWT.NULL);
    Messages.setLanguageText(lbl,"label.socks");

    lbl = new Label(generalSocksPanel,SWT.NULL);

    	// proxy state

    lbl = new Label(generalSocksPanel,SWT.NULL);
    lbl.setText( MessageText.getString( "label.proxy" ) + ":" );

    socksState =  new Label(generalSocksPanel,SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 120;
    socksState.setLayoutData(gridData);

    	// current details

    lbl = new Label(generalSocksPanel,SWT.NULL);
    lbl.setText( MessageText.getString( "PeersView.state" ) + ":" );

    socksCurrent =  new BufferedLabel(generalSocksPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    socksCurrent.setLayoutData(gridData);

    	// fail details

    lbl = new Label(generalSocksPanel,SWT.NULL);
    lbl.setText( MessageText.getString( "label.fails" ) + ":" );

    socksFails =  new BufferedLabel(generalSocksPanel,SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    socksFails.setLayoutData(gridData);

    	// more info

    lbl = new Label(generalSocksPanel,SWT.NULL);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalAlignment = GridData.END;
    socksMore  =  new Label(generalSocksPanel, SWT.NULL );
    socksMore.setText( MessageText.getString( "label.more") + "..." );
    socksMore.setLayoutData(gridData);
    socksMore.setCursor(socksMore.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    Utils.setLinkForeground( socksMore );
    socksMore.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseDoubleClick(MouseEvent arg0) {
    	  showSOCKSInfo();
      }
      @Override
      public void mouseUp(MouseEvent arg0) {
    	  showSOCKSInfo();
      }
    });

    	// got a rare layout bug that results in the generalStatsPanel not showing the bottom row correctly until the panel
    	// is resized - attempt to fix by sizing based on the socks panel which seems to consistently layout OK

    Point socks_size = generalSocksPanels[0].computeSize( SWT.DEFAULT, SWT.DEFAULT );
    
    Rectangle trim = generalSocksPanels[0].computeTrim(0, 0, socks_size.x, socks_size.y );
    
    generalStatsPanelGridData.heightHint = socks_size.y - ( trim.height - socks_size.y );
  }

  private void
  showSOCKSInfo()
  {
	  AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

	  String	info = proxy_selector.getInfo();

	  TextViewerWindow viewer = new TextViewerWindow(
			MessageText.getString( "proxy.info.title" ),
			null,
			info, false  );

  }

  private void
  createCapacityPanel()
  {
	  blahPanel = new Composite(currentPanel,SWT.NONE);
	  GridData blahPanelData = new GridData(GridData.FILL_HORIZONTAL);
	  blahPanel.setLayoutData(blahPanelData);

	  GridLayout panelLayout = new GridLayout();
	  panelLayout.numColumns = 8;
	  blahPanel.setLayout(panelLayout);


	  Label label;
	  GridData gridData;

	  label = new Label(blahPanel,SWT.NONE);
	  Messages.setLanguageText(label,"SpeedView.stats.asn");
	  asn = new BufferedLabel(blahPanel,SWT.DOUBLE_BUFFERED);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  asn.setLayoutData(gridData);

	  label = new Label(blahPanel,SWT.NONE);
	  Messages.setLanguageText(label,"label.current_ip");
	  currentIP = new BufferedLabel(blahPanel,SWT.DOUBLE_BUFFERED);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  currentIP.setLayoutData(gridData);

	  label = new Label(blahPanel,SWT.NONE);
	  Messages.setLanguageText(label,"SpeedView.stats.estupcap");
	  estUpCap = new BufferedLabel(blahPanel,SWT.DOUBLE_BUFFERED);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  estUpCap.setLayoutData(gridData);

	  label = new Label(blahPanel,SWT.NONE);
	  Messages.setLanguageText(label,"SpeedView.stats.estdowncap");
	  estDownCap = new BufferedLabel(blahPanel,SWT.DOUBLE_BUFFERED);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  estDownCap.setLayoutData(gridData);

	  label = new Label(blahPanel,SWT.NONE);
	  Messages.setLanguageText(label,"SpeedView.stats.upbias");
	  uploadBiaser = new BufferedLabel(blahPanel,SWT.DOUBLE_BUFFERED);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  gridData.horizontalSpan = 7;
	  uploadBiaser.setLayoutData(gridData);
  }

  private void
  createConnectionPanel()
  {
	  boolean dark = Utils.isDarkAppearanceNative();
	  
	  Composite connectionHeader = new Composite(currentPanel,SWT.NONE);
	  GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
	  connectionHeader.setLayoutData(gridData);
	  
	  GridLayout chLayout =  Utils.getSimpleGridLayout(4);
	  chLayout.makeColumnsEqualWidth = true;
	  connectionHeader.setLayout(chLayout);
	  
	  connectionPanel = new Composite(currentPanel,SWT.NONE);
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  connectionPanel.setLayoutData(gridData);

	  GridLayout panelLayout = Utils.getSimpleGridLayout(2);
	  panelLayout.makeColumnsEqualWidth = true;
	  connectionPanel.setLayout(panelLayout);

	  Composite conn_area = new Composite( connectionHeader, SWT.NULL );
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  gridData.horizontalSpan = 3;
	  conn_area.setLayoutData(gridData);

	  panelLayout = Utils.getSimpleGridLayout(2);
	  panelLayout.numColumns = 2;
	  conn_area.setLayout(panelLayout);

	  Label label = new Label( conn_area, SWT.NULL );
	  Messages.setLanguageText( label, "SpeedView.stats.con" );

	  connection_label = new BufferedLabel( conn_area, SWT.DOUBLE_BUFFERED );
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  connection_label.setLayoutData(gridData);

	  Composite upload_area = new Composite( connectionHeader, SWT.NULL );
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  upload_area.setLayoutData(gridData);

	  panelLayout = Utils.getSimpleGridLayout(2);
	  upload_area.setLayout(panelLayout);

	  label = new Label( upload_area, SWT.NULL );
	  Messages.setLanguageText( label, "SpeedView.stats.upload" );

	  upload_label = new BufferedLabel( upload_area, SWT.DOUBLE_BUFFERED );
	  gridData = new GridData(GridData.FILL_HORIZONTAL);
	  upload_label.setLayoutData(gridData);


	  	// connections

	  con_folder = new CTabFolder(connectionPanel, SWT.FLAT);
	  gridData = new GridData(GridData.FILL_BOTH);
	  gridData.horizontalSpan = 1;
	  con_folder.setLayoutData(gridData);

	  	// connection counts

	  CTabItem conn_item = new CTabItem(con_folder, SWT.NULL);

	  conn_item.setText( MessageText.getString( "label.connections" ));

	  Canvas connection_canvas = new Canvas(con_folder,SWT.NO_BACKGROUND);
	  conn_item.setControl( connection_canvas );
	  gridData = new GridData(GridData.FILL_BOTH);
	  gridData.heightHint = 200;
	  connection_canvas.setLayoutData(gridData);

	  connection_graphic.initialize(connection_canvas);
	  Color[] colors = connection_graphic.colors;

	  connection_graphic.setLineColors( colors );

	  	// route info

	  CTabItem route_info_tab = new CTabItem(con_folder, SWT.NULL);

	  route_info_tab.setText( MessageText.getString( "label.routing" ));

	  Composite route_tab_comp = new Composite( con_folder, dark?SWT.BORDER:SWT.NULL );
	  route_tab_comp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	  GridLayout routeTabLayout = Utils.getSimpleGridLayout(1);
	  route_tab_comp.setLayout(routeTabLayout);
	  route_info_tab.setControl( route_tab_comp );

	  ScrolledComposite sc = new ScrolledComposite( route_tab_comp, SWT.V_SCROLL );
	  sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

	  con_folder.setSelection( 0 );
	  
	  route_comp = new Composite( sc, SWT.NULL );

	  if ( !dark ) {
	  
		  route_comp.setBackground( Colors.white );
	  
		  sc.setBackground( Colors.white );
	  }
	  
	  route_comp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	  GridLayout routeLayout = Utils.getSimpleGridLayout(3);
	  route_comp.setLayout(routeLayout);

	  sc.setContent( route_comp );

	  buildRouteComponent( 5 );


	  	// upload queued

	  Canvas upload_canvas = new Canvas(connectionPanel,SWT.NO_BACKGROUND);
	  gridData = new GridData(GridData.FILL_BOTH);
	  gridData.horizontalIndent=4;
	  gridData.heightHint = 200;
	  upload_canvas.setLayoutData(gridData);

	  upload_graphic.initialize(upload_canvas);

  }

  private void
  buildRouteComponent(
	int			rows )
  {
	  boolean	changed = false;

	  boolean force = route_labels.length > 0 && route_labels[0][0].isDisposed();
		  
	  if ( rows <= route_labels.length && !force ){

		  for ( int i=rows;i<route_labels.length;i++){

			  for ( int j=0;j<3;j++){

				  route_labels[i][j].setText( "" );
			  }
		  }
	  }else{

		  Control[] labels = route_comp.getChildren();
		  for (int i = 0; i < labels.length; i++){
				labels[i].dispose();
		  }

		  boolean dark = Utils.isDarkAppearanceNative();
			  
		  Label h1 = new Label( route_comp, SWT.NULL );
		  h1.setLayoutData(new GridData(GridData.FILL_HORIZONTAL ));
		  h1.setText( MessageText.getString( "label.route" ));
		  if ( !dark ){
			  h1.setForeground( Colors.black );
			  h1.setBackground( Colors.white );
		  }
		  Label h2 = new Label( route_comp, SWT.NULL );
		  h2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL ));
		  h2.setText( MessageText.getString( "tps.type.incoming" ));
		  if ( !dark ){
			  h2.setForeground( Colors.black );
			  h2.setBackground( Colors.white );
		  }
		  Label h3 = new Label( route_comp, SWT.NULL );
		  h3.setLayoutData(new GridData(GridData.FILL_HORIZONTAL ));
		  h3.setText( MessageText.getString( "label.outgoing" ));
		  if ( !dark ){
			  h3.setForeground( Colors.black );
			  h3.setBackground( Colors.white );
		  }

		  new Label( route_comp, SWT.NULL );
		  new Label( route_comp, SWT.NULL );
		  new Label( route_comp, SWT.NULL );

		  route_labels = new BufferedLabel[rows][3];

		  for ( int i=0;i<rows;i++ ){

			  for ( int j=0;j<3;j++){
				  BufferedLabel l = new BufferedLabel( route_comp, SWT.DOUBLE_BUFFERED );
				  GridData gridData = new GridData(GridData.FILL_HORIZONTAL );
				  l.setLayoutData(gridData);
				  if ( !dark ){
					  l.getControl().setBackground( Colors.white );
					  l.getControl().setForeground( Colors.black );
				  }
				  route_labels[i][j] = l;
			  }
		  }

		  changed = true;
	  }

	  Point size = route_comp.computeSize(route_comp.getParent().getSize().x, SWT.DEFAULT);
	  
	  changed = changed || !route_comp.getSize().equals( size );

	  route_comp.setSize(size);
	  
	  if ( !changed ){
		  
		  	// sometimes things get layouted when not visibel and things don't work proper when visibilized ;(
		  	// look for something zero height that shouldn't be

		  for ( int i=0;i<route_labels.length;i++){
			  for (int j=0;j<3;j++){
				  BufferedLabel lab = route_labels[i][j];
				  if ( !lab.isDisposed() && lab.getControl().getSize().y == 0 &&  lab.getText().length() > 0 ){
					  changed = true;
				  }
			  }
		  }
	  }

	  if ( changed ){

		  route_comp.getParent().layout( true, true );
	  }

	  route_comp.update();
  }

  private void createAutoSpeedPanel() {
    autoSpeedPanel = Utils.createSkinnedGroup(currentPanel,SWT.NONE);
    GridData generalPanelData = new GridData(GridData.FILL_BOTH);
    autoSpeedPanel.setLayoutData(generalPanelData);
    Messages.setLanguageText(autoSpeedPanel,"SpeedView.stats.autospeed", new String[]{ String.valueOf( MAX_DISPLAYED_PING_MILLIS_DISP )});


    autoSpeedPanelLayout = new StackLayout();
    autoSpeedPanelLayout.marginHeight = autoSpeedPanelLayout.marginWidth = 0;
    autoSpeedPanel.setLayout(autoSpeedPanelLayout);

    autoSpeedInfoPanel = new Composite(autoSpeedPanel,SWT.NULL);
    autoSpeedInfoPanel.setLayoutData(new GridData(GridData.FILL_BOTH));
    GridLayout layout = Utils.getSimpleGridLayout(8);

    layout.makeColumnsEqualWidth = true;
    autoSpeedInfoPanel.setLayout(layout);

    Canvas pingCanvas = new Canvas(autoSpeedInfoPanel,SWT.NO_BACKGROUND);
    GridData gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 4;
    pingCanvas.setLayoutData(gridData);

    pingGraph.initialize(pingCanvas);

    CTabFolder folder = new CTabFolder(autoSpeedInfoPanel, SWT.FLAT);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalIndent = 4;
    gridData.horizontalSpan = 4;
    folder.setLayoutData(gridData);
    //folder.setBackground(Colors.background);

    ValueFormater speed_formatter =
    	new ValueFormater()
	    {
	    	@Override
		    public String
	    	format(
	    		int value)
	    	{
	    		return( DisplayFormatters.formatByteCountToKiBEtc( value ));
	    	}
	    };

    ValueFormater time_formatter =
    	new ValueFormater()
	    {
	    	@Override
		    public String
	    	format(
	    		int value)
	    	{
	    		return( value + TimeFormatter.MS_SUFFIX );
	    	}
	    };

    ValueFormater[] formatters = new ValueFormater[]{ speed_formatter, speed_formatter, time_formatter };

    String[] labels = new String[]{ "up", "down", "ping" };

    SpeedManagerPingMapper[] mappers = speedManager.getMappers();

    plot_views	= new plotView[mappers.length];
    zone_views	= new zoneView[mappers.length];

    for (int i=0;i<mappers.length;i++){

    	SpeedManagerPingMapper mapper = mappers[i];

    	CTabItem plot_item = new CTabItem(folder, SWT.NULL);

	    plot_item.setText( "Plot " + mapper.getName());

	    Canvas plotCanvas = new Canvas(folder,SWT.NO_BACKGROUND);
	    gridData = new GridData(GridData.FILL_BOTH);
	    plotCanvas.setLayoutData(gridData);

	    plot_views[i] = new plotView( mapper, plotCanvas, labels, formatters );

	    plot_item.setControl( plotCanvas );

	    CTabItem zones_item = new CTabItem(folder, SWT.NULL);
	    zones_item.setText( "Zones " + mapper.getName() );

	    Canvas zoneCanvas = new Canvas(folder,SWT.NO_BACKGROUND);
	    gridData = new GridData(GridData.FILL_BOTH);
	    zoneCanvas.setLayoutData(gridData);

	    zone_views[i] = new zoneView( mapper, zoneCanvas, labels, formatters );

	    zones_item.setControl( zoneCanvas );
    }

    folder.setSelection( 0 );
    
    autoSpeedDisabledPanel = new Composite(autoSpeedPanel,SWT.NULL);
    autoSpeedDisabledPanel.setLayout(new GridLayout());
    Label disabled = new Label(autoSpeedDisabledPanel,SWT.NULL);
    disabled.setEnabled(false);
    Messages.setLanguageText(disabled,"SpeedView.stats.autospeed.disabled");
    disabled.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL));

    autoSpeedPanelLayout.topControl = speedManager.isAvailable() ? autoSpeedInfoPanel : autoSpeedDisabledPanel;

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 8;

	Legend.createLegendComposite(
			autoSpeedInfoPanel,
    		PingGraphic.defaultColors,
    		new String[]{
        			"TransferStatsView.legend.pingaverage",
        			"TransferStatsView.legend.ping1",
        			"TransferStatsView.legend.ping2",
    				"TransferStatsView.legend.ping3" },
    		gridData );
  }

  private void delete() {
		Utils.disposeComposite(generalPanel);
		Utils.disposeComposite(blahPanel);

		if ( upload_graphic != null ){
			upload_graphic.dispose();
		}

		if ( connection_graphic != null ){
			connection_graphic.dispose();
		}

		if (pingGraph != null) {
			pingGraph.dispose();
		}

		if (plot_views != null) {
			for (int i = 0; i < plot_views.length; i++) {

				plot_views[i].dispose();
			}
		}

		if (zone_views != null) {
			for (int i = 0; i < zone_views.length; i++) {

				zone_views[i].dispose();
			}
		}
	}

  private Composite getComposite() {
    return mainPanel;
  }



  private void refresh() {

	  if ( !initialised || mainPanel == null || mainPanel.isDisposed() ){
		  return;
	  }
	  refreshGeneral();

	  refreshCapacityPanel();

	  refreshConnectionPanel();

	  refreshPingPanel();
	  
	  refreshHistory();
  }

  private void refreshGeneral() {
	if ( stats == null ){
		return;
	}

    int now_prot_down_rate = stats.getProtocolReceiveRate();
    int now_prot_up_rate = stats.getProtocolSendRate();

    int now_total_down_rate = stats.getDataReceiveRate() + now_prot_down_rate;
    int now_total_up_rate = stats.getDataSendRate() + now_prot_up_rate;

    float now_perc_down = (float)(now_prot_down_rate *100) / (now_total_down_rate==0?1:now_total_down_rate);
    float now_perc_up = (float)(now_prot_up_rate *100) / (now_total_up_rate==0?1:now_total_up_rate);

    nowDown.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec( now_total_down_rate ) +
                    "  (" + DisplayFormatters.formatByteCountToKiBEtcPerSec( now_prot_down_rate ) +
                    ", " +formatter.format( now_perc_down )+ "%)" );

    nowUp.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec( now_total_up_rate ) +
                  "  (" + DisplayFormatters.formatByteCountToKiBEtcPerSec( now_prot_up_rate ) +
                  ", " +formatter.format( now_perc_up )+ "%)" );

    ///////////////////////////////////////////////////////////////////////

    long session_prot_received = stats.getTotalProtocolBytesReceived();
    long session_prot_sent = stats.getTotalProtocolBytesSent();

    long session_total_received = stats.getTotalDataBytesReceived() + session_prot_received;
    long session_total_sent = stats.getTotalDataBytesSent() + session_prot_sent;

    float session_perc_received = (float)(session_prot_received *100) / (session_total_received==0?1:session_total_received);
    float session_perc_sent = (float)(session_prot_sent *100) / (session_total_sent==0?1:session_total_sent);

    sessionDown.setText(DisplayFormatters.formatByteCountToKiBEtc( session_total_received ) +
                        "  (" + DisplayFormatters.formatByteCountToKiBEtc( session_prot_received ) +
                        ", " +formatter.format( session_perc_received )+ "%)" );

    sessionUp.setText(DisplayFormatters.formatByteCountToKiBEtc( session_total_sent ) +
                      "  (" + DisplayFormatters.formatByteCountToKiBEtc( session_prot_sent ) +
                      ", " +formatter.format( session_perc_sent )+ "%)" );

    ////////////////////////////////////////////////////////////////////////

    if (totalStats != null) {
      long mark = totalStats.getMarkTime();
      if ( mark > 0 ){
    	  Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total.since", new String[]{ new SimpleDateFormat().format( new Date( mark )) });
      }else{
    	  Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total");
      }

      long dl_bytes = totalStats.getDownloadedBytes( true );
      long ul_bytes = totalStats.getUploadedBytes( true );

      totalDown.setText(DisplayFormatters.formatByteCountToKiBEtc( dl_bytes ));
      totalUp.setText(DisplayFormatters.formatByteCountToKiBEtc( ul_bytes ));

      long session_up_time 	= totalStats.getSessionUpTime();
      long total_up_time 	= totalStats.getTotalUpTime( true );

      sessionTime.setText( session_up_time==0?"":DisplayFormatters.formatETA( session_up_time ));
      totalTime.setText( total_up_time==0?"":DisplayFormatters.formatETA( total_up_time ));


      long t_ratio_raw = (1000* ul_bytes / (dl_bytes==0?1:dl_bytes) );
      long s_ratio_raw = (1000* session_total_sent / (session_total_received==0?1:session_total_received) );

      String t_ratio = "";
      String s_ratio = "";

      String partial = String.valueOf(t_ratio_raw % 1000);
      while (partial.length() < 3) {
        partial = "0" + partial;
      }
      t_ratio = (t_ratio_raw / 1000) + "." + partial;

      partial = String.valueOf(s_ratio_raw % 1000);
      while (partial.length() < 3) {
        partial = "0" + partial;
      }
      s_ratio = (s_ratio_raw / 1000) + "." + partial;


      total_ratio.setText( t_ratio );
      session_ratio.setText( s_ratio );
    }

    AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

    Proxy proxy = proxy_selector.getActiveProxy();

    socksMore.setEnabled( proxy != null );

    if ( Constants.isOSX ){

    	socksMore.setForeground(proxy==null?Colors.light_grey:Colors.blue);
    }

    socksState.setText( proxy==null?MessageText.getString( "label.inactive" ): ((InetSocketAddress)proxy.address()).getHostName());

    if ( proxy == null ){

    	socksCurrent.setText( "" );

    	socksFails.setText( "" );

    }else{
	    long	last_con 	= proxy_selector.getLastConnectionTime();
	    long	last_fail 	= proxy_selector.getLastFailTime();
	    int		total_cons	= proxy_selector.getConnectionCount();
	    int		total_fails	= proxy_selector.getFailCount();

	    long	now = SystemTime.getMonotonousTime();

	    long	con_ago		= now - last_con;
	    long	fail_ago 	= now - last_fail;

	    String	state_str;

	    if ( last_fail < 0 ){

	    	state_str = "PeerManager.status.ok";

	    }else{

		    if ( fail_ago > 60*1000 ){

		    	if ( con_ago < fail_ago ){

		    		state_str = "PeerManager.status.ok";

		    	}else{

		    		state_str = "SpeedView.stats.unknown";
		    	}
		    }else{

		    	state_str = "ManagerItem.error";
		    }
	    }

	    socksCurrent.setText( MessageText.getString( state_str ) + ", con=" + total_cons );

	    long	fail_ago_secs = fail_ago/1000;

	    if ( fail_ago_secs == 0 ){

	    	fail_ago_secs = 1;
	    }

	    socksFails.setText( last_fail<0?"":(DisplayFormatters.formatETA( fail_ago_secs, false ) + " " + MessageText.getString( "label.ago" ) + ", tot=" + total_fails ));
    }
  }

  private void
  refreshCapacityPanel()
  {
	  if ( speedManager == null ){
		  return;
	  }

	  asn.setTextAndTooltip(speedManager.getASN());

	  estUpCap.setTextAndTooltip(limit_to_text.getLimitText(speedManager.getEstimatedUploadCapacityBytesPerSec()));

	  estDownCap.setTextAndTooltip(limit_to_text.getLimitText(speedManager.getEstimatedDownloadCapacityBytesPerSec()));

	  uploadBiaser.setTextAndTooltip( DownloadManagerRateController.getString());

	  NetworkAdmin na = NetworkAdmin.getSingleton();
	  
	  InetAddress current_ip 	= na.getDefaultPublicAddress( true );
	  InetAddress current_ip6 	= na.getDefaultPublicAddressV6();

	  String str = "";
	  
	  if (  current_ip != null ){
		  
		  str += current_ip.getHostAddress();
	  }
	  
	  if ( current_ip6 != null ){
		  
		  if ( current_ip == null || !current_ip6.equals( current_ip )){
			  
			  str += (str.isEmpty()?"":"/") + current_ip6.getHostAddress();
		  }
	  }
	  
	  currentIP.setTextAndTooltip( str );
  }

  private void
  refreshConnectionPanel()
  {
	  if ( global_manager == null ){
		  return;
	  }

	  int	total_connections	= 0;
	  int	total_con_queued	= 0;
	  int	total_con_blocked	= 0;
	  int	total_con_unchoked	= 0;

	  int	total_data_queued	= 0;

	  int	total_in 	= 0;

	  List<DownloadManager> dms = global_manager.getDownloadManagers();

	  for ( DownloadManager dm: dms ){

		  PEPeerManager pm = dm.getPeerManager();

		  if ( pm != null ){

			  total_data_queued += pm.getBytesQueuedForUpload();

			  total_connections += pm.getNbPeers() + pm.getNbSeeds();

			  total_con_queued 	+= pm.getNbPeersWithUploadQueued();
			  total_con_blocked	+= pm.getNbPeersWithUploadBlocked();

			  total_con_unchoked += pm.getNbPeersUnchoked();

			  total_in += pm.getNbRemoteTCPConnections() + pm.getNbRemoteUDPConnections() + pm.getNbRemoteUTPConnections();
		  }
	  }

	  connection_label.setText(
			MessageText.getString(
					"SpeedView.stats.con_details",
					new String[]{
							String.valueOf(total_connections) + "[" +MessageText.getString( "label.in").toLowerCase() + ":" + total_in + "]",
							String.valueOf(total_con_unchoked), String.valueOf(total_con_queued), String.valueOf(total_con_blocked) }));

	  upload_label.setText(
			MessageText.getString(
					"SpeedView.stats.upload_details",
					new String[]{ DisplayFormatters.formatByteCountToKiBEtc( total_data_queued )}));

	  upload_graphic.refresh(false);
	  connection_graphic.refresh(false);

	  if ( con_folder.getSelectionIndex() == 1 ){

		  long	now = SystemTime.getMonotonousTime();

		  if ( now - last_route_update >= 2*1000 ){

			  last_route_update = now;

			  NetworkAdmin na = NetworkAdmin.getSingleton();

			  Map<InetAddress,String>		ip_to_name_map		= new HashMap<>();

			  Map<String,RouteInfo>			name_to_route_map 	= new HashMap<>();

			  RouteInfo udp_info 		= null;
			  RouteInfo unknown_info 	= null;

			  List<PRUDPPacketHandler> udp_handlers = PRUDPPacketHandlerFactory.getHandlers();

			  InetAddress	udp_bind_ip = null;

			  for ( PRUDPPacketHandler handler: udp_handlers ){

				  if ( handler.hasPrimordialHandler()){

					  udp_bind_ip = handler.getBindIP();

					  if ( udp_bind_ip != null ){

						  if( udp_bind_ip.isAnyLocalAddress()){

							  udp_bind_ip = null;
						  }
					  }
				  }
			  }

			  for ( DownloadManager dm: dms ){

				  PEPeerManager pm = dm.getPeerManager();

				  if ( pm != null ){

					  List<PEPeer> peers = pm.getPeers();

					  for ( PEPeer p: peers ){

						  NetworkConnection nc = PluginCoreUtils.unwrap( p.getPluginConnection());

						  boolean done = false;

						  if ( nc != null ){

							  Transport transport = nc.getTransport();

							  if ( transport != null ){

								  InetSocketAddress notional_address = nc.getEndpoint().getNotionalAddress();

								  String ip    = AddressUtils.getHostAddress( notional_address );

								  String network	= AENetworkClassifier.categoriseAddress( ip );

								  if ( network == AENetworkClassifier.AT_PUBLIC ){
									
									  if ( transport.isTCP()){
	
										  TransportStartpoint start = transport.getTransportStartpoint();
	
										  if ( start != null ){
	
											  InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();
	
											  if ( socket_address != null ){
	
												  InetAddress	address = socket_address.getAddress();
	
												  String name;
	
												  if ( address.isAnyLocalAddress()){
	
													  name = "* (TCP)";
	
												  }else{
	
													  name = ip_to_name_map.get( address );
												  }
	
												  if ( name == null ){
	
													  name = na.classifyRoute( address);
	
													  ip_to_name_map.put( address, name );
												  }
	
												  if ( transport.isSOCKS()){
	
													  name += " (SOCKS)";
												  }
	
												  RouteInfo	info = name_to_route_map.get( name );
	
												  if ( info == null ){
	
													  info = new RouteInfo( name );
	
													  name_to_route_map.put( name, info );
	
													  route_last_seen.put( name, now );
												  }
	
												  info.update( p );
	
												  done = true;
											  }
										  }
									  }else{
	
										  if ( udp_bind_ip != null ){
	
											  RouteInfo	info;
	
											  String name = ip_to_name_map.get( udp_bind_ip );
	
											  if ( name == null ){
	
												  name = na.classifyRoute( udp_bind_ip);
	
												  ip_to_name_map.put( udp_bind_ip, name );
	
												  info = name_to_route_map.get( name );
	
												  route_last_seen.put( name, now );
	
												  if ( info == null ){
	
													  info = new RouteInfo( name );
	
													  name_to_route_map.put( name, info );
												  }
	
											  }else{
	
												  info = name_to_route_map.get( name );
											  }
	
											  info.update( p );
	
											  done = true;
	
										  }else{
	
											  if ( udp_info == null ){
	
												  udp_info 		= new RouteInfo( "* (UDP)" );
	
												  name_to_route_map.put( udp_info.getName(), udp_info );
	
												  route_last_seen.put( udp_info.getName(), now );
											  }
	
											  udp_info.update( p );
	
											  done = true;
										  }
									  }
								  }else{
									  
									  RouteInfo	info = name_to_route_map.get( network );
										
									  if ( info == null ){

										  info = new RouteInfo( network );

										  name_to_route_map.put( network, info );

										  route_last_seen.put( network, now );
									  }

									  info.update( p );

									  done = true;
								  }
							  }
						  }

						  if ( !done ){

							  if ( unknown_info == null ){

								  unknown_info 		= new RouteInfo( "Pending" );

								  name_to_route_map.put( unknown_info.getName(), unknown_info );

								  route_last_seen.put( unknown_info.getName(), now );
							  }

							  unknown_info.update( p );
						  }
					  }
				  }
			  }

			  List<RouteInfo>	rows = new ArrayList<>();

			  Iterator<Map.Entry<String,Long>> it = route_last_seen.entrySet().iterator();

			  while( it.hasNext()){

				  Map.Entry<String,Long> entry = it.next();

				  long	when = entry.getValue();

				  if ( now - when > 60*1000 ){

					  it.remove();

				  }else if ( when != now ){

					  rows.add( new RouteInfo( entry.getKey()));
				  }
			  }

			  rows.addAll( name_to_route_map.values());

			  Collections.sort(
					 rows,
					 new Comparator<RouteInfo>()
					 {
						@Override
						public int
						compare(
							RouteInfo o1,
							RouteInfo o2)
						{
							String	n1 = o1.getName();
							String	n2 = o2.getName();

								// wildcard and pending to the bottom
							if ( n1.startsWith( "*" ) || n1.equals( "Pending" )){
								n1 = "zzzz" + n1;
							}
							if ( n2.startsWith( "*" ) || n2.equals( "Pending" )){
								n2 = "zzzz" + n2;
							}

							return( n1.compareTo(n2));
						}
					 });

			  buildRouteComponent( rows.size());

			  for ( int i=0;i<rows.size();i++){

				  RouteInfo	info = rows.get( i );

				  route_labels[i][0].setText( info.getName());
				  route_labels[i][1].setText( info.getIncomingString());
				  route_labels[i][2].setText( info.getOutgoingString());
			  }

			  buildRouteComponent( rows.size());
		  }
	  }
  }

  private static class
  RouteInfo
  {
	  private String			name;
	  private RouteInfoRecord	incoming = new RouteInfoRecord();
	  private RouteInfoRecord	outgoing = new RouteInfoRecord();

	  private
	  RouteInfo(
		String		_name )
	  {
		  name	= _name;
	  }

	  private String
	  getName()
	  {
		  return( name );
	  }

	  private String
	  getIncomingString()
	  {
		  return( incoming.getString());
	  }

	  private String
	  getOutgoingString()
	  {
		  return( outgoing.getString());
	  }

	  private void
	  update(
		  PEPeer	peer )
	  {
		  RouteInfoRecord record;

		  if ( peer.isIncoming()){

			  record = incoming;

		  }else{

			  record = outgoing;
		  }

		  record.update( peer );
	  }
  }

  private static class
  RouteInfoRecord
  {
	  private int	peer_count;
	  private int	up_rate;
	  private int	down_rate;

	  private void
	  update(
		  PEPeer	peer )
	  {
		  peer_count++;

		  PEPeerStats stats = peer.getStats();

		  up_rate 	+= stats.getDataSendRate() + stats.getProtocolSendRate();
		  down_rate += stats.getDataReceiveRate() + stats.getProtocolReceiveRate();
	  }

	  private String
	  getString()
	  {
		  if ( peer_count == 0 ){

			  return( "0" );
		  }

		  return( peer_count + ": up=" +
				  DisplayFormatters.formatByteCountToKiBEtcPerSec( up_rate ) + ", down=" +
				  DisplayFormatters.formatByteCountToKiBEtcPerSec( down_rate ));
	  }
  }

  private void refreshPingPanel() {
  	if (speedManager == null) {
  		return;
  	}
    if(speedManager.isAvailable()){// && speedManager.isEnabled()) {
      autoSpeedPanelLayout.topControl = autoSpeedInfoPanel;
      autoSpeedPanel.layout();

      pingGraph.refresh();
      for (int i=0;i<plot_views.length;i++){

    	  plot_views[i].refresh();
      }

      for (int i=0;i<zone_views.length;i++){

    	  zone_views[i].refresh(false);
      }

    } else {
      autoSpeedPanelLayout.topControl = autoSpeedDisabledPanel;
      autoSpeedPanel.layout();
    }
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.views.stats.PeriodicViewUpdate#periodicUpdate()
   */
  public void periodicUpdate() {
  	if (speedManager == null) {
  		return;
  	}
    if(speedManager.isAvailable()){// && speedManager.isEnabled()) {
      SpeedManagerPingSource sources[] = speedManager.getPingSources();
      if(sources.length > 0) {
        int[] pings = new int[sources.length];
        for(int i = 0 ; i < sources.length ; i++) {

        	SpeedManagerPingSource source = sources[i];

        	if ( source != null ){

	        	int	ping = source.getPingTime();

	        	ping = Math.min( ping, MAX_DISPLAYED_PING_MILLIS );

	        	pings[i] = ping;
        	}
        }
        pingGraph.addIntsValue(pings);

        if (plot_views != null) {
          for ( plotView view: plot_views ){
        	  if ( view != null ){
        		  view.update();
        	  }
          }
        }

        if (zone_views != null) {
          for ( zoneView view: zone_views ){
          	if ( view != null ){
          		view.update();
          	}
          }
        }
      }
    }
    
    if ( upload_graphic != null && connection_graphic != null ){
    
	  int	total_data_queued	= 0;

	  int	total_connections	= 0;
	  int	total_con_queued	= 0;
	  int	total_con_blocked	= 0;
	  int	total_con_unchoked	= 0;

	  List<DownloadManager> dms = global_manager.getDownloadManagers();

	  for ( DownloadManager dm: dms ){

		  PEPeerManager pm = dm.getPeerManager();

		  if ( pm != null ){

			  total_data_queued += pm.getBytesQueuedForUpload();
			  
			  total_connections += pm.getNbPeers() + pm.getNbSeeds();

			  total_con_queued 	+= pm.getNbPeersWithUploadQueued();
			  total_con_blocked	+= pm.getNbPeersWithUploadBlocked();

			  total_con_unchoked += pm.getNbPeersUnchoked();

		  }
	  }

	  upload_graphic.addIntValue( total_data_queued );

	  connection_graphic.addIntsValue( new int[]{ total_connections, total_con_unchoked, total_con_queued, total_con_blocked });
    }
  }


  protected String
  getMapperTitle(
		SpeedManagerPingMapper mapper )
  {
	  if ( mapper.isActive()){

		  SpeedManagerLimitEstimate up_1 		= mapper.getEstimatedUploadLimit(false);
		  SpeedManagerLimitEstimate up_2 		= mapper.getEstimatedUploadLimit(true);
		  SpeedManagerLimitEstimate down_1 	= mapper.getEstimatedDownloadLimit(false);
		  SpeedManagerLimitEstimate down_2 	= mapper.getEstimatedDownloadLimit(true);

		  return( "ul=" + DisplayFormatters.formatByteCountToKiBEtc(up_1.getBytesPerSec()) + ":" + DisplayFormatters.formatByteCountToKiBEtc(up_2.getBytesPerSec())+
				  ",dl=" + DisplayFormatters.formatByteCountToKiBEtc(down_1.getBytesPerSec()) + ":" + DisplayFormatters.formatByteCountToKiBEtc(down_2.getBytesPerSec()) +
				  ",mr=" + DisplayFormatters.formatDecimal( mapper.getCurrentMetricRating(),2));
	  }

	  return( "" );
  }

  class
  plotView
  {
	  private SpeedManagerPingMapper	mapper;
	  private Plot3D 					plotGraph;

	  protected
	  plotView(
		  SpeedManagerPingMapper	_mapper,
		  Canvas					_canvas,
		  String[]					_labels,
		  ValueFormater[]			_formatters )
	  {
		  mapper	= _mapper;

		  plotGraph = new Plot3D( _labels, _formatters );

		  plotGraph.setMaxZ( MAX_DISPLAYED_PING_MILLIS );

		  plotGraph.initialize(_canvas);
	  }

	  protected void
	  update()
	  {
		  int[][]	history = mapper.getHistory();

		  plotGraph.update(history);

		  plotGraph.setTitle( getMapperTitle( mapper ));
	  }

	  protected void
	  refresh()
	  {
		  plotGraph.refresh( false );
	  }

	  protected void
	  dispose()
	  {
		  plotGraph.dispose();
	  }
  }

  class
  zoneView
  	implements ParameterListener
  {
	  private SpeedManagerPingMapper	mapper;

	  private SpeedManagerPingZone[] zones = new SpeedManagerPingZone[0];

	  private Canvas			canvas;

	  private ValueFormater[] formatters;

	  private String[] labels;

	  private String	title = "";

	  private int 	refresh_count;
	  private int 	graphicsUpdate;
	  private Point old_size;

	  protected Image buffer_image;

	  protected
	  zoneView(
		  SpeedManagerPingMapper		_mapper,
		  Canvas 						_canvas,
		  String[]						_labels,
		  ValueFormater[]				_formatters )
	  {
		  mapper		= _mapper;
		  canvas		= _canvas;
		  labels		= _labels;
		  formatters	= _formatters;

		  canvas.addPaintListener(new PaintListener() {
				@Override
				public void paintControl(PaintEvent e) {
					if (buffer_image != null && !buffer_image.isDisposed()) {
						Rectangle bounds = buffer_image.getBounds();
						if (bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )) {

							e.gc.drawImage(buffer_image, e.x, e.y, e.width, e.height, e.x, e.y,
									e.width, e.height);
						}
					}
				}
			});

		  canvas.addListener(SWT.Resize, new Listener() {
				@Override
				public void handleEvent(Event event) {
					refresh(true);
				}
			});
		  COConfigurationManager.addAndFireParameterListener( "Graphics Update", this );
	  }

	  @Override
	  public void
	  parameterChanged(
		  String name )
	  {
		  graphicsUpdate = COConfigurationManager.getIntParameter( name );

	  }

	  protected void
	  update()
	  {
		  zones	= mapper.getZones();

		  title = getMapperTitle( mapper );
	  }

	  private void
	  refresh( boolean force )
	  {
		  if ( canvas.isDisposed() || !canvas.isVisible()){

			  return;
		  }

		  Rectangle bounds = canvas.getClientArea();

		  if ( bounds.height < 30 || bounds.width  < 100 || bounds.width > 2000 || bounds.height > 2000 ){

			  return;
		  }

		  boolean size_changed = (old_size == null || old_size.x != bounds.width || old_size.y != bounds.height);

		  old_size = new Point(bounds.width,bounds.height);

		  refresh_count++;

		  if ( refresh_count > graphicsUpdate || force ){

			  refresh_count = 0;
		  }

		  if ( refresh_count == 0 || size_changed ){

			  if ( buffer_image != null && ! buffer_image.isDisposed()){

				  buffer_image.dispose();
			  }

			  buffer_image = draw( bounds );
		  }

		  if ( buffer_image != null ){

			  GC gc = new GC( canvas );

			  gc.drawImage( buffer_image, bounds.x, bounds.y );

			  gc.dispose();
		  }
	  }

	  private Image
	  draw(
		Rectangle	bounds )
	  {
		  final int	PAD_TOP		= 10;
		  final int	PAD_BOTTOM	= 10;
		  final int	PAD_RIGHT	= 10;
		  final int	PAD_LEFT	= 10;

		  int usable_width 	= bounds.width - PAD_LEFT - PAD_RIGHT;
		  int usable_height	= bounds.height - PAD_TOP - PAD_BOTTOM;

		  Image image = new Image( canvas.getDisplay(), bounds );

		  GC gc = new GC( image );

		  boolean dark = Utils.isDarkAppearanceNative();
		  
		  if ( dark ){
				gc.setBackground( canvas.getBackground() );
				gc.fillRectangle(bounds);
		  }
		  
		  gc.setForeground( dark?Colors.grey:Colors.black );

		  gc.drawRectangle( bounds.x, bounds.y, bounds.width-1, bounds.height-1 );

			
		  try {
		  	gc.setAntialias( SWT.ON );
		  } catch (Exception e) {
		  	// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
		  }

		  int font_height 	= gc.getFontMetrics().getHeight();
		  int char_width 	= gc.getFontMetrics().getAverageCharWidth();


		  Color[] colours = plot_views[0].plotGraph.getColours();

		  int	max_x 		= 0;
		  int	max_y 		= 0;

		  if ( zones.length > 0 ){

			  int	max_metric	= 0;

			  for (int i=0;i<zones.length;i++){

				  SpeedManagerPingZone zone = zones[i];

				  int	metric 	= zone.getMetric();

				  if ( metric > 0 ){

					  max_metric = Math.max( max_metric, metric );

					  max_x = Math.max( max_x, zone.getUploadEndBytesPerSec());
					  max_y = Math.max( max_y, zone.getDownloadEndBytesPerSec());
				  }
			  }

			  if ( max_x > 0 && max_y > 0 ){

				  double x_ratio = (double)usable_width/max_x;
				  double y_ratio = (double)usable_height/max_y;

				  List<Object[]>	texts = new ArrayList<>();

				  for (int i=0;i<zones.length;i++){

					  SpeedManagerPingZone zone = zones[i];

					  int	metric 	= zone.getMetric();
					  int	x1		= zone.getUploadStartBytesPerSec();
					  int	y1 		= zone.getDownloadStartBytesPerSec();
					  int	x2 		= zone.getUploadEndBytesPerSec();
					  int	y2		= zone.getDownloadEndBytesPerSec();

					  if ( metric > 0 ){

						  int	colour_index = (int)((float)metric*colours.length/max_metric);

						  if ( colour_index >= colours.length ){

							  colour_index = colours.length-1;
						  }

						  gc.setBackground( colours[colour_index] );

						  int	x 		= PAD_LEFT + (int)(x1*x_ratio);
						  int	y 		= PAD_TOP  + (int)(y1*y_ratio);
						  int	width 	= (int)Math.ceil((x2-x1+1)*x_ratio);
						  int	height	= (int)Math.ceil((y2-y1+1)*y_ratio );

						  int	y_draw = usable_height + PAD_TOP + PAD_TOP - y - height;

						  gc.fillRectangle( x, y_draw, width, height );

						  int	text_metric = zone.getMetric();

						  String text = String.valueOf( metric );

						  int	text_width = text.length()*char_width + 4;

						  if ( width >= text_width && height >= font_height ){


							  Rectangle text_rect =
								new Rectangle(
										x + ((width-text_width)/2),
										y_draw + ((height-font_height)/2),
										text_width, font_height );

							  	// check for overlap with existing and delete older

							  Iterator<Object[]> it = texts.iterator();

							  while( it.hasNext()){

								  Object[]	old = it.next();

								  Rectangle old_coords = (Rectangle)old[1];

								  if ( old_coords.intersects( text_rect )){

									  it.remove();
								  }
							  }

							  texts.add( new Object[]{ new Integer( text_metric ), text_rect });
						  }
					  }
				  }

				  	// only do the last 100 texts as things get a little cluttered

				  int	text_num = texts.size();

				  gc.setForeground( dark?Colors.white:Colors.black );
				  
				  for (int i=(text_num>100?(text_num-100):0);i<text_num;i++){

					  Object[]	entry = texts.get(i);

					  String	str = String.valueOf(entry[0]);

					  Rectangle	rect = (Rectangle)entry[1];

					  gc.drawText(str, rect.x, rect.y, SWT.DRAW_TRANSPARENT );
				  }
			  }
		  }

		  	// x axis

		  int x_axis_left_x = PAD_LEFT;
		  int x_axis_left_y = usable_height + PAD_TOP;

		  int x_axis_right_x = PAD_LEFT + usable_width;
		  int x_axis_right_y = x_axis_left_y;


		  gc.drawLine( x_axis_left_x, x_axis_left_y, x_axis_right_x, x_axis_right_y );
		  gc.drawLine( usable_width, x_axis_right_y - 4, x_axis_right_x, x_axis_right_y );
		  gc.drawLine( usable_width, x_axis_right_y + 4, x_axis_right_x, x_axis_right_y );

		  for (int i=1;i<10;i++){

			  int	x = x_axis_left_x + ( x_axis_right_x - x_axis_left_x )*i/10;

			  gc.drawLine( x, x_axis_left_y, x, x_axis_left_y+4 );
		  }

		  SpeedManagerLimitEstimate le = mapper.getEstimatedUploadLimit( false );

		  if ( le != null ){

			  gc.setForeground(Colors.grey );

			  int[][] segs = le.getSegments();

			  if ( segs.length > 0 ){

				  int	max_metric 	= 0;
				  int	max_pos		= 0;

				  for (int i=0;i<segs.length;i++){

					  int[]	seg = segs[i];

					  max_metric 	= Math.max( max_metric, seg[0] );
					  max_pos 		= Math.max( max_pos, seg[2] );
				  }

				  double	metric_ratio 	= max_metric==0?1:((float)50/max_metric);
				  double	pos_ratio 		= max_pos==0?1:((float)usable_width/max_pos);

				  int	prev_x	= 1;
				  int	prev_y	= 1;

				  for (int i=0;i<segs.length;i++){

					  int[]	seg = segs[i];

					  int	next_x 	= (int)((seg[1] + (seg[2]-seg[1])/2)*pos_ratio) + 1;
					  int	next_y	= (int)((seg[0])*metric_ratio) + 1;

					  gc.drawLine(
								x_axis_left_x + prev_x,
								x_axis_left_y - prev_y,
								x_axis_left_x + next_x,
								x_axis_left_y - next_y );

					  prev_x = next_x;
					  prev_y = next_y;
				  }
			  }

			  gc.setForeground( dark?Colors.grey:Colors.black );
		  }

		  SpeedManagerLimitEstimate[] bad_up = mapper.getBadUploadHistory();

		  if ( bad_up.length > 0 ){

			  gc.setLineWidth( 3 );

			  gc.setForeground( Colors.red );

			  for (int i=0;i<bad_up.length;i++){

				  int speed = bad_up[i].getBytesPerSec();

				  int	x = max_x == 0?0:(speed * usable_width / max_x);

				  gc.drawLine(
							x_axis_left_x + x,
							x_axis_left_y - 0,
							x_axis_left_x + x,
							x_axis_left_y - 10 );

			  }

			  gc.setForeground( dark?Colors.grey:Colors.black );

			  gc.setLineWidth( 1 );
		  }

		  gc.setForeground( dark?Colors.white:Colors.black );
		  
		  String x_text = labels[0] + " - " + formatters[0].format( max_x+1 );

		  gc.drawText( 	x_text,
				  		x_axis_right_x - 20 - x_text.length()*char_width,
				  		x_axis_right_y - font_height - 2,
				  		SWT.DRAW_TRANSPARENT );

		  	// y axis

		  int y_axis_bottom_x = PAD_LEFT;
		  int y_axis_bottom_y = usable_height + PAD_TOP;

		  int y_axis_top_x 	= PAD_LEFT;
		  int y_axis_top_y 	= PAD_TOP;

		  gc.drawLine( y_axis_bottom_x, y_axis_bottom_y, y_axis_top_x, y_axis_top_y );

		  gc.drawLine( y_axis_top_x-4, y_axis_top_y+PAD_TOP,	y_axis_top_x, y_axis_top_y );
		  gc.drawLine( y_axis_top_x+4, y_axis_top_y+PAD_TOP,	y_axis_top_x, y_axis_top_y );

		  for (int i=1;i<10;i++){

			  int	y = y_axis_bottom_y + ( y_axis_top_y - y_axis_bottom_y )*i/10;

			  gc.drawLine( y_axis_bottom_x, y, y_axis_bottom_x-4, y );
		  }

		  le = mapper.getEstimatedDownloadLimit( false );

		  if ( le != null ){

			  gc.setForeground(Colors.grey );

			  int[][] segs = le.getSegments();

			  if ( segs.length > 0 ){

				  int	max_metric 	= 0;
				  int	max_pos		= 0;

				  for (int i=0;i<segs.length;i++){

					  int[]	seg = segs[i];

					  max_metric 	= Math.max( max_metric, seg[0] );
					  max_pos 		= Math.max( max_pos, seg[2] );
				  }

				  double	metric_ratio 	= max_metric==0?1:((float)50/max_metric);
				  double	pos_ratio 		= max_pos==0?1:((float)usable_height/max_pos);

				  int	prev_x	= 1;
				  int	prev_y	= 1;

				  for (int i=0;i<segs.length;i++){

					  int[]	seg = segs[i];

					  int	next_x	= (int)((seg[0])*metric_ratio) + 1;
					  int	next_y 	= (int)((seg[1] + (seg[2]-seg[1])/2)*pos_ratio) + 1;

					  gc.drawLine(
							y_axis_bottom_x + prev_x,
							y_axis_bottom_y - prev_y,
							y_axis_bottom_x + next_x,
							y_axis_bottom_y - next_y );

					  prev_x = next_x;
					  prev_y = next_y;
				  }
			  }

			  gc.setForeground( dark?Colors.grey:Colors.black );
		  }

		  SpeedManagerLimitEstimate[] bad_down = mapper.getBadDownloadHistory();

		  if ( bad_down.length > 0 ){

			  gc.setForeground( Colors.red );

			  gc.setLineWidth( 3 );

			  for (int i=0;i<bad_down.length;i++){

				  int speed = bad_down[i].getBytesPerSec();

				  int	y = max_y==0?0:( speed * usable_height / max_y );

				  gc.drawLine(
						  	y_axis_bottom_x + 0,
						  	y_axis_bottom_y - y,
							y_axis_bottom_x + 10,
							y_axis_bottom_y - y );
			  }

			  gc.setForeground( dark?Colors.grey:Colors.black );

			  gc.setLineWidth( 1 );
		  }

		  String	y_text = labels[1] + " - " + formatters[1].format( max_y+1 );

		  gc.setForeground( dark?Colors.white:Colors.black );
		  
		  gc.drawText( y_text, y_axis_top_x+4, y_axis_top_y + 2, SWT.DRAW_TRANSPARENT );

		  gc.drawText( title, ( bounds.width - title.length()*char_width )/2, 1, SWT.DRAW_TRANSPARENT );

		  gc.dispose();

		  return( image );
	  }

	  protected void
	  dispose()
	  {
			if ( buffer_image != null && ! buffer_image.isDisposed()){

				buffer_image.dispose();
			}

			COConfigurationManager.removeParameterListener("Graphics Update",this);
	  }
  }
  
  	private volatile long	history_scale_div				= 1;
  	private volatile int	history_selected_span_suffix	= TimeFormatter.TS_WEEK;
  	
  	private int				history_period_suffix;
  	
  	private Label			history_resolution;
  	private Label			history_total_down;
  	private Label			history_total_up;
  	
	private void 
	createHistoryPanel() 
	{
		Composite controlPanel = new Composite(historyPanel,SWT.NULL);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);

		controlPanel.setLayoutData(gd);
		controlPanel.setLayout( new GridLayout( 8, false ));
		
		Combo combo = new Combo( controlPanel, SWT.SINGLE | SWT.READ_ONLY );
		
		for ( int i=TimeFormatter.TS_HOUR; i<= TimeFormatter.TS_YEAR; i++ ){
			
			String str = TimeFormatter.TIME_SUFFIXES_2_LONG[i];
			
			if ( str.length() > 1 ){
				
				str = Character.toUpperCase(str.charAt(0)) + str.substring(1);
			}
			
			combo.add( str );
		}
				
		combo.select( history_selected_span_suffix - TimeFormatter.TS_HOUR );
		
		combo.addListener( SWT.Selection, (ev)->{
			
			int index = combo.getSelectionIndex();
			
			history_selected_span_suffix = TimeFormatter.TS_HOUR + index;
		});
		
		
		
		Label lbl_res = new Label(controlPanel,SWT.NULL);
		lbl_res.setText( MessageText.getString("label.time.resolution") + ":" );
		
		history_resolution = new Label(controlPanel,SWT.NULL);
		
		Label lbl_spacer = new Label(controlPanel,SWT.NULL);
		lbl_spacer.setLayoutData(new GridData( GridData.FILL_HORIZONTAL));
		
		Label lbl_down = new Label(controlPanel,SWT.NULL);
		lbl_down.setText( MessageText.getString("TableColumn.header.tag.downtotal") + ":" );

		history_total_down = new Label(controlPanel,SWT.NULL);

		Label lbl_up = new Label(controlPanel,SWT.NULL);
		lbl_up.setText( MessageText.getString("TableColumn.header.tag.uptotal") + ":" );

		history_total_up = new Label(controlPanel,SWT.NULL);
						
		
		Color[]	colors = {
			Colors.blues[Colors.BLUES_DARKEST],
			Colors.fadedGreen,
		};
		
		ValueFormater formatter =
				new ValueFormater()
		{
			@Override
			public String
			format(
				int value )
			{
				return( DisplayFormatters.formatByteCountToKiBEtc( value  * history_scale_div ));
			}
			
			@Override
			public String 
			formatTime( 
				long time )
			{
				if ( history_selected_span_suffix < TimeFormatter.TS_WEEK ){
					
					return( new SimpleDateFormat( "HH:mm").format(new Date( time )));
					
				}else if ( history_selected_span_suffix == TimeFormatter.TS_WEEK ){
					
					return( new SimpleDateFormat( "dd HH:mm").format(new Date( time )));
					
				}else{
					
					return( new SimpleDateFormat( "MM/dd").format(new Date( time )));
				}
			}
		};


		ValueSourceImpl[] sources = {
			new ValueSourceImpl( "Down", 0, colors, false, false, false )
			{
				@Override
				public int
				getValue()
				{
					Debug.out( "eh?" );

					return(0);
				}
			},
			new ValueSourceImpl( "Up", 1, colors, true, false, false )
			{
				@Override
				public int
				getValue()
				{
					Debug.out( "eh?" );

					return(0);
				}
			}
		};

		Monitor[] monitors = historyPanel.getDisplay().getMonitors();
		
		int total_width = 0;
		
		for ( Monitor m: monitors ){
			
			total_width += m.getClientArea().width;
		}
		
		history_mpg = MultiPlotGraphic.getInstance( total_width, sources, formatter );


		String[] color_configs = new String[] {
			"TransferStats.history.legend.down",
			"TransferStats.history.legend.up",
		};

		Legend.LegendListener legend_listener =
				new Legend.LegendListener()
		{
			private int	hover_index = -1;

			@Override
			public void
			hoverChange(
					boolean 	entry,
					int 		index )
			{
				if ( hover_index != -1 ){

					sources[hover_index].setHover( false );
				}

				if ( entry ){

					hover_index = index;

					sources[index].setHover( true );
				}

				history_mpg.refresh( true );
			}

			@Override
			public void
			visibilityChange(
					boolean	visible,
					int		index )
			{
				sources[index].setVisible( visible );

				history_mpg.refresh( true );
			}
		};

		Composite graphPanel = new Composite(historyPanel,SWT.NULL);
		gd = new GridData(GridData.FILL_BOTH);

		graphPanel.setLayoutData(gd);
		graphPanel.setLayout(Utils.getSimpleGridLayout(1));

		gd = new GridData(GridData.FILL_HORIZONTAL);

		Legend.createLegendComposite(historyPanel, colors, color_configs, null, gd, true, legend_listener );

		Canvas speedCanvas = new Canvas(graphPanel,SWT.NO_BACKGROUND);
		gd = new GridData(GridData.FILL_BOTH);
		speedCanvas.setLayoutData(gd);

		history_mpg.initialize( speedCanvas, false );
	}
	
	private long history_last_span;
	private long history_last_period;
	private long history_last_width;
	
	private void
	refreshHistory()
	{
		if ( !historyPanel.isVisible()){
			
			return;
		}
		
		int span_suffix = history_selected_span_suffix;;
				
		long 	span	= TimeFormatter.TIME_SUFFIXES_2_MULT[span_suffix] * 1000;
		
		int period_suffix;
				
		if ( span_suffix == TimeFormatter.TS_HOUR ){
			
			period_suffix	= TimeFormatter.TS_MINUTE;
			
		}else if ( span_suffix == TimeFormatter.TS_DAY ){
			
			period_suffix	= TimeFormatter.TS_MINUTE;
			
		}else if ( span_suffix == TimeFormatter.TS_WEEK ){
			
			period_suffix	= TimeFormatter.TS_HOUR;
			
		}else if ( span_suffix == TimeFormatter.TS_MONTH ){
			
			period_suffix	= TimeFormatter.TS_HOUR;
			
		}else{
			
			period_suffix = TimeFormatter.TS_DAY;
		}

		long	period = TimeFormatter.TIME_SUFFIXES_2_MULT[ period_suffix ] * 1000;

		int current_width = history_mpg.getVisibleEntryCount();

		if ( current_width <= 0 ){
			
			return;
		}
		
		if ( 	history_last_span == span &&
				history_last_period == period &&
				history_last_width == current_width ){
			
			return;
		}
		
		int	entries = (int)( span / period );
		
		LongTermStats lt_stats = StatsFactory.getLongTermStats();

		long end_time 	= SystemTime.getCurrentTime();
		
		end_time = ((end_time+(period-1))/period)*period;
		
		long start_time = end_time - span;
					
		long[] overall_stats = getTotalUsageInPeriod( lt_stats, start_time, end_time );

		double chunk = current_width/(double)entries;
		
		long[][]	data	= new long[2][current_width];
		long[]		times	= new long[current_width];

		int pos = 0;
		
		int last_time = -1;
				
		long	max_data = 0;
		
		long pending_down	= 0;
		long pending_up		= 0;
		
		for ( int i=0;i<entries;i++){
			
			int this_chunk;
			
			if ( i==entries-1){
				this_chunk = current_width - pos;
			}else{
				this_chunk = (int)(i*chunk) - pos;
			}
			
			if ( last_time == -1 || pos - last_time > 50 ){
				times[pos+this_chunk/2] = start_time;
				
				last_time = pos;
			}
			
			Date start_date	= new Date( start_time );
			Date end_date	= new Date( start_time+period );
						
			long[] stats = lt_stats.getTotalUsageInPeriod(start_date, end_date );

			long downloaded = stats[LongTermStats.ST_DATA_DOWNLOAD];
			long uploaded	= stats[LongTermStats.ST_DATA_UPLOAD];
						
			if ( this_chunk == 0 ){
			
				pending_down 	+= downloaded;
				pending_up		+= uploaded;
				
			}else{
				
				downloaded	+= pending_down;
				uploaded	+= pending_up;
				
				pending_down	= 0;
				pending_up		= 0;
				
				max_data = Math.max( max_data, downloaded );
				max_data = Math.max( max_data, uploaded );

				for ( int j=0;j<this_chunk;j++){
					
					data[0][pos] = downloaded;
					data[1][pos] = uploaded;
					
					pos++;
				}
			}
						
			start_time += period;
		}
		
		long total_down = overall_stats[LongTermStats.ST_DATA_DOWNLOAD];
		long total_up = overall_stats[LongTermStats.ST_DATA_UPLOAD];
		
		String str = TimeFormatter.TIME_SUFFIXES_2_LONG[ period_suffix ];
		
		if ( str.length() > 1 ){
			
			str = Character.toUpperCase(str.charAt(0)) + str.substring(1);
		}
		
		history_resolution.setText( str );
		
		history_total_down.setText( DisplayFormatters.formatByteCountToKiBEtc( total_down ));
		history_total_up.setText( DisplayFormatters.formatByteCountToKiBEtc( total_up ));
		
		history_resolution.getParent().layout( true );
		
		int div = 1;
	
		while ( max_data > Integer.MAX_VALUE ){
				
			div			*= 2;
			max_data	/= 2;
		}
				
		history_scale_div		= div;
		history_period_suffix	= period_suffix;
		
		int[][]	idata	= new int[2][current_width];
		
		for ( int i=0;i<2;i++){
			
			long[] d = data[i];
			
			int[] id = idata[i];
			
			for (int j=0;j<current_width;j++){
				
				id[j] = (int)(d[j]/div);
			}
		}
		
		reverse( times );
		
		history_mpg.reset( idata, times );
		
		history_last_span		= span;
		history_last_period		= period;
		history_last_width		= current_width;
	}

	private long[]
	getTotalUsageInPeriod(
		LongTermStats	lt_stats,
		long			start,
		long			end )
	{		
		long[] r1 = lt_stats.getTotalUsageInPeriod(new Date( start ), new Date( end ));
		
		/*
		LongTermStats.RecordAccepter accepter = (t)->true; 
		long[] r2 = lt_stats.getTotalUsageInPeriod(new Date( start ), new Date( end ),accepter);

		for ( int i=0; i<r1.length; i++ ){
			if ( r1[i] != r2[i] ){
				System.out.println( "diff" );
			}
		}
		*/
		
		return( r1 );
	}
	
	private void
	reverse(
		long[]	a )
	{
		int len = a.length;
		
		for ( int i=0;i<len/2;i++){
			long t = a[i];
			a[i] = a[len-i-1];
			a[len-i-1] = t;
		}
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
		case UISWTViewEvent.TYPE_CREATE:
			swtView = (UISWTView)event.getData();
			swtView.setTitle(MessageText.getString(MSGID_PREFIX + ".title.full"));
			break;

		case UISWTViewEvent.TYPE_DESTROY:
			delete();
			break;

		case UISWTViewEvent.TYPE_INITIALIZE:
			initialize((Composite)event.getData());
			break;

		case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
			Messages.updateLanguageForControl(getComposite());
			break;

		case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
			break;

		case UISWTViewEvent.TYPE_SHOWN:
			// weird layout issue with general panel - switch away from view and then back leaves bottom line not rendering - this fixes it
			if ( generalPanel != null ){
				generalPanel.layout( true, true );
			}
			break;

		case UISWTViewEvent.TYPE_REFRESH:
			refresh();
			break;

		case StatsView.EVENT_PERIODIC_UPDATE:
			periodicUpdate();
			break;
		}

		return true;
	}
	
	private abstract static class
	ValueSourceImpl
		implements ValueSource
	{
		private String			name;
		private int				index;
		private Color[]			colours;
		private boolean			is_up;
		private boolean			trimmable;

		private boolean			is_hover;
		private boolean			is_invisible;
		private boolean			is_dotted;

		private
		ValueSourceImpl(
			String					_name,
			int						_index,
			Color[]					_colours,
			boolean					_is_up,
			boolean					_trimmable,
			boolean					_is_dotted )
		{
			name			= _name;
			index			= _index;
			colours			= _colours;
			is_up			= _is_up;
			trimmable		= _trimmable;
			is_dotted		= _is_dotted;
		}

		@Override
		public String
		getName()
		{
			return( name );
		}

		@Override
		public Color
		getLineColor()
		{
			return( colours[index] );
		}

		@Override
		public boolean
		isTrimmable()
		{
			return( trimmable );
		}

		private void
		setHover(
			boolean	h )
		{
			is_hover = h;
		}

		private void
		setVisible(
			boolean	visible )
		{
			is_invisible = !visible;
		}

		@Override
		public int
		getStyle()
		{
			if ( is_invisible ){

				return( STYLE_INVISIBLE );
			}

			int	style = STYLE_NONE;

			if ( is_hover ){

				style |= STYLE_BOLD;
			}

			style |= STYLE_HIDE_LABEL;

			return( style );
		}

		@Override
		public int
		getAlpha()
		{
			return( is_dotted?128:255 );
		}
	}
}

