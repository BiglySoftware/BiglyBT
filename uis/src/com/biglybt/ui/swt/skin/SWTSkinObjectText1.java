/*
 * Created on Jun 26, 2006 12:46:42 PM
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
package com.biglybt.ui.swt.skin;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.utils.FontUtils;

/**
 * Text Skin Object.  This one uses a label widget.
 *
 * @author TuxPaper
 * @created Jun 26, 2006
 *
 */
public class SWTSkinObjectText1
	extends SWTSkinObjectBasic
	implements SWTSkinObjectText
{
	String sText;

	String sKey;

	boolean bIsTextDefault = false;

	Label label;

	private int style;

	public SWTSkinObjectText1(SWTSkin skin, SWTSkinProperties skinProperties,
			String sID, String sConfigID, String[] typeParams, SWTSkinObject parent) {
		super(skin, skinProperties, sID, sConfigID, "text", parent);

		String sPrefix = sConfigID + ".text";

		if ( properties.getBooleanValue(sPrefix + ".wrap", true )){
			style = SWT.WRAP;
		}else{
			style = SWT.NONE;
		}

		String sAlign = skinProperties.getStringValue(sConfigID + ".align");
		if (sAlign != null) {
			int align = SWTSkinUtils.getAlignment(sAlign, SWT.NONE);
			if (align != SWT.NONE) {
				style |= align;
			}
		}

		if (skinProperties.getIntValue(sConfigID + ".border", 0) == 1) {
			style |= SWT.BORDER;
		}

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		boolean bKeepMaxSize = properties.getStringValue(
				sConfigID + ".keepMaxSize", "").equals("1");
		label = bKeepMaxSize ? new LabelNoShrink(createOn, style) : new Label(
				createOn, style);
		setControl(label);
		if (typeParams.length > 1) {
			bIsTextDefault = true;
			sText = typeParams[1];
			label.setText(sText);
		}
	}

	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp, boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);
		if (suffix == null) {
			return null;
		}

		String sPrefix = sConfigID + ".text";

		if (sText == null || bIsTextDefault) {
			String text = properties.getStringValue(sPrefix + suffix);
			if (text != null) {
				label.setText(text);
			}
		}

		Color color = properties.getColor(sPrefix + ".color" + suffix);
		//System.out.println(this + "; " + sPrefix + ";" + suffix + "; " + color + "; " + text);
		if (color != null) {
			label.setForeground(color);
		}

		Font existingFont = (Font) label.getData("Font" + suffix);
		if (existingFont != null && !existingFont.isDisposed()) {
			label.setFont(existingFont);
		} else {
			boolean bNewFont = false;
			float fontSizeAdj = -1;
			int iFontWeight = -1;
			String sFontFace = null;
			FontData[] tempFontData = label.getFont().getFontData();

			String sSize = properties.getStringValue(sPrefix + ".size" + suffix);
			if (sSize != null) {
				FontData[] fd = label.getFont().getFontData();

				try {
					char firstChar = sSize.charAt(0);
					char lastChar = sSize.charAt(sSize.length() - 1);
					if (firstChar == '+' || firstChar == '-') {
						sSize = sSize.substring(1);
					} else if (lastChar == '%') {
						sSize = sSize.substring(0, sSize.length() - 1);
					}

					float dSize = NumberFormat.getInstance(Locale.US).parse(sSize).floatValue();

					if (lastChar == '%') {
						fontSizeAdj = dSize / 100;
					} else if (firstChar == '+') {
						fontSizeAdj = 1.0f + (dSize * 0.1f);
					} else if (firstChar == '-') {
						fontSizeAdj = 1.0f - (dSize * 0.1f);
					} else if (sSize.endsWith("rem")) {
						fontSizeAdj = dSize;
					} else {
						fontSizeAdj = dSize / (float) FontUtils.getFontHeightInPX(tempFontData);
					}

					bNewFont = true;
				} catch (NumberFormatException e) {
					e.printStackTrace();
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			String sStyle = properties.getStringValue(sPrefix + ".style" + suffix);
			if (sStyle != null) {
				String[] sStyles = RegExUtil.PAT_SPLIT_COMMA.split(sStyle.toLowerCase());
				for (int i = 0; i < sStyles.length; i++) {
					String s = sStyles[i];
					if (s.equals("bold")) {
						if (iFontWeight == -1) {
							iFontWeight = SWT.BOLD;
						} else {
							iFontWeight |= SWT.BOLD;
						}
						bNewFont = true;
					}

					if (s.equals("italic")) {
						if (iFontWeight == -1) {
							iFontWeight = SWT.ITALIC;
						} else {
							iFontWeight |= SWT.ITALIC;
						}
						bNewFont = true;
					}

					if (s.equals("underline")) {
						label.addPaintListener(new PaintListener() {
							@Override
							public void paintControl(PaintEvent e) {
								Point size = ((Control) e.widget).getSize();
								e.gc.drawLine(0, size.y - 1, size.x - 1, size.y - 1);
							}
						});
					}

					if (s.equals("strike")) {
						label.addPaintListener(new PaintListener() {
							@Override
							public void paintControl(PaintEvent e) {
								Point size = ((Control) e.widget).getSize();
								int y = size.y / 2;
								e.gc.drawLine(0, y, size.x - 1, y);
							}
						});
					}
				}
			}

			sFontFace = properties.getStringValue(sPrefix + ".font" + suffix);
			if (sFontFace != null) {
				bNewFont = true;
			}

			if (bNewFont) {
				FontData[] fd = label.getFont().getFontData();

				if (fontSizeAdj > 0) {
					FontUtils.setFontDataHeight(fd,
							FontUtils.getHeight(fd) * fontSizeAdj);
				}

				if (iFontWeight >= 0) {
					fd[0].setStyle(iFontWeight);
				}

				if (sFontFace != null) {
					fd[0].setName(sFontFace);
				}

				final Font labelFont = new Font(label.getDisplay(), fd);
				label.setFont(labelFont);
				label.addDisposeListener(new DisposeListener() {
					@Override
					public void widgetDisposed(DisposeEvent e) {
						labelFont.dispose();
					}
				});

				label.setData("Font" + suffix, labelFont);
			}
		}

		label.update();

		return suffix;
	}

	/**
	 * @param searchText
	 */
	@Override
	public void setText(String text) {
		if (text == null) {
			text = "";
		}

		if (text.equals(sText)) {
			return;
		}

		this.sText = text;
		this.sKey = null;
		bIsTextDefault = false;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (label != null && !label.isDisposed()) {
					label.setText(sText);
					Utils.relayout(label);
				}
			}
		});
	}

	@Override
	public void setTextID(final String key) {
		if (key == null) {
			setText("");
		}

		else if (key.equals(sKey)) {
			return;
		}

		this.sText = MessageText.getString(key);
		this.sKey = key;
		bIsTextDefault = false;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (label != null && !label.isDisposed()) {
					Messages.setLanguageText(label, key);
					Utils.relayout(label);
				}
			}
		});
	}

	@Override
	public void setTextID(final String key, final String[] params) {
		if (key == null) {
			setText("");
		} else if (key.equals(sKey)) {
			return;
		}

		this.sText = MessageText.getString(key);
		this.sKey = key;
		bIsTextDefault = false;

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (label != null && !label.isDisposed()) {
					Messages.setLanguageText(label, key, params);
					Utils.relayout(label);
				}
			}
		});
	}

	private static class LabelNoShrink
		extends Label
	{
		Point ptMax;

		/**
		 * Default Constructor
		 *
		 * @param parent
		 * @param style
		 */
		public LabelNoShrink(Composite parent, int style) {
			super(parent, style | SWT.CENTER);
			ptMax = new Point(0, 0);
		}

		// I know what I'm doing. Maybe ;)
		@Override
		public void checkSubclass() {
		}

		@Override
		public Point computeSize(int wHint, int hHint, boolean changed) {
			Point pt = super.computeSize(wHint, hHint, changed);
			if (pt.x > ptMax.x) {
				ptMax.x = pt.x;
			}
			if (pt.y > ptMax.y) {
				ptMax.y = pt.y;
			}

			return ptMax;
		}
	}

	// @see SWTSkinObjectText#getStyle()
	@Override
	public int getStyle() {
		return style;
	}

	// @see SWTSkinObjectText#setStyle(int)
	@Override
	public void setStyle(int style) {
		this.style = style;
	}

	// @see SWTSkinObjectText#getText()
	@Override
	public String getText() {
		return sText;
	}

	// @see SWTSkinObjectText#addUrlClickedListener(SWTSkinObjectText_UrlClickedListener)
	@Override
	public void addUrlClickedListener(SWTSkinObjectText_UrlClickedListener l) {
		// TODO Auto-generated method stub

	}

	// @see SWTSkinObjectText#removeUrlClickedListener(SWTSkinObjectText_UrlClickedListener)
	@Override
	public void removeUrlClickedListener(SWTSkinObjectText_UrlClickedListener l) {
		// TODO Auto-generated method stub

	}

	// @see SWTSkinObjectText#setTextColor(org.eclipse.swt.graphics.Color)
	@Override
	public void setTextColor(Color color) {
		// TODO Auto-generated method stub

	}
}
