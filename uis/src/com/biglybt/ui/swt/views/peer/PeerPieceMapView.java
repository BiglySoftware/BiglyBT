/*
 * Created : Oct 2, 2005
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

package com.biglybt.ui.swt.views.peer;

import java.io.InputStream;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.Legend;
import com.biglybt.ui.swt.components.Legend.LegendListener;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.mainwindow.HSLColor;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.piece.PieceMapView;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;

/**
 * Piece Map subview for Peers View.
 * Testing bed for SubView stuff.
 *
 * @author TuxPaper
 * @created 2005/10/02
 *
 * @todo on paint, paint cached image instead of recalc
 */
public class PeerPieceMapView
	implements UISWTViewCoreEventListener
{
	public static final String VIEW_ID = "PeerPieceMapView";
	
	private final static int BLOCK_FILLSIZE = 14;

	private final static int BLOCK_SPACING = 2;

	private final static int BLOCK_SIZE = BLOCK_FILLSIZE + BLOCK_SPACING;

	private final static int BLOCKCOLOR_AVAIL_HAVE = 0;

	private final static int BLOCKCOLOR_AVAIL_NOHAVE = 1;

	private final static int BLOCKCOLOR_NOAVAIL_HAVE = 2;

	private final static int BLOCKCOLOR_NOAVAIL_NOHAVE = 3;

	private final static int BLOCKCOLOR_TRANSFER = 4;

	private final static int BLOCKCOLOR_NEXT = 5;

	private static final byte SHOW_BIG = 2;

	private static final byte SHOW_SMALL = 1;
	private static final boolean DEBUG = false;

	private Composite peerInfoComposite;

	private ScrolledComposite sc;

	protected Canvas peerInfoCanvas;

	private Color[] blockColors;

	private Label topLabel;

	private Label imageLabel;

	// More delay for this view because of high workload
	private int graphicsUpdate = COConfigurationManager
			.getIntParameter("Graphics Update") * 2;

	private int loopFactor = 0;

	private PEPeer peer;

	private Plugin countryLocator = null;

	private String sCountryImagesDir;

	private Font font = null;

	Image img = null;

	protected boolean alreadyFilling;

	private UISWTView swtView;

	private int		currentNumColumns;
	private int		currentNumPieces;
	
	BlockInfo[] oldBlockInfo;

	private final List<Integer> distinctPieceCache = new ArrayList<>();

	/**
	 * Initialize
	 *
	 */
	public PeerPieceMapView() {
		blockColors = new Color[] { Colors.blues[Colors.BLUES_DARKEST],
				Colors.blues[Colors.BLUES_MIDLIGHT], Colors.fadedGreen, Colors.white,
				Colors.red, Colors.fadedRed };

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				initCountryPlugin();
			}
		});
	}

	private void initCountryPlugin() {
		// Pull in Country Information if the plugin exists
		/**
		 * If this view was a real plugin view, we could attach the CountryLocator.jar
		 * to our project, cast countryLocator as CountryLocator (instead of Plugin),
		 * and then directly call the functions.
		 *
		 * Since we are in core, and we don't want to add a dependency on the
		 * CountryLocator.jar, we invoke the methods via the Class object.
		 */
		try {
			PluginInterface pi = PluginInitializer.getDefaultInterface()
					.getPluginManager().getPluginInterfaceByID("CountryLocator");
			if (pi != null) {
				countryLocator = pi.getPlugin();
				if (!pi.getPluginState().isOperational()
						|| pi.getUtilities().compareVersions(pi.getPluginVersion(), "1.6") < 0)
					countryLocator = null;

				if (countryLocator != null) {
					sCountryImagesDir = (String) countryLocator.getClass().getMethod(
							"getImageLocation", new Class[] { Integer.TYPE }).invoke(
							countryLocator, new Object[] { new Integer(0) });
				}
			}
		} catch (Throwable t) {

		}
	}

	private void dataSourceChanged(Object newDataSource) {
		if (newDataSource instanceof Object[]
				&& ((Object[]) newDataSource).length > 0) {
			newDataSource = ((Object[]) newDataSource)[0];
		}

		if (newDataSource instanceof PEPeer) {
			peer = (PEPeer) newDataSource;
		} else {
			peer = null;
		}

		oldBlockInfo = null;

		Utils.execSWTThreadLater(0, () -> swt_fillPeerInfoSection());
	}

	private String getFullTitle() {
		return MessageText.getString("PeersView.BlockView.title");
	}

	private void initialize(Composite composite) {
		if (peerInfoComposite != null && !peerInfoComposite.isDisposed()) {
			Logger.log(new LogEvent(LogIDs.GUI, LogEvent.LT_ERROR,
					"PeerPieceMapView already initialized! Stack: "
							+ Debug.getStackTrace(true, false)));
			delete();
		}
		createPeerInfoPanel(composite);
	}

	private Composite createPeerInfoPanel(Composite parent) {
		GridLayout layout;
		GridData gridData;

		// Peer Info section contains
		// - Peer's Block display
		// - Peer's Datarate
		peerInfoComposite = new Composite(parent, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		peerInfoComposite.setLayout(layout);
		gridData = new GridData(GridData.FILL, GridData.FILL, true, true);
		peerInfoComposite.setLayoutData(gridData);

		imageLabel = new Label(peerInfoComposite, SWT.NULL);
		gridData = new GridData();
		if (ImageRepository.hasCountryFlags( false ) || countryLocator != null)
			gridData.widthHint = 28;
		imageLabel.setLayoutData(gridData);

		topLabel = new Label(peerInfoComposite, SWT.NULL);
		gridData = new GridData(SWT.FILL, SWT.DEFAULT, false, false);
		topLabel.setLayoutData(gridData);

		sc = new ScrolledComposite(peerInfoComposite, SWT.V_SCROLL);
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

		peerInfoCanvas = new Canvas(sc, SWT.NO_REDRAW_RESIZE | SWT.NO_BACKGROUND);
		gridData = new GridData(GridData.FILL, SWT.DEFAULT, true, false);
		peerInfoCanvas.setLayoutData(gridData);
		peerInfoCanvas.addPaintListener(e -> {
			if (e.width <= 0 || e.height <= 0) {
				return;
			}
			try {
				Rectangle bounds = (img == null) ? null : img.getBounds();
				if (bounds == null) {
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
					if (DEBUG) {
						log("draw " + e.x + "x" + e.y + ", w=" + width + ", h=" + height + "; af=" + alreadyFilling);
					}
				}
			} catch (Exception ex) {
			}
		});
		Listener doNothingListener = new Listener() {
			@Override
			public void handleEvent(Event event) {
			}
		};
		peerInfoCanvas.addListener(SWT.KeyDown, doNothingListener);
		
		peerInfoCanvas.addListener(SWT.Resize, e -> {
			if (!peerInfoCanvas.isVisible()) {
				return;
			}

			synchronized (PeerPieceMapView.this) {
				if (DEBUG) {
					log("resize.  af=" + alreadyFilling);
				}
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
					int iNewColCount = peerInfoCanvas.getClientArea().width / BLOCK_SIZE;
					if (DEBUG) {
						log("resize. col " + iOldColCount + "->" + iNewColCount);
					}
					if (iOldColCount != iNewColCount)
						refreshInfoCanvas();
				}

				synchronized (PeerPieceMapView.this) {
					alreadyFilling = false;
				}
			});
		});

		sc.setContent(peerInfoCanvas);

		peerInfoCanvas.addListener(SWT.Move, event -> swt_fillPeerInfoSection());
		sc.addListener(SWT.Resize, event -> swt_fillPeerInfoSection());

		Legend.createLegendComposite(peerInfoComposite, blockColors, new String[] {
			"PeersView.BlockView.Avail.Have",
			"PeersView.BlockView.Avail.NoHave",
			"PeersView.BlockView.NoAvail.Have",
			"PeersView.BlockView.NoAvail.NoHave",
			"PeersView.BlockView.Transfer",
			"PeersView.BlockView.NextRequest"
		}, null, new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1), true,
				new LegendListener() {
					@Override
					public void hoverChange(boolean entry, int index) {
					}

					@Override
					public void visibilityChange(boolean visible, int index) {
					}

					@Override
					public void colorChange(int index) {
						oldBlockInfo = null;
						distinctPieceCache.clear();
						refreshInfoCanvas();
					}
				});

		font = FontUtils.getFontPercentOf(peerInfoCanvas.getFont(), 0.7f);

		return peerInfoComposite;
	}

	private void swt_fillPeerInfoSection() {
		if (peerInfoComposite == null || peerInfoComposite.isDisposed()) {
			return;
		}
		if (imageLabel.getImage() != null) {
			Image image = imageLabel.getImage();
			imageLabel.setImage(null);
			image.dispose();
		}

		if (peer == null) {
			topLabel.setText("");
		} else {
			String s = peer.getClient();
			if (s == null){
				s = "";
			}else if (s.length() > 0){
				s += "; ";
			}

			s += peer.getIp()
					+ "; "
					+ DisplayFormatters.formatPercentFromThousands(peer
							.getPercentDoneInThousandNotation());
			
			topLabel.setText(s);

			Image flag = ImageRepository.getCountryFlag( peer, false );

			if ( flag != null ){

				flag = new Image( flag.getDevice(), flag.getImageData());

				flag.setBackground(imageLabel.getBackground());

				imageLabel.setImage(flag);

				String[] country_details = PeerUtils.getCountryDetails( peer );

				if ( country_details != null && country_details.length == 2 ){
					Utils.setTT(imageLabel,country_details[0] + "- " + country_details[1]);
				}else{
					Utils.setTT(imageLabel, "" );
				}
			}else if (countryLocator != null) {
					try {
						String sCountry = (String) countryLocator.getClass().getMethod(
								"getIPCountry", new Class[] { String.class, Locale.class })
								.invoke(countryLocator,
										new Object[] { peer.getIp(), Locale.getDefault() });

						String sCode = (String) countryLocator.getClass().getMethod(
								"getIPISO3166", new Class[] { String.class }).invoke(
								countryLocator, new Object[] { peer.getIp() });

						Utils.setTT(imageLabel,sCode + "- " + sCountry);

						InputStream is = countryLocator.getClass().getClassLoader()
								.getResourceAsStream(
										sCountryImagesDir + "/" + sCode.toLowerCase() + ".png");
						if (is != null) {
							try{
								Image img = new Image(imageLabel.getDisplay(), is);
								img.setBackground(imageLabel.getBackground());
								imageLabel.setImage(img);
							}finally{
								is.close();
							}
						}

					} catch (Exception e) {
						// ignore
					}
			}else{
				Utils.setTT(imageLabel, "" );
			}

		}
		refreshInfoCanvas();
	}

	private void refresh() {
		if (loopFactor++ % graphicsUpdate == 0) {
			refreshInfoCanvas();
		}
	}

	/**
	 * Constructs and image representing the download state of _all_
	 * the pieces in the torrent.  Particularily slow when there's lots of pieces,
	 * and also wasteful since only a fraction of them ever get painted at
	 * any given time.
	 *
	 * TODO: Construct image for visible area only or something
	 */
	private void refreshInfoCanvas() {
		alreadyFilling = false;

		if (peerInfoComposite == null || peerInfoComposite.isDisposed()
				|| !peerInfoComposite.isVisible()) {
			return;
		}

		peerInfoCanvas.layout(true);
		Rectangle bounds = peerInfoCanvas.getClientArea();
		if (bounds.width <= 0 || bounds.height <= 0)
			return;

		if (peer == null || peer.getPeerState() != PEPeer.TRANSFERING) {
			GC gc = new GC(peerInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();

			return;
		}

		PEPeerManager pm = peer.getManager();

		DiskManager dm = pm==null?null:pm.getDiskManager();

		BitFlags peerHavePieces = peer.getAvailable();
		
		if ( dm == null || peerHavePieces == null) {
			GC gc = new GC(peerInfoCanvas);
			gc.fillRectangle(bounds);
			gc.dispose();

			return;
		}

		int iNumCols = bounds.width / BLOCK_SIZE;

		if ( currentNumColumns != iNumCols ){

			currentNumColumns = iNumCols;

			oldBlockInfo = null;

			if ( img != null ){

				img.dispose();

				img = null;
			}
			distinctPieceCache.clear();
		}

		int numPieces =  dm.getNbPieces();
		int iNeededHeight = (((numPieces - 1) / iNumCols) + 1) * BLOCK_SIZE;

		if (img != null && !img.isDisposed()) {
			Rectangle imgBounds = img.getBounds();
			if ( 	numPieces != currentNumPieces ||
				imgBounds.width != bounds.width ||
				imgBounds.height != iNeededHeight) {
				oldBlockInfo = null;
				img.dispose();
				img = null;
				distinctPieceCache.clear();
			}
		}

		if (sc.getMinHeight() != iNeededHeight) {
			boolean scrollVisible = sc.getVerticalBar() != null && sc.getVerticalBar().getVisible();
			if (DEBUG) {
				log("minHeight sc=" + sc.getMinHeight() +
					", needed=" + iNeededHeight + "; sc.ca=" + sc.getClientArea()
					+ "; sc.vbv=" + scrollVisible + "; " + Debug.getCompressedStackTrace(3));
			}
			sc.setMinHeight(iNeededHeight);
			sc.layout(true, true);
			if (!scrollVisible && iNeededHeight > sc.getClientArea().height) {
				if (DEBUG) {
					log("skip canvas fill, scrollbar incoming. " + (sc.getVerticalBar() != null && sc.getVerticalBar().getVisible()));
				}
				return;
			}
			bounds = peerInfoCanvas.getClientArea();
		}

		currentNumPieces = numPieces;

		DiskManagerPiece[] dm_pieces = dm.getPieces();

		Rectangle dirtyBounds = null;

		if (img == null) {
			img = new Image(peerInfoCanvas.getDisplay(), bounds.width, iNeededHeight);
			oldBlockInfo = null;
			dirtyBounds = peerInfoCanvas.getBounds();
		}

		GC gcImg = new GC(img);
		gcImg.setFont(font);

		int numChanged = 0;
		int numCopyArea = 0;
		boolean needsMore = false;
		long startTime = SystemTime.getMonotonousTime();
		try {
			// use advanced capabilities for faster drawText
			gcImg.setAdvanced(true);
			Color canvasBG = peerInfoCanvas.getBackground();

			if (oldBlockInfo == null) {
				gcImg.setBackground(canvasBG);
				gcImg.fillRectangle(0, 0, bounds.width, iNeededHeight);
			}

			int[] availability = pm.getAvailability();

			int minAvailability = Integer.MAX_VALUE;
			int minAvailability2 = Integer.MAX_VALUE;
			int maxAvailability = 0;
			if (availability != null && availability.length > 0) {
				for (int anAvailability : availability) {
					if (anAvailability > maxAvailability) {
						maxAvailability = anAvailability;
					}
					if (anAvailability != 0 && anAvailability < minAvailability) {
						minAvailability2 = minAvailability;
						minAvailability = anAvailability;
						if (minAvailability == 1) {
							break;
						}
					}
				}
			}

			int iNextDLPieceID = -1;
			int iDLPieceID = -1;
			int[] ourRequestedPieces = peer.getOutgoingRequestedPieceNumbers();
			if (ourRequestedPieces != null) {
				if (!peer.isChokingMe()) {
					// !choking == downloading

					if (ourRequestedPieces.length > 0) {
						iDLPieceID = ourRequestedPieces[0];
						if (ourRequestedPieces.length > 1)
							iNextDLPieceID = ourRequestedPieces[1];
					}
				} else {
					if (ourRequestedPieces.length > 0)
						iNextDLPieceID = ourRequestedPieces[0];
				}

				//			if (iNextDLPieceID == -1) {
				//				iNextDLPieceID = peer.getNextPieceNumberGuess();
				//			}
			}

			int[] peerRequestedPieces = peer.getIncomingRequestedPieceNumbers();
			if (peerRequestedPieces == null)
				peerRequestedPieces = new int[0];

			int peerNextRequestedPiece = -1;
			if (peerRequestedPieces.length > 0)
				peerNextRequestedPiece = peerRequestedPieces[0];
			Arrays.sort(peerRequestedPieces);
			
			int iRow = 0;
			int iCol = 0;

			Rectangle canvasBounds = peerInfoCanvas.getBounds();
			if (canvasBounds.y < 0) {
				iRow = (-canvasBounds.y) / BLOCK_SIZE;
			}
			int startPieceNo = iRow * iNumCols;
			int drawHeight = sc.getClientArea().height;
			// +1 in case first visible row is partial
			int endPieceNo = Math.min(numPieces - 1, (int) (startPieceNo
				+ (Math.ceil(drawHeight / (float) BLOCK_SIZE) + 1) * iNumCols) - 1);

			if (DEBUG) {
				log("start filling " + (endPieceNo - startPieceNo + 1) + ". " + startPieceNo + " to " + endPieceNo + " (of " + numPieces + "), canvasBounds.y=" + canvasBounds.y + ", row=" + iRow);
			}

			if (oldBlockInfo == null) {
				oldBlockInfo = new BlockInfo[numPieces];
				distinctPieceCache.clear();
			}

			pieceLoop:
			for (int i = startPieceNo; i <= endPieceNo; i++) {
				int colorIndex;
				DiskManagerPiece dm_piece = dm_pieces == null ? null : dm_pieces[i];

				if (iCol >= iNumCols) {
					iCol = 0;
					iRow++;
				}

				boolean done = dm_piece != null && dm_piece.isDone();
				int iXPos = iCol * BLOCK_SIZE;
				int iYPos = iRow * BLOCK_SIZE;

				BlockInfo newInfo = new BlockInfo();

				newInfo.peerHas = peerHavePieces.flags[i];
				newInfo.showDown = i == iDLPieceID ? SHOW_BIG : i == iNextDLPieceID ? SHOW_SMALL : 0;
				newInfo.showUp = i == peerNextRequestedPiece ? SHOW_BIG : Arrays.binarySearch(peerRequestedPieces, i) >= 0 ? SHOW_SMALL : 0;
				newInfo.availNum = availability == null ? -1 : availability[i];


				if (done) {

					newInfo.haveWidth = BLOCK_FILLSIZE;
				} else if (dm_piece != null) {
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

				newInfo.bounds = new Rectangle(iXPos - 1, iYPos - 1, BLOCK_SIZE, BLOCK_SIZE);

				BlockInfo oldInfo = oldBlockInfo != null ? oldBlockInfo[i] : null;

				if (newInfo.sameAs(oldInfo)) {

					// skip this one as unchanged
					iCol++;
					continue;
				}

				// Use copyArea if in cache. Saves the most time
				if (oldBlockInfo != null) {
					for (Iterator<Integer> iter = distinctPieceCache.iterator(); iter.hasNext();) {
						Integer cachePieceNo = iter.next();

						BlockInfo cacheInfo = oldBlockInfo[cachePieceNo];
						if (cacheInfo == null || !cacheInfo.sameAs(newInfo)) {
							continue;
						}

						Rectangle cacheBounds = cacheInfo.bounds;
						gcImg.copyArea(cacheBounds.x, cacheBounds.y, cacheBounds.width,
							cacheBounds.height, iXPos - 1, iYPos - 1, false);
						//log("copyArea(" + cacheBounds.x + ", " + cacheBounds.y + ", " + cacheBounds.width + ", " + cacheBounds.height + ", "	+ (iXPos - 1) + ", " + (iYPos - 1) + ")\n" + cacheInfo);
						Rectangle rect = new Rectangle(iXPos - 1, iYPos - 1,
							cacheBounds.width, cacheBounds.height);
						if (dirtyBounds == null) {
							dirtyBounds = rect;
						} else {
							dirtyBounds.add(rect);
						}

						iCol++;
						numCopyArea++;
						oldBlockInfo[i] = newInfo;
						if (iter.hasNext()) {
							// move to end
							iter.remove();
							distinctPieceCache.add(cachePieceNo);
						}

						if (SystemTime.getMonotonousTime() - startTime > 200) {
							needsMore = true;
							break pieceLoop;
						}
						continue pieceLoop;
					}
				}

				Colors colorsInstance = Colors.getInstance();
				Color bg;

				if (newInfo.haveWidth > 0) {
					bg = blockColors[newInfo.peerHas ? BLOCKCOLOR_AVAIL_HAVE : BLOCKCOLOR_NOAVAIL_HAVE];

					if (newInfo.availNum >= 0 && newInfo.availNum < maxAvailability) {
						HSLColor hslColor = new HSLColor();
						hslColor.initHSLbyRGB(bg.getRed(), bg.getGreen(), bg.getBlue());
						float pct = (1 - ((float) newInfo.availNum / maxAvailability)) / 2;
						hslColor.blend(canvasBG.getRed(), canvasBG.getGreen(), canvasBG.getBlue(), pct);
						bg = ColorCache.getColor(gcImg.getDevice(), hslColor.getRed(), hslColor.getGreen(), hslColor.getBlue());
					}

					gcImg.setBackground(bg);

					gcImg.fillRectangle(iXPos, iYPos, newInfo.haveWidth, BLOCK_FILLSIZE);
				}

				colorIndex = done
						? newInfo.peerHas ? BLOCKCOLOR_AVAIL_HAVE : BLOCKCOLOR_NOAVAIL_HAVE
						: newInfo.peerHas ? BLOCKCOLOR_AVAIL_NOHAVE
								: BLOCKCOLOR_NOAVAIL_NOHAVE;
				bg = blockColors[colorIndex];

				if (newInfo.availNum >= 0 && newInfo.availNum < maxAvailability) {
					HSLColor hslColor = new HSLColor();
					hslColor.initHSLbyRGB(bg.getRed(), bg.getGreen(), bg.getBlue());
					float pct = (1 - ((float) newInfo.availNum / maxAvailability)) / 2;
					hslColor.blend(canvasBG.getRed(), canvasBG.getGreen(), canvasBG.getBlue(), pct);
					bg = ColorCache.getColor(gcImg.getDevice(), hslColor.getRed(), hslColor.getGreen(), hslColor.getBlue());
				}

				gcImg.setBackground(bg);
				gcImg.fillRectangle(iXPos + newInfo.haveWidth, iYPos,
					BLOCK_FILLSIZE - newInfo.haveWidth, BLOCK_FILLSIZE);


				// Down Arrow inside box for "dowloading" piece
				// Small Down Arrow inside box for next download piece
				if (newInfo.showDown > 0) {
					boolean isSmall = newInfo.showDown == SHOW_SMALL;
					bg = blockColors[isSmall ? BLOCKCOLOR_NEXT : BLOCKCOLOR_TRANSFER];
					gcImg.setBackground(bg);
					PieceMapView.drawDownloadIndicator(gcImg, iXPos, iYPos, isSmall, BLOCK_FILLSIZE);
				}

				// Up Arrow in uploading piece
				// Small Up Arrow each upload request
				if (newInfo.showUp > 0) {
					boolean isSmall = newInfo.showUp == SHOW_SMALL;
					bg = blockColors[isSmall ? BLOCKCOLOR_NEXT : BLOCKCOLOR_TRANSFER];
					gcImg.setBackground(bg);
					PieceMapView.drawUploadIndicator(gcImg, iXPos, iYPos, isSmall, BLOCK_FILLSIZE);
				}

				if (newInfo.availNum != -1) {
					String availText = newInfo.availNum < 100
							? String.valueOf(newInfo.availNum) : "+";
					Point size = gcImg.stringExtent(availText);

					int x = iXPos + (BLOCK_FILLSIZE / 2) - (size.x / 2);
					int y = iYPos + (BLOCK_FILLSIZE / 2) - (size.y / 2);

					Color availCol = colorsInstance.getReadableColor(bg);
					gcImg.setForeground(availCol);
					gcImg.drawText(availText, x, y, SWT.DRAW_TRANSPARENT);
				}

				Rectangle dirtyBlockBounds = new Rectangle(iXPos - 1, iYPos - 1, BLOCK_SIZE, BLOCK_SIZE);
				if (dirtyBounds == null) {
					// copy because cache needs a const
					dirtyBounds = new Rectangle(iXPos - 1, iYPos - 1, BLOCK_SIZE, BLOCK_SIZE);
				} else {
					dirtyBounds.add(dirtyBlockBounds);
				}

				iCol++;
				numChanged++;
				oldBlockInfo[i] = newInfo;

				if (newInfo.showDown == 0 && newInfo.showUp == 0
						&& (newInfo.haveWidth == 0
								|| newInfo.haveWidth == BLOCK_FILLSIZE)) {
					distinctPieceCache.remove((Integer) i);
					distinctPieceCache.add(i);
					if (distinctPieceCache.size() > 15) {
						distinctPieceCache.remove(0);
					}
				}

				long diff = SystemTime.getMonotonousTime() - startTime;
				if (diff > 200) {
					needsMore = true;
					break;
				}
			}

			if (DEBUG) {
				long diff = SystemTime.getMonotonousTime() - startTime;
				log("end fill. numChanged=" + numChanged + "; numCopyArea=" + numCopyArea + (needsMore ? " (Needs More)" : "") + " in " + diff + "ms" + (numChanged > 0 ? "; redraw " + dirtyBounds : "") + "; cache=" + distinctPieceCache.size());
			}
		} catch (Exception e) {
			Logger.log(new LogEvent(LogIDs.GUI, "drawing piece map", e));
		} finally {
			gcImg.dispose();
		}

		if (numChanged > 0) {
			peerInfoCanvas.redraw(dirtyBounds.x, dirtyBounds.y, dirtyBounds.width, dirtyBounds.height, false);
		}

		if (needsMore) {
			Utils.execSWTThreadLater(0, this::refreshInfoCanvas);
		}
	}

	private Composite getComposite() {
		return peerInfoComposite;
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

		oldBlockInfo = null;
		distinctPieceCache.clear();

		if (font != null && !font.isDisposed()) {
			font.dispose();
			font = null;
		}
	}

	private Image obfuscatedImage(Image image) {
		UIDebugGenerator.obfuscateArea(image, topLabel, "");
		return image;
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

	    case UISWTViewEvent.TYPE_SHOWN:
    		refreshInfoCanvas();
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

	private static class BlockInfo {
		public int haveWidth;
		public boolean peerHas;
		int availNum;
		/** 0 : no; 1 : Yes; 2: small */
		byte showUp;
		byte showDown;
		Rectangle bounds;

		public boolean sameAs(BlockInfo otherBlockInfo) {
			if (otherBlockInfo == null) {
				return false;
			}
			return haveWidth == otherBlockInfo.haveWidth
				&& peerHas == otherBlockInfo.peerHas
				&& availNum == otherBlockInfo.availNum
				&& showDown == otherBlockInfo.showDown
				&& showUp == otherBlockInfo.showUp;
		}

		@Override
		public String toString() {
			return "BlockInfo@" + Integer.toHexString(hashCode()) + "{" +
				"haveWidth=" + haveWidth +
				", availNum=" + availNum +
				", showUp=" + showUp +
				", showDown=" + showDown +
				'}';
		}
	}

	private static void log(String s) {
		System.out.println(SystemTime.getCurrentTime() + " PIV] " + s);
	}
}
