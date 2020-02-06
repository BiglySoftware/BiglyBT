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
public class MovingImmediateAverage implements Average {

   private final int periods;
   private double data[];
   private int pos = 0;
   private double total;


   /**
    * Create a new moving average.
    */
   public MovingImmediateAverage(int periods) {
      this.periods = periods;
      this.data = new double[periods];
   }

   @Override
   public void
   reset()
   {
	   pos 		= 0;
	   total 	= 0;
	   data 	= new double[periods];
   }

   /**
    * Update average and return average-so-far.
    */
   @Override
   public double update(final double newValue) {

	  total -= data[pos%periods];
	  total += newValue;

      data[pos++%periods] = newValue;

      if ( pos==Integer.MAX_VALUE){
    	  pos = pos%periods;
      }
      return calculateAve();
   }

   public double[]
   getValues()
   {
	  double[]	res = new double[periods];
	  int	p = pos;
	  for (int i=0;i<periods;i++){
		  res[i] = data[p++%periods];
	  }
	  return( res );
   }

   /**
    * Return average-so-far.
    */
   @Override
   public double getAverage() { return calculateAve(); }

   public int getPeriods(){
	   return( periods );
   }

   public int getSampleCount(){
	   return( pos>periods?periods:pos );
   }

   public double getSum(){
	   return( total );
   }
   
   private double calculateAve() {
      int	lim = pos>periods?periods:pos;
      if ( lim == 0 ){
    	  return( 0 );
      }else{
    	  if ( pos % periods == 0 ){
    		  	// resync in case total has become inaccurate
    		  double sum = 0.0;
    		  for (int i=0; i < lim; i++) {
    			  sum += data[i];
    		  }
    		  total = sum;
    	  }
	      return total / lim;
      }
   }

}
