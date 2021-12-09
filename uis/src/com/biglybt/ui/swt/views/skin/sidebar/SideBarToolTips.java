/*
 * Created on Aug 13, 2008
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

package com.biglybt.ui.swt.views.skin.sidebar;

import java.util.List;

import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.common.updater.UIUpdatable;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MdiEntryVitalityImage;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.mdi.MdiEntrySWT;
import com.biglybt.ui.swt.mdi.MdiEntryVitalityImageSWT;
import com.biglybt.ui.swt.uiupdater.UIUpdaterSWT;


/**
 * @author TuxPaper
 * @created Aug 13, 2008
 *
 */
public class SideBarToolTips
	implements Listener, UIUpdatable
{
	Shell toolTipShell = null;

	Shell mainShell = null;

	CLabel toolTipLabel = null;

	private final Tree tree;

	private BaseMdiEntry mdiEntry;

	private Point lastRelMouseHoverPos;

	/**
	 * Initialize
	 */
	public SideBarToolTips(SideBar sidebar, Tree tree) {
		this.tree = tree;
		mainShell = tree.getShell();

		tree.addListener(SWT.Dispose, this);
		tree.addListener(SWT.KeyDown, this);
		tree.addListener(SWT.MouseMove, this);
		tree.addListener(SWT.MouseExit, this);
		tree.addListener(SWT.MouseHover, this);
		mainShell.addListener(SWT.Deactivate, this);
		tree.addListener(SWT.Deactivate, this);
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.type) {
			case SWT.MouseHover: {
				handleHover(new Point(event.x, event.y));
				break;
			}

			case SWT.Dispose:
				if (mainShell != null && !mainShell.isDisposed()) {
					mainShell.removeListener(SWT.Deactivate, this);
				}
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

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	private void handleHover(Point mousePos) {
		if (toolTipShell != null && !toolTipShell.isDisposed())
			toolTipShell.dispose();

		if ( !Utils.getTTEnabled()){
			return;
		}
		
		if (tree.getItemCount() == 0) {
			return;
		}
		int indent = SideBar.END_INDENT ? tree.getClientArea().width - 1 : 0;
		TreeItem treeItem = tree.getItem(new Point(indent, mousePos.y));
		if (treeItem == null) {
			return;
		}
		mdiEntry = (BaseMdiEntry) treeItem.getData("MdiEntry");
		if (mdiEntry == null) {
			return;
		}

		Rectangle itemBounds = treeItem.getBounds();
		Point relPos = new Point(mousePos.x, mousePos.y - itemBounds.y);
		String sToolTip = getToolTip(relPos);
		if (sToolTip == null || sToolTip.length() == 0) {
			return;
		}

		lastRelMouseHoverPos = relPos;

		Display d = tree.getDisplay();
		if (d == null)
			return;

		// We don't get mouse down notifications on trim or borders..
		toolTipShell = new Shell(tree.getShell(), SWT.ON_TOP);
		
		UIUpdaterSWT.getInstance().addUpdater(this);

		toolTipShell.addListener(SWT.Dispose, new Listener() {

			@Override
			public void handleEvent(Event event) {
				UIUpdaterSWT.getInstance().removeUpdater(SideBarToolTips.this);
			}
		});

		FillLayout f = new FillLayout();
		try {
			f.marginWidth = 3;
			f.marginHeight = 1;
		} catch (NoSuchFieldError e) {
			/* Ignore for Pre 3.0 SWT.. */
		}
		toolTipShell.setLayout(f);
		toolTipShell.setBackground(Colors.getSystemColor(d, SWT.COLOR_INFO_BACKGROUND));

		toolTipLabel = new CLabel(toolTipShell, SWT.WRAP);
		toolTipLabel.setForeground(Colors.getSystemColor(d, SWT.COLOR_INFO_FOREGROUND));
		toolTipLabel.setBackground(Colors.getSystemColor(d, SWT.COLOR_INFO_BACKGROUND));
		toolTipLabel.setText(sToolTip.replaceAll("&", "&&"));
		// compute size on label instead of shell because label
		// calculates wrap, while shell doesn't
		Point size = toolTipLabel.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		if (size.x > 600) {
			size = toolTipLabel.computeSize(600, SWT.DEFAULT, true);
		}
		size.x += toolTipShell.getBorderWidth() * 2 + 2;
		size.y += toolTipShell.getBorderWidth() * 2;
		try {
			size.x += toolTipShell.getBorderWidth() * 2 + (f.marginWidth * 2);
			size.y += toolTipShell.getBorderWidth() * 2 + (f.marginHeight * 2);
		} catch (NoSuchFieldError e) {
			/* Ignore for Pre 3.0 SWT.. */
		}
		Point pt = tree.toDisplay(mousePos.x, mousePos.y);
		Rectangle displayRect;
		try {
			displayRect = tree.getMonitor().getClientArea();
		} catch (NoSuchMethodError e) {
			displayRect = tree.getDisplay().getClientArea();
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
	}

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	private String getToolTip(Point mousePos_RelativeToItem) {
		List<MdiEntryVitalityImageSWT> vitalityImages = mdiEntry.getVitalityImages();
		for (MdiEntryVitalityImageSWT vitalityImage : vitalityImages) {
			if (vitalityImage == null) {
				continue;
			}
			String indicatorToolTip = vitalityImage.getToolTip();
			if (indicatorToolTip == null || !vitalityImage.isVisible()) {
				continue;
			}
			Rectangle hitArea = vitalityImage.getHitArea();
			if (hitArea == null) {
				continue;
			}
			if (hitArea.contains(mousePos_RelativeToItem)) {
				return indicatorToolTip;
			}
		}

		if (mdiEntry.getViewTitleInfo() != null) {
			String tt = (String) mdiEntry.getViewTitleInfo().getTitleInfoProperty(ViewTitleInfo.TITLE_INDICATOR_TEXT_TOOLTIP);
			return tt;
		}

		return null;
	}

	// @see UIUpdatable#getUpdateUIName()
	@Override
	public String getUpdateUIName() {
		return "SideBarToolTips";
	}

	// @see UIUpdatable#updateUI()
	@Override
	public void updateUI() {
		if (toolTipLabel == null || toolTipLabel.isDisposed()) {
			return;
		}
		if (mdiEntry == null || mdiEntry.getViewTitleInfo() == null) {
			return;
		}
		String sToolTip = getToolTip(lastRelMouseHoverPos);
		if (sToolTip == null) {
			return;
		}

		toolTipLabel.setText(sToolTip.replaceAll("&", "&&"));
	}
}
