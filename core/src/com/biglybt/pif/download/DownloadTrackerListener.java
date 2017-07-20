/*
 * File    : DownloadTrackerListener.java
 * Created : 11-Jan-2004
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

package com.biglybt.pif.download;

/**
 * A listener that will be informed when the latest announce/scrape results
 * change. See {@link com.biglybt.pif.download#addTrackerListener}
 *
 * @author parg
 * @author TuxPaper 2005/Oct/01: JavaDocs & reformat to Java Conventions w/Tabs
 */
public interface DownloadTrackerListener {
	/**
	 * A scrape result has been returned from a tracker
	 *
	 * @param result Information about the scrape
	 *
	 * @since 2.0.7.0
	 *
	 * @note If an announce result is returned from the tracker contains
	 *        seed and non-seed (peer) counts, a new DownloadScrapeResult will be
	 *        created with the new information, and a scrapeResult will be
	 *        triggered.
	 *        <p>
	 *        The DownloadScrapeResult isn't always information from the currently
	 *        selected tracker.  Compare result.getURL() with
	 *        getDownload().getTorrent().getAnnounceURL() to determine if it's
	 *        from the currently selected tracker.
	 */
	public void scrapeResult(DownloadScrapeResult result);

	/**
	 * An announce result has been returned from the tracker
	 *
	 * @param result Information about the announce
	 *
	 * @since 2.0.7.0
	 */
	public void announceResult(DownloadAnnounceResult result);
}
