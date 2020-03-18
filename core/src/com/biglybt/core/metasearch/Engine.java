/*
 * Created on May 6, 2008
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

package com.biglybt.core.metasearch;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.vuzefile.VuzeFile;


public interface
Engine
{
	/**
	 * AZ_VERSION:
	 * 1: Original
	 * 2: field value substitution in JSON engine type using ${FIELD_ID}
	 * 3: field value substitution in REGEX engine type using ${FIELD_NO}
	 * 4: JSON engine now supports [x] in Entry Path, where x is the array index
	 * 5: JSON and REGEX support functions in ${FIELD_ID} or ${FIELD_NO}, such as
	 *      ${1,ucase}
	 *      ${1,lcase}
	 *      ${1,urldecode}
	 *      ${1,replace,fromRegex,toText}
	 *      ${1,lcase+urldecode}
	 */
	public static final int	AZ_VERSION		= 5;

	public static final Object	VUZE_FILE_COMPONENT_ENGINE_KEY = new Object();

		// Don't change these values as they get persisted

	public static final int FIELD_NAME 			= 1;
	public static final int FIELD_DATE 			= 2;
	public static final int FIELD_SIZE 			= 3;
	public static final int FIELD_PEERS 		= 4;
	public static final int FIELD_SEEDS 		= 5;
	public static final int FIELD_CATEGORY 		= 6;
	public static final int FIELD_COMMENTS 		= 7;
	public static final int FIELD_CONTENT_TYPE 	= 8;
	public static final int FIELD_DISCARD	 	= 9;
	public static final int FIELD_VOTES		 	= 10;
	public static final int FIELD_SUPERSEEDS 	= 11;
	public static final int FIELD_PRIVATE	 	= 12;
	public static final int FIELD_DRMKEY        = 13;
	public static final int FIELD_VOTES_DOWN    = 14;


	public static final int FIELD_TORRENTLINK 		= 102;
	public static final int FIELD_CDPLINK 			= 103;
	public static final int FIELD_PLAYLINK 			= 104;
	public static final int FIELD_DOWNLOADBTNLINK 	= 105;

	public static final int FIELD_HASH			= 200;
	public static final int FIELD_RANK    		= 201;
	public static final int FIELD_ASSET_DATE	= 202;

	public static final int[] FIELD_IDS = {
		FIELD_NAME, FIELD_DATE, FIELD_SIZE, FIELD_PEERS, FIELD_SEEDS, FIELD_CATEGORY,
		FIELD_COMMENTS, FIELD_CONTENT_TYPE, FIELD_DISCARD,
		FIELD_TORRENTLINK, FIELD_CDPLINK, FIELD_PLAYLINK,FIELD_DOWNLOADBTNLINK, FIELD_VOTES, FIELD_SUPERSEEDS,
		FIELD_PRIVATE, FIELD_DRMKEY, FIELD_VOTES_DOWN, FIELD_HASH, FIELD_RANK, FIELD_ASSET_DATE
	};

	public static final String[] FIELD_NAMES = {
		"TITLE", "DATE", "SIZE", "PEERS", "SEEDS", "CAT",
		"COMMENTS", "CONTENT_TYPE", "DISCARD",
		"TORRENT", "CDP", "PLAY","DLBTN", "VOTES", "XSEEDS",
		"PRIVATE", "DRMKEY", "VOTESDOWN", "HASH", "RANK", "ASSETDATE"
	};

	public static final int ENGINE_TYPE_REGEX		= 1;
	public static final int ENGINE_TYPE_JSON		= 2;
	public static final int ENGINE_TYPE_PLUGIN		= 3;
	public static final int ENGINE_TYPE_RSS			= 4;


	public static final int	ENGINE_SOURCE_UNKNOWN				= 0;
	public static final int	ENGINE_SOURCE_VUZE					= 1;
	public static final int	ENGINE_SOURCE_LOCAL					= 2;
	public static final int	ENGINE_SOURCE_RSS					= 3;

	public static final int	SEL_STATE_DESELECTED			= 0;
	public static final int	SEL_STATE_AUTO_SELECTED			= 1;
	public static final int	SEL_STATE_MANUAL_SELECTED		= 2;
	public static final int	SEL_STATE_FORCE_DESELECTED		= 3;

	public static final int AUTO_DL_SUPPORTED_UNKNOWN		= 0;
	public static final int AUTO_DL_SUPPORTED_YES			= 1;
	public static final int AUTO_DL_SUPPORTED_NO			= 2;

		/**
		 * don't change these as they are externalised
		 */
	public static final String[] ENGINE_SOURCE_STRS = { "unknown","vuze","local","rss","unused" };
	public static final String[] SEL_STATE_STRINGS	= { "no", "auto", "manual", "force_no" };
	public static final String[] ENGINE_TYPE_STRS 	= { "unknown","regexp","json", "plugin" };

	public static final String	SC_SOURCE			= "azsrc";
	public static final String	SC_FORCE_FULL		= "force_full";	// ignore if-modified stuff and force a full search
	public static final String	SC_BATCH_PERIOD		= "batch_millis";
	public static final String	SC_REMOVE_DUP_HASH	= "remove_dup_hash";


	public static final String	CT_VIDEO	= "video";
	public static final String	CT_AUDIO	= "audio";
	public static final String	CT_GAME		= "game";

	public int getType();

	public Result[]
	search(
		SearchParameter[] 	searchParameters,
		Map					context,
		int					desired_max_matches,
		int					absolute_max_matches,
		String				headers,
		ResultListener		listener )

		throws SearchException;

	public String
	getName();

	public String
	getNameEx();

	public long
	getId();

	public String
	getUID();

	public int
	getVersion();

	public long
	getLastUpdated();

	public String
	getIcon();

	public String
	getDownloadLinkCSS();

	public boolean
	isActive();

	public boolean
	isMine();

	public boolean
	isPublic();

	public void
	setMine(
		boolean		mine );

	public int
	getSelectionState();

	public void
	setSelectionState(
		int			state );

	public void
	recordSelectionState();

	public void
	checkSelectionStateRecorded();

	public int
	getSource();

	public void
	setSource(
		int		source );

	public String
	getReferer();

	public float
	getRankBias();

	public void
	setRankBias(
		float		bias );

	public void
	setPreferredDelta(
		float	delta  );

	public float
	getPreferredWeighting();

	public float
	applyRankBias(
		float	rank );

	public boolean
	supportsField(
		int		field_id );

	public boolean
	supportsContext(
		String	context_key );

	public boolean
	isShareable();

	public boolean
	isAnonymous();

	public boolean
	isAuthenticated();

		/**
		 * @return one of AUTO_DL constants above
		 */

	public int
	getAutoDownloadSupported();

	public int
	getAZVersion();

	public void
	addPotentialAssociation(
		String		key );

	public Subscription
	getSubscription();

	public Map<String,Object>
	exportToBencodedMap()

		throws IOException;

	public Map<String,Object>
	exportToBencodedMap(
		boolean		generic )

		throws IOException;

	public String
	exportToJSONString()

		throws IOException;

	public void
	exportToVuzeFile(
		File	target )

		throws IOException;

	public VuzeFile
	exportToVuzeFile()

		throws IOException;

		/**
		 * Tests for sameness in terms of function (ignores id, selection state etc)
		 * @param other
		 * @return
		 */

	public boolean
	sameLogicAs(
		Engine	other );

		/**
		 * resets to initial state (e.g. if the engine has state pertaining to what has/hasn't been downloaded
		 * such as etags then this will be cleared)
		 */

	public void
	reset();

	public void
	delete();

	public String
	getString();
}
