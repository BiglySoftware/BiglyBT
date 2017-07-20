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

package com.biglybt.ui.swt.pifimpl;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.pif.ui.toolbar.UIToolBarActivationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

import com.biglybt.ui.common.ToolBarItem;

/**
 *
 * A Toolbar item implementation, independent of UI (SWT)
 *
 * @author TuxPaper
 * @created Feb 19, 2015
 *
 */
public class UIToolBarItemImpl
	implements ToolBarItem
{
	private String id;

	private String imageID = "image.toolbar.run";

	private String textID;

	private boolean alwaysAvailable = false;

	private long state;

	private UIToolBarActivationListener defaultActivation;

	private String tooltipID;

	private String groupID = UIToolBarManager.GROUP_MAIN;

	private List<ToolBarItemListener> toolBarItemListeners = new ArrayList<>();

	private String toolTip;

	public UIToolBarItemImpl(String id) {
		this.id = id;
	}

	// @see ToolBarItem#addToolBarItemListener(ToolBarItem.ToolBarItemListener)
	@Override
	public void addToolBarItemListener(ToolBarItemListener l) {
		if (!toolBarItemListeners.contains(l)) {
			toolBarItemListeners.add(l);
		}
	}

	// @see ToolBarItem#removeToolBarItemListener(ToolBarItem.ToolBarItemListener)
	@Override
	public void removeToolBarItemListener(ToolBarItemListener l) {
		toolBarItemListeners.remove(l);
	}

	private void triggerFieldChange() {
		ToolBarItemListener[] array = toolBarItemListeners.toArray(new ToolBarItemListener[0]);
		for (ToolBarItemListener l : array) {
			l.uiFieldChanged(this);
		}
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getID()
	@Override
	public String getID() {
		return id;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getTextID()
	@Override
	public String getTextID() {
		return textID;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setTextID(java.lang.String)
	@Override
	public void setTextID(String id) {
		textID = id;
		triggerFieldChange();
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getImageID()
	@Override
	public String getImageID() {
		return imageID;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setImageID(java.lang.String)
	@Override
	public void setImageID(String id) {
		imageID = id;
		triggerFieldChange();
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#isAlwaysAvailable()
	@Override
	public boolean isAlwaysAvailable() {
		return alwaysAvailable;
	}

	@Override
	public void setAlwaysAvailable(boolean alwaysAvailable) {
		this.alwaysAvailable = alwaysAvailable;
		triggerFieldChange();
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getState()
	@Override
	public long getState() {
		return state;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setState(long)
	@Override
	public void setState(long state) {
		this.state = state;
		triggerFieldChange();
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#triggerToolBarItem(long, java.lang.Object)
	@Override
	public boolean triggerToolBarItem(long activationType, Object datasource) {
		ToolBarItemListener[] array = toolBarItemListeners.toArray(new ToolBarItemListener[0]);
		for (ToolBarItemListener l : array) {
			if (l.triggerToolBarItem(this, activationType, datasource)) {
				return true;
			}
		}
		return false;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setDefaultActivationListener(com.biglybt.pif.ui.toolbar.UIToolBarActivationListener)
	@Override
	public void setDefaultActivationListener(
			UIToolBarActivationListener defaultActivation) {
		this.defaultActivation = defaultActivation;
	}

	// @see ToolBarItem#getDefaultActivationListener()
	@Override
	public UIToolBarActivationListener getDefaultActivationListener() {
		return defaultActivation;
	}

	// @see ToolBarItem#getTooltipID()
	@Override
	public String getTooltipID() {
		return tooltipID;
	}

	public void setTooltipID(String tooltipID) {
		this.tooltipID = tooltipID;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getGroupID()
	@Override
	public String getGroupID() {
		return groupID;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setGroupID(java.lang.String)
	@Override
	public void setGroupID(String groupID) {
		this.groupID = groupID;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#setToolTip(java.lang.String)
	@Override
	public void setToolTip(String text) {
		toolTip = text;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarItem#getToolTip()
	@Override
	public String getToolTip() {
		return toolTip;
	}
}
