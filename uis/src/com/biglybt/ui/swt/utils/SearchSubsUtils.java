/*
 * Created on Dec 16, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.ui.swt.utils;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.download.DownloadManagerState;

public class
SearchSubsUtils
{
	public static boolean
	addMenu(
		final SearchSubsResultBase	result,
		Menu						menu )
	{
		final byte[] hash = result.getHash();

		if ( hash != null ){
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("searchsubs.menu.google.hash"));
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					String s = ByteFormatter.encodeString(hash);
					String URL = "https://google.com/search?q=" + UrlUtils.encode(s);
					launchURL(URL);
				}
			});
		}

		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("searchsubs.menu.gis"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String s = result.getName();
				s = s.replaceAll("[-_]", " ");
				String URL = "http://images.google.com/images?q=" + UrlUtils.encode(s);
				launchURL(URL);
			}

		});

		item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("searchsubs.menu.google"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String s = result.getName();
				s = s.replaceAll("[-_]", " ");
				String URL = "https://google.com/search?q=" + UrlUtils.encode(s);
				launchURL(URL);
			}
		});

		item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("searchsubs.menu.bis"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String s = result.getName();
				s = s.replaceAll("[-_]", " ");
				String URL = "http://www.bing.com/images/search?q=" + UrlUtils.encode(s);
				launchURL(URL);
			}
		});

		return( true );
	}

	public static void
	addMenu(
		final SearchSubsResultBase[]	results,
		Menu							menu )
	{
		boolean	has_hash = false;

		for ( SearchSubsResultBase result: results ){

			byte[] hash = result.getHash();

			if ( hash != null ){

				has_hash = true;

				break;
			}
		}

		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("MagnetPlugin.contextmenu.exporturi"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				StringBuffer buffer = new StringBuffer(1024);

				for ( SearchSubsResultBase result: results ){

					byte[] hash = result.getHash();

					if ( hash != null ){
						if ( buffer.length() > 0 ){
							buffer.append( "\r\n" );
						}

						String torrent_link = result.getTorrentLink();

						String str = UrlUtils.getMagnetURI( hash, result.getName(), null );

						if ( torrent_link != null ){

							str += "&fl=" + UrlUtils.encode( torrent_link );
						}

						buffer.append( str );
					}
				}
				ClipboardCopy.copyToClipBoard( buffer.toString());
			}
		});

		item.setEnabled( has_hash );
	}

	private static void launchURL(String s) {
		Program program = Program.findProgram(".html");
		if (program != null && program.getName().contains("Chrome")) {
			try {
				Field field = Program.class.getDeclaredField("command");
				field.setAccessible(true);
				String command = (String) field.get(program);
				command = command.replaceAll("%[1lL]", Matcher.quoteReplacement(s));
				command = command.replace(" --", "");
				PluginInitializer.getDefaultInterface().getUtilities().createProcess(command + " -incognito");
			} catch (Exception e1) {
				e1.printStackTrace();
				Utils.launch(s);
			}
		} else {
			Utils.launch(s);
		}
	}

	public static boolean
	filterCheck(
		SearchSubsResultBase 	ds,
		String 					filter,
		boolean 				regex)
	{
		if ( filter == null || filter.length() == 0 ){

			return( true );
		}

		try{
			boolean	hash_filter = filter.startsWith( "t:" );

			if ( hash_filter ){

				filter = filter.substring( 2 );
			}

			String s = regex ? filter : "\\Q" + filter.replaceAll("[|;]", "\\\\E|\\\\Q") + "\\E";

			boolean	match_result = true;

			if ( regex && s.startsWith( "!" )){

				s = s.substring(1);

				match_result = false;
			}

			Pattern pattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

			if ( hash_filter ){

				byte[] hash = ds.getHash();

				if ( hash == null ){

					return( false );
				}

				String[] names = { ByteFormatter.encodeString( hash ), Base32.encode( hash )};

				for ( String name: names ){

					if ( pattern.matcher(name).find() == match_result ){

						return( true );
					}
				}

				return( false );

			}else{

				String name = ds.getName();

				return( pattern.matcher(name).find() == match_result );
			}

		}catch(Exception e ){

			return true;
		}
	}

	private static final Object	HS_KEY = new Object();

	public static final int HS_NONE			= 0;
	public static final int HS_LIBRARY		= 1;
	public static final int HS_ARCHIVE		= 2;
	public static final int HS_HISTORY		= 3;
	public static final int HS_UNKNOWN		= 4;
	public static final int HS_FETCHING		= 5;

	private static GlobalManager 			gm;
	private static DownloadManager			dm;
	private static DownloadHistoryManager	hm;

	public static int
	getHashStatus(
		SearchSubsResultBase	result )
	{
		if ( result == null ){

			return( HS_NONE );
		}

		byte[] hash = result.getHash();

		if ( hash == null || hash.length != 20 ){

			return( HS_UNKNOWN );
		}

		long	now = SystemTime.getMonotonousTime();

		Object[] entry = (Object[])result.getUserData( HS_KEY );

		if ( entry != null ){

			long time = (Long)entry[0];

			if ( now - time < 10*1000 ){

				return((Integer)entry[1] );
			}
		}

		synchronized( HS_KEY ){

			if ( gm == null ){

				Core core = CoreFactory.getSingleton();

				gm = core.getGlobalManager();
				dm = core.getPluginManager().getDefaultPluginInterface().getDownloadManager();
				hm = (DownloadHistoryManager)gm.getDownloadHistoryManager();
			}
		}

		int hs_result;

		com.biglybt.core.download.DownloadManager dl = gm.getDownloadManager(new HashWrapper(hash));
		if ( dl != null ){

			DownloadManagerState downloadState = dl.getDownloadState();
			hs_result = downloadState != null
					&& downloadState.getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD)
							? HS_FETCHING : HS_LIBRARY;

		}else if (dm.lookupDownloadStub( hash ) != null ){

			hs_result = HS_ARCHIVE;

		}else if ( hm.getDates(hash) != null ){

			hs_result = HS_HISTORY;

		}else{

			hs_result = HS_NONE;
		}

		result.setUserData( HS_KEY, new Object[]{ now + RandomUtils.nextInt( 2500 ), hs_result });

		return( hs_result );
	}
}
