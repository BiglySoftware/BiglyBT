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

package com.biglybt.ui.swt.views.piece;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.download.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.PiecePickerListener;
import com.biglybt.core.util.*;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BufferedLabel;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.PiecesView;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;

/**
 * Piece Map View.
 * <p>
 * This view is placed within the {@link PiecesView} even though it relies on
 * a {@link DownloadManager} datasource instead of a {@link PEPiece}
 * <p>
 * Also placed in Library views
 *
 * @author TuxPaper
 * @created Feb 26, 2007
 *
 */
public class PieceMapView
	implements DownloadManagerPieceListener, DownloadManagerPeerListener, PiecePickerListener,
	UISWTViewCoreEventListener
{
	public static final String	KEY_INSTANCE = "PieceMapView::instance";
	
	private final static int BLOCK_FILLSIZE = 14;

	private final static int BLOCK_SPACING = 3;

	private final static int BLOCK_SIZE = BLOCK_FILLSIZE + BLOCK_SPACING;

	private final static int BLOCKCOLOR_HAVE 		= 0;
	private final static int BLOCKCOLOR_NOHAVE 		= 1;
	private final static int BLOCKCOLOR_TRANSFER 	= 2;
	private final static int BLOCKCOLOR_NEXT 		= 3;
	private final static int BLOCKCOLOR_AVAILCOUNT 	= 4;
	private final static int BLOCKCOLOR_SHOWFILE 	= 5;
	private final static int BLOCKCOLOR_MERGE_READ	= 6;
	private final static int BLOCKCOLOR_MERGE_WRITE	= 7;
	private final static int BLOCKCOLOR_FORCED		= 8;

	public static final String MSGID_PREFIX = "PieceMapView";

	private static final byte SHOW_BIG = 2;

	private static final byte SHOW_SMALL = 1;

	public static final int MAX_PIECE_CACHE	= 32*1024;
	
	private Composite pieceInfoComposite;

	private ScrolledComposite sc;

	private Canvas 	pieceInfoCanvas;
	private int		currentNumColumns;
	private int		currentNumPieces;
	
	private final static Color[] blockColors = {
		Colors.blues[Colors.BLUES_DARKEST],
		Colors.white,
		Colors.red,
		Colors.fadedRed,
		Colors.black,
		Colors.fadedGreen,
		Colors.yellow,
		Colors.grey,
		Colors.cyan
	};

	private final static String[] legendKeys = {
		"PiecesView.BlockView.Have",
		"PiecesView.BlockView.NoHave",
		"PeersView.BlockView.Transfer",
		"PeersView.BlockView.NextRequest",
		"PeersView.BlockView.AvailCount",
		"PeersView.BlockView.ShowFile",
		"PeersView.BlockView.MergeRead",
		"PeersView.BlockView.MergeWrite",
		"PeersView.BlockView.ForcePiece",
	};
	
	public static Color
	getLegendColor(
		String		key )
	{
		return( Legend.getLegendColor( key, legendKeys, blockColors ));
	}
	
	private BufferedLabel topLabel;
	private String topLabelLHS = "";
	private String topLabelRHS = "";

	private List<Integer> selectedPieceExplicit			= null;
	private int		selectedPiece					= -1;
	private int		selectedPieceShowFilePending	= -1;
	private boolean	selectedPieceShowFile;
	
	private boolean scrollPending = false;
	
	private Color	file_color;
	private Color	file_color_faded;
	
	private Label imageLabel;

	// More delay for this view because of high workload
	private final int graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update") * 2;

	private int loopFactor = 0;

	private Font font = null;

	Image img = null;

	private DownloadManager dlm;
	private PiecePicker		current_pp;
	
	BlockInfo[] oldBlockInfo;
	int oldBlockInfoStart = 0;

	/**
	 * Initialize
	 *
	 */
	public PieceMapView() {
		
	}

	private void dataSourceChanged(Object newDataSource) {
		//System.out.println( "dsc: dlm=" + dlm + ", new=" + (newDataSource instanceof Object[]?((Object[])newDataSource)[0]:newDataSource));

		DownloadManager[] newManager = DataSourceUtils.getDMs(newDataSource);
		if (newManager.length == 0 && swtView != null) {
			// PiecesInfoView can be placed in PiecesView
			UISWTView parentView = swtView.getParentView();
			if (parentView != null) {
				newManager = DataSourceUtils.getDMs(parentView.getDataSource());
			}
		}

		if ( newManager.length != 1){

			oldBlockInfo = null;
		}

		topLabelRHS = "";
		
		synchronized( this ){
			if (dlm != null) {
				dlm.removePieceListener(this);
				dlm.removePeerListener(this);
				if ( current_pp != null ){
					current_pp.removeListener( this );
				}
			}
			dlm = newManager.length == 1 ? newManager[0] : null;
			if ( dlm != null ){
				dlm.addPieceListener(this, false);
				dlm.addPeerListener(this,true);
			}
		}

		PEPiece[] pieces = DataSourceUtils.getPieces(newDataSource);
		if (dlm != null && pieces.length > 0) {
			selectPieces(pieces);
		}

		if ( dlm != null ){
			fillPieceInfoSection();
		}
		
		Utils.execSWTThread(()->updateTopLabel());
	}

	private static String getFullTitle() {
		return MessageText.getString("PeersView.BlockView.title");
	}

	private void initialize(Composite composite) {
		if (pieceInfoComposite != null && !pieceInfoComposite.isDisposed()) {
			Logger.log(new LogEvent(LogIDs.GUI, LogEvent.LT_ERROR,
					"PieceMapView already initialized! Stack: "
							+ Debug.getStackTrace(true, false)));
			delete();
		}
		createPeerInfoPanel(composite);

		fillPieceInfoSection();
		
		Control c = composite;
		
		while( c != null ){
			
			c.setData( KEY_INSTANCE, composite );
			
			c = c.getParent();
		}
	}

	public void
	selectPieces(
		PEPiece...		pieces )
	{
		if (pieces.length > 0) {
			selectedPieceExplicit = new ArrayList<>();
			for (PEPiece piece : pieces) {
				selectedPieceExplicit.add(piece.getPieceNumber());
			}
		} else {
			selectedPieceExplicit = null;
		}
		
		Utils.execSWTThread(() -> {
			scrollPending = true;

			refreshInfoCanvas();
		});
	}
	
	private void createPeerInfoPanel(Composite parent) {
		GridLayout layout;
		GridData gridData;

		// Peer Info section contains
		// - Peer's Block display
		// - Peer's Datarate
		pieceInfoComposite = new Composite(parent, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		pieceInfoComposite.setLayout(layout);
		gridData = new GridData(GridData.FILL, GridData.FILL, true, true);
		pieceInfoComposite.setLayoutData(gridData);

		imageLabel = new Label(pieceInfoComposite, SWT.NULL);
		gridData = new GridData();
		imageLabel.setLayoutData(gridData);

		topLabel = new BufferedLabel(pieceInfoComposite, SWT.DOUBLE_BUFFERED);
		if ( !Utils.isDarkAppearanceNative()){
			topLabel.getControl().setBackground(Colors.white);
		}
		gridData = new GridData(SWT.FILL, SWT.DEFAULT, true, false);
		topLabel.setLayoutData(gridData);

		sc = new ScrolledComposite(pieceInfoComposite, SWT.V_SCROLL);
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
		layout = new GridLayout();
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		sc.setLayout(layout);
		gridData = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
		sc.setLayoutData(gridData);
		sc.getVerticalBar().setIncrement(BLOCK_SIZE);

		pieceInfoCanvas = new Canvas(sc, SWT.NO_REDRAW_RESIZE | SWT.NO_BACKGROUND);
		gridData = new GridData(GridData.FILL, SWT.DEFAULT, true, false);
		pieceInfoCanvas.setLayoutData(gridData);
		pieceInfoCanvas.addPaintListener(e -> {
			if (e.width <= 0 || e.height <= 0) {
				return;
			}
			try {
				Rectangle bounds = (img == null) ? null : img.getBounds();
				if (bounds == null || dlm == null ) {
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);
				} else {
					int visibleImageWidth = bounds.width - e.x;
					int visibleImageHeight = bounds.height - e.y;
					if (e.width > visibleImageWidth) {
						e.gc.fillRectangle(bounds.width, e.y,
								e.width - visibleImageWidth + 1, e.height);
					}
					if (e.height > visibleImageHeight) {
						e.gc.fillRectangle(e.x, bounds.height, e.width,
								e.height - visibleImageHeight + 1);
					}

					int width = Math.min(e.width, visibleImageWidth);
					int height = Math.min(e.height, visibleImageHeight);
					e.gc.drawImage(img, e.x, e.y, width, height, e.x, e.y, width,
							height);
					//log("draw " + e.x + "x" + e.y + ", w=" + width + ", h=" + height);
				}
			} catch (Exception ex) {
			}
		});
		pieceInfoCanvas.addListener(SWT.KeyDown, new DoNothingListener());

		pieceInfoCanvas.addListener(SWT.Resize, e -> {
			synchronized (PieceMapView.this) {
//				log("resize.  af=" + alreadyFilling);
			  if (alreadyFilling) {
				  return;
			  }

			  alreadyFilling = true;
			}

			// wrap in asyncexec because sc.setMinWidth (called later) doesn't work
			// too well inside a resize (the canvas won't size isn't always updated)
			Utils.execSWTThreadLater(0, () -> {
				if (img != null) {
					int iOldColCount = img.getBounds().width / BLOCK_SIZE;
					int iNewColCount = pieceInfoCanvas.getClientArea().width
							/ BLOCK_SIZE;
//					log("resize. col " + iOldColCount + "->" + iNewColCount);
					if (iOldColCount != iNewColCount) {
						refreshInfoCanvas();
					}
				} else {
//					log("resize. no img");
				}
				synchronized (PieceMapView.this) {
					alreadyFilling = false;
				}
			});
		});

		sc.setContent(pieceInfoCanvas);

		pieceInfoCanvas.addMouseMoveListener(event -> {
			int piece_number = getPieceNumber( event.x, event.y );
			
			if ( piece_number != selectedPiece ){
				
				selectedPieceShowFilePending = -1;
			}
		});
		
		pieceInfoCanvas.addListener(SWT.Move, event -> fillPieceInfoSection());
		sc.addListener(SWT.Resize, event -> fillPieceInfoSection());
		
		pieceInfoCanvas.addMouseTrackListener(
			new MouseTrackAdapter()
			{
				@Override
				public void
				mouseHover(
					MouseEvent event )
				{
					int piece_number = getPieceNumber( event.x, event.y );

					boolean pieceChanged = piece_number != selectedPiece;
					
					if ( piece_number >= 0 ){

						selectedPieceExplicit = null;
						
						selectedPiece 					= piece_number;
						selectedPieceShowFilePending	= piece_number;
						
						SimpleTimer.addEvent(
							"ShowFile",
							SystemTime.getOffsetTime( 1000 ),
							event1 -> Utils.execSWTThread(() -> {
									
								if ( selectedPieceShowFilePending == piece_number ){

									selectedPieceShowFile = true;
									
									refreshInfoCanvas();
								}	
							}));
						
						refreshInfoCanvas();
						
						setTopLableRHS( piece_number );

					}else{

						topLabelRHS = "";
					}
					
					updateTopLabel();
					
					if ( pieceChanged ){
						
						refreshInfoCanvas();
					}
				}
				
				@Override
				public void mouseExit(MouseEvent e){
					selectedPiece 			= -1;
					selectedPieceShowFile 	= false;
					
					Utils.setTT( pieceInfoCanvas, null );
					
					refreshInfoCanvas();
				}
			});

		final Menu menu = new Menu(pieceInfoCanvas.getShell(), SWT.POP_UP );

		pieceInfoCanvas.setMenu( menu );

		pieceInfoCanvas.addListener(
			SWT.MenuDetect,
			event -> {
				Point pt = pieceInfoCanvas.toControl(event.x, event.y);

				int	piece_number = getPieceNumber( pt.x, pt.y );

				menu.setData( "pieceNumber", piece_number );
			});

		MenuBuildUtils.addMaintenanceListenerForMenu(
			menu,
			(menu1, event) -> {
				Integer pn = (Integer) menu1.getData( "pieceNumber" );

				if ( pn != null && pn != -1 ){

					DownloadManager	download_manager = dlm;

					if ( download_manager == null ){

						return;
					}

					DiskManager		disk_manager = download_manager.getDiskManager();
					PEPeerManager	peer_manager = download_manager.getPeerManager();

					if ( disk_manager == null || peer_manager == null ){

						return;
					}

					final PiecePicker picker = peer_manager.getPiecePicker();

					DiskManagerPiece[] 	dm_pieces = disk_manager.getPieces();
					PEPiece[]			pe_pieces = peer_manager.getPieces();

					final int piece_number = pn;

					final DiskManagerPiece	dm_piece = dm_pieces[piece_number];
					final PEPiece			pm_piece = pe_pieces[piece_number];

					final MenuItem force_piece = new MenuItem(menu1, SWT.CHECK );

					Messages.setLanguageText( force_piece, "label.force.piece" );

					boolean	done = dm_piece.isDone();

					force_piece.setEnabled( !done );

					if ( !done ){

						force_piece.setSelection( picker.isForcePiece( piece_number ));

						force_piece.addSelectionListener(
								new SelectionListenerForcePiece(picker, piece_number, force_piece));
					}

					final MenuItem reset_piece = new MenuItem(menu1, SWT.PUSH );

					Messages.setLanguageText( reset_piece, "label.reset.piece" );

					boolean	can_reset = dm_piece.isDone() || dm_piece.getNbWritten() > 0;

					reset_piece.setEnabled( can_reset );

					reset_piece.addSelectionListener(
							new SelectionListenerResetPiece(dm_piece, pm_piece));
									
					new MenuItem(menu1, SWT.SEPARATOR );
					
					final MenuItem seq_asc = new MenuItem(menu1, SWT.PUSH );

					Messages.setLanguageText( seq_asc, "label.seq.asc.from", String.valueOf( piece_number ));

					seq_asc.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void widgetSelected(SelectionEvent e){
								
								download_manager.getDownloadState().setFlag( DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD, false);
								
								picker.setReverseBlockOrder( false );
								
								picker.setSequentialAscendingFrom( piece_number );
							}
						});
					
					final MenuItem seq_desc = new MenuItem(menu1, SWT.PUSH );

					Messages.setLanguageText( seq_desc, "label.seq.desc.from", String.valueOf( piece_number ));

					seq_desc.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void widgetSelected(SelectionEvent e){
								download_manager.getDownloadState().setFlag( DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD, false);
								
								picker.setReverseBlockOrder( true );
								
								picker.setSequentialDescendingFrom( piece_number );
							}
						});
					
					final MenuItem seq_clear = new MenuItem(menu1, SWT.PUSH );

					Messages.setLanguageText( seq_clear, "label.seq.clear", String.valueOf( piece_number ));

					seq_clear.addSelectionListener(
						new SelectionAdapter()
						{
							@Override
							public void widgetSelected(SelectionEvent e){
								download_manager.getDownloadState().setFlag( DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD, false);
								
								picker.setReverseBlockOrder( false );
								
								picker.clearSequential();
							}
						});
					
				}
			});



		Legend.createLegendComposite(pieceInfoComposite,
				blockColors, legendKeys, new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));

		font = FontUtils.getFontPercentOf(pieceInfoCanvas.getFont(), 0.7f);
	}

	private int
	getPieceNumber(
		int		x,
		int		y )
	{
		DownloadManager manager = dlm;

		if ( manager == null ){

			return( -1 );
		}

		Rectangle bounds = pieceInfoCanvas.getClientArea();

		if (bounds.width <= 0 || bounds.height <= 0){

			return( -1 );
		}

		int iNumCols = bounds.width / BLOCK_SIZE;

		int	x_block = x/BLOCK_SIZE;
		int	y_block = y/BLOCK_SIZE;

		int	piece_number = y_block*iNumCols + x_block;

		if ( piece_number >= dlm.getNbPieces()){

			return( -1 );

		}else{

			return( piece_number );
		}
	}

	private boolean alreadyFilling = false;

	private UISWTView swtView;

	private void fillPieceInfoSection() {
//		log("fillPieceInfoSection.  af=" + alreadyFilling + "; " + Debug.getCompressedStackTrace(4));
		synchronized (this) {
			if (alreadyFilling) {
				return;
			}
			alreadyFilling = true;
		}

		Utils.execSWTThreadLater(0, () -> {
			synchronized (PieceMapView.this) {
				if (!alreadyFilling) {
					return;
				}
			}

			try {
				if (imageLabel == null || imageLabel.isDisposed()) {
					return;
				}

				if (imageLabel.getImage() != null) {
					Image image = imageLabel.getImage();
					imageLabel.setImage(null);
					image.dispose();
				}

				refreshInfoCanvas();
			} finally {
				synchronized (PieceMapView.this) {
					alreadyFilling = false;
				}
			}
		});
	}

	private void refresh() {
		if (loopFactor++ % graphicsUpdate == 0) {
			refreshInfoCanvas();
		}
	}

	private void
	setTopLableRHS(
		Integer...	piece_numbers )
	{
		DiskManager		disk_manager 	= dlm.getDiskManager();
		PEPeerManager	pm 				= dlm.getPeerManager();

		DiskManagerPiece[] dm_pieces = disk_manager == null ? dlm.getDiskManagerPiecesSnapshot() :  disk_manager.getPieces();

		// TODO: i18n
		StringBuilder text = new StringBuilder();
		
		if ( dm_pieces != null ){
			
			StringBuilder files_str = new StringBuilder();
			int		file_count = 0;

			for (int piece_number : piece_numbers) {
				if (text.length() > 0) {
					text.append("\n");
				}
					
				DiskManagerPiece	dm_piece = dm_pieces[ piece_number ];
				PEPiece 			pm_piece = pm==null?null:pm.getPiece( piece_number );

				text.append("Piece ").append(piece_number);
				text.append(": ").append(dm_piece.getString());

				if ( pm_piece != null ){

					text.append(", active: ").append(pm_piece.getString());

				}else{
		
					if ( dm_piece.isNeeded() && !dm_piece.isDone() && pm != null ){

						text.append(", inactive: ");
						text.append(pm.getPiecePicker().getPieceString(piece_number));
					}
				}
				
				text.append(" - ");
				
				DMPieceList l = dm_piece.getPieceList();
								
				for ( int i=0;i<l.size();i++) {
		       
					DMPieceMapEntry entry = l.get( i );
					
					DiskManagerFileInfo info = entry.getFile();

					if (i > 0) {
						text.append("; ");
					}
					text.append(info.getFile(true).getName());
					text.append(", ");
					text.append(MessageText.getString("label.offset"));
					text.append(" ");
					text.append(entry.getOffset());

					if ( file_count < 20 ){

						if (files_str.length() > 0) {
							files_str.append('\n');
						}
						files_str.append(info.getTorrentFile().getRelativePath());
						files_str.append(", ");
						files_str.append(MessageText.getString("label.offset"));
						files_str.append(" ");
						files_str.append(entry.getOffset());

					}else if ( file_count == 20 ){
						
						files_str.append("\n...");
					}

					file_count++;
				}	
			}
				
			Utils.setTT( pieceInfoCanvas, files_str.toString());
			
		}else{
			Utils.setTT( pieceInfoCanvas, null );
		}
		
		topLabelRHS = text.toString();
	}
	
	private void
	updateTopLabel()
	{
		String text = topLabelLHS;
		
		if (selectedPieceExplicit != null) {
			setTopLableRHS( selectedPieceExplicit.toArray(new Integer[0]) );
		}

		if ( text.length() > 0 && topLabelRHS.length() > 0 ){

			text += "; " + topLabelRHS;
		}

		topLabel.setText( text );
	}

	protected void refreshInfoCanvas() {

		int pos = refreshInfoCanvasSupport();
		
		if ( scrollPending && pos != -1 ){
			
			scrollPending = false;
			
			sc.setOrigin( 0, pos );

			refreshInfoCanvasSupport();
		}
	}
	
	protected int refreshInfoCanvasSupport() {
		
		int	result = -1;
		
		synchronized (PieceMapView.this) {
			alreadyFilling = false;
		}

		if (pieceInfoCanvas == null || pieceInfoCanvas.isDisposed()
				|| !pieceInfoCanvas.isVisible()) {
			return( result );
		}
		pieceInfoCanvas.layout(true);
		Rectangle bounds = pieceInfoCanvas.getClientArea();
		if (bounds.width <= 0 || bounds.height <= 0) {
			topLabelLHS = "";
			updateTopLabel();
			return( result );
		}

		if (dlm == null) {
			GC gc = new GC(pieceInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();
			topLabelLHS = MessageText.getString("view.one.download.only");
			topLabelRHS = "";
			updateTopLabel();

			return( result );
		}

		PEPeerManager pm = dlm.getPeerManager();

		PiecePicker	piecePicker = pm==null?null:pm.getPiecePicker();
		
		DiskManager dm = dlm.getDiskManager();

		DiskManagerPiece[] dm_pieces = dm == null ? dlm.getDiskManagerPiecesSnapshot() :  dm.getPieces();

		if (dm_pieces == null) {
			GC gc = new GC(pieceInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();
			topLabelLHS = "";
			updateTopLabel();

			return( result );
		}

		boolean	forceRepaint = false;
		
		int iNumCols = bounds.width / BLOCK_SIZE;
		
		if ( currentNumColumns != iNumCols ){
			
			currentNumColumns = iNumCols;
			
			forceRepaint = true;
			
			oldBlockInfo = null;
			
			if ( img != null ){
				
				img.dispose();
				
				img = null;
			}
		}
		
		int numPieces = dm_pieces.length;
		int iNeededHeight = (((numPieces - 1) / iNumCols) + 1) * BLOCK_SIZE;

		if (img != null && !img.isDisposed()) {
			Rectangle imgBounds = img.getBounds();
			if ( 	numPieces != currentNumPieces || 
					imgBounds.width != bounds.width || 
					imgBounds.height != iNeededHeight) {
				oldBlockInfo = null;
				img.dispose();
				img = null;
			}
		}

		if (sc.getMinHeight() != iNeededHeight) {
			boolean scrollVisible = sc.getVerticalBar() != null && sc.getVerticalBar().getVisible();
//			log("minHeight sc=" + sc.getMinHeight() +
//				", needed=" + iNeededHeight + "; sc.ca=" + sc.getClientArea()
//				+ "; sc.vbv=" + scrollVisible + "; " + Debug.getCompressedStackTrace(3));
			sc.setMinHeight(iNeededHeight);
			sc.layout(true, true);
			if (!scrollVisible && iNeededHeight > sc.getClientArea().height) {
//				log("skip canvas fill, scrollbar incoming. " + (sc.getVerticalBar() != null && sc.getVerticalBar().getVisible()));
				return refreshInfoCanvasSupport();
			}
			bounds = pieceInfoCanvas.getClientArea();
		}

		currentNumPieces = numPieces;
		
		PEPiece[] currentDLPieces = pm == null ? null : pm.getPieces();
		
		byte[] uploadingPieces = new byte[numPieces];

		// find upload pieces
		if (pm != null ) {
			for (PEPeer peer : pm.getPeers()) {
				int[] peerRequestedPieces = peer.getIncomingRequestedPieceNumbers();
				if (peerRequestedPieces != null && peerRequestedPieces.length > 0) {
					int pieceNum = peerRequestedPieces[0];
					if ( pieceNum < uploadingPieces.length && uploadingPieces[pieceNum] < 2)
						uploadingPieces[pieceNum] = 2;
					for (int j = 1; j < peerRequestedPieces.length; j++) {
						pieceNum = peerRequestedPieces[j];
						if (pieceNum < uploadingPieces.length && uploadingPieces[pieceNum] < 1)
							uploadingPieces[pieceNum] = 1;
					}
				}
	
			}
		}

		int[] availability = pm == null ? null : pm.getAvailability();

		int minAvailability = Integer.MAX_VALUE;
		int minAvailability2 = Integer.MAX_VALUE;
		if (availability != null && availability.length > 0) {
			for (int anAvailability : availability) {
				if (anAvailability != 0 && anAvailability < minAvailability) {
					minAvailability2 = minAvailability;
					minAvailability = anAvailability;
					if (minAvailability == 1) {
						break;
					}
				}
			}
		}

		if (img == null) {
			img = new Image(pieceInfoCanvas.getDisplay(), bounds.width, iNeededHeight);
			oldBlockInfo = null;
		}
		GC gcImg = new GC(img);


		int iRow;
		int numChanged = 0;
		try {
			// use advanced capabilities for faster drawText
			gcImg.setAdvanced(true);

			if (oldBlockInfo == null) {
				gcImg.setBackground(pieceInfoCanvas.getBackground());
				gcImg.fillRectangle(0, 0, bounds.width, iNeededHeight);
			}

			int	selectionStart 	= Integer.MAX_VALUE;
			int selectionEnd	= Integer.MIN_VALUE;
			
			if ( selectedPiece != -1 ){
			
				if ( selectedPieceShowFile ){
					
					DMPieceList l = dm_pieces[ selectedPiece ].getPieceList();
				
					for ( int i=0;i<l.size();i++) {
	           		 
						DMPieceMapEntry entry = l.get( i );
						
						DiskManagerFileInfo info = entry.getFile();
						
						int first 	= info.getFirstPieceNumber();
						int last	= info.getLastPieceNumber();
						
						if ( first < selectionStart ){
							selectionStart = first;
						}
						
						if ( last > selectionEnd ){
							selectionEnd = last;
						}
					}
				}
			}
			
			gcImg.setFont(font);

			int iCol = 0;
			int startPieceNo = 0;

			Rectangle canvasBounds = pieceInfoCanvas.getBounds();
			if (canvasBounds.y < 0) {
				startPieceNo = ((-canvasBounds.y) / BLOCK_SIZE) * iNumCols;
			}
			int drawHeight = sc.getClientArea().height;
			// +1 in case first visible row is partial
			int endPieceNo = Math.min(numPieces - 1, (int) (startPieceNo
					+ (Math.ceil(drawHeight / (float) BLOCK_SIZE) + 1) * iNumCols) - 1);
//			log("start filling " + (endPieceNo - startPieceNo + 1) + ". " + startPieceNo + " to " + endPieceNo + " (of " + numPieces + ")");
			iRow = startPieceNo / iNumCols;

			int blockInfoStart = (endPieceNo / MAX_PIECE_CACHE) * MAX_PIECE_CACHE;
			if (oldBlockInfo == null || oldBlockInfoStart != blockInfoStart) {
				oldBlockInfo = new BlockInfo[numPieces];
				oldBlockInfoStart = blockInfoStart;
			}
			
			for (int i = startPieceNo; i <= endPieceNo; i++) {
				DiskManagerPiece dm_piece = dm_pieces[i];
				
				if (iCol >= iNumCols) {
					iCol = 0;
					iRow++;
				}

				BlockInfo newInfo = new BlockInfo();

				if ( i >= selectionStart && i <= selectionEnd ){
					newInfo.selectedRange = true;
				}

				boolean done = dm_piece.isDone();
				int iXPos = iCol * BLOCK_SIZE + 1;
				int iYPos = iRow * BLOCK_SIZE + 1;

				if (done) {
				
					newInfo.haveWidth = BLOCK_FILLSIZE;
				} else {
					// !done
					boolean partiallyDone = dm_piece.getNbWritten() > 0;

					int width = BLOCK_FILLSIZE;
					if (partiallyDone) {
						int iNewWidth = (int) (((float) dm_piece.getNbWritten()
										/ dm_piece.getNbBlocks()) * width);
						if (iNewWidth >= width)
							iNewWidth = width - 1;
						else if (iNewWidth <= 0)
							iNewWidth = 1;

						newInfo.haveWidth = iNewWidth;
					}
				}

				PEPiece pe_piece;
				
				if ( currentDLPieces != null ){
					pe_piece = currentDLPieces[i];
				}else{
					pe_piece = null;
				}
				
				newInfo.forced =  piecePicker != null && piecePicker.isForcePiece( i );
				
				if (pe_piece != null && pe_piece.hasUndownloadedBlock()) {
					newInfo.showDown = pe_piece.getNbRequests() == 0
							? SHOW_SMALL : SHOW_BIG;
				}

				if (uploadingPieces[i] > 0) {
					newInfo.showUp = uploadingPieces[i] < 2 ? SHOW_SMALL
							: SHOW_BIG;
				}


				if (availability != null) {
					newInfo.availNum = availability[i];
					if (minAvailability2 == availability[i]) {
						newInfo.availDotted = true;
					}
				} else {
					newInfo.availNum = -1;
				}

				boolean isSel = i == selectedPiece || (selectedPieceExplicit != null && selectedPieceExplicit.contains(i));
				
				if ( isSel ){
					newInfo.selected = true;
					result = iYPos - BLOCK_FILLSIZE;
				}

				if ( 	!forceRepaint && 
						oldBlockInfo != null &&
						i >= oldBlockInfoStart &&
						i - oldBlockInfoStart < oldBlockInfo.length &&
						newInfo.sameAs(oldBlockInfo[i - oldBlockInfoStart])) {

						// skip this one as unchanged

					iCol++;
					continue;
				}

				if ( newInfo.selectedRange ){
					Color fc = blockColors[BLOCKCOLOR_SHOWFILE ];
					
					gcImg.setBackground( fc );
					gcImg.fillRectangle(iCol * BLOCK_SIZE, iRow * BLOCK_SIZE, BLOCK_SIZE,
							BLOCK_SIZE);
					
					if ( fc != file_color ){
						
						file_color 			= fc;
						file_color_faded 	= Colors.getInstance().getLighterColor( fc, 75 );
					}
					
					gcImg.setBackground(file_color_faded);

				}else {
					gcImg.setBackground(pieceInfoCanvas.getBackground());
					gcImg.fillRectangle(iCol * BLOCK_SIZE, iRow * BLOCK_SIZE, BLOCK_SIZE,
							BLOCK_SIZE);
	
					if ( dm_piece.isMergeRead()){
						
						gcImg.setBackground(blockColors[BLOCKCOLOR_MERGE_READ]);
						
					}else if ( dm_piece.isMergeWrite()){
						
						gcImg.setBackground(blockColors[BLOCKCOLOR_MERGE_WRITE]);
						
					}else{
						
						gcImg.setBackground(blockColors[BLOCKCOLOR_HAVE]);
					}
					
					gcImg.fillRectangle(iXPos, iYPos, newInfo.haveWidth, BLOCK_FILLSIZE);
	
					gcImg.setBackground(blockColors[BLOCKCOLOR_NOHAVE]);
				}

				gcImg.fillRectangle(iXPos + newInfo.haveWidth, iYPos,
						BLOCK_FILLSIZE - newInfo.haveWidth, BLOCK_FILLSIZE);

				if (newInfo.showDown > 0) {
					drawDownloadIndicator(gcImg, iXPos, iYPos,
							newInfo.showDown == SHOW_SMALL);
				}

				if (newInfo.showUp > 0) {
					drawUploadIndicator(gcImg, iXPos, iYPos,
							newInfo.showUp == SHOW_SMALL);
				}
				
				Color availCol = isSel?Colors.red:blockColors[BLOCKCOLOR_AVAILCOUNT];
				
				int availNum = newInfo.availNum;
				
				String availText = availNum==-1?".":(availNum<100?String.valueOf( availNum ):"+");
				Point size = gcImg.stringExtent(availText);

				if ( newInfo.forced ){
					
					gcImg.setForeground(blockColors[ BLOCKCOLOR_FORCED ]);
					
					gcImg.setLineWidth( 2 );
					
					gcImg.drawRectangle(iXPos, iYPos, BLOCK_FILLSIZE, BLOCK_FILLSIZE );
					
					gcImg.setLineWidth( 1 );
					
				}else if (minAvailability == availNum){
					
					gcImg.setForeground(availCol);
					
					gcImg.drawRectangle(iXPos - 1, iYPos - 1, BLOCK_FILLSIZE + 1, BLOCK_FILLSIZE + 1);
					
				}else if (minAvailability2 == availNum){
					
					gcImg.setLineStyle(SWT.LINE_DOT);
					
					gcImg.setForeground(availCol);
					
					gcImg.drawRectangle(iXPos - 1, iYPos - 1, BLOCK_FILLSIZE + 1, BLOCK_FILLSIZE + 1);
					
					gcImg.setLineStyle(SWT.LINE_SOLID);
					
				}else if ( isSel ){

					gcImg.setForeground(availCol);
					
					gcImg.drawRectangle(iXPos - 1, iYPos - 1, BLOCK_FILLSIZE + 1, BLOCK_FILLSIZE + 1);
				}

				int x = iXPos + (BLOCK_FILLSIZE / 2) - (size.x / 2);
				int y = iYPos + (BLOCK_FILLSIZE / 2) - (size.y / 2);
				gcImg.setForeground(availCol);
				gcImg.drawText(availText, x, y, true);

				iCol++;
				numChanged++;
				if (i >= oldBlockInfoStart) {
					oldBlockInfo[i - oldBlockInfoStart] = newInfo;
				}
			}

//			log("end fill. numChanged=" + numChanged);

		} catch (Exception e) {
			Logger.log(new LogEvent(LogIDs.GUI, "drawing piece map", e));
		} finally {
			gcImg.dispose();
		}

		topLabelLHS = numPieces + " " + MessageText.getString("Peers.column.pieces"); 

		if (pm != null) {
			PiecePicker picker = pm.getPiecePicker();
			
			int seq_info = picker.getSequentialInfo();
			
			if ( seq_info != 0 ){
				
				int			seq_from;
				boolean		asc;
				
				if ( seq_info > 0 ){
					
					seq_from 	= seq_info-1;
					
					asc			= true;
					
				}else{
				
					seq_from	= - ( seq_info + 1 );
					asc			= false;
				}
				
				topLabelLHS += "; seq=" + seq_from + (asc?'+':'-');
			}
			
			String egm_info = picker.getEGMInfo();
			
			if ( egm_info != null ){
				
				topLabelLHS += "; EGM=" + egm_info;
			}
		}
		
		updateTopLabel();

		if (numChanged > 0) {
//			log("redraw");
			pieceInfoCanvas.redraw();
		}

		if (result == -1 && (selectedPiece >= 0 || selectedPieceExplicit != null)) {
			int i = selectedPiece >= 0 ? selectedPiece : selectedPieceExplicit.get(0);
			result = (i / iNumCols) * BLOCK_SIZE;
		}

		return( result );
	}

//	private static void log(String s) {
//		System.out.println(SystemTime.getCurrentTime() + " PIV] " + s);
//	}

	private static void drawDownloadIndicator(GC gcImg, int iXPos, int iYPos,
		boolean small) {
		if (small) {
			gcImg.setBackground(blockColors[BLOCKCOLOR_NEXT]);
			gcImg.fillPolygon(new int[] {
				iXPos + 2,
				iYPos + 2,
				iXPos + BLOCK_FILLSIZE - 1,
				iYPos + 2,
				iXPos + (BLOCK_FILLSIZE / 2),
				iYPos + BLOCK_FILLSIZE - 1
			});
		} else {
			gcImg.setBackground(blockColors[BLOCKCOLOR_TRANSFER]);
			gcImg.fillPolygon(new int[] {
				iXPos,
				iYPos,
				iXPos + BLOCK_FILLSIZE,
				iYPos,
				iXPos + (BLOCK_FILLSIZE / 2),
				iYPos + BLOCK_FILLSIZE
			});
		}
	}

	private static void drawUploadIndicator(GC gcImg, int iXPos, int iYPos, boolean small) {
		if (!small) {
			gcImg.setBackground(blockColors[BLOCKCOLOR_TRANSFER]);
			gcImg.fillPolygon(new int[] {
				iXPos,
				iYPos + BLOCK_FILLSIZE,
				iXPos + BLOCK_FILLSIZE,
				iYPos + BLOCK_FILLSIZE,
				iXPos + (BLOCK_FILLSIZE / 2),
				iYPos
			});
		} else {
			// Small Up Arrow each upload request
			gcImg.setBackground(blockColors[BLOCKCOLOR_NEXT]);
			gcImg.fillPolygon(new int[] {
				iXPos + 1,
				iYPos + BLOCK_FILLSIZE - 2,
				iXPos + BLOCK_FILLSIZE - 2,
				iYPos + BLOCK_FILLSIZE - 2,
				iXPos + (BLOCK_FILLSIZE / 2),
				iYPos + 2
			});
		}

	}

	private Composite getComposite() {
		return pieceInfoComposite;
	}

	private void delete() {
		if (imageLabel != null && !imageLabel.isDisposed()
				&& imageLabel.getImage() != null) {
			Image image = imageLabel.getImage();
			imageLabel.setImage(null);
			image.dispose();
		}

		if (img != null && !img.isDisposed()) {
			img.dispose();
			img = null;
		}

		if (font != null && !font.isDisposed()) {
			font.dispose();
			font = null;
		}

		synchronized( this ){
			if ( dlm != null){
				dlm.removePieceListener(this);
				dlm.removePeerListener(this);
				if ( current_pp != null ){
					current_pp.removeListener( this );
				}
				dlm = null;
			}
		}
		
		selectedPieceExplicit = null;
		selectedPiece = -1;
	}

	@Override
	public void
	somethingChanged(
		PiecePicker	pp,
		int			thing,
		Object		data )
	{	
		Utils.execSWTThread(this::refreshInfoCanvas);
	}
	
	private void obfuscatedImage(Image image) {
		UIDebugGenerator.obfuscateArea(image, topLabel.getControl(), "");
	}

	// @see com.biglybt.core.download.DownloadManagerPeerListener#pieceAdded(com.biglybt.core.peer.PEPiece)
	@Override
	public void pieceAdded(PEPiece piece) {
		fillPieceInfoSection();
	}

	// @see com.biglybt.core.download.DownloadManagerPeerListener#pieceRemoved(com.biglybt.core.peer.PEPiece)
	@Override
	public void pieceRemoved(PEPiece piece) {
		fillPieceInfoSection();
	}

	@Override
	public void
	peerManagerWillBeAdded(
		PEPeerManager	manager )
	{
	}

	@Override
	public void
	peerManagerAdded(
		PEPeerManager	manager )
	{		
		PiecePicker pp = manager.getPiecePicker();
		
		if ( pp != null ){
			
			current_pp = pp;
			
			pp.addListener( this );
		}
	}

	@Override
	public void
	peerManagerRemoved(
		PEPeerManager	manager )
	{
		PiecePicker pp = manager.getPiecePicker();

		if ( pp != null && current_pp == pp){
			
			pp.removeListener( this );
			
			current_pp = null;
		}
	}

	@Override
	public void
	peerAdded(
		PEPeer 	peer )
	{
	}

	@Override
	public void
	peerRemoved(
		PEPeer	peer )
	{
	}
	
	private static class BlockInfo {
		public int haveWidth;
		int availNum;
		boolean availDotted;
		/** 0 : no; 1 : Yes; 2: small */
		byte showUp;
		byte showDown;
		boolean selectedRange;
		boolean selected;
		boolean forced;
		/**
		 *
		 */
		public BlockInfo() {
			haveWidth = 0;
		}

		public boolean sameAs(BlockInfo otherBlockInfo) {
			if (otherBlockInfo == null) {
				return false;
			}
			return haveWidth == otherBlockInfo.haveWidth
					&& availNum == otherBlockInfo.availNum
					&& availDotted == otherBlockInfo.availDotted
					&& showDown == otherBlockInfo.showDown
					&& showUp == otherBlockInfo.showUp 
					&& selectedRange == otherBlockInfo.selectedRange 
					&& selected == otherBlockInfo.selected 
					&& forced == otherBlockInfo.forced;
		}

		@Override
		public String toString() {
			return "BlockInfo@" + Integer.toHexString(hashCode()) + "{" +
				"haveWidth=" + haveWidth +
				", availNum=" + availNum +
				", availDotted=" + availDotted +
				", showUp=" + showUp +
				", showDown=" + showDown +
				", selectedRange=" + selectedRange +
				", selected=" + selected +
				", forced=" + forced +
				'}';
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	// Don't show if is a tab and the MDI is Torrent Details View
	      // We are already registered to show under PiecesView for Torrent Details View
      	if (swtView instanceof MdiEntry) {
					if (UISWTInstance.VIEW_TORRENT_DETAILS.equals(
							((MdiEntry) swtView).getMDI().getViewID())) {
      			return false;
		      }
	      }
      	swtView.setTitle(getFullTitle());
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

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
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
  
  

	private static class DoNothingListener implements Listener {
		@Override
		public void handleEvent(Event event) {
		}
	}

	private static class SelectionListenerForcePiece extends SelectionAdapter {
		private final PiecePicker picker;
		private final int piece_number;
		private final MenuItem force_piece;

		public SelectionListenerForcePiece(PiecePicker picker, int piece_number, MenuItem force_piece) {
			this.picker = picker;
			this.piece_number = piece_number;
			this.force_piece = force_piece;
		}

		@Override
		public void
		widgetSelected(
			SelectionEvent e)
		{
			picker.setForcePiece(piece_number, force_piece.getSelection());
		}
	}

	private static class SelectionListenerResetPiece extends SelectionAdapter {
		private final DiskManagerPiece dm_piece;
		private final PEPiece pm_piece;

		public SelectionListenerResetPiece(DiskManagerPiece dm_piece, PEPiece pm_piece) {
			this.dm_piece = dm_piece;
			this.pm_piece = pm_piece;
		}

		@Override
		public void
		widgetSelected(
			SelectionEvent e)
		{
			if ( pm_piece != null ){

				pm_piece.reset();
				
			}else{
				
				dm_piece.reset();
			}
		}
	}
}
