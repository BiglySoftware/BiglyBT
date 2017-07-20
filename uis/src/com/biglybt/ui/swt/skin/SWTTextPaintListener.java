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
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.biglybt.ui.swt.shells.GCStringPrinter;

/**
 * @author TuxPaper
 * @created Jun 8, 2006
 *
 * XXX NOT USED XXX
 */
public class SWTTextPaintListener
	implements PaintListener
{
	private int align;

	private Color bgcolor;

	private Color fgcolor;

	private Font font;

	private String text;

	private SWTSkinProperties skinProperties;

	/**
	 *
	 */
	public SWTTextPaintListener(SWTSkin skin, Control createOn, String sConfigID) {
		skinProperties = skin.getSkinProperties();

		bgcolor = skinProperties.getColor(sConfigID + ".color");
		text = skinProperties.getStringValue(sConfigID + ".text");
		fgcolor = skinProperties.getColor(sConfigID + ".text.color");
		align = SWT.NONE;

		String sAlign = skinProperties.getStringValue(sConfigID + ".align");
		if (sAlign != null) {
			align = SWTSkinUtils.getAlignment(sAlign, SWT.NONE);
		}

		String sSize = skinProperties.getStringValue(sConfigID + ".text.size");

		if (sSize != null) {
			FontData[] fd = createOn.getFont().getFontData();

			try {
				char firstChar = sSize.charAt(0);
				if (firstChar == '+' || firstChar == '-') {
					sSize = sSize.substring(1);
				}

				int iSize = Integer.parseInt(sSize);

				if (firstChar == '+') {
					fd[0].height += iSize;
				} else if (firstChar == '-') {
					fd[0].height -= iSize;
				} else {
					fd[0].height = iSize;
				}

				font = new Font(createOn.getDisplay(), fd);
				createOn.addDisposeListener(new DisposeListener() {
					@Override
					public void widgetDisposed(DisposeEvent e) {
						font.dispose();
					}
				});
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void paintControl(PaintEvent e) {
		e.gc.setClipping(e.x, e.y, e.width, e.height);

		if (bgcolor != null) {
			e.gc.setBackground(bgcolor);
		}
		if (fgcolor != null) {
			e.gc.setForeground(fgcolor);
		}

		if (font != null) {
			e.gc.setFont(font);
		}

		if (text != null) {
			Rectangle clientArea = ((Composite) e.widget).getClientArea();
			GCStringPrinter.printString(e.gc, text, clientArea, true, true, align);
		}
	}
}
