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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;

import com.biglybt.core.peermanager.piecepicker.PiecePicker;

/**
 * @author Aaron Grunthal
 * @create 02.10.2007
 */
public abstract class PieceDistributionView
	implements UISWTViewCoreEventListener
{
	private Composite		comp;
	private Canvas			pieceDistCanvas;
	protected PEPeerManager	pem;
	// list of pieces that the data source has, won't be used if isMe is true
	protected boolean[]		hasPieces;
	// field must be set to true to display data that we know about ourselves
	// instead of remote peers
	protected boolean		isMe		= false;
	private boolean			initialized	= false;
	private Image imgToPaint = null;
	protected UISWTView swtView;

	/**
	 * implementors of this method must provide an appropriate peer manager and
	 * possibly provide the hasPieces array for pieces the data source has
	 */
	abstract public void dataSourceChanged(Object newDataSource);

	private String getFullTitle() {
		return MessageText.getString("PiecesView.DistributionView.title");
	}

	private void initialize(Composite parent) {
		comp = new Composite(parent,SWT.NONE);
		createPieceDistPanel();
		initialized = true;
		refresh();
	}

	private void createPieceDistPanel() {
		comp.setLayout(new FillLayout());
		//pieceDistComposite = new Composite(parent, SWT.NONE);
		pieceDistCanvas = new Canvas(comp,SWT.NO_BACKGROUND);
		pieceDistCanvas.addListener(SWT.Paint, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if ( pem==null || pem.isDestroyed()){
					event.gc.setBackground(Utils.isDarkAppearanceNative()?pieceDistCanvas.getBackground():null);
					event.gc.fillRectangle(event.x, event.y, event.width, event.height);
				}else{
					if (imgToPaint != null && !imgToPaint.isDisposed()) {
						event.gc.drawImage(imgToPaint, 0, 0);
					}
				}
			}
		});
	}

	private final void updateDistribution()
	{
		if (!initialized || pem == null || comp == null
				|| pem.getPiecePicker() == null || pem.getDiskManager() == null
				|| !comp.isVisible())
			return;
		Rectangle rect = pieceDistCanvas.getBounds();
		if (rect.height <= 0 || rect.width <= 0)
			return;

		PiecePicker picker = pem.getPiecePicker();

		final int seeds = pem.getNbSeeds() + (pem.isSeeding() ? 1 : 0);
		final int connected = pem.getNbPeers() + seeds + (pem.isSeeding() ? 0 : 1);
		final int upperBound = 1 + (1 << (int) Math.ceil(Math.log(connected + 0.0) / Math.log(2.0)));
		// System.out.println("conn:"+connected+" bound:"+upperBound);
		final int minAvail = (int) picker.getMinAvailability();
		//final int maxAvail = picker.getMaxAvailability();
		final int nbPieces = picker.getNumberOfPieces();
		final int[] availabilties = picker.getAvailability();
		final DiskManagerPiece[] dmPieces = pem.getDiskManager().getPieces();
		final PEPiece[] pePieces = pem.getPieces();
		final int[] globalPiecesPerAvailability = new int[upperBound];
		final int[] datasourcePiecesPerAvailability = new int[upperBound];

		// me-only stuff
		final boolean[] downloading = new boolean[upperBound];

		int avlPeak = 0;
		//int avlPeakIdx = -1;

		for (int i = 0; i < nbPieces; i++)
		{
			if (availabilties[i] >= upperBound)
				return; // availability and peer lists are OOS, just wait for the next round
			final int newPeak;
			if (avlPeak < (newPeak = ++globalPiecesPerAvailability[availabilties[i]]))
			{
				avlPeak = newPeak;
				//avlPeakIdx = availabilties[i];
			}
			if ((isMe && dmPieces[i].isDone()) || (!isMe && hasPieces != null && hasPieces[i]))
				++datasourcePiecesPerAvailability[availabilties[i]];
			if (isMe && pePieces[i] != null)
				downloading[availabilties[i]] = true;
		}

		Image img = new Image(comp.getDisplay(),pieceDistCanvas.getBounds());

		GC gc = new GC(img);

		if ( Utils.isDarkAppearanceNative()){
			gc.setBackground(pieceDistCanvas.getBackground());
			gc.fillRectangle(img.getBounds());
		}
		
		try
		{
			int stepWidthX = rect.width / upperBound;
			int barGap = 1;
			int barWidth = stepWidthX - barGap - 1;
			int barFillingWidth = barWidth - 1;
			double stepWidthY = 1.0 * (rect.height - 1) / avlPeak;
			int offsetY = rect.height;

			Color rarestColor = Utils.isDarkAppearanceNative()?Colors.yellow:Colors.blue;
			
			gc.setForeground(Colors.green);
			for (int i = 0; i <= connected; i++)
			{
				Color curColor;
				if (i == 0)
					curColor = Colors.colorError;
				else if (i <= seeds)
					curColor = Colors.green;
				else
					curColor = Colors.blues[Colors.BLUES_DARKEST];


				gc.setBackground(curColor);
				gc.setForeground(curColor);

				if(globalPiecesPerAvailability[i] == 0)
				{
					gc.setLineWidth(2);
					gc.drawLine(stepWidthX * i, offsetY - 1, stepWidthX * (i + 1) - barGap, offsetY - 1);
				} else
				{
					gc.setLineWidth(1);
					if (downloading[i])
						gc.setLineStyle(SWT.LINE_DASH);
					gc.fillRectangle(stepWidthX * i + 1, offsetY - 1, barFillingWidth, (int) (Math.ceil(stepWidthY * datasourcePiecesPerAvailability[i] - 1) * -1));
					gc.drawRectangle(stepWidthX * i, offsetY, barWidth, (int) (Math.ceil(stepWidthY * globalPiecesPerAvailability[i]) + 1) * -1);
				}

				if(i==minAvail)
				{
					gc.setForeground(rarestColor);
					gc.drawRectangle(stepWidthX*i+1, offsetY-1, barWidth-2, (int)(Math.ceil(stepWidthY*globalPiecesPerAvailability[i]-1))*-1);
				}


				gc.setLineStyle(SWT.LINE_SOLID);
			}
			gc.setLineWidth(1);


			String[] boxContent = new String[] {
				MessageText.getString("PiecesView.DistributionView.NoAvl"),
				MessageText.getString("PiecesView.DistributionView.SeedAvl"),
				MessageText.getString("PiecesView.DistributionView.PeerAvl"),
				MessageText.getString("PiecesView.DistributionView.RarestAvl",new String[] {globalPiecesPerAvailability[minAvail]+"",minAvail+""}),
				MessageText.getString("PiecesView.DistributionView."+ (isMe? "weHave" : "theyHave")),
				MessageText.getString("PiecesView.DistributionView.weDownload")
				};

			int charHeight = gc.getFontMetrics().getHeight();
			int maxBoxOffsetY = charHeight + 2;
			int maxBoxWidth = 0;
			int maxBoxOffsetX = 0;
			for (int i = 0; i < boxContent.length; i++){
				maxBoxWidth = Math.max( maxBoxWidth, gc.stringExtent( boxContent[i] ).x );
			}
			
			maxBoxOffsetX = maxBoxWidth + 20;
			maxBoxWidth = maxBoxWidth + 10;


			int boxNum = 1;
			gc.setForeground(Colors.colorError);
			gc.setBackground(Colors.background);
			gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);

			boxNum++;
			gc.setForeground(Colors.green);
			gc.setBackground(Colors.background);
			gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);

			boxNum++;
			gc.setForeground(Colors.blues[Colors.BLUES_DARKEST]);
			gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);

			boxNum++;
			gc.setForeground(rarestColor);
			gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);

			boxNum++;
			gc.setForeground(Colors.black);
			gc.setBackground(Colors.black);
			gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
			gc.fillRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth/2,charHeight);
			gc.setForeground(Colors.grey);
			gc.setBackground(Colors.background);
			gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);

			if(isMe)
			{
				boxNum++;
				gc.setForeground(Utils.isDarkAppearanceNative()?Colors.grey:Colors.black);
				gc.setLineStyle(SWT.LINE_DASH);
				gc.drawRectangle(rect.width+(maxBoxOffsetX)*-1,maxBoxOffsetY*boxNum,maxBoxWidth,charHeight);
				gc.drawString(boxContent[boxNum-1],rect.width+(maxBoxOffsetX-5)*-1,maxBoxOffsetY*boxNum,true);
			}

			gc.setLineStyle(SWT.LINE_SOLID);

		} finally
		{
			gc.dispose();
		}

		if (imgToPaint != null) {
			imgToPaint.dispose();
		}
		imgToPaint = img;
		pieceDistCanvas.redraw();
	}

	public void refresh() {
		if (!initialized || pem == null)
			return;
		updateDistribution();
	}

	private Composite getComposite() {
		return comp;
	}

	private void delete() {
		if (!initialized)
			return;
		initialized = false;
		Utils.disposeSWTObjects(new Object[] { pieceDistCanvas, comp, imgToPaint });
	}

	private void viewActivated() {
		updateDistribution();
	}

	private void viewDeactivated() {
		Utils.disposeSWTObjects(new Object[] { imgToPaint });
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
      	viewActivated();
      	break;

      case UISWTViewEvent.TYPE_FOCUSLOST:
      	viewDeactivated();
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;
    }

    return true;
  }
}
