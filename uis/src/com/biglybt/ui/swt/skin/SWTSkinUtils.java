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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AERunnableBoolean;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Jun 8, 2006
 *
 */
public class SWTSkinUtils
{

	public static final int TILE_NONE = 0;

	public static final int TILE_Y = 1;

	public static final int TILE_X = 2;

	public static final int TILE_CENTER_X = 4;

	public static final int TILE_CENTER_Y = 8;

	public static final int TILE_BOTH = TILE_X | TILE_Y;

	private static Listener imageDownListener;

	//private static Listener imageOverListener;

	static {
		//imageOverListener = new SWTSkinImageChanger("-over", SWT.MouseEnter,
		//		SWT.MouseExit);
		imageDownListener = new SWTSkinImageChanger("-down", SWT.MouseDown,	SWT.MouseUp, 1 );
	}

	public static int getAlignment(String sAlign, int def) {
		int align;

		if (sAlign == null) {
			align = def;
		} else if (sAlign.equalsIgnoreCase("center")) {
			align = SWT.CENTER;
		} else if (sAlign.equalsIgnoreCase("bottom")) {
			align = SWT.BOTTOM;
		} else if (sAlign.equalsIgnoreCase("top")) {
			align = SWT.TOP;
		} else if (sAlign.equalsIgnoreCase("left")) {
			align = SWT.LEFT;
		} else if (sAlign.equalsIgnoreCase("right")) {
			align = SWT.RIGHT;
		} else {
			align = def;
		}

		return align;
	}

	/**
	 * @param tileMode
	 * @return
	 */
	public static int getTileMode(String sTileMode) {
		int tileMode = TILE_NONE;
		if (sTileMode == null || sTileMode == "") {
			return tileMode;
		}

		sTileMode = sTileMode.toLowerCase();

		if (sTileMode.equals("tile")) {
			tileMode = TILE_X | TILE_Y;
		} else if (sTileMode.equals("tile-x")) {
			tileMode = TILE_X;
		} else if (sTileMode.equals("tile-y")) {
			tileMode = TILE_Y;
		} else if (sTileMode.equals("center-x")) {
			tileMode = TILE_CENTER_X;
		} else if (sTileMode.equals("center-y")) {
			tileMode = TILE_CENTER_Y;
		}

		return tileMode;
	}

	static void addMouseImageChangeListeners(Control widget) {
		if (widget.getData("hasMICL") != null) {
			return;
		}

		//widget.addListener(SWT.MouseEnter, imageOverListener);
		//widget.addListener(SWT.MouseExit, imageOverListener);
		//		new MouseEnterExitListener(widget);

		widget.addListener(SWT.MouseDown, imageDownListener);
		widget.addListener(SWT.MouseUp, imageDownListener);

		widget.setData("hasMICL", "1");
	}

	public static void setVisibility(SWTSkin skin, String configID,
			String viewID, final boolean visible, boolean save) {

		SWTSkinObject skinObject = skin.getSkinObject(viewID);

		if (skinObject == null) {
			Debug.out("setVisibility on non existing skin object: " + viewID);
			return;
		}

		if (skinObject.isVisible() == visible && skin.getShell().isVisible()) {
			return;
		}

		final Control control = skinObject.getControl();

		if (control != null && !control.isDisposed()) {
			Point size;
			if (visible) {
				final FormData fd = (FormData) control.getLayoutData();
				size = (Point) control.getData("v3.oldHeight");
				//System.out.println(control.getData("SkinID") + " oldHeight = " + size + ";v=" + control.getVisible() + ";s=" + control.getSize());
				if (size == null) {
					size = control.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					if (fd.height > 0) {
						size.y = fd.height;
					}
					if (fd.width > 0) {
						size.x = fd.width;
					}
				}
			} else {
				size = new Point(0, 0);
			}
			setVisibility(skin, configID, skinObject, size, save );
		}
	}

	private static void setVisibility(SWTSkin skin, String configID,
			final SWTSkinObject skinObject, final Point destSize, boolean save ) {
		boolean visible = destSize.x != 0 || destSize.y != 0;
		try {
			if (skinObject == null) {
				return;
			}
			final Control control = skinObject.getControl();
			if (control != null && !control.isDisposed()) {
				if (visible) {
					FormData fd = (FormData) control.getLayoutData();
					fd.width = 0;
					fd.height = 0;
					control.setData("oldSize", new Point(0, 0));

					skinObject.setVisible(visible);

					// FormData should now be 0,0, but setVisible may have
					// explicitly changed it
					fd = (FormData) control.getLayoutData();

					if (fd.width != 0 || fd.height != 0) {
						return;
					}

					//if (destSize != null) {
						if (fd.width != destSize.x || fd.height != destSize.y) {
							fd.width = destSize.x;
							fd.height = destSize.y;
							control.setLayoutData(fd);
							Utils.relayout(control);
						}
					/*} else {
						if (fd.width == 0) {
							fd.width = SWT.DEFAULT;
						}
						if (fd.height == 0) {
							fd.height = SWT.DEFAULT;
						}
						control.setLayoutData(fd);
						Utils.relayout(control);
					}*/
					control.setData("v3.oldHeight", null);
				} else {
					final FormData fd = (FormData) control.getLayoutData();
					if (fd != null) {
						Point oldSize = new Point(fd.width, fd.height);
						if (oldSize.y <= 0) {
							oldSize = null;
						}
						control.setData("v3.oldHeight", oldSize);

						skinObject.setVisible(false);
					}
				}
			}

		} finally {
			if (save
					&& COConfigurationManager.getBooleanParameter(configID) != visible) {
				COConfigurationManager.setParameter(configID, visible);
			}

		}
	}

	public static void setVisibilityRelaxed(SWTSkin skin, String configID,
			String viewID, final boolean visible, boolean save) {

		SWTSkinObject skinObject = skin.getSkinObject(viewID);

		if (skinObject == null) {
			Debug.out("setVisibility on non existing skin object: " + viewID);
			return;
		}

		if (skinObject.isVisible() == visible && skin.getShell().isVisible()) {
			return;
		}

		final Control control = skinObject.getControl();

		if (control != null && !control.isDisposed()) {
			setVisibilityRelaxed(skin, configID, skinObject, visible, save );
		}
	}

	private static void setVisibilityRelaxed(SWTSkin skin, String configID,
			final SWTSkinObject skinObject, boolean visible, boolean save ) {

		try {
			if (skinObject == null) {
				return;
			}
			final Control control = skinObject.getControl();
			if (control != null && !control.isDisposed()) {
				if (visible) {
					FormData fd = (FormData) control.getLayoutData();
					fd.width = 0;
					fd.height = 0;
					control.setData("oldSize", new Point(0, 0));

					skinObject.setVisible(visible);

					// FormData should now be 0,0, but setVisible may have
					// explicitly changed it
					fd = (FormData) control.getLayoutData();

					if (fd.width != 0 || fd.height != 0) {
						return;
					}

					fd.width 	= SWT.DEFAULT;
					fd.height	= SWT.DEFAULT;
					
					Composite comp = control instanceof Composite?(Composite)control:control.getParent();
					
					if ( comp != null ){
						comp.layout( true, true );
						
						Utils.relayoutUp( comp );
					}
				} else {
					final FormData fd = (FormData) control.getLayoutData();
					if (fd != null) {
						Point oldSize = new Point(fd.width, fd.height);
						if (oldSize.y <= 0) {
							oldSize = null;
						}

						skinObject.setVisible(false);
					}
				}
			}

		} finally {
			if (save
					&& COConfigurationManager.getBooleanParameter(configID) != visible) {
				COConfigurationManager.setParameter(configID, visible);
			}

		}
	}
	
	
	
	
	
	/*
	public static void slide(final SWTSkinObject skinObject, final FormData fd,
			final Point destSize, final Runnable runOnCompletion) {
		final Control control = skinObject.getControl();
		//System.out.println("slide to " + size + " via "+ Debug.getCompressedStackTrace());
		Boolean exit = Utils.execSWTThreadWithBool("slide",
				new AERunnableBoolean() {
					@Override
					public boolean runSupport() {
						boolean exit = control.getData("slide.active") != null;
						Runnable oldROC = (Runnable) control.getData("slide.runOnCompletion");
						if (oldROC != null) {
							oldROC.run();
						}
						control.setData("slide.destSize", destSize);
						control.setData("slide.runOnCompletion", runOnCompletion);
						if (destSize.y > 0) {
							skinObject.setVisible(true);
						}
						return exit;
					}
				}, 1000);

		if (exit == null || exit) {
			return;
		}

		AERunnable runnable = new AERunnable() {
			boolean firstTime = true;

			float pct = 0.4f;

			@Override
			public void runSupport() {
				if (control.isDisposed()) {
					return;
				}
				Point size = (Point) control.getData("slide.destSize");
				if (size == null) {
					return;
				}

				if (firstTime) {
					firstTime = false;
					control.setData("slide.active", "1");
				}

				int newWidth = (int) (fd.width + (size.x - fd.width) * pct);
				int h = fd.height >= 0 ? fd.height : control.getSize().y;
				int newHeight = (int) (h + (size.y - h) * pct);
				pct += 0.01;
				//System.out.println(control + "] newh=" + newHeight + "/" + newWidth + " to " + size.y);

				if (newWidth == fd.width && newHeight == h) {
					fd.width = size.x;
					fd.height = size.y;
					//System.out.println(control + "] side to " + size.y + " done" + size.x);
					control.setLayoutData(fd);
					Utils.relayout(control);
					control.getParent().layout();

					control.setData("slide.active", null);
					control.setData("slide.destSize", null);

					if (newHeight == 0) {
						skinObject.setVisible(false);
						Utils.relayout(control);
					}

					Runnable oldROC = (Runnable) control.getData("slide.runOnCompletion");
					if (oldROC != null) {
						control.setData("slide.runOnCompletion", null);
						oldROC.run();
					}
				} else {
					fd.width = newWidth;
					fd.height = newHeight;
					control.setLayoutData(fd);
					//Utils.relayout(control, false);
					control.getParent().layout();

					Utils.execSWTThreadLater(20, this);
				}
			}
		};
		control.getDisplay().asyncExec(runnable);
	}
	*/
	
	public static class MouseEnterExitListener
		implements Listener
	{

		boolean bOver = false;

		public MouseEnterExitListener(Widget widget) {

			widget.addListener(SWT.MouseMove, this);
			widget.addListener(SWT.MouseExit, this);
		}

		@Override
		public void handleEvent(Event event) {
			Control control = (Control) event.widget;

			SWTSkinObject skinObject = (SWTSkinObject) control.getData("SkinObject");

			if (event.type == SWT.MouseMove) {
				if (bOver) {
					return;
				}
				System.out.println(System.currentTimeMillis() + ": " + skinObject
						+ "-- OVER");
				bOver = true;
				skinObject.switchSuffix("-over", 2, true);

			} else {
				bOver = false;
				System.out.println(System.currentTimeMillis() + ": " + skinObject
						+ "-- NOOVER");
				skinObject.switchSuffix("", 2, true);
			}

		}

	}


	public static SWTSkinObjectBrowser findBrowserSO(SWTSkinObject so) {
		if (so instanceof SWTSkinObjectBrowser) {
			return (SWTSkinObjectBrowser) so;
		}
		if (so instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer soContainer = (SWTSkinObjectContainer) so;
			SWTSkinObject[] children = soContainer.getChildren();
			for (int i = 0; i < children.length; i++) {
				SWTSkinObject child = children[i];
				SWTSkinObjectBrowser found = findBrowserSO(child);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}
