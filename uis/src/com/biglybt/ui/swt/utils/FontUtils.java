/*
 * Created on Mar 7, 2010 11:10:45 AM
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
 */
package com.biglybt.ui.swt.utils;

import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Mar 7, 2010
 *
 */
public class FontUtils
{

	private static Method mFontData_SetHeight;

	private static Method mFontData_GetHeightF;

	private static Font fontBold;

	static {
		try {
			mFontData_SetHeight = FontData.class.getDeclaredMethod("setHeight",
					new Class[] {
						float.class
					});
			mFontData_SetHeight.setAccessible(true);
		} catch (Throwable e) {
			mFontData_SetHeight = null;
		}

		try {
			mFontData_GetHeightF = FontData.class.getDeclaredMethod("getHeightF",
					new Class[] {
					});
			mFontData_GetHeightF.setAccessible(true);
		} catch (Throwable e) {
			mFontData_GetHeightF = null;
		}
	}

	/**
	 *
	 * @param baseFont
	 * @param gc Can be null
	 * @param heightInPixels
	 * @return
	 *
	 * @since 1.0.0.0
	 */
	public static float getFontHeightFromPX(Font baseFont, GC gc,
			int heightInPixels) {
		Font font = null;
		Device device = baseFont.getDevice();

		// hack..
		heightInPixels++;

		// This isn't accurate, but gets us close
		float[] size = {
			Utils.pixelsToPoint(heightInPixels, Utils.getDPIRaw( device ).y) + 1
		};
		if (size[0] <= 0) {
			return 0;
		}

		boolean bOurGC = gc == null || gc.isDisposed();
		try {
			if (bOurGC) {
				gc = new GC(device);
			}
			FontData[] fontData = baseFont.getFontData();

			font = findFont(gc, font, fontData, size, heightInPixels, SWT.DEFAULT);

		} finally {
			if (bOurGC) {
				gc.dispose();
			}
			if (font != null && !font.isDisposed()) {
				font.dispose();
			}
		}
		return size[0];
	}

	public static float getFontHeightFromPX(Device device, FontData[] fontData,
			GC gc, int heightInPixels) {
		Font font = null;

		// hack..
		heightInPixels++;

		// This isn't accurate, but gets us close
		float[] size = {
			Utils.pixelsToPoint(heightInPixels, Utils.getDPIRaw( device ).y) + 1
		};
		if (size[0] <= 0) {
			return 0;
		}

		boolean bOurGC = gc == null || gc.isDisposed();
		try {
			if (bOurGC) {
				gc = new GC(device);
			}

			font = findFont(gc, font, fontData, size, heightInPixels, SWT.DEFAULT);

		} finally {
			if (bOurGC) {
				gc.dispose();
			}
			if (font != null && !font.isDisposed()) {
				font.dispose();
			}
		}
		return size[0];
	}

	public static Font getFontWithHeight(Font baseFont, GC gc, int heightInPixels) {
		return getFontWithHeight(baseFont, gc, heightInPixels, SWT.DEFAULT);
	}

	public static Font getFontWithHeight(Font baseFont, GC gc,
			int heightInPixels, int style) {
		Font font = null;
		Device device = baseFont.getDevice();

		// hack..
		heightInPixels++;

		// This isn't accurate, but gets us close
		float[] size = {
			Utils.pixelsToPoint(heightInPixels, Utils.getDPIRaw( device ).y) + 1
		};
		if (size[0] <= 0) {
			size[0] = 2;
		}

		boolean bOurGC = gc == null || gc.isDisposed();
		try {
			if (bOurGC) {
				gc = new GC(device);
			}
			FontData[] fontData = baseFont.getFontData();

			font = findFont(gc, font, fontData, size, heightInPixels, style);

		} finally {
			if (bOurGC) {
				gc.dispose();
			}
		}

		return font;
	}

	public static void setFontDataHeight(FontData[] fd, float fontSize) {
		if (mFontData_SetHeight != null) {
			try {
				mFontData_SetHeight.invoke(fd[0], fontSize);
				return;
			} catch (Throwable e) {
			}
		}

		fd[0].setHeight((int) fontSize);
	}

	private static Font findFont(GC gc, Font font, FontData[] fontData,
			float[] size, int heightInPixels, int style) {
		if (mFontData_SetHeight != null) {
			return findFontByFloat(gc, font, fontData, size, heightInPixels, style);
		}
		return findFontByInt(gc, font, fontData, size, heightInPixels, style);
	}

	public static Font findFontByInt(GC gc, Font font, FontData[] fontData,
			float[] returnSize, int heightInPixels, int style) {
		int size = (int) returnSize[0];
		do {
			if (font != null) {
				size--;
				font.dispose();
			}
			fontData[0].setHeight(size);
			if (style != SWT.DEFAULT) {
				fontData[0].setStyle(style);
			}

			font = new Font(gc.getDevice(), fontData);

			gc.setFont(font);

		} while (font != null
				&& gc.textExtent(Utils.GOOD_STRING).y > heightInPixels && size > 1);

		returnSize[0] = size;
		return font;
	}

	public static Font findFontByFloat(GC gc, Font font, FontData[] fontData,
			float[] returnSize, int heightInPixels, int style) {
		float size = returnSize[0];
		float delta = 2.0f;
		boolean fits;
		int numLoops = 0;
		do {
			numLoops++;
			if (font != null) {
				size -= delta;
				font.dispose();
			}
			try {
				mFontData_SetHeight.invoke(fontData[0], size);
			} catch (Throwable e) {
				Debug.out(e);
			}
			if (style != SWT.DEFAULT) {
				fontData[0].setStyle(style);
			}

			font = new Font(gc.getDevice(), fontData);

			gc.setFont(font);

			//System.out.println("yay " + size + " = "
			//		+ gc.textExtent(Utils.GOOD_STRING).y + " (want " + heightInPixels
			//		+ ")");

			fits = gc.textExtent(Utils.GOOD_STRING).y <= heightInPixels;
			if (fits && delta > .1) {
				size += delta;
				delta /= 2;
				fits = false;
			}
		} while (!fits && size > 1);

		returnSize[0] = size;
		return font;
	}

	public static int getFontHeightInPX(FontData[] fd) {
		Font font = new Font(Display.getDefault(), fd);
		try {
			return getFontHeightInPX(font);
		} finally {
			font.dispose();
		}
	}

	public static int getFontHeightInPX(Font font) {
		GC gc = new GC(font.getDevice());
		try {
			gc.setFont(font);
			return gc.textExtent(Utils.GOOD_STRING).y;
		} finally {
			gc.dispose();
		}
	}

	/**
	 * Change the height of the installed <code>Font</code> and takes care of disposing
	 * the new font when the control is disposed
	 * @param control
	 * @param height
	 * @param style one or both of SWT.BOLD, SWT.ITALIC, or SWT.NORMAL
	 */
	public static void setFontHeight(Control control, int height, int style) {
		FontData[] fDatas = control.getFont().getFontData();
		for (int i = 0; i < fDatas.length; i++) {
			fDatas[i].height = height;
			fDatas[i].setStyle(style);
		}
		final Font newFont = new Font(control.getDisplay(), fDatas);
		control.setFont(newFont);
		control.addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (null != newFont && !newFont.isDisposed()) {
					newFont.dispose();
				}
			}
		});
	}

	public static float getHeight(FontData[] fd) {
		if (mFontData_GetHeightF != null) {
			try {
				return ((Number) mFontData_GetHeightF.invoke(fd[0], new Object[] {})).floatValue();
			} catch (Throwable e) {
			}
		}

		return fd[0].getHeight();
	}

	public static Font getFontPercentOf(Font baseFont, float pct) {
		FontData[] fontData = baseFont.getFontData();
		float height = getHeight(fontData) * pct;
		setFontDataHeight(fontData, height);

		return new Font(baseFont.getDevice(), fontData);
	}

	public static Font getAnyFontBold(GC gc) {
		if (fontBold == null || fontBold.isDisposed()) {
			FontData[] fontData = gc.getFont().getFontData();
			for (int i = 0; i < fontData.length; i++) {
				FontData fd = fontData[i];
				fd.setStyle(SWT.BOLD);
			}
			fontBold = new Font(gc.getDevice(), fontData);
		}
		return fontBold;
	}

	public static void dispose() {
		if (fontBold != null) {
			fontBold.dispose();
			fontBold = null;
		}
	}

}
