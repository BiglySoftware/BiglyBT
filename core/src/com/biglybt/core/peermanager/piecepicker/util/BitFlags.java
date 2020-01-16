/*
 * Created on Oct 30, 2005
 * Created by Joseph Bridgewater
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

package com.biglybt.core.peermanager.piecepicker.util;

import java.util.Arrays;

import com.biglybt.core.util.HashCodeUtils;

/**
 * @author MjrTom
 * A fairly light-weight, versatile boolean array of bit flags with administrative fields and methods
 * Originaly designed as a boolean array to correspond to the pieces in a torrent,
 * for example to show which pieces are; downloading, high priority, rarest, available, or whatever.
 * This class is subject to experimentation, although the important uses of the class must NOT be broken.
 */
public class BitFlags
    implements Cloneable
{
	// These are public so they can be read quickly.
    // Please don't try to modify them outside of the given methods.
    /** Index of first set bit */
    public int          start;
    /** Index of last set bit */
    public int          end;
    /** how many bits are set */
	public int			nbSet;
    /** The array of bit flags */
	public final boolean[]	flags;

	public BitFlags(final int count)
	{
        start =count;
        end =0;
        nbSet =0;
		flags =new boolean[count];
	}

	public BitFlags(final boolean[]	_flags )
	{
        start =_flags.length;
		flags	= _flags;
		for (int i=0;i<flags.length;i++){
			if ( flags[i]){
				nbSet++;
				if ( i < start ){
					start = i;
				}
				end	= i;
			}
		}
	}

    /** clone constructor */
    public BitFlags(final BitFlags other)
    {
        start =other.start;
        end =other.end;
        nbSet =other.nbSet;
        flags =(boolean[])other.flags.clone();
    }

    @Override
    public Object clone()
    {
        return new BitFlags(this);
    }

    public int hashCode()
    {
        int result =HashCodeUtils.hashMore(0, flags);
        result =HashCodeUtils.hashMore(result, nbSet);
        result =HashCodeUtils.hashMore(result, end);
        return HashCodeUtils.hashMore(result, start);
    }

    public boolean equals(Object o)
    {
        if (o ==null ||!(o instanceof BitFlags))
            return false;
        final BitFlags other =(BitFlags) o;
        if (this.start !=other.start)
            return false;
        if (this.end !=other.end)
            return false;
        if (this.nbSet !=other.nbSet)
            return false;
        if (this.flags ==null &&other.flags ==null)
            return true;
        if (this.flags ==null ||other.flags ==null)
            return false;
        if (this.flags.length !=other.flags.length)
            return false;
        for (int i =0; i <this.flags.length; i++)
        {
            if (this.flags[i] ^ other.flags[i])
                return false;
        }

        return true;
    }

    /** You can read flags.length instead (but please don't modify it)
     * @return the number of elements in this array
     */
    public int size()
    {
        return flags.length;
    }

	public void clear()
	{
		Arrays.fill(flags, false);
		start =flags.length;
		end =0;
		nbSet =0;
	}

    /** for setting a flag that is already known to be the first true flag */
	public void setStart(final int i)
	{
		flags[i] =true;
		nbSet++;
		start =i;
	}

    /** for setting a flag that is not known to be the first or last, or not */
	public void set(final int i)
	{
		if (!flags[i])
		{
			flags[i] =true;
			nbSet++;
			if (start >i)
				start =i;
			if (end <i)
				end =i;
		}
	}

	/** for setting a flag that is not known to be the first or last, or not */
	public void unset(final int i)
	{
		if (flags[i])
		{
			flags[i] =false;
			nbSet--;
		}
	}
		
    /** this is for setting a flag that is already known to be the last true flag */
	public void setEnd(final int i)
	{
		flags[i] =true;
		nbSet++;
		end =i;
	}

    /** clears the array then sets the given flag */
	public void setOnly(final int i)
	{
        if (start <flags.length)
            Arrays.fill(flags, start, end, false);
		nbSet =1;
		start =i;
		end =i;
		flags[i] =true;
	}

	public void setAll()
	{
		start =0;
		end =flags.length -1;
		Arrays.fill(flags, true);
		nbSet =flags.length;
	}

	/**
	 * Experimental.  Returns a new BitFlags with flags set as the logical AND of both BitFlags.
     * The length of both must be the same.
	 * @param other BitFlags to be ANDed with this BitFlags. Must not be null.
	 * @return new BitFlags representing the logical AND of the two
	 */
	public BitFlags and(final BitFlags other)
	{
		final BitFlags result =new BitFlags(flags.length);
		if (this.nbSet >0 &&other.nbSet >0)
		{
            // setup outer union bounds
			int i =this.start >other.start ?this.start :other.start;
			final int endI =this.end <other.end ?this.end :other.end;
            // find the first common set bit
			for (; i <=endI; i++)
			{
				if (this.flags[i] &&other.flags[i])
				{
                    result.flags[i] =true;
                    result.nbSet++;
                    result.start =i;
					break;
				}
			}
            // find any remaining common bits
			for (; i <=endI; i++)
			{
				if (this.flags[i] &&other.flags[i])
				{
                    result.flags[i] =true;
                    result.nbSet++;
                    result.end =i;
				}
			}
            if (result.end <result.start)
                result.end =result.start;
		}
		return result;
	}

}
