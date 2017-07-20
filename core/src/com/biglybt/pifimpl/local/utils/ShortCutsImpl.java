/*
 * Created on 10-May-2004
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

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.utils.ShortCuts;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;

public class
ShortCutsImpl
	implements ShortCuts
{
	protected PluginInterface pi;

	public
	ShortCutsImpl(
		PluginInterface		_pi )
	{
		pi		= _pi;
	}

	@Override
	public DownloadStats
	getDownloadStats(
		byte[]		hash )

		throws DownloadException
	{
		return( getDownload(hash).getStats());
	}

	@Override
	public void
	restartDownload(
		byte[]		hash )

		throws DownloadException
	{
		getDownload(hash).restart();
	}

	@Override
	public void
	stopDownload(
		byte[]		hash )

		throws DownloadException
	{
		getDownload(hash).stop();
	}

	@Override
	public void
	removeDownload(
		byte[]		hash )

		throws DownloadException, DownloadRemovalVetoException
	{
		getDownload(hash).remove();
	}

	@Override
	public Download
	getDownload(
		byte[]		hash )

		throws DownloadException
	{
		Download	dl = ((DownloadManagerImpl)pi.getDownloadManager()).getDownload( hash );

		if ( dl == null ){

			throw( new DownloadException("Torrent not found" ));
		}

		return( dl );
	}
}
