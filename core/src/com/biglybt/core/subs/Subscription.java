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

import java.util.List;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public interface
Subscription
	extends UtilitiesImpl.PluginSubscription
{
	public static final int AZ_VERSION	= 1;

	public static final Object	VUZE_FILE_COMPONENT_SUBSCRIPTION_KEY = new Object();

	public static final int	ADD_TYPE_CREATE		= 1;
	public static final int	ADD_TYPE_IMPORT		= 2;
	public static final int	ADD_TYPE_LOOKUP		= 3;

		/**
		 * Returns local name if set
		 * @return
		 */

	@Override
	public String
	getName();

	public String
	getName(
		boolean	use_local );

	public void
	setLocalName(
		String		str );

	public void
	setName(
		String		str )

		throws SubscriptionException;

	public String
	getNameEx();

	public String
	getQueryKey();

	@Override
	public String
	getID();

	public byte[]
	getPublicKey();

	public int
	getVersion();

	public long
	getAddTime();

	public int
	getAddType();

	public int
	getHighestVersion();

	public void
	resetHighestVersion();

	public int
	getAZVersion();

	public boolean
	isMine();

	public boolean
	isPublic();

	public void
	setPublic(
		boolean	is_public )

		throws SubscriptionException;

	public boolean
	isAnonymous();

	public boolean
	isUpdateable();

	public boolean
	isShareable();

	public boolean
	isSubscriptionTemplate();
	
	public long
	getMetadataMutationIndicator();
	
	public List<Subscription>
	getDependsOn();
	
	public void
	setDependsOn(
		List<Subscription>	subs );
	
	public long
	getNewestResultTime();
	
	public long
	getNextUpdateTime();
	
	public String
	getExecuteOnNewResult();
	
	public void
	setExecuteOnNewResult(
		String	str );
	
	@Override
	public boolean
	isSearchTemplate();

	public boolean
	isSearchTemplateImportable();

	public VuzeFile
	getSearchTemplateVuzeFile();

	public String
	getJSON()

		throws SubscriptionException;

	public boolean
	setJSON(
		String		json )

		throws SubscriptionException;

	public boolean
	isSubscribed();

	public void
	setSubscribed(
		boolean		subscribed );

	public void
	getPopularity(
		SubscriptionPopularityListener	listener )

		throws SubscriptionException;

	public boolean
	setDetails(
		String		name,
		boolean		is_public,
		String		json )

		throws SubscriptionException;

	public String
	getReferer();

	public long
	getCachedPopularity();

	public void
	addAssociation(
		byte[]		hash );

	public void
	addPotentialAssociation(
		String		result_id,
		String		key );

	public int
	getAssociationCount();

	public boolean
	hasAssociation(
		byte[]		hash );

	public String
	getCategory();

	public void
	setCategory(
		String	category );

	/**
	 * Tag UID
	 */
	public long
	getTagID();

	public void
	setTagID(
		long	tag_id );

	public static final int VO_FULL			= 0x00;
	public static final int VO_HIDE_HEADER	= 0x01;
	
	public int
	getViewOptions();
	
	public void
	setViewOptions(
		int		options );
	
	public String
	getParent();

	public void
	setParent(
		String		parent );

	public Engine
	getEngine()

		throws SubscriptionException;

	public Subscription
	cloneWithNewEngine(
		Engine 	engine )

		throws SubscriptionException;

	public boolean
	isAutoDownloadSupported();

	public VuzeFile
	getVuzeFile()

		throws SubscriptionException;

	public void
	setCreatorRef(
		String	str );

	public String
	getCreatorRef();

	public void
	reset();

	public void
	remove();

	public SubscriptionManager
	getManager();

	public SubscriptionHistory
	getHistory();

		/**
		 * shortcut to help plugin interface
		 * @param l
		 */

	@Override
	public SubscriptionResult[]
	getResults(
		boolean		include_deleted );

	public String
	getURI();

	public SubscriptionResultFilter
	getFilters()

		throws SubscriptionException;

	public void
	requestAttention();

	public void
	addListener(
		SubscriptionListener		l );

	public void
	removeListener(
		SubscriptionListener		l );

	public void
	setUserData(
		Object		key,
		Object		data );

	public Object
	getUserData(
		Object		key );

	public String
	getString();

		// fil
}
