/*
 * Created on 12-Sep-2005
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pifimpl.local.launch;


import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.PluginManagerArgumentHandler;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;


public class
PluginSingleInstanceHandler
{
	private static boolean		active;

	private static int								port;
	private static PluginManagerArgumentHandler		handler;

	public static void
	initialise(
		int									_port,
		PluginManagerArgumentHandler		_handler )
	{
		port		= _port;
		handler		= _handler;

		String	multi_instance = System.getProperty( "MULTI_INSTANCE");

		if ( multi_instance != null && multi_instance.equalsIgnoreCase( "true" )){

			return;
		}

		active = true;
	}

	public static boolean
	initialiseAndProcess(
		int									_port,
		PluginManagerArgumentHandler		_handler,
		String[]							_args )
	{
		initialise( _port, _handler );

		return( process( null, _args ));
	}

	protected static boolean
	process(
		LoggerChannelListener	log,
		String[]				args )
	{
		if ( active ){

			if ( startListener( log )){

				return( false );

			}else{

				sendArguments( log, args );

				return( true );
			}
		}else{

			return( false );
		}
	}


	protected static boolean
	startListener(
		final LoggerChannelListener	log )
	{
		try{
			final ServerSocket server_socket = new ServerSocket( port, 50, InetAddress.getByName("127.0.0.1"));

			if ( log != null ){
				log.messageLogged(
					  LoggerChannel.LT_INFORMATION,
					  "SingleInstanceHandler: listening on 127.0.0.1:" + port + " for passed arguments");
			}

			Thread t =
				new Thread("Single Instance Handler")
				{
		    		@Override
				    public void
		    		run()
					{
		    		    while ( true ){

		    		    	Socket socket			= null;
		    		    	ObjectInputStream	ois	= null;

		    		    	try{
		    		    		socket = server_socket.accept();

		    		    		String address = socket.getInetAddress().getHostAddress();

		    		    		if ( !( address.equals("localhost") || address.equals("127.0.0.1"))){

		    		    			socket.close();

		    		    			continue;
		    		    		}

		    		    		ois = new ObjectInputStream( socket.getInputStream());

		    		    		ois.readInt();	// version

		    		    		String	header = (String)ois.readObject();

		    		    		if ( !header.equals( getHeader())){

		    		    			if ( log != null ){
		    		    				log.messageLogged(
		    		    					LoggerChannel.LT_ERROR,
		    		    					"SingleInstanceHandler: invalid header - " + header );
		    		    			}

		    		    			continue;
		    		    		}

		    		    		String[]	args = (String[])ois.readObject();

		    					String config_dir = System.getProperty( SystemProperties.SYSPROP_CONFIG_PATH, null );

		    					if ( config_dir != null ){

		    							// caller will have written args to a file

		    		    			String config_path 	= (String)ois.readObject();
		    		    			String file_name	= (String)ois.readObject();

		    		    			if ( !config_path.equals( config_dir )){

		    		    				throw( new Exception( "Called supplied incorrect config path: " + config_path ));
		    		    			}

		    		    			File cmd_file = FileUtil.newFile( config_dir, "tmp" , file_name ).getCanonicalFile();

		    		    			if ( !cmd_file.getParentFile().getParentFile().equals( FileUtil.newFile( config_dir ))){

		    		    				throw( new Exception( "Called supplied invalid file name: " + file_name ));
		    		    			}

		    		    			ObjectInputStream ois2 = new ObjectInputStream( FileUtil.newFileInputStream( cmd_file ));

		    		    			try{

		    		    				args = (String[])ois2.readObject();

		    		    			}finally{

		    		    				ois2.close();

		    		    				cmd_file.delete();
		    		    			}
		    					}

		    		    		handler.processArguments( args );

		    		    	}catch( Throwable e ){

		    		    		if ( log != null ){
		    		    			log.messageLogged( "SingleInstanceHandler: receive error", e );
		    		    		}

		    		    	}finally{

		    		    		if ( ois != null ){
		    		    			try{
		    		    				ois.close();

		    		    			}catch( Throwable e ){
		    		    			}
		    		    		}

		    		    		if ( socket != null ){
		    		    			try{
		    		    				socket.close();

		    		    			}catch( Throwable e ){
		    		    			}
		    		    		}
		    		    	}
		    		    }
					}
				};

			t.setDaemon( true );

			t.start();

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}

	protected static void
	sendArguments(
		LoggerChannelListener	log,
		String[]				args )
	{
		Socket	socket = null;

		try{
			socket = new Socket( "127.0.0.1", port );

			ObjectOutputStream	oos = new ObjectOutputStream( socket.getOutputStream());

			oos.writeInt( 0 );

			oos.writeObject( getHeader());

			oos.writeObject( args );

				// if we know the config dir then use more secure mechanism to pass args by writing
				// to a file (this proving we have write access to the directory at least)

			String config_dir = System.getProperty( SystemProperties.SYSPROP_CONFIG_PATH, null );

			if ( config_dir != null ){

				File	file = FileUtil.newFile( config_dir, "tmp" );

				file.mkdirs();

				file = File.createTempFile( "AZU" + RandomUtils.nextSecureAbsoluteLong(), ".tmp", file );

				ObjectOutputStream oos2 = new ObjectOutputStream( FileUtil.newFileOutputStream( file ));

				try{
					oos2.writeObject( args );

				}finally{

					oos2.close();
				}

				oos.writeObject( config_dir );

				oos.writeObject( file.getName());
			}

			oos.flush();

			if ( log != null ){

				log.messageLogged( LoggerChannel.LT_INFORMATION, "SingleInstanceHandler: arguments passed to existing process" );
			}
    	}catch( Throwable e ){

    		if ( log != null ){

    			log.messageLogged( "SingleInstanceHandler: send error", e );
    		}
    	}finally{

    		if ( socket != null ){
    			try{
    				socket.close();

    			}catch( Throwable e ){
    			}
    		}
    	}
	}

	protected static String
	getHeader()
	{
		return( SystemProperties.getApplicationName() + " Single Instance Handler" );
	}
}
