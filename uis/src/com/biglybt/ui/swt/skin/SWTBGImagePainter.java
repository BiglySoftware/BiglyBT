/*
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

package com.biglybt.ui.swt.skin;

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author TuxPaper
 * @created Jun 8, 2006
 *
 */
public class SWTBGImagePainter
	implements Listener
{
	private static boolean DEBUG = false;

	private static boolean TEST_SWT_PAINTING = false; //Constants.isOSX;

	private Rectangle lastResizeRect = Utils.EMPTY_RECT;

	private final Shell shell;

	private String imgSrcID;

	private String imgSrcLeftID;

	private String imgSrcRightID;

	private Image imgSrc;

	private Image imgSrcLeft;

	private Image imgSrcRight;

	private Rectangle imgSrcBounds;

	private Rectangle imgSrcLeftBounds;

	private Rectangle imgSrcRightBounds;

	private Image lastImage = null;

	boolean inEvent = false;

	Rectangle lastBounds = Utils.EMPTY_RECT;

	Point lastShellBGSize = new Point(0, 0);

	private final int tileMode;

	private final Control control;

	private boolean bDirty;

	private int fdWidth = -1;

	private int fdHeight = -1;

	private ImageLoader imageLoader = null;

	private SWTBGImagePainter(Control control, int tileMode) {
		this.control = control;
		this.shell = control.getShell();
		this.tileMode = tileMode;
		control.setData("BGPainter", this);
	}

	public SWTBGImagePainter(Control control, Image bgImage, int tileMode) {
		this(control, null, null, bgImage, tileMode);
	}

	public SWTBGImagePainter(Control control, Image bgImageLeft,
			Image bgImageRight, Image bgImage, int tileMode) {
		this(control, tileMode);
		setImages(bgImageLeft, bgImageRight, bgImage);

		if (bDirty) {
			if (control.isVisible()) {
				buildBackground(control);
			}
		}

		if (!TEST_SWT_PAINTING) {
			control.addListener(SWT.Resize, this);
			control.addListener(SWT.Paint, this);
			control.getShell().addListener(SWT.Show, this);
		}

		control.addListener(SWT.Dispose, this);
	}

	public SWTBGImagePainter(Control control, ImageLoader imageLoader,
			String bgImageLeftId,
			String bgImageRightId, String bgImageId, int tileMode) {
		this(control, tileMode);
		setImage(imageLoader, bgImageLeftId, bgImageRightId, bgImageId);

		if (bDirty) {
			if (control.isVisible()) {
				buildBackground(control);
			}
		}

		if (!TEST_SWT_PAINTING) {
			control.addListener(SWT.Resize, this);
			control.addListener(SWT.Paint, this);
			control.getShell().addListener(SWT.Show, this);
		}

		control.addListener(SWT.Dispose, this);
	}

	public void dispose() {
		if (control == null || control.isDisposed()) {
			return;
		}

		if (!TEST_SWT_PAINTING) {
			control.removeListener(SWT.Resize, this);
			control.removeListener(SWT.Paint, this);
			control.getShell().removeListener(SWT.Show, this);
		}

		control.removeListener(SWT.Dispose, this);
		control.setBackgroundImage(null);
		FormData formData = (FormData) control.getLayoutData();
		formData.width = SWT.DEFAULT;
		formData.height = SWT.DEFAULT;
		control.setData("BGPainter", null);
	}

	/**
	 * @param bgImageLeft
	 * @param bgImageRight
	 * @param bgImage
	 */
	public void setImage(Image bgImageLeft, Image bgImageRight, Image bgImage) {
		setImages(bgImageLeft, bgImageRight, bgImage);
		if (bDirty) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (!control.isVisible()) {
						return;
					}
					buildBackground(control);
				}
			});
		}
	}

	public void setImage(ImageLoader imageLoader, String idLeft, String idRight,
			String id) {
		setImages(imageLoader, idLeft, idRight, id);
		if (bDirty) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (!control.isVisible()) {
						return;
					}
					buildBackground(control);
				}
			});
		}
	}

	private boolean imagesEqual(Image image1, Image image2) {
		if (image1 == image2) {
			return true;
		}

		if (!ImageLoader.isRealImage(image1) && !ImageLoader.isRealImage(image2)) {
			return true;
		}

		return false;
	}

	private void setImages(Image bgImageLeft, Image bgImageRight, Image bgImage) {
		if (imagesEqual(imgSrc, bgImage) && imagesEqual(imgSrcLeft, bgImageLeft)
				&& imagesEqual(imgSrcRight, bgImageRight)) {
			if (DEBUG) {
				System.out.println("same");
			}
			return;
		}

		imgSrcLeftID = null;
		imgSrcRightID = null;
		imgSrcID = null;


		if (DEBUG) {
			System.out.println("SI " + bgImageLeft + ";" + bgImageRight + ";"
					+ bgImage + ";" + control.getData("SkinObject") + "/" + control.isVisible() + control.getSize() + "\\"
					+ Debug.getStackTrace(true, false));
		}

		imgSrc = bgImage;
		if (imgSrc != null) {
			imgSrcBounds = imgSrc.getBounds();
		}
		lastShellBGSize = new Point(0, 0);
		if (ImageLoader.isRealImage(bgImageLeft)) {
			imgSrcLeft = bgImageLeft;
			imgSrcLeftBounds = imgSrcLeft.getBounds();
		} else {
			imgSrcLeft = null;
			imgSrcLeftBounds = Utils.EMPTY_RECT;
		}
		if (ImageLoader.isRealImage(bgImageRight)) {
			imgSrcRight = bgImageRight;
			imgSrcRightBounds = imgSrcRight.getBounds();
		} else {
			imgSrcRight = null;
			imgSrcRightBounds = Utils.EMPTY_RECT;
		}


		if (TEST_SWT_PAINTING) {
			control.removeListener(SWT.Resize, this);
			control.removeListener(SWT.Paint, this);

			if (imgSrcRight == null && imgSrcLeft == null
					&& tileMode == SWTSkinUtils.TILE_NONE) {
				control.setBackgroundImage(imgSrc);
			} else {
				control.addListener(SWT.Resize, this);
				control.addListener(SWT.Paint, this);
				bDirty = true;
				buildBackground(control);
			}
		} else {
			bDirty = true;
		}


		if ((tileMode & SWTSkinUtils.TILE_BOTH) != SWTSkinUtils.TILE_BOTH) {
			int width = SWT.DEFAULT;
			int height = SWT.DEFAULT;

			if (tileMode == SWTSkinUtils.TILE_Y || tileMode == SWTSkinUtils.TILE_NONE) {
				width = imgSrcBounds.width + imgSrcLeftBounds.width
						+ imgSrcRightBounds.width;
			}
			if (tileMode == SWTSkinUtils.TILE_X || tileMode == SWTSkinUtils.TILE_NONE) {
				height = imgSrcBounds.height;
			}
			FormData fd = (FormData) control.getLayoutData();
			if (fd == null) {
				fd = new FormData();
			}

			if (fd.width == fdWidth || fd.height == fdHeight) {

				if (fd.width == fdWidth) {
					fdWidth = fd.width = width;
				}
				if (fd.height == fdHeight) {
					fdHeight = fd.height = height;
				}
				control.setLayoutData(fd);
				if (control.isVisible()) {
					bDirty = true;
					control.getParent().layout(true, true);
				}
			}
		}

	}

	/**
	 * @param bgImageLeftId
	 * @param bgImageRightId
	 * @param bgImageId
	 *
	 * @since 4.0.0.5
	 */
	public void setImages(ImageLoader imageLoader, String bgImageLeftId, String bgImageRightId,
			String bgImageId) {
		this.imageLoader = imageLoader;
		imgSrcLeftID = bgImageLeftId;
		imgSrcRightID = bgImageRightId;
		imgSrcID = bgImageId;

		imgSrcLeftBounds = Utils.EMPTY_RECT;
		imgSrcRightBounds = Utils.EMPTY_RECT;

		if (imgSrcID != null) {
			Image imgSrc = imageLoader.getImage(imgSrcID);
			imgSrcBounds = imgSrc.getBounds();
			imageLoader.releaseImage(imgSrcID);
		}
		Image imgSrcLeft = imageLoader.getImage(imgSrcLeftID);
		if (ImageLoader.isRealImage(imgSrcLeft)) {
			imgSrcLeftBounds = imgSrcLeft.getBounds();
		}
		imageLoader.releaseImage(imgSrcLeftID);

		Image imgSrcRight = imageLoader.getImage(imgSrcRightID);
		if (ImageLoader.isRealImage(imgSrcRight)) {
			imgSrcRightBounds = imgSrcRight.getBounds();
		}
		imageLoader.releaseImage(imgSrcRightID);

		if (TEST_SWT_PAINTING) {
			control.removeListener(SWT.Resize, this);
			control.removeListener(SWT.Paint, this);

			control.addListener(SWT.Resize, this);
			control.addListener(SWT.Paint, this);
			bDirty = true;
			buildBackground(control);
		} else {
			bDirty = true;
		}


		if ((tileMode & SWTSkinUtils.TILE_BOTH) != SWTSkinUtils.TILE_BOTH) {
			int width = SWT.DEFAULT;
			int height = SWT.DEFAULT;

			if (tileMode == SWTSkinUtils.TILE_Y || tileMode == SWTSkinUtils.TILE_NONE) {
				width = imgSrcBounds.width + imgSrcLeftBounds.width
						+ imgSrcRightBounds.width;
			}
			if (tileMode == SWTSkinUtils.TILE_X || tileMode == SWTSkinUtils.TILE_NONE) {
				height = imgSrcBounds.height;
			}
			FormData fd = (FormData) control.getLayoutData();
			if (fd == null) {
				fd = new FormData();
			}

			if (fd.width == fdWidth || fd.height == fdHeight) {

				if (fd.width == fdWidth) {
					fdWidth = fd.width = width;
				}
				if (fd.height == fdHeight) {
					fdHeight = fd.height = height;
				}
				control.setLayoutData(fd);
				if (control.isVisible()) {
					bDirty = true;
					control.getParent().layout(true, true);
				}
			}
		}

	}


	public void buildBackground(Control control) {
		if (inEvent || shell == null || shell.isDisposed() || control == null
				|| control.isDisposed()) {
			return;
		}

		//System.out.println("BB: " + control.getData("ConfigID"));

		inEvent = true;

		ArrayList<String> imagesToRelease = new ArrayList<>(0);

		if (imgSrcLeftID != null && imageLoader.imageExists(imgSrcLeftID)) {
			imagesToRelease.add(imgSrcLeftID);
			imgSrcLeft = imageLoader.getImage(imgSrcLeftID);
			imgSrcLeftBounds = imgSrcLeft.getBounds();
		}
		if (imgSrcRightID != null && imageLoader.imageExists(imgSrcRightID)) {
			imagesToRelease.add(imgSrcRightID);
			imgSrcRight = imageLoader.getImage(imgSrcRightID);
			imgSrcRightBounds = imgSrcRight.getBounds();
		}
		if (imgSrcID != null) {
			Image[] images = imageLoader.getImages(imgSrcID);
			imagesToRelease.add(imgSrcID);
			if (images.length == 1) {
  			imgSrc = images[0];
  			imgSrcBounds = imgSrc.getBounds();
			} else if (images.length == 2) {
				imgSrcLeft = images[0];
				imgSrcLeftBounds = imgSrcLeft.getBounds();
  			imgSrc = images[1];
  			imgSrcBounds = imgSrc.getBounds();
				imgSrcRight = images[1];
				imgSrcRightBounds = imgSrcRight.getBounds();
			} else if (images.length == 3) {
				imgSrcLeft = images[0];
				imgSrcLeftBounds = imgSrcLeft.getBounds();
  			imgSrc = images[1];
  			imgSrcBounds = imgSrc.getBounds();
				imgSrcRight = images[2];
				imgSrcRightBounds = imgSrcRight.getBounds();
			}
		}

		try {

		Point size = control.getSize();
		if (size.x <= 0 || size.y <= 0 || imgSrc == null || imgSrc.isDisposed()) {
			if (DEBUG) {
				System.out.println("- size " + control.getData("ConfigID"));
			}
			Image image = new Image(shell.getDisplay(), 1, 1);
			control.setBackgroundImage(image);

			if (lastImage != null) {
				lastImage.dispose();
			}

			lastImage = image;
			imgSrc = image;
			imgSrcBounds = new Rectangle(0,0,1,1);

			lastBounds = control.getBounds();

			inEvent = false;
			return;
		}

		Composite parent = control.getParent();
		Image imgBG = parent.getBackgroundImage();

		if (imgBG != null && imgBG.isDisposed()) {
			imgBG = null;
		}

		Rectangle imgBGBounds = imgBG == null ? new Rectangle(0, 0, 1, 1)
				: imgBG.getBounds();
		Rectangle compositeArea = control.getBounds();

		boolean bTileY = (tileMode & SWTSkinUtils.TILE_Y) > 0;
		boolean bTileX = (tileMode & SWTSkinUtils.TILE_X) > 0;

		// TODO: Can also exit early if size shrunk but position
		//       same and imgBGBounds same.

		if (!bDirty && imgBG == null && bTileX && bTileY) {
			inEvent = false;
			return;
		}

		if (!bDirty && imgBG == null && compositeArea.width == lastBounds.width
				&& compositeArea.height == lastBounds.height) {
			inEvent = false;
			return;
		}

		if (!bDirty && compositeArea.equals(lastBounds)
				&& imgBGBounds.width == lastShellBGSize.x
				&& imgBGBounds.height == lastShellBGSize.y) {
			inEvent = false;
			return;
		}

		if (TEST_SWT_PAINTING && !bDirty && compositeArea.width == lastBounds.width
				&& compositeArea.height == lastBounds.height) {
			inEvent = false;
			return;
		}

		//control.setRedraw(false);
			if (DEBUG) {
				System.out.println(System.currentTimeMillis() + "@"
						+ Integer.toHexString(hashCode()) + "BGPain: "
						+ control.getData("SkinObject") + "/" + "; image" + size + ";"
						+ tileMode + ";lB=" + lastBounds + "/" + compositeArea + ";"
						+ "lBG=" + lastShellBGSize + "/" + imgBGBounds.width + "x"
						+ imgBGBounds.height + ";" + bDirty);
				//+ "\n" + Debug.getCompressedStackTrace());
			}

			lastBounds = compositeArea;
			lastShellBGSize = new Point(imgBGBounds.width, imgBGBounds.height);
			//System.out.println(size);

			//size.x = 10;
			//size.y = 10;
			Image newImage = new Image(shell.getDisplay(), size.x, size.y);

			//			GC gc = new GC(newImage);
			//			gc.setBackground(shell.getDisplay().getSystemColor(
			//					(int) (Math.random() * 16)));
			//			gc.fillRectangle(0, 0, size.x, size.y);
			//			gc.dispose();

			Point ofs;

			if (control.getParent() == shell) {
				ofs = control.getLocation();
				Rectangle clientArea = shell.getClientArea();
				ofs.x += clientArea.x;
				ofs.y += clientArea.y;
			} else {
				Point controlPos = new Point(0, 0);
				if (control instanceof Composite) {
					Composite composite = (Composite) control;
					Rectangle compArea = composite.getClientArea();
					//System.out.println("comparea=" + compArea);
					controlPos.x = compArea.x;
					controlPos.y = compArea.y;
				}

				Point locControl = control.toDisplay(controlPos.x, controlPos.y);
				Rectangle clientArea = shell.getClientArea();
				Point locShell = control.getParent().toDisplay(clientArea.x,
						clientArea.y);
				//System.out.println("locC="+ locControl + ";locS=" + locShell);

				ofs = new Point(locControl.x - locShell.x, locControl.y - locShell.y);
			}

			ofs.x = (ofs.x % imgBGBounds.width);
			ofs.y = (ofs.y % imgBGBounds.height);

			GC gc = new GC(newImage);
			try {

				control.setBackgroundImage(null);
				gc.setBackground(control.getBackground());
				gc.fillRectangle(0, 0, size.x, size.y);

				if (imgBG != null) {
					for (int y = 0; y < size.y; y += imgBGBounds.height) {
						for (int x = 0; x < size.x; x += imgBGBounds.width) {
							gc.drawImage(imgBG, x - ofs.x, y - ofs.y);
						}
					}
				}

				int maxY = bTileY ? size.y : imgSrcBounds.height;
				int maxX = bTileX ? size.x : imgSrcBounds.width;
				int x0 = 0;

				if ((tileMode & SWTSkinUtils.TILE_CENTER_X) > 0) {
					x0 = (size.x - imgSrcBounds.width) / 2;
					maxX += x0;
				}
				int y0 = 0;
				if ((tileMode & SWTSkinUtils.TILE_CENTER_Y) > 0) {
					y0 = (size.y - imgSrcBounds.height) / 2;
					maxY += y0;
				}

				if (imgSrcRight != null) {
					int width = imgSrcRightBounds.width;

					maxX -= width;
				}

				if (imgSrcLeft != null) {
					// TODO: Tile down
					gc.drawImage(imgSrcLeft, 0, 0);

					x0 += imgSrcLeftBounds.width;
				}

				for (int y = y0; y < maxY; y += imgSrcBounds.height) {
					for (int x = x0; x < maxX; x += imgSrcBounds.width) {
						if (x + imgSrcBounds.width >= maxX) {
							int width = maxX - x;
							gc.drawImage(imgSrc, 0, 0, width, imgSrcBounds.height, x, y,
									width, imgSrcBounds.height);
						} else {
							gc.drawImage(imgSrc, x, y);
						}
					}
				}

				if (imgSrcRight != null) {
					// TODO: Tile down
					gc.drawImage(imgSrcRight, maxX, 0);
				}
			} finally {
				gc.dispose();
			}

			control.setBackgroundImage(newImage);

			if (lastImage != null) {
				lastImage.dispose();
			}

			lastImage = newImage;

			bDirty = false;

		} finally {
			for (String key : imagesToRelease) {
				imageLoader.releaseImage(key);
			}
			if (imgSrcID != null && imgSrc != null) {
				imgSrc = null;
			}
			if (imgSrcLeftID != null && imgSrcLeft != null) {
				imgSrcLeft = null;
			}
			if (imgSrcRightID != null && imgSrcRight != null) {
				imgSrcRight = null;
			}

			//control.setRedraw(true);
			//control.update();
			//control.getShell().update();
			//			if (control instanceof Composite) {
			//				Control[] children = ((Composite)control).getChildren();
			//				((Composite)control).layout(true, true);
			//				for (int i = 0; i < children.length; i++) {
			//					Control control2 = children[i];
			//					control2.redraw();
			//					control2.update();
			//				}
			//			}

			inEvent = false;
		}
	}

	public static void main(String[] args) {
		Display display = Display.getDefault();
		Shell shell = new Shell(display, SWT.DIALOG_TRIM);
		shell.setLayout(new FillLayout());

		Composite c = new Composite(shell, SWT.BORDER);
		c.setLayout(new FillLayout());
		c.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				e.gc.drawLine(0, 0, 100, 50);
			}
		});

		Label lbl = new Label(c, SWT.NONE);
		lbl.setText("text");

		shell.open();

		while (!shell.isDisposed()) {
			if (display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	@Override
	public void handleEvent(final Event event) {
		if (event.type == SWT.Resize) {
			Control control = (Control) event.widget;

			Rectangle resizeRect = control.getBounds();
			if (resizeRect.equals(lastResizeRect)) {
				return;
			}

			lastResizeRect = resizeRect;

			if (DEBUG) {
				System.out.println("BGPaint:HE: " + control.getData("ConfigID") + ";"
						+ event + ";" + control.isVisible());
			}
			buildBackground(control);
		} else if (event.type == SWT.Paint) {
			Control control = (Control) event.widget;
			if (DEBUG) {
				System.out.println("BGPaint:P: " + control.getData("ConfigID") + ";"
						+ event + ";" + control.isVisible());
			}

			if (!TEST_SWT_PAINTING) {
				buildBackground(control);
			}
		} else if (event.type == SWT.Show) {
			if (DEBUG) {
				System.out.println("BGPaint:S: " + control.getData("ConfigID") + ";"
						+ event + ";" + control.isVisible());
			}

			if (!TEST_SWT_PAINTING) {
				buildBackground(control);
			}
		} else if (event.type == SWT.Dispose) {
			if (DEBUG) {
				System.out.println("dispose.. " + lastImage + ";"
						+ control.getData("SkinObject"));
			}
			if (lastImage != null && !lastImage.isDisposed()) {
				lastImage.dispose();
				lastImage = null;
			}
		}
	}
}
