/*
 * Created on Dec 5, 2012
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


package com.biglybt.core.pairing.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.AlgorithmParameters;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.gudy.bouncycastle.crypto.agreement.srp.SRP6Server;
import org.gudy.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator;
import org.gudy.bouncycastle.crypto.digests.SHA256Digest;
import org.json.simple.JSONObject;

import com.biglybt.core.Core;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.nat.DHTNATPuncher;
import com.biglybt.core.dht.nat.DHTNATPuncherListener;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.nat.NATTraversalHandler;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.pairing.PairedServiceRequestHandler;
import com.biglybt.core.pairing.impl.PairingManagerImpl.PairedServiceImpl;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.util.JSONUtils;

public class
PairingManagerTunnelHandler
{
	private static final String DEFAULT_IDENTITY = "vuze";

	private BigInteger N_3072;
	private BigInteger G_3072;

	private byte[] 		SRP_SALT;
	private BigInteger 	SRP_VERIFIER;

	final PairingManagerImpl		manager;
	private final Core core;

	private boolean	started = false;
	private boolean	active	= false;

	private final List<DHTNATPuncher>	nat_punchers_ipv4 = new ArrayList<>();
	private final List<DHTNATPuncher>	nat_punchers_ipv6 = new ArrayList<>();

	private int	last_punchers_registered = 0;

	private TimerEvent	update_event;


	private final Map<String,Object[]> local_server_map =
		new LinkedHashMap<String,Object[]>( 10, 0.75f, true )
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,Object[]> eldest)
			{
				return size() > 10;
			}
		};

	private long 	last_server_create_time;
	private long 	last_server_agree_time;
	private int		total_servers;

	private long 	last_local_server_create_time;
	private long 	last_local_server_agree_time;
	private int		total_local_servers;

	private static final int MAX_TUNNELS	= 10;

	final Map<String,PairManagerTunnel>	tunnels = new HashMap<>();

	private String	init_fail;


	protected
	PairingManagerTunnelHandler(
		PairingManagerImpl	_manager,
		Core _core )
	{
		manager		= _manager;
		core		= _core;

		CryptoManager.SRPParameters params = CryptoManagerFactory.getSingleton().getSRPParameters();

		if ( params != null ){

			SRP_SALT		= params.getSalt();
			SRP_VERIFIER	= params.getVerifier();
		}
	}

	public void
	setSRPPassword(
		char[]		password )
	{
		if ( password == null || password.length == 0 ){

			SRP_SALT		= null;
			SRP_VERIFIER	= null;

			CryptoManagerFactory.getSingleton().setSRPParameters(  null, null );

		}else{

			start();

			try{
				byte[] I = DEFAULT_IDENTITY.getBytes( "UTF-8" );
				byte[] P = new String(password).getBytes( "UTF-8" );

				byte[] salt = new byte[16];

				RandomUtils.nextSecureBytes( salt );

				SRP6VerifierGenerator gen = new SRP6VerifierGenerator();

				gen.init( N_3072, G_3072, new SHA256Digest());

				BigInteger verifier = gen.generateVerifier( salt, I, P );

				CryptoManagerFactory.getSingleton().setSRPParameters( salt, verifier );

				SRP_SALT		= salt;
				SRP_VERIFIER 	= verifier;

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		updateActive();
	}

	private void
	start()
	{
		synchronized( this ){

			if ( started ){

				return;
			}

			started = true;
		}

		N_3072 = fromHex(
				"FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
				"8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
				"302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
				"A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
				"49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
				"FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
				"670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
				"180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
				"3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
				"04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
				"B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
				"1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
				"BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
				"E0FD108E 4B82D120 A93AD2CA FFFFFFFF FFFFFFFF" );

		G_3072 = BigInteger.valueOf(5);

		try{
			PluginInterface dht_pi = core.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

			if ( dht_pi == null ){

				throw( new Exception( "DHT Plugin not found" ));
			}

			DHTPlugin dht_plugin = (DHTPlugin)dht_pi.getPlugin();

			if ( !dht_plugin.isEnabled()){

				throw( new Exception( "DHT Plugin is disabled" ));

			}

			DHT[] dhts = dht_plugin.getDHTs();

			List<DHTNATPuncher> punchers = new ArrayList<>();

			for ( DHT dht: dhts ){

				int net = dht.getTransport().getNetwork();

				if ( net == DHT.NW_AZ_MAIN ){

					DHTNATPuncher primary_puncher = dht.getNATPuncher();

					if ( primary_puncher != null ){

						punchers.add( primary_puncher );

						nat_punchers_ipv4.add( primary_puncher );

						for ( int i=1;i<=2; i++ ){

							DHTNATPuncher puncher = primary_puncher.getSecondaryPuncher();

							punchers.add( puncher );

							nat_punchers_ipv4.add( puncher );
						}
					}
				}else if ( net == DHT.NW_AZ_MAIN_V6 ){

					/*
					 * no point in this atm as we don't support v6 tunnels

					DHTNATPuncher puncher = dht.getNATPuncher();

					if ( puncher != null ){

						punchers.add( puncher );

						nat_punchers_ipv6.add( puncher );

						puncher = puncher.getSecondaryPuncher();

						punchers.add( puncher );

						nat_punchers_ipv6.add( puncher );
					}
					*/
				}
			}

			if ( punchers.size() == 0 ){

				throw( new Exception( "No suitable DHT instances available" ));
			}

			for ( DHTNATPuncher p: punchers ){

				p.forceActive( true );

				p.addListener(
					new DHTNATPuncherListener()
					{
						@Override
						public void
						rendezvousChanged(
							DHTTransportContact rendezvous )
						{
							System.out.println( "active: " + rendezvous.getString());

							synchronized( PairingManagerTunnelHandler.this ){

								if ( update_event == null ){

									update_event =
										SimpleTimer.addEvent(
											"PMT:defer",
											SystemTime.getOffsetTime( 15*1000 ),
											new TimerEventPerformer()
											{
												@Override
												public void
												perform(
													TimerEvent event)
												{
													synchronized( PairingManagerTunnelHandler.this ){

														update_event = null;
													}

													System.out.println( "    updating" );

													manager.updateNeeded();
												}
											});
								}
							}
						}
					});
			}

			core.getNATTraverser().registerHandler(
				new NATTraversalHandler()
				{
					private final Map<Long,Object[]> server_map =
						new LinkedHashMap<Long,Object[]>( 10, 0.75f, true )
						{
							@Override
							protected boolean
							removeEldestEntry(
						   		Map.Entry<Long,Object[]> eldest)
							{
								return size() > 10;
							}
						};

					@Override
					public int
					getType()
					{
						return( NATTraverser.TRAVERSE_REASON_PAIR_TUNNEL );
					}

					@Override
					public String
					getName()
					{
						return( "Pairing Tunnel" );
					}

					@Override
					public Map
					process(
						InetSocketAddress	originator,
						Map					data )
					{
						if ( SRP_VERIFIER == null || !active ){

							return( null );
						}

						boolean	good_request = false;

						try{

							Map result = new HashMap();

							Long session = (Long)data.get( "sid" );

							if ( session == null ){

								return( null );
							}

							InetAddress	tunnel_originator;

							try{
								tunnel_originator = InetAddress.getByAddress( (byte[])data.get( "origin"));

							}catch( Throwable e ){

								Debug.out( "originator decode failed: " + data );

								return( null );
							}

							System.out.println( "PairManagerTunnelHander: incoming message - session=" + session + ", payload=" + data + " from " + tunnel_originator + " via " + originator);

							SRP6Server	server;
							BigInteger	B;

							synchronized( server_map ){

								Object[] entry = server_map.get( session );

								if ( entry == null ){

									long diff = SystemTime.getMonotonousTime() - last_server_create_time;

									if ( diff < 5000 ){

										try{
											long	sleep = 5000 - diff;

											System.out.println( "Sleeping for " + sleep + " before starting srp" );

											Thread.sleep( sleep );

										}catch( Throwable e ){
										}
									}

									server = new SRP6Server();

							        server.init( N_3072, G_3072, SRP_VERIFIER, new SHA256Digest(), RandomUtils.SECURE_RANDOM );

							        B = server.generateServerCredentials();

									server_map.put( session, new Object[]{ server, B });

									last_server_create_time = SystemTime.getMonotonousTime();

									total_servers++;

								}else{

									server 	= (SRP6Server)entry[0];
									B		= (BigInteger)entry[1];
								}
							}

							Long	op = (Long)data.get( "op" );

							if ( op == 1 ){

								result.put( "op", 2 );

								result.put( "s", SRP_SALT );

						        result.put( "b", B.toByteArray());

						        good_request = true;

						        if ( data.containsKey( "test" )){

									manager.recordRequest( "SRP Test", originator.getAddress().getHostAddress(), true );

						        }
							}else if ( op == 3 ){

								boolean log_error = true;

								try{
									long diff = SystemTime.getMonotonousTime() - last_server_agree_time;

									if ( diff < 5000 ){

										try{
											long	sleep = 5000 - diff;

											System.out.println( "Sleeping for " + sleep + " before completing srp" );

											Thread.sleep( sleep );

										}catch( Throwable e ){
										}
									}

									BigInteger A = new BigInteger((byte[])data.get( "a" ));

							        BigInteger serverS = server.calculateSecret( A );

							        byte[] shared_secret = serverS.toByteArray();

									Cipher decipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

									byte[] key = new byte[16];

									System.arraycopy( shared_secret, 0, key, 0, 16 );

									SecretKeySpec secret = new SecretKeySpec( key, "AES");

									decipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec((byte[])data.get( "enc_iv" )));

									byte[] dec = decipher.doFinal( (byte[])data.get( "enc_data" ));

									String json_str = new String( dec, "UTF-8" );

									if ( !json_str.startsWith( "{" )){

										log_error = false;

										throw( new Exception( "decode failed" ));
									}

									JSONObject dec_json = (JSONObject)JSONUtils.decodeJSON( json_str );

									String tunnel_url = (String)dec_json.get( "url" );

									String service_id = new String((byte[])data.get( "service"), "UTF-8" );

									String endpoint_url = (String)dec_json.get( "endpoint");

									boolean	ok = createTunnel( tunnel_originator, session, service_id, secret, tunnel_url, endpoint_url );

									result.put( "op", 4 );
									result.put( "status", ok?"ok":"failed" );

									good_request = true;

								}catch( Throwable e ){

									result.put( "op", 4 );
									result.put( "status", "failed" );

										// filter usual errors on bad agreement

									if ( 	e instanceof BadPaddingException ||
											e instanceof IllegalBlockSizeException ){

										log_error = false;
									}

									if ( log_error ){

										e.printStackTrace();
									}
								}finally{

									last_server_agree_time = SystemTime.getMonotonousTime();
								}
							}

							return( result );

						}finally{

							if ( !good_request ){

								manager.recordRequest( "SRP", originator.getAddress().getHostAddress(), false );
							}
						}
					}
				});

			SimpleTimer.addPeriodicEvent(
				"pm:tunnel:stats",
				30*1000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event)
					{
						synchronized( tunnels ){

							if ( tunnels.size() > 0 ){

								System.out.println( "PairTunnels: " + tunnels.size());

								for ( PairManagerTunnel t: tunnels.values()){

									System.out.println( "\t" + t.getString());
								}
							}
						}
					}
				});

		}catch( Throwable e ){

			Debug.out( e );

			init_fail = Debug.getNestedExceptionMessage( e );

			manager.updateSRPState();
		}
	}

	protected String
	getStatus()
	{
		if ( init_fail != null ){

			return( MessageText.getString("label.disabled") + ": " + init_fail );

		}else if ( !active ){

			return( MessageText.getString( "pairing.status.initialising" ) + "..." );

		}else if ( SRP_SALT == null ){

			return( MessageText.getString( "pairing.srp.pw.req" ));

		}else if ( last_punchers_registered == 0 ){

			return( MessageText.getString( "pairing.srp.registering" ));

		}else{

			return( MessageText.getString( "tps.status.available" ));
		}
	}

	protected void
	setActive(
		boolean	a )
	{
		synchronized( this ){

			if ( active == a ){

				return;
			}

			active	= a;
		}

		updateActive();
	}

	private void
	updateActive()
	{
		manager.updateSRPState();

		if ( active && SRP_VERIFIER != null ){

			start();

		}else{

			synchronized( tunnels ){

				for ( PairManagerTunnel t: new ArrayList<>(tunnels.values())){

					t.destroy();
				}
			}


			synchronized( local_server_map ){

				local_server_map.clear();
			}
		}

		List<DHTNATPuncher> punchers = new ArrayList<>();

		punchers.addAll( nat_punchers_ipv4 );
		punchers.addAll( nat_punchers_ipv6 );

		for ( DHTNATPuncher p: punchers ){

			p.forceActive( active );
		}
	}

	protected void
	updateRegistrationData(
		Map<String,Object>		payload )
	{
		int	puncher_num = 0;

		int	num_registered = 0;

		for ( DHTNATPuncher nat_ipv4: nat_punchers_ipv4 ){

			DHTTransportContact rend 	= nat_ipv4.getRendezvous();
			DHTTransportContact lc 		= nat_ipv4.getLocalContact();

			if ( rend != null && lc != null ){

				puncher_num++;

				InetSocketAddress rend_address = rend.getTransportAddress();

				num_registered++;

				payload.put( "rc_v4-" + puncher_num, rend_address.getAddress().getHostAddress() + ":" + rend_address.getPort());

				if ( puncher_num == 1 ){

					payload.put( "rl_v4", lc.getExternalAddress().getAddress().getHostAddress() + ":" + lc.getAddress().getPort());
				}
			}
		}

		puncher_num = 0;

		for ( DHTNATPuncher nat_ipv6: nat_punchers_ipv6 ){

			DHTTransportContact rend 	= nat_ipv6.getRendezvous();
			DHTTransportContact lc 		= nat_ipv6.getLocalContact();

			if ( rend != null && lc != null ){

				puncher_num++;

				InetSocketAddress rend_address = rend.getTransportAddress();

				num_registered++;

				payload.put( "rc_v6-" + puncher_num, rend_address.getAddress().getHostAddress() + ":" + rend_address.getPort());

				if ( puncher_num == 1 ){

					payload.put( "rl_v6", lc.getExternalAddress().getAddress().getHostAddress() + ":" + lc.getAddress().getPort());
				}
			}
		}

		if ( num_registered != last_punchers_registered ){

			last_punchers_registered = num_registered;

			manager.updateSRPState();
		}
	}

	private BigInteger
	fromHex(
		String hex )
	{
		return new BigInteger(1, ByteFormatter.decodeString( hex.replaceAll( " ", "" )));
	}

	protected boolean
	handleLocalTunnel(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		start();

		if ( SRP_VERIFIER == null || !active ){

			throw( new IOException( "Secure pairing is not enabled" ));
		}

		boolean	good_request = false;

		try{
				// remove /pairing/tunnel/

			String url = request.getURL().substring( 16 );

			int	q_pos = url.indexOf( '?' );

			Map<String,String> args = new HashMap<>();

			if ( q_pos != -1 ){

				String	args_str = url.substring( q_pos+1 );

				String[]	bits = args_str.split( "&" );

				for ( String arg: bits ){

					String[] x = arg.split( "=" );

					if ( x.length == 2 ){

						args.put( x[0].toLowerCase(), x[1] );
					}
				}

				url = url.substring( 0, q_pos );
			}

			if ( url.startsWith( "create" )){

				String	ac 	= args.get( "ac" );
				String	sid	= args.get( "sid" );

				if ( ac == null || sid == null ){

					throw( new IOException( "Access code or service id missing" ));
				}

				if ( !ac.equals( manager.peekAccessCode())){

					throw( new IOException( "Invalid access code" ));
				}

				PairedServiceImpl ps = manager.getService( sid );

				if ( ps == null ){

					good_request = true;

					throw( new IOException( "Service '" + sid + "' not registered" ));
				}

				PairedServiceRequestHandler handler = ps.getHandler();

				if ( handler == null ){

					good_request = true;

					throw( new IOException( "Service '" + sid + "' has no handler registered" ));
				}

				JSONObject json = new JSONObject();

				JSONObject result = new JSONObject();

				json.put( "result", result );

				byte[]	ss = new byte[]{ SRP_SALT[0], SRP_SALT[1], SRP_SALT[2], SRP_SALT[3] };

				long	tunnel_id = RandomUtils.nextSecureAbsoluteLong();

				String	tunnel_name = Base32.encode( ss ) + "_" + tunnel_id;

				synchronized( local_server_map ){

					long diff = SystemTime.getMonotonousTime() - last_local_server_create_time;

					if ( diff < 5000 ){

						try{
							long	sleep = 5000 - diff;

							System.out.println( "Sleeping for " + sleep + " before starting srp" );

							Thread.sleep( sleep );

						}catch( Throwable e ){
						}
					}

					SRP6Server server = new SRP6Server();

			        server.init( N_3072, G_3072, SRP_VERIFIER, new SHA256Digest(), RandomUtils.SECURE_RANDOM );

			        BigInteger B = server.generateServerCredentials();

			        local_server_map.put( tunnel_name, new Object[]{ server, handler, null, null });

					last_local_server_create_time = SystemTime.getMonotonousTime();

					total_local_servers++;

					result.put( "srp_salt", Base32.encode( SRP_SALT ));

			        result.put( "srp_b", Base32.encode( B.toByteArray()));

			        Map<String,String> headers = request.getHeaders();

			        String	host = headers.get( "host" );

			        	// remove port number

			        int pos = host.lastIndexOf( "]" );

			        if ( pos != -1 ){

			        		// ipv6 literal

			        	host = host.substring( 0, pos+1 );

			        }else{

			        	pos = host.indexOf( ':' );

			        	if ( pos != -1 ){

			        		host = host.substring( 0, pos );
			        	}
			        }

			        String abs_url = request.getAbsoluteURL().toString();

			        	// unfortunately there is some nasty code that uses a configured tracker
			        	// address as the default host

			        abs_url = UrlUtils.setHost( new URL( abs_url ), host).toExternalForm();

			        pos = abs_url.indexOf( "/create" );

			        String tunnel_url = abs_url.substring(0,pos) + "/id/" + tunnel_name;

			        result.put( "url", tunnel_url );
				}

				response.getOutputStream().write( JSONUtils.encodeToJSON( json ).getBytes( "UTF-8" ));

				response.setContentType( "application/json; charset=UTF-8" );

				response.setGZIP( true );

				good_request = true;

				return( true );

			}else if ( url.startsWith( "id/" )){

				String	tunnel_name = url.substring( 3 );

				Object[]	entry;

				synchronized( local_server_map ){

					entry = local_server_map.get( tunnel_name );

					if ( entry == null ){

						good_request = true;

						throw( new IOException( "Unknown tunnel id" ));
					}
				}

				String	srp_a 		= args.get( "srp_a" );
				String	enc_data 	= args.get( "enc_data" );
				String	enc_iv	 	= args.get( "enc_iv" );

				if ( srp_a != null && enc_data != null && enc_iv != null ){

					try{
						synchronized( local_server_map ){

							long diff = SystemTime.getMonotonousTime() - last_local_server_agree_time;

							if ( diff < 5000 ){

								try{
									long	sleep = 5000 - diff;

									System.out.println( "Sleeping for " + sleep + " before completing srp" );

									Thread.sleep( sleep );

								}catch( Throwable e ){
								}
							}
						}

						JSONObject json = new JSONObject();

						JSONObject result = new JSONObject();

						json.put( "result", result );

						SRP6Server server = (SRP6Server)entry[0];

						BigInteger A = new BigInteger( Base32.decode( srp_a ));

				        BigInteger serverS = server.calculateSecret( A );

				        byte[] shared_secret = serverS.toByteArray();

						Cipher decipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

						byte[] key = new byte[16];

						System.arraycopy( shared_secret, 0, key, 0, 16 );

						SecretKeySpec secret = new SecretKeySpec( key, "AES");

						decipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec( Base32.decode( enc_iv )));

						byte[] dec = decipher.doFinal( Base32.decode( enc_data ));

						JSONObject dec_json = (JSONObject)JSONUtils.decodeJSON( new String( dec, "UTF-8" ));

						String tunnel_url = (String)dec_json.get( "url" );

						if ( !tunnel_url.contains( tunnel_name )){

							throw( new IOException( "Invalid tunnel url" ));
						}

						String endpoint_url = (String)dec_json.get( "endpoint");

						entry[2] = secret;
						entry[3] = endpoint_url;

						result.put( "state", "activated" );

						response.getOutputStream().write( JSONUtils.encodeToJSON( json ).getBytes( "UTF-8" ));

						response.setContentType( "application/json; charset=UTF-8" );

						response.setGZIP( true );

						good_request = true;

						return( true );

					}catch( Throwable e ){

						throw( new IOException( Debug.getNestedExceptionMessage( e )));

					}finally{

						last_local_server_agree_time = SystemTime.getMonotonousTime();
					}
				}else if ( args.containsKey( "close" )){

					synchronized( local_server_map ){

						local_server_map.remove( tunnel_name );
					}

					good_request = true;

					return( true );

				}else{

					PairedServiceRequestHandler	request_handler = (PairedServiceRequestHandler)entry[1];

					SecretKeySpec	secret = (SecretKeySpec)entry[2];

					String	endpoint_url = (String)entry[3];

					if ( secret == null ){

						throw( new IOException( "auth not completed" ));
					}

					byte[] request_data = FileUtil.readInputStreamAsByteArray( request.getInputStream());

	         		try{
	         			byte[] decrypted;

	         			{
	         				byte[]	IV = new byte[16];

	         				System.arraycopy( request_data, 0, IV, 0, IV.length );

	         				Cipher decipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

	         				decipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec( IV ));

	         				decrypted = decipher.doFinal( request_data, 16, request_data.length-16 );
	         			}

	         			byte[] reply_bytes = request_handler.handleRequest( request.getClientAddress2().getAddress(), endpoint_url, decrypted );

	         			{
	         				Cipher encipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");

	         				encipher.init( Cipher.ENCRYPT_MODE, secret );

	         				AlgorithmParameters params = encipher.getParameters ();

	         				byte[] IV = params.getParameterSpec(IvParameterSpec.class).getIV();

	         				byte[] enc = encipher.doFinal( reply_bytes );

	         				byte[] rep_bytes = new byte[ IV.length + enc.length ];

	         				System.arraycopy( IV, 0, rep_bytes, 0, IV.length );
	         				System.arraycopy( enc, 0, rep_bytes, IV.length, enc.length );

	         				response.getOutputStream().write( rep_bytes );

	         				response.setContentType( "application/octet-stream" );

	         				good_request = true;

	         				return( true );
	         			}
	         		}catch( Throwable e ){

	         			throw( new IOException( Debug.getNestedExceptionMessage( e )));
	         		}
				}
			}

			throw( new IOException( "Unknown tunnel operation" ));

		}finally{

			if ( !good_request ){

				manager.recordRequest( "SRP", request.getClientAddress2().getAddress().getHostAddress(), false );
			}
		}
	}

	private boolean
	createTunnel(
		InetAddress		originator,
		long			session,
		String			sid,
		SecretKeySpec	secret,
		String			tunnel_url,
		String			endpoint_url )
	{
		PairedServiceImpl ps = manager.getService( sid );

		if ( ps == null ){

			Debug.out( "Service '" + sid + "' not registered" );

			return( false );
		}

		PairedServiceRequestHandler handler = ps.getHandler();

		if ( handler == null ){

			Debug.out( "Service '" + sid + "' has no handler registered" );

			return( false );
		}

		String 	key = originator.getHostAddress() + ":" + session + ":" + sid;

		synchronized( tunnels ){

			PairManagerTunnel existing = tunnels.get( key );

			if ( existing != null ){

				return( true );
			}

			if ( tunnels.size() > MAX_TUNNELS ){

				long				oldest_active = Long.MAX_VALUE;
				PairManagerTunnel	oldest_tunnel = null;

				for ( PairManagerTunnel t: tunnels.values()){

					long	at = t.getLastActive();

					if ( at < oldest_active ){

						oldest_active 	= at;
						oldest_tunnel	= t;
					}
				}

				oldest_tunnel.destroy();

				tunnels.remove( oldest_tunnel.getKey());
			}

			PairManagerTunnel tunnel = new PairManagerTunnel( this, key, originator, sid, handler, secret, tunnel_url, endpoint_url );

			tunnels.put( key, tunnel );

			System.out.println( "Created pair manager tunnel: " + tunnel.getString());

		}

		return( true );
	}

	protected void
	closeTunnel(
		PairManagerTunnel		tunnel )
	{

		System.out.println( "Destroyed pair manager tunnel: " + tunnel.getString());

		synchronized( tunnels ){

			tunnels.remove( tunnel.getKey());
		}
	}

	protected void
	generateEvidence(
		IndentWriter writer )
	{
		writer.println( "Tunnel Handler" );

		writer.indent();

		writer.println( "started=" + started + ", active=" + active );

		if ( init_fail != null ){

			writer.println( "Init fail: " + init_fail );
		}

		long	now = SystemTime.getMonotonousTime();

		writer.println( "total local=" + total_local_servers );
		writer.println( "last local create=" + (last_local_server_create_time==0?"<never>":String.valueOf(now-last_local_server_create_time)));
		writer.println( "last local agree=" + (last_local_server_agree_time==0?"<never>":String.valueOf(now-last_local_server_agree_time)));

		writer.println( "total remote=" + total_servers );
		writer.println( "last remote create=" + (last_server_create_time==0?"<never>":String.valueOf(now-last_server_create_time)));
		writer.println( "last remote agree=" + (last_server_agree_time==0?"<never>":String.valueOf(now-last_server_agree_time)));

		synchronized( tunnels ){

			writer.println( "tunnels=" + tunnels.size());

			for ( PairManagerTunnel tunnel: tunnels.values()){

				writer.println( "    " + tunnel.getString());
			}
		}

		try{
			writer.println( "IPv4 punchers: " + nat_punchers_ipv4.size());

			for ( DHTNATPuncher p: nat_punchers_ipv4 ){

				writer.println( "    " + p.getStats());
			}

			writer.println( "IPv6 punchers: " + nat_punchers_ipv6.size());

			for ( DHTNATPuncher p: nat_punchers_ipv6 ){

				writer.println( "    " + p.getStats());
			}
		}finally{

			writer.exdent();
		}
	}
}
