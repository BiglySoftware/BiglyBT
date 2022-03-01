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

import java.util.List;
import java.util.Map;

import com.biglybt.pif.ui.UIPluginView;
import com.biglybt.pif.ui.toolbar.UIToolBarEnablerBase;
import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;


/**
 * A view (entry) that can be placed in a MDI (Multiple Document Interface).
 * Successor to UISWTView, this class is not SWT specific. In fact, UISWTView
 * and MdiEntry merge into a implementation class MdiEntrySWT later on:
 * <pre>
 *   UIPluginView
 *     + UISWTView
 *     |  + UISWTViewCore
 *     |     + MdiEntrySWT
 *     |     |  + BaseMdiEntry
 *     |     + UISWTViewImpl
 *     |        + BaseMdiEntry
 *     + MdiEntry
 *        + MdiEntrySWT
 *           + BaseMdiEntry
 * </pre>
 * 
 * MdiEntry differ from UISWTView in that they contain MDI related properties,
 * such as {@link ViewTitleInfo}, {@link MdiEntryVitalityImage}, expand state,
 * parent entry, etc.
 * 
 * @author TuxPaper
 * @created Aug 13, 2008
 *
 */
public interface MdiEntry extends UIPluginView
{

	/**
	 * ID of Parent MdiEntry
	 */
	public String getParentID();

	//TODO: Remove after 2.1.0.1 (RCM Plugin uses this)
	/**
	 * @apiNote Super class has  {@link UIPluginView#getDataSource()} which uses 
	 * capital S and is part of the PI, so we must go with that
	 * 
	 * @deprecated use {@link #getDataSource()}
	 */
	public Object getDatasource();

	/**
	 * Return an exportable version of the datasource.  Usually String, but
	 * can be Map and List
	 */
	public Object getExportableDatasource();

	public boolean isCloseable();

	/**
	 * @apiNote {@link #getViewID()} returns same value.
	 * @implNote RCM Uses this.  Remove after 2.1.0.1
	 * @deprecated Use {@link #getViewID()}
	 */
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

	public void addAcceleratorListener(MdiAcceleratorListener listener );
	
	public void processAccelerator(char c, int stateMask );
	
	public void removeAcceleratorListener(MdiAcceleratorListener listener );
	
	public void setDatasource(Object ds);

	/**
	 * Whether the content of this entry is disposed.
	 * <p/>
	 * Content can be disposed while entry is not disposed.  Content is always disposed when entry is disposed. 
	 */
	public boolean isContentDisposed();

	/**
	 * Whether this entry is disposed.
	 * <p/>
	 * Content can be disposed while entry is not disposed.  Content is always disposed when entry is disposed. 
	 */
	public boolean isEntryDisposed();
	
	public ViewTitleInfo getViewTitleInfo();

	public void setViewTitleInfo(ViewTitleInfo viewTitleInfo);

	public MultipleDocumentInterface getMDI();

	public List<? extends MdiEntryVitalityImage> getVitalityImages();

	/**
	 * Closes this entry. Disposes this entry and its contents.
	 *
	 * @param forceClose Some views may cancel a close (upon user request). 
	 *  If true, the cancel attempts will be ignored, ensuring view will be closed.
	 *  
	 * @deprecated use {@link #closeView()}
	 */
	// TODO: Remove after RCM, EMP updated and > 2101
	public boolean close(boolean forceClose);

	/**
	 * Closes this entry.
	 * Disposes this entry and its contents.
	 * Removes entry from auto-open list.
	 */
	@Override
	void closeView();

	public void updateUI( boolean force );

	public void redraw();

	public void hide();

	public void requestAttention();

	public String getTitle();

	public void setTitle(String title);

	public void setTitleID(String titleID);

	public String getImageLeftID();

	public boolean isExpanded();

	public void setExpanded(boolean expanded);

	public void setDefaultExpanded(boolean defaultExpanded);

	/**
	 * Set this entries belonging under another MdiEntry
	 * 
	 * @apiNote The getter is {@link #getParentID()} due to plugins already using it
	 * 
	 * @param parentEntryID Parent Entry ID to place under (if MDI supports it)
	 */
	public void setParentEntryID(String parentEntryID);

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
