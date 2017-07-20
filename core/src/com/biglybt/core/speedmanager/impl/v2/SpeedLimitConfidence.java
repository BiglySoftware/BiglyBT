package com.biglybt.core.speedmanager.impl.v2;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;

/*
 * Created on Jun 5, 2007
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

public class SpeedLimitConfidence
    implements Comparable
{
    public static final SpeedLimitConfidence NONE = new SpeedLimitConfidence("NONE",0, SpeedManagerLimitEstimate.TYPE_UNKNOWN);
    public static final SpeedLimitConfidence LOW = new SpeedLimitConfidence("LOW",1, SpeedManagerLimitEstimate.TYPE_ESTIMATED);
    public static final SpeedLimitConfidence MED = new SpeedLimitConfidence("MED",2, SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED);
    public static final SpeedLimitConfidence HIGH = new SpeedLimitConfidence("HIGH",3, SpeedManagerLimitEstimate.TYPE_MEASURED_MIN);
    public static final SpeedLimitConfidence ABSOLUTE = new SpeedLimitConfidence("ABSOLUTE",4, SpeedManagerLimitEstimate.TYPE_MANUAL);

    private final String name;
    private final int order;
    private final float estimateType;

    private SpeedLimitConfidence(String _name, int _order, float _speedLimitEstimateType){
        name = _name;
        order = _order;
        estimateType = _speedLimitEstimateType;
    }

    public static SpeedLimitConfidence convertType( float type ){
        if( type <= SpeedLimitConfidence.NONE.estimateType ){
            return NONE;
        }else if( type <= SpeedLimitConfidence.LOW.estimateType ){
            return LOW;
        }else if( type <= SpeedLimitConfidence.MED.estimateType ){
            return MED;
        }else if( type <= SpeedLimitConfidence.HIGH.estimateType ){
            return HIGH;
        }else{
            return ABSOLUTE;
        }
    }//convertType


    /**
     * Turns a string into a SpeedLimitConfidence class.
     * @param setting - String is expected to be one of: NONE, LOW, MED, HIGH, ABSOLUE.
     * @return - class corresponding to String. If it isn't one of the know values then NONE is returned.
     */
    public static SpeedLimitConfidence parseString(String setting){
        SpeedLimitConfidence retVal = SpeedLimitConfidence.NONE;

        if(setting==null){
            return retVal;
        }

        if( "NONE".equalsIgnoreCase(setting) ){
            return SpeedLimitConfidence.NONE;
        }else if("LOW".equalsIgnoreCase(setting)){
            return SpeedLimitConfidence.LOW;
        }else if("MED".equalsIgnoreCase(setting)){
            return SpeedLimitConfidence.MED;
        }else if("HIGH".equalsIgnoreCase(setting)){
            return SpeedLimitConfidence.HIGH;
        }else if("ABSOLUTE".equalsIgnoreCase(setting)){
            return SpeedLimitConfidence.ABSOLUTE;
        }

        return retVal;
    }

    public float asEstimateType(){
        return estimateType;
    }

    public static String asEstimateTypeString(float type){
        //ToDo: move to proper class. This is to do something now.
        if( type==SpeedManagerLimitEstimate.TYPE_UNKNOWN ){
            return "Unknown";
        }else if( type==SpeedManagerLimitEstimate.TYPE_ESTIMATED){
            return "Estimate";
        }else if( type==SpeedManagerLimitEstimate.TYPE_MANUAL){
            return "Fixed";
        }
        return "";
    }

    public String getString(){
        return name;
    }


    private static final String MESSAGE_BUNDLE_PREFIX = "SpeedTestWizard.name.conf.level.";
    /**
     * Get the internationalized string for UI panels and
     * drop downs.
     * @return - Internationalized String.
     */
    public String getInternationalizedString(){

        return MessageText.getString( MESSAGE_BUNDLE_PREFIX + name.toLowerCase() );
    }

    /**
     * compareTo to with boolean syntax.
     *
     * @param limitConf -
     * @return - true if greater then, false if equal or less.
     */
    public boolean isGreater(SpeedLimitConfidence limitConf)
    {
        if( this.compareTo(limitConf)>0 ){
            return true;
        }
        return false;
    }

    /**
     * Comparable interface
     * @param limitConf - Item to compare with.
     * @return -
     */
    public int compareTo(SpeedLimitConfidence limitConf){
        return  (order - limitConf.order);
    }

    /**
     * Comparable interface.
     * @param obj -
     * @return -
     */
    @Override
    public int compareTo(Object obj){
        if( !(obj instanceof SpeedLimitConfidence)  ){
            throw new ClassCastException("Only comparable to SpeedLimitConfidence class.");
        }
        SpeedLimitConfidence casted = (SpeedLimitConfidence) obj;
        return compareTo(casted);
    }

}
