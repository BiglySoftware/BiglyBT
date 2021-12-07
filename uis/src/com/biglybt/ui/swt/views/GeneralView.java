/*
 * Created on 2 juil. 2003
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
package com.biglybt.ui.swt.views;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.DateWindow;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.views.piece.PieceInfoView;
import com.biglybt.util.MapUtils;

/**
 * View of General information on the torrent
 * <p>
 * See also {@link com.biglybt.ui.swt.views.skin.SBC_TorrentDetailsView}
 *
 * @author Olivier
 *
 */
public class GeneralView
	implements ParameterListener, UISWTViewCoreEventListener, UIPluginViewToolBarListener
{
	public static final String MSGID_PREFIX = "GeneralView";

	private static Color badAvailColor;
	private static Color transferringColor;
	  
	private static boolean showTransferring;
	
	  static{
		  COConfigurationManager.addAndFireParameterListener(
			"generalview.avail.bad.colour",
			(n)->{
				badAvailColor = Utils.getConfigColor( n, Colors.maroon );
			});
		  
		  COConfigurationManager.addAndFireParameterListener(
		  	"PeersView.BlockView.Transfer",
			(n)->{
				transferringColor = PieceInfoView.getLegendColor( n );
			});
		  
		  COConfigurationManager.addAndFireParameterListener(
			"GeneralView.show.transferring",
			(n)->{
				showTransferring = COConfigurationManager.getBooleanParameter( n, false );
			});
	  }
	  
	protected AEMonitor this_mon 	= new AEMonitor( MSGID_PREFIX );

  private Display display;
  private DownloadManager manager = null;

  int 		piecesStateCache[];
  long 		piecesStateSkippedMarker;
  boolean 	piecesStateFileBoundariesDone;

  int loopFactor;

  Composite genComposite;
  Composite gFile;
  Canvas piecesImage;
  Image pImage;
  BufferedLabel piecesPercent;
  Canvas availabilityImage;
  Image aImage;
  BufferedLabel availabilityPercent;
  Group gTransfer;
  BufferedLabel timeElapsed;
  BufferedLabel timeRemaining;
  BufferedLabel download;
  BufferedLabel downloadSpeed;
  //Text 			maxDLSpeed;
  BufferedLabel upload;
  BufferedLabel uploadSpeed;
  //Text 			maxULSpeed;
  //Text maxUploads;
  BufferedLabel totalSpeed;
  BufferedLabel ave_completion;
  BufferedLabel distributedCopies;
  BufferedLabel seeds;
  BufferedLabel peers;
  BufferedLabel completedLbl;
  Group gInfo;
  BufferedLabel fileName;
  BufferedLabel torrentStatus;
  BufferedLabel fileSize;
  BufferedLabel saveIn;
  BufferedLabel hash;

  BufferedLabel pieceNumber;
  BufferedLabel pieceSize;
  Control lblComment;
  BufferedLabel creation_date;
  MenuItem[]	date_menus;
  BufferedLabel privateStatus;
  Control user_comment;
  BufferedLabel hashFails;
  BufferedLabel shareRatio;

  Canvas		thumbImage;
  Image 		tImage;
  Image			tImageResized;
  String		tImageResizedKey = "";
  
  private int graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");

  private boolean	piecesImageRefreshNeeded;

  private Composite parent;
  private ScrolledComposite	scrolled_comp;
  private UISWTView swtView;

  /**
   * Initialize GeneralView
   */
  public GeneralView() {
  }

	public void dataSourceChanged(Object newDataSource) {
		DownloadManager newManager = ViewUtils.getDownloadManagerFromDataSource( newDataSource, manager );

		if (newManager == manager) {
			return;
		}

		manager = newManager;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_refreshInfo();
			}
		});
	}

  public void initialize(Composite composite) {
  	parent = composite;

  	scrolled_comp = new ScrolledComposite(composite, SWT.V_SCROLL );
	scrolled_comp.setExpandHorizontal(true);
	scrolled_comp.setExpandVertical(true);
	GridLayout layout = new GridLayout();
	layout.horizontalSpacing = 0;
	layout.verticalSpacing = 0;
	layout.marginHeight = 0;
	layout.marginWidth = 0;
	scrolled_comp.setLayout(layout);
	GridData gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
		scrolled_comp.setLayoutData(gridData);

    genComposite = new Canvas(scrolled_comp, SWT.NULL);


    GridLayout genLayout = new GridLayout();
    genLayout.marginHeight = 0;
    try {
    	genLayout.marginTop = 5;
    } catch (NoSuchFieldError e) {
    	// pre 3.1
    }
    genLayout.marginWidth = 2;
    genLayout.numColumns = 1;
    genComposite.setLayout(genLayout);

    scrolled_comp.setContent(genComposite);
	scrolled_comp.addControlListener(new ControlAdapter() {
		@Override
		public void controlResized(ControlEvent e) {
			piecesImageRefreshNeeded = true;
			Utils.updateScrolledComposite(scrolled_comp);
		}
	});


    Utils.execSWTThreadLater(0, new AERunnable() {
			@Override
			public void runSupport() {
				swt_refreshInfo();
			}
		});

    COConfigurationManager.addParameterListener("Graphics Update", this);
  }
  
  private boolean viewBuilt = false;
  

  private void swt_refreshInfo() {
  	if (manager == null || parent == null || parent.isDisposed()){
  		ViewUtils.setViewRequiresOneDownload(genComposite);
  		viewBuilt = false;
  		return;
  	}

  	piecesStateCache = new int[manager.getNbPieces()];

    piecesStateSkippedMarker		= 0;
    piecesStateFileBoundariesDone	= false;

    if ( !viewBuilt ){
    
     	Utils.disposeComposite(genComposite, false);

    	buildView();
    
    	viewBuilt = true;
    }
    
    boolean persistent = manager.isPersistent();

    for ( MenuItem mi: date_menus ){
    	mi.setEnabled( persistent );
    }
    
    updateAvailability();
    updatePiecesInfo(true);

    loadThumb();
    
    refresh( true );
    
    Utils.updateScrolledComposite(scrolled_comp);
    //Utils.changeBackgroundComposite(genComposite,MainWindow.getWindow().getBackground());
  }
  
  private void
  buildView()
  {
    this.display = parent.getDisplay();

    gFile = new Composite(genComposite, SWT.SHADOW_OUT);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gFile.setLayoutData(gridData);
    GridLayout fileLayout = new GridLayout();
    fileLayout.marginHeight = 0;
    fileLayout.marginWidth = 10;
    fileLayout.numColumns = 3;
    gFile.setLayout(fileLayout);

    Label piecesInfo = new Label(gFile, SWT.LEFT);
    Messages.setLanguageText(piecesInfo, "GeneralView.section.downloaded");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    piecesInfo.setLayoutData(gridData);

    piecesImage = new Canvas(gFile, SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 25;
    piecesImage.setLayoutData(gridData);

    Menu piecesMenu = new Menu( piecesImage );
    piecesImage.setMenu( piecesMenu );
    
    MenuItem piecesSubItem = new MenuItem( piecesMenu, SWT.CASCADE );
    Messages.setLanguageText(piecesSubItem, "Button.bar.show" );
    Menu piecesSubMenu = new Menu(piecesMenu.getShell(), SWT.DROP_DOWN);
    piecesSubItem.setMenu( piecesSubMenu );
    piecesSubMenu.addMenuListener(
    	new MenuAdapter(){
    		@Override
    		public void menuShown(MenuEvent e){
    			Utils.clearMenu( piecesSubMenu );
    			
    			MenuItem xferItem = new MenuItem( piecesSubMenu, SWT.CHECK );
    			Messages.setLanguageText(xferItem, "PeersView.BlockView.Transfer" );
    			xferItem.addListener( SWT.Selection, (ev)->{
    				COConfigurationManager.setParameter( "GeneralView.show.transferring", !showTransferring );
    				
    		    });
    			xferItem.setSelection( showTransferring);
    		}});
    
    piecesPercent = new BufferedLabel(gFile, SWT.RIGHT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.widthHint = 50;
    piecesPercent.setLayoutData(gridData);

    Label availabilityInfo = new Label(gFile, SWT.LEFT);
    Messages.setLanguageText(availabilityInfo, "GeneralView.section.availability");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    availabilityInfo.setLayoutData(gridData);

    availabilityImage = new Canvas(gFile, SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 25;
    availabilityImage.setLayoutData(gridData);
    Messages.setLanguageText(availabilityImage, "GeneralView.label.status.pieces_available.tooltip");

    Menu availMenu = new Menu( availabilityImage );
    availabilityImage.setMenu( availMenu );
    
    MenuItem availSubItem = new MenuItem( availMenu, SWT.CASCADE );
    Messages.setLanguageText(availSubItem, "GeneralView.avail.bad.color" );
    Menu availSubMenu = new Menu(availMenu.getShell(), SWT.DROP_DOWN);
    availSubItem.setMenu( availSubMenu );
    availSubMenu.addMenuListener(
    	new MenuAdapter(){
    		@Override
    		public void menuShown(MenuEvent e){
    			Utils.clearMenu( availSubMenu );
  				
    			MenuItem defItem = new MenuItem( availSubMenu, SWT.RADIO );
    			Messages.setLanguageText(defItem, "label.default" );
    			defItem.addListener( SWT.Selection, (ev)->{
    				if ( defItem.getSelection()){
    					Utils.setConfigColor( "generalview.avail.bad.colour", null );
    				}
    		    });
    		    
    		    MenuItem setItem = new MenuItem( availSubMenu, SWT.RADIO );
    		    Messages.setLanguageText(setItem, "label.set" );
    		    setItem.addListener( SWT.Selection, (ev)->{
    		    	if ( setItem.getSelection()){
	    		    	RGB res = Utils.showColorDialog( availabilityImage, badAvailColor==null?null:badAvailColor.getRGB());
	    		    	
	    		    	if ( res != null ){
	    		    		
	    		       		Utils.setConfigColor( "generalview.avail.bad.colour", ColorCache.getColor( availabilityImage.getShell().getDisplay(), res ) );
	    		    	}
    		    	}
    		    });
    		    
    		    defItem.setSelection( badAvailColor == null );
       		    setItem.setSelection( badAvailColor != null );
    		}
    	});

    
    availabilityPercent = new BufferedLabel(gFile, SWT.RIGHT | SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.widthHint = 50;
    availabilityPercent.setLayoutData(gridData);
    Messages.setLanguageText(availabilityPercent.getWidget(), "GeneralView.label.status.pieces_available.tooltip");

    gTransfer = new Group(genComposite, SWT.SHADOW_OUT);
    Messages.setLanguageText(gTransfer, "GeneralView.section.transfer"); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gTransfer.setLayoutData(gridData);

    GridLayout layoutTransfer = new GridLayout();
    layoutTransfer.numColumns = 6;
    gTransfer.setLayout(layoutTransfer);

    Label label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.timeelapsed"); //$NON-NLS-1$
    timeElapsed = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeElapsed.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.remaining"); //$NON-NLS-1$
    timeRemaining = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeRemaining.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT); //$NON-NLS-1$
    Messages.setLanguageText(label, "GeneralView.label.shareRatio");
    shareRatio = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    shareRatio.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.downloaded"); //$NON-NLS-1$
    download = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    download.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.downloadspeed"); //$NON-NLS-1$
    downloadSpeed = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    downloadSpeed.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT); //$NON-NLS-1$
    Messages.setLanguageText(label, "GeneralView.label.hashfails");
    hashFails = new BufferedLabel(gTransfer, SWT.LEFT); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    hashFails.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.uploaded"); //$NON-NLS-1$
    upload = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    upload.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.uploadspeed"); //$NON-NLS-1$
    uploadSpeed = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    uploadSpeed.setLayoutData(gridData);

    	// blah

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.seeds");
    seeds = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    seeds.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.peers");
    peers = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    peers.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.completed");
    completedLbl = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    completedLbl.setLayoutData(gridData);


    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.totalspeed");
    totalSpeed = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalSpeed.setLayoutData(gridData);


    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.swarm_average_completion");
    ave_completion = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    ave_completion.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.distributedCopies");
    distributedCopies = new BufferedLabel(gTransfer, SWT.LEFT | SWT.DOUBLE_BUFFERED);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    distributedCopies.setLayoutData(gridData);


    ////////////////////////

    gInfo = new Group(genComposite, SWT.SHADOW_OUT);
    Messages.setLanguageText(gInfo, "GeneralView.section.info");
    gridData = new GridData(GridData.FILL_BOTH);
    gInfo.setLayoutData(gridData);

    GridLayout layoutInfo = new GridLayout(4,true);
    gInfo.setLayout(layoutInfo);

    Composite cInfoLeft = new Composite( gInfo, SWT.NULL );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan=3;
    cInfoLeft.setLayoutData(gridData);
    GridLayout layout =  new GridLayout( 2, false );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    cInfoLeft.setLayout(layout);
    
    Composite cInfoRight = new Composite( gInfo, SWT.NULL );
    gridData = new GridData(GridData.FILL_BOTH);
    cInfoRight.setLayoutData(gridData);
    layout =  new GridLayout( 2, false );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    cInfoRight.setLayout(layout);

    
    label = new Label(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.filename"); //$NON-NLS-1$
    fileName = new BufferedLabel(cInfoLeft, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileName.setLayoutData(gridData);

    label = new Label(cInfoRight, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.status"); //$NON-NLS-1$
    torrentStatus = new BufferedLabel(cInfoRight, SWT.LEFT | SWT.DOUBLE_BUFFERED );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    torrentStatus.setLayoutData(gridData);

    label = new Label(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.savein"); //$NON-NLS-1$
    saveIn = new BufferedLabel(cInfoLeft, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    saveIn.setLayoutData(gridData);

    label = new Label(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.totalsize"); //$NON-NLS-1$
    fileSize = new BufferedLabel(cInfoLeft, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileSize.setLayoutData(gridData);

    label = new Label(cInfoRight, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.numberofpieces"); //$NON-NLS-1$
    pieceNumber = new BufferedLabel(cInfoRight, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceNumber.setLayoutData(gridData);

    label = new Label(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.hash"); //$NON-NLS-1$
    hash = new BufferedLabel(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(hash.getWidget(), "GeneralView.label.hash.tooltip", true);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    hash.setLayoutData(gridData);
    	// click on hash -> copy to clipboard
    hash.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
    hash.setForeground(display.getSystemColor(SWT.COLOR_LINK_FOREGROUND));
    label.addMouseListener(new MouseAdapter() {
    	@Override
	    public void mouseDoubleClick(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    	@Override
	    public void mouseDown(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    });
    hash.addMouseListener(new MouseAdapter() {
    	@Override
	    public void mouseDoubleClick(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    	@Override
	    public void mouseDown(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    });


    label = new Label(cInfoRight, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.size");
    pieceSize = new BufferedLabel(cInfoRight, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceSize.setLayoutData(gridData);

    	// creation date stuff
        
    SelectionAdapter cd_listener = 
    	new SelectionAdapter()
		{
	    	@Override
	    	public void
	    	widgetSelected(
	    			SelectionEvent arg0)
	    	{
	    		long millis = manager.getTorrentCreationDate()*1000;
	    		
	    		if ( millis == 0 ){
	    			millis = -1;	// use current date as default
	    		}
	    		
	    		new DateWindow( 
	    			"label.enter.date", 
	    			millis,
	    			new DateWindow.DateReceiver(){
						
						@Override
						public void dateSelected(long millis){
							try{
								TOTorrent torrent = manager.getTorrent();
								torrent.setCreationDate( millis/1000 );
								TorrentUtils.writeToFile(torrent);
							}catch( Throwable e ){
								Debug.out( e );
							}
						}
					});
	    	}
		};
		  
    label = new Label(cInfoLeft, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.creationdate");
    
    Menu cd_menu = new Menu(label.getShell(),SWT.POP_UP);

	MenuItem   mi_cd1 = new MenuItem( cd_menu,SWT.NONE );
	Messages.setLanguageText( mi_cd1, "menu.set.date" );
	mi_cd1.addSelectionListener( cd_listener );
	label.setMenu( cd_menu );

    creation_date = new BufferedLabel(cInfoLeft, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    creation_date.setLayoutData(gridData);

    cd_menu = new Menu(label.getShell(),SWT.POP_UP);

    MenuItem mi_cd2 = new MenuItem( cd_menu,SWT.NONE );
	Messages.setLanguageText( mi_cd2, "menu.set.date" );
	mi_cd2.addSelectionListener( cd_listener );
	
	date_menus = new MenuItem[]{ mi_cd1, mi_cd2 };
	
	new MenuItem( cd_menu, SWT.SEPARATOR );
	ClipboardCopy.addCopyToClipMenu(
		cd_menu,
		new ClipboardCopy.copyToClipProvider(){
			
			@Override
			public String getText(){
				return( creation_date.getText());
			}
		});	
	
	creation_date.getControl().setMenu( cd_menu );

    label = new Label(cInfoRight, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.private");
    privateStatus = new BufferedLabel(cInfoRight, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    privateStatus.setLayoutData(gridData);

	// empty row
    label = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 4;
    label.setLayoutData(gridData);

    
    Composite cCommentsThumb = new Composite( gInfo, SWT.NULL );
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan=4;
    cCommentsThumb.setLayoutData(gridData);
    layout = new GridLayout( 4, true );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    cCommentsThumb.setLayout( layout);

    Composite cComments = new Composite( cCommentsThumb, SWT.NULL );
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan=3;
    cComments.setLayoutData(gridData);
    layout =  new GridLayout( 2, false );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    cComments.setLayout(layout);
    
    Composite cThumb = new Composite( cCommentsThumb, SWT.NULL );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    cThumb.setLayoutData(gridData);
    layout =  new GridLayout( 1, false );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    cThumb.setLayout(layout);

    Group gThumb = new Group( cThumb, SWT.NULL );
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gThumb.setLayoutData(gridData);
    layout =  new GridLayout( 1, false );
    layout.marginHeight=layout.marginLeft=layout.marginRight=layout.marginWidth=0;
    gThumb.setLayout(layout);
    
    Messages.setLanguageText(gThumb, "label.thumbnail" );
    
    thumbImage = new Canvas(gThumb, SWT.NULL){
    	@Override
    	public Point computeSize(int wHint, int hHint, boolean changed){
    		Point size = super.computeSize(wHint, hHint, changed);

    		size.y = size.x*9/16;
    	
    		return( size );
    	}
    };
    
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    thumbImage.setLayoutData(gridData);
    
    Menu thumbMenu = new Menu( thumbImage );
    thumbImage.setMenu( thumbMenu );
    
    thumbMenu.addMenuListener(
    	new MenuAdapter(){
    		@Override
    		public void 
    		menuShown(
    			MenuEvent e)
    		{
    			for ( MenuItem mi: thumbMenu.getItems()){
    				mi.dispose();
    			}
    			
    			java.util.List<DiskManagerFileInfo>	images = new ArrayList<>();
    			
    			for ( DiskManagerFileInfo fi: manager.getDiskManagerFileInfoSet().getFiles()){
    			
    				if ( HTTPUtils.isImageFileType( fi.getExtension())){
    					
    					long	len = fi.getLength();
    					
    					if ( fi.getDownloaded() == len ){
    						
    						File file = fi.getFile( true );
    						
    						if ( file.exists() && file.length() == len ){
    							
    							images.add( fi );
    						}
    					}
    				}
    			}
    			
    			Collections.sort( 
    				images,
    				new Comparator<DiskManagerFileInfo>(){
    					@Override
    					public int compare(DiskManagerFileInfo o1, DiskManagerFileInfo o2){
    						long diff = o2.getLength() - o1.getLength();
    						if ( diff < 0 ){
    							return( -1 );
    						}else if ( diff > 0 ){
    							return( 1 );
    						}else{
    							return( 0 );
    						}
    					}
					});
    			
    			int rem = 15;
    			
    			for ( DiskManagerFileInfo fi: images ){
    			
    				rem--;
    				
    				if ( rem < 0 ){
    					
    					MenuItem mi = new MenuItem( thumbMenu, SWT.PUSH );
        				
    					mi.setText( "..." );
    					
    					mi.setEnabled( false );
    					
    					break;
    				}
    				
    				MenuItem mi = new MenuItem( thumbMenu, SWT.PUSH );
    				
					mi.setText( fi.getTorrentFile().getRelativePath());
					
					mi.addListener( SWT.Selection, (ev)->{
						
						setThumb( fi.getFile( true ));
					});
    			}
    			
    			if ( thumbMenu.getItemCount() > 0 ){
      			
    				new MenuItem( thumbMenu, SWT.SEPARATOR );
    			}
    			
    			MenuItem mi = new MenuItem( thumbMenu, SWT.PUSH );
    			Messages.setLanguageText(mi, "MyTorrentsView.menu.torrent.set.thumb");

    			mi.addListener(SWT.Selection, (ev)->{
    				setThumb();
    			});

    			mi = new MenuItem( thumbMenu, SWT.SEPARATOR );

    			mi = new MenuItem( thumbMenu, SWT.PUSH );
    			Messages.setLanguageText(mi, "Button.clear");

    			mi.addListener(SWT.Selection, (ev)->{
    				clearThumb();
    			});

    		}
		});
       
    thumbImage.addMouseListener(
    	new MouseAdapter(){
    		@Override
    		public void mouseDoubleClick(MouseEvent ev){
    			setThumb();
    		}
		});
    
    label = new Label(cComments, SWT.LEFT);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    label.setLayoutData(gridData);
    label.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
    label.setForeground(display.getSystemColor(SWT.COLOR_LINK_FOREGROUND));
    Messages.setLanguageText(label, "GeneralView.label.user_comment");

    try {
    	user_comment = new Link(cComments, SWT.LEFT | SWT.WRAP);
    	((Link)user_comment).addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Utils.launch(e.text);
				}
			});
    } catch (Throwable e) {
    	user_comment = new Label(cComments, SWT.LEFT | SWT.WRAP);
    }

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    user_comment.setLayoutData(gridData);

    label.addMouseListener(new MouseAdapter() {
    	private void editComment() {
    		TorrentUtil.promptUserForComment(new DownloadManager[] {manager});
    	}

        @Override
        public void mouseDoubleClick(MouseEvent arg0) {editComment();}
        @Override
        public void mouseDown(MouseEvent arg0) {editComment();}
      });

    label = new Label(cComments, SWT.LEFT);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    label.setLayoutData(gridData);
    Messages.setLanguageText(label, "GeneralView.label.comment");

    try {
    	lblComment = new Link(cComments, SWT.LEFT | SWT.WRAP);
    	((Link)lblComment).addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Utils.launch(e.text);
				}
			});
    } catch (Throwable e) {
    	lblComment = new Label(cComments, SWT.LEFT | SWT.WRAP);
    }
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    lblComment.setLayoutData(gridData);
    
    
    Composite pad1 = new Composite(cComments, SWT.NULL);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 2;
    gridData.heightHint = 0;
    pad1.setLayoutData(gridData);
    
    Composite pad2 = new Composite(cThumb, SWT.NULL);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 1;
    gridData.heightHint = 0;
    pad2.setLayoutData(gridData);
    
    ClipboardCopy.addCopyToClipMenu( 
    	lblComment,
    	new ClipboardCopy.copyToClipProvider(){
			
			@Override
			public String getText(){
				String comment = (String)lblComment.getData( "comment" );
				
				return( comment==null?"":comment );
			}
		});
    
    piecesImage.addListener(SWT.Paint, new Listener() {
      @Override
      public void handleEvent(Event e) {
      	if (pImage == null || pImage.isDisposed()) {
      		return;
      	}
      	e.gc.drawImage(pImage, 0, 0);
      }
    });
    availabilityImage.addListener(SWT.Paint, new Listener() {
      @Override
      public void handleEvent(Event e) {
        if (aImage == null || aImage.isDisposed()) {
        	return;
        }
        e.gc.drawImage(aImage, 0, 0);
      }
    });

    thumbImage.addListener(SWT.Paint, new Listener() {
        @Override
        public void handleEvent(Event ev) {
        	if (tImage == null || tImage.isDisposed()) {
        		return;
        	}
        	
        	Rectangle cellBounds = thumbImage.getBounds();
        	
        	Rectangle imgBounds = tImage.getBounds();
        	
			int dstWidth;
			int dstHeight;
			if (imgBounds.height > cellBounds.height) {
				dstHeight = cellBounds.height;
				dstWidth = imgBounds.width * cellBounds.height / imgBounds.height;
			} else if (imgBounds.width > cellBounds.width) {
				dstWidth = cellBounds.width;
				dstHeight = imgBounds.height * cellBounds.width / imgBounds.width;
			} else {
				dstWidth = imgBounds.width;
				dstHeight = imgBounds.height;
			}
						  
			int dstX = (cellBounds.width-dstWidth)/2;
			int dstY = (cellBounds.height-dstHeight)/2;
			
			String resizeKey = imgBounds.width + ":" + imgBounds.height + ":" + dstWidth + ":" + dstHeight;
			
			if ( tImageResized == null || !tImageResizedKey.equals( resizeKey )){
				
				if ( tImageResized != null && !tImageResized.isDisposed()){
					
					tImageResized.dispose();
				}
				
				tImageResized =
					Utils.getResizedImage( 
						tImage, 
						0, 0, imgBounds.width, imgBounds.height, 
						dstWidth, dstHeight );
				
				tImageResizedKey = resizeKey;
			}
			
			ev.gc.drawImage( tImageResized, dstX, dstY );
        }
      });
    
    loadThumb();
    
    genComposite.layout();
  }

  public Composite getComposite() {
    return genComposite;
  }

  private void
  loadThumb()
  {
	  if ( tImage != null && !tImage.isDisposed()){
		  
		  tImage.dispose();
	  }
	  
	  if ( tImageResized != null && !tImageResized.isDisposed()){
		  
		  tImageResized.dispose();
	  }
	  
	  tImage 		= null;
	  tImageResized	= null;

	  if ( !thumbImage.isDisposed()){
		  
		  TOTorrent torrent = manager.getTorrent();
	
		  if ( torrent != null ){
			  
			  final byte[] imageBytes = PlatformTorrentUtils.getContentThumbnail(torrent);
			  
			  if ( imageBytes != null ){
				  
				  try{
					  ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
					  
					  Image image = new Image(Display.getDefault(), bis);
					  	
					  tImage = image;
					  
				  }catch( Throwable f ){
				  }
			  }
		  }
		  
		  thumbImage.redraw();
	  }
  }
  
  private void
  setThumb()
  {
	  FileDialog fDialog = new FileDialog(thumbImage.getShell(), SWT.OPEN | SWT.MULTI);

	  fDialog.setText(MessageText.getString("MainWindow.dialog.choose.thumb"));

	  fDialog.setFilterPath( manager.getSaveLocation().getAbsolutePath());
	  
	  String path = fDialog.open();

	  if ( path == null ){

		  return;
	  }

	  File file = new File( path );

	  setThumb( file );
  }
  
  private void
  setThumb(
	File		file )
  {
	  try{
		  byte[] thumbnail = FileUtil.readFileAsByteArray( file );

		  String name = file.getName();

		  int	pos = name.lastIndexOf( "." );

		  String ext;

		  if ( pos != -1 ){

			  ext = name.substring( pos+1 );

		  }else{

			  ext = "";
		  }

		  String type = HTTPUtils.guessContentTypeFromFileType( ext );

		  try{
			  TOTorrent torrent = manager.getTorrent();

			  PlatformTorrentUtils.setContentThumbnail( torrent, thumbnail, type );

			  loadThumb();
			  
		  }catch( Throwable e ){

		  }
	  }catch( Throwable e ){

		  Debug.out( e );
	  }
  }
  
  private void
  clearThumb()
  {
	  try{
		  TOTorrent torrent = manager.getTorrent();

		  PlatformTorrentUtils.setContentThumbnail( torrent, null, "" );

		  loadThumb();
		  
	  }catch( Throwable e ){

	  }
  }
  

  
  public void refresh(boolean force) {
    if(gFile == null || gFile.isDisposed() || manager == null)
      return;

    loopFactor++;
    if ( force || (loopFactor % graphicsUpdate) == 0) {
      updateAvailability();
      availabilityImage.redraw();
      updatePiecesInfo(false);
      piecesImage.redraw();
    }



    DiskManager dm = manager.getDiskManager();

    String	remaining;
    String	eta			= DisplayFormatters.formatETA(manager.getStats().getSmoothedETA());

    if ( dm != null ){

    	long	rem = dm.getRemainingExcludingDND();

    	String	data_rem = DisplayFormatters.formatByteCountToKiBEtc( rem );

			// append data length unless we have an eta value and none left

    	if ( rem > 0 ){

    		remaining = eta + (eta.length()==0?"":" ") + data_rem;

    	}else{

    			// no bytes left, don't show remaining bytes unless no eta

    		if ( eta.length() == 0 ){

    			remaining = data_rem;
    		}else{

    			remaining = eta;
    		}
    	}
    }else{

    		// only got eta value, just use that

    	remaining = eta;
    }


    setTime(manager.getStats().getElapsedTime(), remaining );

    TRTrackerScraperResponse hd = manager.getTrackerScrapeResponse();
    String seeds_str = manager.getNbSeeds() +" "+ MessageText.getString("GeneralView.label.connected");
    String peers_str = manager.getNbPeers() +" "+ MessageText.getString("GeneralView.label.connected");
    String completed;
    if(hd != null && hd.isValid()) {
      seeds_str += " ( " + hd.getSeeds() +" "+ MessageText.getString("GeneralView.label.in_swarm") + " )";
      peers_str += " ( " + hd.getPeers() +" "+ MessageText.getString("GeneralView.label.in_swarm") + " )";
      completed = hd.getCompleted() > -1 ? Integer.toString(hd.getCompleted()) : "?";

    } else {
      completed = "?";
    }

    String _shareRatio = "";
    int sr = manager.getStats().getShareRatio();

    if(sr == -1) _shareRatio = Constants.INFINITY_STRING;
    if(sr >  0){
      String partial = "" + sr%1000;
      while(partial.length() < 3) partial = "0" + partial;
      _shareRatio = (sr/1000) + "." + partial;

    }

    DownloadManagerStats	stats = manager.getStats();

    String swarm_speed = DisplayFormatters.formatByteCountToKiBEtcPerSec( stats.getTotalAverage() ) + " ( " +DisplayFormatters.formatByteCountToKiBEtcPerSec( stats.getTotalAveragePerPeer())+ " " +MessageText.getString("GeneralView.label.averagespeed") + " )";

    String swarm_completion = "";
    String distributedCopies = "0.000";
    String piecesDoneAndSum = ""+manager.getNbPieces();

    PEPeerManager pm = manager.getPeerManager();
    if( pm != null ) {
    	int comp = pm.getAverageCompletionInThousandNotation();
    	if( comp >= 0 ) {
    		swarm_completion = DisplayFormatters.formatPercentFromThousands( comp );
    	}

    	piecesDoneAndSum = pm.getPiecePicker().getNbPiecesDone() + "/" + piecesDoneAndSum;

    	float dc = pm.getPiecePicker().getMinAvailability()-pm.getNbSeeds()-(pm.isSeeding()&&stats.getDownloadCompleted(false)==1000?1:0);
    	
    	if ( dc < 0 ){
    		dc = 0;
    	}
    	
    	distributedCopies = new DecimalFormat("0.000").format( dc );
    }


    int kInB = DisplayFormatters.getKinB();

    setStats(
    		DisplayFormatters.formatDownloaded(stats),
    		DisplayFormatters.formatByteCountToKiBEtc(stats.getTotalDataBytesSent()),
    		DisplayFormatters.formatByteCountToKiBEtcPerSec(stats.getDataReceiveRate()),
    		DisplayFormatters.formatByteCountToKiBEtcPerSec(stats.getDataSendRate()),
    		swarm_speed,
    		""+manager.getStats().getDownloadRateLimitBytesPerSecond() /kInB,
    		""+(manager.getStats().getUploadRateLimitBytesPerSecond() /kInB),
      	seeds_str,
      	peers_str,
      	completed,
      	DisplayFormatters.formatHashFails(manager),
      	_shareRatio,
      	swarm_completion,
      	distributedCopies
    );

    TOTorrent	torrent_maybe_null = manager.getTorrent();

    String creation_date = DisplayFormatters.formatDate(manager.getTorrentCreationDate()*1000);
    byte[] created_by = torrent_maybe_null == null ? null : torrent_maybe_null.getCreatedBy();
    if (created_by != null) {
			creation_date = MessageText.getString("GeneralView.torrent_created_on_and_by", new String[] {
					creation_date, new String(created_by, Constants.DEFAULT_ENCODING_CHARSET)
			});
		}

    String privateAndSourceStr = MessageText.getString("GeneralView."+(torrent_maybe_null != null && torrent_maybe_null.getPrivate()?"yes":"no"));
    
    if ( torrent_maybe_null != null ){
	    String source = torrent_maybe_null.getSource();
	    
	    if ( source != null ){
	    	
	    	privateAndSourceStr += ", " + MessageText.getString( "wizard.source") + ": " + source;
	    }
    }
    
    setInfos(
      manager.getDisplayName(),
	  DisplayFormatters.formatByteCountToKiBEtc(manager.getSize()),
	  DisplayFormatters.formatDownloadStatus(manager), manager.getState()==DownloadManager.STATE_ERROR,
      manager.getSaveLocation().toString(),
      TorrentUtils.nicePrintTorrentHash(torrent_maybe_null),
      piecesDoneAndSum,
      manager.getPieceLength(),
      manager.getTorrentComment(),
      creation_date,
      manager.getDownloadState().getUserComment(),
      privateAndSourceStr);


    //A special layout, for OS X and Linux, on which for some unknown reason
    //the initial layout fails.
    if (loopFactor == 2) {
      getComposite().layout(true);
    }
  }

  public void delete() {
	if (aImage != null){
		aImage.dispose();
		aImage = null;
	}
	if (pImage != null){
		pImage.dispose();
		pImage = null;
	}
  
	if ( tImage != null ){
		tImage.dispose();
		tImage = null;
	}
	if ( tImageResized != null ){
		tImageResized.dispose();
		tImageResized = null;
	}
	Utils.disposeComposite(genComposite);
	
	viewBuilt = false;
	
    COConfigurationManager.removeParameterListener("Graphics Update", this);
  }

  private String getFullTitle() {
    return MessageText.getString(MSGID_PREFIX + ".title.full");
  }

  private void updateAvailability() {
	  if (manager == null)
		  return;

	  try{
		  this_mon.enter();

		  final int[] available;

		  DiskManagerPiece[]	dmPieces;

		  PEPeerManager	pm 	= manager.getPeerManager();
		  DiskManager		dm	= manager.getDiskManager();

		  long runningFor;

		  if (pm == null) {
			  if (availabilityPercent.getText().length() > 0 ){

				  availabilityPercent.setText("");
			  }

			  available	= new int[manager.getNbPieces()];
			  dmPieces	= null;

			  runningFor = -1;
		  }else{
			  available	= pm.getAvailability();
			  dmPieces	= dm==null?null:dm.getPieces();

			  runningFor = SystemTime.getMonotonousTime() - pm.getTimeStarted( true );
		  }

		  if (display == null || display.isDisposed())
			  return;

		  if (availabilityImage == null || availabilityImage.isDisposed()) {
			  return;
		  }
		  Rectangle bounds = availabilityImage.getClientArea();

		  int xMax = bounds.width - 2;

		  int yMax = bounds.height - 2;

		  if (xMax < 10 || yMax < 5){
			  return;
		  }

		  if (aImage != null && !aImage.isDisposed()){
			  aImage.dispose();
		  }
		  aImage = new Image(display, bounds.width, bounds.height);

		  GC gcImage = new GC(aImage);

		  try{
			  gcImage.setForeground(Colors.grey);
			  gcImage.drawRectangle(0, 0, bounds.width-1, bounds.height-1);
			  int allMin = 0;
			  int allMax = 0;
			  int total = 0;
			  String sTotal = "000";
			  
			  boolean	hasBadAvailBlock = false;
			  
			  if (available != null) {

				  float minAvail = manager.getStats().getAvailability();

				  boolean badAvail = badAvailColor != null && runningFor >= 60*1000 && minAvail >= 0 && minAvail < 1;

				  if ( badAvail ){
					  if ( manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
						  badAvail = minAvail == 0;
					  }
				  }
				  allMin = available.length==0?0:available[0];
				  allMax = available.length==0?0:available[0];

				  int nbPieces = available.length;

				  for (int i = 0; i < nbPieces; i++) {
					  int avail = available[i];
					  if (avail < allMin)
						  allMin = avail;
					  if (avail > allMax)
						  allMax = avail;
				  }

				  int maxAboveMin = allMax - allMin;
				  if ( maxAboveMin == 0 && !badAvail){

					  gcImage.setBackground(Colors.blues[allMin == 0 ? Colors.BLUES_LIGHTEST : Colors.BLUES_DARKEST]);

					  gcImage.fillRectangle(1, 1, xMax, yMax);
					  
				  } else {
					  for (int i = 0; i < nbPieces; i++) {
						  if (available[i] > allMin)
							  total++;
					  }
					  total = (total * 1000) / nbPieces;
					  sTotal = "" + total;
					  if (total < 10) sTotal = "0" + sTotal;
					  if (total < 100) sTotal = "0" + sTotal;

					  for (int i = 0; i < xMax; i++) {
						  int a0 = (i * nbPieces) / xMax;
						  int a1 = ((i + 1) * nbPieces) / xMax;
						  if (a1 == a0)
							  a1++;
						  if (a1 > nbPieces)
							  a1 = nbPieces;
						  int max = 0;
						  int min = available[a0];
						  int Pi = 1000;

						  boolean badAvailBlock = false;
						  
						  for (int j = a0; j < a1; j++) {
							  int avail = available[j];
							  if ( avail > max)
								  max = avail;
							  if (available[j] < min)
								  min = avail;
							  Pi *= avail;
							  Pi /= (avail + 1);

							  if ( badAvail ){
								  if ( dmPieces != null ){
									  DiskManagerPiece dmp = dmPieces[j];
									  
									  if ( !dmp.isDone() &&  dmp.isNeeded()){
										 
										  if ( avail < 1 ){
											  
											  badAvailBlock = true;
											  
											  break;
										  }
									  }
								  }
							  }
						  }

						  if ( badAvailBlock ){

							  hasBadAvailBlock = true;
							  
							  gcImage.setBackground( badAvailColor );
							  
						  }else{
							  int pond = Pi;
							  if (max == 0)
								  pond = 0;
							  else {
								  int PiM = 1000;
								  for (int j = a0; j < a1; j++) {
									  PiM *= (max + 1);
									  PiM /= max;
								  }
								  pond *= PiM;
								  pond /= 1000;
								  pond *= (max - min);
								  pond /= 1000;
								  pond += min;
							  }
							  int index;
							  if (pond <= 0 || allMax == 0) {
								  index = 0;
							  } else {
								  // we will always have allMin, so subtract that
								  index = (pond - allMin) * (Colors.BLUES_DARKEST - 1) / maxAboveMin + 1;
								  // just in case?
								  if (index > Colors.BLUES_DARKEST) {
									  index = Colors.BLUES_DARKEST;
								  }
							  }

							  gcImage.setBackground(Colors.blues[index]);
						  }
						  
						  gcImage.fillRectangle(i+1, 1, 1, yMax);
					  }
				  }
			  }
			  if (availabilityPercent == null || availabilityPercent.isDisposed()) {
				  return;
			  }
			  
			  availabilityPercent.setForeground( hasBadAvailBlock?badAvailColor:null );
			  
			  
			  availabilityPercent.setText(allMin + "." + sTotal);
		  }finally{

			  gcImage.dispose();
		  }
		  availabilityImage.redraw();
	  }finally{

		  this_mon.exit();
	  }
  }

  private void updatePiecesInfo(boolean bForce) {
  	if (manager == null) {
  		return;
	  }

  	try{
  		this_mon.enter();

	    if (display == null || display.isDisposed()) {
	      return;
	    }

	    if (piecesImage == null || piecesImage.isDisposed()) {
	      return;
	    }

	    if ( piecesImageRefreshNeeded ){
	    	bForce = true;
	    	piecesImageRefreshNeeded = false;
	    }

	    DiskManager	dm = manager.getDiskManager();

	    int	nbPieces = manager.getNbPieces();

	    boolean valid;

	    int[] oldPiecesState	= piecesStateCache;

	    if ( oldPiecesState == null || oldPiecesState.length != nbPieces ){

	    	valid	= false;

	    }else{

	    	valid = !bForce;
	    }


        int[] newPiecesState 	= new int[nbPieces];

        final int PS_NONE			= 0x00000000;
        final int PS_DONE			= 0x00000001;
        final int PS_SKIPPED		= 0x00000002;
        final int PS_FILE_BOUNDARY	= 0x00000004;
        final int PS_XFERING		= 0x00000008;

	    if ( dm != null ){

	      	DiskManagerPiece[]	dm_pieces = dm.getPieces();

	      	PEPeerManager pm = showTransferring?manager.getPeerManager():null;
	      	
	      	PEPiece[] pm_pieces = pm==null?null:pm.getPieces();
	      	
	      	boolean	update_skipped;
	      	boolean	update_boundaries;

	      		// ensure disk manager is in a decent state before we start poking about as during
	      		// allocation the checking of skipped status is not reliable

	    	int dm_state = dm.getState();

	    	if ( dm_state == DiskManager.CHECKING || dm_state == DiskManager.READY ){

		      	if ( !valid ){
		      		update_skipped 		= true;
		      		update_boundaries	= true;
		      	}else{
		      		if ( piecesStateFileBoundariesDone ){
		      			update_boundaries = false;
		      		}else{
		      			piecesStateFileBoundariesDone = true;
		      			update_boundaries = true;
		      		}
		      		long marker = dm.getPriorityChangeMarker();
		      		if ( marker == piecesStateSkippedMarker ){
		      			update_skipped = false;
		      		}else{
		      			piecesStateSkippedMarker = marker;
		      			update_skipped = true;
		      		}
		      	}
	    	}else{
	    		update_skipped 		= false;
	      		update_boundaries	= false;
	    	}

	 		for (int i=0;i<nbPieces;i++){

	 			DiskManagerPiece	piece = dm_pieces[i];

	 			int state = piece.isDone()?PS_DONE:PS_NONE;

	 			if ( pm_pieces != null && state == PS_NONE && pm_pieces[i] != null ){
	 				
	 				state = PS_XFERING;
	 			}
	 			
	 			if ( update_skipped ){
		 			if (piece.isSkipped()){
		 				state |= PS_SKIPPED;
		 			}
	 			}else{
	 				state |= oldPiecesState[i]&PS_SKIPPED;
	 			}

	 			if ( update_boundaries ){

	 				if ( piece.spansFiles()){
		 				state |= PS_FILE_BOUNDARY;
		 			}
	 			}else{
	 				state |= oldPiecesState[i]&PS_FILE_BOUNDARY;
	 			}

	 			newPiecesState[i] = state;

	 			if ( valid ){

	 				if ( oldPiecesState[i] != state ){

	 					valid	= false;
	 				}
	 			}
	 		}
	    }else if ( valid ){
	    
	    	for (int i=0;i<nbPieces;i++){
	    		if ( oldPiecesState[i] != 0 ){
	    			valid = false;
	    			break;
	    		}
	    	}
	    	
	    }

	    piecesStateCache	= newPiecesState;

	    if (!valid) {
	      Rectangle bounds = piecesImage.getClientArea();
	      int xMax = bounds.width - 2;
	      int yMax = bounds.height - 2 - 6;
	      if (xMax < 10 || yMax < 5){
	        return;
	      }

          int total = manager.getStats().getDownloadCompleted(true);

	      if (pImage != null && !pImage.isDisposed()){
		        pImage.dispose();
	      }

	      
	      pImage = new Image(display, bounds.width, bounds.height);

	      GC gcImage = new GC(pImage);
	      try{
		      gcImage.setForeground(Colors.grey);
		      gcImage.drawRectangle(0, 0, bounds.width-1, bounds.height-1);
		      gcImage.drawLine(1,6,xMax,6);

		      if (newPiecesState != null && newPiecesState.length != 0) {

		    	int[] boundariesHandled = new int[newPiecesState.length];

		        for (int i = 0; i < xMax; i++) {
		          int a0 = (i * nbPieces) / xMax;
		          int a1 = ((i + 1) * nbPieces) / xMax;
		          if (a1 == a0)
		            a1++;
		          if (a1 > nbPieces)
		            a1 = nbPieces;
		          int nbAvailable = 0;
		          int nbSkipped   = 0;
		          int nbXferring = 0;
		          
		          boolean	hasFileBoundary = false;

		          for (int j = a0; j < a1; j++) {
		        	int ps = newPiecesState[j];
		            if ( (ps & PS_DONE ) != 0 ) {
		              nbAvailable++;
		            }
		            if ( (ps & PS_XFERING ) != 0 ) {
		            	nbXferring++;
			            }
		            if ( (ps & PS_SKIPPED ) != 0 ) {
		              nbSkipped++;
		            }
		            if ( (ps & PS_FILE_BOUNDARY ) != 0 ) {
		            	if ( boundariesHandled[j] < 2 ){
		            		boundariesHandled[j]++;

		            		hasFileBoundary = true;
		            	}
		            }
		          }
		          
		          if ( nbXferring > 0 ){
		        	  gcImage.setBackground( transferringColor );
			          gcImage.fillRectangle(i+1,7,1,yMax);
		          }else if ( nbAvailable == 0 && nbSkipped > 0 ){
		        	  gcImage.setBackground(Colors.grey);
			          gcImage.fillRectangle(i+1,7,1,yMax);
		          }else{
			          int index = (nbAvailable * Colors.BLUES_DARKEST) / (a1 - a0);
			          gcImage.setBackground(Colors.blues[index]);
			          gcImage.fillRectangle(i+1,7,1,yMax);
		          }

		          if ( hasFileBoundary ){
		        	  gcImage.setBackground(Colors.green);
			          gcImage.fillRectangle(i+1,7+yMax-6,1,6);
		          }
		        }
		      }

		      // draw file % bar above
		      int limit = (xMax * total) / 1000;
		      gcImage.setBackground(Colors.colorProgressBar);
		      gcImage.fillRectangle(1,1,limit,5);
		      if (limit < xMax) {
		        gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
		        gcImage.fillRectangle(limit+1,1,xMax-limit,5);
		      }
	      }finally{

	    	  gcImage.dispose();
	      }

	      if (piecesPercent != null && !piecesPercent.isDisposed())
	        piecesPercent.setText(DisplayFormatters.formatPercentFromThousands(total));

	      if (pImage == null || pImage.isDisposed()) {
	        return;
	      }
	      piecesImage.redraw();
	    }
  	}finally{

  		this_mon.exit();
  	}
  }

  private void setTime(String elapsed, String remaining) {
    timeElapsed.setText( elapsed );
    timeRemaining.setText( remaining);
  }

  private void
  setStats(
  	String dl, String ul,
	String dls, String uls,
	String ts,
	String dl_speed, String ul_speed,
	String s,
	String p,
	String completed,
	String hash_fails,
	String share_ratio,
	String ave_comp,
	String distr_copies
	)
  {
    if (display == null || display.isDisposed())
      return;

	download.setText( dl );
	downloadSpeed.setText( dls );
	upload.setText( ul );
	uploadSpeed.setText( uls );
	totalSpeed.setText( ts );
	ave_completion.setText( ave_comp );
	distributedCopies.setText(distr_copies);

	/*
	if ( !maxDLSpeed.getText().equals( dl_speed )){

		maxDLSpeed.setText( dl_speed );
	}

	if ( !maxULSpeed.getText().equals( ul_speed )){

		maxULSpeed.setText( ul_speed );
	}
	*/

	seeds.setText( s);
	peers.setText( p);
	completedLbl.setText(completed);
	hashFails.setText( hash_fails);
	shareRatio.setText( share_ratio);
  }


  private void setInfos(
    final String 	_fileName,
    final String 	_fileSize,
    final String 	_torrentStatus,
    final boolean	_statusIsError,
    final String 	_path,
    final String 	_hash,
    final String 	_pieceData,
    final String 	_pieceLength,
    final String 	_comment,
	final String 	_creation_date,
	final String 	_user_comment,
	final String 	isPrivateAndSource) {
    if (display == null || display.isDisposed())
			return;
		Utils.execSWTThread(new AERunnable()
		{
			@Override
			public void runSupport() {
				fileName.setText(_fileName);
				fileSize.setText(_fileSize);
				torrentStatus.setText(_torrentStatus);
				int pos = _torrentStatus.indexOf( "http://" );
				if ( pos > 0 ){
					torrentStatus.setLink( UrlUtils.getURL( _torrentStatus ));
				}else{
					torrentStatus.setLink( null );
				}
				torrentStatus.setForeground(_statusIsError?Colors.red:null);
				saveIn.setText(_path);
				hash.setText(_hash);
				pieceNumber.setText(_pieceData); //$NON-NLS-1$
				pieceSize.setText(_pieceLength);
				creation_date.setText(_creation_date);
				privateStatus.setText(isPrivateAndSource);
				boolean do_relayout = false;
				do_relayout = setCommentAndFormatLinks(lblComment, _comment.length() > 5000 && Constants.isWindowsXP ? _comment.substring(0, 5000) : _comment ) | do_relayout;
				do_relayout = setCommentAndFormatLinks(user_comment, _user_comment) | do_relayout;
				if (do_relayout)
				{
					gInfo.layout(true, true);
					Utils.updateScrolledComposite(scrolled_comp);
				}
			}
		});
	}

  private static boolean setCommentAndFormatLinks(Control c, String new_comment) {
	  String old_comment = (String)c.getData("comment");
	  if (new_comment == null) {new_comment = "";}
	  if (new_comment.equals(old_comment)) {return false;}

	  c.setData("comment", new_comment);
	  if (c instanceof Label) {
		  ((Label) c).setText(new_comment);
	  } else if (c instanceof Link) {
						String sNewComment;
		  sNewComment = new_comment.replaceAll(
								"([^=\">][\\s]+|^)((?:https?://|chat:)[\\S]+)", "$1<A HREF=\"$2\">$2</A>");
						// need quotes around url
		  sNewComment = sNewComment.replaceAll("(href=)(htt[^\\s>]+)", "$1\"$2\"");

		  	// probably want to URL decode the link text if it is a URL

		  try{
			  Pattern p = Pattern.compile("(?i)(<A HREF=[^>]*>)([^<]*</A>)");

			  Matcher m = p.matcher( sNewComment );

			  boolean result = m.find();

			  if ( result ){

				  StringBuffer sb = new StringBuffer();

				  while( result ){

					  m.appendReplacement(sb, m.group(1));

					  String str = m.group(2);

					  sb.append( UrlUtils.decode( str ));

					  result = m.find();
				  }

				  m.appendTail(sb);

				  sNewComment = sb.toString();

			  }}catch( Throwable e ){
			  }

						// Examples:
						// http://cowbow.com/fsdjl&sdfkj=34.sk9391 moo
						// <A HREF=http://cowbow.com/fsdjl&sdfkj=34.sk9391>moo</a>
						// <A HREF="http://cowbow.com/fsdjl&sdfkj=34.sk9391">moo</a>
						// <A HREF="http://cowbow.com/fsdjl&sdfkj=34.sk9391">http://moo.com</a>
			((Link)c).setText(sNewComment);
		  // Reduce white flicker on Windows ever so slightly
			c.redraw();
			c.update();
	  }

	  return true;
  }

  @Override
  public void parameterChanged(String parameterName) {
    graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");
  }

	private Image obfuscatedImage(Image image) {
		if (fileName == null) {
			return image;
		}
		UIDebugGenerator.obfuscateArea(image, (Control) fileName.getWidget(),
				manager == null ? "" : UIDebugGenerator.obfuscateDownloadName( manager ));
		UIDebugGenerator.obfuscateArea(image, (Control) saveIn.getWidget(),
				Debug.secretFileName(saveIn.getText()));
		return image;
}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = event.getView();
      	swtView.setTitle(getFullTitle());
      	swtView.setToolBarListener(this);
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
      	swtView.setTitle(getFullTitle());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	dataSourceChanged(event.getData());
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	String id = "DMDetails_General";
      	if (manager != null) {
      		if (manager.getTorrent() != null) {
  					id += "." + manager.getInternalName();
      		} else {
						id += ":" + manager.getSize();
					}
					SelectedContentManager.changeCurrentlySelectedContent(id,
							new SelectedContent[] {
								new SelectedContent(manager)
					});
				} else {
					SelectedContentManager.changeCurrentlySelectedContent(id, null);
				}

      	break;

      case UISWTViewEvent.TYPE_FOCUSLOST:
    		SelectedContentManager.clearCurrentlySelectedContent();
    		break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh(false);
        break;

      case UISWTViewEvent.TYPE_OBFUSCATE:
				Object data = event.getData();
				if (data instanceof Map) {
					obfuscatedImage((Image) MapUtils.getMapObject((Map) data, "image",
							null, Image.class));
				}
      	break;
    }

    return true;
  }

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.toolbar.UIToolBarActivationListener#toolBarItemActivated(ToolBarItem, long, java.lang.Object)
	 */
	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
	                                    Object datasource) {
		return false; // default handler will handle it
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.UIPluginViewToolBarListener#refreshToolBarItems(java.util.Map)
	 */
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		Map<String, Long> states = TorrentUtil.calculateToolbarStates(
				SelectedContentManager.getCurrentlySelectedContent(), null);
		list.putAll(states);
	}
}
