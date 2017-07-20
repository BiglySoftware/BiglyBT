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

import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SimpleTimer.TimerTickReceiver;
import com.biglybt.core.util.SystemTime;


/**
 * Generates different types of averages.
 */
public abstract class AverageFactory {

   /**
    * Create a simple running average.
    */
   public static RunningAverage RunningAverage() {
      return new RunningAverage();
   }

   /**
    * Create a moving average, that moves over the given number of periods.
    */
   public static MovingAverage MovingAverage(int periods) {
      return new MovingAverage(periods);
   }

   /**
    * Create a moving average, that moves over the given number of periods and gives immediate
    * results (i.e. after the first update of X the average will be X
    */

   public static MovingImmediateAverage MovingImmediateAverage(int periods) {
	      return new MovingImmediateAverage(periods);
	   }
   /**
    * Create an exponential moving average, smoothing over the given number
    * of periods, using a default smoothing weight value of 2/(1 + periods).
    */
   public static ExponentialMovingAverage ExponentialMovingAverage(int periods) {
      return new ExponentialMovingAverage(periods);
   }

   /**
    * Create an exponential moving average, with the given smoothing weight.
    * Larger weigths (closer to 1.0) will give more influence to
    * recent data and smaller weights (closer to 0.00) will provide
    * smoother averaging (give more influence to older data).
    */
   public static ExponentialMovingAverage ExponentialMovingAverage(float weight) {
      return new ExponentialMovingAverage(weight);
   }

   /**
    * Creates an auto-updating immediate moving average that will auto-deactivate average maintenance
    * when values are not being extracted from it, auto-reactivate when required etc.
    * @param periods
    * @param adapter
    * @param instance
    * @return
    */

   public static <T> long
   LazySmoothMovingImmediateAverage(
		  final LazyMovingImmediateAverageAdapter<T>		adapter,
		  final T											instance )
   {
	   int update_window 	= GeneralUtils.getSmoothUpdateWindow();
	   int update_interval	= GeneralUtils.getSmoothUpdateInterval();

	   return( LazyMovingImmediateAverage( update_window / update_interval, update_interval, adapter, instance ));
   }

   public static <T> long
   LazyMovingImmediateAverage(
		  final int 										periods,
		  final int											interval_secs,
		  final LazyMovingImmediateAverageAdapter<T>		adapter,
		  final T											instance )
   {
	   LazyMovingImmediateAverageState current = adapter.getCurrent( instance );

	   if ( current == null ){

		   final LazyMovingImmediateAverageState state = current = new LazyMovingImmediateAverageState();

		   SimpleTimer.addTickReceiver(
				  new TimerTickReceiver() {

					@Override
					public void
					tick(
						long	mono_now,
						int 	tick_count )
					{
						long	now = SystemTime.getMonotonousTime();

						if ( now - state.last_read > 60*1000 ){

							SimpleTimer.removeTickReceiver( this );

							adapter.setCurrent( instance, null );

						}else if ( tick_count % interval_secs == 0 ){

							long value = adapter.getValue( instance );

							long last 	= state.last_value;
							long diff	= value - last;

							if ( last >= 0 && diff >= 0 ){

								MovingImmediateAverage average = state.average;

								if ( diff == 0 ){

									state.consec_zeros++;

								}else{

									state.consec_zeros = 0;
								}

								if ( average == null ){

									if ( diff > 0 ){

										state.average = average = MovingImmediateAverage( periods );

										int	zeros_to_do = Math.min( state.consec_zeros, periods );

										for ( int i=0;i<zeros_to_do;i++){

											average.update( 0 );
										}
									}
								}

								if ( average != null ){

									long ave = (long)average.update( diff );

									if ( ave == 0 && average.getSampleCount() >= periods ){

										// looks pretty dead

										state.average = null;
									}
								}
							}

							state.last_value = value;
						}
					}
				});

		   adapter.setCurrent( instance, current );

	   }else{

		   current.last_read = SystemTime.getMonotonousTime();
	   }

	   MovingImmediateAverage average = current.average;

	   if ( average == null ){

		   return( 0 );

	   }else{

		   return((long)average.getAverage() / interval_secs );
	   }
   }

   public interface
   LazyMovingImmediateAverageAdapter<T>
   {
	   public LazyMovingImmediateAverageState
	   getCurrent(
			  T	instance );

	   public void
	   setCurrent(
			  T									instance,
			  LazyMovingImmediateAverageState	average );

	   public long
	   getValue(
			  T		instance );
   }

   public static class
   LazyMovingImmediateAverageState
   {
	   private MovingImmediateAverage	average;
	   private int						consec_zeros;
	   private long						last_value 	= -1;
	   private long						last_read	= SystemTime.getMonotonousTime();
   }
}
