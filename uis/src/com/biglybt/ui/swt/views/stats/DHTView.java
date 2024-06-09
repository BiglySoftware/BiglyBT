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


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.graphics.PingGraphic;
import com.biglybt.ui.swt.components.graphics.SpeedGraphic;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.views.IViewRequiresPeriodicUpdates;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.DHTStorageAdapter;
import com.biglybt.core.dht.control.DHTControlActivity;
import com.biglybt.core.dht.control.DHTControlListener;
import com.biglybt.core.dht.control.DHTControlStats;
import com.biglybt.core.dht.db.DHTDBStats;
import com.biglybt.core.dht.nat.DHTNATPuncher;
import com.biglybt.core.dht.router.DHTRouterStats;
import com.biglybt.core.dht.transport.DHTTransport;
import com.biglybt.core.dht.transport.DHTTransportFullStats;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.plugin.dht.DHTPlugin;

/**
 *
 */
public class DHTView
	implements UISWTViewEventListener, IViewRequiresPeriodicUpdates
{

  public static final int DHT_TYPE_MAIN 	= DHT.NW_AZ_MAIN;
  public static final int DHT_TYPE_CVS  	= DHT.NW_AZ_CVS;
  public static final int DHT_TYPE_MAIN_V6 	= DHT.NW_AZ_MAIN_V6;
  public static final int DHT_TYPE_BIGLYBT 	= DHT.NW_BIGLYBT_MAIN;
  
  public static final String MSGID_PREFIX = "DHTView";

  public static Color[] rttColours = new Color[] {
	  	Colors.grey, Colors.fadedGreen,Colors.fadedRed };

  private static Map<String,int[]>	table_col_map = new HashMap<>();

  private boolean auto_dht;

  DHT dht;

  Composite panel;

  String	yes_str;
  String	no_str;

  Label lblUpTime,lblNumberOfUsers;
  Label lblNodes,lblLeaves;
  Label lblContacts,lblReplacements,lblLive,lblUnknown,lblDying;
  Label lblSkew, lblRendezvous, lblReachable;
  Label lblKeys,lblValues,lblSize;
  Label lblLocal,lblDirect,lblIndirect;
  Label lblDivFreq,lblDivSize;

  BufferedLabel lblTransportAddress;
  Label lblReceivedPackets,lblReceivedBytes;
  Label lblSentPackets,lblSentBytes;


  Label lblPings[] = new Label[4];
  Label lblFindNodes[] = new Label[4];
  Label lblFindValues[] = new Label[4];
  Label lblStores[] = new Label[4];
  Label lblData[] = new Label[4];

  Canvas  in,out,rtt;
  SpeedGraphic inGraph,outGraph;
  PingGraphic rttGraph;

  boolean activityChanged;
  DHTControlListener controlListener;
  Table activityTable;
  DHTControlActivity[] activities;

  private String 	id;
  
  private int dht_type;
  protected Core core;


  public DHTView()
  {
	  this( true );
  }
  public DHTView( boolean _auto_dht ) {
	auto_dht = _auto_dht;
    inGraph = SpeedGraphic.getInstance();
    outGraph = SpeedGraphic.getInstance();
    rttGraph = PingGraphic.getInstance();

    rttGraph.setColors( rttColours );
    rttGraph.setExternalAverage( true );
  }

  private void init(Core core) {
    try {
      PluginInterface dht_pi = core.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

      if ( dht_pi == null ){

    	  return;
      }

      DHT[] dhts = ((DHTPlugin)dht_pi.getPlugin()).getDHTs();

      for (int i=0;i<dhts.length;i++){
    	  if ( dhts[i].getTransport().getNetwork() == dht_type ){
    		  dht = dhts[i];
    		  break;
    	  }
      }

      if ( dht == null ){

    	  return;
      }

      controlListener = new DHTControlListener() {
        @Override
        public void activityChanged(DHTControlActivity activity, int type) {
          activityChanged = true;
        }
      };
      dht.getControl().addListener(controlListener);

    } catch(Exception e) {
      Debug.printStackTrace( e );
    }
  }

  public void
  setDHT(
	DHT		_dht )
  {
	  if ( dht == null ){

		  dht	= _dht;

		  controlListener = new DHTControlListener() {
		        @Override
		        public void activityChanged(DHTControlActivity activity, int type) {
		          activityChanged = true;
		        }
		      };
		  dht.getControl().addListener(controlListener);

	  }else if ( dht == _dht ){

	  }else{

		  Debug.out( "Not Supported ");
	  }
  }

  public void initialize(Composite composite) {
	if ( auto_dht ){
	  	CoreFactory.addCoreRunningListener(new CoreRunningListener() {

				@Override
				public void coreRunning(Core core) {
					DHTView.this.core = core;
					init(core);
				}
			});
	}

  	panel = new Composite(composite,SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    panel.setLayout(layout);

    yes_str = MessageText.getString( "Button.yes").replaceAll("&", "");
    no_str 	= MessageText.getString( "Button.no").replaceAll("&", "");

    initialiseGeneralGroup();
    initialiseDBGroup();

    initialiseTransportDetailsGroup();
    initialiseOperationDetailsGroup();

    initialiseActivityGroup();
  }

  private void initialiseGeneralGroup() {
    Group gGeneral = Utils.createSkinnedGroup(panel,SWT.NONE);
    Messages.setLanguageText(gGeneral, "DHTView.general.title" );

    GridData data = new GridData();
    data.verticalAlignment = SWT.BEGINNING;
    data.widthHint = 400;
    gGeneral.setLayoutData(data);

    GridLayout layout = new GridLayout();
    layout.numColumns = 6;
    gGeneral.setLayout(layout);

    	// row1

    Label label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.uptime");

    lblUpTime = new Label(gGeneral,SWT.NONE);
    lblUpTime.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.users");

    lblNumberOfUsers = new Label(gGeneral,SWT.NONE);
    lblNumberOfUsers.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.reachable");

    lblReachable = new Label(gGeneral,SWT.NONE);
    lblReachable.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));


    	// row 2

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.nodes");

    lblNodes = new Label(gGeneral,SWT.NONE);
    lblNodes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.leaves");

    lblLeaves = new Label(gGeneral,SWT.NONE);
    lblLeaves.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.rendezvous");

    lblRendezvous = new Label(gGeneral,SWT.NONE);
    lblRendezvous.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    	// row 3

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.contacts");

    lblContacts = new Label(gGeneral,SWT.NONE);
    lblContacts.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.replacements");

    lblReplacements = new Label(gGeneral,SWT.NONE);
    lblReplacements.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.live");

    lblLive= new Label(gGeneral,SWT.NONE);
    lblLive.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    	// row 4

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.skew");

    lblSkew= new Label(gGeneral,SWT.NONE);
    lblSkew.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.unknown");

    lblUnknown = new Label(gGeneral,SWT.NONE);
    lblUnknown.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.dying");

    lblDying = new Label(gGeneral,SWT.NONE);
    lblDying.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
  }

  private void initialiseDBGroup() {
    Group gDB = Utils.createSkinnedGroup(panel,SWT.NONE);
    Messages.setLanguageText(gDB,"DHTView.db.title");

    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.verticalAlignment = SWT.FILL;
    gDB.setLayoutData(data);

    GridLayout layout = new GridLayout();
    layout.numColumns = 6;
    layout.makeColumnsEqualWidth = true;
    gDB.setLayout(layout);

    Label label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.keys");

    lblKeys = new Label(gDB,SWT.NONE);
    lblKeys.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.values");

    lblValues = new Label(gDB,SWT.NONE);
    lblValues.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"TableColumn.header.size");

    lblSize = new Label(gDB,SWT.NONE);
    lblSize.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.local");

    lblLocal = new Label(gDB,SWT.NONE);
    lblLocal.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.direct");

    lblDirect = new Label(gDB,SWT.NONE);
    lblDirect.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.indirect");

    lblIndirect = new Label(gDB,SWT.NONE);
    lblIndirect.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));


    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.divfreq");

    lblDivFreq = new Label(gDB,SWT.NONE);
    lblDivFreq.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gDB,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.db.divsize");

    lblDivSize = new Label(gDB,SWT.NONE);
    lblDivSize.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
  }

  private void initialiseTransportDetailsGroup() {
    Group gTransport = Utils.createSkinnedGroup(panel,SWT.NONE);
    Messages.setLanguageText(gTransport,"DHTView.transport.title");

    GridData data = new GridData(GridData.FILL_VERTICAL);
    data.widthHint = 400;
    data.verticalSpan = 2;
    gTransport.setLayoutData(data);

    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    layout.makeColumnsEqualWidth = true;
    gTransport.setLayout(layout);


    lblTransportAddress = new BufferedLabel(gTransport,SWT.DOUBLE_BUFFERED);
    data = new GridData(SWT.FILL,SWT.TOP,true,false);
    data.horizontalSpan = 3;
    lblTransportAddress.setLayoutData(data);

    
    Label label = new Label(gTransport,SWT.NONE);

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.packets");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.bytes");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.received");

    lblReceivedPackets = new Label(gTransport,SWT.NONE);
    lblReceivedPackets.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    lblReceivedBytes = new Label(gTransport,SWT.NONE);
    lblReceivedBytes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.sent");

    lblSentPackets = new Label(gTransport,SWT.NONE);
    lblSentPackets.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    lblSentBytes = new Label(gTransport,SWT.NONE);
    lblSentBytes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.in");
    data = new GridData();
    data.horizontalSpan = 3;
    label.setLayoutData(data);

    in = new Canvas(gTransport,SWT.NO_BACKGROUND);
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    in.setLayoutData(data);
    inGraph.initialize(in);

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.out");
    data = new GridData();
    data.horizontalSpan = 3;
    label.setLayoutData(data);

    out = new Canvas(gTransport,SWT.NO_BACKGROUND);
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    out.setLayoutData(data);
    outGraph.initialize(out);

    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.rtt");
    data = new GridData();
    data.horizontalSpan = 3;
    label.setLayoutData(data);

    rtt = new Canvas(gTransport,SWT.NO_BACKGROUND);
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    rtt.setLayoutData(data);
    rttGraph.initialize(rtt);

    data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 3;

	Legend.createLegendComposite(
			gTransport,
			rttColours,
    		new String[]{
        			"DHTView.rtt.legend.average",
        			"DHTView.rtt.legend.best",
        			"DHTView.rtt.legend.worst" },
    				data );
  }

  private void initialiseOperationDetailsGroup() {
    Group gOperations = Utils.createSkinnedGroup(panel,SWT.NONE);
    Messages.setLanguageText(gOperations,"DHTView.operations.title");
    gOperations.setLayoutData(new GridData(SWT.FILL,SWT.BEGINNING,true,false));

    GridLayout layout = new GridLayout();
    layout.numColumns = 5;
    layout.makeColumnsEqualWidth = true;
    gOperations.setLayout(layout);


    Label label = new Label(gOperations,SWT.NONE);

    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.sent");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.ok");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.failed");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));

    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.received");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));


    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.ping");

    for(int i = 0 ; i < 4 ; i++) {
      lblPings[i] = new Label(gOperations,SWT.NONE);
      lblPings[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }


    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.findNode");

    for(int i = 0 ; i < 4 ; i++) {
      lblFindNodes[i] = new Label(gOperations,SWT.NONE);
      lblFindNodes[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }


    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.findValue");

    for(int i = 0 ; i < 4 ; i++) {
      lblFindValues[i] = new Label(gOperations,SWT.NONE);
      lblFindValues[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }


    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.store");

    for(int i = 0 ; i < 4 ; i++) {
      lblStores[i] = new Label(gOperations,SWT.NONE);
      lblStores[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }

    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.data");

    for(int i = 0 ; i < 4 ; i++) {
      lblData[i] = new Label(gOperations,SWT.NONE);
      lblData[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }
  }

  private void initialiseActivityGroup() {
    Group gActivity = Utils.createSkinnedGroup(panel,SWT.NONE);
    Messages.setLanguageText(gActivity,"DHTView.activity.title");
    gActivity.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
    gActivity.setLayout(new GridLayout());

    activityTable = new Table(gActivity,SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
    activityTable.setLayoutData(new GridData(GridData.FILL_BOTH));

    TableColumn colStatus =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colStatus,"DHTView.activity.status");

    TableColumn colType =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colType,"DHTView.activity.type");
 
    TableColumn colName =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colName,"DHTView.activity.target");

    TableColumn colDetails =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colDetails,"label.details");
    colDetails.setResizable(false);

    TableColumn[] columns = { colStatus, colType, colName, colDetails };
    
    int[]	col_widths = { 80, 80, 80, 300 };
    
    if ( id != null ){
    	
    	int[] widths = table_col_map.get( id );
    	
    	if ( widths != null ){
    		
    		col_widths = widths;
    	}
    }

    for ( int i=0;i<columns.length;i++){
    	columns[i].setWidth( col_widths[i]);
    }
    
    activityTable.setHeaderVisible(true);
    Listener computeLastRowWidthListener = new Listener() {
    	// inUse flag to prevent a SWT stack overflow.  For some reason
    	// the setWidth call was triggering a resize.
    	boolean inUse = false;
      @Override
      public void handleEvent(Event event) {
      	if (inUse) {
      		return;
      	}

      	inUse = true;
       	try {
          if(activityTable == null || activityTable.isDisposed()) return;
          
          int totalWidth = activityTable.getClientArea().width;
          
          int[] widths = new int[columns.length];
          
          int remainingWidth = totalWidth;
          
          for ( int i=0;i<widths.length-1;i++){
        	  remainingWidth -=  widths[i] = columns[i].getWidth();
          }
         
          TableColumn lastCol = columns[columns.length-1];
          
          if(remainingWidth > 0){
        	  lastCol.setWidth(remainingWidth);
          }
          
          widths[ columns.length-1 ] = lastCol.getWidth();
         
          if ( id != null ){
        	  table_col_map.put( id, widths );
          }
       	} finally {
      		inUse = false;
      	}
      }
    };
    activityTable.addListener(SWT.Resize, computeLastRowWidthListener);
    colStatus.addListener(SWT.Resize,computeLastRowWidthListener);
    colType.addListener(SWT.Resize,computeLastRowWidthListener);
    colName.addListener(SWT.Resize,computeLastRowWidthListener);

    activityTable.addListener(SWT.SetData, new Listener() {
      @Override
      public void handleEvent(Event event) {
        TableItem item = (TableItem) event.item;
        int index = activityTable.indexOf (item);
        item.setText (0,MessageText.getString("DHTView.activity.status." + activities[index].isQueued()));
        item.setText (1,MessageText.getString("DHTView.activity.type." + activities[index].getType()));
        item.setText (2,ByteFormatter.nicePrint(activities[index].getTarget()));
        item.setText (3,activities[index].getDescription());
      }
    });

	Menu menu = new Menu(activityTable);

	ClipboardCopy.addCopyToClipMenu(
			menu,
			new ClipboardCopy.copyToClipProvider(){
				
				@Override
				public String getText(){
					StringBuffer b = new StringBuffer( activities.length * 256 );
					
					for ( DHTControlActivity act: activities ){
						b.append( "\"");
						b.append( MessageText.getString("DHTView.activity.status." + act.isQueued()));
						b.append( "\", \"" );
						b.append( MessageText.getString("DHTView.activity.type." + act.getType()));
						b.append( "\", \"" );;
						b.append( ByteFormatter.nicePrint(act.getTarget()));
						b.append( "\", \"" );
						b.append( act.getDescription());
						b.append( "\"\n" );
					}
						
					return( b.toString());
				}
			});	
	
    activityTable.setMenu( menu );
  }


  public void delete() {
    Utils.disposeComposite(panel);
    if (dht != null) {
      dht.getControl().removeListener(controlListener);
    }
    outGraph.dispose();
    inGraph.dispose();
    rttGraph.dispose();
  }

  private String getTitleID() {
	  if ( dht_type == DHT_TYPE_MAIN ){

		  return( "DHTView.title.full" );

	  }else if ( dht_type == DHT_TYPE_CVS ){

		  return( "DHTView.title.fullcvs" );
		  
	  }else if ( dht_type == DHT_TYPE_BIGLYBT ){

		  return( "DHTView.title.biglybt" );

	  }else{

		  return( "DHTView.title.full_v6" );
	  }
  }

  private Composite getComposite() {
    return panel;
  }

  private void refresh() {
	  if ( panel == null || panel.isDisposed()){
		  return;
	  }

	  // need to do these here otherwise they sit in an unpainted state
	  inGraph.refresh(false);
	  outGraph.refresh(false);
	  rttGraph.refresh();

	  if (dht == null) {
		  if (core != null) {
			  // keep trying until dht is avail
			  init(core);
		  }
		  return;
	  }

	  refreshGeneral();
	  refreshDB();
	  refreshTransportDetails();
	  refreshOperationDetails();
	  refreshActivity();
  }

  private void refreshGeneral() {
    DHTControlStats controlStats = dht.getControl().getStats();
    DHTRouterStats routerStats = dht.getRouter().getStats();
    DHTTransport transport = dht.getTransport();
    DHTTransportStats transportStats = transport.getStats();
    lblUpTime.setText(TimeFormatter.format(controlStats.getRouterUptime() / 1000));
    lblNumberOfUsers.setText("" + controlStats.getEstimatedDHTSize());
    int percent = transportStats.getRouteablePercentage();
    lblReachable.setText((transport.isReachable()?yes_str:no_str) + (percent==-1?"":(" " + percent+"%")));

    DHTNATPuncher puncher = dht.getNATPuncher();

    String	puncher_str;

    if ( puncher == null ){
    	puncher_str = "";
    }else{
    	puncher_str = puncher.operational()?yes_str:no_str;
    }

    lblRendezvous.setText(transport.isReachable()?"":puncher_str);
    long[] stats = routerStats.getStats();
    lblNodes.setText("" + stats[DHTRouterStats.ST_NODES]);
    lblLeaves.setText("" + stats[DHTRouterStats.ST_LEAVES]);
    lblContacts.setText("" + stats[DHTRouterStats.ST_CONTACTS]);
    lblReplacements.setText("" + stats[DHTRouterStats.ST_REPLACEMENTS]);
    lblLive.setText("" + stats[DHTRouterStats.ST_CONTACTS_LIVE]);
    lblUnknown.setText("" + stats[DHTRouterStats.ST_CONTACTS_UNKNOWN]);
    lblDying.setText("" + stats[DHTRouterStats.ST_CONTACTS_DEAD]);

    long skew_average = transportStats.getSkewAverage();

    lblSkew.setText( skew_average==0?"":(skew_average<0?"-":"") + TimeFormatter.format100ths( Math.abs(skew_average )));
  }

  private int refreshIter = 0;
	private UISWTView swtView;

  private void refreshDB() {
    if(refreshIter == 0) {
	  DHTDBStats    dbStats = dht.getDataBase().getStats();
      lblKeys.setText("" + dbStats.getKeyCount() + " (" + dbStats.getLocalKeyCount() + ")" );
      int[] stats = dbStats.getValueDetails();
      lblValues.setText("" + stats[DHTDBStats.VD_VALUE_COUNT]);
      lblSize.setText(DisplayFormatters.formatByteCountToKiBEtc(dbStats.getSize()));
      lblDirect.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_DIRECT_SIZE]));
      lblIndirect.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_INDIRECT_SIZE]));
      lblLocal.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_LOCAL_SIZE]));

      DHTStorageAdapter sa = dht.getStorageAdapter();

      String rem_freq;
      String rem_size;

      if ( sa == null ){
    	  rem_freq = "-";
    	  rem_size = "-";
      }else{
    	  rem_freq = "" + sa.getRemoteFreqDivCount();
    	  rem_size = "" + sa.getRemoteSizeDivCount();
      }

      lblDivFreq.setText("" + stats[DHTDBStats.VD_DIV_FREQ] + " (" + rem_freq + ")");
      lblDivSize.setText("" + stats[DHTDBStats.VD_DIV_SIZE] + " (" + rem_size + ")");
    } else {
      refreshIter++;
      if(refreshIter == 100) refreshIter = 0;
    }

  }

  private void refreshTransportDetails() {
    DHTTransportStats   transportStats = dht.getTransport().getStats();
    lblTransportAddress.setText( AddressUtils.getHostAddress( dht.getTransport().getLocalContact().getExternalAddress()));
    lblReceivedBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesReceived()));
    lblSentBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesSent()));
    lblReceivedPackets.setText("" + transportStats.getPacketsReceived());
    lblSentPackets.setText("" + transportStats.getPacketsSent());
  }

  private void refreshOperationDetails() {
    DHTTransportStats   transportStats = dht.getTransport().getStats();
    long[] pings = transportStats.getPings();
    for(int i = 0 ; i < 4 ; i++) {
      lblPings[i].setText("" + pings[i]);
    }

    long[] findNodes = transportStats.getFindNodes();
    for(int i = 0 ; i < 4 ; i++) {
      lblFindNodes[i].setText("" + findNodes[i]);
    }

    long[] findValues = transportStats.getFindValues();
    for(int i = 0 ; i < 4 ; i++) {
      lblFindValues[i].setText("" + findValues[i]);
    }

    long[] stores 	= transportStats.getStores();
    long[] qstores 	= transportStats.getQueryStores();

    for(int i = 0 ; i < 4 ; i++) {
      lblStores[i].setText("" + stores[i] + " (" + qstores[i] + ")");
    }
    long[] data = transportStats.getData();
    for(int i = 0 ; i < 4 ; i++) {
      lblData[i].setText("" + data[i]);
    }
  }

  private void refreshActivity() {
    if(activityChanged) {
      activityChanged = false;
      activities = dht.getControl().getActivities();
      activityTable.setItemCount(activities.length);
      activityTable.clearAll();
      //Dunno if still needed?
      activityTable.redraw();
    }
  }

  public void periodicUpdate() {
    if(dht == null) return;

    DHTTransportFullStats fullStats = dht.getTransport().getLocalContact().getStats();
    if ( fullStats != null ){
    	inGraph.addIntValue((int)fullStats.getAverageBytesReceived());
    	outGraph.addIntValue((int)fullStats.getAverageBytesSent());
    }
    DHTTransportStats stats = dht.getTransport().getStats();
    int[] rtts = stats.getRTTHistory().clone();

    Arrays.sort( rtts );

    int	rtt_total = 0;
    int	rtt_num		= 0;

    int	start = 0;

    for ( int rtt: rtts ){

    	if ( rtt > 0 ){
    		rtt_total += rtt;
    		rtt_num++;
    	}else{
    		start++;
    	}
    }

    int	average = 0;
    int	best	= 0;
    int worst	= 0;


    if ( rtt_num > 0 ){
    	average = rtt_total/rtt_num;
    }

    int chunk = rtt_num/3;

    int	max_best 	= start+chunk;
    int min_worst	= rtts.length-1-chunk;

    int	worst_total = 0;
    int	worst_num	= 0;

    int best_total	= 0;
    int	best_num	= 0;

    for ( int i=start;i<rtts.length;i++){

    	if ( i < max_best ){
    		best_total+=rtts[i];
    		best_num++;
    	}else if ( i > min_worst ){
    		worst_total+=rtts[i];
        	worst_num++;
    	}
    }

    if ( best_num > 0 ){
    	best = best_total/best_num;
    }

    if ( worst_num > 0 ){
    	worst = worst_total/worst_num;
    }

    rttGraph.addIntsValue( new int[]{ average,best,worst });
  }

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
	      if (swtView.getInitialDataSource() instanceof Number) {
		      dht_type = ((Number) swtView.getInitialDataSource()).intValue();
		      id = String.valueOf( dht_type );
	      }
      	swtView.setTitle(MessageText.getString(getTitleID()));
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
    		if (swtView != null) {
        	swtView.setTitle(MessageText.getString(getTitleID()));
    		}
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	if (event.getData() instanceof Number) {
      		dht_type = ((Number) event.getData()).intValue();
      		id = String.valueOf( dht_type );
      		if (swtView != null) {
          	swtView.setTitle(MessageText.getString(getTitleID()));
      		}
      	}
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
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
}


