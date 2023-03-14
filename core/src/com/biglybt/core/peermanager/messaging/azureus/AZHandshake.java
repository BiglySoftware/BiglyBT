/*
 * Created on Apr 30, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.peermanager.messaging.azureus;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.util.*;




/**
 * AZ handshake message.
 */
public class AZHandshake implements AZMessage {

  public static final int HANDSHAKE_TYPE_PLAIN  = 0;
  public static final int HANDSHAKE_TYPE_CRYPTO = 1;

  private static final byte bss = DirectByteBuffer.SS_MSG;

  private final byte version;
  private DirectByteBuffer buffer = null;
  private String description = null;

  private final byte[] identity;
  private final HashWrapper sessionID;
  private final HashWrapper reconnectID;
  private final String client;
  private final String client_version;
  private final String[] avail_ids;
  private final byte[] avail_versions;
  private int tcp_port;
  private int udp_port;
  private int udp_non_data_port;
  private final int handshake_type;
  private final boolean uploadOnly;
  private final InetAddress ipv6;
  private final String localHost;
  private final int	md_size;


  public AZHandshake( byte[] peer_identity,
	  				  HashWrapper sessionID,
	  				  HashWrapper reconnectID,
                      String _client,
                      String version,
                      int tcp_listen_port,
                      int udp_listen_port,
                      int udp_non_data_listen_port,
                      InetAddress ipv6addr,
                      String localHost,
                      int md_size,
                      String[] avail_msg_ids,
                      byte[] avail_msg_versions,
                      int _handshake_type,
                      byte _version,
                      boolean uploadOnly) {

    this.identity = peer_identity;
    this.sessionID = sessionID;
    this.reconnectID = reconnectID;
    this.client = _client;
    this.client_version = version;
    this.avail_ids = avail_msg_ids;
    this.avail_versions = avail_msg_versions;
    this.tcp_port = tcp_listen_port;
    this.udp_port = udp_listen_port;
    this.udp_non_data_port = udp_non_data_listen_port;
    this.handshake_type = _handshake_type;
    this.version = _version;
    this.uploadOnly = uploadOnly;
    this.ipv6 = ipv6addr;
    this.localHost = localHost;
    this.md_size = md_size;

    //verify given port info is ok
    if( tcp_port < 0 || tcp_port > 65535 ) {
      Debug.out( "given TCP listen port is invalid: " +tcp_port );
      tcp_port = 0;
    }

    if( udp_port < 0 || udp_port > 65535 ) {
      Debug.out( "given UDP listen port is invalid: " +udp_port );
      udp_port = 0;
    }

    if( udp_non_data_port < 0 || udp_non_data_port > 65535 ) {
        Debug.out( "given UDP non-data listen port is invalid: " +udp_non_data_port );
        udp_non_data_port = 0;
      }
  }



  public byte[] getIdentity() {  return identity;  }
  public HashWrapper getRemoteSessionID() { return sessionID; }
  public HashWrapper getReconnectSessionID() { return reconnectID; }
  public boolean isUploadOnly() {return uploadOnly;}


  public String getClient() {  return client;  }

  public String getClientVersion() {  return client_version;  }

  public String[] getMessageIDs() {  return avail_ids;  }

  public byte[] getMessageVersions() {  return avail_versions;  }

  public int getTCPListenPort() {  return tcp_port;  }
  public int getUDPListenPort() {  return udp_port;  }
  public int getUDPNonDataListenPort() {  return udp_non_data_port;  }
  public InetAddress getIPv6() { return ipv6; }
  public String getLocalHost() { return localHost; }
  public int getMetadataSize(){ return md_size; }
  public int getHandshakeType() {  return handshake_type;  }


  @Override
  public String getID() {  return AZMessage.ID_AZ_HANDSHAKE;  }
  @Override
  public byte[] getIDBytes() {  return AZMessage.ID_AZ_HANDSHAKE_BYTES;  }

  @Override
  public String getFeatureID() {  return AZMessage.AZ_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() { return AZMessage.SUBID_AZ_HANDSHAKE;  }


  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {
    if( description == null ) {
      String msgs_desc = "";
      for( int i=0; i < avail_ids.length; i++ ) {
        String id = avail_ids[ i ];
        byte ver = avail_versions[ i ];
        if( id.equals( getID() ) )  continue;  //skip ourself
        msgs_desc += "[" +id+ ":" +ver+ "]";
      }
      description = getID()+ " from [" +ByteFormatter.nicePrint( identity, true )+ ", " +
      							client+ " " +client_version+ ", TCP/UDP ports " +tcp_port+ "/" +udp_port+ "/" + udp_non_data_port +
      							", handshake " + (getHandshakeType() == HANDSHAKE_TYPE_PLAIN ? "plain" : "crypto") +
      							", upload_only = " + (isUploadOnly() ? "1" : "0") +
      							(ipv6 != null ? ", ipv6 = "+ipv6.getHostAddress() : "") +
      							", md_size=" + md_size +
      							(sessionID != null ? ", sessionID: "+sessionID.toBase32String() : "") +
      							(reconnectID != null ? ", reconnect request: "+reconnectID.toBase32String() : "") +
      							"] supports " +msgs_desc;
    }

    return description;
  }

	@Override
	public DirectByteBuffer[] getData() {
		if (buffer == null)
		{
			Map payload_map = new HashMap();
			//client info
			payload_map.put("identity", identity);
			if (sessionID != null)
				payload_map.put("session", sessionID.getBytes());
			if (reconnectID != null)
				payload_map.put("reconn", reconnectID.getBytes());
			payload_map.put("client", client);
			payload_map.put("version", client_version);
			payload_map.put("tcp_port", new Long(tcp_port));
			payload_map.put("udp_port", new Long(udp_port));
			payload_map.put("udp2_port", new Long(udp_non_data_port));
			payload_map.put("handshake_type", new Long(handshake_type));
			payload_map.put("upload_only", new Long(uploadOnly ? 1L : 0L));
			if(ipv6 != null){
				payload_map.put("ipv6", ipv6.getAddress());
			}
			if (localHost != null ){
				payload_map.put( "lh", localHost.getBytes( Constants.UTF_8 ));
			}
			if ( md_size > 0 ){
				payload_map.put("mds", new Long(md_size));
			}
			//available message list
			List message_list = new ArrayList();
			for (int i = 0; i < avail_ids.length; i++)
			{
				String id = avail_ids[i];
				byte ver = avail_versions[i];
				if (id.equals(getID()))
					continue; //skip ourself
				Map msg = new HashMap();
				msg.put("id", id);
				msg.put("ver", new byte[] { ver });
				message_list.add(msg);
			}

			payload_map.put("messages", message_list);

			// random padding if crypto
			if (handshake_type == AZHandshake.HANDSHAKE_TYPE_CRYPTO)
				payload_map.put("pad", new byte[RandomUtils.nextInt( AZMessageFactory.AZ_HANDSHAKE_PAD_MAX)]);

			buffer = MessagingUtil.convertPayloadToBencodedByteStream(payload_map, DirectByteBuffer.AL_MSG_AZ_HAND);
			if (buffer.remaining(bss) > 1350 && Constants.IS_CVS_VERSION ){
				System.out.println("Generated AZHandshake size = " + buffer.remaining(bss) + " bytes");
			}
		}

		return new DirectByteBuffer[] { buffer };
	}


  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    Map root = MessagingUtil.convertBencodedByteStreamToPayload( data, 100, getID() );

    byte[] id = (byte[])root.get( "identity" );
    if( id == null )  throw new MessageException( "id == null" );
    if( id.length != 20 )  throw new MessageException( "id.length != 20: " +id.length );

    byte[] session = (byte[])root.get("session");
    byte[] reconnect = (byte[])root.get("reconn");

    byte[] raw_name = (byte[])root.get( "client" );
    if( raw_name == null )  throw new MessageException( "raw_name == null" );
    String name = new String( raw_name );

    byte[] raw_ver = (byte[])root.get( "version" );
    if( raw_ver == null )  throw new MessageException( "raw_ver == null" );
    String client_version = new String( raw_ver );

    Long tcp_lport = (Long)root.get( "tcp_port" );
    if( tcp_lport == null ) {  //old handshake
      tcp_lport = new Long( 0 );
    }

    Long udp_lport = (Long)root.get( "udp_port" );
    if( udp_lport == null ) {  //old handshake
      udp_lport = new Long( 0 );
    }

    Long udp2_lport = (Long)root.get( "udp2_port" );
    if( udp2_lport == null ) {  //old handshake
      udp2_lport = udp_lport;
    }

    Long h_type = (Long)root.get( "handshake_type" );
    if( h_type == null ) {  //only 2307+ send type
    	h_type = new Long( HANDSHAKE_TYPE_PLAIN );
    }

    InetAddress ipv6 = null;
    if(root.get("ipv6") instanceof byte[])
	{
		try
		{
			InetAddress.getByAddress((byte[]) root.get("ipv6"));
		} catch (Exception e)
		{

		}
	}
    int md_size = 0;
    Long mds = (Long)root.get( "mds" );
    if ( mds != null ){
    	md_size = mds.intValue();
    }
    List raw_msgs = (List) root.get("messages");
    if (raw_msgs == null)  throw new MessageException("raw_msgs == null");

    String[] ids = new String[raw_msgs.size()];
    byte[] vers = new byte[raw_msgs.size()];

    int pos = 0;

    for (Iterator i = raw_msgs.iterator(); i.hasNext();) {
      Map msg = (Map) i.next();

      byte[] mid = (byte[]) msg.get("id");
      if (mid == null)  throw new MessageException("mid == null");
      ids[pos] = new String(mid);

      byte[] ver = (byte[]) msg.get("ver");
      if (ver == null)  throw new MessageException("ver == null");

      if (ver.length != 1)  throw new MessageException("ver.length != 1");
      vers[pos] = ver[0];

      pos++;
    }

    Long ulOnly = (Long)root.get("upload_only");
    boolean uploadOnly = ulOnly != null && ulOnly.longValue() > 0L ? true : false;

    if ( name.equals( Constants.AZUREUS_PROTOCOL_NAME_PRE_4813 )){
    	name = Constants.AZUREUS_PROTOCOL_NAME;
    }
    
    byte[] b_lh = (byte[])root.get( "lh" );
    
    String localHost;
    
    if ( b_lh == null ){
    	localHost = null;
    }else{
    	localHost = new String( b_lh, Constants.UTF_8 );
    }
    return new AZHandshake( id, session == null ? null : new HashWrapper(session),reconnect == null ? null : new HashWrapper(reconnect), name, client_version, tcp_lport.intValue(), udp_lport.intValue(), udp2_lport.intValue(), ipv6, localHost, md_size, ids, vers, h_type.intValue(), version , uploadOnly);
  }


  @Override
  public void destroy() {
    if( buffer != null )  buffer.returnToPool();
  }
}
