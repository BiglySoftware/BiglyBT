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

import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import org.apache.commons.lang.Entities;
import org.json.simple.JSONObject;

import com.biglybt.core.metasearch.utils.MomentsAgoDateFormatter;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SystemTime;

public abstract class 
Result
	implements FilterableResult
{
	private static final String HTML_TAGS = "(\\<(/?[^\\>]+)\\>)" ;
	private static final String DUPLICATE_SPACES = "\\s{2,}";

	private final Engine		engine;
	private final long			time_created_secs = SystemTime.getCurrentTime()/1000;

	public abstract Date getPublishedDate();
	public abstract Date getAssetDate();
	
	public abstract String getCategory();
	public abstract void setCategory(String category);

	public abstract String getContentType();
	public abstract void setContentType(String contentType);

	public abstract String getName();
	public abstract long getSize();
	public abstract int getNbPeers();
	public abstract int getNbSeeds();
	public abstract int getNbSuperSeeds();
	public abstract int getNbCompleted();
	public abstract int getComments();
	public abstract int getVotes();
	public abstract int getVotesDown();
	public abstract boolean isPrivate();

	public abstract String getDRMKey();

	//Links
	public abstract String getDownloadLink();
	public abstract String getTorrentLink();
	public abstract String getDownloadButtonLink();
	public abstract String getCDPLink();
	public abstract String getPlayLink();
	public abstract float getAccuracy();  // 0.0 -> 1.0 and -1 if not supported


	public abstract String getSearchQuery();

	public abstract String getUID();
	public abstract String getHash();

	protected
	Result(
		Engine		_engine )
	{
		engine	= _engine;
	}

	public Engine
	getEngine()
	{
		return( engine );
	}

	public String toString() {
		return getName() + " : " + getNbSeeds() + " s, " + getNbPeers() + "p, "  ;
	}


	/*
	public String getNameHTML() {
		if(getName() != null) {
			return( getName());
			//return( XUXmlWriter.escapeXML( getName()));
			//return Entities.XML.escape(getName());
		}
		return null;
	}

	public String getCategoryHTML() {
		if(getCategory() != null) {
			return( getCategory());
			//return Entities.XML.escape(getCategory());
		}
		return null;
	}
	*/

	/**
	 *
	 * @return a value between 0 and 1 representing the rank of the result
	 */
	public float getRank() {

		int seeds = getNbSeeds();
		int peers = getNbPeers();

		if ( seeds < 0 ){
			seeds = 0;
		}

		if ( peers < 0 ){
			peers = 0;
		}

		int totalVirtualPeers = 3 * seeds + peers + 2;

		int superSeeds = getNbSuperSeeds();
		if(superSeeds > 0) {
			totalVirtualPeers  += 50 * superSeeds;
		}

		int votes = getVotes();
		if(votes > 0) {
			if(votes > 50) {
				votes = 50;
			}
			totalVirtualPeers += 5 * votes;
		}

		int votesDown = getVotesDown();
		if(votesDown > 0) {
			totalVirtualPeers -= 200 * votesDown;
		}

		if(totalVirtualPeers < 2) totalVirtualPeers = 2;

		float rank = (float) (Math.log(totalVirtualPeers)/Math.log(10)) / 5f;

		if(rank > 2f) rank = 2f;

		if(isPrivate()) {
			rank /= 2;
		}

		String queryString = getSearchQuery();
		String name = getName();
		if(queryString != null && name != null) {
			name = name.toLowerCase( Locale.ENGLISH );

			String	token = "";

			List<String>	tokens = new ArrayList<>();

			char[] chars = queryString.toCharArray();

			for ( char c: chars ){

				if ( Character.isLetterOrDigit( c )){

					token += String.valueOf(c).toLowerCase( Locale.ENGLISH );

				}else{

					if ( token.length() > 0 ){

						tokens.add( token );

						token = "";
					}
				}
			}

			if ( token.length() > 0 ){

				tokens.add( token );
			}

			for ( String s: tokens ){
				if( !name.contains( s )){
					rank /= 2;
				}
			}
		}

		rank = applyRankBias( rank );

		return rank;
	}

	protected float
	applyRankBias(
		float	_rank )
	{
		float rank = engine.applyRankBias( _rank );

		/*
		if ( rank != _rank ){

			System.out.println( "bias applied for " + engine.getName() + ": " + _rank + "-> " + rank );
		}
		*/

		return( rank );
	}

	public Map toJSONMap() {
		Map object = new JSONObject();

		object.put( "tf", "" + time_created_secs );

		Date pub_date = this.getPublishedDate();
		if ( pub_date == null ){
			object.put("d", "unknown");
			object.put("ts", "0");
		}else{
			try {
				object.put("d", MomentsAgoDateFormatter.getMomentsAgoString(pub_date));
				object.put("ts", "" + pub_date.getTime());
			} catch(Exception e) {
				object.put("d", "unknown");
				object.put("ts", "0");
			}
		}

		Date ad = this.getAssetDate();
		if ( ad != null ){
			object.put( "ad", String.valueOf( ad.getTime()));
		}
		
		object.put("c", this.getCategory());
		
		String[] tags = this.getTags();
		if ( tags != null && tags.length > 0 ){
			object.put( "tgs", Arrays.asList( tags ));
		}
		
		object.put("n",this.getName());

		int	super_seeds = getNbSuperSeeds();
		int	seeds		= getNbSeeds();

		int	seed_total = -1;

		if ( super_seeds > 0 ){

			seed_total = 10*super_seeds + new Random().nextInt(10);
		}

		if ( seeds > 0 ){

			if ( seed_total == -1 ){

				seed_total = 0;
			}

			seed_total += seeds;
		}

		object.put("s","" + seed_total);

		if(this.getNbPeers() >= 0) {
			object.put("p","" + this.getNbPeers());
		} else {
			object.put("p","-1");
		}

		int completed = getNbCompleted();
		
		if ( completed >= 0 ) {
			
			object.put( "gr", "" + completed);	// grabbed
		}
		
		int	comments = getComments();

		if ( comments >= 0 ){

			object.put( "co", "" + comments );
		}

		long size = this.getSize();
		if(size >= 0) {
				// max three digits for display purposes

			String size_str = DisplayFormatters.formatByteCountToKiBEtc( size );

			size_str = DisplayFormatters.trimDigits( size_str, 3 );

			object.put("l", size_str );
			object.put("lb", "" + size  );
		} else {
			object.put("l", "-1");
			object.put("lb", "0");
		}

		object.put("r", "" + this.getRank());

		object.put("ct", this.getContentType());

		float accuracy = getAccuracy();

		if ( accuracy >= 0 ){
			if ( accuracy > 1 ){
				accuracy = 1;
			}
			object.put ("ac", "" + accuracy );
		}

		if ( this.getCDPLink().length() > 0 ){
			object.put("cdp", this.getCDPLink());
		}

			// This is also used by subscription code to extract download link so if you
			// change this you'll need to change that too...

		if ( this.getDownloadLink().length() > 0 ){
			object.put("dl", this.getDownloadLink());
		}else{
			String tl = getTorrentLink();
			if ( tl.length() > 0 ){
				object.put("dl", tl );
			}
		}

		if ( this.getDownloadButtonLink().length() > 0 ){
			object.put("dbl", this.getDownloadButtonLink());
		}

		if ( this.getPlayLink().length() > 0 ){
			object.put("pl", this.getPlayLink());
		}

		if ( this.getVotes() >= 0 ){
			object.put("v", "" + this.getVotes());
		}

		if ( this.getVotesDown() >= 0 ){
			object.put("vd", "" + this.getVotesDown());
		}

		String drmKey = getDRMKey();
		if(drmKey != null) {
			object.put("dk",drmKey);
		}

			// used by subscriptions...

		String uid = getUID();
		if ( uid != null ){
			object.put( "u", uid );
		}
		object.put("pr", this.isPrivate() ? "1" : "0");

		String hash = getHash();
		if ( hash != null ){
			object.put( "h", hash );
		}

		return object;
	}

	protected String
	guessContentTypeFromCategory(
		String		category )
	{
		if ( category == null || category.length() == 0 ){

			return( "" );
		}

		category = category.toLowerCase(Locale.US);

		if ( 	category.startsWith( "video" ) ||
				category.startsWith( "movie" ) ||
				category.startsWith( "show" ) ||
				category.startsWith( "tv" )){

			return( Engine.CT_VIDEO );

		}else if ( 	category.startsWith( "audio" ) ||
				category.startsWith( "music" )){

			return( Engine.CT_AUDIO );

		}else if ( category.startsWith( "game" )){

			return( Engine.CT_GAME );
		}else{

			return( "" );
		}
	}

	@Override
	public long
	getTime()
	{
		Date date = getPublishedDate();

		if ( date != null ){

			return( date.getTime());
		}

		return( 0 );
	}

	public static String
	adjustLink(
		String		link )
	{
		if ( link == null || link.length() < 5 ){

			return( link );
		}

		char c = link.charAt(0);

		if ( c == 'h' || c == 'H' || c == 'f' || c == 'F' ){

			int pt = MetaSearchManagerFactory.getSingleton().getProxyRequestsEnabled();
			
			if ( pt != MetaSearchManager.PROXY_NONE ){

				try{
					String host =  new URL( link ).getHost();

					if ( AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC ){

						return( link );
					}

					InetAddress ia = HostNameToIPResolver.hostAddressToInetAddress( host );

					if ( ia != null ){

						if ( 	ia.isLoopbackAddress() ||
								ia.isLinkLocalAddress() ||
								ia.isSiteLocalAddress()){

							return( link );
						}
					}
				}catch( Throwable e ){

				}

				if ( pt == MetaSearchManager.PROXY_TOR ){
				
					return( "tor:" + link );
					
				}else{
					
					return( "i2p:" + link );
				}
			}
		}

		return( link );
	}

	public static void
	adjustRelativeTerms(
		Map		map )
	{
		String	ts = (String)map.get( "ts" );

		if ( ts != null ){

			long	l_ts = Long.parseLong(ts);

			if ( l_ts > 0 ){

				map.put("d", MomentsAgoDateFormatter.getMomentsAgoString(new Date( l_ts )));

			}
		}
	}

	public static String removeHTMLTags(String input) {
		if ( input == null ){
			return( null );
		}
		String result = input.replaceAll(HTML_TAGS, " ");
		return result.replaceAll(DUPLICATE_SPACES, " ").trim();
	}

	protected static String unescapeEntities(String input )
	{
		if ( input == null ){
			return( null );
		}
		return( Entities.HTML40.unescape( input ));
	}
}
