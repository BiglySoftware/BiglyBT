/*
 * Created on May 30, 2006 7:32:56 PM
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
package com.biglybt.core.internat;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TorrentUtils;

/**
 * Locale functions specific to Torrents.

 * @note Moved from LocaleUtil to keep (cyclical) dependencies down.
 */
public class LocaleTorrentUtil
{
	private static final List<LocaleUtilListener> listeners = new ArrayList<>();

	/**
	 * Retrieves the encoding of the torrent if it can be determined.
	 * <br>
	 * Does not prompt the user with choices.
	 *
	 * @param torrent Torrent to get encoding of
	 * @return Locale torrent is in.  Torrents with .utf8 keys will always get a decoder
	 */
	static public LocaleUtilDecoder getTorrentEncodingIfAvailable(
			TOTorrent torrent)
	{
		String encoding = torrent.getAdditionalStringProperty("encoding");

		if (encoding == null) {
			return null;
		}
		if (TOTorrent.ENCODING_ACTUALLY_UTF8_KEYS.equals(encoding)) {
			encoding = "utf8";
		}

		LocaleUtil localeUtil = LocaleUtil.getSingleton();

		// get canonical name

		String canonical_name;

		try {
			LocaleUtilDecoder fallback_decoder = localeUtil.getFallBackDecoder();

			canonical_name = encoding.equals(fallback_decoder.getName())
					? encoding : Charset.forName(encoding).name();

		} catch (Throwable e) {

			canonical_name = encoding;
		}

		LocaleUtilDecoder[] all_decoders = localeUtil.getDecoders();

		for (LocaleUtilDecoder decoder : all_decoders) {

			if (decoder.getName().equals(canonical_name)) {

				return (decoder);
			}
		}

		return null;
	}

	/**
	 * Get the torrent's encoding, optionally prompting the user to choose from a list
	 * if needed.
	 *
	 * @param torrent Torrent to get encoding of
	 * @return LocaleUtilDecoder that the torrent is in
	 */
	static public LocaleUtilDecoder getTorrentEncoding(TOTorrent torrent)

		throws TOTorrentException
	{
		return getTorrentEncoding(torrent, true, false);
	}

	static public LocaleUtilDecoder getTorrentEncoding(TOTorrent torrent,
			boolean saveToFileAllowed, boolean forcePrompt)
			throws TOTorrentException
	{
		String encoding = torrent.getAdditionalStringProperty("encoding");
		if (TOTorrent.ENCODING_ACTUALLY_UTF8_KEYS.equals(encoding)) {
			forcePrompt = false;
		}

		if (!forcePrompt) {
			LocaleUtilDecoder decoder = getTorrentEncodingIfAvailable(torrent);
			if (decoder != null) {
				return decoder;
			}
		}

		// we can only persist the torrent if it has a filename defined for it

		boolean bSaveToFile = saveToFileAllowed;

		try {

			bSaveToFile &= TorrentUtils.getTorrentFileName(torrent) != null;

		} catch (Throwable e) {

			bSaveToFile = false;
		}

		// get the decoders valid for various localisable parts of torrent content
		// not in any particular order (well, system encoding is guaranteed to be first entry me thinks)

		List<LocaleUtilDecoderCandidate> candidates = getTorrentCandidates(torrent);
		LocaleUtil localeUtil = LocaleUtil.getSingleton();

		LocaleUtilDecoder selected_decoder = null;

		// updated this to select UTF-8 over the system decoder if it is 'as good' as any other

		int	min_length 	= Integer.MAX_VALUE;
		int utf8_length = Integer.MAX_VALUE;

		LocaleUtilDecoder	utf8_decoder = null;

		for ( LocaleUtilDecoderCandidate candidate: candidates ){

			String val = candidate.getValue();

			if ( val != null ){

				int	len = val.length();

				if ( len < min_length ){

					min_length = len;
				}

				LocaleUtilDecoder decoder = candidate.getDecoder();
				String name = decoder.getName().toUpperCase( Locale.US );

				if ( name.equals( "UTF-8" ) || name.equals( "UTF8" )){

					utf8_length		= len;
					utf8_decoder 	= decoder;
				}
			}
		}

		if ( utf8_decoder != null && utf8_length == min_length ){
			selected_decoder = utf8_decoder;
		} else {

			// see if we can try and apply a default encoding

			String default_name = COConfigurationManager.getStringParameter(
					"File.Decoder.Default", "");

			if (default_name.length() > 0) {
				for (LocaleUtilDecoderCandidate candidate : candidates) {
					LocaleUtilDecoder decoder = candidate.getDecoder();
					if (candidate.getValue() != null
							&& decoder.getName().equals(default_name)) {

						selected_decoder = decoder;
						break;
					}
				}
			}
		}

		if (selected_decoder == null || forcePrompt) {

			// ask listeners for an explicit decision

			for (LocaleUtilListener listener : listeners) {

				try {
					LocaleUtilDecoderCandidate candidate = listener.selectDecoder(
							localeUtil, torrent, candidates, forcePrompt);

					if (candidate != null) {

						selected_decoder = candidate.getDecoder();

						break;
					}
				} catch (Throwable ignore) {
				}
			}

			if (forcePrompt && selected_decoder == null) {
				LocaleUtilDecoder decoder = getTorrentEncodingIfAvailable(torrent);
				if (decoder != null) {
					return decoder;
				}
			}
		}

		if ( selected_decoder == null ){

			// going to make an automatic decision, don't persist this

			bSaveToFile = false;

			boolean system_decoder_is_valid = false;

			LocaleUtilDecoder system_decoder = localeUtil.getSystemDecoder();
			for (LocaleUtilDecoderCandidate candidate : candidates) {
				if (candidate.getDecoder() == system_decoder) {
					system_decoder_is_valid = true;
					break;
				}
			}

			if ( system_decoder_is_valid ){

				// go for system decoder, if valid, fallback if not

				selected_decoder = localeUtil.getSystemDecoder();

			}else{

				selected_decoder = localeUtil.getFallBackDecoder();
			}
		}

		torrent.setAdditionalStringProperty("encoding",
				selected_decoder.getName());

		if (bSaveToFile) {
			TorrentUtils.writeToFile(torrent);
		}

		return (selected_decoder);
	}

	/**
	 * Checks the Torrent's text fields (path, comment, etc) against a list
	 * of locals, returning only those that can handle all the fields.
	 *
	 * @return {@link LocaleUtilDecoderCandidate}s that can decode the torrent.
	 * All entries will have a non-null decoder
	 */
	static protected List<LocaleUtilDecoderCandidate> getTorrentCandidates(
			TOTorrent torrent) {
		long lMinCandidates;
		byte[] minCandidatesArray;

		LocaleUtil localeUtil = LocaleUtil.getSingleton();

		byte[] torrentName = torrent.getName();
		List<LocaleUtilDecoderCandidate> candidateList = localeUtil.getCandidates(
				torrentName);
		lMinCandidates = candidateList.size();
		minCandidatesArray = torrentName;

		TOTorrentFile[] files = torrent.getFiles();

		for (TOTorrentFile file : files) {

			byte[][] comps = file.getPathComponents();

			for (byte[] comp : comps) {
				List<LocaleUtilDecoderCandidate> candidateDecoders = localeUtil.getCandidates(
						comp);
				if (candidateDecoders.size() < lMinCandidates) {
					lMinCandidates = candidateDecoders.size();
					minCandidatesArray = comp;
				}
				retainAll(candidateList, candidateDecoders);
			}
		}

		byte[] comment = torrent.getComment();

		if (comment != null) {
			List<LocaleUtilDecoderCandidate> candidateDecoders = localeUtil.getCandidates(comment);
			if (candidateDecoders.size() < lMinCandidates) {
				lMinCandidates = candidateDecoders.size();
				minCandidatesArray = comment;
			}
			retainAll(candidateList, candidateDecoders);
		}

		byte[] created = torrent.getCreatedBy();

		if (created != null) {
			List<LocaleUtilDecoderCandidate> candidateDecoders = localeUtil.getCandidates(created);
			if (candidateDecoders.size() < lMinCandidates) {
				lMinCandidates = candidateDecoders.size();
				minCandidatesArray = created;
			}
			retainAll(candidateList, candidateDecoders);
		}

		if (candidateList.isEmpty()) {
			candidateList = localeUtil.getCandidates(minCandidatesArray);
		}

		// prefer utf-8
		for (int i = 0; i < candidateList.size(); i++) {
			LocaleUtilDecoderCandidate candidate = candidateList.get(i);
			if (candidate.getDecoder().getName().equals("UTF-8")) {
				if (i > 0) {
					candidateList.remove(i);
					candidateList.add(0, candidate);
				}
				break;
			}
		}

		return candidateList;
	}

	private static void retainAll(List<LocaleUtilDecoderCandidate> listToModify,
			List<LocaleUtilDecoderCandidate> list) {
		Iterator<LocaleUtilDecoderCandidate> it = listToModify.iterator();
		while (it.hasNext()) {
			LocaleUtilDecoderCandidate candidate = it.next();
			if (candidate == null) {
				continue;
			}

			boolean found = false;
			for (LocaleUtilDecoderCandidate candidate2 : list) {
				if (candidate.getIndex() == candidate2.getIndex()) {
					found = true;
					break;
				}
			}
			if (!found) {
				it.remove();
			}
		}
	}

	static public void setTorrentEncoding(TOTorrent torrent, String encoding)

		throws LocaleUtilEncodingException
	{
		try {
			LocaleUtil localeUtil = LocaleUtil.getSingleton();
			List<LocaleUtilDecoderCandidate> candidates = getTorrentCandidates(torrent);

			String canonical_requested_name;

			// "System" means use the system encoding

			if (encoding.equalsIgnoreCase("system")) {
				canonical_requested_name = localeUtil.getSystemEncoding().name();

			} else if (encoding.equalsIgnoreCase(LocaleUtilDecoderFallback.NAME)) {

				canonical_requested_name = LocaleUtilDecoderFallback.NAME;

			} else {

				CharsetDecoder requested_decoder = Charset.forName(encoding).newDecoder();

				canonical_requested_name = requested_decoder.charset().name();
			}

			boolean ok = false;

			for (LocaleUtilDecoderCandidate candidate : candidates) {

				if (candidate.getDecoder().getName().equals(
						canonical_requested_name)) {

					ok = true;

					break;
				}
			}

			if (!ok) {

				String[] charsets = new String[candidates.size()];
				String[] names = new String[candidates.size()];

				for (int i = 0; i < candidates.size(); i++) {

					LocaleUtilDecoder decoder = candidates.get(i).getDecoder();

					charsets[i] = decoder.getName();
					names[i] = decoder.decodeString(torrent.getName());
				}

				throw (new LocaleUtilEncodingException(charsets, names));
			}

			torrent.setAdditionalStringProperty("encoding", canonical_requested_name);

		} catch (Throwable e) {

			if (e instanceof LocaleUtilEncodingException) {

				throw ((LocaleUtilEncodingException) e);
			}

			throw (new LocaleUtilEncodingException(e));
		}
	}

	static public void setDefaultTorrentEncoding(TOTorrent torrent)

		throws LocaleUtilEncodingException
	{
		setTorrentEncoding(torrent, Constants.DEFAULT_ENCODING_CHARSET.name());
	}

	static public String getCurrentTorrentEncoding(TOTorrent torrent) {
		return (torrent.getAdditionalStringProperty("encoding"));
	}

	public static void addListener(LocaleUtilListener l) {
		listeners.add(l);
	}

	public static void removeListener(LocaleUtilListener l) {
		listeners.remove(l);
	}
}
