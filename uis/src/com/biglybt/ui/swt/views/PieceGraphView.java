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

package com.biglybt.ui.swt.views;

import java.util.*;

import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

/**
 * @author TuxPaper
 * @created Apr 9, 2007
 *
 */
public class PieceGraphView
	implements UISWTViewCoreEventListener
{
	// TODO: Buffer

	private boolean onePiecePerBlock = false;

	private int BLOCK_FILLSIZE = 21;

	private final static int BLOCK_SPACING = 3;

	private int BLOCK_SIZE = BLOCK_FILLSIZE + BLOCK_SPACING;

	private final static int BLOCKCOLOR_HAVEALL = 0;

	private final static int BLOCKCOLOR_NOHAVE = 1;

	private final static int BLOCKCOLOR_UPLOADING = 2;

	private final static int BLOCKCOLOR_DOWNLOADING = 3;

	private final static int BLOCKCOLOR_NOAVAIL = 4;

	private final static int BLOCKCOLOR_HAVESOME = 5;

	private Color[] blockColors;

	private Canvas canvas;

	private Image img;

	private Image imgHaveAll;

	private Image imgNoHave;

	private DownloadManager dlm;

	private Comparator compFindPEPiece;

	private final SWTSkinProperties properties;

	private double[] squareCache;

	public PieceGraphView() {
		this.properties = SWTSkinFactory.getInstance().getSkinProperties();
	}

	// @see com.biglybt.ui.swt.views.AbstractIView#initialize(org.eclipse.swt.widgets.Composite)
	private void initialize(Composite parent) {

		blockColors = new Color[] {
			properties.getColor("color.pieceview.alldone"),
			properties.getColor("color.pieceview.notdone"),
			properties.getColor("color.pieceview.uploading"),
			properties.getColor("color.pieceview.downloading"),
			properties.getColor("color.pieceview.noavail"),
			properties.getColor("color.pieceview.havesome"),
		};

		compFindPEPiece = new Comparator() {
			@Override
			public int compare(Object arg0, Object arg1) {
				int arg0no = (arg0 instanceof PEPiece)
						? ((PEPiece) arg0).getPieceNumber() : ((Long) arg0).intValue();
				int arg1no = (arg1 instanceof PEPiece)
						? ((PEPiece) arg1).getPieceNumber() : ((Long) arg1).intValue();
				return arg0no - arg1no;
			}
		};

		canvas = new Canvas(parent, SWT.NO_BACKGROUND);
		canvas.setLayout(new FillLayout());

		canvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				if (img != null && !img.isDisposed()) {
					Rectangle bounds = img.getBounds();
					if (bounds.width >= ( e.width + e.x ) && bounds.height >= ( e.height + e.y )) {
						e.gc.drawImage(img, e.x, e.y, e.width, e.height, e.x, e.y, e.width,
								e.height);
					}
				} else {
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);
				}
			}
		});

		canvas.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event event) {
				calcBlockSize();
			}
		});
		
	    canvas.addListener( SWT.Dispose, (ev)->{
	    	for ( Image i: new Image[]{ img, imgHaveAll, imgNoHave }){
		    	if ( i != null && !i.isDisposed()){
		    		i.dispose();
		    	}
	    	}
	    	
	    	img = imgHaveAll = imgNoHave = null;
	    });
	}

	// @see com.biglybt.ui.swt.views.AbstractIView#dataSourceChanged(java.lang.Object)
	private void dataSourceChanged(Object newDataSource) {
		if (newDataSource instanceof DownloadManager) {
			dlm = (DownloadManager) newDataSource;
		} else {
			dlm = null;
		}
		calcBlockSize();
	}

	// @see com.biglybt.ui.swt.views.AbstractIView#refresh()
	private void refresh() {
		buildImage();
	}

	private void calcBlockSize() {
		if (!onePiecePerBlock) {
			buildImage();
			return;
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (canvas == null || canvas.isDisposed()) {
					return;
				}
				TOTorrent torrent = dlm == null ? null : dlm.getTorrent();
				if (torrent == null) {
					BLOCK_SIZE = 21 + BLOCK_SPACING;
				} else {
					long numPieces = torrent.getNumberOfPieces();
					Rectangle bounds = canvas.getClientArea();
					BLOCK_SIZE = (int) Math.sqrt((double) (bounds.width * bounds.height)
							/ numPieces);
					if (BLOCK_SIZE <= 0) {
						BLOCK_SIZE = 1;
					}
					// since calc above doesn't account for not splitting squares across
					// rows, make sure we can fit.  If we can't, we have to shrink
					int numCanFit = (bounds.width / BLOCK_SIZE)
							* (bounds.height / BLOCK_SIZE);
					if (numCanFit < numPieces) {
						BLOCK_SIZE--;
					}
					//System.out.println((float)(bounds.width * bounds.height) / numPieces);
					//System.out.println(BLOCK_SIZE + ";" + (bounds.width / BLOCK_SIZE));

					if (BLOCK_SIZE < 2) {
						BLOCK_SIZE = 2;
					}
				}

				BLOCK_FILLSIZE = BLOCK_SIZE - BLOCK_SPACING;

				Utils.disposeSWTObjects(new Object[] {
					imgHaveAll,
					imgNoHave
				});

				buildImage();
			}
		});
	}

	private void buildImage() {
		if (canvas == null || canvas.isDisposed()) {
			return;
		}

		//canvas.setBackground(ColorCache.getColor(canvas.getDisplay(), "#1b1b1b"));

		Rectangle bounds = canvas.getClientArea();
		if (bounds.isEmpty()) {
			return;
		}

		if (dlm == null) {
			canvas.redraw();
			return;
		}

		PEPeerManager pm = dlm.getPeerManager();

		DiskManager dm = dlm.getDiskManager();

		if (pm == null || dm == null) {
			canvas.redraw();
			return;
		}

		final DiskManagerPiece[] dm_pieces = dm.getPieces();

		if (dm_pieces == null || dm_pieces.length == 0) {
			canvas.redraw();
			return;
		}

		int numPieces = dm_pieces.length;

		if (imgHaveAll == null || imgHaveAll.isDisposed()) {
			imgHaveAll = new Image(canvas.getDisplay(), BLOCK_SIZE, BLOCK_SIZE);
			GC gc = new GC(imgHaveAll);
			try {
				try {
					gc.setAntialias(SWT.ON);
				} catch (Exception e) {
					// ignore
				}
				gc.setBackground(canvas.getBackground());
				gc.fillRectangle(imgHaveAll.getBounds());

				gc.setBackground(blockColors[BLOCKCOLOR_HAVEALL]);
				gc.fillRoundRectangle(1, 1, BLOCK_FILLSIZE, BLOCK_FILLSIZE,
						BLOCK_FILLSIZE, BLOCK_FILLSIZE);
			} finally {
				gc.dispose();
			}
		}

		if (imgNoHave == null || imgNoHave.isDisposed()) {
			imgNoHave = new Image(canvas.getDisplay(), BLOCK_SIZE, BLOCK_SIZE);
			GC gc = new GC(imgNoHave);
			try {
				try {
					gc.setAntialias(SWT.ON);
				} catch (Exception e) {
					// ignore
				}
				gc.setBackground(canvas.getBackground());
				gc.fillRectangle(imgNoHave.getBounds());

				gc.setBackground(blockColors[BLOCKCOLOR_NOHAVE]);
				gc.fillRoundRectangle(1, 1, BLOCK_FILLSIZE, BLOCK_FILLSIZE,
						BLOCK_FILLSIZE, BLOCK_FILLSIZE);
			} finally {
				gc.dispose();
			}
		}

		boolean clearImage = img == null || img.isDisposed()
				|| img.getBounds().width != bounds.width
				|| img.getBounds().height != bounds.height;
		if (clearImage) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
			//System.out.println("clear " + img);
			img = new Image(canvas.getDisplay(), bounds.width, bounds.height);
			squareCache = null;
		}

		PEPiece[] currentDLPieces = dlm.getCurrentPieces();
		Arrays.sort(currentDLPieces, compFindPEPiece);

		// find upload pieces
		ArrayList currentULPieces = new ArrayList();
		ArrayList futureULPieces = new ArrayList();
		PEPeer[] peers = (PEPeer[]) pm.getPeers().toArray(new PEPeer[0]);
		for (int i = 0; i < peers.length; i++) {
			PEPeer peer = peers[i];
			int[] peerRequestedPieces = peer.getIncomingRequestedPieceNumbers();
			if (peerRequestedPieces != null && peerRequestedPieces.length > 0) {
				currentULPieces.add(new Long(peerRequestedPieces[0]));
				for (int j = 1; j < peerRequestedPieces.length; j++) {
					futureULPieces.add(new Long(peerRequestedPieces[j]));
				}
			}

			// we'll have duplicates
			Collections.sort(currentULPieces);
			Collections.sort(futureULPieces);
		}

		int iNumCols = bounds.width / BLOCK_SIZE;
		int iNumRows = bounds.height / BLOCK_SIZE;
		int numSquares = onePiecePerBlock ? numPieces : iNumCols * iNumRows;
		double numPiecesPerSquare = numPieces / (double) numSquares;
		//System.out.println("numPiecesPerSquare=" + numPiecesPerSquare);

		if (squareCache == null || squareCache.length != numSquares) {
			squareCache = new double[numSquares];
			Arrays.fill(squareCache, -1);
		}

		int[] availability = pm.getAvailability();

		int numRedraws = 0;

		GC gc = new GC(img);
		try {
			int iRow = 0;
			if (clearImage) {
				gc.setBackground(canvas.getBackground());
				gc.fillRectangle(bounds);
			}

			try {
				gc.setAdvanced(true);
				gc.setAntialias(SWT.ON);
				gc.setInterpolation(SWT.HIGH);
			} catch (Exception e) {
				// ignore
			}
			int iCol = 0;
			for (int squareNo = 0; squareNo < numSquares; squareNo++) {
				if (iCol >= iNumCols) {
					iCol = 0;
					iRow++;
				}

				int startNo = (int) (squareNo * numPiecesPerSquare);
				int count = (int) ((squareNo + 1) * numPiecesPerSquare) - startNo;
				if (count == 0) {
					count = 1;
				}
				//if (count > 1) System.out.println("!!! " + startNo);

				//System.out.println(startNo + ";" + count);

				double pctDone = getPercentDone(startNo, count, dm_pieces);
				//System.out.print(pctDone + ";");

				int colorIndex;
				int iXPos = iCol * BLOCK_SIZE;
				int iYPos = iRow * BLOCK_SIZE;

				if (pctDone == 1) {
					if (squareCache[squareNo] != pctDone) {
						squareCache[squareNo] = pctDone;
						gc.drawImage(imgHaveAll, iXPos, iYPos);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
					}
				} else if (pctDone == 0) {
					if (squareCache[squareNo] != pctDone) {
						squareCache[squareNo] = pctDone;
						gc.drawImage(imgNoHave, iXPos, iYPos);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
					}
				} else {
					// !done
					boolean isDownloading = false;
					for (int i = startNo; i < startNo + count; i++) {
						if (Arrays.binarySearch(currentDLPieces, new Long(i),
								compFindPEPiece) >= 0) {
							isDownloading = true;
							break;
						}
					}

					double val = pctDone + (isDownloading ? 0 : 1);
					if (squareCache[squareNo] != val) {
						squareCache[squareNo] = val;
						gc.drawImage(imgNoHave, iXPos, iYPos);

						int size = (int) (BLOCK_FILLSIZE * pctDone);
						if (size == 0) {
							size = 1;
						}
						int q = (int) ((BLOCK_FILLSIZE - size) / 2.0 + 0.5) + 1;

						colorIndex = isDownloading ? BLOCKCOLOR_DOWNLOADING
								: BLOCKCOLOR_HAVESOME;
						gc.setBackground(blockColors[colorIndex]);
						gc.fillOval(iXPos + q, iYPos + q, size, size);
						//gc.fillRoundRectangle(iXPos + q, iYPos + q, size, size, size, size);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
					}
				}

				for (int i = startNo; i < startNo + count; i++) {
					if (Collections.binarySearch(currentULPieces, new Long(i)) >= 0) {
						colorIndex = BLOCKCOLOR_UPLOADING;
						int size = BLOCK_FILLSIZE + 1;

						gc.setForeground(blockColors[colorIndex]);
						gc.drawRoundRectangle(iXPos, iYPos, size, size, size, size);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
						squareCache[squareNo] = -1;
						break;
					} else if (Collections.binarySearch(futureULPieces, new Long(i)) >= 0) {
						colorIndex = BLOCKCOLOR_UPLOADING;
						int size = BLOCK_FILLSIZE + 1;

						gc.setForeground(blockColors[colorIndex]);
						gc.setLineStyle(SWT.LINE_DOT);
						gc.drawRoundRectangle(iXPos, iYPos, size, size, size, size);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
						gc.setLineStyle(SWT.LINE_SOLID);
						squareCache[squareNo] = -1;
						break;
					}
				}

				if (availability != null) {
					boolean hasNoAvail = false;
					for (int i = startNo; i < startNo + count; i++) {
						if (availability[i] == 0) {
							hasNoAvail = true;
							squareCache[squareNo] = -1;
							break;
						}
					}

					if (hasNoAvail) {
						gc.setForeground(blockColors[BLOCKCOLOR_NOAVAIL]);
						gc.drawRectangle(iXPos, iYPos, BLOCK_FILLSIZE + 1,
								BLOCK_FILLSIZE + 1);
						if (!clearImage) {
							numRedraws++;
							canvas.redraw(iXPos, iYPos, BLOCK_SIZE, BLOCK_SIZE, false);
						}
					}
				}

				iCol++;
			}
			//System.out.println("redraws " + numRedraws);
		} catch (Exception e) {
			Debug.out(e);
		} finally {
			gc.dispose();
		}

		//canvas.redraw();
	}

	/**
	 * @param startNo
	 * @param count
	 * @param dm_pieces
	 * @return
	 *
	 * @since 3.0.1.1
	 */
	private double getPercentDone(int startNo, int count, DiskManagerPiece[] dm_pieces) {

		int totalComplete = 0;
		int totalBlocks = 0;
		for (int i = startNo; i < startNo + count; i++) {
			DiskManagerPiece piece = dm_pieces[i];
			int numBlocks = piece.getNbBlocks();
			totalBlocks += numBlocks;

			if (piece.isDone()) {
				totalComplete += numBlocks;
			} else {
				// !done
				totalComplete += piece.getNbWritten();
			}
		}
		return (double) totalComplete / totalBlocks;
	}

	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(canvas);
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
      	dataSourceChanged(event.getData());
        break;


      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }

	/**
	 *
	 *
	 * @since 3.1.0.1
	 */
	private void delete() {
		// TODO Auto-generated method stub

	}
}
