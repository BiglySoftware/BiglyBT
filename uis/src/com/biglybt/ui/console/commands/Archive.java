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
import java.util.*;

import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.ui.console.ConsoleInput;



public class Archive extends IConsoleCommand {

	public Archive()
	{
		super("archive", "ar");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("archive\t\tar\tLists, and allows the restoration of, archived downloads.");
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("Subcommands:");
		out.println("list\t\tl\t\tList archived downloads");
		out.println("show <num>\ts\t\tShow archived download");
		out.println("restore <num>\tres\t\tRestore archived download");
		out.println("delete <num>\tdel\t\tDelete archived download");
		out.println("> -----");
	}

	@Override
	public void
	execute(
		String commandName, ConsoleInput ci, List<String> args) {

		if ( args.size() > 0 ){

			PluginInterface pi = ci.getCore().getPluginManager().getDefaultPluginInterface();

			DownloadStub[] stubs = pi.getDownloadManager().getDownloadStubs();

			String sub = args.get(0);

			int	index = -1;

			if ( args.size() > 1 ){

				String index_str = args.get(1);

				try{
					index = Integer.parseInt( index_str );

					index--;

					if ( index < 0 || index >= stubs.length ){

						index = -1;
					}

				}catch( Throwable e ){
				}

				if ( index == -1 ){

					ci.out.println( "Invalid archive index: " + index_str );
				}
			}

			if ( sub.equals( "list" ) || sub.equals( "l" )){

				int pos = 1;

				ci.out.println( "> -----" );

				for ( DownloadStub stub: stubs ){

					System.out.println( " " + (pos++) + "\t" + stub.getName() + " (" + DisplayFormatters.formatByteCountToKiBEtc( stub.getTorrentSize()) + ")");
				}

				ci.out.println( "> -----" );

			}else if ( index != -1 && ( sub.equals( "show" ) || sub.equals( "s" ))){

				try{
					DownloadStub stub = stubs[index];

					ci.out.println( "> -----" );
					ci.out.println( "  " + stub.getName() + " - hash=" + ByteFormatter.encodeString( stub.getTorrentHash()));

					DownloadStubFile[] files = stub.getStubFiles();

					ci.out.println( "  Files: " + files.length );

					for ( DownloadStubFile file: files ){

						long	length = file.getLength();

						ci.out.println( "    " + file.getFile() + " - " + (length < 0?("Not downloaded"):DisplayFormatters.formatByteCountToKiBEtc( length )));
					}

					ci.out.println( "> -----" );

				}catch( Throwable e ){

					ci.out.print( e );
				}

			}else if ( index != -1 && ( sub.equals( "restore" ) || sub.equals( "res" ))){

				try{
					Download d = stubs[index].destubbify();

					ci.out.println( "> Restore of " + d.getName() + " succeeded." );

				}catch( Throwable e ){

					ci.out.print( e );
				}

			}else if ( index != -1 && ( sub.equals( "delete" ) || sub.equals( "del" ))){

				try{
					DownloadStub stub = stubs[index];

					String 	name = stub.getName();

					stub.remove();

					ci.out.println( "> Delete of " + name + " succeeded." );

				}catch( Throwable e ){

					ci.out.print( e );
				}

			}else{

				ci.out.println( "Unsupported sub-command: " + sub );

				return;
			}
		}else{

			printHelp( ci.out, args );
		}

	}
}
