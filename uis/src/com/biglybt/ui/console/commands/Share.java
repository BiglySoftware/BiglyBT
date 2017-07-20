/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * AddFind.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.io.File;
import java.io.PrintStream;
import java.util.*;


import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author tobi
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class Share extends IConsoleCommand {

	public Share()
	{
		super("share");
	}

	@Override
	public String getCommandDescriptions() {
		return("share <type> <path> [<properties>]\t\t\tShare a file or folder(s). Use without parameters to get a list of available options." );
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println( "> -----" );
		out.println( "[share <type> <path> [<properties>]" );
		out.println( "type options:" );
		out.println( "file           Share a single file." );
		out.println( "folder         Share a folder as a single multi-file torrent." );
		out.println( "contents       Share files and sub-dirs in a folder as single and multi-file torrents." );
		out.println( "rcontents      Share files and sub-dir files in a folder as separate torrents." );
		out.println( "list           List the shares (path not required)");
		out.println( "remove         Remove a share given its path");
		out.println( "remove hash <hash>   Remove a share given its hash");

		out.println( "      <properties> is semicolon separated <name>=<value> list.");
		out.println( "      Defined values are 'category=<cat>', 'private=<true/false>', 'dht_backup=<true/false>' and 'comment=<comment>' ('_' in <comment> are replaced with spaces)");
		out.println( "          currently only 'category' can be applied to file/folder and the rest only apply to items added *after* the share has been defined" );
		out.println( "      For example: share contents /music category=music;private=true;comment=Great_Stuff");
		out.println( "> -----" );
	}

	@Override
	public void execute(String commandName, final ConsoleInput ci, List args )
	{
		if( args.isEmpty() )
		{
			printHelp(ci.out, args);
			return;
		}
		final ShareManager share_manager;
		try{
			share_manager = ci.core.getPluginManager().getDefaultPluginInterface().getShareManager();
		}catch( ShareException e ){
			ci.out.println( "ERROR: " + e.getMessage() + " ::");
			Debug.printStackTrace( e );
			return;
		}

		final String arg = (String) args.remove(0);

		if ( args.isEmpty() && ("list".equalsIgnoreCase(arg)) ) {
			ShareResource[]	shares = share_manager.getShares();
			if( shares.length == 0 ){
				ci.out.println("> No shares found");
			}else{

				HashSet	share_map = new HashSet();

				int	share_num = 0;

				for (int i=0;i<shares.length;i++){

					ShareResource	share = shares[i];

					if ( share instanceof ShareResourceDirContents ){

						share_map.add( share );

					}else if ( share.getParent() != null ){

					}else{

						ci.out.println( "> " + share_num++ + ": " + shares[i].getName());
					}
				}

				Iterator	it = share_map.iterator();

				TorrentManager tm = ci.core.getPluginManager().getDefaultPluginInterface().getTorrentManager();

				TorrentAttribute	category_attribute 	= tm.getAttribute( TorrentAttribute.TA_CATEGORY );
				TorrentAttribute	props_attribute 	= tm.getAttribute( TorrentAttribute.TA_SHARE_PROPERTIES );

				while( it.hasNext()){

					ShareResourceDirContents	root = (ShareResourceDirContents)it.next();

					String	cat 	= root.getAttribute( category_attribute );
					String	props 	= root.getAttribute( props_attribute );

					String	extra = cat==null?"":(",cat=" + cat );

					extra += props==null?"":(",props=" + props );

					ci.out.println( "> " + share_num++ + ": " + root.getName() + extra );

					outputChildren( ci, "    ", root );
				}
			}
			return;
		}

		String	first_arg = (String)args.get(0);

		if ( first_arg.equals( "hash" ) && args.size() > 1 ){

			byte[]	hash = ByteFormatter.decodeString((String)args.get(1));

			boolean	force = false;

			if ( args.size() > 2 ){

				force = ((String)args.get(2)).equalsIgnoreCase( "true" );
			}

			if (( "remove".equalsIgnoreCase(arg))){

				ShareResource[]	shares = share_manager.getShares();

				boolean	done = false;

				for (int i=0;i<shares.length;i++){

					ShareResource share = shares[i];

					ShareItem item = null;

					if ( share instanceof ShareResourceFile ){

						item = ((ShareResourceFile)share).getItem();

					}else if ( share instanceof ShareResourceDir ){

						item = ((ShareResourceDir)share).getItem();
					}

					if ( item != null ){

						try{
							byte[] item_hash = item.getTorrent().getHash();

							if ( Arrays.equals( hash, item_hash )){

								share.delete( force );

								ci.out.println( "> Share " + share.getName() + " removed" );

								done = true;

								break;
							}
						}catch( Throwable e ) {

							ci.out.println( "ERROR: " + e.getMessage() + " ::");

							Debug.printStackTrace( e );
						}
					}
				}

				if ( !done ){

					ci.out.println( "> Share with hash " + ByteFormatter.encodeString( hash ) + " not found" );
				}
			}else{

				ci.out.println( "ERROR: Unsupported hash based command '" + arg + "'" );
			}

			return;
		}

		final File path = new File( first_arg );
		if( !path.exists() ) {
			ci.out.println( "ERROR: path [" +path+ "] does not exist." );
			return;
		}

		if ( ("remove".equalsIgnoreCase(arg)) ) {

			ShareResource[]	shares = share_manager.getShares();

			boolean	done = false;

			for (int i=0;i<shares.length;i++){

				if ( shares[i].getName().equals( path.toString())){

					try{
						shares[i].delete();

						ci.out.println( "> Share " + path.toString() + " removed" );

						done	= true;

					}catch( Throwable e ) {

						ci.out.println( "ERROR: " + e.getMessage() + " ::");

						Debug.printStackTrace( e );
					}

					break;
				}
			}

			if ( !done ){

				ci.out.println( "> Share " + path.toString() + " not found" );
			}

			return;
		}

		String	category 	= null;
		String	props		= null;

		if ( args.size() == 2 ){

			String	properties = (String)args.get(1);

			StringTokenizer tok = new StringTokenizer( properties, ";" );

			while( tok.hasMoreTokens()){

				String	token = tok.nextToken();

				int	pos = token.indexOf('=');

				if ( pos == -1 ){

					ci.out.println( "ERROR: invalid properties string '" + properties + "'" );

					return;

				}else{

					String	lhs = token.substring(0,pos).trim().toLowerCase();
					String	rhs = token.substring(pos+1).trim();

					if ( lhs.equals( "category" )){

						category = rhs;

					}else{

						if ( 	lhs.equals( "private" ) ||
								lhs.equals( "dht_backup" ) ||
								lhs.equals( "comment" )){

							if ( props == null ){

								props = "";
							}

								// _ are replaced with spaces

							if ( lhs.equals("comment")){

								rhs = rhs.replace('_', ' ' );
							}

							if ( rhs.length() > 0 ){

								props += (props.length()==0?"":";") + lhs + "=" + rhs;
							}

						}else{

							ci.out.println( "ERROR: invalid properties string '" + properties + "'" );

							return;
						}
					}
				}
			}
		}

		final String		f_category	= category;
		final String		f_props		= props;

		new AEThread( "shareFile" )
		{
			@Override
			public void
			runSupport()
			{
				try{
					ShareResource resource = share_manager.getShare( path );

					if( "file".equalsIgnoreCase( arg ) ) {

						ci.out.println( "File [" +path+ "] share being processed in background..." );

						if ( resource == null ){

							resource = share_manager.addFile( path );
						}

					}else if( "folder".equalsIgnoreCase( arg ) ) {

						ci.out.println( "Folder [" +path+ "] share being processed in background..." );

						if ( resource == null ){

							resource = share_manager.addDir( path );
						}

					}else if( "contents".equalsIgnoreCase( arg ) ) {

						ci.out.println( "Folder contents [" +path+ "] share being processed in background..." );

						if ( resource == null ){

							resource = share_manager.addDirContents( path, false );
						}

					}else if( "rcontents".equalsIgnoreCase( arg ) ) {

						ci.out.println( "Folder contents recursive [" +path+ "] share being processed in background..." );

						if ( resource == null ){

							resource = share_manager.addDirContents( path, true );
						}
					}else{

						ci.out.println( "ERROR: type '" + arg + "' unknown." );

					}

					if ( resource != null ){

						TorrentManager tm = ci.core.getPluginManager().getDefaultPluginInterface().getTorrentManager();

						String	cat = f_category;

						if ( cat != null ){

							if ( cat.length() == 0 ){

								cat	= null;
							}

							resource.setAttribute( tm.getAttribute( TorrentAttribute.TA_CATEGORY), cat );
						}

						String	pro = f_props;

						if ( pro != null ){

							if ( pro.length() == 0 ){

								pro = null;
							}

							resource.setAttribute( tm.getAttribute( TorrentAttribute.TA_SHARE_PROPERTIES), pro );
						}
					}

					if ( resource != null ){

						ci.out.println( "... processing complete" );
					}
				}catch( Throwable e ) {

					ci.out.println( "ERROR: " + e.getMessage() + " ::");

					Debug.printStackTrace( e );
				}
			}
		}.start();
	}

	protected void
	outputChildren(
		ConsoleInput				ci,
		String						indent,
		ShareResourceDirContents	node )
	{
		ShareResource[]	kids = node.getChildren();

		for (int i=0;i<kids.length;i++){

			ShareResource	kid = kids[i];

			ci.out.println( indent + kid.getName());

			if ( kid instanceof ShareResourceDirContents ){

				outputChildren( ci, indent + "    ", (ShareResourceDirContents)kid );
			}
		}
	}
}
