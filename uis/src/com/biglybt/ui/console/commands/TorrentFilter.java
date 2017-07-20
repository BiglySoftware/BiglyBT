/*
 * Created on 26/09/2005
 * Created by Paul Duran
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.biglybt.ui.console.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.download.DownloadManager;

import com.biglybt.core.CoreException;

public class TorrentFilter {

	private static final Pattern rangePattern = Pattern.compile("([0-9]+)\\s*((-)|(-\\s*([0-9]+)))?");

	public TorrentFilter() {
		super();
	}

	/**
	 * matches a range of torrents. eg: 3-5 or a single torrent. eg: 3. or from 3 onwards: 3-
	 * @param torrents torrents to match
	 * @param filter range expression
	 * @return list of matched DownloadManager objects
	 */
	private List matchRange( List torrents, String filter )
	{
		Matcher matcher = rangePattern.matcher(filter);
		List list = new ArrayList();
		if( matcher.matches() )
		{
			int minId = Integer.parseInt(matcher.group(1));
			if( minId == 0 )
				throw new CoreException("lower range must be greater than 0");
			if( minId > torrents.size() )
				throw new CoreException("lower range specified (" + minId + ") is outside number of torrents (" + torrents.size() + ")");
			if( matcher.group(2) == null )
			{
				// received a single number. eg: 3
				list.add(torrents.get(minId-1));
				return list;
			}
			int maxId;
			if( matcher.group(3) == null )
				// received bound range. eg: 3-5
				maxId = Integer.parseInt(matcher.group(5));
			else
				// received open ended range. eg: 3-
				maxId = torrents.size();

			if( minId > maxId )
				throw new CoreException("when specifying a range, the max value must be greater than or equal to the min value");

			for( int i = (minId-1) ; i < maxId && i < torrents.size() ; i++ )
			{
				list.add(torrents.get(i));
			}
		}
		return list;
	}

	/**
	 * attempst to match a wildcard against the list of torrents by
	 * checking their display name
	 * @param torrents list of available torrents to match
	 * @param filter wildcard (glob) filter
	 * @return list of matched DownloadManager objects
	 */
	private List matchWildcard( List torrents, String filter )
	{
		Pattern pattern = Pattern.compile(wildcardToPattern(filter), Pattern.CASE_INSENSITIVE);
		List list = new ArrayList();
		for (Iterator iter = torrents.iterator(); iter.hasNext();) {
			DownloadManager dm = (DownloadManager) iter.next();
			if( pattern.matcher(dm.getDisplayName()).matches() )
				list.add(dm);
		}
		return list;
	}

	/**
	 * converts the wildcard (eg: tran*) into a regular expression - (tran.*)
	 * @param wild wildcard (glob) expression
	 * @return regular expression string
	 */
	private String wildcardToPattern(String wild)
	{
		if (wild == null)
			return null;

		StringBuilder buffer = new StringBuilder();

		char[] chars = wild.toCharArray();

		for (int i = 0; i < chars.length; ++i) {
			if (chars[i] == '*')
				buffer.append(".*");
			else if (chars[i] == '?')
				buffer.append(".");
			else if ("+()^$.{}[]|\\".indexOf(chars[i]) != -1)
				buffer.append('\\').append(chars[i]); // prefix all metacharacters
													// with backslash
			else
				buffer.append(chars[i]);
		}
		return buffer.toString().toLowerCase();
	}

	/**
	 * tries our two different matching algorithms using the
	 * supplied filter against the list of torrents in the ConsoleInput object
	 * @param torrentsToMatch list of DownloadManager objects to attempt to match against
	 * @param filter filter - eg: range or glob filter
	 * @return list of matched DownloadManager objects
	 */
	public List getTorrents(List torrentsToMatch, String filter)
	{
		List torrents = new ArrayList();
		torrents.addAll(matchRange(torrentsToMatch, filter) );
		torrents.addAll(matchWildcard(torrentsToMatch, filter) );
		return torrents;
	}

	/**
	 * first tries to match torrents by concatenating all of the arguments.
	 * if that doesn't work, attempts to match each argument individually.
	 * @param torrentsToMatch list of DownloadManager objects to attempt to match against
	 * @param args arguments to try to match
	 * @return list of matched DownloadManager objects
	 */
	public List getTorrents(List torrentsToMatch, List args)
	{
		// first, try to match the whole list concatenated as a string
		StringBuilder allArgs = new StringBuilder();
		boolean first = true;
		for (Iterator iter = args.iterator(); iter.hasNext();) {
			if( ! first )
				allArgs.append(",");
			else
				first = false;
			allArgs.append(iter.next());
		}
		List torrents;
		torrents = matchWildcard(torrentsToMatch, allArgs.toString());
		if( torrents.size() > 0 )
			return torrents;
		torrents = matchRange(torrentsToMatch, allArgs.toString());
		if( torrents.size() > 0 )
			return torrents;
		// if no torrents then handle each argument individually
		for (Iterator iter = args.iterator(); iter.hasNext();) {
			torrents.addAll(getTorrents(torrentsToMatch, (String)iter.next()) );
		}
		return torrents;
	}
}
