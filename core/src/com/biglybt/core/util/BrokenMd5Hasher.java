/********************************************************************************
 *
 * jMule - a Java massive parallel file sharing client
 *
 * Copyright (C) by the jMuleGroup ( see the CREDITS file )
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc.,  59 Temple Plac(int)e, Suite 330, Boston, MA  02111-1307  USA
 *
 * $Id: BrokenMd5Hasher.java,v 1.1 2005-11-16 13:36:23 parg Exp $
 *
 ********************************************************************************/

package com.biglybt.core.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Use this class for getting a MD5 message digest.
 * Create a MD5 and reuse it after a message digest calculation. There can be as
 * many MD5 objects as you want to have multiple calculations same time.
 * The message can be passed in one or a sequenze of parts wrapped in a
 * ByteBuffer to the update of the same MD5 instance. To finish the calculation
 * use final, it will reset the MD5 instance for a new calculation.
 *
 * @author emarant
 * @version $Revision: 1.1 $
 * <br>Last changed by $Author: parg $ on $Date: 2005-11-16 13:36:23 $
 */
public final class BrokenMd5Hasher {

    private final ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private int stateA = 0x67452301;
    private int stateB = 0xEFCDAB89;
    private int stateC = 0x98BADCFE;
    private int stateD = 0x10325476;
    private long count = 0;

    /**
    * Constructor returns a MD6 ready for use.
    */
    public BrokenMd5Hasher(){
    }

    public byte[]
    calculateHash(
    	byte[]		data )
    {
    	ByteBuffer input_buffer = ByteBuffer.wrap(data);

    	reset();

    	update(input_buffer);

    	ByteBuffer result_buffer = ByteBuffer.allocate(16);

    	finalDigest(result_buffer);

    	byte[] result = new byte[16];

    	result_buffer.position(0);

    	for(int i = 0 ; i < result.length ; i++) {

    		result[i] = result_buffer.get();
    	}

    	return result;
    }

    /**
    * Resets the MD5 to initial state for a new message digest calculation.
    */
    public void reset(){
        stateA = 0x67452301;
        stateB = 0xEFCDAB89;
        stateC = 0x98BADCFE;
        stateD = 0x10325476;
        count = 0;
        buffer.rewind();
        for(int i=0;i<64;i++){
            buffer.put((byte)0);
        }
        buffer.rewind();
    }

    /**
    * Starts or continues a MD5 message digest calculation.
    * input.remaining() should be a multiple of 64 to be most efficant, but
    * other amounts work too. Only remaining bytes of the ByteBuffer are used
    * and input.position() will be input.limit() after return.
    * @param input hold a part of the message. input.order() have to be ByteOrder.LITTLE_ENDIAN
    */
    public void update(ByteBuffer input){
        int index, partLen, i, inputLen;
        inputLen = input.remaining();
        index = ((int)count) & 63;
        count += inputLen;
        partLen = 64 - index;
        i = 0;
        if (inputLen >= partLen){
            if (index>0){
                int t = input.limit();
                input.limit(input.position()+partLen);
                buffer.put(input);
                buffer.rewind();
                input.limit(t);
                transform(buffer);
                buffer.rewind();
                i = partLen;
                index = partLen;
            }

            while(i + 63 < inputLen){
                transform(input);
                i += 64;
            }
        }
        if (i<inputLen){
            buffer.put(input);
        }
    }

    public void
    update(
    	byte[]		data )
    {
    	update( ByteBuffer.wrap( data ));
    }

    public byte[]
    getDigest()
    {
    	ByteBuffer result_buffer = ByteBuffer.allocate(16);

    	finalDigest(result_buffer);

    	byte[] result = new byte[16];

    	result_buffer.position(0);

    	for(int i = 0 ; i < result.length ; i++) {

    		result[i] = result_buffer.get();
    	}

    	return result;
    }

    /**
    * Finishes an MD5 message digest calculation.
    * The result is stored in digest and the MD5-object is <b>reset</b> and so
    * ready for a new message digest calculation.
    *
    * @param digest should be a ByteBuffer with digest.remaining() &gt;= 16
    *
    */
    public void finalDigest(ByteBuffer digest){
        int index;

        index = ((int)count) & 63;
        if (index < 56){
            buffer.put((byte)0x80);
            for(int i = index ; i < 55 ;i++)
                buffer.put((byte)0);
            buffer.putLong(count << 3);
            buffer.rewind();
            transform(buffer);
            buffer.rewind();
        }else{
            buffer.put((byte)0x80);
            for(int i = index ; i < 63 ; i++)
                buffer.put((byte)0);
            buffer.rewind();
            transform(buffer);
            buffer.rewind();
            for(int i=0;i<56;i++)
                buffer.put((byte)0);
            buffer.putLong(count << 3);
            buffer.rewind();
            transform(buffer);
            buffer.rewind();
        }
        // save the result in digest
        digest.putInt(stateA);
        digest.putInt(stateB);
        digest.putInt(stateC);
        digest.putInt(stateD);

        reset();
    }

    private void transform(ByteBuffer block) {
        int a, b, c, d;
        long e, f, g, h, i, j, k, l;

        a = stateA;
        b = stateB;
        c = stateC;
        d = stateD;
        e = block.getLong();// 0   1
        f = block.getLong();// 2   3
        g = block.getLong();// 4   5
        h = block.getLong();// 6   7
        i = block.getLong();// 8   9
        j = block.getLong();//10  11
        k = block.getLong();//12  13
        l = block.getLong();//14  15

        a = FF(a, b, c, d, (int)e,  7, 0xd76aa478);
        d = FF(d, a, b, c, (int)(e >>> 32), 12, 0xe8c7b756);
        c = FF(c, d, a, b, (int)f, 17, 0x242070db);
        b = FF(b, c, d, a, (int)(f >>> 32), 22, 0xc1bdceee);
        a = FF(a, b, c, d, (int)g,  7, 0xf57c0faf);
        d = FF(d, a, b, c, (int)(g >>> 32), 12, 0x4787c62a);
        c = FF(c, d, a, b, (int)h, 17, 0xa8304613);
        b = FF(b, c, d, a, (int)(h >>> 32), 22, 0xfd469501);
        a = FF(a, b, c, d, (int)i,  7, 0x698098d8);
        d = FF(d, a, b, c, (int)(i >>> 32), 12, 0x8b44f7af);
        c = FF(c, d, a, b, (int)j, 17, 0xffff5bb1);
        b = FF(b, c, d, a, (int)(j >>> 32), 22, 0x895cd7be);
        a = FF(a, b, c, d, (int)k,  7, 0x6b901122);
        d = FF(d, a, b, c, (int)(k >>> 32), 12, 0xfd987193);
        c = FF(c, d, a, b, (int)l, 17, 0xa679438e);
        b = FF(b, c, d, a, (int)(l >>> 32), 22, 0x49b40821);

        a = GG(a, b, c, d, (int)(e >>>32),  5, 0xf61e2562);
        d = GG(d, a, b, c, (int)h,  9, 0xc040b340);
        c = GG(c, d, a, b, (int)(j >>> 32), 14, 0x265e5a51);
        b = GG(b, c, d, a, (int)e, 20, 0xe9b6c7aa);
        a = GG(a, b, c, d, (int)(g >>> 32),  5, 0xd62f105d);
        d = GG(d, a, b, c, (int)j,  9, 0x02441453);
        c = GG(c, d, a, b, (int)(l >>> 32), 14, 0xd8a1e681);
        b = GG(b, c, d, a, (int)g, 20, 0xe7d3fbc8);
        a = GG(a, b, c, d, (int)(i >>> 32),  5, 0x21e1cde6);
        d = GG(d, a, b, c, (int)l,  9, 0xc33707d6);
        c = GG(c, d, a, b, (int)(f >>> 32), 14, 0xf4d50d87);
        b = GG(b, c, d, a, (int)i, 20, 0x455a14ed);
        a = GG(a, b, c, d, (int)(k >>> 32),  5, 0xa9e3e905);
        d = GG(d, a, b, c, (int)f,  9, 0xfcefa3f8);
        c = GG(c, d, a, b, (int)(h >>> 32), 14, 0x676f02d9);
        b = GG(b, c, d, a, (int)k, 20, 0x8d2a4c8a);

        a = HH(a, b, c, d, (int)(g >>> 32),  4, 0xfffa3942);
        d = HH(d, a, b, c, (int)i, 11, 0x8771f681);
        c = HH(c, d, a, b, (int)(j >>> 32), 16, 0x6d9d6122);
        b = HH(b, c, d, a, (int)l, 23, 0xfde5380c);
        a = HH(a, b, c, d, (int)(e >>>32),  4, 0xa4beea44);
        d = HH(d, a, b, c, (int)g, 11, 0x4bdecfa9);
        c = HH(c, d, a, b, (int)(h >>> 32), 16, 0xf6bb4b60);
        b = HH(b, c, d, a, (int)j, 23, 0xbebfbc70);
        a = HH(a, b, c, d, (int)(k >>> 32),  4, 0x289b7ec6);
        d = HH(d, a, b, c, (int)e, 11, 0xeaa127fa);
        c = HH(c, d, a, b, (int)(f >>> 32), 16, 0xd4ef3085);
        b = HH(b, c, d, a, (int)h, 23, 0x04881d05);
        a = HH(a, b, c, d, (int)(i >>> 32),  4, 0xd9d4d039);
        d = HH(d, a, b, c, (int)k, 11, 0xe6db99e5);
        c = HH(c, d, a, b, (int)(l >>> 32), 16, 0x1fa27cf8);
        b = HH(b, c, d, a, (int)f, 23, 0xc4ac5665);

        a = II(a, b, c, d, (int)e,  6, 0xf4292244);
        d = II(d, a, b, c, (int)(h >>> 32), 10, 0x432aff97);
        c = II(c, d, a, b, (int)l, 15, 0xab9423a7);
        b = II(b, c, d, a, (int)(g >>> 32), 21, 0xfc93a039);
        a = II(a, b, c, d, (int)k,  6, 0x655b59c3);
        d = II(d, a, b, c, (int)(f >>> 32), 10, 0x8f0ccc92);
        c = II(c, d, a, b, (int)j, 15, 0xffeff47d);
        b = II(b, c, d, a, (int)(e >>>32), 21, 0x85845dd1);
        a = II(a, b, c, d, (int)i,  6, 0x6fa87e4f);
        d = II(d, a, b, c, (int)(l >>> 32), 10, 0xfe2ce6e0);
        c = II(c, d, a, b, (int)h, 15, 0xa3014314);
        b = II(b, c, d, a, (int)(k >>> 32), 21, 0x4e0811a1);
        a = II(a, b, c, d, (int)g,  6, 0xf7537e82);
        d = II(d, a, b, c, (int)(j >>> 32), 10, 0xbd3af235);
        c = II(c, d, a, b, (int)f, 15, 0x2ad7d2bb);
        b = II(b, c, d, a, (int)(i >>> 32), 21, 0xeb86d391);

        stateA += a;
        stateB += b;
        stateC += c;
        stateD += d;
    }

    private static int FF(int a, int b, int c, int d, int x, int s, int t) {
        int r = a + x + t + (d ^ (b & (c ^ d)));
        return (r << s | r >>> (32 - s)) + b;
    }

    private static int GG(int a, int b, int c, int d, int x, int s, int t) {
        int r = a + x + t + (c ^ (d & (b ^ c)));
        return (r << s | r >>> (32 - s)) + b;
    }

    private static int HH(int a, int b, int c, int d, int x, int s, int t) {
        int r = a + x + t + (b ^ c ^ d);
        return (r << s | r >>> (32 - s)) + b;
    }

    private static int II(int a, int b, int c, int d, int x, int s, int t) {
        int r = a + x + t + (c ^ (b | ~d));
        return (r << s | r >>> (32 - s)) + b;
    }
}

