package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;

/*
 * Created on May 8, 2007
 * Created by Alan Snyder
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

/**
 * Keeps the ping time stats for a single source. Should calculate averages for the data.
 *
 */
public class PingSourceStats
{

    final SpeedManagerPingSource source;
    double currPing;
    final Average shortTerm = AverageFactory.MovingImmediateAverage( 3 );
    final Average medTerm = AverageFactory.MovingImmediateAverage( 6 );
    final Average longTerm = AverageFactory.MovingImmediateAverage( 10 );

    final Average forChecks = AverageFactory.MovingImmediateAverage( 100 );

    public PingSourceStats(SpeedManagerPingSource _source){
        source = _source;
    }

    public void madeChange(){
        //want to make all the values NAN until it is ready to compare again.

    }

    public void addPingTime(int ping){
        currPing = (double) ping;
        shortTerm.update( (double)ping );
        medTerm.update( (double)ping );
        longTerm.update( (double)ping );
    }

    /**
     * Speculative method to see if it can determine a trend. The larger the number
     * the stronger the trend.
     * @return current - integer. A positive number is an increasing trend. A negative number is a decreasing trend.
     */
    public int getTrend(){

        int retVal = 0;

        //based on current ping values.
        if(currPing<0.0){
            retVal--;
        }else{
            if( currPing < shortTerm.getAverage() ){
                retVal++;
            }else{
                retVal--;
            }

            if( currPing < medTerm.getAverage() ){
                retVal++;
            }else{
                retVal--;
            }

            if( currPing < longTerm.getAverage() ){
                retVal++;
            }else{
                retVal--;
            }
        }

        //compare shortTerm and medium term averages.
        if(shortTerm.getAverage() < medTerm.getAverage() ){
            retVal++;
        }else{
            retVal--;
        }

        //compare short-term with long term.
        if(shortTerm.getAverage() < longTerm.getAverage() ){
            retVal++;
        }else{
            retVal--;
        }

        //compare medium-term with long-term.
        if(medTerm.getAverage() < longTerm.getAverage() ){
            retVal++;
        }else{
            retVal--;
        }

        //modify results based on absolute ping values.
        final int ABSOLUTE_GOOD_PING_VALUE = 30;
        if(currPing<ABSOLUTE_GOOD_PING_VALUE){
            retVal++;
        }
        if(shortTerm.getAverage()<ABSOLUTE_GOOD_PING_VALUE){
            retVal++;
        }
        if(medTerm.getAverage()<ABSOLUTE_GOOD_PING_VALUE){
            retVal++;
        }
        if(longTerm.getAverage()<ABSOLUTE_GOOD_PING_VALUE){
            retVal++;
        }

        //modify results based on absolute ping values that are too long.
        final int ABSOLUTE_BAD_PING_VALUE = 300;
        if(currPing>ABSOLUTE_BAD_PING_VALUE){
            retVal--;
        }
        if(shortTerm.getAverage()>ABSOLUTE_BAD_PING_VALUE){
            retVal--;
        }
        if(medTerm.getAverage()>ABSOLUTE_BAD_PING_VALUE){
            retVal--;
        }
        if(longTerm.getAverage()>ABSOLUTE_BAD_PING_VALUE){
            retVal--;
        }

        return retVal;
    }//getTrend

    /**
     * Get the long-term average.
     * @return Average - longTerm
     */
    public Average getLongTermAve(){
        return longTerm;
    }

    /**
     * Get the average that should be used for checking ping times.
     * @return - ping time of history.
     */
    public Average getHistory(){
        return forChecks;
    }

}
