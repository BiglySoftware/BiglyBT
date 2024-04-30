/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.download;

public interface 
DownloadManagerOptionsHandler
{
	public String
	getName();
	
	public int
	getUploadRateLimitBytesPerSecond();
	
	public void
	setUploadRateLimitBytesPerSecond(
		int		limit );
	
	public int
	getDownloadRateLimitBytesPerSecond();
	
	public void
	setDownloadRateLimitBytesPerSecond(
		int		limit );

	public int
	getIntParameter(
		String		name );
	
	public void
	setIntParameter(
		String		key,
		int			value );
	
	public boolean
	getBooleanParameter(
		String		name );
	
	public void
	setBooleanParameter(
		String		key,
		boolean		value );
	
	public void
	setParameterDefault(
		String		key );
	
	public DownloadManager
	getDownloadManager();
	
	public void
	addListener(
		ParameterChangeListener	listener );
	
	public void
	removeListener(
		ParameterChangeListener	listener );
	
	public interface
	ParameterChangeListener
	{
		public void
		parameterChanged(
			DownloadManagerOptionsHandler		handler );
	}
}
