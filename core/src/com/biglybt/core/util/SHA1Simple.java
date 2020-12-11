 /*
 * Created on Apr 12, 2004
 * Created by Olivier Chalouhi
 * Modified Apr 13, 2004 by Alon Rohter
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

package com.biglybt.core.util;

import java.util.Arrays;
import java.util.Random;


/**
 * SHA-1 message digest class.
 */
public final class SHA1Simple {

  private int h0,h1,h2,h3,h4;

  private final byte[]	temp = new byte[64];

  /**
   * Create a new SHA-1 message digest hasher.
   */
  public SHA1Simple(){
  }


  private void transform(byte[] M, int pos) {
    int w0 , w1 , w2 , w3 ,  w4 , w5 , w6 , w7 , w8 , w9 ,
    w10, w11, w12, w13, w14, w15;

    int a,b,c,d,e;

	   w0 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w1 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w2 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w3 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w4 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w5 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w6 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w7 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w8 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w9 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w10 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w11 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w12 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w13 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w14 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;
	   w15 = (int)((((M[pos] & 0xff) << 24) |((M[pos+1] & 0xff) << 16) |((M[pos+2] & 0xff) <<  8) |((M[pos+3] & 0xff) <<  0)));	pos+=4;


    a = h0 ; b = h1 ; c = h2 ; d = h3 ; e = h4;
    e += ((a << 5) | ( a >>> 27)) + w0 + ((b & c) | ((~b ) & d)) + 0x5A827999 ;
    b = (b << 30) | (b >>> 2) ;
    d += ((e << 5) | ( e >>> 27)) + w1 + ((a & b) | ((~a ) & c)) + 0x5A827999 ;
    a = (a << 30) | (a >>> 2) ;
    c += ((d << 5) | ( d >>> 27)) + w2 + ((e & a) | ((~e ) & b)) + 0x5A827999 ;
    e = (e << 30) | (e >>> 2) ;
    b += ((c << 5) | ( c >>> 27)) + w3 + ((d & e) | ((~d ) & a)) + 0x5A827999 ;
    d = (d << 30) | (d >>> 2) ;
    a += ((b << 5) | ( b >>> 27)) + w4 + ((c & d) | ((~c ) & e)) + 0x5A827999 ;
    c = (c << 30) | (c >>> 2) ;
    e += ((a << 5) | ( a >>> 27)) + w5 + ((b & c) | ((~b ) & d)) + 0x5A827999 ;
    b = (b << 30) | (b >>> 2) ;
    d += ((e << 5) | ( e >>> 27)) + w6 + ((a & b) | ((~a ) & c)) + 0x5A827999 ;
    a = (a << 30) | (a >>> 2) ;
    c += ((d << 5) | ( d >>> 27)) + w7 + ((e & a) | ((~e ) & b)) + 0x5A827999 ;
    e = (e << 30) | (e >>> 2) ;
    b += ((c << 5) | ( c >>> 27)) + w8 + ((d & e) | ((~d ) & a)) + 0x5A827999 ;
    d = (d << 30) | (d >>> 2) ;
    a += ((b << 5) | ( b >>> 27)) + w9 + ((c & d) | ((~c ) & e)) + 0x5A827999 ;
    c = (c << 30) | (c >>> 2) ;
    e += ((a << 5) | ( a >>> 27)) + w10 + ((b & c) | ((~b ) & d)) + 0x5A827999 ;
    b = (b << 30) | (b >>> 2) ;
    d += ((e << 5) | ( e >>> 27)) + w11 + ((a & b) | ((~a ) & c)) + 0x5A827999 ;
    a = (a << 30) | (a >>> 2) ;
    c += ((d << 5) | ( d >>> 27)) + w12 + ((e & a) | ((~e ) & b)) + 0x5A827999 ;
    e = (e << 30) | (e >>> 2) ;
    b += ((c << 5) | ( c >>> 27)) + w13 + ((d & e) | ((~d ) & a)) + 0x5A827999 ;
    d = (d << 30) | (d >>> 2) ;
    a += ((b << 5) | ( b >>> 27)) + w14 + ((c & d) | ((~c ) & e)) + 0x5A827999 ;
    c = (c << 30) | (c >>> 2) ;
    e += ((a << 5) | ( a >>> 27)) + w15 + ((b & c) | ((~b ) & d)) + 0x5A827999 ;
    b = (b << 30) | (b >>> 2) ;
    w0 = w13 ^ w8 ^ w2 ^ w0; w0 = (w0 << 1) | (w0 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w0 + ((a & b) | ((~a ) & c)) + 0x5A827999 ;
    a = (a << 30) | (a >>> 2) ;
    w1 = w14 ^ w9 ^ w3 ^ w1; w1 = (w1 << 1) | (w1 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w1 + ((e & a) | ((~e ) & b)) + 0x5A827999 ;
    e = (e << 30) | (e >>> 2) ;
    w2 = w15 ^ w10 ^ w4 ^ w2; w2 = (w2 << 1) | (w2 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w2 + ((d & e) | ((~d ) & a)) + 0x5A827999 ;
    d = (d << 30) | (d >>> 2) ;
    w3 = w0 ^ w11 ^ w5 ^ w3; w3 = (w3 << 1) | (w3 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w3 + ((c & d) | ((~c ) & e)) + 0x5A827999 ;
    c = (c << 30) | (c >>> 2) ;
    w4 = w1 ^ w12 ^ w6 ^ w4; w4 = (w4 << 1) | (w4 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w4 + (b ^ c ^ d) + 0x6ED9EBA1 ;
    b = (b << 30) | (b >>> 2) ;
    w5 = w2 ^ w13 ^ w7 ^ w5; w5 = (w5 << 1) | (w5 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w5 + (a ^ b ^ c) + 0x6ED9EBA1 ;
    a = (a << 30) | (a >>> 2) ;
    w6 = w3 ^ w14 ^ w8 ^ w6; w6 = (w6 << 1) | (w6 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w6 + (e ^ a ^ b) + 0x6ED9EBA1 ;
    e = (e << 30) | (e >>> 2) ;
    w7 = w4 ^ w15 ^ w9 ^ w7; w7 = (w7 << 1) | (w7 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w7 + (d ^ e ^ a) + 0x6ED9EBA1 ;
    d = (d << 30) | (d >>> 2) ;
    w8 = w5 ^ w0 ^ w10 ^ w8; w8 = (w8 << 1) | (w8 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w8 + (c ^ d ^ e) + 0x6ED9EBA1 ;
    c = (c << 30) | (c >>> 2) ;
    w9 = w6 ^ w1 ^ w11 ^ w9; w9 = (w9 << 1) | (w9 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w9 + (b ^ c ^ d) + 0x6ED9EBA1 ;
    b = (b << 30) | (b >>> 2) ;
    w10 = w7 ^ w2 ^ w12 ^ w10; w10 = (w10 << 1) | (w10 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w10 + (a ^ b ^ c) + 0x6ED9EBA1 ;
    a = (a << 30) | (a >>> 2) ;
    w11 = w8 ^ w3 ^ w13 ^ w11; w11 = (w11 << 1) | (w11 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w11 + (e ^ a ^ b) + 0x6ED9EBA1 ;
    e = (e << 30) | (e >>> 2) ;
    w12 = w9 ^ w4 ^ w14 ^ w12; w12 = (w12 << 1) | (w12 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w12 + (d ^ e ^ a) + 0x6ED9EBA1 ;
    d = (d << 30) | (d >>> 2) ;
    w13 = w10 ^ w5 ^ w15 ^ w13; w13 = (w13 << 1) | (w13 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w13 + (c ^ d ^ e) + 0x6ED9EBA1 ;
    c = (c << 30) | (c >>> 2) ;
    w14 = w11 ^ w6 ^ w0 ^ w14; w14 = (w14 << 1) | (w14 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w14 + (b ^ c ^ d) + 0x6ED9EBA1 ;
    b = (b << 30) | (b >>> 2) ;
    w15 = w12 ^ w7 ^ w1 ^ w15; w15 = (w15 << 1) | (w15 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w15 + (a ^ b ^ c) + 0x6ED9EBA1 ;
    a = (a << 30) | (a >>> 2) ;
    w0 = w13 ^ w8 ^ w2 ^ w0; w0 = (w0 << 1) | (w0 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w0 + (e ^ a ^ b) + 0x6ED9EBA1 ;
    e = (e << 30) | (e >>> 2) ;
    w1 = w14 ^ w9 ^ w3 ^ w1; w1 = (w1 << 1) | (w1 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w1 + (d ^ e ^ a) + 0x6ED9EBA1 ;
    d = (d << 30) | (d >>> 2) ;
    w2 = w15 ^ w10 ^ w4 ^ w2; w2 = (w2 << 1) | (w2 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w2 + (c ^ d ^ e) + 0x6ED9EBA1 ;
    c = (c << 30) | (c >>> 2) ;
    w3 = w0 ^ w11 ^ w5 ^ w3; w3 = (w3 << 1) | (w3 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w3 + (b ^ c ^ d) + 0x6ED9EBA1 ;
    b = (b << 30) | (b >>> 2) ;
    w4 = w1 ^ w12 ^ w6 ^ w4; w4 = (w4 << 1) | (w4 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w4 + (a ^ b ^ c) + 0x6ED9EBA1 ;
    a = (a << 30) | (a >>> 2) ;
    w5 = w2 ^ w13 ^ w7 ^ w5; w5 = (w5 << 1) | (w5 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w5 + (e ^ a ^ b) + 0x6ED9EBA1 ;
    e = (e << 30) | (e >>> 2) ;
    w6 = w3 ^ w14 ^ w8 ^ w6; w6 = (w6 << 1) | (w6 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w6 + (d ^ e ^ a) + 0x6ED9EBA1 ;
    d = (d << 30) | (d >>> 2) ;
    w7 = w4 ^ w15 ^ w9 ^ w7; w7 = (w7 << 1) | (w7 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w7 + (c ^ d ^ e) + 0x6ED9EBA1 ;
    c = (c << 30) | (c >>> 2) ;
    w8 = w5 ^ w0 ^ w10 ^ w8; w8 = (w8 << 1) | (w8 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w8 + ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC ;
    b = (b << 30) | (b >>> 2) ;
    w9 = w6 ^ w1 ^ w11 ^ w9; w9 = (w9 << 1) | (w9 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w9 + ((a & b) | (a & c) | (b & c)) + 0x8F1BBCDC ;
    a = (a << 30) | (a >>> 2) ;
    w10 = w7 ^ w2 ^ w12 ^ w10; w10 = (w10 << 1) | (w10 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w10 + ((e & a) | (e & b) | (a & b)) + 0x8F1BBCDC ;
    e = (e << 30) | (e >>> 2) ;
    w11 = w8 ^ w3 ^ w13 ^ w11; w11 = (w11 << 1) | (w11 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w11 + ((d & e) | (d & a) | (e & a)) + 0x8F1BBCDC ;
    d = (d << 30) | (d >>> 2) ;
    w12 = w9 ^ w4 ^ w14 ^ w12; w12 = (w12 << 1) | (w12 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w12 + ((c & d) | (c & e) | (d & e)) + 0x8F1BBCDC ;
    c = (c << 30) | (c >>> 2) ;
    w13 = w10 ^ w5 ^ w15 ^ w13; w13 = (w13 << 1) | (w13 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w13 + ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC ;
    b = (b << 30) | (b >>> 2) ;
    w14 = w11 ^ w6 ^ w0 ^ w14; w14 = (w14 << 1) | (w14 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w14 + ((a & b) | (a & c) | (b & c)) + 0x8F1BBCDC ;
    a = (a << 30) | (a >>> 2) ;
    w15 = w12 ^ w7 ^ w1 ^ w15; w15 = (w15 << 1) | (w15 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w15 + ((e & a) | (e & b) | (a & b)) + 0x8F1BBCDC ;
    e = (e << 30) | (e >>> 2) ;
    w0 = w13 ^ w8 ^ w2 ^ w0; w0 = (w0 << 1) | (w0 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w0 + ((d & e) | (d & a) | (e & a)) + 0x8F1BBCDC ;
    d = (d << 30) | (d >>> 2) ;
    w1 = w14 ^ w9 ^ w3 ^ w1; w1 = (w1 << 1) | (w1 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w1 + ((c & d) | (c & e) | (d & e)) + 0x8F1BBCDC ;
    c = (c << 30) | (c >>> 2) ;
    w2 = w15 ^ w10 ^ w4 ^ w2; w2 = (w2 << 1) | (w2 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w2 + ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC ;
    b = (b << 30) | (b >>> 2) ;
    w3 = w0 ^ w11 ^ w5 ^ w3; w3 = (w3 << 1) | (w3 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w3 + ((a & b) | (a & c) | (b & c)) + 0x8F1BBCDC ;
    a = (a << 30) | (a >>> 2) ;
    w4 = w1 ^ w12 ^ w6 ^ w4; w4 = (w4 << 1) | (w4 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w4 + ((e & a) | (e & b) | (a & b)) + 0x8F1BBCDC ;
    e = (e << 30) | (e >>> 2) ;
    w5 = w2 ^ w13 ^ w7 ^ w5; w5 = (w5 << 1) | (w5 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w5 + ((d & e) | (d & a) | (e & a)) + 0x8F1BBCDC ;
    d = (d << 30) | (d >>> 2) ;
    w6 = w3 ^ w14 ^ w8 ^ w6; w6 = (w6 << 1) | (w6 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w6 + ((c & d) | (c & e) | (d & e)) + 0x8F1BBCDC ;
    c = (c << 30) | (c >>> 2) ;
    w7 = w4 ^ w15 ^ w9 ^ w7; w7 = (w7 << 1) | (w7 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w7 + ((b & c) | (b & d) | (c & d)) + 0x8F1BBCDC ;
    b = (b << 30) | (b >>> 2) ;
    w8 = w5 ^ w0 ^ w10 ^ w8; w8 = (w8 << 1) | (w8 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w8 + ((a & b) | (a & c) | (b & c)) + 0x8F1BBCDC ;
    a = (a << 30) | (a >>> 2) ;
    w9 = w6 ^ w1 ^ w11 ^ w9; w9 = (w9 << 1) | (w9 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w9 + ((e & a) | (e & b) | (a & b)) + 0x8F1BBCDC ;
    e = (e << 30) | (e >>> 2) ;
    w10 = w7 ^ w2 ^ w12 ^ w10; w10 = (w10 << 1) | (w10 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w10 + ((d & e) | (d & a) | (e & a)) + 0x8F1BBCDC ;
    d = (d << 30) | (d >>> 2) ;
    w11 = w8 ^ w3 ^ w13 ^ w11; w11 = (w11 << 1) | (w11 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w11 + ((c & d) | (c & e) | (d & e)) + 0x8F1BBCDC ;
    c = (c << 30) | (c >>> 2) ;
    w12 = w9 ^ w4 ^ w14 ^ w12; w12 = (w12 << 1) | (w12 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w12 + (b ^ c ^ d) + 0xCA62C1D6 ;
    b = (b << 30) | (b >>> 2) ;
    w13 = w10 ^ w5 ^ w15 ^ w13; w13 = (w13 << 1) | (w13 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w13 + (a ^ b ^ c) + 0xCA62C1D6 ;
    a = (a << 30) | (a >>> 2) ;
    w14 = w11 ^ w6 ^ w0 ^ w14; w14 = (w14 << 1) | (w14 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w14 + (e ^ a ^ b) + 0xCA62C1D6 ;
    e = (e << 30) | (e >>> 2) ;
    w15 = w12 ^ w7 ^ w1 ^ w15; w15 = (w15 << 1) | (w15 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w15 + (d ^ e ^ a) + 0xCA62C1D6 ;
    d = (d << 30) | (d >>> 2) ;
    w0 = w13 ^ w8 ^ w2 ^ w0; w0 = (w0 << 1) | (w0 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w0 + (c ^ d ^ e) + 0xCA62C1D6 ;
    c = (c << 30) | (c >>> 2) ;
    w1 = w14 ^ w9 ^ w3 ^ w1; w1 = (w1 << 1) | (w1 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w1 + (b ^ c ^ d) + 0xCA62C1D6 ;
    b = (b << 30) | (b >>> 2) ;
    w2 = w15 ^ w10 ^ w4 ^ w2; w2 = (w2 << 1) | (w2 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w2 + (a ^ b ^ c) + 0xCA62C1D6 ;
    a = (a << 30) | (a >>> 2) ;
    w3 = w0 ^ w11 ^ w5 ^ w3; w3 = (w3 << 1) | (w3 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w3 + (e ^ a ^ b) + 0xCA62C1D6 ;
    e = (e << 30) | (e >>> 2) ;
    w4 = w1 ^ w12 ^ w6 ^ w4; w4 = (w4 << 1) | (w4 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w4 + (d ^ e ^ a) + 0xCA62C1D6 ;
    d = (d << 30) | (d >>> 2) ;
    w5 = w2 ^ w13 ^ w7 ^ w5; w5 = (w5 << 1) | (w5 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w5 + (c ^ d ^ e) + 0xCA62C1D6 ;
    c = (c << 30) | (c >>> 2) ;
    w6 = w3 ^ w14 ^ w8 ^ w6; w6 = (w6 << 1) | (w6 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w6 + (b ^ c ^ d) + 0xCA62C1D6 ;
    b = (b << 30) | (b >>> 2) ;
    w7 = w4 ^ w15 ^ w9 ^ w7; w7 = (w7 << 1) | (w7 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w7 + (a ^ b ^ c) + 0xCA62C1D6 ;
    a = (a << 30) | (a >>> 2) ;
    w8 = w5 ^ w0 ^ w10 ^ w8; w8 = (w8 << 1) | (w8 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w8 + (e ^ a ^ b) + 0xCA62C1D6 ;
    e = (e << 30) | (e >>> 2) ;
    w9 = w6 ^ w1 ^ w11 ^ w9; w9 = (w9 << 1) | (w9 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w9 + (d ^ e ^ a) + 0xCA62C1D6 ;
    d = (d << 30) | (d >>> 2) ;
    w10 = w7 ^ w2 ^ w12 ^ w10; w10 = (w10 << 1) | (w10 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w10 + (c ^ d ^ e) + 0xCA62C1D6 ;
    c = (c << 30) | (c >>> 2) ;
    w11 = w8 ^ w3 ^ w13 ^ w11; w11 = (w11 << 1) | (w11 >>> 31) ;
    e += ((a << 5) | ( a >>> 27)) + w11 + (b ^ c ^ d) + 0xCA62C1D6 ;
    b = (b << 30) | (b >>> 2) ;
    w12 = w9 ^ w4 ^ w14 ^ w12; w12 = (w12 << 1) | (w12 >>> 31) ;
    d += ((e << 5) | ( e >>> 27)) + w12 + (a ^ b ^ c) + 0xCA62C1D6 ;
    a = (a << 30) | (a >>> 2) ;
    w13 = w10 ^ w5 ^ w15 ^ w13; w13 = (w13 << 1) | (w13 >>> 31) ;
    c += ((d << 5) | ( d >>> 27)) + w13 + (e ^ a ^ b) + 0xCA62C1D6 ;
    e = (e << 30) | (e >>> 2) ;
    w14 = w11 ^ w6 ^ w0 ^ w14; w14 = (w14 << 1) | (w14 >>> 31) ;
    b += ((c << 5) | ( c >>> 27)) + w14 + (d ^ e ^ a) + 0xCA62C1D6 ;
    d = (d << 30) | (d >>> 2) ;
    w15 = w12 ^ w7 ^ w1 ^ w15; w15 = (w15 << 1) | (w15 >>> 31) ;
    a += ((b << 5) | ( b >>> 27)) + w15 + (c ^ d ^ e) + 0xCA62C1D6 ;
    c = (c << 30) | (c >>> 2) ;

    h0 += a;
    h1 += b;
    h2 += c;
    h3 += d;
    h4 += e;
  }


	public byte[]
	calculateHash(
		byte[] buffer )
	{
		return( calculateHash( buffer, 0, buffer.length ));
	}

  	public byte[]
  	calculateHash(
		final byte[] 	buffer,
		final int		offset,
		final int		length )
	{
		h0 = 0x67452301;
	    h1 = 0xEFCDAB89;
	    h2 = 0x98BADCFE;
	    h3 = 0x10325476;
	    h4 = 0xC3D2E1F0;

		int	pos = offset;
		int	rem	= length;

		while( rem >= 64) {

			transform( buffer, pos );

			pos += 64;
			rem -= 64;
		}

		if ( rem > 0 ){

			System.arraycopy( buffer, pos, temp, 0, rem );

			pos = rem;

		}else{

			pos = 0;
		}

		temp[pos++]	= ((byte)0x80);

		if ( pos > 56 ){

			for (int i=pos;i<64;i++){

				temp[i] = 0;
			}

			transform( temp, 0 );

			pos = 0;
		}

		for (int i=pos;i<56;i++){

			temp[i] = 0;
		}

		long	l = length << 3;

		temp[56]	= (byte)(l >> 56);
		temp[57]	= (byte)(l >> 48);
		temp[58]	= (byte)(l >> 40);
		temp[59]	= (byte)(l >> 32);
		temp[60]	= (byte)(l >> 24);
		temp[61]	= (byte)(l >> 16);
		temp[62]	= (byte)(l >> 8);
		temp[63]	= (byte)(l);

		transform( temp, 0 );

		byte[] result = new byte[20];

		result[0] = (byte)(h0>>24);
		result[1] = (byte)(h0>>16);
		result[2] = (byte)(h0>>8);
		result[3] = (byte)(h0>>0);
		result[4] = (byte)(h1>>24);
		result[5] = (byte)(h1>>16);
		result[6] = (byte)(h1>>8);
		result[7] = (byte)(h1>>0);
		result[8] = (byte)(h2>>24);
		result[9] = (byte)(h2>>16);
		result[10] = (byte)(h2>>8);
		result[11] = (byte)(h2>>0);
		result[12] = (byte)(h3>>24);
		result[13] = (byte)(h3>>16);
		result[14] = (byte)(h3>>8);
		result[15] = (byte)(h3>>0);
		result[16] = (byte)(h4>>24);
		result[17] = (byte)(h4>>16);
		result[18] = (byte)(h4>>8);
		result[19] = (byte)(h4>>0);

		return( result );
	}
  	/*
  public static void
  main(
	String[]	args )
  {
	  SHA1Hasher	s1 = new SHA1Hasher();
	  SHA1Simple	s2 = new SHA1Simple();

	  Random	r = new Random();

	  for (int i=0;i<10000;i++){

		  int	len = r.nextInt(32);

		  byte[]	x = new byte[len];

		  r.nextBytes( x );

		  byte[]	h1 = s1.calculateHash( x );
		  byte[]	h2 = s2.calculateHash( x );

		  if ( Arrays.equals( h1, h2)){

			  System.out.println( ByteFormatter.nicePrint( h1 ) + " - " + ByteFormatter.nicePrint( x ));

		  }else{

			  System.out.println( "arghh" );

			  return;
		  }
	  }

	  System.out.println( "End" );
  }
  */
}
