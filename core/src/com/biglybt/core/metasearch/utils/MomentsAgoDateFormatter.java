/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
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
 */

package com.biglybt.core.metasearch.utils;

import java.text.DateFormat;
import java.util.*;

/**
* Improvement based on AZWEB-318.
*
* @version 3.2.1.0 6/19/2007
* @since 3.2.1.0 6/19/2007
*/
public class MomentsAgoDateFormatter {

   // A list of id's that we use in the two maps to ensure we have valid refs
   private static final Integer ID_YEAR = new Integer(Calendar.YEAR);
   private static final Integer ID_MONTH = new Integer(Calendar.MONTH);
   private static final Integer ID_WEEK_OF_YEAR = new Integer(Calendar.WEEK_OF_YEAR);
   private static final Integer ID_DAY = new Integer(Calendar.DAY_OF_MONTH);
   private static final Integer ID_HOUR_OF_DAY = new Integer(Calendar.HOUR_OF_DAY);
   private static final Integer ID_MINUTE = new Integer(Calendar.MINUTE);
   private static final Integer ID_SECOND = new Integer(Calendar.SECOND);

   // A list of units we're comparing.
   private static final Long MS_IN_YEAR   = new Long(31536000000L);
   private static final Long MS_IN_MONTH  = new Long(2678400000L);
   private static final Long MS_IN_WEEK   = new Long(604800000L);
   private static final Long MS_IN_DAY    = new Long(86400000L);
   private static final Long MS_IN_HOUR   = new Long(3600000L);
   private static final Long MS_IN_MINUTE = new Long(60000L);
   private static final Long MS_IN_SECOND = new Long(1000L);

   // A few externalized strings to display to the user
   private static final String AGO = " ago";
   private static final String PLURAL = "s";

   private static final Map CONVERSION_MAP = new HashMap();

   // Build the map at system start
   static {
       CONVERSION_MAP.put(ID_YEAR, MS_IN_YEAR);
       CONVERSION_MAP.put(ID_MONTH, MS_IN_MONTH);
       CONVERSION_MAP.put(ID_WEEK_OF_YEAR, MS_IN_WEEK);
       CONVERSION_MAP.put(ID_DAY, MS_IN_DAY);
       CONVERSION_MAP.put(ID_HOUR_OF_DAY, MS_IN_HOUR);
   }

   private static final Map UNIT_MAP =
       new HashMap();

   // Build the map at system start
   static {
       UNIT_MAP.put(ID_YEAR, " yr");
       UNIT_MAP.put(ID_MONTH, " mo");
       UNIT_MAP.put(ID_WEEK_OF_YEAR, " wk");
       UNIT_MAP.put(ID_DAY, " day");
       UNIT_MAP.put(ID_HOUR_OF_DAY, " hr");
   }

   /**
    * Returns "x <units of time> ago on <formatted pastDate>" by comparing the
    * given pastDate with the current time.  All formats are converted to GMT
    * time.  In the future the user might have their own locale, in which case
    * we will display the time in their own locale (neat!)
    *
    * @param pastDate A date in the past
    * @param format The format for the pastDate
    * @return "x <units of time> ago on <formatted pastDate in GMT time>"
    */
   public static String getMomentsAgoString(Date pastDate, DateFormat format) {
       String timeAgo = getMomentsAgoString(pastDate);
       format.setTimeZone(new SimpleTimeZone(0, "GMT"));
       if (timeAgo.length() > 0) timeAgo = timeAgo.concat(" on ");
       return timeAgo.concat(format.format(pastDate));
   }

   /**
    * Returns "x <units of time> ago on <formatted pastDate>" by comparing the
    * given pastDate with the current time.
    *
    * @param pastDate A default locale date in the past
    * @return "x <units of time> ago"
    */
   public static String getMomentsAgoString(Date pastDate) {
       Calendar then = Calendar.getInstance();
       then.setTime(pastDate);
       Calendar now = Calendar.getInstance();
       String result = null;
       result = handleUnit(then, now, ID_YEAR);
       if (result == null) {
           result = handleUnit(then, now, ID_MONTH);
           if (result == null) {
               result = handleUnit(then, now, ID_WEEK_OF_YEAR);
               if (result == null) {
                   result = handleUnit(then, now, ID_DAY);
                   if (result == null) {
                       result = handleUnit(then, now, ID_HOUR_OF_DAY);
                       if (result == null) {
                           return "< 1 h";
                       }
                   }
               }
           }
       }
       return result;
   }

   /**
    * Checks to see if the unit we're comparing is less than the difference of
    * the given "then" and "now" dates in milliseconds.
    *
    * @param then The date we're evaluating
    * @param now The current time
    * @param field The field which we're evaluating ("units")
    * @return null if then is 0 "units" from now, otherwise a displayable
    *         string that will notify the user how long ago then was from now.
    */
   private static String handleUnit(Calendar then, Calendar now,
           Integer field) {
       String result = null;
       long diff = now.getTimeInMillis() - then.getTimeInMillis();
       long comparison = ((Long)CONVERSION_MAP.get(field)).longValue();
       if (diff > comparison) {
           long timeAgo = diff / comparison;
           result = String.valueOf(timeAgo).concat((String)UNIT_MAP.get(field));
           //if (timeAgo > 1) result = result.concat(PLURAL);
           //result = result.concat(AGO);
       }
       return result;
   }
}
