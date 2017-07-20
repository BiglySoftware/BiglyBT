/*
 * File    : DownloadScrapeResultImpl.java
 * Created : 13-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.download;

/**
 * @author parg
 *
 */

import java.net.URL;

import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadScrapeResult;

public class
DownloadScrapeResultImpl
	implements DownloadScrapeResult
{
	protected DownloadImpl				download;
	protected TRTrackerScraperResponse	response;

	protected
	DownloadScrapeResultImpl(
		DownloadImpl				_download,
		TRTrackerScraperResponse	_response )
	{
		download	= _download;
		response	= _response;
	}

	protected void
	setContent(
		TRTrackerScraperResponse	_response )
	{
		response = _response;
	}

	@Override
	public Download
	getDownload()
	{
		return( download );
	}

	@Override
	public int
	getResponseType()
	{
		if ( response != null && response.isValid()){

			return( RT_SUCCESS );

		}else{

			return( RT_ERROR );
		}
	}

	@Override
	public int
	getSeedCount()
	{
		return( response==null?-1:response.getSeeds());
	}

	@Override
	public int
	getNonSeedCount()
	{
		return( response==null?-1:response.getPeers());
	}

	@Override
	public long
	getScrapeStartTime()
	{
		return( response==null?-1:response.getScrapeStartTime());
	}

	@Override
	public void
	setNextScrapeStartTime(
		long nextScrapeStartTime)
	{
		TRTrackerScraperResponse	current_response = getCurrentResponse();

		if (current_response != null){
			current_response.setNextScrapeStartTime(nextScrapeStartTime);
		}
	}

	@Override
	public long
	getNextScrapeStartTime()
	{
			// some weirdness going on here as we're not reporting the current values correctly
			// so quick hack to base this on the current

		TRTrackerScraperResponse	current_response = getCurrentResponse();

		return( current_response == null?-1:current_response.getNextScrapeStartTime());
	}

	@Override
	public String
	getStatus()
	{
		if ( response != null ){
			return( response.getStatusString());
		}

		return("");
	}

	@Override
	public URL
	getURL()
	{
		if (response != null) {
			return( response.getURL());
		}
		return null;
	}

	protected TRTrackerScraperResponse
	getCurrentResponse()
	{
		TRTrackerScraperResponse	current = download.getDownload().getTrackerScrapeResponse();

		if ( current == null ){

			current	= response;
		}

		return( current );
	}
}
