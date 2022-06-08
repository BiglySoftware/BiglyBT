/*
 * Created on Jul 11, 2008
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

import java.net.URL;
import java.util.Map;

import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public interface
SubscriptionManager
	extends UtilitiesImpl.PluginSubscriptionManager
{
	public Subscription
	create(
		String		name,
		boolean		is_public,
		String		json )

		throws SubscriptionException;

	public Subscription
	createRSS(
		String		name,
		URL			url,
		int			check_interval_mins,
		Map			user_data )

		throws SubscriptionException;

	public Subscription
	createRSS(
		String		name,
		URL			url,
		int			check_interval_mins,
		boolean		anonymous,
		Map			user_data )

		throws SubscriptionException;

		// creates a subscription that will always have the same identity for the given parameters
		// and can't be updated

	public Subscription
	createSingletonRSS(
		String		name,
		URL			url,
		int			check_interval_mins,
		boolean		is_anon )

		throws SubscriptionException;

	public Subscription
	createSubscriptionTemplate(
		String		name )
	
		throws SubscriptionException;
	
	public Subscription
	createFromURI(
		String		uri )

		throws SubscriptionException;

	public int
	getKnownSubscriptionCount();

	public int
	getSubscriptionCount(
		boolean	subscribed_only );

	public Subscription[]
	getSubscriptions();

	@Override
	public Subscription[]
   	getSubscriptions(
   		boolean	subscribed_only );

	public Subscription
	getSubscriptionByID(
		String			id );

		/**
		 * Full lookup
		 * @param hash
		 * @param listener
		 * @return
		 * @throws SubscriptionException
		 */

	public SubscriptionAssociationLookup
	lookupAssociations(
		byte[]						hash,
		String						description,
		SubscriptionLookupListener	listener )

		throws SubscriptionException;

	/**
	 * @deprecated
	 * @param hash
	 * @param listener
	 * @return
	 * @throws SubscriptionException
	 */
	
	public SubscriptionAssociationLookup
	lookupAssociations(
		byte[]						hash,
		SubscriptionLookupListener	listener )

		throws SubscriptionException;
	public SubscriptionAssociationLookup
	lookupAssociations(
		byte[]						hash,
		String						description,
		String[]					networks,
		SubscriptionLookupListener	listener )

		throws SubscriptionException;

		/**
		 * Cached view of hash's subs
		 * @param hash
		 * @return
		 */

	public Subscription[]
	getKnownSubscriptions(
		byte[]						hash );

	public Subscription[]
	getLinkedSubscriptions(
		byte[]						hash );

	public SubscriptionScheduler
	getScheduler();

	public int
	getDefaultCheckFrequencyMins();
	
	public void
	setDefaultCheckFrequencyMins(
		int		mins );
	
	public int
	getMaxNonDeletedResults();

	public void
	setMaxNonDeletedResults(
		int			max );

	public boolean
	getAutoStartDownloads();

	public void
	setAutoStartDownloads(
		boolean		auto_start );

	public int
	getAutoStartMinMB();

	public void
	setAutoStartMinMB(
		int			mb );

	public int
	getAutoStartMaxMB();

	public void
	setAutoStartMaxMB(
		int			mb );

	public int
	getAutoDownloadMarkReadAfterDays();

	public void
	setAutoDownloadMarkReadAfterDays(
		int		days );

	public boolean
	isRSSPublishEnabled();

	public void
	setRSSPublishEnabled(
		boolean		enabled );

	public boolean
	isSearchEnabled();

	public void
	setSearchEnabled(
		boolean		enabled );

	public boolean
	isSubsDownloadEnabled();

	public void
	setSubsDownloadEnabled(
		boolean		enabled );

	public boolean
	hideSearchTemplates();

	public void
	setActivateSubscriptionOnChange(
		boolean		b );

	public boolean
	getActivateSubscriptionOnChange();

	public void
	setMarkResultsInLibraryRead(
		boolean		b );
	
	public boolean
	getMarkResultsInLibraryRead();
	
	public String
	getRSSLink();

	public void
	setRateLimits(
		String		limits );

	public String
	getRateLimits();

	public boolean
	getAddHashDirs();
	
	public void
	setAddHashDirs(
		boolean	b );
	
	public void
	addListener(
		SubscriptionManagerListener	listener );

	public void
	removeListener(
		SubscriptionManagerListener	listener );

	public Subscription
	subscribeToSubscription(
		String uri )
		throws Exception;

	public Subscription
	subscribeToRSS(
			String		name,
			URL 		url,
			int			interval,
			boolean		is_public,
			String		creator_ref )

			throws Exception;
	
	public void
	markAllRead(
		SearchSubsResultBase[]		results );
}
