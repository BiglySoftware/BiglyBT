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


package com.biglybt.core.subs.impl;

import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.activities.LocalActivityManager;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.subs.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.util.MapUtils;

public class
SubscriptionHistoryImpl
	implements SubscriptionHistory
{
	private static AsyncDispatcher	dispatcher = new AsyncDispatcher( "subspost" );

	private final SubscriptionManagerImpl		manager;
	private final SubscriptionImpl				subs;

	private boolean		enabled;
	private boolean		auto_dl;
	private boolean		post_notifications;

	private long		last_scan;
	private long		last_new_result;
	private int			num_unread;
	private int			num_read;
	private long		max_results	= -1;
	private String[]	networks	= null;
	
	private long		max_age_secs	= -1;
	
	private String			last_error;
	private boolean			auth_failed;
	private int				consec_fails;

	private boolean			auto_dl_supported;

	private boolean			dl_with_ref	= true;

	private int				interval_override;

	protected
	SubscriptionHistoryImpl(
		SubscriptionManagerImpl		_manager,
		SubscriptionImpl			_subs )
	{
		manager		= _manager;
		subs		= _subs;

		loadConfig();
	}

	protected SubscriptionResultImpl[]
	reconcileResults(
		Engine							engine_maybe_null,
		SubscriptionResultImpl[]		latest_results )
	{
		auto_dl_supported	= engine_maybe_null==null?false:engine_maybe_null.getAutoDownloadSupported() == Engine.AUTO_DL_SUPPORTED_YES;

		int	new_unread 	= 0;
		int new_read	= 0;

		if ( last_scan == 0 ){

					// first download feed -> mark all existing as read

			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

			for (int i=0;i<latest_results.length;i++){

				SubscriptionResultImpl result = latest_results[i];

				result.setReadInternal(true);

					// see if we can associate result with existing download

				try{
					String hash_str = result.getAssetHash();

					if ( hash_str != null ){

						byte[] hash = Base32.decode( hash_str );

						DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));

						if ( dm != null ){

							log( "Adding existing association on first read for '" + dm.getDisplayName());

							subs.addAssociation( hash );
						}
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		long	now = SystemTime.getCurrentTime();

		SubscriptionResultImpl[] result;

		int max_results = getMaxNonDeletedResults();

		if ( max_results < 0 ){

			max_results = manager.getMaxNonDeletedResults();
		}

		SubscriptionResultImpl	first_new_result = null;

		synchronized( this ){

			boolean got_changed	= false;

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] existing_results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			ByteArrayHashMap<SubscriptionResultImpl>	result_key_map 	= new ByteArrayHashMap<>();
			ByteArrayHashMap<SubscriptionResultImpl>	result_key2_map = new ByteArrayHashMap<>();

			List<SubscriptionResultImpl>	updated_results = new ArrayList<>(existing_results.length+latest_results.length);

			List<SubscriptionResultImpl>	new_results		= new ArrayList<>(latest_results.length);
			
			for (int i=0;i<existing_results.length;i++){

				SubscriptionResultImpl r = existing_results[i];

				result_key_map.put( r.getKey1(), r );

				byte[]	key2 = r.getKey2();

				if ( key2 != null ){

					result_key2_map.put( key2, r );
				}

				updated_results.add( r );

				if ( !r.isDeleted()){

					if ( r.getRead()){

						new_read++;

					}else{

						new_unread++;
					}
				}
			}

			for (int i=0;i<latest_results.length;i++){

				SubscriptionResultImpl r = latest_results[i];

					// we first of all insist on names uniqueness

				SubscriptionResultImpl existing = (SubscriptionResultImpl)result_key_map.get( r.getKey1());

				if ( existing == null ){

						// only if non-unique name do we fall back and use UID to remove duplicate
						// entries where the name has changed

					byte[]	key2 = r.getKey2();

					if ( key2 != null ){

						existing = (SubscriptionResultImpl)result_key2_map.get( key2 );
					}
				}

				if ( existing == null ){

					last_new_result = now;

					updated_results.add( r );

					result_key_map.put( r.getKey1(), r );

					byte[]	key2 = r.getKey2();

					if ( key2 != null ){

						result_key2_map.put( key2, r );
					}

					new_results.add( r );

					if ( r.getRead()){

						new_read++;

					}else{

						new_unread++;

						if ( first_new_result == null ){

							first_new_result = r;
						}
					}
				}else{

					if ( existing.updateFrom( r )){

						got_changed = true;
					}
				}
			}

				// see if we need to delete any old ones

			if ( max_results > 0 && (new_unread + new_read ) > max_results ){

				for (int i=0;i<updated_results.size();i++){

					SubscriptionResultImpl r = (SubscriptionResultImpl)updated_results.get(i);

					if ( !r.isDeleted()){

						if ( r.getRead()){

							new_read--;

						}else{

							new_unread--;
						}

						r.deleteInternal();

						got_changed = true;

						if (( new_unread + new_read ) <= max_results ){

							break;
						}
					}
				}
			}

			if ( got_changed || !new_results.isEmpty()){

				result = (SubscriptionResultImpl[])updated_results.toArray( new SubscriptionResultImpl[updated_results.size()]);

				manager.saveResults( subs, result, new_results );

			}else{

				result = existing_results;
			}

			last_scan 	= now;
			num_unread	= new_unread;
			num_read	= new_read;
		}

			// always save config as we have a new scan time

		saveConfig( SubscriptionListener.CR_RESULTS );

		if ( post_notifications && first_new_result != null ){

			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void
					runSupport()
					{
						Map<String,String>	cb_data = new HashMap<>();

						cb_data.put( "subname", subs.getName());
						cb_data.put( "subid", subs.getID());

						cb_data.put( "allowReAdd", "true" );

						String date_str = new SimpleDateFormat( "yyyy/MM/dd HH:mm:ss" ).format( new Date( SystemTime.getCurrentTime()));

						LocalActivityManager.addLocalActivity(
							"NewResults:" + subs.getID(),
							"rss",
							MessageText.getString(
								"subs.activity.new.results",
								new String[]{ subs.getName(), String.valueOf( num_unread )}) + ": " + date_str,
							new String[]{ MessageText.getString( "label.view" )},
							ActivityCallback.class,
							cb_data );
					}
				});
		}

		return( result );
	}

	public static class
	ActivityCallback
		implements LocalActivityManager.LocalActivityCallback
	{
		@Override
		public void
		actionSelected(
			String action, Map<String, String> data)
		{
			SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();

			String sub_id = (String)data.get( "subid" );

			final Subscription sub = subs_man.getSubscriptionByID( sub_id );

			if ( sub != null ){

				sub.requestAttention();
			}
		}
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	@Override
	public void
	setEnabled(
		boolean		_enabled )
	{
		if ( _enabled != enabled ){

			enabled	= _enabled;

			saveConfig(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public boolean
	isAutoDownload()
	{
		return( auto_dl );
	}

	@Override
	public void
	setAutoDownload(
		boolean		_auto_dl )
	{
		if ( _auto_dl != auto_dl ){

			auto_dl	= _auto_dl;

			saveConfig(SubscriptionListener.CR_METADATA);

			if ( auto_dl ){

				downloadNow();
			}
		}
	}

	@Override
	public int
	getMaxNonDeletedResults()
	{
		return( max_results<0?-1:(int)max_results );
	}

	@Override
	public void
	setMaxNonDeletedResults(
		int		_max_results )
	{
		if ( _max_results != max_results ){

			max_results	= _max_results;

			saveConfig(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public String[]
	getDownloadNetworks()
	{
		return( networks );
	}

	@Override
	public void
	setDownloadNetworks(
		String[] 	nets )
	{
		networks = nets;

		saveConfig(SubscriptionListener.CR_METADATA);
	}

	@Override
	public long 
	getMaxAgeSecs()
	{
		return( max_age_secs );
	}
	
	@Override
	public void 
	setMaxAgeSecs(
		long max )
	{
		max_age_secs = max;
		
		saveConfig(SubscriptionListener.CR_METADATA);
	}
	
	@Override
	public boolean
	getNotificationPostEnabled()
	{
		return( post_notifications );
	}

	@Override
	public void
	setNotificationPostEnabled(
		boolean	enabled )
	{
		if ( enabled != post_notifications ){

			post_notifications = enabled;

			saveConfig(SubscriptionListener.CR_METADATA);
		}
	}

	@Override
	public void
	setDetails(
		boolean 	_enabled,
		boolean 	_auto_dl )
	{
		if ( enabled != _enabled || auto_dl != _auto_dl ){

			enabled	= _enabled;
			auto_dl	= _auto_dl;

			saveConfig(SubscriptionListener.CR_METADATA);

			if ( enabled && auto_dl ){

				downloadNow();
			}
		}
	}

	protected void
	downloadNow()
	{
		try{
			subs.getManager().getScheduler().downloadAsync( subs, false );

		}catch( Throwable e ){

			log( "Failed to initiate download", e );
		}
	}

	@Override
	public long
	getLastScanTime()
	{
		return( last_scan );
	}

	@Override
	public long
	getLastNewResultTime()
	{
		return( last_new_result );
	}

	@Override
	public long
	getNextScanTime()
	{
		if ( interval_override > 0 ){

			if ( last_scan == 0 ){

					// never scanned, scan immediately

				return( SystemTime.getCurrentTime());

			}else{

				return( last_scan + interval_override*60*1000 );
			}
		}

		Map	schedule = subs.getScheduleConfig();

		if ( schedule.size() == 0  ){

			log( "Schedule is empty!");

			return( Long.MAX_VALUE );

		}else{

			try{

				long	interval_min = ((Long)schedule.get( "interval" )).longValue();

				if ( interval_min <= 0 ){
					
					interval_min = manager.getDefaultCheckFrequencyMins();
				}
				
				if ( interval_min == Integer.MAX_VALUE || interval_min == Long.MAX_VALUE ){

					return( Long.MAX_VALUE );
				}

				if ( last_scan == 0 ){

						// never scanned, scan immediately

					return( SystemTime.getCurrentTime());

				}else{

					return( last_scan + interval_min*60*1000 );
				}
			}catch( Throwable e ){

				log( "Failed to decode schedule " + schedule, e );

				return( Long.MAX_VALUE );
			}
		}
	}

	@Override
	public int
	getCheckFrequencyMins()
	{
		if ( interval_override > 0 ){

			return( interval_override );
		}

		Map	schedule = subs.getScheduleConfig();

		if ( schedule.size() == 0  ){

			return( manager.getDefaultCheckFrequencyMins());

		}else{

			try{
				int	interval_min = ((Long)schedule.get( "interval" )).intValue();

				if ( interval_min <= 0 ){
					
					interval_min = manager.getDefaultCheckFrequencyMins();
				}
				
				return( interval_min );

			}catch( Throwable e ){

				return( manager.getDefaultCheckFrequencyMins());
			}
		}
	}

	@Override
	public void
	setCheckFrequencyMins(
		int		mins )
	{
		interval_override		= mins;

		saveConfig(SubscriptionListener.CR_METADATA);

		subs.fireChanged( SubscriptionListener.CR_METADATA );
	}

	@Override
	public int
	getNumUnread()
	{
		return( num_unread );
	}

	@Override
	public int
	getNumRead()
	{
		return( num_read );
	}

	@Override
	public SubscriptionResult[]
	getResults(
		boolean		include_deleted )
	{
		SubscriptionResult[] results;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );
		}

		if ( include_deleted ){

			return( results );

		}else{

			List	l = new ArrayList( results.length );

			for (int i=0;i<results.length;i++){

				if ( !results[i].isDeleted()){

					l.add( results[i] );
				}
			}

			return((SubscriptionResult[])l.toArray( new SubscriptionResult[l.size()]));
		}
	}

	@Override
	public SubscriptionResult
	getResult(
		String		result_id )
	{
		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results = manager.loadResults( subs );

			return( results.get( result_id ));
		}
	}

	protected void
	updateResult(
		SubscriptionResultImpl 	result )
	{
		byte[]	key = result.getKey1();

		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				if ( Arrays.equals( results[i].getKey1(), key )){

					results[i] = result;

					changed	= true;
				}
			}

			if ( changed ){

				updateReadUnread( results );

				List<SubscriptionResultImpl>	new_unread_results = null;
								
				if ( !result.getRead()){
					
					new_unread_results= new ArrayList<>(1);
					
					new_unread_results.add( result );
				}
				
				manager.saveResults( subs, results, new_unread_results );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}

		if ( isAutoDownload() && !result.getRead() && !result.isDeleted()){

			manager.getScheduler().download( subs, result );
		}
	}


	@Override
	public void
	deleteResults(
		String[] result_ids )
	{
		ByteArrayHashMap<String> rids = new ByteArrayHashMap<>();

		for (int i=0;i<result_ids.length;i++){

			rids.put( Base32.decode( result_ids[i]), "" );
		}

		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( !result.isDeleted() && rids.containsKey( result.getKey1())){

					changed = true;

					result.deleteInternal();
				}
			}

			if ( changed ){

				updateReadUnread( results );

				manager.saveResults( subs, results, null );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}

	@Override
	public void
	deleteAllResults()
	{
		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( !result.isDeleted()){

					changed = true;

					result.deleteInternal();
				}
			}

			if ( changed ){

				updateReadUnread( results );

				manager.saveResults( subs, results, null );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}

	@Override
	public void
	markAllResultsRead()
	{
		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( !result.getRead()){

					changed = true;

					result.setReadInternal( true );
				}
			}

			if ( changed ){

				updateReadUnread( results );

				manager.saveResults( subs, results, null );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}

	@Override
	public void
	markAllResultsUnread()
	{
		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( result.getRead()){

					changed = true;

					result.setReadInternal( false );
				}
			}

			if ( changed ){

				updateReadUnread( results );

				List<SubscriptionResultImpl> new_unread_results = Arrays.asList( results );
				
				manager.saveResults( subs, results, new_unread_results );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}

	@Override
	public void
	markResults(
		String[] 		result_ids,
		boolean[]		reads )
	{
		ByteArrayHashMap<Boolean> rid_map = new ByteArrayHashMap<>();

		for (int i=0;i<result_ids.length;i++){

			rid_map.put( Base32.decode( result_ids[i]), Boolean.valueOf(reads[i]));
		}

		boolean	changed = false;

		List<SubscriptionResultImpl>	new_unread_results = new ArrayList<>( result_ids.length );

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( result.isDeleted()){

					continue;
				}

				Boolean	b_read = (Boolean)rid_map.get( result.getKey1());

				if ( b_read != null ){

					boolean	read = b_read.booleanValue();

					if ( result.getRead() != read ){

						changed = true;

						result.setReadInternal( read );

						if ( !read ){

							new_unread_results.add( result );
						}
					}
				}
			}

			if ( changed ){

				updateReadUnread( results );

				manager.saveResults( subs, results, new_unread_results );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}

		if ( isAutoDownload()){

			for (int i=0;i<new_unread_results.size();i++){

				manager.getScheduler().download( subs, (SubscriptionResult)new_unread_results.get(i));
			}
		}
	}

	protected void
	markResults(
		Set<String>		hashes,
		Set<String>		name_sizes )
	{
		boolean	changed = false;

		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			for (int i=0;i<results.length;i++){

				SubscriptionResultImpl result = results[i];

				if ( result.isDeleted()){

					continue;
				}

				if ( !result.getRead()){

					String	hash = result.getAssetHash();
					
					if ( hash != null && hashes.contains( hash )){

						changed = true;

						result.setReadInternal( true );
						
					}else{
						
						Map<Integer,Object>	properties = result.toPropertyMap();

						String 	name = (String)properties.get( SearchResult.PR_NAME );
						Long	size = (Long)properties.get( SearchResult.PR_SIZE );

						if ( name != null && size != null ){
							
							String ns = name + ":" + size;
							
							if ( name_sizes.contains( ns )){
								
								changed = true;

								result.setReadInternal( true );
							}
						}
					}
				}
			}

			if ( changed ){

				updateReadUnread( results );

				manager.saveResults( subs, results, null );
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}
	
	@Override
	public void
	reset()
	{
		synchronized( this ){

			LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

			SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

			if ( results.length > 0 ){

				results = new SubscriptionResultImpl[0];

				manager.saveResults( subs, results, null );
			}

			updateReadUnread( results );
		}

		last_error		= null;
		last_new_result	= 0;
		last_scan		= 0;

		saveConfig(SubscriptionListener.CR_RESULTS);
	}

	protected void
	checkMaxResults(
		int		max_results )
	{
		if ( max_results <= 0 ){

			return;
		}

		boolean	changed = false;

		synchronized( this ){

			if ((num_unread + num_read ) > max_results ){

				LinkedHashMap<String,SubscriptionResultImpl> results_map = manager.loadResults( subs );

				SubscriptionResultImpl[] results = results_map.values().toArray( new SubscriptionResultImpl[results_map.size()] );

				for (int i=0;i<results.length;i++){

					SubscriptionResultImpl r = results[i];

					if ( !r.isDeleted()){

						if ( r.getRead()){

							num_read--;

						}else{

							num_unread--;
						}

						r.deleteInternal();

						changed = true;

						if (( num_unread + num_read ) <= max_results ){

							break;
						}
					}
				}

				if ( changed ){

					manager.saveResults( subs, results, null );
				}
			}
		}

		if ( changed ){

			saveConfig(SubscriptionListener.CR_RESULTS);
		}
	}

	protected void
	updateReadUnread(
		SubscriptionResultImpl[]	results )
	{
		int	new_unread	= 0;
		int	new_read	= 0;

		for (int i=0;i<results.length;i++){

			SubscriptionResultImpl result = results[i];

			if ( !result.isDeleted()){

				if ( result.getRead()){

					new_read++;

				}else{

					new_unread++;
				}
			}
		}

		num_read	= new_read;
		num_unread	= new_unread;
	}

	protected boolean
	isAutoDownloadSupported()
	{
		return( auto_dl_supported );
	}

	protected void
	setFatalError(
		String		_error )
	{
		last_error		= _error;
		consec_fails	= 1024;
	}

	protected void
	setLastError(
		String		_last_error,
		boolean		_auth_failed )
	{
		last_error 		= _last_error;
		auth_failed		= _auth_failed;

		if ( last_error == null ){

			consec_fails = 0;

		}else{

			consec_fails++;
		}

		subs.fireChanged( SubscriptionListener.CR_METADATA );
	}

	@Override
	public String
	getLastError()
	{
		return( last_error );
	}

	@Override
	public boolean
	isAuthFail()
	{
		return( auth_failed );
	}

	@Override
	public int
	getConsecFails()
	{
		return( consec_fails );
	}

	@Override
	public boolean
	getDownloadWithReferer()
	{
		return( dl_with_ref );
	}

	@Override
	public void
	setDownloadWithReferer(
		boolean		b )
	{
		if ( b != dl_with_ref ){

			dl_with_ref = b;

			saveConfig(SubscriptionListener.CR_METADATA);
		}
	}

	protected void
	loadConfig()
	{
		Map	map = subs.getHistoryConfig();

		Long	l_enabled	= (Long)map.get( "enabled" );
		enabled				= l_enabled==null?true:l_enabled.longValue()==1;

		Long	l_auto_dl	= (Long)map.get( "auto_dl" );
		auto_dl				= l_auto_dl==null?false:l_auto_dl.longValue()==1;

		Long	l_last_scan = (Long)map.get( "last_scan" );
		last_scan			= l_last_scan==null?0:l_last_scan.longValue();

		Long	l_last_new 	= (Long)map.get( "last_new" );
		last_new_result		= l_last_new==null?0:l_last_new.longValue();

		Long	l_num_unread 	= (Long)map.get( "num_unread" );
		num_unread				= l_num_unread==null?0:l_num_unread.intValue();

		Long	l_num_read 	= (Long)map.get( "num_read" );
		num_read			= l_num_read==null?0:l_num_read.intValue();

			// migration - if we've already downloaded this feed then we default to being
			// enabled

		Long	l_auto_dl_s	= (Long)map.get( "auto_dl_supported" );
		auto_dl_supported	= l_auto_dl_s==null?(last_scan>0):l_auto_dl_s.longValue()==1;

		Long	l_dl_with_ref	= (Long)map.get( "dl_with_ref" );
		dl_with_ref	= l_dl_with_ref==null?true:l_dl_with_ref.longValue()==1;

		Long	l_interval_override	= (Long)map.get( "interval_override" );
		interval_override	= l_interval_override==null?0:l_interval_override.intValue();

		Long	l_max_results	= (Long)map.get( "max_results" );
		max_results				= l_max_results==null?-1:l_max_results.longValue();

		String  s_networks 		= MapUtils.getMapString(map, "nets", null );

		if ( s_networks != null ){
			networks = s_networks.split(",");
			for ( int i=0;i<networks.length;i++){
				networks[i] = AENetworkClassifier.internalise( networks[i] );
			}
		}

		Long	l_post_noto	= (Long)map.get( "post_noti" );
		post_notifications	= l_post_noto==null?false:l_post_noto.longValue()==1;

		Long l_max_age_secs = (Long)map.get( "max_age_secs" );
		
		max_age_secs = l_max_age_secs==null?-1:l_max_age_secs;
	}

	protected void
	saveConfig(
		int		reason )
	{
		Map<String,Object>	map = new HashMap<>();

		map.put( "enabled", new Long( enabled?1:0 ));
		map.put( "auto_dl", new Long( auto_dl?1:0 ));
		map.put( "auto_dl_supported", new Long( auto_dl_supported?1:0));
		map.put( "last_scan", new Long( last_scan ));
		map.put( "last_new", new Long( last_new_result ));
		map.put( "num_unread", new Long( num_unread ));
		map.put( "num_read", new Long( num_read ));
		map.put( "dl_with_ref", new Long( dl_with_ref?1:0 ));
		map.put( "max_results", new Long( max_results ));

		if ( interval_override > 0 ){
			map.put( "interval_override", new Long( interval_override ));
		}
		if ( networks != null ){
			String str = "";
			for ( String net: networks ){
				str += (str.length()==0?"":",") + net;
			}
			map.put( "nets", str );
		}
		if (post_notifications ){
			map.put( "post_noti", 1);
		}
		
		if ( max_age_secs > 0 ){
			map.put( "max_age_secs", max_age_secs );
		}
		
		subs.updateHistoryConfig( map, reason );
	}

	protected void
	log(
		String		str )
	{
		subs.log( "History: " + str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		subs.log( "History: " + str, e );
	}

	protected String
	getString()
	{
		return( "unread=" + num_unread + ",read=" + num_read+ ",last_err=" + last_error );
	}
}
