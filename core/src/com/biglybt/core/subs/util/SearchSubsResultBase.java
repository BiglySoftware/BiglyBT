/*
 * Created on Dec 8, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.subs.util;


public interface
SearchSubsResultBase
{
	public String
	getName();

	public byte[]
	getHash();

	public int
	getContentType();

	public long
	getSize();

	public int
	getNbSeeds();
	
	public String
	getSeedsPeers();

	public long
	getSeedsPeersSortValue();

	public String
	getVotesComments();

	public long
	getVotesCommentsSortValue();

	public int
	getRank();

	public String
	getTorrentLink();

	public String
	getDetailsLink();

	public String
	getCategory();
	
	public String[]
	getTags();

	public long
	getTime();

	public long
	getAssetDate();
	
	public boolean
	getRead();

	public void
	setRead(
		boolean		read );

	public void
	setUserData(
		Object	key,
		Object	data );

	public Object
	getUserData(
		Object	key );
}
