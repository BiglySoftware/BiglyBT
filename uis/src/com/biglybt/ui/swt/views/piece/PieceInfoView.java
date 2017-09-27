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
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.impl.DiskManagerImpl;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPieceListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.PiecesView;
import com.biglybt.ui.swt.views.ViewUtils;
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
public class PieceInfoView
	implements DownloadManagerPieceListener,
	UISWTViewCoreEventListenerEx
{

	private final static int BLOCK_FILLSIZE = 14;

	private final static int BLOCK_SPACING = 3;

	private final static int BLOCK_SIZE = BLOCK_FILLSIZE + BLOCK_SPACING;

	private final static int BLOCKCOLOR_HAVE = 0;

	private final static int BLOCKCOLORL_NOHAVE = 1;

	private final static int BLOCKCOLOR_TRANSFER = 2;

	private final static int BLOCKCOLOR_NEXT = 3;

	private final static int BLOCKCOLOR_AVAILCOUNT = 4;

	public static final String MSGID_PREFIX = "PieceInfoView";

	private static final byte SHOW_BIG = 2;

	private static final byte SHOW_SMALL = 1;

	private Composite pieceInfoComposite;

	private ScrolledComposite sc;

	protected Canvas pieceInfoCanvas;

	private final Color[] blockColors;

	private Label topLabel;
	private String topLabelLHS = "";
	private String topLabelRHS = "";

	private Label imageLabel;

	// More delay for this view because of high workload
	private int graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update") * 2;

	private int loopFactor = 0;

	private Font font = null;

	Image img = null;

	private DownloadManager dlm;

	BlockInfo[] oldBlockInfo;

	/**
	 * Initialize
	 *
	 */
	public PieceInfoView() {
		blockColors = new Color[] {
			Colors.blues[Colors.BLUES_DARKEST],
			Colors.white,
			Colors.red,
			Colors.fadedRed,
			Colors.black
		};
	}

	@Override
	public boolean
	isCloneable()
	{
		return( true );
	}

	@Override
	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new PieceInfoView());
	}

	@Override
	public CloneConstructor
	getCloneConstructor()
	{
		return( 
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( PieceInfoView.class );
				}
				
				public java.util.List<Object>
				getParameters()
				{
					return( null );
				}
			});
	}
	
	private void dataSourceChanged(Object newDataSource) {
		//System.out.println( "dsc: dlm=" + dlm + ", new=" + (newDataSource instanceof Object[]?((Object[])newDataSource)[0]:newDataSource));

		DownloadManager newManager = ViewUtils.getDownloadManagerFromDataSource( newDataSource );

		if ( newManager != null ){

			oldBlockInfo = null;

		}else if (newDataSource instanceof Object[]) {
			Object[] objects = (Object[]) newDataSource;
			if (objects.length > 0 && (objects[0] instanceof PEPiece)) {
				PEPiece piece = (PEPiece) objects[0];
				DiskManager diskManager = piece.getDMPiece().getManager();
				if (diskManager instanceof DiskManagerImpl) {
					DiskManagerImpl dmi = (DiskManagerImpl) diskManager;
					newManager = dmi.getDownloadManager();
				}
			}
		}

		synchronized( this ){
			if (dlm != null) {
				dlm.removePieceListener(this);
			}
			dlm = newManager;
			if ( dlm != null ){
				dlm.addPieceListener(this, false);
			}
		}

		if ( newManager != null ){
			fillPieceInfoSection();
		}
	}

	private static String getFullTitle() {
		return MessageText.getString("PeersView.BlockView.title");
	}

	private void initialize(Composite composite) {
		if (pieceInfoComposite != null && !pieceInfoComposite.isDisposed()) {
			Logger.log(new LogEvent(LogIDs.GUI, LogEvent.LT_ERROR,
					"PeerInfoView already initialized! Stack: "
							+ Debug.getStackTrace(true, false)));
			delete();
		}
		createPeerInfoPanel(composite);

		fillPieceInfoSection();
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

		topLabel = new Label(pieceInfoComposite, SWT.NULL);
		gridData = new GridData(SWT.FILL, SWT.DEFAULT, false, false);
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
		pieceInfoCanvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (e.width <= 0 || e.height <= 0)
					return;
				try {
					Rectangle bounds = (img == null) ? null : img.getBounds();
					if (bounds == null || dlm == null || dlm.getPeerManager() == null ) {
						e.gc.fillRectangle(e.x, e.y, e.width, e.height);
					} else {
						if (e.x + e.width > bounds.width)
							e.gc.fillRectangle(bounds.width, e.y, e.x + e.width
									- bounds.width + 1, e.height);
						if (e.y + e.height > bounds.height)
							e.gc.fillRectangle(e.x, bounds.height, e.width, e.y + e.height
									- bounds.height + 1);

						int width = Math.min(e.width, bounds.width - e.x);
						int height = Math.min(e.height, bounds.height - e.y);
						e.gc.drawImage(img, e.x, e.y, width, height, e.x, e.y, width,
								height);
					}
				} catch (Exception ex) {
				}
			}
		});
		pieceInfoCanvas.addListener(SWT.KeyDown, new DoNothingListener());

		pieceInfoCanvas.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
				synchronized (PieceInfoView.this) {
  				if (alreadyFilling) {
  					return;
  				}

  				alreadyFilling = true;
				}

				// wrap in asyncexec because sc.setMinWidth (called later) doesn't work
				// too well inside a resize (the canvas won't size isn't always updated)
				Utils.execSWTThreadLater(0, new AERunnable() {
					@Override
					public void runSupport() {
						if (img != null) {
							int iOldColCount = img.getBounds().width / BLOCK_SIZE;
							int iNewColCount = pieceInfoCanvas.getClientArea().width
									/ BLOCK_SIZE;
							if (iOldColCount != iNewColCount)
								refreshInfoCanvas();
						}
						synchronized (PieceInfoView.this) {
							alreadyFilling = false;
						}
					}
				});
			}
		});

		sc.setContent(pieceInfoCanvas);

		pieceInfoCanvas.addMouseTrackListener(
			new MouseTrackAdapter()
			{
				@Override
				public void
				mouseHover(
					MouseEvent event )
				{
					int piece_number = getPieceNumber( event.x, event.y );

					if ( piece_number >= 0 ){

						DiskManager		disk_manager 	= dlm.getDiskManager();
						PEPeerManager	pm 				= dlm.getPeerManager();

						DiskManagerPiece	dm_piece = disk_manager.getPiece( piece_number );
						PEPiece 			pm_piece = pm.getPiece( piece_number );

						String	text =  "Piece " + piece_number + ": " + dm_piece.getString();

						if ( pm_piece != null ){

							text += ", active: " + pm_piece.getString();

						}else{

							if ( dm_piece.isNeeded() && !dm_piece.isDone()){

								text += ", inactive: " + pm.getPiecePicker().getPieceString( piece_number );
							}
						}

						topLabelRHS = text;

					}else{

						topLabelRHS = "";
					}

					updateTopLabel();
				}
			});

		final Menu menu = new Menu(pieceInfoCanvas.getShell(), SWT.POP_UP );

		pieceInfoCanvas.setMenu( menu );

		pieceInfoCanvas.addListener(
			SWT.MenuDetect,
			new Listener()
			{
				@Override
				public void
				handleEvent(
					Event event)
				{
					Point pt = pieceInfoCanvas.toControl(event.x, event.y);

					int	piece_number = getPieceNumber( pt.x, pt.y );

					menu.setData( "pieceNumber", piece_number );
				}
			});

		MenuBuildUtils.addMaintenanceListenerForMenu(
			menu,
			new MenuBuildUtils.MenuBuilder()
			{
				@Override
				public void
				buildMenu(
					Menu 		menu,
					MenuEvent 	event)
				{
					Integer pn = (Integer)menu.getData( "pieceNumber" );

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

						final MenuItem force_piece = new MenuItem( menu, SWT.CHECK );

						Messages.setLanguageText( force_piece, "label.force.piece" );

						boolean	done = dm_piece.isDone();

						force_piece.setEnabled( !done );

						if ( !done ){

							force_piece.setSelection( picker.isForcePiece( piece_number ));

							force_piece.addSelectionListener(
									new SelectionListenerForcePiece(picker, piece_number, force_piece));
						}

						final MenuItem reset_piece = new MenuItem( menu, SWT.PUSH );

						Messages.setLanguageText( reset_piece, "label.reset.piece" );

						boolean	can_reset = dm_piece.isDone() || dm_piece.getNbWritten() > 0;

						reset_piece.setEnabled( can_reset );

						reset_piece.addSelectionListener(
								new SelectionListenerResetPiece(dm_piece, pm_piece));
					}
				}
			});



		Legend.createLegendComposite(pieceInfoComposite,
				blockColors, new String[] {
					"PiecesView.BlockView.Have",
					"PiecesView.BlockView.NoHave",
					"PeersView.BlockView.Transfer",
					"PeersView.BlockView.NextRequest",
					"PeersView.BlockView.AvailCount"
				}, new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));

		int iFontPixelsHeight = 10;
		int iFontPointHeight = (iFontPixelsHeight * 72)
				/ Utils.getDPIRaw( pieceInfoCanvas.getDisplay()).y;
		Font f = pieceInfoCanvas.getFont();
		FontData[] fontData = f.getFontData();
		fontData[0].setHeight(iFontPointHeight);
		font = new Font(pieceInfoCanvas.getDisplay(), fontData);

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

		PEPeerManager pm = manager.getPeerManager();

		if ( pm == null ){

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

		if ( piece_number >= pm.getPiecePicker().getNumberOfPieces()){

			return( -1 );

		}else{

			return( piece_number );
		}
	}

	private boolean alreadyFilling = false;

	private UISWTView swtView;

	private void fillPieceInfoSection() {
		synchronized (this) {
			if (alreadyFilling) {
				return;
			}
			alreadyFilling = true;
		}

		Utils.execSWTThreadLater(100, new AERunnable() {
			@Override
			public void runSupport() {
				synchronized (PieceInfoView.this) {
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
					synchronized (PieceInfoView.this) {
						alreadyFilling = false;
					}
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
	updateTopLabel()
	{
		String text = topLabelLHS;

		if ( text.length() > 0 && topLabelRHS.length() > 0 ){

			text += "; " + topLabelRHS;
		}

		topLabel.setText( text );
	}

	protected void refreshInfoCanvas() {
		synchronized (PieceInfoView.this) {
			alreadyFilling = false;
		}

		if (pieceInfoCanvas == null || pieceInfoCanvas.isDisposed()
				|| !pieceInfoCanvas.isVisible()) {
			return;
		}
		pieceInfoCanvas.layout(true);
		Rectangle bounds = pieceInfoCanvas.getClientArea();
		if (bounds.width <= 0 || bounds.height <= 0) {
			topLabelLHS = "";
			updateTopLabel();
			return;
		}

		if (dlm == null) {
			GC gc = new GC(pieceInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();
			topLabelLHS = MessageText.getString("view.one.download.only");
			topLabelRHS = "";
			updateTopLabel();

			return;
		}

		PEPeerManager pm = dlm.getPeerManager();

		DiskManager dm = dlm.getDiskManager();

		if (pm == null || dm == null) {
			GC gc = new GC(pieceInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();
			topLabelLHS = "";
			updateTopLabel();

			return;
		}

		int iNumCols = bounds.width / BLOCK_SIZE;
		int iNeededHeight = (((dm.getNbPieces() - 1) / iNumCols) + 1) * BLOCK_SIZE;

		if (img != null && !img.isDisposed()) {
			Rectangle imgBounds = img.getBounds();
			if (imgBounds.width != bounds.width || imgBounds.height != iNeededHeight) {
				oldBlockInfo = null;
				img.dispose();
				img = null;
			}
		}

		DiskManagerPiece[] dm_pieces = dm.getPieces();

		PEPiece[] currentDLPieces = pm.getPieces();
		byte[] uploadingPieces = new byte[dm_pieces.length];

		// find upload pieces
		for (PEPeer peer : pm.getPeers()) {
			int[] peerRequestedPieces = peer.getIncomingRequestedPieceNumbers();
			if (peerRequestedPieces != null && peerRequestedPieces.length > 0) {
				int pieceNum = peerRequestedPieces[0];
				if (uploadingPieces[pieceNum] < 2)
					uploadingPieces[pieceNum] = 2;
				for (int j = 1; j < peerRequestedPieces.length; j++) {
					pieceNum = peerRequestedPieces[j];
					if (uploadingPieces[pieceNum] < 1)
						uploadingPieces[pieceNum] = 1;
				}
			}

		}

		if (sc.getMinHeight() != iNeededHeight) {
			sc.setMinHeight(iNeededHeight);
			sc.layout(true, true);
			bounds = pieceInfoCanvas.getClientArea();
		}

		int[] availability = pm.getAvailability();

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


		BlockInfo[] newBlockInfo = new BlockInfo[dm_pieces.length];

		int iRow = 0;
		try {
			// use advanced capabilities for faster drawText
			gcImg.setAdvanced(true);

			if (oldBlockInfo == null) {
				gcImg.setBackground(pieceInfoCanvas.getBackground());
				gcImg.fillRectangle(0, 0, bounds.width, iNeededHeight);
			}

			gcImg.setFont(font);

			int iCol = 0;
			for (int i = 0; i < dm_pieces.length; i++) {
				if (iCol >= iNumCols) {
					iCol = 0;
					iRow++;
				}

				newBlockInfo[i] = new BlockInfo();

				int colorIndex;
				boolean done = dm_pieces[i].isDone();
				int iXPos = iCol * BLOCK_SIZE + 1;
				int iYPos = iRow * BLOCK_SIZE + 1;

				if (done) {
					colorIndex = BLOCKCOLOR_HAVE;
					newBlockInfo[i].haveWidth = BLOCK_FILLSIZE;
				} else {
					// !done
					boolean partiallyDone = dm_pieces[i].getNbWritten() > 0;

					int width = BLOCK_FILLSIZE;
					if (partiallyDone) {
						int iNewWidth = (int) (((float) dm_pieces[i].getNbWritten() / dm_pieces[i].getNbBlocks()) * width);
						if (iNewWidth >= width)
							iNewWidth = width - 1;
						else if (iNewWidth <= 0)
							iNewWidth = 1;

						newBlockInfo[i].haveWidth = iNewWidth;
					}
				}

				if (currentDLPieces[i] != null && currentDLPieces[i].hasUndownloadedBlock()) {
					newBlockInfo[i].showDown = currentDLPieces[i].getNbRequests() == 0
							? SHOW_SMALL : SHOW_BIG;
				}

				if (uploadingPieces[i] > 0) {
					newBlockInfo[i].showUp = uploadingPieces[i] < 2 ? SHOW_SMALL
							: SHOW_BIG;
				}


				if (availability != null) {
					newBlockInfo[i].availNum = availability[i];
					if (minAvailability2 == availability[i]) {
						newBlockInfo[i].availDotted = true;
					}
				} else {
					newBlockInfo[i].availNum = -1;
				}

				if (oldBlockInfo != null && i < oldBlockInfo.length
						&& oldBlockInfo[i].sameAs(newBlockInfo[i])) {
					iCol++;
					continue;
				}

				gcImg.setBackground(pieceInfoCanvas.getBackground());
				gcImg.fillRectangle(iCol * BLOCK_SIZE, iRow * BLOCK_SIZE, BLOCK_SIZE,
						BLOCK_SIZE);

				colorIndex = BLOCKCOLOR_HAVE;
				gcImg.setBackground(blockColors[colorIndex]);
				gcImg.fillRectangle(iXPos, iYPos, newBlockInfo[i].haveWidth, BLOCK_FILLSIZE);

				colorIndex = BLOCKCOLORL_NOHAVE;
				gcImg.setBackground(blockColors[colorIndex]);
				gcImg.fillRectangle(iXPos + newBlockInfo[i].haveWidth, iYPos, BLOCK_FILLSIZE - newBlockInfo[i].haveWidth, BLOCK_FILLSIZE);

				if (newBlockInfo[i].showDown > 0) {
					drawDownloadIndicator(gcImg, iXPos, iYPos,
							newBlockInfo[i].showDown == SHOW_SMALL);
				}

				if (newBlockInfo[i].showUp > 0) {
					drawUploadIndicator(gcImg, iXPos, iYPos,
							newBlockInfo[i].showUp == SHOW_SMALL);
				}

				if (newBlockInfo[i].availNum != -1) {
					if (minAvailability == newBlockInfo[i].availNum) {
						gcImg.setForeground(blockColors[BLOCKCOLOR_AVAILCOUNT]);
						gcImg.drawRectangle(iXPos - 1, iYPos - 1, BLOCK_FILLSIZE + 1,
								BLOCK_FILLSIZE + 1);
					}
					if (minAvailability2 == newBlockInfo[i].availNum) {
						gcImg.setLineStyle(SWT.LINE_DOT);
						gcImg.setForeground(blockColors[BLOCKCOLOR_AVAILCOUNT]);
						gcImg.drawRectangle(iXPos - 1, iYPos - 1, BLOCK_FILLSIZE + 1,
								BLOCK_FILLSIZE + 1);
						gcImg.setLineStyle(SWT.LINE_SOLID);
					}

					String sNumber = String.valueOf(newBlockInfo[i].availNum);
					Point size = gcImg.stringExtent(sNumber);

					if (newBlockInfo[i].availNum < 100) {
						int x = iXPos + (BLOCK_FILLSIZE / 2) - (size.x / 2);
						int y = iYPos + (BLOCK_FILLSIZE / 2) - (size.y / 2);
						gcImg.setForeground(blockColors[BLOCKCOLOR_AVAILCOUNT]);
						gcImg.drawText(sNumber, x, y, true);
					}
				}


				iCol++;
			}
			oldBlockInfo = newBlockInfo;
		} catch (Exception e) {
			Logger.log(new LogEvent(LogIDs.GUI, "drawing piece map", e));
		} finally {
			gcImg.dispose();
		}

		topLabelLHS = MessageText.getString("PiecesView.BlockView.Header",
				new String[] {
					"" + iNumCols,
					"" + (iRow + 1),
					"" + dm_pieces.length
				});

		updateTopLabel();

		pieceInfoCanvas.redraw();
	}

	private void drawDownloadIndicator(GC gcImg, int iXPos, int iYPos,
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

	private void drawUploadIndicator(GC gcImg, int iXPos, int iYPos, boolean small) {
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
				dlm = null;
			}
		}
	}

	private void obfuscatedImage(Image image) {
		UIDebugGenerator.obfuscateArea(image, topLabel, "");
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

	private static class BlockInfo {
		public int haveWidth;
		int availNum;
		boolean availDotted;
		/** 0 : no; 1 : Yes; 2: small */
		byte showUp;
		byte showDown;

		/**
		 *
		 */
		public BlockInfo() {
			haveWidth = -1;
		}

		public boolean sameAs(BlockInfo otherBlockInfo) {
			return haveWidth == otherBlockInfo.haveWidth
					&& availNum == otherBlockInfo.availNum
					&& availDotted == otherBlockInfo.availDotted
					&& showDown == otherBlockInfo.showDown
					&& showUp == otherBlockInfo.showUp;
		}
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
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

      case UISWTViewEvent.TYPE_FOCUSGAINED:
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
			dm_piece.reset();

			if ( pm_piece != null ){

				pm_piece.reset();
			}
		}
	}
}
