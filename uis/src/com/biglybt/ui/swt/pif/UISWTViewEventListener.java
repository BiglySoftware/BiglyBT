/*
 * File    : UISWTViewEventListener.java
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
 * Listener to be triggered when an event related to a UISWTView takes place
 *
 * @see com.biglybt.ui.swt.pif.UISWTInstance#addView(String, String, UISWTViewEventListener)
 *
 * @author TuxPaper
 */
public interface UISWTViewEventListener {
	/**
	 * Triggers when an even listed in UISWTViewEvent occurs
	 *
	 * @param event event that occurred
	 * @return meaning dependent upon event type
	 *
	 * @since 2.3.0.6
	 */
	public boolean eventOccurred(UISWTViewEvent event);
	
	public default boolean
	informOfDuplicates(
		int		type )
	{
		return( false );
	}
}
