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
import org.eclipse.swt.widgets.*;

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
	private static Font fontItalic;
	private static Font fontBoldItalic;
	
	static {
		try {
			mFontData_SetHeight = FontData.class.getDeclaredMethod("setHeight",
					float.class);
			mFontData_SetHeight.setAccessible(true);
		} catch (Throwable e) {
			mFontData_SetHeight = null;
		}

		try {
			mFontData_GetHeightF = FontData.class.getDeclaredMethod("getHeightF"
			);
			mFontData_GetHeightF.setAccessible(true);
		} catch (Throwable e) {
			mFontData_GetHeightF = null;
		}
	}

	public static Font getFontWithHeight(Font baseFont, int heightInPixels,
			int style) {
		boolean destroyBaseFont = style != SWT.DEFAULT;
		if (destroyBaseFont) {
			baseFont = getFontWithStyle(baseFont, style, 1.0f);
		}
		int fontHeightPX = FontUtils.getFontHeightInPX(baseFont);
		float pct = heightInPixels / (float) fontHeightPX;
		Font font = FontUtils.getFontPercentOf(baseFont, pct);
		if (destroyBaseFont) {
			baseFont.dispose();
		}
		return font;
	}

		// Used by azemp plugin
	@Deprecated
	public static Font getFontWithHeight(Font baseFont, GC gc,
			int heightInPixels, int style) {
		return getFontWithHeight(baseFont, heightInPixels, style);
	}

	public static void setFontDataHeight(FontData[] fd, float fontSize) {
		if (mFontData_SetHeight != null) {
			try {
				mFontData_SetHeight.invoke(fd[0], fontSize);
				return;
			} catch (Throwable ignore) {
			}
		}

		fd[0].setHeight((int) fontSize);
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
		for (FontData fData : fDatas) {
			fData.height = height;
			fData.setStyle(style);
		}
		final Font newFont = new Font(control.getDisplay(), fDatas);
		control.setFont(newFont);
		control.addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (!newFont.isDisposed()) {
					newFont.dispose();
				}
			}
		});
	}

	public static float getHeight(FontData[] fd) {
		if (mFontData_GetHeightF != null) {
			try {
				return ((Number) mFontData_GetHeightF.invoke(fd[0], new Object[] {})).floatValue();
			} catch (Throwable ignore) {
			}
		}

		return fd[0].getHeight();
	}

	public static Font getFontWithStyle(Font baseFont, int style,
			float sizeByPct) {
		FontData[] fontData = baseFont.getFontData();
		for (FontData fd : fontData) {
			fd.setStyle(style);
		}
		if (sizeByPct != 1.0f) {
			float height = getHeight(fontData) * sizeByPct;
			setFontDataHeight(fontData, height);
		}
		return new Font(baseFont.getDevice(), fontData);
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
			for (FontData fd : fontData) {
				fd.setStyle(SWT.BOLD);
			}
			fontBold = new Font(gc.getDevice(), fontData);
		}
		return fontBold;
	}

	public static Font getAnyFontItalic(GC gc) {
		if (fontItalic == null || fontItalic.isDisposed()) {
			FontData[] fontData = gc.getFont().getFontData();
			for (FontData fd : fontData) {
				fd.setStyle(SWT.ITALIC);
			}
			fontItalic = new Font(gc.getDevice(), fontData);
		}
		return fontItalic;
	}
	
	public static Font getAnyFontBoldItalic(GC gc) {
		if (fontBoldItalic == null || fontBoldItalic.isDisposed()) {
			FontData[] fontData = gc.getFont().getFontData();
			for (FontData fd : fontData) {
				fd.setStyle(SWT.BOLD | SWT.ITALIC);
			}
			fontBoldItalic = new Font(gc.getDevice(), fontData);
		}
		return fontBoldItalic;
	}
	
	public static void dispose() {
		if (fontBold != null) {
			fontBold.dispose();
			fontBold = null;
		}
		if (fontItalic != null) {
			fontItalic.dispose();
			fontItalic = null;
		}
		if (fontBoldItalic != null) {
			fontBoldItalic.dispose();
			fontBoldItalic = null;
		}
	}

	public static void fontToWidgetHeight(Text text) {
		text.addListener(SWT.Resize, new Listener() {
			Font lastFont = null;
			int	lastHeight = -1;

			@Override
			public void handleEvent(Event event) {
				Text text = (Text) event.widget;

				if (text == null) {
					return;
				}

				// getLineHeight doesn't take into account zoom on GTK3?
				// GTK3 source gets height from fontHeight, which is
				// PANGO_PIXELS(pango_font_metrics_get_ascent + pango_font_metrics_get_decent)
				// Tested: Zoom 200; getLineHeight=36; getFontHeightInPX=18
				//
				// On Windows, getLineHeight uses DPIUtil.autoScaleDown(px)
				// On Mac, who knows, but it doesn't call DPIUtil.
				//int lineHeightPX = text.getLineHeight();
				int lineHeightPX = getFontHeightInPX(text.getFont());

				int h = text.getClientArea().height - (text.getBorderWidth() * 2);
				if (Utils.isGTK3 || Utils.isDarkAppearance()) {
					// GTK3 and OSX dark mode has border included in clientArea
					h -= 6;
				}
				//System.out.println("h=" + h + ";lh=" + lineHeightPX + ";" + getFontHeightInPX(text.getFont()) );

				if ( h == lastHeight ){
					//return;
				}

				float pctAdjust = h / (float) lineHeightPX;
				//System.out.println("h=" + h + ";lh=" + lineHeightPX + "; " + pctAdjust);

				lastHeight = h;
				Font font = FontUtils.getFontPercentOf(text.getFont(), pctAdjust);
				font = ensureFontFitsHeight(font, h);
				text.setFont(font);

				if ( lastFont == null ){

					text.addDisposeListener(new DisposeListener() {
						@Override
						public void widgetDisposed(DisposeEvent e) {
							Text text = (Text) e.widget;
							if (text != null) {
								text.setFont(null);
							}
							Utils.disposeSWTObjects(lastFont);
						}
					});

				}else{
					Utils.disposeSWTObjects(lastFont);
				}

				lastFont = font;
			}
		});
	}

	private static Font ensureFontFitsHeight(Font font, int pxMaxHeight) {
		int px = getFontHeightInPX(font);
		if (px <= pxMaxHeight) {
			//System.out.println("ensureFontFitsHeight; wanted max " + pxMaxHeight + "; got " + px);
			return font;
		}
		Device device = font.getDevice();
		FontData[] fontData = font.getFontData();
		float height = getHeight(fontData);
		int newPX;
		do {
			height -= 0.25;
			setFontDataHeight(fontData, height);
			font = new Font(device, fontData);

			newPX = getFontHeightInPX(font);
			//System.out.println("Reduced font; wanted max " + pxMaxHeight + "; got " + px + "; trying " + height + " adjusts to " + newPX);
		} while (newPX > pxMaxHeight);

		return font;
	}

	public static double getCharacterWidth(Font f) {
		GC gc = new GC(f.getDevice());
		gc.setFont(f);
		FontMetrics metrics = gc.getFontMetrics();
		double d;
		try {
			d = metrics.getAverageCharacterWidth();
		} catch (Throwable t) {
				// last win32 SWT 4757
			d = (double) metrics.getAverageCharWidth();
		}
		gc.dispose();
		return d;
	}
}
