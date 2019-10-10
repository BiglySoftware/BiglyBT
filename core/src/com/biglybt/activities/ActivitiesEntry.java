/*
 * Created on Jan 28, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.activities;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.activities.LocalActivityManager.LocalActivityCallback;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableColumnSortObject;
import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.utils.ImageBytesDownloader;
import com.biglybt.ui.utils.ImageBytesDownloader.ImageDownloaderListener;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.util.MapUtils;
import com.biglybt.util.PlayUtils;

/**
 *
 * Comparable implementation sorts on timestamp.<P>
 * equals() implementation compares IDs
 *
 * @author TuxPaper
 * @created Jan 28, 2008
 *
 */
public class ActivitiesEntry
	implements TableColumnSortObject
{
	private String text;

	private String iconID;

	private String id;

	private long timestamp;

	private String typeID;

	private String assetHash;

	private String assetImageURL;

	private DownloadManager dm;

	public Object urlInfo;

	public TableColumnCore tableColumn;

	private byte[] imageBytes;

	private boolean showThumb = true;

	private String torrentName;

	private TOTorrent torrent;

	private boolean playable;

	private long readOn;

	private String[]			actions;
	private String				callback_class;
	private Map<String,String>	callback_data;
	private boolean				viewed;

	private GlobalManager gm = null;

	public ActivitiesEntry(long timestamp, String text, String typeID) {
		this.setText(text);
		this.timestamp = timestamp;
		this.setTypeID(typeID, true);
	}

	/**
	 *
	 */
	public ActivitiesEntry() {
		this.timestamp = SystemTime.getCurrentTime();
	}

	protected void
	updateFrom(
		ActivitiesEntry other )
	{
		text				= other.text;
		iconID				= other.iconID;
		id					= other.id;
		timestamp			= other.timestamp;
		typeID				= other.typeID;
		assetHash			= other.assetHash;
		assetImageURL		= other.assetImageURL;
		dm					= other.dm;
		urlInfo				= other.urlInfo;
		tableColumn			= other.tableColumn;
		imageBytes			= other.imageBytes;
		showThumb			= other.showThumb;
		torrentName			= other.torrentName;
		torrent				= other.torrent;
		playable			= other.playable;
		readOn				= 0; // other.readOn;
		actions				= other.actions;
		callback_class		= other.callback_class;
		callback_data		= other.callback_data;
		viewed				= false; // other.viewed;
	}

	/**
	 * @param platformEntry
	 */
	public void loadFromExternalMap(Map<?, ?> platformEntry) {
		timestamp = SystemTime.getCurrentTime()
				- MapUtils.getMapLong(platformEntry, "age-ms", 0);
		setIconID(MapUtils.getMapString(platformEntry, "icon-url",
				MapUtils.getMapString(platformEntry, "icon-id", null)));
		setTypeID(MapUtils.getMapString(platformEntry, "type-id", null), true);
		setAssetHash(MapUtils.getMapString(platformEntry, "related-asset-hash",
				null));
		setAssetImageURL(MapUtils.getMapString(platformEntry, "related-image-url",
				null));
		setTorrentName(MapUtils.getMapString(platformEntry, "related-asset-name",
				null));
		setReadOn(MapUtils.getMapLong(platformEntry, "readOn", 0));
		loadCommonFromMap(platformEntry);
	}

	public void loadFromInternalMap(Map<?, ?> map) {
		timestamp = MapUtils.getMapLong(map, "timestamp", 0);
		if (timestamp == 0) {
			timestamp = SystemTime.getCurrentTime();
		}
		setAssetHash(MapUtils.getMapString(map, "assetHash", null));
		setIconIDRaw(MapUtils.getMapString(map, "icon", null));
		setTypeID(MapUtils.getMapString(map, "typeID", null), true);
		setShowThumb(MapUtils.getMapLong(map, "showThumb", 1) == 1);
		setAssetImageURL(MapUtils.getMapString(map, "assetImageURL", null));
		setImageBytes(MapUtils.getMapByteArray(map, "imageBytes", null));
		setReadOn(MapUtils.getMapLong(map, "readOn", SystemTime.getCurrentTime()));
		setActions(MapUtils.getMapStringArray(map, "actions",null ));

		callback_class 	= MapUtils.getMapString( map, "cb_class", null );
		callback_data	= (Map<String,String>)BDecoder.decodeStrings((Map)map.get( "cb_data" ));

		viewed = MapUtils.getMapBoolean(map, "viewed", false);

		loadCommonFromMap(map);
	}

	public void loadCommonFromMap(Map<?, ?> map) {
		if (!playable) {
			setPlayable(MapUtils.getMapBoolean(map, "playable", false));
		}
		setID(MapUtils.getMapString(map, "id", null));
		setText(MapUtils.getMapString(map, "text", null));
		Map<?, ?> torrentMap = MapUtils.getMapMap(map, "torrent", null);
		if (torrentMap != null) {
			TOTorrent torrent = null;
			try {
				torrent = TOTorrentFactory.deserialiseFromMap(torrentMap);
				setTorrent(torrent);
			} catch (TOTorrentException e) {
			}
		}
		if (dm == null && torrentName == null) {
			setTorrentName(MapUtils.getMapString(map, "torrent-name", null));
		}
	}

	public boolean equals(Object obj) {
		if ((obj instanceof ActivitiesEntry) && id != null) {
			return id.equals(((ActivitiesEntry) obj).id);
		}
		return super.equals(obj);
	}

	public int
	hashCode()
	{
		if ( id == null ){
			return( 0 );
		}
		return( id.hashCode());
	}

	@Override
	public int compareTo(Object obj) {
		if (obj instanceof ActivitiesEntry) {
			ActivitiesEntry otherEntry = (ActivitiesEntry) obj;

			long x = (timestamp - otherEntry.timestamp);
			return x == 0 ? 0 : x > 0 ? 1 : -1;
		}
		// we are bigger
		return 1;
	}

	public void setAssetImageURL(final String url) {
		if (url == null && assetImageURL == null) {
			return;
		}
		if (url == null || url.length() == 0) {
			assetImageURL = null;
			ActivitiesManager.triggerEntryChanged(ActivitiesEntry.this);
			return;
		}
		if (url.equals(assetImageURL)) {
			return;
		}

		assetImageURL = url;
		ImageBytesDownloader.loadImage(url, new ImageDownloaderListener() {
			@Override
			public void imageDownloaded(byte[] image) {
				setImageBytes(image);
				ActivitiesManager.triggerEntryChanged(ActivitiesEntry.this);
			}
		});
	}

	public String getAssetImageURL() {
		return assetImageURL;
	}

	public Map<String, Object> toDeletedMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("timestamp", new Long(timestamp));
		map.put("id", id);
		return map;
	}

	public void
	setActions(
		String[]		_actions )
	{
		actions = _actions;
	}

	public String[]
	getActions()
	{
		return( actions==null?new String[0]:actions );
	}

	public boolean
	allowReAdd()
	{
		return( callback_data != null && callback_data.containsKey( "allowReAdd" ));
	}

	public void
	setCallback(
		Class<? extends LocalActivityCallback>	_callback,
		Map<String,String>												_callback_data )
	{
		callback_class	= _callback==null?null:_callback.getName();
		callback_data	= _callback_data;
	}

	public void
	invokeCallback(
		String		action )
	{
		try{
			Class<? extends LocalActivityCallback> cb = (Class<? extends LocalActivityCallback>)getClass().forName( callback_class );

			cb.newInstance().actionSelected( action, callback_data );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("timestamp", new Long(timestamp));
		if (assetHash != null) {
			map.put("assetHash", assetHash);
		}
		map.put("icon", getIconID());
		map.put("id", id);
		map.put("text", getText());
		map.put("typeID", getTypeID());
		map.put("assetImageURL", assetImageURL);
		map.put("showThumb", new Long(getShowThumb() ? 1 : 0));
		if (imageBytes != null) {
			map.put("imageBytes", imageBytes);
		} else if (dm != null) {
			byte[] thumbnail = PlatformTorrentUtils.getContentThumbnail(dm.getTorrent());
			if (thumbnail != null) {
				map.put("imageBytes", thumbnail);
			}
		}

		if (torrent != null && (dm == null || assetHash == null)) {
			try {
				// make a copy of the torrent

				Map torrent_map = torrent.serialiseToMap();

				TOTorrent torrent_to_send = TOTorrentFactory.deserialiseFromMap( torrent_map );

				Map<?, ?>	vuze_map = (Map<?, ?>)torrent_map.get( "vuze" );

				// remove any non-standard stuff (e.g. resume data)

				torrent_to_send.removeAdditionalProperties();

				torrent_map = torrent_to_send.serialiseToMap();

				if ( vuze_map != null ){

					torrent_map.put( "vuze", vuze_map );
				}

				map.put("torrent", torrent_map );
			} catch (TOTorrentException e) {
				Debug.outNoStack("VuzeActivityEntry.toMap: " + e.toString());
			}
		}
		if (torrentName != null) {
			map.put("torrent-name", torrentName);
		}

		if (playable) {
			map.put("playable", new Long(playable ? 1 : 0));
		}

		map.put("readOn", new Long(readOn));

		if ( actions != null && actions.length > 0 ){
			List<String>	list = Arrays.asList( actions );
			map.put( "actions", list );
		}
		if ( callback_class != null ){
			map.put( "cb_class", callback_class );
		}
		if ( callback_data != null ){
			map.put( "cb_data", callback_data );
		}

		map.put( "viewed", viewed?1:0 );

		return map;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		if (this.timestamp == timestamp) {
			return;
		}
		this.timestamp = timestamp;
		if (tableColumn != null) {
			tableColumn.setLastSortValueChange(SystemTime.getCurrentTime());
		}
	}

	/**
	 * @param typeID the typeID to set
	 */
	public void setTypeID(String typeID, boolean autoSetIcon) {
		this.typeID = typeID;
		if (getIconID() == null && typeID != null) {
			setIconID("image.vuze-entry." + typeID.toLowerCase());
		}
	}

	/**
	 * @return the typeID
	 */
	public String getTypeID() {
		return typeID;
	}

	/**
	 * @param iconID the iconID to set
	 */
	public void setIconID(String iconID) {
		if (iconID != null && !iconID.contains("image.")
				&& !iconID.startsWith("http")) {
			iconID = "image.vuze-entry." + iconID;
		}
		this.iconID = iconID;
	}

	public void setIconIDRaw(String iconID) {
		this.iconID = iconID;
	}

	/**
	 * @return the iconID
	 */
	public String getIconID() {
		return iconID;
	}

	/**
	 * @param text the text to set
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * @return the text
	 */
	public String getText() {
		return text;
	}

	/**
	 * @param id the id to set
	 */
	public void setID(String id) {
		this.id = id;
	}

	/**
	 * @return the id
	 */
	public String getID() {
		return id;
	}

	/**
	 * @param assetHash the assetHash to set
	 */
	public void setAssetHash(String assetHash) {
		this.assetHash = assetHash;
		if (assetHash != null) {
			try {
  			if (gm == null) {
  				gm = CoreFactory.getSingleton().getGlobalManager();
  			}
  			setDownloadManager(gm.getDownloadManager(new HashWrapper(
  					Base32.decode(assetHash))));
			} catch (Exception e) {
				setDownloadManager(null);
				Debug.out("Core not ready", e);
			}
		} else {
			setDownloadManager(null);
		}
	}

	/**
	 * @return the assetHash
	 */
	public String getAssetHash() {
		return assetHash;
	}

	/**
	 * @param dm the dm to set
	 */
	public void setDownloadManager(DownloadManager dm) {
		if (this.dm == dm) {
			return;
		}
		if (gm == null) {
			try {
				gm = CoreFactory.getSingleton().getGlobalManager();
			} catch (Exception e) {
				// ignore
			}
		}

		this.dm = dm;
		if (dm != null) {
			setTorrent(dm.getTorrent());
		}
	}

	/**
	 * @return the dm
	 */
	public DownloadManager getDownloadManger() {
		if (gm != null && !gm.contains(dm)) {
			setDownloadManager(null);
			return null;
		}
		return dm;
	}

	/**
	 * @param imageBytes the imageBytes to set
	 */
	public void setImageBytes(byte[] imageBytes) {
		this.imageBytes = imageBytes;
	}

	/**
	 * @return the imageBytes
	 */
	public byte[] getImageBytes() {
		return imageBytes;
	}

	/**
	 * @param showThumb the showThumb to set
	 */
	public void setShowThumb(boolean showThumb) {
		this.showThumb = showThumb;
	}

	/**
	 * @return the showThumb
	 */
	public boolean getShowThumb() {
		return showThumb;
	}

	/**
	 * Independant for {@link #getDownloadManger()}.  This will be written to
	 * the map.
	 *
	 * @return Only returns TOTorrent set via {@link #setTorrent(TOTorrent)}
	 *
	 * @since 3.0.5.3
	 */
	public TOTorrent getTorrent() {
		return torrent;
	}

	/**
	 * Not needed if you {@link #setDownloadManager(DownloadManager)}. This will
	 * be written the map.
	 *
	 * @param torrent
	 *
	 * @since 3.0.5.3
	 */
	public void setTorrent(TOTorrent torrent) {
		this.torrent = torrent;

		try {
			assetHash = torrent.getHashWrapper().toBase32String();
		} catch (Exception e) {
		}
	}

	public String getTorrentName() {
		if (torrentName == null) {
			if (dm != null) {
				return PlatformTorrentUtils.getContentTitle2(dm);
			}
			if (torrent != null) {
				return TorrentUtils.getLocalisedName(torrent);
			}
		}
		return torrentName;
	}

	public void setTorrentName(String torrentName) {
		this.torrentName = torrentName;
	}

	public SelectedContent createSelectedContentObject()
			throws Exception {

		SelectedContent sc = new SelectedContent( "activity" );
		if (assetHash == null) {
			// Contains no content
			return sc;
		}

		dm = getDownloadManger();
		if (dm != null) {
			sc.setDisplayName(PlatformTorrentUtils.getContentTitle2(dm));
			sc.setDownloadManager(dm);
			return sc;
		}else{
			if ( torrent != null ){
				sc.setTorrent( torrent );
			}
		}

		sc.setDisplayName(getTorrentName());
		if (sc.getDisplayName() == null) {
			TOTorrent torrent = getTorrent();
			if (torrent != null) {
				sc.setDisplayName(TorrentUtils.getLocalisedName(torrent));
				sc.setHash(torrent.getHashWrapper().toBase32String());
			}
		}

		if (sc.getHash() == null ){

			if ( assetHash != null ){

				sc.setHash(assetHash);
			}
		}

		return sc;

	}

	public boolean isPlayable( boolean blocking) {
		// our variable is an override
		if (playable)  {
			return true;
		}
		// use torrent so we don't recurse
		return PlayUtils.canPlayDS(DataSourceUtils.getTorrent(this), -1, blocking);
	}

	public void setPlayable(boolean playable) {
		this.playable = playable;
	}

	public long getReadOn() {
		return readOn;
	}

	public void setReadOn(long readOn) {
		if (this.readOn == readOn) {
			return;
		}
		this.readOn = readOn;
		ActivitiesManager.triggerEntryChanged(ActivitiesEntry.this);
	}

	public void setRead(boolean read) {
		long now = SystemTime.getCurrentTime();
		if (read) {
			setReadOn(now);
		} else {
			setReadOn(now * -1);
		}
	}

	public boolean isRead() {
		return readOn > 0;
	}

	public void
	setViewed()
	{
		if ( !viewed ){
			viewed = true;
			ActivitiesManager.triggerEntryChanged(ActivitiesEntry.this);
		}
	}

	public boolean
	getViewed()
	{
		return( viewed );
	}

	public boolean canFlipRead() {
		long ofs = SystemTime.getOffsetTime(-300);
		if (readOn > 0) {
			return ofs > readOn;
		} else {
			return ofs > (-1 * readOn);
		}
	}
}
