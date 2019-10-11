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

package com.biglybt.ui.swt.components;

import com.biglybt.ui.swt.utils.FontUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

/**
 * TextBox with a "search bubble" style around it.  Search icon on left, X on the right
 *
 * @author TuxPaper
 */
public class BubbleTextBox
{
	private Text textWidget;

	private Composite cBubble;

	private static final int PADDING_TOP = (Utils.isGTK3) ? 1 : 2;
	private static final int PADDING_BOTTOM = (Utils.isGTK3) ? 2 : 2;

	private int INDENT_OVAL;

	private int WIDTH_CLEAR;

	private int WIDTH_PADDING;

	private String 	text = "";

	public BubbleTextBox(Composite parent, int style) {
		cBubble = new Composite(parent, SWT.DOUBLE_BUFFERED);
		FormLayout layout = new FormLayout();
		layout.marginTop = PADDING_TOP;
		layout.marginBottom = PADDING_BOTTOM;
		cBubble.setLayout(layout);

		textWidget = new Text(cBubble, style & ~(SWT.BORDER | SWT.SEARCH)) {
			@Override
			protected void checkSubclass() {
			}

			@Override
			public Point computeSize(int wHint, int hHint, boolean changed) {
				Point point = super.computeSize(wHint, hHint, changed);
				if (Utils.isGTK3) {
					Rectangle area = getParent().getClientArea();
					//System.out.println("computedSize = " + point  + "; parent.h=" + area.height);

					// Bug in SWT: When there's no border, computed size chops off a few
					// pixels in height (probably because GTK3 shows a border when focused)
					point.y += 2;

					// Bug in SWT: Seems Text widget on GTK3 doesn't obey parent's fixed height
					if (area.height > 0 && point.y > area.height) {
						point.y = area.height;
					}
				}
				return point;
			}
		};
		if (Utils.isGTK3) {
			Display display = textWidget.getDisplay();
			textWidget.setBackground(Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND));
			textWidget.setForeground(Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));
		}

		FormData fd;

		Composite cCenterV = new Composite(cBubble, SWT.NO_BACKGROUND);
		fd = new FormData();
		fd.width = 1;
		fd.height = 1;
		fd.top = new FormAttachment(0);
		fd.bottom = new FormAttachment(100);
		cCenterV.setVisible(false);
		cCenterV.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(cCenterV,0, SWT.CENTER);
		fd.left = new FormAttachment(0, 17);
		fd.right = new FormAttachment(100, -15);
		textWidget.setLayoutData(fd);

		INDENT_OVAL = 6;
		WIDTH_CLEAR = 7;
		WIDTH_PADDING = 6;

		cBubble.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				Rectangle clientArea = cBubble.getClientArea();
				e.gc.setBackground(textWidget.getBackground());
				e.gc.setAdvanced(true);
				e.gc.setAntialias(SWT.ON);
				e.gc.fillRoundRectangle(clientArea.x, clientArea.y,
						clientArea.width - 1, clientArea.height - 1, clientArea.height,
						clientArea.height);
				e.gc.setAlpha(127);
				e.gc.drawRoundRectangle(clientArea.x, clientArea.y,
						clientArea.width - 1, clientArea.height - 1, clientArea.height,
						clientArea.height);

				e.gc.setAlpha(255);
				e.gc.setLineCap(SWT.CAP_FLAT);

				int fontHeight = FontUtils.getFontHeightInPX(textWidget.getFont());
				if (fontHeight > 17 - INDENT_OVAL - 1) {
					fontHeight = 17 - INDENT_OVAL - 1;
				}
				float heightOval = fontHeight * 0.7f;
				float widthOval = heightOval;

				Color colorClearX = Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW);
				e.gc.setForeground(colorClearX);


				int iconY = clientArea.y + ((clientArea.height - fontHeight + 1) / 2);

				e.gc.setLineWidth(2);
				e.gc.drawOval(clientArea.x + INDENT_OVAL, iconY, (int) widthOval,
					(int) heightOval);
				e.gc.drawPolyline(new int[] {
					(int) (clientArea.x + INDENT_OVAL + widthOval - 1),
					(int) (iconY + heightOval - 1),
					clientArea.x + INDENT_OVAL + fontHeight,
					iconY + fontHeight,
				});

				boolean textIsBlank = textWidget.getText().length() == 0;
				if (!textIsBlank) {
					int YADJ = (clientArea.height
							- (WIDTH_CLEAR + WIDTH_PADDING + WIDTH_PADDING)) / 2;
					e.gc.setLineCap(SWT.CAP_ROUND);
					//e.gc.setLineWidth(1);
					Rectangle rXArea = new Rectangle(
							clientArea.x + clientArea.width - (WIDTH_CLEAR + WIDTH_PADDING),
							clientArea.y + (WIDTH_PADDING / 2), WIDTH_CLEAR + (WIDTH_PADDING / 2),
							clientArea.height - WIDTH_PADDING);
					cBubble.setData("XArea", rXArea);

					e.gc.drawPolyline(new int[] {
						clientArea.x + clientArea.width - WIDTH_PADDING,
						clientArea.y + WIDTH_PADDING + YADJ,
						clientArea.x + clientArea.width - (WIDTH_PADDING + WIDTH_CLEAR),
						clientArea.y + WIDTH_PADDING + WIDTH_CLEAR + YADJ,
					});
					e.gc.drawPolyline(new int[] {
						clientArea.x + clientArea.width - WIDTH_PADDING,
						clientArea.y + WIDTH_PADDING + WIDTH_CLEAR + YADJ,
						clientArea.x + clientArea.width - (WIDTH_PADDING + WIDTH_CLEAR),
						clientArea.y + WIDTH_PADDING + YADJ,
					});
				}
			}
		});

		cBubble.addListener(SWT.MouseDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Rectangle r = (Rectangle) event.widget.getData("XArea");
				if (r != null && r.contains(event.x, event.y)) {
					textWidget.setText("");
				}
			}
		});

		// pick up changes in the text control's bg color and propagate to the bubble

		textWidget.addPaintListener(new PaintListener() {
			private Color existing_bg;

			@Override
			public void paintControl(PaintEvent arg0) {
				Color current_bg = textWidget.getBackground();

				if (!current_bg.equals(existing_bg)) {

					existing_bg = current_bg;

					cBubble.redraw();
				}
			}
		});

		textWidget.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				boolean textWasBlank = text.length() == 0;
				text = textWidget.getText();
				boolean textIsBlank = text.length() == 0;
				if (textWasBlank != textIsBlank && cBubble != null) {
					cBubble.redraw();
				}
			}
		});

	}

	public Composite getParent() {
		return cBubble;
	}

	public Text getTextWidget() {
		return textWidget;
	}
}
