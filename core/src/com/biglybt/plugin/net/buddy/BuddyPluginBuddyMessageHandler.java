/*
 * Created on Apr 23, 2008
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

import java.io.File;
import java.util.*;

import com.biglybt.core.util.*;

public class
BuddyPluginBuddyMessageHandler
{
	private BuddyPluginBuddy		buddy;
	private File					store;

	private Map	config_map;
	private int	message_count;
	private int pending_deletes;

	private int	next_message_id;

	private CopyOnWriteList			listeners = new CopyOnWriteList();

	private BuddyPluginBuddyMessage	active_message;

	private long					last_failure;
	private long					last_pending_success;


	protected
	BuddyPluginBuddyMessageHandler(
		BuddyPluginBuddy		_buddy,
		File					_store )
	{
		buddy	= _buddy;
		store	= _store;

		loadConfig();

		if ( message_count > 0 ){

			buddy.persistentDispatchPending();
		}
	}

	public BuddyPluginBuddy
	getBuddy()
	{
		return( buddy );
	}

	public BuddyPluginBuddyMessage
	queueMessage(
		int		subsystem,
		Map		content,
		int		timeout_millis )

		throws BuddyPluginException
	{
		BuddyPluginBuddyMessage	message;

		boolean	dispatch_pending;

		synchronized( this ){

			int	id = next_message_id++;

			message =
				new BuddyPluginBuddyMessage(
						this, id, subsystem, content, timeout_millis, SystemTime.getCurrentTime());

			storeMessage( message );

			dispatch_pending = message_count == 1;
		}

		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginBuddyMessageListener)it.next()).messageQueued( message );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		if ( dispatch_pending ){

			buddy.persistentDispatchPending();
		}

		return( message );
	}

	protected void
	checkPersistentDispatch()
	{
		boolean	request_dispatch = false;

		synchronized( this ){

			long	now = SystemTime.getCurrentTime();

			if ( now < last_failure ){

				last_failure = now;
			}

			if ( now < last_pending_success ){

				last_pending_success = now;
			}

			if ( last_pending_success > 0 && now - last_pending_success >= BuddyPluginNetwork.PERSISTENT_MSG_RETRY_PERIOD ){

				request_dispatch = true;

			}else if ( active_message != null || message_count == 0 || last_failure == 0 ){

					// no messages pending

			}else{

				request_dispatch = now - last_failure >= BuddyPluginNetwork.PERSISTENT_MSG_RETRY_PERIOD;
			}
		}

		if ( request_dispatch ){

			buddy.persistentDispatchPending();
		}
	}

	protected void
	persistentDispatch()
	{
		checkPendingSuccess();

		synchronized( this ){

			if ( active_message != null || message_count == 0 ){

				return;
			}

			List	messages = (List)config_map.get( "messages" );

			Map		map = (Map)messages.get(0);

			try{
				active_message = restoreMessage( map );

			}catch( Throwable e ){

					// should never happen...

				Debug.out( "Failed to restore message, deleting it", e );

				messages.remove(0);

				try{
					saveConfig();

				}catch( Throwable f ){

					buddy.log( "Config save failed during delete of bad message", f );
				}
			}
		}

		boolean	request_ok = false;

		try{
			Map	request = active_message.getRequest();

			request_ok = true;

			buddy.sendMessage(
					active_message.getSubsystem(),
					request,
					active_message.getTimeout(),
					new BuddyPluginBuddyReplyListener()
					{
						@Override
						public void
						replyReceived(
							BuddyPluginBuddy		from_buddy,
							Map						reply )
						{
							BuddyPluginBuddyMessage message = active_message;

								// inform listeners before deleting message as it gives them one
								// last chance to do something with the message if they so desire

							Iterator it = listeners.iterator();

							boolean	processing_ok = true;

								// prematurely reduce message count when informing listeners
								// so they see the "correct" value

							try{
								synchronized( BuddyPluginBuddyMessageHandler.this ){

									pending_deletes++;
								}

								while( it.hasNext()){

									try{
										if ( !((BuddyPluginBuddyMessageListener)it.next()).deliverySucceeded( message, reply )){

											processing_ok = false;
										}

									}catch( Throwable e ){

										Debug.printStackTrace(e);
									}
								}
							}finally{

								synchronized( BuddyPluginBuddyMessageHandler.this ){

									pending_deletes--;
								}
							}
							if ( processing_ok ){

								message.delete();

							}else{
								synchronized( BuddyPluginBuddyMessageHandler.this ){

									boolean found = false;

									List	messages = (List)config_map.get( "messages" );

									if ( messages != null ){

										for ( int i=0;i<messages.size();i++){

											Map	msg = (Map)messages.get(i);

											if ( message.getID() == ((Long)msg.get( "id")).intValue()){

												found = true;

												messages.remove(i);

												try{
													writeReply( message, reply );

													List pending_success = (List)config_map.get( "pending_success" );

													if ( pending_success == null ){

														pending_success = new ArrayList();

														config_map.put( "pending_success", pending_success );
													}

													pending_success.add( msg );

													last_pending_success = SystemTime.getCurrentTime();

													buddy.log( "Message moved to pending success queue after listener failed" );

													saveConfig();

												}catch( Throwable e ){

													buddy.log( "Config save failed during message pending queueing", e );
												}

												break;
											}
										}
									}

									if ( !found ){

										buddy.log( "Failed to find message " + message.getID());
									}
								}
							}

							boolean messages_queued;

							synchronized( BuddyPluginBuddyMessageHandler.this ){

								active_message 	= null;

								messages_queued = message_count > 0;

								last_failure	= 0;
							}

							if ( messages_queued ){

								buddy.persistentDispatchPending();
							}
						}

						@Override
						public void
						sendFailed(
							BuddyPluginBuddy		to_buddy,
							BuddyPluginException	cause )
						{
							BuddyPluginBuddyMessage message = active_message;

							synchronized( BuddyPluginBuddyMessageHandler.this ){

								active_message 	= null;

								last_failure	= SystemTime.getCurrentTime();
							}

							reportFailed( message, cause, true );
						}
					});

		}catch( Throwable cause ){

			BuddyPluginBuddyMessage message = active_message;

			synchronized( this ){

				active_message 	= null;

				last_failure	= SystemTime.getCurrentTime();
			}

			boolean do_subsequent = true;

			if ( !request_ok && !( cause instanceof BuddyPluginPasswordException )){

				buddy.logMessage( "Message request unavailable, deleting message" );

				message.delete();

				boolean messages_queued = false;

				synchronized( this ){

					last_failure = 0;

					messages_queued = message_count > 0;
				}

				if ( messages_queued ){

					do_subsequent = false;

					buddy.persistentDispatchPending();
				}
			}

			reportFailed( message, cause, do_subsequent );
		}
	}

	protected void
	reportFailed(
		BuddyPluginBuddyMessage		message,
		Throwable					cause,
		boolean						do_subsequent )
	{
		BuddyPluginException b_cause;

		if ( cause instanceof BuddyPluginException ){

			b_cause = (BuddyPluginException)cause;

		}else{

			b_cause = new BuddyPluginException( "Failed to send message", cause );
		}

		reportFailedSupport( message, b_cause );

		if ( do_subsequent ){

			List	other_messages = new ArrayList();

			synchronized( this ){

				List	messages = (List)config_map.get( "messages" );

				for (int i=0;i<messages.size();i++){

					try{
						BuddyPluginBuddyMessage msg = restoreMessage((Map)messages.get(i));

						if ( msg.getID() != message.getID()){

							other_messages.add( msg );
						}
					}catch( Throwable e ){

					}
				}
			}

			if ( other_messages.size() > 0 ){

				BuddyPluginException o_cause = new BuddyPluginException( "Reporting probable failure to subsequent messages" );

				for (int i=0;i<other_messages.size();i++){

					reportFailedSupport((BuddyPluginBuddyMessage)other_messages.get(i), o_cause );
				}
			}
		}
	}

	protected void
	reportFailedSupport(
		BuddyPluginBuddyMessage		message,
		BuddyPluginException		cause )
	{
		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginBuddyMessageListener)it.next()).deliveryFailed( message, cause );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	checkPendingSuccess()
	{
		List	pending_messages = new ArrayList();

		boolean	save_pending = false;

		synchronized( this ){

			last_pending_success	= 0;

			List pending_success = (List)config_map.get( "pending_success" );

			if ( pending_success == null || pending_success.size() == 0 ){

				return;
			}

			Iterator it = pending_success.iterator();

			while( it.hasNext()){

				Map	map = (Map)it.next();

				try{
					pending_messages.add( restoreMessage( map ));

				}catch( Throwable e ){

					buddy.log( "Failed to restore message from pending success queue", e );

					it.remove();

					save_pending = true;
				}
			}
		}

		for ( int i=0;i<pending_messages.size();i++){

			BuddyPluginBuddyMessage message = (BuddyPluginBuddyMessage)pending_messages.get(i);

			try{
				Map	reply = message.getReply();

				Iterator it = listeners.iterator();

				boolean	processing_ok = true;

				while( it.hasNext()){

					try{
						if ( !((BuddyPluginBuddyMessageListener)it.next()).deliverySucceeded( message, reply )){

							processing_ok = false;
						}

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}

				if ( processing_ok ){

					message.delete();

				}else{
					synchronized( this ){

						last_pending_success = SystemTime.getCurrentTime();
					}
				}
			}catch( BuddyPluginPasswordException e ){

				buddy.log( "Failed to restore message reply", e );

					// we don't want to delete the message if failed due to password issue

			}catch( Throwable e ){

				buddy.log( "Failed to restore message reply - deleting message", e );

				message.delete();
			}
		}

		if ( save_pending ){

			try{
				saveConfig();

			}catch( Throwable e ){

				buddy.log( "Save failed during pending success processing", e );
			}
		}
	}

	public int
	getMessageCount()
	{
		synchronized( this ){

			return( message_count - pending_deletes );
		}
	}

	protected void
	deleteMessage(
		BuddyPluginBuddyMessage		message )
	{
		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginBuddyMessageListener)it.next()).messageDeleted( message );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		synchronized( this ){

			String[]	keys = { "messages", "pending_success", "explicit" };

			for (int i=0;i<keys.length;i++){

				List	messages = (List)config_map.get( keys[i] );

				if ( messages != null ){

					boolean	found = false;

					for ( int j=0;j<messages.size();j++){

						Map	msg = (Map)messages.get(j);

						if ( message.getID() == ((Long)msg.get( "id")).intValue()){

							messages.remove(j);

							found	= true;

							break;
						}
					}

					if ( found ){

						deleteRequest( message );

						deleteReply( message );

						try{
							saveConfig();

						}catch( Throwable e ){

							buddy.log( "Config save failed during message delete", e );
						}

						return;
					}
				}
			}
		}
	}

	protected void
	destroy()
	{
		synchronized( this ){

			config_map.clear();

			try{
				saveConfig();

			}catch( Throwable e ){

				buddy.log( "Config save failed during destroy", e );
			}
		}
	}

	protected void
	writeRequest(
		BuddyPluginBuddyMessage		message,
		Map							content )

		throws BuddyPluginException
	{
		writeContent( message.getID() + ".req.dat", content );
	}

	protected Map
	readRequest(
		BuddyPluginBuddyMessage		message )

		throws BuddyPluginException
	{
		return( readContent( message.getID() + ".req.dat" ));
	}

	protected void
	writeReply(
		BuddyPluginBuddyMessage		message,
		Map							content )

		throws BuddyPluginException
	{
		writeContent( message.getID() + ".rep.dat", content );
	}

	protected Map
	readReply(
		BuddyPluginBuddyMessage		message )

		throws BuddyPluginException
	{
		return( readContent( message.getID() + ".rep.dat" ));
	}

	protected void
	writeContent(
		String						target_str,
		Map							content )

		throws BuddyPluginException
	{
		if ( !store.exists()){

			if ( !store.mkdirs()){

				throw( new BuddyPluginException( "Failed to create " + store ));
			}
		}

		File target = FileUtil.newFile( store, target_str );

		try{

			BuddyPlugin.CryptoResult result = buddy.encrypt( BEncoder.encode( content ));

			Map	store_map = new HashMap();

			store_map.put( "pk", buddy.getPlugin().getPublicKey());
			store_map.put( "data", result.getPayload());

			if ( !buddy.writeConfigFile( target, store_map )){

				throw( new BuddyPluginException( "failed to write " + target ));
			}

		}catch( BuddyPluginException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new BuddyPluginException( "Failed to write message", e ));
		}
	}

	protected Map
	readContent(
		String						target_str )

		throws BuddyPluginException
	{
		File target = FileUtil.newFile( store, target_str );

		if ( !target.exists()){

			throw( new BuddyPluginException( "Failed to read persisted message - " + target + " doesn't exist" ));
		}

		Map	map = buddy.readConfigFile( target );

		if ( map.size() == 0 ){

			throw( new BuddyPluginException( "Failed to read persisted message file " + target ));
		}

		try{
			String	pk = new String((byte[])map.get("pk"));

			if ( !pk.equals( buddy.getPlugin().getPublicKey())){

				throw( new BuddyPluginException( "Can't decrypt message as key changed" ));
			}

			byte[]	data = (byte[])map.get( "data" );

			return( BDecoder.decode( buddy.decrypt( data ).getPayload()));

		}catch( BuddyPluginException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new BuddyPluginException( "Failed to read message", e ));
		}
	}

	protected void
	deleteRequest(
		BuddyPluginBuddyMessage		message )
	{
		deleteRequest( message.getID());
	}

	protected void
	deleteRequest(
		int			id  )
	{
		File target = FileUtil.newFile( store, id + ".req.dat" );

		if ( target.exists()){

			if ( !target.delete()){

				Debug.out( "Failed to delete " + target );
			}
		}
	}

	protected void
	deleteReply(
		BuddyPluginBuddyMessage		message )
	{
		deleteReply( message.getID());
	}

	protected void
	deleteReply(
		int			id  )
	{
		File target = FileUtil.newFile( store, id + ".rep.dat" );

		if ( target.exists()){

			if ( !target.delete()){

				Debug.out( "Failed to delete " + target );
			}
		}
	}

	public BuddyPluginBuddyMessage
	storeExplicitMessage(
		int		type,
		Map		msg )
	{
		BuddyPluginBuddyMessage	message;

		synchronized( this ){

			int	id = next_message_id++;

			try{
				message =
					new BuddyPluginBuddyMessage(
							this, id, BuddyPluginNetwork.SUBSYSTEM_MSG_TYPE_BASE + type, msg, 0, SystemTime.getCurrentTime());

				storeExplicitMessage( message );

			}catch( Throwable e ){

				buddy.log( "Failed to store explicit message", e );

				return( null );
			}
		}

		return( message );
	}

	public List<BuddyPluginBuddyMessage>
	retrieveExplicitMessages(
		int		type )
	{
		List<BuddyPluginBuddyMessage>	result = new ArrayList<>();

		synchronized( this ){

			List<Map<String,Object>>	messages = (List<Map<String,Object>>)config_map.get( "explicit" );

			if ( messages != null ){

				for (int i=0;i<messages.size();i++){

					try{
						BuddyPluginBuddyMessage msg = restoreMessage(messages.get(i));

						if ( msg.getSubsystem() == BuddyPluginNetwork.SUBSYSTEM_MSG_TYPE_BASE + type ){

							result.add( msg );
						}
					}catch( Throwable e ){

						buddy.log( "Failed to restore message", e );
					}
				}
			}
		}

		return( result );
	}

	protected void
	storeExplicitMessage(
		BuddyPluginBuddyMessage		msg )

		throws BuddyPluginException
	{
		storeMessageSupport( msg, "explicit" );
	}

	protected void
	storeMessage(
		BuddyPluginBuddyMessage		msg )

		throws BuddyPluginException
	{
		storeMessageSupport( msg, "messages" );
	}

	protected void
	storeMessageSupport(
		BuddyPluginBuddyMessage		msg,
		String						key )

		throws BuddyPluginException
	{
		List	messages = (List)config_map.get( key );

		if ( messages == null ){

			messages = new ArrayList();

			config_map.put( key, messages );
		}

		Map map = new HashMap();

		map.put( "id", new Long( msg.getID()));
		map.put( "ss", new Long( msg.getSubsystem()));
		map.put( "to", new Long( msg.getTimeout()));
		map.put( "cr", new Long( msg.getCreateTime()));

		messages.add( map );

		saveConfig();
	}

	protected BuddyPluginBuddyMessage
	restoreMessage(
		Map			map )

		throws BuddyPluginException
	{
		int	id = ((Long)map.get( "id" )).intValue();
		int	ss = ((Long)map.get( "ss" )).intValue();
		int	to = ((Long)map.get( "to" )).intValue();

		long	cr = ((Long)map.get( "cr" )).longValue();

		return( new BuddyPluginBuddyMessage( this, id, ss, null, to, cr ));
	}

	protected void
	loadConfig()
	{
		File	config_file = FileUtil.newFile( store, "messages.dat" );

		if ( config_file.exists()){

			config_map = buddy.readConfigFile( config_file );

		}else{

			config_map = new HashMap();
		}

		List	messages = (List)config_map.get( "messages" );

		if ( messages != null ){

			message_count = messages.size();

			if ( message_count > 0 ){

				Map	last_msg = (Map)messages.get( message_count - 1 );

				next_message_id = ((Long)last_msg.get( "id")).intValue() + 1;
			}
		}

		List	pending_success = (List)config_map.get( "pending_success" );

		if ( pending_success != null ){

			int ps_count = pending_success.size();

			if ( ps_count > 0 ){

				Map	last_msg = (Map)pending_success.get( ps_count - 1 );

				next_message_id = Math.max( next_message_id, ((Long)last_msg.get( "id")).intValue() + 1 );

				synchronized( this ){

					last_pending_success = SystemTime.getCurrentTime();
				}
			}
		}

		List	explicit = (List)config_map.get( "explicit" );

		if ( explicit != null ){

			int exp_count = explicit.size();

			if ( exp_count > 0 ){

				Map	last_msg = (Map)explicit.get( exp_count - 1 );

				next_message_id = Math.max( next_message_id, ((Long)last_msg.get( "id")).intValue() + 1 );
			}
		}
	}

	protected void
	saveConfig()

		throws BuddyPluginException
	{
		File	config_file = FileUtil.newFile( store, "messages.dat" );

		List	messages 	= (List)config_map.get( "messages" );
		List	pending 	= (List)config_map.get( "pending_success" );
		List	explicit 	= (List)config_map.get( "explicit" );

		if ( 	( messages == null || messages.size() == 0 ) &&
				( pending == null || pending.size() == 0 ) &&
				( explicit == null || explicit.size() == 0 )){

			if ( store.exists()){

				File[]	 files = store.listFiles();

				for (int i=0;i<files.length;i++ ){

					files[i].delete();
				}

				store.delete();
			}

			message_count = 0;

			next_message_id	= 0;

		}else{

			if ( !store.exists()){

				if ( !store.mkdirs()){

					throw( new BuddyPluginException( "Failed to create " + store ));
				}
			}

			if ( !buddy.writeConfigFile( config_file, config_map )){

				throw( new BuddyPluginException( "Failed to write" + config_file ));
			}

			message_count = messages==null?0:messages.size();
		}
	}

	public void
	addListener(
		BuddyPluginBuddyMessageListener		listener )
	{
		listeners.add( listener );
	}

	public void
	removeListener(
		BuddyPluginBuddyMessageListener		listener )
	{
		listeners.remove( listener );
	}
}
