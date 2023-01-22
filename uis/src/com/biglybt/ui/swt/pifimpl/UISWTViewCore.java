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

import org.eclipse.swt.widgets.Composite;

import com.biglybt.ui.common.viewtitleinfo.ViewTitleInfo;
import com.biglybt.ui.swt.pif.PluginUISWTSkinObject;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;

/**
 * A holding area between the public UISWTView plugin interface,
 * and things that we may eventually move into UISWTView
 *
 */
public interface UISWTViewCore
	extends UISWTView
{
	// XXX ControlType never seems to be set to this.. could remove?
	public static final int CONTROLTYPE_SKINOBJECT = 0x100 + 1;

	// >> From IView
  /**
   * This method is called when the view is instanciated, it should initialize all GUI
   * components. Must NOT be blocking, or it'll freeze the whole GUI.
   * Caller is the GUI Thread.
   *
   * @param composite the parent composite. Each view should create a child
   *         composite, and then use this child composite to add all elements
   *         to.
   *
   * @note It's possible that the view may be created, but never initialize'd.
   *        In these cases, delete will still be called.
   */
  public void initialize(Composite composite);

  /**
   * This method is called after initialize so that the Tab is set its control
   * Caller is the GUI Thread.
   * @return the Composite that should be set as the control for the Tab item
   */
  public Composite getComposite();

  /**
   * Messagebundle ID for title
   */
  public String getTitleID();

  /**
   * Called in order to set / update the title of this View.  When the view
   * is being displayed in a tab, the full title is used for the tooltip.
   *
   * @return the full title for the view
   */
  public String getFullTitle();

  // << From IView

	public void setPluginSkinObject(PluginUISWTSkinObject so);

	public PluginUISWTSkinObject getPluginSkinObject();

	public void setUseCoreDataSource(boolean useCoreDataSource);

	public boolean useCoreDataSource();

	/**
	 * @return Returns data source, based on {@link #useCoreDataSource()}
	 */
	@Override
	Object getDataSource();

	public UISWTViewEventListener getEventListener();

	UISWTViewBuilderCore getEventListenerBuilder();

	public ViewTitleInfo
	getViewTitleInfo();
	
	public void
	setViewTitleInfo(
		ViewTitleInfo		info );
	
	public void
	setUserData(
		Object		key,
		Object		data );

	public Object
	getUserData(
		Object		key );

	public void setParentView(UISWTView parentView);

	SWTSkinObjectContainer buildStandAlone(SWTSkinObjectContainer soParent);

	boolean canBuildStandAlone();
}
