/*
 * Created on 1 Nov 2006
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


#include "framework.h"

#include "stdio.h"
#include "stdlib.h"
#include "windows.h"
#include "time.h"
#include "winsock2.h"
#include "WS2TCPIP.H"

#include "aereg.h"
#include "aenet.h"

#include "com_biglybt_platform_win32_access_impl_AEWin32AccessInterface.h"


// IP	ftp://ftp.rfc-editor.org/in-notes/rfc791.txt
// ICMP	ftp://ftp.rfc-editor.org/in-notes/rfc792.txt
// UDP  ftp://ftp.rfc-editor.org/in-notes/rfc768.txt	

bool winsock_started_up = false;

void 
initialiseWinsock()
{
	if ( !winsock_started_up ){

		winsock_started_up = true;

		WSADATA wdData;

		WSAStartup(MAKEWORD(2, 2), &wdData);
	}
}


unsigned short
calculateChecksum(
	unsigned short const	words[], 
	int						num_words )
{
    unsigned long  sum = 0;

    while (num_words-- > 0){
    
        sum += *(words++);
    }

    sum = (sum >> 16) + (sum & 0xFFFF);
    sum += sum >> 16;

    unsigned short result =  ((unsigned short) ~sum);

	if ( result == 0 ){

		result = 0xffff;
	}

	return( result );
} 

unsigned short
calculateChecksum2(
	unsigned short const	words1[], 
	int						num_words1,
	unsigned short const	words2[], 
	int						num_words2 )
{
    unsigned long  sum = 0;

    while (num_words1-- > 0){
    
        sum += *(words1++);
    }

	while (num_words2-- > 0){
    
        sum += *(words2++);
    }

    sum = (sum >> 16) + (sum & 0xFFFF);
    sum += sum >> 16;

    unsigned short result =  ((unsigned short) ~sum);

	if ( result == 0 ){

		result = 0xffff;
	}

	return( result );
} 

int 
sendTraceRouteMessage(
	JNIEnv*			env,
	unsigned short	trace_id,
	int				time_to_live,
    unsigned int	send_sock,
	unsigned int	recv_sock,
	unsigned long	source_ip,
	unsigned short	source_port,
    unsigned long	target_ip,
	unsigned short	target_port,
	bool			use_udp,
	bool			use_icmp )
{
		// Advice is to use UDP rather than ICMP echos. However, reality is that UDP packets can be totally dropped with no ICMP response. So send both...

		// UDP

	if ( use_udp ){

		udp_probe_packet  udp_probe;  
		
		struct sockaddr_in target;

		memset( &target, 0, sizeof( target ));
    
		target.sin_family		= AF_INET;
		target.sin_addr.s_addr	= htonl( target_ip );
		target.sin_port			= htons( target_port );

		// printf( "Sending to %lx from %lx: ip=%d,udp=%d,tot=%d\n", target_ip, source_ip, sizeof( ip_header ), sizeof( udp_header ), sizeof( probe_packet ));

		memset( &udp_probe, 0, sizeof( udp_probe ));

		udp_probe.ip.ip_version		= 4;
		udp_probe.ip.ip_header_len	= sizeof( ip_header) >> 2;
		udp_probe.ip.tos			= 0;
		udp_probe.ip.total_len		= htons( sizeof( udp_probe_packet ));
		udp_probe.ip.ident			= htons( trace_id );
		udp_probe.ip.frag_and_flags = 0;
		udp_probe.ip.ttl			= time_to_live;
		udp_probe.ip.protocol		= IPPROTO_UDP;
		udp_probe.ip.checksum		= 0;
		udp_probe.ip.source_ip		= htonl( source_ip );
		udp_probe.ip.dest_ip		= htonl( target_ip );

		udp_probe.ip.checksum = calculateChecksum((unsigned short*)&udp_probe.ip, sizeof( ip_header ) / 2 );

		udp_probe.udp.source_port	= htons( source_port );
		udp_probe.udp.dest_port		= htons( target_port  );
		udp_probe.udp.data_len		= htons( sizeof( udp_probe_packet ) - sizeof( ip_header ));
		udp_probe.udp.checksum		= 0;

		udp_probe.data			= 1234;

		pseudo_udp_header	puh;

		puh.source_ip	= htonl( source_ip );
		puh.dest_ip		= htonl( target_ip );
		puh.zero		= 0;
		puh.protocol	= IPPROTO_UDP;
		puh.data_len	= htons( sizeof( udp_probe_packet ) - sizeof( ip_header ));

		udp_probe.udp.checksum = calculateChecksum2( (unsigned short*)&puh, sizeof( pseudo_udp_header ) / 2, (unsigned short *)&udp_probe.udp, (sizeof( udp_probe ) - sizeof( ip_header ))/2);



		
		/*
		char* bytes = (char *)&probe;

		for (int i=0;i<sizeof( probe );i++ ){

			printf( "%x\n", bytes[i] & 0xff );
		}
		printf( "\n" );
		*/
			// whereto = to, contains sin_addr and sin_family

		int	num_sent = sendto(send_sock, (char *)&udp_probe, sizeof( udp_probe ), 0, (struct sockaddr *)&target, sizeof(target));

		if ( num_sent == sizeof( udp_probe ) ){

			if ( !use_icmp ){

				return( 1 );
			}

		}else if ( num_sent == SOCKET_ERROR ){

			throwException( env, "sendto", "UDP operation failed", WSAGetLastError());

			return 0;

		}else{

			throwException( env, "sendto", "UDP incomplete packet sent" );

			return 0;

		}
	}

		// ICMP

	if ( use_icmp ){

		icmp_probe_packet  icmp_probe;  
		
		struct sockaddr_in target;

		memset( &target, 0, sizeof( target ));
    
		target.sin_family		= AF_INET;
		target.sin_addr.s_addr	= htonl( target_ip );
		target.sin_port			= htons( target_port );

		// printf( "Sending to %lx from %lx: ip=%d,udp=%d,tot=%d\n", target_ip, source_ip, sizeof( ip_header ), sizeof( udp_header ), sizeof( probe_packet ));

		memset( &icmp_probe, 0, sizeof( icmp_probe ));

		icmp_probe.ip.ip_version		= 4;
		icmp_probe.ip.ip_header_len	= sizeof( ip_header) >> 2;
		icmp_probe.ip.tos			= 0;
		icmp_probe.ip.total_len		= htons( sizeof( icmp_probe_packet ));
		icmp_probe.ip.ident			= htons( trace_id );
		icmp_probe.ip.frag_and_flags = 0;
		icmp_probe.ip.ttl			= time_to_live;
		icmp_probe.ip.protocol		= IPPROTO_ICMP;
		icmp_probe.ip.checksum		= 0;
		icmp_probe.ip.source_ip		= htonl( source_ip );
		icmp_probe.ip.dest_ip		= htonl( target_ip );

		icmp_probe.ip.checksum = calculateChecksum((unsigned short*)&icmp_probe.ip, sizeof( ip_header ) / 2 );

		icmp_probe.icmp.type		= ICMP_TYPE_ECHO;
		icmp_probe.icmp.code		= 0;
		icmp_probe.icmp.checksum	= 0;
		icmp_probe.icmp.ident		= htons( trace_id );
		icmp_probe.icmp.sequence	= htons( target_port );


		icmp_probe.icmp.checksum = calculateChecksum( (unsigned short*)&icmp_probe.icmp, sizeof( icmp_echo_header ) / 2 );



		/*
		char* bytes = (char *)&probe;

		for (int i=0;i<sizeof( probe );i++ ){

			printf( "%x\n", bytes[i] & 0xff );
		}
		printf( "\n" );
		*/
			// whereto = to, contains sin_addr and sin_family

		int	num_sent = sendto(send_sock, (char *)&icmp_probe, sizeof( icmp_probe ), 0, (struct sockaddr *)&target, sizeof(target));

		if ( num_sent == sizeof( icmp_probe ) ){

			return 1;

		}else if ( num_sent == SOCKET_ERROR ){

			throwException( env, "sendto", "UDP operation failed", WSAGetLastError());

			return 0;

		}else{

			throwException( env, "sendto", "UDP incomplete packet sent" );

			return 0;

		}
	}

	throwException( env, "sendto", "no protocol selected" );

	return( 0 );
}


int
traceRouteReport(
	JNIEnv		*env,
	jobject		callback,
	char*		msg )
{
	jstring	j_msg = env->NewStringUTF( msg );

	if ( j_msg == NULL ){

		throwException( env, "NewStringUTF", "alloc failed" );

		return 0;
	}

	int	res = 0;

	jclass callback_class = env->GetObjectClass( callback );

	if ( callback_class == NULL ){

		throwException( env, "GetObjectClass", "failed" );

		return 0;

	}else{

		jmethodID method = env->GetMethodID( callback_class, "generalMessage", "(Ljava/lang/String;)J");

		if ( method == NULL ){

			throwException( env, "GetMethodID", "method not found" );

		}else{

			res = (long)env->CallLongMethod( callback, method, j_msg );
		}

		env->DeleteLocalRef( callback_class );
	}

	env->ReleaseStringUTFChars( j_msg, msg );

	return( res );
}

int
traceRouteReportTimeout(
	JNIEnv		*env,
	jobject		callback,
	int			ttl )
{
	const size_t buffer_size = 1024;
	char	buffer[buffer_size];

	sprintf_s( buffer, buffer_size, "%ld", ttl );

	return( traceRouteReport( env, callback, buffer ));
}

int
traceRouteReportResult(
	JNIEnv			*env,
	jobject			callback,
	int				ttl,
	unsigned long	address,
	int				time,
	bool			udp )
{
	const size_t buffer_size = 1024;
	char	buffer[buffer_size];

	sprintf_s( buffer, buffer_size, "%d, %ld, %d, %d", ttl, address, time, udp?1:0 );

	return( traceRouteReport( env, callback, buffer ));
}

void
traceRoute(
	JNIEnv*			env,
	jobject			callback,
	unsigned short	trace_id,
	unsigned long	source_ip,
	unsigned long	target_ip,
	unsigned int	send_sock,
	unsigned int	receive_sock,
	bool			ping_mode )
{
	char	receive_buffer[ sizeof(ip_header) + sizeof(icmp_header) + 1024];

	unsigned short source_port = TRACE_ROUTE_BASE_PORT;
	unsigned short target_port = TRACE_ROUTE_BASE_PORT;

	int	seq			= 0;
	int consec_bad	= 0;


	bool	complete	= false;

	bool	use_udp		= true;
	bool	use_icmp	= true;

	int	ttl;

	if ( ping_mode ){

		ttl = 32;

	}else{

		ttl = 0;
	}

	while( ttl < 33 && ( ping_mode || !complete )){

	
		complete = false;

		if ( ping_mode ){

			if ( consec_bad > 256 ){

				throwException( env, "error", "too many consecutive bad packets in ping mode" );

				return;
			}

		}else{

			ttl++;
		}

			// try each node up to 3 times

		bool probe_successful = false;

		for (int probe_count=0; probe_count<3 && !( complete || probe_successful); probe_count++){

			int	probe_sequence = seq++;

			if ( !sendTraceRouteMessage( env, trace_id, ttl, send_sock, receive_sock, source_ip, source_port, target_ip, target_port + probe_sequence, use_udp, use_icmp )){
			
					// send failed, exception already reported

				return;
			}

			unsigned long	start_ticks = 0;

			while( !complete ){
		
				int receive_timeout = PROBE_TIMEOUT;

				unsigned long	current_ticks = GetTickCount();

				if ( start_ticks == 0 ){

					start_ticks = current_ticks;

				}else{

					receive_timeout -= ( current_ticks - start_ticks );
				}

				if ( receive_timeout <= 0 ){

					if ( !traceRouteReportTimeout( env, callback, ttl )){

						return;
					}

					consec_bad = 0;

					break;
				}

				if( setsockopt(receive_sock, SOL_SOCKET, SO_RCVTIMEO, (char *) &receive_timeout, sizeof(receive_timeout)) == SOCKET_ERROR ){

					throwException( env, "setsockopt", "failed to set receive timeout socket options" );

					return;
				}

				struct sockaddr_in from;
				
				int	from_len = sizeof( from );

				unsigned int	read_len  = recvfrom( receive_sock, receive_buffer, sizeof( receive_buffer ), 0, (struct sockaddr *)&from, &from_len );
				
				if ( read_len == SOCKET_ERROR ){
					
					if ( WSAGetLastError() == WSAETIMEDOUT ){

						if ( !traceRouteReportTimeout( env, callback, ttl )){

							return;
						}

						consec_bad = 0;

						break;

					}else{

						throwException( env, "recvfrom", "operation failed", WSAGetLastError());

						return;
					}

				}else{

					if ( read_len < sizeof( 4 )){

						// printf( "invalid packet read - length < 4\n" );

						consec_bad++;

						continue;
					}

					ip_header	*ip = (ip_header *)receive_buffer;

					unsigned char	ip_len		= ip->ip_header_len << 2;
					unsigned short	total_len	= ntohs( ip->total_len );

						// ensure we have a whole packet and an icmp reply

					if ( read_len != total_len || read_len < ip_len + sizeof(icmp_header)){

						// printf( "invalid packet read - read_len != total_len\n" );

						consec_bad++;

						continue;
					}

					int	elapsed_time = (int)( GetTickCount() - current_ticks );

					icmp_header *icmp = (icmp_header *)(receive_buffer + ip_len );

					unsigned char	icmp_type	= icmp->type;

					if ( icmp_type == ICMP_TYPE_ECHO_REPLY ){

						icmp_echo_header *icmp = (icmp_echo_header *)(receive_buffer + ip_len );

						unsigned short	reply_seq = ntohs( icmp->sequence ) - TRACE_ROUTE_BASE_PORT;

						if ( reply_seq != probe_sequence ){

							consec_bad++;

							continue;
						}

						if ( !traceRouteReportResult( env, callback, ttl, ntohl( from.sin_addr.s_addr ), elapsed_time, false )){

							return;
						}

						consec_bad = 0;

						if ( ping_mode ){

							use_udp = false;
						}

						complete = true;

						break;
					}

					if ( read_len != total_len || read_len < ip_len + sizeof(icmp_header) + sizeof( ip_header ) + 2){

						// printf( "invalid packet read - read_len != total_len\n" );

						consec_bad++;

						continue;
					}

					
					ip_header* old_ip = (ip_header *)(receive_buffer + ip_len + sizeof( icmp_header ));

					if (	icmp_type != ICMP_TYPE_TTL_EXCEEDED &&
							icmp_type != ICMP_TYPE_UNREACHABLE ){

						// printf( "Unexpected ICMP reply type %d\n", icmp_type );

						consec_bad++;

						continue;
					}
		
					if ( trace_id != ntohs(old_ip->ident )){

							// not our reply

						consec_bad++;

						continue;
					}

					bool	reply_was_udp;

					if ( old_ip->protocol == IPPROTO_ICMP ){

						icmp_probe_packet*	probe = (icmp_probe_packet *)(receive_buffer + ip_len + sizeof( icmp_header ));

						unsigned short	reply_seq = ntohs( probe->icmp.sequence ) - TRACE_ROUTE_BASE_PORT;

						if ( reply_seq != probe_sequence ){

							consec_bad++;

							continue;
						}

						reply_was_udp = false;

					}else{
					
						udp_probe_packet*	probe = (udp_probe_packet *)(receive_buffer + ip_len + sizeof( icmp_header ));

						unsigned short	reply_seq = ntohs( probe->udp.dest_port ) - TRACE_ROUTE_BASE_PORT;

						if ( reply_seq != probe_sequence ){

							consec_bad++;

							continue;
						}

						reply_was_udp = true;
					}

					probe_successful = true;

					if ( icmp_type == ICMP_TYPE_ECHO_REPLY ){

						complete = true;

					}else if ( icmp_type == ICMP_TYPE_TTL_EXCEEDED ){
					

					}else if ( icmp_type == ICMP_TYPE_UNREACHABLE ){

						unsigned char	icmp_code	= icmp->code;

						if (	icmp_code == ICMP_CODE_PROTOCOL_UNREACHABLE ||
								icmp_code == ICMP_CODE_PORT_UNREACHABLE ){

								// these are received from the target host

							complete	= true;

						}else{

						}		   
					}

					if ( ping_mode ){

						if ( reply_was_udp ){

							use_icmp = false;

						}else{

							use_udp = false;
						}
					}

					if ( !traceRouteReportResult( env, callback, ttl, ntohl( from.sin_addr.s_addr ), elapsed_time, reply_was_udp )){

						return;
					}

					consec_bad = 0;

					break;
				}
	
			}	
		}
	}

	return;
}


JNIEXPORT void JNICALL 
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_traceRoute(
	JNIEnv*		env,
	jclass		cla, 
	jint		trace_id,
	jint		source_ip,
	jint		target_ip,
	jint		ping_mode,
	jobject		callback )
{
	initialiseWinsock();

	int receive_sock = socket( AF_INET, SOCK_RAW, IPPROTO_ICMP );

	if ( receive_sock == SOCKET_ERROR ) {

		throwException( env, "socket", "failed to create receive socket" );

		return;
	}

	int receive_timeout = PROBE_TIMEOUT;

	if( setsockopt(receive_sock, SOL_SOCKET, SO_RCVTIMEO, (char *) &receive_timeout, sizeof(receive_timeout)) == SOCKET_ERROR ){

		closesocket(receive_sock);

		throwException( env, "setsockopt", "failed to set receive timeout socket options" );

		return;
	}

	int so_reuseaddress = 1;

	if( setsockopt(receive_sock, SOL_SOCKET, SO_REUSEADDR, (char *) &so_reuseaddress, sizeof(so_reuseaddress)) == SOCKET_ERROR ){

		closesocket(receive_sock);

		throwException( env, "setsockopt", "failed to set receive reuse address socket options" );

		return;
	}

		// we have to bind the socket before we can receive ICMP messages on it
	
	struct sockaddr_in				recv_bind_addr;

	recv_bind_addr.sin_family		= AF_INET;
	recv_bind_addr.sin_addr.s_addr	= htonl( source_ip );
	recv_bind_addr.sin_port			= 0;

	if ( bind( receive_sock, (struct sockaddr *)&recv_bind_addr, sizeof( recv_bind_addr )) == SOCKET_ERROR ){

		closesocket(receive_sock);
	
		throwException( env, "socket", "failed to bind send socket" );

		return;
	}


	int send_sock = socket( AF_INET, SOCK_RAW, IPPROTO_RAW );

	if ( send_sock == SOCKET_ERROR ) {

		closesocket(receive_sock);
	
		throwException( env, "socket", "failed to create send socket" );

		return;
	}

	int	enable = 1;

	if ( setsockopt(send_sock, IPPROTO_IP, IP_HDRINCL, (char *)&enable,sizeof(enable)) == SOCKET_ERROR ){

		closesocket(send_sock);
		closesocket(receive_sock);

		throwException( env, "setsockopt", "failed to set receive socket options" );

		return;
	}


	traceRoute( env, callback, (unsigned short)trace_id, (unsigned long)source_ip, (unsigned long)target_ip, send_sock, receive_sock, ping_mode==1?true:false );

	closesocket(send_sock);
	closesocket(receive_sock);
}



