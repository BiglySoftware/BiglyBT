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
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;


public class
SearchSubsUtils
{
	public static boolean
	addMenu(
		SearchSubsResultBase		result,
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
		Subscription				subs_maybe_null,
		SearchSubsResultBase[]		results,
		Menu						menu )
	{
		boolean	has_hash 	= false;
		boolean	all_read	= true;
		boolean	all_unread	= true;
		
		for ( SearchSubsResultBase result: results ){

			byte[] hash = result.getHash();

			if ( hash != null ){

				has_hash = true;
			}
			
			if ( result.getRead()){
				all_unread = false;
			}else{
				all_read = false;
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
		
		new MenuItem(menu, SWT.SEPARATOR);
		
		item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("menu.mark.read"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				if ( subs_maybe_null == null || results.length == 1 ){
					for ( SearchSubsResultBase result: results ){
	
						result.setRead( true );
					}
				}else{
					String[]	ids 	= new String[results.length];
					boolean[]	read	= new boolean[ids.length];
					
					Arrays.fill( read, true );
					
					for ( int i=0;i<ids.length;i++){
						ids[i] = results[i].getID();
					}
					
					subs_maybe_null.getHistory().markResults( ids, read );
				}
			}
		});
		
		item.setEnabled( !all_read );
		
		item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("menu.mark.read.in.all"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				if ( subs_maybe_null == null || results.length == 1 ){
					for ( SearchSubsResultBase result: results ){
	
						result.setRead( true );
					}
				}else{
					String[]	ids 	= new String[results.length];
					boolean[]	read	= new boolean[ids.length];
					
					Arrays.fill( read, true );
					
					for ( int i=0;i<ids.length;i++){
						ids[i] = results[i].getID();
					}
					
					subs_maybe_null.getHistory().markResults( ids, read );
				}
				Utils.getOffOfSWTThread(()->{
					SubscriptionManagerFactory.getSingleton().markAllRead( results );
				});
			}
		});
		
		item.setEnabled( !all_read );
		
		
		
		item = new MenuItem(menu, SWT.PUSH);
		item.setText(MessageText.getString("menu.mark.unread"));
		item.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				if ( subs_maybe_null == null || results.length == 1 ){
					for ( SearchSubsResultBase result: results ){
	
						result.setRead( false );
					}
				}else{
					String[]	ids 	= new String[results.length];
					boolean[]	read	= new boolean[ids.length];
					
					Arrays.fill( read, false );
					
					for ( int i=0;i<ids.length;i++){
						ids[i] = results[i].getID();
					}
					
					subs_maybe_null.getHistory().markResults( ids, read );
				}
			}
		});
		
		item.setEnabled( !all_unread );
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
		boolean 				regex,
		boolean					confusable )
	{
		if ( filter == null || filter.length() == 0 ){

			return( true );
		}

		if ( confusable ){
		
			filter = GeneralUtils.getConfusableEquivalent( filter, true );
		}
		
		try{
			boolean	hash_filter = filter.startsWith( "t:" );

			if ( hash_filter ){

				filter = filter.substring( 2 );
			}

			String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

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

				if ( confusable ){
				
					name = GeneralUtils.getConfusableEquivalent (name, false );
				}
				
				return( pattern.matcher(name).find() == match_result );
			}

		}catch(Exception e ){

			return true;
		}
	}
}
