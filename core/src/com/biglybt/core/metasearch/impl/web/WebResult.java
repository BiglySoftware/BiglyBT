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

package com.biglybt.core.metasearch.impl.web;

import java.util.Date;
import java.util.StringTokenizer;

import org.apache.commons.lang.Entities;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.impl.DateParser;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pifimpl.local.utils.xml.rss.RSSUtils;

public class WebResult extends Result {



	String searchQuery;

	String rootPageURL;
	String basePageURL;
	DateParser dateParser;


	String contentType = "";
	String name;
	String category = "";

	String drmKey = null;

	Date publishedDate;
	Date assetDate;
	
	long size = -1;
	int nbPeers = -1;
	int nbSeeds = -1;
	int nbSuperSeeds = -1;
	int	comments	= -1;
	int votes = -1;
	int votesDown = -1;
	float rank = -1;

	boolean privateTorrent;

	String cdpLink;
	String torrentLink;
	String downloadButtonLink;
	String playLink;

	String uid;
	String hash;

	public WebResult(Engine engine, String rootPageURL,String basePageURL,DateParser dateParser,String searchQuery) {
		super( engine );
		this.rootPageURL = rootPageURL;
		this.basePageURL = basePageURL;
		this.dateParser = dateParser;
		this.searchQuery = searchQuery;
	}

	public void setName(String name) {
		if(name != null) {
			this.name = name;
		}
	}

	public void setNameFromHTML(String name) {
		if(name != null) {
			name = removeHTMLTags(name);
			this.name = Entities.HTML40.unescape(name);
		}
	}

	public void setCommentsFromHTML(String comments) {
		if(comments != null) {
			comments = removeHTMLTags(comments);
			comments = Entities.HTML40.unescape(comments);
			comments = comments.replaceAll(",", "");
			comments = comments.replaceAll(" ", "");
			try{
				this.comments = Integer.parseInt(comments);
			}catch( Throwable e ){
				//e.printStackTrace();
			}
		}
	}
	public void setCategoryFromHTML(String category) {
		if(category != null) {
			category = removeHTMLTags(category);
			this.category = Entities.HTML40.unescape(category).trim();
			/*int separator = this.category.indexOf(">");

			if(separator != -1) {
				this.category = this.category.substring(separator+1).trim();
			}*/

			if ( contentType == null || contentType.length() == 0 ){
				contentType = guessContentTypeFromCategory( this.category );
			}
		}
	}

	public void
	setUID(
		String	_uid )
	{
		uid	= _uid;
	}

	@Override
	public String
	getUID()
	{
		return( uid );
	}

	public void setNbPeersFromHTML(String nbPeers) {
		if(nbPeers != null) {
			nbPeers = removeHTMLTags(nbPeers);
			String nbPeersS = Entities.HTML40.unescape(nbPeers);
			nbPeersS = nbPeersS.replaceAll(",", "");
			nbPeersS = nbPeersS.replaceAll(" ", "");
			try {
				this.nbPeers = Integer.parseInt(nbPeersS);
			} catch(Throwable e) {
				//this.nbPeers = 0;
				//e.printStackTrace();
			}
		}
	}

	public void setNbSeedsFromHTML(String nbSeeds) {
		if(nbSeeds != null) {
			nbSeeds = removeHTMLTags(nbSeeds);
			String nbSeedsS = Entities.HTML40.unescape(nbSeeds);
			nbSeedsS = nbSeedsS.replaceAll(",", "");
			nbSeedsS = nbSeedsS.replaceAll(" ", "");
			try {
				this.nbSeeds = Integer.parseInt(nbSeedsS);
			} catch(Throwable e) {
				//this.nbSeeds = 0;
				//e.printStackTrace();
			}
		}
	}

	public void setNbSuperSeedsFromHTML(String nbSuperSeeds) {
		if(nbSuperSeeds != null) {
			nbSuperSeeds = removeHTMLTags(nbSuperSeeds);
			String nbSuperSeedsS = Entities.HTML40.unescape(nbSuperSeeds);
			nbSuperSeedsS = nbSuperSeedsS.replaceAll(",", "");
			nbSuperSeedsS = nbSuperSeedsS.replaceAll(" ", "");
			try {
				this.nbSuperSeeds = Integer.parseInt(nbSuperSeedsS);
			} catch(Throwable e) {
				//this.nbSeeds = 0;
				//e.printStackTrace();
			}
		}
	}

	public void setRankFromHTML( String rank_str, float divisor ){
		if (rank_str == null) {
			return;
		}
		if ( rank_str.isEmpty()){
			rank = -2; // explicit 'no rank'
		}else{
			try{
				float f = Float.parseFloat( rank_str.trim() );

				rank = f / divisor;
			}catch( Throwable e ){
			}
		}
	}

	public void setRankFromHTML( String rank_str ){
		if ( rank_str != null ){
			if ( rank_str.isEmpty()){
				rank = -2;	// explicit 'no rank'
			}else{
				try{
						// either a float 0->1 or integer 0->100

					float f = Float.parseFloat( rank_str.trim() );

					if (!rank_str.contains(".")){

						if ( f >= 0 &&  f <= 100 ){

							rank = f/100;
						}
					}else{

						if ( f >= 0 &&  f <= 1 ){

							rank = f;
						}
					}
				}catch( Throwable e ){
				}
			}
		}
	}

	@Override
	public float
	getRank()
	{
		if ( rank != -1 ){

			if ( rank == -2 ){

				return( -1 );	// no rank -> turn into -1

			}else{

				return( applyRankBias( rank ));
			}
		}

		return( super.getRank());
	}

	public void setPublishedDate(Date date) {
		this.publishedDate = date;
	}

	public void setPublishedDateFromHTML(String publishedDate) {
		if(publishedDate != null && publishedDate.length() > 0) {
			publishedDate = removeHTMLTags(publishedDate);
			String publishedDateS = Entities.HTML40.unescape(publishedDate).replace((char)160,(char)32);
			this.publishedDate = dateParser.parseDate(publishedDateS);
		}
	}

	public Date getAssetDate(){
		return( assetDate );
	}
	public void setAssetDate( String str ){
		
		assetDate = RSSUtils.parseRSSDate( str );
	}

	public void setSizeFromHTML(String size) {
		if(size != null) {
			size = removeHTMLTags(size);
			String sizeS = Entities.HTML40.unescape(size).replace((char)160,(char)32);
			sizeS = sizeS.replaceAll("<[^>]+>", " ");
			//Add a space between the digits and unit if there is none
			sizeS = sizeS.replaceFirst("(\\d)([a-zA-Z])", "$1 $2");
			try {
				StringTokenizer st = new StringTokenizer(sizeS," ");
				double base = Double.parseDouble(st.nextToken());
				String unit = "b";
				try {
					unit = st.nextToken().toLowerCase();
				} catch(Throwable e) {
					//No unit
				}
					// dunno why this code always uses binary but I just replicated how it worked when abstracting
				
				long multiplier = GeneralUtils.getUnitMultiplier( unit, true );
	
				if ( multiplier <= 0 ){
					multiplier= 1;	// ignore invalid 
				}
				this.size = (long) (base * multiplier );
			} catch(Throwable e) {
				//e.printStackTrace();
			}
		}
	}

	public void setVotesFromHTML(String votes_str) {
		if(votes_str != null) {
			votes_str = removeHTMLTags(votes_str);
			votes_str = Entities.HTML40.unescape(votes_str);
			votes_str = votes_str.replaceAll(",", "");
			votes_str = votes_str.replaceAll(" ", "");
			try {
				this.votes = Integer.parseInt(votes_str);
			} catch(Throwable e) {
				//e.printStackTrace();
			}
		}
	}

	public void setVotesDownFromHTML(String votes_str) {
		if(votes_str != null) {
			votes_str = removeHTMLTags(votes_str);
			votes_str = Entities.HTML40.unescape(votes_str);
			votes_str = votes_str.replaceAll(",", "");
			votes_str = votes_str.replaceAll(" ", "");
			try {
				this.votesDown = Integer.parseInt(votes_str);
			} catch(Throwable e) {
				//e.printStackTrace();
			}
		}
	}

	public void setPrivateFromHTML(String privateTorrent) {
		if(privateTorrent != null && ! "".equals(privateTorrent)) {
			this.privateTorrent = true;
		}
	}

	@Override
	public int
	getVotes()
	{
		return( votes );
	}

	@Override
	public int
	getVotesDown()
	{
		return( votesDown );
	}

	public void setCDPLink(String cdpLink) {
		this.cdpLink = UrlUtils.unescapeXML(cdpLink);
	}

	public void setDownloadButtonLink(String downloadButtonLink) {
		this.downloadButtonLink = UrlUtils.unescapeXML(downloadButtonLink);
	}

	public void setTorrentLink(String torrentLink) {
		this.torrentLink = UrlUtils.unescapeXML(torrentLink);
	}

	/**
	 * Use this internally to get the current value set for the torrent link as opposed to
	 * getDownloadLink that messes with the result
	 * @return
	 */

	public String getTorrentLinkRaw(){
		return( torrentLink );
	}

	public void setPlayLink(String playLink) {
		this.playLink = playLink;
	}

	@Override
	public String getContentType() {
		return this.contentType;
	}

	@Override
	public String getPlayLink() {
		return( reConstructLink(  playLink ));
	}

	@Override
	public void setCategory(String category) {
		this.category = category;

	}

	@Override
	public void setContentType(String contentType) {
		this.contentType = contentType;

	}

	public void setDrmKey(String drmKey) {
		this.drmKey = drmKey;
	}

	public void
	setHash(
		String	_hash )
	{
		try{
			hash = _hash.trim();

			if ( hash.length() == 32 ){

					// base 32 hash

			}else if ( hash.length() == 40 ){

					// base 16

				hash = Base32.encode( ByteFormatter.decodeString( hash ));

			}else{

				hash = null;
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);

			hash = null;
		}

		if(hash != null && downloadButtonLink == null) {
			setDownloadButtonLink(UrlUtils.normaliseMagnetURI(hash));
		}
		if(hash != null && torrentLink == null) {
			setTorrentLink(UrlUtils.normaliseMagnetURI(hash));
		}
	}

	@Override
	public String
	getHash()
	{
		return( hash );
	}

	@Override
	public String getCDPLink() {

		return reConstructLink(cdpLink);
	}

	@Override
	public String getCategory() {
		return category;
	}

	@Override
	public String getDownloadLink() {

		return reConstructLink(torrentLink);

	}

	@Override
	public String getDownloadButtonLink() {

		//If we don't have a download button link, but we do have a direct download link,
		//then we should use the direct download link...
		if(downloadButtonLink != null) {
			return reConstructLink(downloadButtonLink);
		} else {
			return getDownloadLink();
		}
	}

	@Override
	public String getTorrentLink(){
		return( reConstructLink( torrentLink ));
	}

	private String
	reConstructLink(
		String link)
	{
		if ( link != null ){

			String lc_link = link.toLowerCase();

			if ( 	lc_link.startsWith("http://") ||
					lc_link.startsWith("https://") ||
					lc_link.startsWith("tor:http://") ||
					lc_link.startsWith("tor:https://") ||
					lc_link.startsWith("azplug:") ||
					lc_link.startsWith("magnet:") ||
					lc_link.startsWith("bc:") ||
					lc_link.startsWith("bctp:") ||
					lc_link.startsWith("dht:" )){

				return( adjustLink( link ));
			}

			if ( link.startsWith("/")){

				return(adjustLink((rootPageURL==null?"":rootPageURL) + link ));
			}

			return(adjustLink((basePageURL==null?"":basePageURL) + link ));
		}

		return( "" );
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getNbPeers() {
		return nbPeers;
	}

	@Override
	public int getNbSeeds() {
		return nbSeeds;
	}

	@Override
	public int getNbSuperSeeds() {
		return nbSuperSeeds;
	}

	@Override
	public Date getPublishedDate() {
		return publishedDate;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public int
	getComments()
	{
		return( comments );
	}

	@Override
	public String getSearchQuery() {
		return searchQuery;
	}

	@Override
	public boolean isPrivate() {
		return privateTorrent;
	}

	@Override
	public String getDRMKey() {
		return drmKey;
	}

	@Override
	public float getAccuracy() {
		return -1;
	}
}
