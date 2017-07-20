/*
 * Created on 07-May-2004
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

import java.io.InputStream;

import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;

public interface
Update
{
	public static final int	RESTART_REQUIRED_NO			= 1;
	public static final int	RESTART_REQUIRED_YES		= 2;
	public static final int	RESTART_REQUIRED_MAYBE		= 3;

	public String
	getName();

	public String[]
	getDescription();

	public String
	getRelativeURLBase();

	public void
	setRelativeURLBase(
		String	base );

	/**
	 *
	 * @param url URL to load instead of showing the description text)
	 *
	 * @since 3.0.1.7
	 */
	public void
	setDescriptionURL(
		String url);

	/**
	 * @return null if no description url
	 *
	 * @since 3.0.1.7
	 */
	public String
	getDesciptionURL();

	public String
	getOldVersion();

	public String
	getNewVersion();

	public ResourceDownloader[]
	getDownloaders();

	public boolean
	isMandatory();

	public void
	setRestartRequired(
		int	restart_required );

	public int
	getRestartRequired();

	public void
	setUserObject(
		Object		obj );

	public Object
	getUserObject();

		/**
		 * mark the update as complete and inform any listeners
		 */

	public void
	complete(
		boolean	success );

		/**
		 * cancel this update
		 */

	public void
	cancel();

	public boolean
	isCancelled();

	public boolean
	isComplete();

	public boolean
	wasSuccessful();

	public UpdateCheckInstance
	getCheckInstance();

	public Object
	getDecision(
		int			decision_type,	// see UpdateManagerDecisionListener
		String		decision_name,
		String		decision_description,
		Object		decision_data );

	public InputStream
	verifyData(
		InputStream		is,
		boolean			force )

		throws UpdateException;

	public void
	addListener(
		UpdateListener	l );

	public void
	removeListener(
		UpdateListener	l );
}
