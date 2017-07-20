/*
 * Created by Joseph Bridgewater
 * Created on Feb 24, 2006
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

/**
 * @author MjrTom Feb 24, 2006
 * This is for utilities for calculating custom object hashCode() values
 */
public final class HashCodeUtils
{
    public static int hashMore(final int hash, final int more)
    {
        int result =hash <<1;
        if (result <0)
            result |=1;
        return result ^more;
    }

    public static int hashMore(final int hash, final long more)
    {
        int result =hashMore(hash, (int)(more >>>32));
        return hashMore(result, (int)(more &0xffff));
    }

    public static int hashMore(final int hash, final boolean[] more)
    {
        int result =hash <<1;
        if (result <0)
            result |=1;
        if (more[0])
            result ^=1;
        for (int i =1; i <more.length; i++)
        {
            result <<=1;
            if (result <0)
                result |=1;
            if (more[i])
                result ^=1;
        }
        return result;
    }

    /**
     * bob jenkin's hash function
     */
    public static int hashCode(byte[] array)
    {
    	int hash = 0;
        for (int i = 0; i < array.length; i++) {
            hash += array[i];
            hash += (hash << 10);
            hash ^= (hash >> 6);
        }
        hash += (hash << 3);
        hash ^= (hash >> 11);
        hash += (hash << 15);
        return hash;
    }

    public static int hashCode(char[] array )
    {
         int h = 0;
         int len =array.length;
         for (int i = 0; i < len; i++) {
        	 h = 31*h + array[i];
         }
         return( h );
    }
}
