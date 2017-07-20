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

#define PROBE_TIMEOUT			5000
#define TRACE_ROUTE_BASE_PORT	48132

#pragma pack (1)

typedef struct
{
  unsigned char  ip_header_len:4;  
  unsigned char  ip_version:4; 
  unsigned char  tos;             
  unsigned short total_len;       
  unsigned short ident;          
  unsigned short frag_and_flags;  
  unsigned char  ttl;           
  unsigned char  protocol;          
  unsigned short checksum;    
  unsigned int   source_ip;   
  unsigned int   dest_ip;  
} ip_header;


typedef struct
{
	unsigned int	source_ip;
	unsigned int	dest_ip;
	unsigned char	zero;
	unsigned char	protocol;
	unsigned short	data_len;
} pseudo_udp_header;

typedef struct 
{
	unsigned short	source_port;
	unsigned short	dest_port;		
	unsigned short	data_len;	
	unsigned short	checksum;	
} udp_header;

	// basic icmp header

#define ICMP_TYPE_ECHO					8
#define ICMP_TYPE_ECHO_REPLY			0
#define ICMP_TYPE_UNREACHABLE			3
#define ICMP_TYPE_TTL_EXCEEDED			11

#define ICMP_CODE_PROTOCOL_UNREACHABLE	2
#define ICMP_CODE_PORT_UNREACHABLE		3



typedef struct
{
  unsigned char		type;   
  unsigned char		code;  
  unsigned short	checksum;  
  unsigned int		unused;
} icmp_header;

typedef struct
{
  unsigned char		type;   
  unsigned char		code;  
  unsigned short	checksum;  
  unsigned short	ident;
  unsigned short	sequence;
} icmp_echo_header;

typedef struct 
{
    ip_header		ip;
    udp_header		udp;
	unsigned int	data;
} udp_probe_packet;

typedef struct 
{
    ip_header			ip;
    icmp_echo_header	icmp;
	unsigned int		data;
} icmp_probe_packet;