/*
 * Created on 12-Jan-2005
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

package com.biglybt.core.dht.router.impl;

import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.router.DHTRouterContact;
import com.biglybt.core.dht.router.DHTRouterContactAttachment;
import com.biglybt.core.util.SystemTime;

/**
 * @author parg
 *
 */

public class
DHTRouterContactImpl
	implements DHTRouterContact
{
	private final byte[]							node_id;
	private DHTRouterContactAttachment		attachment;

	private boolean		has_been_alive;
	private boolean		ping_outstanding;
	private int			fail_count;
	private long		first_alive_time;
	private long		first_fail_or_last_alive_time;
	private long		last_added_time;

	private boolean		is_bucket_entry;

	protected
	DHTRouterContactImpl(
		byte[]							_node_id,
		DHTRouterContactAttachment		_attachment,
		boolean							_known_to_be_alive )
	{
		node_id			= _node_id;
		attachment		= _attachment;
		
		if ( _known_to_be_alive ){
			
			setAlive();
		}

		if ( attachment != null ){

			attachment.setRouterContact( this );
		}

		is_bucket_entry = false;
	}

	@Override
	public byte[]
	getID()
	{
		return(node_id );
	}

	@Override
	public DHTRouterContactAttachment
	getAttachment()
	{
		return( attachment );
	}

	protected void
	setAttachment(
		DHTRouterContactAttachment	_attachment )
	{
		attachment	= _attachment;
	}

	public void
	setAlive()
	{
		fail_count							= 0;
		first_fail_or_last_alive_time		= SystemTime.getCurrentTime();
		has_been_alive						= true;

		if ( first_alive_time == 0 ){

			first_alive_time = first_fail_or_last_alive_time;
		}
	}

	@Override
	public boolean
	hasBeenAlive()
	{
		return( has_been_alive );
	}

	@Override
	public boolean
	isAlive()
	{
		return( has_been_alive && fail_count == 0 );
	}

	@Override
	public boolean
	isFailing()
	{
		return( fail_count > 0 );
	}

	protected int
	getFailCount()
	{
		return( fail_count );
	}

	@Override
	public long
	getTimeAlive()
	{
		if ( fail_count > 0 || first_alive_time == 0 ){

			return( 0 );
		}

		return( SystemTime.getCurrentTime() - first_alive_time );
	}

	protected boolean
	setFailed()
	{
		fail_count++;

		if ( fail_count == 1 ){

			first_fail_or_last_alive_time = SystemTime.getCurrentTime();
		}

		return( hasFailed());
	}

	protected boolean
	hasFailed()
	{
		if ( has_been_alive ){

			return( fail_count >= attachment.getMaxFailForLiveCount());

		}else{

			return( fail_count >= attachment.getMaxFailForUnknownCount());
		}
	}

	protected long
	getFirstFailTime()
	{
		return( fail_count==0?0:first_fail_or_last_alive_time );
	}

	protected long
	getLastAliveTime()
	{
		return( fail_count==0?first_fail_or_last_alive_time:0 );
	}

	protected long
	getFirstFailOrLastAliveTime()
	{
		return( first_fail_or_last_alive_time );
	}

	protected long
	getFirstAliveTime()
	{
		return( first_alive_time );
	}

	protected long
	getLastAddedTime()
	{
		return( last_added_time );
	}

	protected void
	setLastAddedTime(
		long	l )
	{
		last_added_time	= l;
	}

	protected void
	setPingOutstanding(
		boolean	b )
	{
		ping_outstanding = b;
	}

	protected boolean
	getPingOutstanding()
	{
		return( ping_outstanding );
	}

	@Override
	public String
	getString()
	{
		return( DHTLog.getString2(node_id) + "[hba=" + (has_been_alive?"Y":"N" ) +
				",bad=" + fail_count +
				",OK=" + getTimeAlive() + "]");
	}

	protected void
	getString(
		StringBuilder sb )
	{
		sb.append( DHTLog.getString2(node_id));
		sb.append( "[hba=" );
		sb.append( has_been_alive?"Y":"N" );
		sb.append( ",bad=" );
		sb.append( fail_count );
		sb.append( ",OK=" );
		sb.append( getTimeAlive());
		sb.append( "]");
	}

	@Override
	public boolean isBucketEntry() {
		return is_bucket_entry;
	}

	public void setBucketEntry() {
		is_bucket_entry = true;
	}

	@Override
	public boolean isReplacement() {
		return !is_bucket_entry;
	}

	public void setReplacement() {
		is_bucket_entry = false;
	}
}
