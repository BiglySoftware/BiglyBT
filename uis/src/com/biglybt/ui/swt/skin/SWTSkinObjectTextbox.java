/*
 * Created on Sep 21, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.skin;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * Native checkbox
 *
 * @author TuxPaper
 * @created Dec 24, 2008
 *
 */
public class SWTSkinObjectTextbox
	extends SWTSkinObjectBasic
{
	private Text textWidget;

	private Composite cBubble;

	private String 	text = "";

	public SWTSkinObjectTextbox(SWTSkin skin, SWTSkinProperties properties,
			String id, String configID, SWTSkinObject parentSkinObject) {
		super(skin, properties, id, configID, "textbox", parentSkinObject);

		Composite createOn;
		if (parent == null) {
			createOn = skin.getShell();
		} else {
			createOn = (Composite) parent.getControl();
		}

		boolean doBubble = false;
		int style = SWT.BORDER;

		String styleString = properties.getStringValue(sConfigID + ".style");
		if (styleString != null) {
			String[] styles = RegExUtil.PAT_SPLIT_COMMA.split(styleString.toLowerCase());
			Arrays.sort(styles);
			if (Arrays.binarySearch(styles, "readonly") >= 0) {
				style |= SWT.READ_ONLY;
			}
			if (Arrays.binarySearch(styles, "wrap") >= 0) {
				style |= SWT.WRAP;
			}
			if (Arrays.binarySearch(styles, "multiline") >= 0) {
				style |= SWT.MULTI | SWT.V_SCROLL;
			} else {
				style |= SWT.SINGLE;
			}
			if (Arrays.binarySearch(styles, "search") >= 0) {
				style |= SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL;
				if (Constants.isWindows || Utils.isGTK3) {
					// GTK3's TextBox with search icon and cancel icon can't have 0 width (invisible without taking up layout space)
					doBubble = true;
				}
			}
		} else {
			style |= SWT.SINGLE;
		}

		if ((style & SWT.WRAP) == 0 && (style & SWT.MULTI) > 0 && !properties.getBooleanValue(sConfigID + ".nohbar", false)) {
			style |= SWT.H_SCROLL;
		}

		if (!doBubble) {
			textWidget = new Text(createOn, style);
		} else {
			BubbleTextBox bubbleTextBox = new BubbleTextBox(createOn, style);
			textWidget = bubbleTextBox.getTextWidget();
			cBubble = bubbleTextBox.getParent();
		}

		textWidget.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				text = textWidget.getText();
			}
		});

		String message = properties.getStringValue(configID + ".message", (String) null);
		if (message != null && message.length() > 0) {
			textWidget.setMessage(message);
		}

		setControl(cBubble == null ? textWidget : cBubble);
		updateFont("");


		if ((style & SWT.SINGLE) > 0) {
			// If we set a height for a single line textbox, but didn't set the font
			// size, then force the font size to the widget height.
			int fixedHeight = properties.getIntValue(sConfigID + ".height", -1);
			boolean noFontSize = properties.getStringValue(sConfigID + ".text.size",
					"").isEmpty();
			if (fixedHeight > 0 && noFontSize) {
				FontUtils.fontToWidgetHeight(textWidget);
			}
		}
	}

	// @see SWTSkinObjectBasic#switchSuffix(java.lang.String, int, boolean)
	@Override
	public String switchSuffix(String suffix, int level, boolean walkUp,
	                           boolean walkDown) {
		suffix = super.switchSuffix(suffix, level, walkUp, walkDown);

		if (suffix == null) {
			return null;
		}

		String sPrefix = sConfigID + ".text";
		String text = properties.getStringValue(sPrefix + suffix);
		if (text != null) {
			setText(text);
		}

		return suffix;
	}

	public void setText(final String val) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (textWidget != null && !textWidget.isDisposed()) {
					textWidget.setText(val == null ? "" : val);
					text = val;
				}
			}
		});

	}

	public String getText() {
		return text;
	}

	public Text getTextControl() {
		return textWidget;
	}


	private void updateFont(String suffix) {
		String sPrefix = sConfigID + ".text";

		Font existingFont = (Font) textWidget.getData("Font" + suffix);
		if (existingFont != null && !existingFont.isDisposed()) {
			textWidget.setFont(existingFont);
		} else {
			boolean bNewFont = false;
			float fontSizeAdj = -1;
			String sFontFace = null;
			FontData[] tempFontData = textWidget.getFont().getFontData();

			sFontFace = properties.getStringValue(sPrefix + ".font" + suffix);
			if (sFontFace != null) {
				tempFontData[0].setName(sFontFace);
				bNewFont = true;
			}

			// Can't use properties.getPxValue for fonts, because
			// font.height isn't necessarily in px.
			String sSize = properties.getStringValue(sPrefix + ".size" + suffix);
			if (sSize != null) {
				sSize = sSize.trim();

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

			if (bNewFont) {
				FontData[] fd = textWidget.getFont().getFontData();

				if (fontSizeAdj > 0) {
					FontUtils.setFontDataHeight(fd,
							FontUtils.getHeight(fd) * fontSizeAdj);
				}

				if (sFontFace != null) {
					fd[0].setName(sFontFace);
				}

				final Font textWidgetFont = new Font(textWidget.getDisplay(), fd);
				textWidget.setFont(textWidgetFont);
				textWidget.addDisposeListener(new DisposeListener() {
					@Override
					public void widgetDisposed(DisposeEvent e) {
						textWidgetFont.dispose();
					}
				});

				textWidget.setData("Font" + suffix, textWidgetFont);
			}
		}
	}
}
