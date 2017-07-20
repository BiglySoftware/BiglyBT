/*
 * Created on 12-Jun-2006
 * Created by Marc Colosimo
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
 * Connection class for NAT-PMP (Port Mapping Protocol) Devices
 *
 * @see <http://files.dns-sd.org/draft-cheshire-nat-pmp.txt>
 * Tested with <https://www.grc.com/x/portprobe=6881>
 *
 * This code is ugly, but it works.
 *
 * Some assumptions:
 *  - The NAT device will be at xxx.xxx.xxx.1
 *
 * This needs to be threaded.
 *  - It could take upto 2 minutes to timeout during any request
 *  - We need to listen for address changes (using link-local multicast?)!
 *  - We need to request the mapping again before it expires
 *
 * Some hints and to dos:
 *  - The draft spec says that the device could set max lease life time
 *    to be less than requested. this should be checked.
 *  - Need to make something to renew port mappings - recommend that
 *    the client SHOULD begin trying to renew the mapping halfway to *
 *    expiry time, like DHCP
 *  - Need to listen for public address changes
 *
 * Version 0.1b
 */

package com.biglybt.net.natpmp.impl;

import java.net.*;

import com.biglybt.core.util.NetUtils;
import com.biglybt.net.natpmp.NATPMPDeviceAdapter;
import com.biglybt.net.natpmp.NatPMPDevice;

/**
 *
 * Main class
 *
 * */
public class NatPMPDeviceImpl implements NatPMPDevice
{

    static final int NATMAP_VER = 0;
    static final int NATMAP_PORT = 5351;
    static final int NATMAP_RESPONSE_MASK = 128;
    static final int NATMAP_INIT_RETRY = 250;     // ms
    static final int NATMAP_MAX_RETRY = 2250;     // gives us three tries

    // lease life in seconds
    // 24 hours
    static final int NATMAP_DEFAULT_LEASE = 60*60*24;

    // link-local multicast address - for address changes
    // not implemented
    static final String NATMAP_LLM = "224.0.0.1";

    // Opcodes used for ..
    static final byte NATOp_AddrRequest = 0; // Ask for a NAT-PMP device
    static final byte NATOp_MapUDP = 1;      // Map a UDP Port
    static final byte NATOp_MapTCP = 2;      // Map a TCP Port

    /* Length of Requests in bytes */
    static final int NATAddrRequest = 2;
    static final int NATPortMapRequestLen  = 4 * 3;  // 4 bytes by 3

    /* Length of Replies in Bytes */
    static final int NATAddrReplyLen       = 4 * 3;
    static final int NATPortMapReplyLen    = 4 * 4;

    /* Current Result Codes */
    static final int NATResultSuccess = 0;
    static final int NATResultUnsupportedVer = 1;
    /**
     * Not Authorized/Refused
     * (e.g. box supports mapping, but user has turned feature off)
     **/
    static final int NATResultNotAuth = 2;
    /**
     * Network Failure
     * (e.g. NAT box itself has not obtained a DHCP lease)
     **/
    static final int NATResultNetFailure = 3;
    static final int NATResultNoResc = 4; // Out of resources
    static final int NATResultUnsupportedOp = 5;  // Unsupported opcode

    /* Instance specific globals */
    private String		current_router_address	= "?";
    private InetAddress hostInet;        // Our address
    private InetAddress natPriInet;      // NAT's private (interal) address
    private InetAddress natPubInet;      // NAT's public address
    private NetworkInterface networkInterface;	// natPriInet network interface

    private int nat_epoch = 0;           // This gets updated each request

    private NATPMPDeviceAdapter	adapter;

    /**
     * Singleton creation
     **/
    private static NatPMPDeviceImpl NatPMPDeviceSingletonRef;

    public static synchronized NatPMPDeviceImpl
                    getSingletonObject(NATPMPDeviceAdapter adapter) throws Exception {
        if (NatPMPDeviceSingletonRef == null)
            NatPMPDeviceSingletonRef = new NatPMPDeviceImpl(adapter);
        return NatPMPDeviceSingletonRef;
    }

    private
    NatPMPDeviceImpl(
    	NATPMPDeviceAdapter _adapter)

    	throws Exception
    {
    	adapter		= _adapter;
        hostInet 	= NetUtils.getLocalHost();

        checkRouterAddress();
    }

    protected void
    checkRouterAddress()

    	throws Exception
    {
    	String	natAddr = adapter.getRouterAddress().trim();

        if ( natAddr.length() == 0 ){

        	natAddr = convertHost2RouterAddress(hostInet);
        }

        if ( natAddr.equals( current_router_address )){

        	return;
        }

        current_router_address = natAddr;

        log("Using Router IP: " + natAddr);

        natPriInet = InetAddress.getByName(natAddr);

        networkInterface = NetUtils.getByInetAddress( natPriInet );
    }


    /**
     * Send a request and wait for reply
     * This class should be threaded!!!
     *
     * This sends to the default NATPMP_PORT.
     *
     * @param dstInet destination address (should be the private NAT address)
     * @param dstPkt packet to send
     * @param recBuf byte buffer big enough to hold received
     **/
    public DatagramPacket sendNATMsg(InetAddress dstInet, DatagramPacket dstPkt, byte[] recBuf) throws Exception {
        int retryInterval = NATMAP_INIT_RETRY;
        boolean recRep = false;

        DatagramSocket skt = new DatagramSocket();
        skt.connect( dstInet, NATMAP_PORT );
        skt.setSoTimeout( NATMAP_INIT_RETRY );
        skt.send(dstPkt);       // how do we know we hit something?

        DatagramPacket recPkt = new DatagramPacket(recBuf, recBuf.length);

        // We have several tries at this (like 3)
        while ( !recRep && (retryInterval < NATMAP_MAX_RETRY) ) {
            try {
                skt.receive(recPkt);
                recRep = true;
            } catch (SocketTimeoutException ste) {
                //log("Timed Out!");
                //log( ste.getMessage() );
                // sleep before trying again
                // this.sleep(retryInterval);
                Thread.sleep(retryInterval);        // not sleeping?!?
                // increase retry interval
                retryInterval += (retryInterval * 2);
            }
        }

        if ( !recRep ){

        	throw( new PortUnreachableException());
        }

        // check recRep for true!!!
        return recPkt;
    }

    /**
     * Try to connect with a NAT-PMP device.
     * This could take sometime.
     *
     * @return true if it found one
     **/
    @Override
    public boolean connect() throws Exception {

   		checkRouterAddress();

    	try{
	        // Send NAT request to find out if it is PMP happy
	        byte reqBuf[] = {NATMAP_VER, NATOp_AddrRequest};
	        DatagramPacket dstPkt = new DatagramPacket(reqBuf, reqBuf.length);
	        byte recBuf[] = new byte[NATAddrReplyLen];
	        /* DatagramPacket recPkt = */ sendNATMsg(natPriInet, dstPkt, recBuf);

	        //int recVer = unsigned8ByteArrayToInt( recBuf, 0 );
	        //int recOp  = unsigned8ByteArrayToInt( recBuf, 1 );
	        int recErr = unsigned16ByteArrayToInt( recBuf, 2 );
	        int recEpoch  = unsigned32ByteArrayToInt( recBuf, 4 );
	        String recPubAddr = unsigned8ByteArrayToInt( recBuf, 8 ) + "." +
	                            unsigned8ByteArrayToInt( recBuf, 9 ) + "." +
	                            unsigned8ByteArrayToInt( recBuf, 10 ) + "." +
	                            unsigned8ByteArrayToInt( recBuf, 11 );

	        /* set the global NAT public address */
	        natPubInet = InetAddress.getByName(recPubAddr);

	        /* set the global NAT Epoch time (in seconds) */
	        nat_epoch = recEpoch;

	        if (recErr != 0)
	            throw( new Exception("NAT-PMP connection error: " + recErr) );

	        log("Err: " +recErr);
	        log("Uptime: " + recEpoch);
	        log("Public Address: " + recPubAddr);

	        /**
	         * TO DO:
	         *  Set up listner for announcements from the device for
	         *  address changes (public address changes)
	         **/

	        return true;

    	}catch( PortUnreachableException e ){

    		return( false );
    	}
    }

    /**
     * Asks for a public port to be mapped to a private port from this host.
     *
     * NAP-PMP allows the device to assign another public port if the
     * requested one is taken. So, you should check the returned port.
     *
     * @param tcp true TCP, false UDP
     * @return the returned publicPort. -1 if error occured
     * @todo either take a class (like UPnPMapping) or return a class
     **/
    @Override
    public int addPortMapping(boolean tcp, int publicPort,
                              int privatePort )  throws Exception {
        // check for actual connection!
        return portMappingProtocol( tcp, publicPort, privatePort,
                                    NATMAP_DEFAULT_LEASE );
    }

    /**
     * Delete a mapped public port
     *
     * @param tcp true TCP, false UDP port
     * @param publicPort the public port to close
     * @param privatePort the private port that it is mapped to
     * @warn untested
     */
    @Override
    public void deletePortMapping(boolean tcp, int publicPort,
                                  int privatePort )
                                    throws Exception {
        /**
         * if the request was successful, a zero lifetime will
         * delete the mapping and return a public port of 0
         **/
         // check for actual connection
        /*int result = */ portMappingProtocol(tcp, publicPort, privatePort, 0);

    }

    /**
     * General port mapping protocol
     *
     *
     *
     **/
    public int portMappingProtocol( boolean tcp, int publicPort,
                                    int privatePort, int lifetime )
                                    throws Exception {

        byte NATOp = (tcp?NATOp_MapTCP:NATOp_MapUDP);
        // Should check for errors - only using lower 2 bytes
        byte pubPort[] = intToByteArray(publicPort);
        byte priPort[] = intToByteArray(privatePort);
        byte portLifeTime[] = intToByteArray(lifetime);

        // Generate Port Map request packet
        byte dstBuf[] = new byte[NATPortMapRequestLen];
        dstBuf[0] = NATMAP_VER;  // Ver
        dstBuf[1] = NATOp;       // OP
        dstBuf[2] = 0;           // Reserved - 2 bytes
        dstBuf[3] = 0;
        dstBuf[4] = priPort[2];  // Private Port - 2 bytes
        dstBuf[5] = priPort[3];
        dstBuf[6] = pubPort[2];  // Requested Public Port - 2 bytes
        dstBuf[7] = pubPort[3];
	    System.arraycopy(portLifeTime, 0, dstBuf, 8, 4);

        DatagramPacket dstPkt = new DatagramPacket(dstBuf, dstBuf.length);
        byte recBuf[] = new byte[NATPortMapReplyLen];
        /* DatagramPacket recPkt = */ sendNATMsg(natPriInet, dstPkt, recBuf);

        // Unpack this and check codes
        //int recVers = unsigned8ByteArrayToInt( recBuf, 0 );
        int recOP =  unsigned8ByteArrayToInt( recBuf, 1 );
        int recCode = unsigned16ByteArrayToInt( recBuf, 2 );
        int recEpoch = unsigned32ByteArrayToInt( recBuf, 4);
        //int recPriPort = unsigned16ByteArrayToInt( recBuf, 8 );
        int recPubPort = unsigned16ByteArrayToInt( recBuf, 10 );
        int recLifetime = unsigned32ByteArrayToInt( recBuf, 12);

        /**
         * Should save the epoch. This can be used to determine the
         * time the mapping will be deleted.
         **/
        log("Seconds since Start of Epoch: " + recEpoch);
        log("Returned Mapped Port Lifetime: " + recLifetime);

        if ( recCode != 0 )
            throw( new Exception( "An error occured while getting a port mapping: " + recCode ) );
        if ( recOP != ( NATOp + 128) )
            throw( new Exception( "Received the incorrect port type: " + recOP) );
        if ( lifetime != recLifetime )
            log("Received different port life time!");

        return recPubPort;
    }

    @Override
    public InetAddress
    getLocalAddress()
    {
    	return( hostInet );
    }

	@Override
	public NetworkInterface
	getNetworkInterface()
	{
		return( networkInterface );
	}

	@Override
	public String
	getExternalIPAddress()
	{
		return( natPubInet.getHostAddress());
	}

	@Override
	public int
	getEpoch()
	{
		return( nat_epoch );
	}

	protected void
	log(
		String	str )
	{
		adapter.log( str );
	}
    /**
     *
     * Bunch of conversion functions
     *
     **/

    /**
     * Convert the byte array containing 32-bit to an int starting from
     * the given offset.
     *
     * @param b The byte array
     * @param offset The array offset
     * @return The integer
     */
    public static int unsigned32ByteArrayToInt(byte[] b, int offset) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += ( (int) b[i + offset] & 0xFF) << shift;
        }
        return value;
    }

    /**
     * Convert the byte array containing 16-bits to an int starting from
     * the given offset.
     *
     * @param b The byte array
     * @param offset The array offset
     * @return The integer
     */
    public static int unsigned16ByteArrayToInt(byte[] b, int offset) {
        int value = 0;
        for (int i = 0; i < 2; i++) {
            int shift = (2 - 1 - i) * 8;
            value += ( (int) b[i + offset] & 0xFF) << shift;
        }
        return value;
    }

    /**
     * Convert the byte array containing 8-bits to an int starting from
     * the given offset.
     *
     * @param b The byte array
     * @param offset The array offset
     * @return The integer
     */
    public static int unsigned8ByteArrayToInt(byte[] b, int offset) {
        return (int) b[offset] & 0xFF;
    }

    public short unsignedByteArrayToShort(byte[] buf) {
        if (buf.length == 2) {
            int i;
            i = ( ( ( (int) buf[0] & 0xFF) << 8) | ( (int) buf[1] & 0xFF) );
            return (short) i;
        }
        return -1;

    }

    /**
     * Convert a 16-bit short into a 2 byte array
     *
     * @return unsigned byte array
     **/
    public byte[] shortToByteArray(short v) {
        byte b[] = new byte[2];
        b[0] = (byte) ( 0xFF & (v >> 8) );
        b[1] = (byte) ( 0xFF & (v >> 0) );

        return b;
    }

    /**
     * Convert a 32-bit int into a 4 byte array
     *
     * @return unsigned byte array
     **/
    public byte[] intToByteArray(int v) {
        byte b[] = new byte[4];
        int i, shift;

        for(i = 0, shift = 24; i < 4; i++, shift -= 8)
            b[i] = (byte)(0xFF & (v >> shift));

        return b;
    }

    public String intArrayString(int[] buf) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < buf.length; i++) {
            sb.append(buf[i]).append(" ");
        }
        return sb.toString();
    }

    public String byteArrayString(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < buf.length; i++) {
            sb.append(buf[i]).append(" ");
        }
        return sb.toString();
    }

    /**
     *
     * @param init takes the host address
     * @return String the address as (xxx.xxx.xxx.1)
     **/
    private String convertHost2RouterAddress(InetAddress inet) {

        byte rawIP[] = inet.getAddress();

        // assume router is at xxx.xxx.xxx.1
        rawIP[3] = 1;
        // is there no printf in java?
        String newIP = (rawIP[0]&0xff) +"."+(rawIP[1]&0xff)+"."+(rawIP[2]&0xff)+"."+(rawIP[3]&0xff);
        return newIP;
    }
}