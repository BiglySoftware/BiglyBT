/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.pif;

import com.biglybt.ui.swt.pif.UISWTViewBuilder.UISWTViewEventListenerInstantiator;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;

/**
 * Information on how to build a {@link UISWTView}
 */
public interface UISWTViewBuilder
{
	/**
	 * Set the very first datasource the view sees. Views can retrieve this first
	 * datasource even after the datasource changes using {@link UISWTView#getInitialDataSource()}
	 * 
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder setInitialDatasource(Object datasource);

	/**
	 * Sets a {@link UISWTViewEventListener} class that will be created when the 
	 * UI shows the view.
	 * <br/>
	 * Since this class will be instantiated with cla.newInstance(), the class
	 * must be a top-level class, and not a local or non-static nested class.
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder setListenerClass(
			Class<? extends UISWTViewEventListener> cla);

	/**
	 * Sometimes the title is needed even before an instance is created.  For
	 * example, menu items that open views
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder setInitialTitle(String initialTitle);

	/**
	 * Advanced creation of a {@link UISWTViewEventListener}.  Try not to use :)
	 * 
	 * @param canHandleMultipleViews 
	 *    Whether the {@link UISWTViewEventListener} created can handle multiple {@link UISWTView}
	 * @param listenerInstantiator 
	 *    called when a new {@link UISWTView} is created
	 *    
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilder setListenerInstantiator( UISWTViewEventListenerInstantiator listenerInstantiator);

	public UISWTViewEventListenerInstantiator
	getListenerInstantiator();
	
	/**
	 * Place this entry under the parentEntryID, if UI supports it.<br/>
	 * Sidebar can have multiple levels of views, but Tabbed MDI places all views at the same level.
	 * 
	 * @since BiglyBT 2.1.0.1
	 */
	UISWTViewBuilderCore setParentEntryID(String parentEntryID);

	/**
	 * @see #setListenerInstantiator(boolean, UISWTViewEventListenerInstantiator)
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	interface UISWTViewEventListenerInstantiator
	{
		public String
		getUID();
		
		public boolean
		supportsMultipleViews();
		
		/**
		 * A view has been created and is requesting a {@link UISWTViewEventListener} 
		 * to send {@link UISWTViewEvent}s to.
		 *
		 * @since BiglyBT 2.1.0.1
		 */
		UISWTViewEventListener createNewInstance(UISWTViewBuilder Builder,
			UISWTView forView)
				throws Exception;
		
	}
}
