/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.ui.console.ConsoleInput;

import com.biglybt.core.subs.*;


public class Subscriptions extends IConsoleCommand {

	private Subscription[] 				current_subs;
	private Subscription				current_sub;
	private List<SubscriptionResult>	current_results;

	public Subscriptions()
	{
		super("subscriptions", "subs");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("subscriptions\t\tsubs\tAccess to subscriptions.");
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("Subcommands:");
		out.println("\tlist\t: List subscriptions");
		out.println("\tcreate <name> <rss_url>\t: Create a new subscription");
		out.println("\tselect <number>\t: Select subscription <number> for further operations");
		out.println("The following commands operate on a selected subscription" );
		out.println("\tupdate \t: Update the subscription" );
		out.println("\tset_autodownload [yes|no] \t: Set the auto-download setting" );
		out.println("\tset_updatemins <number>\t: Set the refresh frequency to <number> minutes" );
		out.println("\tresults [all]\t: List the subscription results, unread only unless 'all' supplied" );
		out.println("\tset_read [<number>|all]\t: Mark specified result, or all, as read" );
		out.println("\tset_unread [<number>|all]\t: Mark specified result, or all, as un-read" );
		out.println("\tdownload [<number>|all]\t: Download the specified result, or all" );
		out.println("\tdelete\t: Delete the subscription" );
		out.println("> -----");
	}

	@Override
	public void
	execute(
		String commandName, ConsoleInput ci, List<String> args) {

		if( args.isEmpty()){

			printHelp(ci.out, args);

			return;
		}

		String cmd = args.get(0);

		SubscriptionManager sub_man = SubscriptionManagerFactory.getSingleton();

		if ( cmd.equals( "list" )){

			ci.out.println("> -----");

			current_subs = sub_man.getSubscriptions( true );

			int index = 1;

			for ( Subscription sub: current_subs ){

				SubscriptionHistory history = sub.getHistory();

				String index_str = "" + index++;

				while( index_str.length() < 3 ){
					index_str += " ";
				}

				String str = index_str + sub.getName() + ", unread=" + history.getNumUnread() + ", auto_download=" + (history.isAutoDownload()?"yes":"no");

				str += ", check_period=" + history.getCheckFrequencyMins() + " mins";

				long	last_check = history.getLastScanTime();

				str += ", last_check=" + (last_check<=0?"never":new SimpleDateFormat("yy/MM/dd HH:mm" ).format( last_check ));

				String last_error = history.getLastError();

				if ( last_error != null && last_error.length() > 0 ){

					str += ", last_error=" + last_error;
				}

				ci.out.println( str );
			}

			if ( current_subs.length==0 ){

				ci.out.println( "No Subscriptions" );
			}
		}else if ( cmd.equals( "create" )){

			if ( args.size() < 3 ){

				ci.out.println( "Usage: subs create <name> <rss_feed_url>" );

			}else{

				try{
					sub_man.createRSS( args.get(1), new URL(args.get(2)), SubscriptionHistory.DEFAULT_CHECK_INTERVAL_MINS, new HashMap());

					ci.out.println( "Subscription created" );

				}catch( Throwable e ){

					ci.out.println( "Failed to create subscription: " + Debug.getNestedExceptionMessage( e ));
				}
			}

		}else if ( cmd.equals( "select" )){

			if ( args.size() < 2 ){

				ci.out.println( "Usage: subs select <number>" );

			}else{

				try{
					int	index = Integer.parseInt( args.get(1));

					if ( current_subs == null ){

						throw( new Exception( "subscriptions must be listed prior to being selected" ));

					}else if ( current_subs.length == 0 ){

						throw( new Exception( "no subscriptions exist" ));

					}else if ( index < 0 || index > current_subs.length ){

						throw( new Exception( "subscription index '" + index + "' is out of range" ));

					}else{
						current_sub = current_subs[index-1];

						current_results = null;

						ci.out.println( "Selected subscription '" + current_sub.getName() + "'" );
					}
				}catch( Throwable e ){

					ci.out.println( "Failed to select subscription: " + Debug.getNestedExceptionMessage( e ));

				}
			}

		}else if ( 	cmd.equals( "update" ) ||
					cmd.equals( "results" ) ||
					cmd.equals( "set_autodownload" ) ||
					cmd.equals( "set_updatemins" ) ||
					cmd.equals( "set_read" ) ||
					cmd.equals( "set_unread" )||
					cmd.equals( "download" )||
					cmd.equals( "delete" )){

			if ( current_sub == null ){

				ci.out.println( "No current subscription - select one!" );

			}else{

				if ( cmd.equals( "update" )){

					try{
						sub_man.getScheduler().downloadAsync( current_sub, true );

						ci.out.println( "Subscription scheduled for update" );

					}catch( Throwable e ){

						ci.out.println( "Subscription update failed: " + Debug.getNestedExceptionMessage(e));
					}

				}else if ( cmd.equals( "results" )){

					boolean do_all = args.size() > 1 && args.get(1).equals( "all" );

					int	index = 1;

					int	total_read		= 0;
					int total_unread	= 0;

					current_results = new ArrayList<>();

					SubscriptionResult[] results = current_sub.getHistory().getResults( false );

					for ( SubscriptionResult result: results ){

						boolean is_read = result.getRead();

						if ( is_read ){

							total_read++;

						}else{

							total_unread++;
						}

						if ( is_read && !do_all ){

							continue;
						}

						current_results.add( result );

						Map map = result.toJSONMap();

						String index_str = "" + index++;

						while( index_str.length() < 3 ){
							index_str += " ";
						}

						String str = index_str + map.get("n") + ", size=" + map.get("l") + ", seeds=" + map.get("s") + ", peers=" + map.get("p");

						if ( do_all ){

							str += ", read=" + (is_read?"yes":"no" );
						}

						ci.out.println( str );
					}

					ci.out.println("> -----");
					ci.out.println("Total read=" + total_read + ", unread=" + total_unread );

				}else if ( 	cmd.equals( "set_autodownload" )){

					if ( args.size() < 2 ){

						ci.out.println( "Usage: " + cmd + " [yes|no]" );

					}else if ( !current_sub.isAutoDownloadSupported()){

						ci.out.println( "Auto-download not supported for this subscription" );

					}else{

						String temp = args.get(1);

						if ( temp.equals( "yes" )){

							current_sub.getHistory().setAutoDownload( true );

						}else if ( temp.equals( "no" )){

							current_sub.getHistory().setAutoDownload( false );

						}else{

							ci.out.println( "Usage: " + cmd + " [yes|no]" );
						}
					}

				}else if ( 	cmd.equals( "set_updatemins" )){

					if ( args.size() < 2 ){

						ci.out.println( "Usage: " + cmd + " <minutes>" );


					}else{

						try{
							int	mins = Integer.parseInt( args.get(1));

							current_sub.getHistory().setCheckFrequencyMins( mins );

						}catch( Throwable e ){

							ci.out.println( "Usage: " + cmd + " <minutes>" );
						}
					}
				}else if ( 	cmd.equals( "set_read" ) ||
							cmd.equals( "set_unread" )||
							cmd.equals( "download" )){

					if ( args.size() < 2 ){

						ci.out.println( "Usage: " + cmd + " <result_number>|all" );

					}else if ( current_results == null ){

						ci.out.println( "results must be listed before operating on them" );

					}else{
						try{
							String temp = args.get(1);

							int		do_index = -1;

							if ( !temp.equals( "all" )){

								do_index = Integer.parseInt( temp );

								if ( do_index < 1 || do_index > current_results.size()){

									throw( new Exception( "Invalid result index" ));
								}
							}


							List<SubscriptionResult> to_do = new ArrayList<>();

							if ( do_index == -1 ){

								to_do.addAll( current_results );

							}else{

								to_do.add( current_results.get( do_index-1 ));
							}

							for ( SubscriptionResult result: to_do ){

								if ( cmd.equals( "set_read" )){

									result.setRead( true );

								}else if ( cmd.equals( "set_unread" )){

									result.setRead( false );

								}else if ( cmd.equals( "download" )){

									String download_link = result.getDownloadLink();

									try{
										URL url = new URL( result.getDownloadLink());

										ci.downloadRemoteTorrent( url.toExternalForm());

										ci.out.println( "Queueing '" + download_link + "' for download" );

										result.setRead( true );

									}catch( Throwable e ){

										ci.out.println( "Failed to add download from URL '" + download_link + "': " + Debug.getNestedExceptionMessage(e));
									}
								}

							}
						}catch( Throwable e ){

							ci.out.println( "Operation failed: " + Debug.getNestedExceptionMessage( e ));

						}
					}

				}else if ( cmd.equals( "delete" )){

					ci.out.println( "Subscription '" + current_sub.getName() + "' deleted" );

					current_sub.remove();

					current_sub		= null;
					current_results	= null;
				}
			}
		}else{

			ci.out.println( "Unsupported sub-command: " + cmd );
		}

		ci.out.println("> -----");
	}
}
