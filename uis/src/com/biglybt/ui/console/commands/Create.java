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
import java.net.URL;
import java.util.*;



import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.TOTorrentProgressListener;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.console.ConsoleInput;

/**
 * @author tobi
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class Create extends IConsoleCommand {

	public Create()
	{
		super("create");
	}

	@Override
	public String getCommandDescriptions() {
		return("create <input file or folder> <output torrent file> <tracker url> [another tracker url]*\t\t\tCreate a torrent file" );
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println( "> -----" );
		out.println( "create <input file or folder> <output torrent file> <tracker url> [another tracker url]*" );
		out.println( "\tFor example: create /tmp/file.dat /tmp/file.dat.torrent http://tracker.there.com:6969/announce" );
		out.println( "> -----" );
	}

	@Override
	public void execute(String commandName, final ConsoleInput ci, List<String> args )
	{
		if( args.size() < 3 )
		{
			printHelp(ci.out, args);
			return;
		}

		File input_file = new File( args.get(0));

		if ( !input_file.exists()){

			ci.out.println( "Input file '" + input_file.getAbsolutePath() + "' doesn't exist" );

			return;
		}

		File output_file = new File( args.get(1));

		if ( output_file.exists()){

			ci.out.println( "Output file '" + input_file.getAbsolutePath() + "' already exists" );

			return;
		}

		List<URL>	urls = new ArrayList<>();

		for ( int i=2; i<args.size();i++){

			try{
				urls.add( new URL( args.get( i )));

			}catch( Throwable e ){

				ci.out.println( "Invalid URL: " + args.get( i ));

				return;
			}
		}

		try{
			TOTorrentCreator creator = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength( input_file, urls.get(0));

			creator.addListener(
				new TOTorrentProgressListener()
				{
					@Override
					public void
					reportProgress(
						int		percent_complete )
					{
						ci.out.println( "\t\t" + percent_complete + "%" );
					}

					@Override
					public void
					reportCurrentTask(
						String	task_description )
					{
						ci.out.println( "\t" + task_description );
					}
				});

			TOTorrent torrent = creator.create();

			if ( urls.size() > 1 ){

				TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

				TOTorrentAnnounceURLSet[] sets = new TOTorrentAnnounceURLSet[ urls.size() ];

				for ( int i=0;i<urls.size();i++ ){

					sets[i] = group.createAnnounceURLSet( new URL[]{ urls.get( i ) });

					ci.out.println( "\tAdded URL '" + urls.get(i) + "'" );
				}

				group.setAnnounceURLSets( sets );
			}

			torrent.serialiseToBEncodedFile( output_file );

			ci.out.println( "\tTorrent written to '" + output_file + "'" );

		}catch( Throwable e ){

			ci.out.println( "Failed to create torrent: " + Debug.getNestedExceptionMessage( e ));
		}
	}
}
