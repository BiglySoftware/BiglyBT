/*
 * Created on 11-May-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pif.update;

/**
 * @author parg
 *
 */

public interface
UpdateCheckInstance
{
	public static final int	UCI_INSTALL			= 1;
	public static final int	UCI_UPDATE			= 2;
	public static final int	UCI_UNINSTALL		= 3;

	public static final int	PT_UI_STYLE				= 1;	//Integer
	public static final int	PT_UI_STYLE_DEFAULT		= 1;
	public static final int	PT_UI_STYLE_SIMPLE		= 2;
	public static final int	PT_UI_STYLE_NONE		= 3;

	public static final int	PT_UI_PARENT_SWT_COMPOSITE					= 2;	// SWT Composite

	public static final int	PT_UI_DISABLE_ON_SUCCESS_SLIDEY				= 3;	// Boolean
	public static final int	PT_CLOSE_OR_RESTART_ALREADY_IN_PROGRESS		= 4;	// Boolean
	public static final int	PT_UNINSTALL_RESTART_REQUIRED				= 5;	// Boolean


	public static final int	PT_UI_EXTRA_MESSAGE				= 6;	// String
	
	
	public static final int	PT_RESOURCE_OVERRIDES			= 7;	// Map<String,Object>

	
		/**
		 * returns one of the above UCI_ constants
		 * @return
		 */

	public int
	getType();

		/**
		 * returns the name supplied when the instance was created (or "" if it wasn't)
		 * @return
		 */

	public String
	getName();

	public void
	start();

	public void
	cancel();

	public boolean
	isCancelled();

	public UpdateChecker[]
	getCheckers();

	public Update[]
	getUpdates();

	public UpdateInstaller
	createInstaller()

		throws UpdateException;

		/**
		 * Add a further updatable component to this instance. Must be called before
		 * the check process is started
		 * @param component
		 * @param mandatory
		 */

	public void
	addUpdatableComponent(
		UpdatableComponent		component,
		boolean					mandatory );

		/**
		 * Access to the update manager
		 * @return
		 */

	public UpdateManager
	getManager();

	public void
	setAutomatic(
		boolean	automatic );

	public boolean
	isAutomatic();

	public void
	setLowNoise(
		boolean	low_noise );

	public boolean
	isLowNoise();

	public boolean
	isCompleteOrCancelled();

	public Object
	getProperty(
		int		property_name );

	public void
	setProperty(
		int		property_name,
		Object	value );

	public void
	addDecisionListener(
		UpdateManagerDecisionListener	l );

	public void
	removeDecisionListener(
		UpdateManagerDecisionListener	l );

	public void
	addListener(
		UpdateCheckInstanceListener	l );

	public void
	removeListener(
		UpdateCheckInstanceListener	l );
}
