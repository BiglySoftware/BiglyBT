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

package com.biglybt.ui.swt.skin;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Aug 28, 2007
 *
 */
public class SWTSkinObjectSlider
	extends SWTSkinObjectBasic
	implements PaintListener, MouseListener, MouseMoveListener
{
	private Image imageFG;

	private Object imageFGLeft;

	private Object imageFGRight;

	private Canvas canvas;

	private Image imageThumbRight;

	private Image imageThumb;

	private Image imageThumbLeft;

	private Image imageBGRight;

	private Image imageBG;

	private Image imageBGLeft;

	private double percent;

	private Rectangle imageFGbounds;

	private Rectangle imageBGbounds;

	private Rectangle imageThumbBounds;

	private Point maxSize = new Point(0, 0);

	private boolean mouseDown;

	private boolean mouseMoveAdjusts = true;

	private ArrayList listeners = new ArrayList();

	private double draggingPercent;

	private boolean disabled;

	private List<String> imagesToRelease = new ArrayList<>();

	public SWTSkinObjectSlider(SWTSkin skin, SWTSkinProperties skinProperties,
			String sID, String sConfigID, String[] typeParams, SWTSkinObject parent) {
		super(skin, skinProperties, sID, sConfigID, "slider", parent);

		String sSuffix = ".complete";
		final ImageLoader imageLoader = skin.getImageLoader(properties);

		String imagePrefix = sConfigID + sSuffix;
		Image[] images = imageLoader.getImages(imagePrefix);
		imagesToRelease.add(imagePrefix);
		if (images.length == 1 && ImageLoader.isRealImage(images[0])) {
			imageFG = images[0];
			imageFGLeft = imageLoader.getImage(imagePrefix + "-left");
			imageFGRight = imageLoader.getImage(imagePrefix + "-right");
			imagesToRelease.add(imagePrefix + "-left");
			imagesToRelease.add(imagePrefix + "-right");
		} else if (images.length == 3 && ImageLoader.isRealImage(images[2])) {
			imageFGLeft = images[0];
			imageFG = images[1];
			imageFGRight = images[2];
		}

		if (imageFG != null) {
			imageFGbounds = imageFG.getBounds();
		}

		sSuffix = ".incomplete";
		imagePrefix = sConfigID + sSuffix;
		images = imageLoader.getImages(imagePrefix);
		imagesToRelease.add(imagePrefix);
		if (images.length == 1 && ImageLoader.isRealImage(images[0])) {
			imageBG = images[0];
			imageBGLeft = imageLoader.getImage(imagePrefix + "-left");
			imageBGRight = imageLoader.getImage(imagePrefix + "-right");
			imagesToRelease.add(imagePrefix + "-left");
			imagesToRelease.add(imagePrefix + "-right");
		} else if (images.length == 3 && ImageLoader.isRealImage(images[2])) {
			imageBGLeft = images[0];
			imageBG = images[1];
			imageBGRight = images[2];
		}

		if (imageBG != null) {
			imageBGbounds = imageBG.getBounds();
		}

		sSuffix = ".thumb";
		imagePrefix = sConfigID + sSuffix;
		images = imageLoader.getImages(imagePrefix);
		imagesToRelease.add(imagePrefix);
		if (images.length == 1) {
			imageThumb = images[0];
			imageThumbLeft = imageLoader.getImage(imagePrefix + "-left");
			imageThumbRight = imageLoader.getImage(imagePrefix + "-right");
			imagesToRelease.add(imagePrefix + "-left");
			imagesToRelease.add(imagePrefix + "-right");
		} else if (images.length == 3 && ImageLoader.isRealImage(images[2])) {
			imageThumbLeft = images[0];
			imageThumb = images[1];
			imageThumbRight = images[2];
		}

		if (imageThumb != null) {
			imageThumbBounds = imageThumb.getBounds();
		}

		maxSize = buildMaxSize(new Rectangle[] {
			imageThumbBounds,
			imageBGbounds,
			imageFGbounds
		});

		if (skinProperties.getStringValue(sConfigID + ".width", "").equalsIgnoreCase(
				"auto")) {
			maxSize.x = 0;
		}

		int style = SWT.NONE;

		if (skinProperties.getIntValue(sConfigID + ".border", 0) == 1) {
			style |= SWT.BORDER;
		}

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		canvas = new Canvas(createOn, style);
		Utils.setLayoutData(canvas, new FormData(maxSize.x == 0 ? SWT.DEFAULT
				: maxSize.x, maxSize.y));
		canvas.setSize(SWT.DEFAULT, maxSize.y);
		setControl(canvas);

		canvas.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				try {
  				for (String key : imagesToRelease) {
  					imageLoader.releaseImage(key);
  				}
				} catch (Exception ex) {
					Debug.out(ex);
				}
			}
		});

		setAlwaysHookPaintListener(true);
		canvas.addMouseListener(this);
		canvas.addMouseMoveListener(this);
	}

	private Point buildMaxSize(Rectangle[] bounds) {
		Point maxSize = new Point(0, 0);
		for (int i = 0; i < bounds.length; i++) {
			if (bounds[i] == null) {
				continue;
			}

			if (bounds[i].width > maxSize.x) {
				maxSize.x = bounds[i].width;
			}
			if (bounds[i].height > maxSize.y) {
				maxSize.y = bounds[i].height;
			}
		}
		return maxSize;
	}

	// @see SWTSkinObjectBasic#paintControl(org.eclipse.swt.graphics.GC)
	@Override
	public void paintControl(GC gc) {
		super.paintControl(gc);

		int fullWidth = maxSize.x == 0 || imageFGbounds == null
				? canvas.getClientArea().width : imageFGbounds.width;

		if (percent > 0 && imageFG != null) {
			int xDrawTo = (int) (fullWidth * percent);
			int xDrawToSrc = xDrawTo > imageFGbounds.width ? imageFGbounds.width
					: xDrawTo;
			int y = (maxSize.y - imageFGbounds.height) / 2;
			gc.drawImage(imageFG, 0, 0, xDrawToSrc, imageFGbounds.height, 0, y,
					xDrawTo, imageFGbounds.height);
		}
		if (percent < 100 && imageBG != null && imageFGbounds != null ) {
			int xDrawFrom = (int) (imageBGbounds.width * percent);
			int xDrawWidth = imageBGbounds.width - xDrawFrom;
			gc.drawImage(imageBG, xDrawFrom, 0, xDrawWidth, imageFGbounds.height,
					xDrawFrom, 0, xDrawWidth, imageFGbounds.height);
		}

		int drawWidth = fullWidth - imageThumbBounds.width;
		int xThumbPos = (int) ((mouseDown && !mouseMoveAdjusts ? draggingPercent : percent) * drawWidth);
		gc.drawImage(imageThumb, xThumbPos, 0);

	}

	public double getPercent() {
		return percent;
	}

	public void setPercent(double percent) {
		setPercent(percent, false);
	}

	private boolean areDoublesEqual(double a, double b) {
		return Math.abs(a - b) < 0.000001;
	}

	private void setPercent(double newPercent, boolean triggerListeners) {
		if (areDoublesEqual(percent, newPercent)) {
			return;
		}

		newPercent = validatePercent(newPercent, triggerListeners);

		if (areDoublesEqual(percent, newPercent)) {
			return;
		}

		this.percent = newPercent;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (canvas != null && !canvas.isDisposed()) {
					canvas.redraw();
					canvas.update();
				}
			}
		});

		if (triggerListeners) {
			Object[] listenersArray = listeners.toArray();
			for (int i = 0; i < listenersArray.length; i++) {
				SWTSkinListenerSliderSelection l = (SWTSkinListenerSliderSelection) listenersArray[i];
				l.selectionChanged(this.percent);
			}
		}
	}

	/**
	 * @return
	 *
	 * @since 3.0.2.3
	 */
	private double validatePercent(double percent, boolean triggerListeners) {
		if (triggerListeners) {
			Object[] listenersArray = listeners.toArray();
			for (int i = 0; i < listenersArray.length; i++) {
				SWTSkinListenerSliderSelection l = (SWTSkinListenerSliderSelection) listenersArray[i];
				Double changedPercent = l.selectionChanging(this.percent, percent);
				if (changedPercent != null) {
					return changedPercent.floatValue();
				}
			}
		}

		if (percent < 0) {
			return 0;
		} else if (percent > 1) {
			return 1;
		}
		return percent;
	}

	// @see org.eclipse.swt.events.MouseListener#mouseDoubleClick(org.eclipse.swt.events.MouseEvent)
	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	// @see org.eclipse.swt.events.MouseListener#mouseDown(org.eclipse.swt.events.MouseEvent)
	@Override
	public void mouseDown(MouseEvent e) {
		if (disabled) {
			return;
		}
		mouseDown = true;

		int offset = imageThumbBounds.width / 2;
		int sizeX = maxSize.x;
		if (maxSize.x == 0) {
			sizeX = canvas.getClientArea().width;
		}
		float newPercent = (e.x - offset)
				/ (float) (sizeX - imageThumbBounds.width);

		if (mouseMoveAdjusts) {
			setPercent(newPercent, true);
		} else {
			draggingPercent = validatePercent(newPercent, true);

			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					if (canvas != null && !canvas.isDisposed()) {
						canvas.redraw();
						canvas.update();
					}
				}
			});

		}
	}

	// @see org.eclipse.swt.events.MouseListener#mouseUp(org.eclipse.swt.events.MouseEvent)
	@Override
	public void mouseUp(MouseEvent e) {
		if (disabled) {
			return;
		}
		mouseDown = false;
		if (!mouseMoveAdjusts) {
			int offset = imageThumbBounds.width / 2;
			int sizeX = maxSize.x;
			if (maxSize.x == 0) {
				sizeX = canvas.getClientArea().width;
			}
			float newPercent = (e.x - offset)
					/ (float) (sizeX - imageThumbBounds.width);
			setPercent(newPercent, true);
		}
	}

	// @see org.eclipse.swt.events.MouseMoveListener#mouseMove(org.eclipse.swt.events.MouseEvent)
	@Override
	public void mouseMove(MouseEvent e) {
		if (disabled) {
			return;
		}
		if (mouseDown) {
			int offset = imageThumbBounds.width / 2;
			int sizeX = maxSize.x;
			if (maxSize.x == 0) {
				sizeX = canvas.getClientArea().width;
			}
			float newPercent = (e.x - offset)
					/ (float) (sizeX - imageThumbBounds.width);

			if (mouseMoveAdjusts) {
				setPercent(newPercent, true);
			} else {
				draggingPercent = validatePercent(newPercent, true);

				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (canvas != null && !canvas.isDisposed()) {
							canvas.redraw();
							canvas.update();
						}
					}
				});

			}
		}
	}

	public void addListener(SWTSkinListenerSliderSelection listener) {
		listeners.add(listener);
	}

	public static class SWTSkinListenerSliderSelection
	{
		/**
		 *
		 * @param oldPercent
		 * @param newPercent
		 * @return return null if you do not wish to change the value
		 *
		 * @since 3.0.2.3
		 */
		public Double selectionChanging(double oldPercent, double newPercent) {
			return null;
		}

		public void selectionChanged(double percent) {

		}
	}

	public boolean getMouseMoveAdjusts() {
		return mouseMoveAdjusts;
	}

	public void setMouseMoveAdjusts(boolean mouseMoveAdjusts) {
		this.mouseMoveAdjusts = mouseMoveAdjusts;
	}

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		if (disabled == this.disabled) {
			return;
		}
		this.disabled = disabled;
		if (disabled) {
			mouseDown = false;
		}
	}
}
