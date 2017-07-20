/*
 * Created on Oct 08, 2004
 * Created by Alon Rohter
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
package com.biglybt.core.util.average;

/**
 * Implements a basic moving average.
 */
public class MovingAverage implements Average {

   private final int periods;
   private double data[];
   private int pos = 0;
   private double total;


   /**
    * Create a new moving average.
    */
   public MovingAverage(int periods) {
      this.periods = periods;
      reset();
   }

   	@Override
    public void reset(){
   	 pos 	= 0;
   	 total 	= 0;
   	 data 	= new double[periods];
	}
   /**
    * Update average and return average-so-far.
    */
   @Override
   public double update(final double newValue) {
	  total -= data[pos];
	  total += newValue;

      data[pos] = newValue;
      pos++;
      if (pos == periods) pos = 0;
      return calculateAve();
   }

   /**
    * Return average-so-far.
    */
   @Override
   public double getAverage() { return calculateAve(); }


   private double calculateAve() {
	  if ( pos == 0 ){
		  // resync (I'd prefer not to do this but just in case)
	      double sum = 0.0;
	      for (int i=0; i < periods; i++) {
	         sum += data[i];
	      }
	      total = sum;
	  }

	  return total / periods;
   }
}
