/*
 * File    : UISWTViewEvent.java
 * Created : Oct 14, 2005
 * By      : TuxPaper
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.pif;

/**
 * A UI SWT View Event triggered by the UISWTViewEventListener
 *
 * @see com.biglybt.ui.swt.pif.UISWTViewEventListener
 * @see com.biglybt.ui.swt.pif.UISWTInstance#addView(String, String, UISWTViewEventListener)
 *
 * @author TuxPaper
 */
public interface UISWTViewEvent {
	public static final String[] DEBUG_TYPES = {
		"Create",
		"DS",
		"Init",
		"Shown",
		"Hiddn",
		"Refr",
		"Lang",
		"Destr",
		"Close",
		"Obfus"
	};

	public static String
	getEventDebug( int ev )
	{
		if ( ev < DEBUG_TYPES.length ){
			return( DEBUG_TYPES[ev]);
		}else{
			return( "" + ev );
		}
	}
	
	/**
	 * Triggered before view is initialize in order to allow any set up before
	 * initialization
	 * <p>
	 * This is the only time that setting {@link UISWTView#setControlType(int)}
	 * has any effect.
	 * <p>
	 * return true from {@link UISWTViewEventListener#eventOccurred(UISWTViewEvent)}
	 * if creation was successfull.  If you want only one instance of your view,
	 * or if there's any reason you can't create, return false, and an existing
	 * view will be used, if one is present.
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_CREATE = 0;

	/**
	 * Triggered when the datasource related to this view change.
	 * <p>
	 * Usually called after TYPE_CREATE, but before TYPE_INITIALIZE
	 * <p>
	 * getData() will return an Object[] array, or null
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_DATASOURCE_CHANGED = 1;

	/**
	 * Initialize your view.
	 * <p>
	 * getData() will return a SWT Composite or AWT Container for you to place
	 * object in.
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_INITIALIZE = 2;

	/**
	 * View has been shown.  Erroneously named TYPE_FOCUSGAINED. 
	 * <p/>
	 * When this view is already show, but not focused, 
	 * changing focus to this view will NOT trigger this event.
	 *
	 * @since Azureus 2.3.0.6
	 * @deprecated Use {@link #TYPE_SHOWN}
	 */
	public static final int TYPE_FOCUSGAINED = 3;

	/**
	 * View has been shown.  Same ID as {@link #TYPE_FOCUSGAINED}
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	public static final int TYPE_SHOWN = 3;

	/**
	 * View has been hidden.  Erroneously named TYPE_FOCUSLOST. 
	 * <p/>
	 * Losing the focus, but maintaining visibility will not trigger this event.
	 * <p>
	 * TYPE_FOCUSLOST is called before TYPE_DESTROY
	 *
	 * @since Azureus 2.3.0.6
	 * @deprecated Use {@link #TYPE_HIDDEN}
	 */
	public static final int TYPE_FOCUSLOST = 4;

	/**
	 * View has been hidden. .  Same ID as {@link #TYPE_FOCUSLOST}
	 *
	 * @since BiglyBT 2.1.0.1
	 */
	public static final int TYPE_HIDDEN = 4;

	/** Triggered on user-specified intervals.  Plugins should update any
	 * live information at this time.
	 * <p>
	 * Caller is the GUI thread
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_REFRESH = 5;

	/** Language has changed.  Plugins should update their text to the new
	 * language.  To determine the new language, use Locale.getDefault()
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_LANGUAGEUPDATE = 6;

	/**
	 * Triggered when the view is about to be destroyed
	 * <p>
	 * TYPE_FOCUSLOST may not be called before TYPE_DESTROY
	 *
	 * @since 2.3.0.6
	 */
	public static final int TYPE_DESTROY = 7;

	/**
	 * Triggered when the view is about to be closed
	 *
	 * @since 2.5.0.1
	 * @deprecated Not called.  Use {@link #TYPE_DESTROY}
	 */
	public static final int TYPE_CLOSE = 8;


	/**
	 * Triggered when the UI needs a privacy sensitive view.
	 * <P>
	 * Currently, getData() will return a map, with "image" key containing Image
	 *
	 *  @since 4.7.0.3
	 */
	public static final int TYPE_OBFUSCATE = 9;

	/**
	 * Triggered when the view is initially instantiated. Required for the
	 * rare occurrence when early initialisation is absolutely required.
	 * Before you ask note that the TYPE_CREATE event can be delayed...
	 */
	
	public static final int TYPE_PRE_CREATE = 10;

	/**
	 * Get the type.
	 *
	 * @return The TYPE_* constant for this event
	 *
	 * @since 2.3.0.6
	 */
	public int getType();

	/**
	 * Get the data
	 *
	 * @return Any data for this event
	 *
	 * @since 2.3.0.6
	 */
	public Object getData();

	/**
	 * Get the View
	 *
	 * @return Information and control over the view
	 *
	 * @since 2.3.0.6
	 */
	public UISWTView getView();
}
