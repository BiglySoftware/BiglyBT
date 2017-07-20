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
 * Implements an exponential moving average.
 */
public class ExponentialMovingAverage implements Average {

   private final float weight;
   private double prevEMA;

   /**
    * Create a new exponential moving average which smooths over the
    * given number of periods.
    */
   public ExponentialMovingAverage(final int periods) {
      if (periods < 1) {
         System.out.println("ExponentialMovingAverage:: ERROR: bad periods: " + periods);
      }
      this.weight = 2 / (float)(1 + periods);
      this.prevEMA = 0;
   }

   /**
    * Create a new exponential moving average, using the given
    * smoothing rate weight.
    */
   public ExponentialMovingAverage(final float weight) {
      if ((weight < 0.0) || (weight > 1.0)) {
         System.out.println("ExponentialMovingAverage:: ERROR: bad weight: " + weight);
      }
      this.weight = weight;
      this.prevEMA = 0;
   }

   @Override
   public void reset(){

	  this.prevEMA = 0;
   }

   /**
    * Update average and return average-so-far.
    */
   @Override
   public double update(final double newValue) {
      prevEMA = (weight * (newValue - prevEMA)) + prevEMA;
      return prevEMA;
   }


   /**
    * Return average-so-far.
    */
   @Override
   public double getAverage() { return prevEMA; }

}