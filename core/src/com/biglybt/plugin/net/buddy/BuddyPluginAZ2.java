/*
 * Created on Apr 10, 2008
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


package com.biglybt.plugin.net.buddy;

import java.util.*;

import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.ui.UIManagerEvent;

public class
BuddyPluginAZ2
{
	public static final int RT_AZ2_REQUEST_MESSAGE		= 1;
	public static final int RT_AZ2_REPLY_MESSAGE		= 2;

	public static final int RT_AZ2_REQUEST_SEND_TORRENT	= 3;
	public static final int RT_AZ2_REPLY_SEND_TORRENT	= 4;

	public static final int RT_AZ2_REQUEST_CHAT			= 5;
	public static final int RT_AZ2_REPLY_CHAT			= 6;

	public static final int RT_AZ2_REQUEST_TRACK		= 7;
	public static final int RT_AZ2_REPLY_TRACK			= 8;

	public static final int RT_AZ2_REQUEST_RSS			= 9;
	public static final int RT_AZ2_REPLY_RSS			= 10;

	public static final int CHAT_MSG_TYPE_TEXT						= 1;
	public static final int CHAT_MSG_TYPE_PARTICIPANTS_ADDED		= 2;
	public static final int CHAT_MSG_TYPE_PARTICIPANTS_REMOVED		= 3;


	private static final int SEND_TIMEOUT = 2*60*1000;

	private BuddyPlugin		plugin;

	private Map				chats 		= new HashMap();

	private CopyOnWriteList	listeners = new CopyOnWriteList();

	private CopyOnWriteList	track_listeners = new CopyOnWriteList();

	protected
	BuddyPluginAZ2(
		BuddyPlugin		_plugin )
	{
		plugin	= _plugin;

		plugin.addRequestListener(
				new BuddyPluginBuddyRequestListener()
				{
					@Override
					public Map
					requestReceived(
						BuddyPluginBuddy	from_buddy,
						int					subsystem,
						Map					request )

						throws BuddyPluginException
					{
						if ( subsystem == BuddyPlugin.SUBSYSTEM_AZ2 ){

							if ( !from_buddy.isAuthorised()){

								throw( new BuddyPluginException( "Unauthorised" ));
							}

							return( processAZ2Request( from_buddy, request ));
						}

						return( null );
					}

					@Override
					public void
					pendingMessages(
						BuddyPluginBuddy[]	from_buddies )
					{
					}
				});
	}

	protected Map
	processAZ2Request(
		final BuddyPluginBuddy	from_buddy,
		Map						request )

		throws BuddyPluginException
	{
		logMessage( "AZ2 request received: " + from_buddy.getString() + " -> " + request );

		int	type = ((Long)request.get( "type" )).intValue();

		Map	reply = new HashMap();

		if ( type == RT_AZ2_REQUEST_MESSAGE ){

			try{
				String	msg = new String( (byte[])request.get( "msg" ), "UTF8" );

				from_buddy.setLastMessageReceived( msg );

			}catch( Throwable e ){

			}

			reply.put( "type", new Long( RT_AZ2_REPLY_MESSAGE ));

		}else if (  type == RT_AZ2_REQUEST_SEND_TORRENT ){

			try{
				final Torrent	torrent = plugin.getPluginInterface().getTorrentManager().createFromBEncodedData((byte[])request.get( "torrent" ));

				new AEThread2( "torrentAdder", true )
				{
					@Override
					public void
					run()
					{
						PluginInterface pi = plugin.getPluginInterface();

						String msg = pi.getUtilities().getLocaleUtilities().getLocalisedMessageText(
								"azbuddy.addtorrent.msg",
								new String[]{ from_buddy.getName(), torrent.getName() });

						long res = pi.getUIManager().showMessageBox(
										"azbuddy.addtorrent.title",
										"!" + msg + "!",
										UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );

						if ( res == UIManagerEvent.MT_YES ){

							pi.getUIManager().openTorrent( torrent );
						}
					}
				}.start();

				reply.put( "type", new Long( RT_AZ2_REPLY_SEND_TORRENT ));

			}catch( Throwable e ){

				throw( new BuddyPluginException( "Torrent receive failed " + type ));
			}
		}else if (  type == RT_AZ2_REQUEST_CHAT ){

			Map msg = (Map)request.get( "msg" );

			String	id = new String((byte[])msg.get( "id" ));

			chatInstance	chat;
			boolean			new_chat = false;

			synchronized( chats ){

				 chat = (chatInstance)chats.get( id );

				 if ( chat == null ){

					 if ( chats.size() > 32 ){

						 throw( new BuddyPluginException( "Too many chats" ));
					 }

					 chat = new chatInstance( id );

					 chats.put( id, chat );

					 new_chat = true;
				 }
			}

			if ( new_chat ){

				informCreated( chat );
			}

			chat.addParticipant( from_buddy );

			chat.process( from_buddy, msg );

			reply.put( "type", new Long( RT_AZ2_REPLY_CHAT ));

		}else if (  type == RT_AZ2_REQUEST_TRACK ){

			Map msg = (Map)request.get( "msg" );

			Iterator it = track_listeners.iterator();

			boolean	ok = false;

			while( it.hasNext()){

				try{

					Map res = ((BuddyPluginAZ2TrackerListener)it.next()).messageReceived( from_buddy, msg );

					if ( res != null ){

						reply.put( "msg", res );
						reply.put( "type", new Long( RT_AZ2_REPLY_TRACK ));

						ok = true;

						break;
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			if ( !ok ){

				throw( new BuddyPluginException( "Unhandled request type " + type ));
			}
		}else if (  type == RT_AZ2_REQUEST_RSS ){

			try{
				Map<String,Object> res = new HashMap<>();

				reply.put( "msg", res );
				reply.put( "type", new Long( RT_AZ2_REPLY_RSS ));

				Map msg = (Map)request.get( "msg" );

				String category = new String((byte[])msg.get( "cat"), "UTF-8" );

				byte[] hash	= (byte[])msg.get( "hash" );

				if ( hash == null ){

					byte[]	if_mod 	= (byte[])msg.get( "if_mod" );

					BuddyPlugin.feedDetails feed = plugin.getRSS( from_buddy, category, if_mod==null?null:new String( if_mod, "UTF-8" ));

					res.put( "rss", feed.getContent());

					res.put( "last_mod", feed.getLastModified());

				}else{

					res.put( "torrent", plugin.getRSSTorrent( from_buddy, category, hash ));
				}
			}catch( BuddyPluginException e  ){

				throw( e );

			}catch( Throwable e ){

				throw( new BuddyPluginException( "Failed to handle rss", e ));
			}
		}else{

			throw( new BuddyPluginException( "Unrecognised request type " + type ));
		}

		logMessage( "AZ2 reply sent: " + from_buddy.getString() + " <- " + reply );

		return( reply );
	}

	public chatInstance
	createChat(
		BuddyPluginBuddy[]		buddies )
	{
		byte[]	id_bytes = new byte[20];

		RandomUtils.SECURE_RANDOM.nextBytes( id_bytes );

		String	id = Base32.encode( id_bytes );

		chatInstance	chat;

		synchronized( chats ){

			chat = new chatInstance( id );

			chats.put( id, chat );
		}

		logMessage( "Chat " + chat.getID() + " created" );

		informCreated( chat );

		chat.addParticipants( buddies, true );

		return( chat );
	}

	protected void
	destroyChat(
		chatInstance	chat )
	{
		synchronized( chats ){

			chats.remove( chat.getID());
		}

		logMessage( "Chat " + chat.getID() + " destroyed" );

		informDestroyed( chat );
	}

	protected void
	informCreated(
		chatInstance		chat )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			((BuddyPluginAZ2Listener)it.next()).chatCreated( chat );
		}
	}

	protected void
	informDestroyed(
		chatInstance		chat )
	{
		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			((BuddyPluginAZ2Listener)it.next()).chatDestroyed( chat );
		}
	}

	public void
	sendAZ2Message(
		BuddyPluginBuddy	buddy,
		String				msg )
	{
		try{
			Map	request = new HashMap();

			request.put( "type", new Long( RT_AZ2_REQUEST_MESSAGE ));
			request.put( "msg", msg.getBytes());

			sendMessage( buddy, request );

		}catch( Throwable e ){

			logMessageAndPopup( "Send message failed", e );
		}
	}

	protected void
	sendAZ2Chat(
		BuddyPluginBuddy	buddy,
		Map					msg )
	{
		try{
			Map	request = new HashMap();

			request.put( "type", new Long( RT_AZ2_REQUEST_CHAT ));
			request.put( "msg", msg );

			sendMessage( buddy, request );

		}catch( Throwable e ){

			logMessageAndPopup( "Send message failed", e );
		}
	}

	public void
	sendAZ2Torrent(
		Torrent				torrent,
		BuddyPluginBuddy	buddy )
	{
		try{

			Map	request = new HashMap();

			request.put( "type", new Long( RT_AZ2_REQUEST_SEND_TORRENT ));
			request.put( "torrent", torrent.writeToBEncodedData());

			sendMessage( buddy, request );

		}catch( Throwable e ){

			logMessageAndPopup( "Send torrent failed", e );
		}
	}

	public void
	sendAZ2TrackerMessage(
		BuddyPluginBuddy						buddy,
		Map										msg,
		final BuddyPluginAZ2TrackerListener		listener )
	{
		logMessage( "AZ2 request sent: " + buddy.getString() + " <- " + msg );

		try{
			Map	request = new HashMap();

			request.put( "type", new Long( RT_AZ2_REQUEST_TRACK ));
			request.put( "msg", msg );

			buddy.sendMessage(
				BuddyPlugin.SUBSYSTEM_AZ2,
				request,
				SEND_TIMEOUT,
				new BuddyPluginBuddyReplyListener()
				{
					@Override
					public void
					replyReceived(
						BuddyPluginBuddy		from_buddy,
						Map						reply )
					{
						int type = ((Long)reply.get( "type")).intValue();

						if ( type != RT_AZ2_REPLY_TRACK ){

							sendFailed( from_buddy, new BuddyPluginException( "Mismatched reply type" ));
						}

						listener.messageReceived( from_buddy, (Map)reply.get( "msg" ));
					}

					@Override
					public void
					sendFailed(
						BuddyPluginBuddy		to_buddy,
						BuddyPluginException	cause )
					{
						listener.messageFailed( to_buddy, cause );
					}
				});

		}catch( Throwable e ){

			logMessageAndPopup( "Send message failed", e );
		}
	}

	public void
	sendAZ2RSSMessage(
		BuddyPluginBuddy						buddy,
		Map										msg,
		final BuddyPluginAZ2TrackerListener		listener )
	{
		logMessage( "AZ2 request sent: " + buddy.getString() + " <- " + msg );

		try{
			Map	request = new HashMap();

			request.put( "type", new Long( RT_AZ2_REQUEST_RSS ));
			request.put( "msg", msg );

			buddy.sendMessage(
				BuddyPlugin.SUBSYSTEM_AZ2,
				request,
				SEND_TIMEOUT,
				new BuddyPluginBuddyReplyListener()
				{
					@Override
					public void
					replyReceived(
						BuddyPluginBuddy		from_buddy,
						Map						reply )
					{
						int type = ((Long)reply.get( "type")).intValue();

						if ( type != RT_AZ2_REPLY_RSS ){

							sendFailed( from_buddy, new BuddyPluginException( "Mismatched reply type" ));
						}

						listener.messageReceived( from_buddy, (Map)reply.get( "msg" ));
					}

					@Override
					public void
					sendFailed(
						BuddyPluginBuddy		to_buddy,
						BuddyPluginException	cause )
					{
						listener.messageFailed( to_buddy, cause );
					}
				});

		}catch( Throwable e ){

			logMessage( "Send message failed", e );
		}
	}

	protected void
	sendMessage(
		BuddyPluginBuddy	buddy,
		Map					request )

		throws BuddyPluginException
	{
		logMessage( "AZ2 request sent: " + buddy.getString() + " <- " + request );

		buddy.getMessageHandler().queueMessage(
				BuddyPlugin.SUBSYSTEM_AZ2,
				request,
				SEND_TIMEOUT );
	}

	public void
	addListener(
		BuddyPluginAZ2Listener		listener )
	{
		listeners.add( listener );
	}

	public void
	removeListener(
		BuddyPluginAZ2Listener		listener )
	{
		listeners.remove( listener );
	}

	public void
	addTrackerListener(
		BuddyPluginAZ2TrackerListener		listener )
	{
		track_listeners.add( listener );
	}

	public void
	removeTrackerListener(
		BuddyPluginAZ2TrackerListener		listener )
	{
		track_listeners.remove( listener );
	}

	protected void
	logMessageAndPopup(
		String		str,
		Throwable	e )
	{
		logMessageAndPopup( str + ": " + Debug.getNestedExceptionMessage(e));
	}

	protected void
	logMessageAndPopup(
		String		str )
	{
		logMessage( str );

		plugin.getPluginInterface().getUIManager().showMessageBox(
			"azbuddy.msglog.title", "!" + str + "!", UIManagerEvent.MT_OK );
	}

	protected void
	logMessage(
		String		str )
	{
		plugin.logMessage( str );
	}

	protected void
	logMessage(
		String		str,
		Throwable 	e )
	{
		plugin.logMessage( str + ": " + Debug.getNestedExceptionMessage(e));
	}

	public class
	chatInstance
		extends BuddyPluginAdapter
	{
		private String		id;

		private Map				participants 	= new HashMap();
		private CopyOnWriteList	listeners 		= new CopyOnWriteList();

		private List			history			= new ArrayList();

		protected
		chatInstance(
			String		_id )
		{
			id		= _id;

			plugin.addListener( this );
		}

		public String
		getID()
		{
			return( id );
		}

		@Override
		public void
		buddyAdded(
			BuddyPluginBuddy	buddy )
		{
			buddyChanged( buddy );
		}

		@Override
		public void
		buddyRemoved(
			BuddyPluginBuddy	buddy )
		{
			chatParticipant p = getParticipant( buddy );

			if ( p != null ){

				Iterator it = listeners.iterator();

				while( it.hasNext()){

					try{
						((BuddyPluginAZ2ChatListener)it.next()).participantRemoved( p );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}
		}

		@Override
		public void
		buddyChanged(
			BuddyPluginBuddy	buddy )
		{
			chatParticipant p = getParticipant( buddy );

			if ( p != null ){

				Iterator it = listeners.iterator();

				while( it.hasNext()){

					try{
						((BuddyPluginAZ2ChatListener)it.next()).participantChanged( p );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}
		}

		protected void
		process(
			BuddyPluginBuddy	from_buddy,
			Map					msg )
		{
			chatParticipant p = getOrAddParticipant( from_buddy );

			int	type = ((Long)msg.get( "type")).intValue();

			if ( type == CHAT_MSG_TYPE_TEXT ){

				Iterator it = listeners.iterator();

				synchronized( history ){

					history.add( new chatMessage( p.getName(), msg ));

					if ( history.size() > 128 ){

						history.remove(0);
					}
				}

				while( it.hasNext()){

					try{
						((BuddyPluginAZ2ChatListener)it.next()).messageReceived( p, msg );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}else if ( type == CHAT_MSG_TYPE_PARTICIPANTS_ADDED ){

				List added = (List)msg.get( "p" );

				for (int i=0;i<added.size();i++){

					Map	participant = (Map)added.get(i);

					String pk = new String((byte[])participant.get( "pk" ));

					if ( !pk.equals( plugin.getPublicKey())){

						addParticipant( pk );
					}
				}
			}
		}

		public void
		sendMessage(
			Map		msg )
		{
			msg.put( "type", new Long( CHAT_MSG_TYPE_TEXT ));

			sendMessageBase( msg );
		}

		protected void
		sendMessageBase(
			Map		msg )
		{
			Map	ps;

			synchronized( participants ){

				ps = new HashMap( participants );
			}

			msg.put( "id", id );

			Iterator it = ps.values().iterator();

			while( it.hasNext()){

				chatParticipant participant = (chatParticipant)it.next();

				if ( participant.isAuthorised()){

					sendAZ2Chat( participant.getBuddy(), msg );
				}
			}
		}

		public chatMessage[]
		getHistory()
		{
			synchronized( history ){

				chatMessage[]	res = new chatMessage[history.size()];

				history.toArray( res );

				return( res );
			}
		}

		protected chatParticipant
		getOrAddParticipant(
			BuddyPluginBuddy	buddy )
		{
			return( addParticipant( buddy ));
		}

		public chatParticipant
		addParticipant(
			String		pk )
		{
			chatParticipant p;

			BuddyPluginBuddy buddy = plugin.getBuddyFromPublicKey( pk );

			synchronized( participants ){

				p = (chatParticipant)participants.get( pk );

				if ( p != null ){

					return( p );
				}

				if ( buddy == null ){

					p = new chatParticipant( pk );

				}else{

					p = new chatParticipant( buddy );
				}

				participants.put( pk, p );
			}

			Iterator it = listeners.iterator();

			while( it.hasNext()){

				try{
					((BuddyPluginAZ2ChatListener)it.next()).participantAdded( p );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			return( p );
		}

		public chatParticipant
		addParticipant(
			BuddyPluginBuddy	buddy )
		{
			return( addParticipant( buddy.getPublicKey()));
		}

		public void
		addParticipants(
			BuddyPluginBuddy[]		buddies,
			boolean					inform_others )
		{
			for (int i=0;i<buddies.length;i++ ){

				addParticipant( buddies[i] );
			}

			if ( inform_others ){

				Map	msg = new HashMap();

				msg.put( "type", new Long( CHAT_MSG_TYPE_PARTICIPANTS_ADDED ));

				List	added = new ArrayList();

				msg.put( "p", added );

				for ( int i=0;i<buddies.length;i++){

					Map map = new HashMap();

					map.put( "pk", buddies[i].getPublicKey());

					added.add( map );
				}

				sendMessageBase( msg );
			}
		}

		protected chatParticipant
		getParticipant(
			BuddyPluginBuddy	b )
		{
			String	pk = b.getPublicKey();

			synchronized( participants ){

				chatParticipant p = (chatParticipant)participants.get( pk );

				if ( p != null ){

					return( p );
				}
			}

			return( null );
		}

		public chatParticipant[]
		getParticipants()
		{
			synchronized( participants ){

				chatParticipant[]	res = new chatParticipant[participants.size()];

				participants.values().toArray( res );

				return( res );
			}
		}

		protected void
		removeParticipant(
			chatParticipant		p )
		{
			boolean	removed;

			synchronized( participants ){

				removed = participants.remove( p.getPublicKey()) != null;
			}

			if ( removed ){

				Iterator it = listeners.iterator();

				while( it.hasNext()){

					try{
						((BuddyPluginAZ2ChatListener)it.next()).participantRemoved( p );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}
		}

		public void
		destroy()
		{
			plugin.removeListener( this );

			destroyChat( this );
		}

		public void
		addListener(
			BuddyPluginAZ2ChatListener		listener )
		{
			listeners.add( listener );
		}

		public void
		removeListener(
			BuddyPluginAZ2ChatListener		listener )
		{
			listeners.remove( listener );
		}
	}

	public static class
	chatParticipant
	{
		private BuddyPluginBuddy	buddy;
		private String				public_key;

		protected
		chatParticipant(
			BuddyPluginBuddy		_buddy )
		{
			buddy = _buddy;
		}

		protected
		chatParticipant(
			String			pk  )
		{
			public_key = pk;
		}

		public boolean
		isAuthorised()
		{
			return( buddy != null );
		}

		public BuddyPluginBuddy
		getBuddy()
		{
			return( buddy );
		}

		public String
		getPublicKey()
		{
			if ( buddy != null ){

				return( buddy.getPublicKey());
			}

			return( public_key );
		}

		public String
		getName()
		{
			if ( buddy != null ){

				return( buddy.getName());
			}

			return( public_key );
		}
	}

	public static class
	chatMessage
	{
		private String		nick;
		private Map			map;

		protected
		chatMessage(
			String			_nick,
			Map				_map )
		{
			nick		= _nick;
			map			= _map;
		}

		public String
		getNickName()
		{
			return( nick );
		}

		public Map
		getMessage()
		{
			return( map );
		}
	}
}
