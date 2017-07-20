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

package com.biglybt.ui.swt.views.table.impl;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;

import com.biglybt.ui.common.table.TableCellCore;

/**
 * @author TuxPaper
 * @created Mar 1, 2007
 *
 */
public class TableTooltips
	implements Listener
{
	Shell toolTipShell = null;

	Shell mainShell = null;

	Label toolTipLabel = null;

	private final Composite composite;

	private final TableViewSWT tv;

	/**
	 * Initialize
	 */
	public TableTooltips(TableViewSWT tv, Composite composite) {
		this.tv = tv;
		this.composite = composite;
		mainShell = composite.getShell();

		composite.addListener(SWT.Dispose, this);
		composite.addListener(SWT.KeyDown, this);
		composite.addListener(SWT.MouseMove, this);
		composite.addListener(SWT.MouseHover, this);
		mainShell.addListener(SWT.Deactivate, this);
		tv.getComposite().addListener(SWT.Deactivate, this);
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.type) {
			case SWT.MouseHover: {
				if (toolTipShell != null && !toolTipShell.isDisposed())
					toolTipShell.dispose();

				TableCellCore cell = tv.getTableCell(event.x, event.y);
				if (cell == null)
					return;
				cell.invokeToolTipListeners(TableCellSWT.TOOLTIPLISTENER_HOVER);
				Object oToolTip = cell.getToolTip();
				if ( oToolTip == null ){
					oToolTip = cell.getDefaultToolTip();
				}

				// TODO: support composite, image, etc
				if (oToolTip == null || oToolTip.toString().length() == 0)
					return;

				Display d = composite.getDisplay();
				if (d == null)
					return;

				// We don't get mouse down notifications on trim or borders..
				toolTipShell = new Shell(composite.getShell(), SWT.ON_TOP);
				FillLayout f = new FillLayout();
				try {
					f.marginWidth = 3;
					f.marginHeight = 1;
				} catch (NoSuchFieldError e) {
					/* Ignore for Pre 3.0 SWT.. */
				}
				toolTipShell.setLayout(f);
				toolTipShell.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));

				Point size = new Point(0, 0);

				if (oToolTip instanceof String) {
  				String sToolTip = (String) oToolTip;
  				toolTipLabel = new Label(toolTipShell, SWT.WRAP);
  				toolTipLabel.setForeground(d.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
  				toolTipLabel.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
  				toolTipShell.setData("TableCellSWT", cell);
  				toolTipLabel.setText(sToolTip.replaceAll("&", "&&"));
  				// compute size on label instead of shell because label
  				// calculates wrap, while shell doesn't
  				size = toolTipLabel.computeSize(SWT.DEFAULT, SWT.DEFAULT);
  				if (size.x > 600) {
  					size = toolTipLabel.computeSize(600, SWT.DEFAULT, true);
  				}
				} else if (oToolTip instanceof Image) {
					Image image = (Image) oToolTip;
  				toolTipLabel = new Label(toolTipShell, SWT.CENTER);
  				toolTipLabel.setForeground(d.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
  				toolTipLabel.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
  				toolTipShell.setData("TableCellSWT", cell);
  				toolTipLabel.setImage(image);
  				size = toolTipLabel.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				}
				size.x += toolTipShell.getBorderWidth() * 2 + 2;
				size.y += toolTipShell.getBorderWidth() * 2;
				try {
					size.x += toolTipShell.getBorderWidth() * 2 + (f.marginWidth * 2);
					size.y += toolTipShell.getBorderWidth() * 2 + (f.marginHeight * 2);
				} catch (NoSuchFieldError e) {
					/* Ignore for Pre 3.0 SWT.. */
				}
				Point pt = composite.toDisplay(event.x, event.y);
				Rectangle displayRect;
				try {
					displayRect = composite.getMonitor().getClientArea();
				} catch (NoSuchMethodError e) {
					displayRect = composite.getDisplay().getClientArea();
				}
				if (pt.x + size.x > displayRect.x + displayRect.width) {
					pt.x = displayRect.x + displayRect.width - size.x;
				}

				if (pt.y + size.y > displayRect.y + displayRect.height) {
					pt.y -= size.y + 2;
				} else {
					pt.y += 21;
				}

				if (pt.y < displayRect.y)
					pt.y = displayRect.y;

				toolTipShell.setBounds(pt.x, pt.y, size.x, size.y);
				toolTipShell.setVisible(true);

				break;
			}

			case SWT.Dispose:
				if (mainShell != null && !mainShell.isDisposed())
					mainShell.removeListener(SWT.Deactivate, this);
				if (tv.getComposite() != null && !tv.getComposite().isDisposed())
					tv.getComposite().removeListener(SWT.Deactivate, this);
				// fall through

			default:
				if (toolTipShell != null) {
					toolTipShell.dispose();
					toolTipShell = null;
					toolTipLabel = null;
				}
				break;
		} // switch
	} // handlEvent()
}
