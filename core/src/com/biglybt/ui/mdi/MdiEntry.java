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

package com.biglybt.ui.mdi;

import java.util.Map;

import com.biglybt.pif.ui.UIPluginView;
import com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;


/**
 * @author TuxPaper
 * @created Aug 13, 2008
 *
 */
public interface MdiEntry extends UIPluginView
{

	public String getParentID();

	public Object getDatasource();

	public String getExportableDatasource();

	public boolean isCloseable();

	public String getId();

	public MdiEntryVitalityImage addVitalityImage(String imageID);

	public void addListeners(Object objectWithListeners);

	/**
	 * @param l
	 *
	 * @since 4.1.0.3
	 */
	void addListener(MdiCloseListener l);

	void addListener(MdiChildCloseListener l);

	/**
	 * @param l
	 *
	 * @since 4.1.0.3
	 */
	void removeListener(MdiCloseListener l);

	void removeListener(MdiChildCloseListener l);

	/**
	 * @param l
	 *
	 * @since 4.1.0.3
	 */
	void addListener(MdiEntryOpenListener l);

	/**
	 * @param l
	 *
	 * @since 4.1.0.3
	 */
	void removeListener(MdiEntryOpenListener l);

	public void addListener(MdiEntryDatasourceListener l);

	public void removeListener(MdiEntryDatasourceListener l);

	public void setImageLeftID(String string);

	//public void setCollapseDisabled(boolean b);

	public void addListener(MdiEntryDropListener listener);

	public void removeListener(MdiEntryDropListener listener);

	public void setDatasource(Object ds);

	public void setLogID(String logID);

	public boolean isAdded();

	public boolean isDisposed();

	public boolean isReallyDisposed();
	
	public ViewTitleInfo getViewTitleInfo();

	public void setViewTitleInfo(ViewTitleInfo viewTitleInfo);

	public String getLogID();

	public MultipleDocumentInterface getMDI();

	public MdiEntryVitalityImage[] getVitalityImages();

	public boolean close(boolean forceClose);
	
	public boolean close(boolean forceClose, boolean userInitiated );

	public void updateUI();

	public void redraw();

	public void addListener(MdiEntryLogIdListener l);

	public void removeListener(MdiEntryLogIdListener l);

	public void hide();

	public void requestAttention();

	public String getTitle();

	public void setTitle(String title);

	public void setTitleID(String titleID);

	public String getImageLeftID();

	public boolean isExpanded();

	public void setExpanded(boolean expanded);

	public void setDefaultExpanded(boolean defaultExpanded);

	public void expandTo();

	public void setParentID(String id);

	public UIToolBarEnablerBase[] getToolbarEnablers();

	public void addToolbarEnabler(UIToolBarEnablerBase enabler);

	public void removeToolbarEnabler(UIToolBarEnablerBase enabler);

	public boolean isSelectable();

	public void setSelectable(boolean selectable);

	public void setPreferredAfterID(String preferredAfterID);

	public String getPreferredAfterID();

	public void
	setUserData(
		Object	key,
		Object	value );

	public Object
	getUserData(
		Object	key );

	public Map<String, Object> getAutoOpenInfo();
}
