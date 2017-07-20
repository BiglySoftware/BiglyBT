/*
 * Created on Apr 30, 2004
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
package com.biglybt.ui.swt.mainwindow;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.IUIIntializer;
import com.biglybt.ui.InitializerListener;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * The initial Splash Screen shown while the client loads
 */
public class SplashWindow
	implements InitializerListener
{
	private static final String IMG_SPLASH = "logo_splash";

	// config 1 : PB_HEIGHT = 3, PB_INVERTED = false
	// config 2 : PB_HEIGHT = 3, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 3
	// config 3 : PB_HEIGHT = 2, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 2
	// config 4 : PB_HEIGHT = 3, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 1, PB_INVERTED_X_OFFSET = 4

	protected static final int OFFSET_LEFT = 10;
	protected static final int OFFSET_RIGHT = 10;
	protected static final int OFFSET_BOTTOM = 12;
	protected static final int PB_HEIGHT = 2;

	protected static final boolean PB_INVERTED = true;
	protected static final int PB_INVERTED_BG_HEIGHT = 2;
	protected static final int PB_INVERTED_X_OFFSET = 0;

	protected static final boolean DISPLAY_BORDER = true;

	Display display;

	IUIIntializer initializer;

	Shell splash;

	//Label currentTask;
	//ProgressBar percentDone;

	Canvas canvas;

	Image background;

	int width;

	int height;

	Image current;

	Color progressBarColor;

	Color textColor;

	Color fadedGreyColor;

	Font textFont;

	private String task;

	private int percent;

	private boolean updating;

	int pbX, pbY, pbWidth;

	public SplashWindow(Display display) {
		this(display, null);
	}

	public static void main(String args[]) {
		Display display = new Display();

		final SplashWindow splash = new SplashWindow(display);

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					int percent = 0;
					while (percent <= 100) {
						splash.reportPercent(percent++);
						splash.reportCurrentTask(percent
								+ "% Loading dbnvsudn vjksfdh fgshdu fbhsduh bvsfd fbsd fbvsdb fsuid opnum supnum boopergood haha text doot subliminal.".substring(
										0, (int) (1 + Math.random() * 110)));
						Thread.sleep(100);
					}
				} catch (Exception e) {
					// TODO: handle exception
				}
				splash.closeSplash();
			}
		};
		t.start();

		while (!splash.splash.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	public SplashWindow(Display _display, IUIIntializer initializer) {
		this.display = _display;
		this.initializer = initializer;

		splash = new Shell(display, SWT.NO_TRIM);
		splash.setText(Constants.APP_NAME);
		Utils.setShellIcon(splash);

		splash.setLayout(new FillLayout());
		canvas = new Canvas(splash, SWT.DOUBLE_BUFFERED);

    ImageLoader imageLoader = ImageLoader.getInstance();
    background = imageLoader.getImage(IMG_SPLASH);
    if (ImageLoader.isRealImage(background)) {
    	width = background.getBounds().width;
    	height = background.getBounds().height;

    	width = 500;
    	height = 250;

    	current = new Image(display, background, SWT.IMAGE_COPY);
    } else {
    	width = 400;
    	height = 80;
    	background = new Image(display, width, height);
    	GC gc = new GC(background);
    	try {
    		gc.setBackground(ColorCache.getColor(display, 255, 255, 255));
    		gc.fillRectangle(0, 0, width, height);
    		gc.drawRectangle(0, 0, width - 1, height - 1);
    		gc.drawText(Constants.APP_NAME, 5, 5, true);
    	} finally {
    		gc.dispose();
    	}
    	current = new Image(display, background, SWT.IMAGE_COPY);
    }

		progressBarColor = new Color(display, 21, 92, 198);
		textColor = new Color(display, 90, 90, 90);
		fadedGreyColor = new Color(display, 170, 170, 170);


		pbX = OFFSET_LEFT;
		pbY = height - OFFSET_BOTTOM;
		pbWidth = width - OFFSET_LEFT - OFFSET_RIGHT;

		canvas.setSize(width, height);
		Font font = canvas.getFont();
		FontData[] fdata = font.getFontData();
		fdata[0].setHeight(Constants.isOSX ? 9 : 7);
		textFont = new Font(display, fdata);

		canvas.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent event) {
				if (current == null) {
					return;
				}
				Rectangle imgBounds = current.getBounds();
				Rectangle canvasBounds = canvas.getBounds();
				event.gc.drawImage(current, (canvasBounds.width - imgBounds.width) / 2, (canvasBounds.height - imgBounds.height - 30) / 2);

				GC gc = event.gc;

				try {
					gc.setAntialias(SWT.ON);
					gc.setTextAntialias(SWT.ON);
				} catch (Exception e) {

				}

				int y = pbY;

				if (task != null) {
					gc.setFont(textFont);
					gc.setForeground(textColor);
					Point extent = gc.textExtent(task);
					y = pbY - extent.y - 5;
					gc.setClipping(OFFSET_LEFT, y, width - (OFFSET_LEFT * 2), extent.y);
					gc.drawText(task, OFFSET_LEFT, y, true);
					Utils.setClipping(gc, (Rectangle) null);
				}

				if(PB_INVERTED){
					gc.setForeground(fadedGreyColor);
					gc.setBackground(fadedGreyColor);
					gc.fillRectangle(pbX-PB_INVERTED_X_OFFSET, pbY + Math.abs(PB_HEIGHT - PB_INVERTED_BG_HEIGHT) / 2, pbWidth+2*PB_INVERTED_X_OFFSET, PB_INVERTED_BG_HEIGHT);
					gc.setForeground(progressBarColor);
					gc.setBackground(progressBarColor);
					gc.fillRectangle(pbX, pbY, percent * pbWidth / 100, PB_HEIGHT);

				} else {
					gc.setForeground(progressBarColor);
					gc.setBackground(progressBarColor);
					if(!DISPLAY_BORDER){
						gc.fillRectangle(pbX, pbY, percent * pbWidth / 100, PB_HEIGHT);
					}
				}

				if(DISPLAY_BORDER){
					gc.setForeground(fadedGreyColor);
					gc.setBackground(fadedGreyColor);
					canvasBounds.height--;
					canvasBounds.width--;
					gc.drawRectangle(canvasBounds);
				}


			}
		});

		//splash.pack();
		splash.setSize(width, height);
		//splash.layout();
		Utils.centreWindow(splash);
		splash.open();

		if (initializer != null) {
			initializer.addListener(this);
		}
	}

	public static void create(final Display display, final IUIIntializer initializer) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (display == null || display.isDisposed())
					return;

				new SplashWindow(display, initializer);
			}
		});
	}

	/*
	 * Should be called by the GUI thread
	 */
	public void closeSplash() {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				try {
					if (initializer != null)
						initializer.removeListener(SplashWindow.this);
					if (splash != null && !splash.isDisposed())
						splash.dispose();
					if (current != null && !current.isDisposed()) {
						current.dispose();
					}
					if (progressBarColor != null && !progressBarColor.isDisposed()) {
						progressBarColor.dispose();
					}
					if (fadedGreyColor != null && !fadedGreyColor.isDisposed()) {
						fadedGreyColor.dispose();
					}
					if (textColor != null && !textColor.isDisposed()) {
						textColor.dispose();
					}
					if (textFont != null && !textFont.isDisposed()) {
						textFont.dispose();
					}

			    ImageLoader imageLoader = ImageLoader.getInstance();
			    imageLoader.releaseImage(IMG_SPLASH);
			    imageLoader.collectGarbage();

				} catch (Exception e) {
					//ignore
				}
			}
		});
	}

	@Override
	public void reportCurrentTask(final String task) {
		//Ensure that display is set and not disposed
		if (display == null || display.isDisposed())
			return;

		if (this.task == null || this.task.compareTo(task) != 0) {
			this.task = task;
			update();
		}
	}

	/**
	 *
	 *
	 * @since 3.0.0.7
	 */
	private void update() {
		if (updating && !Utils.isThisThreadSWT()) {
			return;
		}

		updating = true;
		//Post runnable to SWTThread
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				updating = false;
				if (splash == null || splash.isDisposed()) {
					return;
				}

				canvas.redraw(0, height - 50, width, height, true);
				canvas.update();
			}
		});
	}

	public int getPercent() {
		return percent;
	}

	@Override
	public void reportPercent(final int percent) {
		//System.out.println("splash: " + percent + " via " + Debug.getCompressedStackTrace());
		//Ensure that display is set and not disposed
		if (display == null || display.isDisposed())
			return;

		//OK Tricky way to close the splash window BUT ... sending a percent > 100 means closing
		if (percent > 100) {
			closeSplash();
			return;
		}

		if (this.percent != percent) {
			this.percent = percent;
			update();
		}
	}

}
