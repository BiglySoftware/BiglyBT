/*
 * Created on Aug 6, 2008
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.subs;

public interface
SubscriptionHistory
{
	public static final int	DEFAULT_CHECK_INTERVAL_MINS		= -1;	// Switched from 120 to -1 when configurable default introduced, use this to select configured default

	public boolean
	isEnabled();

	public void
	setEnabled(
		boolean		enabled );

	public boolean
	isAutoDownload();

	public void
	setAutoDownload(
		boolean		auto_dl );

	public void
	setDetails(
		boolean		enabled,
		boolean		auto_dl );

		/**
		 * Delete marks the result as explicitly deleted - it won't be re-discovered
		 * @param result_ids
		 */
	
	public void
	deleteResults(
		String[]		result_ids );

	public void
	deleteAllResults();

		/**
		 * This removes the result, it will be available for re-discovery if filters permit
		 * @param result_ids
		 */
	
	public void
	removeResults(
		String[]		result_ids );

	public void
	markAllResultsRead();

	public void
	markAllResultsUnread();

	public void
	markResults(
		String[]		result_ids,
		boolean[]		read );

	public void
	reset();

	public long
	getLastScanTime();

	public long
	getLastNewResultTime();

	public long
	getNextScanTime();

	public int
	getNumUnread();

	public int
	getNumRead();

	public int
	getCheckFrequencyMins();

	public void
	setCheckFrequencyMins(
		int		mins );

	public String
	getLastError();

	public boolean
	isAuthFail();

	public int
	getConsecFails();

	public SubscriptionResult[]
	getResults(
		boolean		include_deleted );

	public SubscriptionResult
	getResult(
		String		result_id );

	public boolean
	getDownloadWithReferer();

	public void
	setDownloadWithReferer(
		boolean		b );

	public int
	getMaxNonDeletedResults();

	public void
	setMaxNonDeletedResults(
		int			max );

	public String[]
	getDownloadNetworks();

	public void
	setDownloadNetworks(
		String[]	nets );

	public long
	getMaxAgeSecs();
	
	public void
	setMaxAgeSecs(
		long		max );
	
	public boolean
	getNotificationPostEnabled();

	public void
	setNotificationPostEnabled(
		boolean	enabled );
}
