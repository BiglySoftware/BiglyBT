/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.core.dht.netcoords.vivaldi.ver1.impl;

import java.io.DataOutputStream;
import java.io.IOException;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.Coordinates;
import com.biglybt.core.dht.netcoords.vivaldi.ver1.VivaldiPosition;



/**
 *
 * Vivaldi Papers :
 * http://www.sigcomm.org/sigcomm2004/papers/p426-dabek111111.pdf
 *
 */

public class VivaldiPositionImpl implements VivaldiPosition{

  private static final float cc = 0.25f;
  private static final float ce = 0.5f;
  private static final float initial_error	= 10f;

  private HeightCoordinatesImpl coordinates;
  private float error;
  private int nbUpdates;

  public VivaldiPositionImpl(HeightCoordinatesImpl coordinates) {
    this.coordinates = coordinates;
    error = initial_error;
  }

  @Override
  public byte
  getPositionType()
  {
	  return( DHTNetworkPosition.POSITION_TYPE_VIVALDI_V1 );
  }

  @Override
  public Coordinates getCoordinates() {
    return coordinates;
  }

	@Override
	public double[] getLocation() {
		Coordinates coords = getCoordinates();

		return( coords.getCoordinates());
	}
  @Override
  public float getErrorEstimate() {
   return error;
  }

  @Override
  public void setErrorEstimate(float error) {
    this.error = error;
   }

  @Override
  public void
  update(float rtt,Coordinates cj,float ej)
  {
	  if ( valid(rtt) && valid(ej) && cj.isValid()){

		 // System.out.println( "accepted vivaldi update:" + rtt + "/" + cj + "/" + ej );

	    //Ensure we have valid data in input (clock changes lead to crazy rtt values)
	    if(rtt <= 0 || rtt > 5*60*1000 ) return;
	    if(error + ej == 0) return;

	    //Sample weight balances local and remote error. (1)
	    float w = error / (ej + error);

	    //Real error
	    float re = rtt - coordinates.distance(cj);

	    //Compute relative error of this sample. (2)
	    float es = Math.abs(re) / rtt;

	    //Update weighted moving average of local error. (3)

	    float new_error = es * ce * w + error * (1 - ce * w);

	    	//Update local coordinates. (4)

	    float delta = cc * w;

	    float scale = delta * re;

	    HeightCoordinatesImpl random_error = new HeightCoordinatesImpl((float)Math.random()/10,(float)Math.random()/10,(float)Math.random()/10);

	    HeightCoordinatesImpl new_coordinates = (HeightCoordinatesImpl)coordinates.add(coordinates.sub(cj.add(random_error)).unity().scale(scale));

	    if ( valid( new_error ) && new_coordinates.isValid()){

	    	coordinates = new_coordinates;

	    	error		= new_error > ERROR_MIN ? new_error : ERROR_MIN;

	    }else{

	    	/* not very interesting and occasionally happen...
	    	Debug.out( "VivaldiPosition: resetting as invalid: " +
	    				coordinates + "/" + error + " + " + rtt + "," + cj + "," + ej + "->" + new_coordinates + "/" + new_error );
	    	*/

	    	coordinates = new HeightCoordinatesImpl(0,0,0);
	    	error		= initial_error;
	    }

      if(! cj.atOrigin()) {
        nbUpdates++;
      }
      if(nbUpdates > CONVERGE_EVERY) {
        nbUpdates = 0;
        update(10,new HeightCoordinatesImpl(0,0,0),CONVERGE_FACTOR);
      }

	  }else{
		 // System.out.println( "rejected vivaldi update:" + rtt + "/" + cj + "/" + ej );
	  }
  }

  @Override
  public boolean
  isValid()
  {
	  return( (!Float.isNaN( getErrorEstimate())) && getCoordinates().isValid());
  }

  private boolean
  valid(
	float	f )
  {
	  return( !(Float.isInfinite( f ) || Float.isNaN( f )));
  }

  @Override
  public void update(float rtt, float[] data ){

	  update( rtt, new HeightCoordinatesImpl( data[0], data[1], data[2] ), data[3] );
  }

  @Override
  public float estimateRTT(Coordinates coordinates) {
    return this.coordinates.distance(coordinates);
  }

  @Override
  public float[] toFloatArray(){
	  return( new float[]{ coordinates.getX(), coordinates.getY(), coordinates.getH(), error });
  }

  @Override
  public void fromFloatArray(float[] data ){

	  coordinates = new HeightCoordinatesImpl( data[0], data[1], data[2] );

	  error			= data[3];
  }

  public String toString() {
    return coordinates +  " : " + error;
  }

  public boolean equals(Object arg0) {
   if(arg0 instanceof VivaldiPositionImpl) {
     VivaldiPositionImpl other = (VivaldiPositionImpl) arg0;
     if(other.error != error) return false;
     if(! other.coordinates.equals(coordinates)) return false;
     return true;
   }
   return false;
  }

  @Override
  public float
  estimateRTT(
	DHTNetworkPosition	_other )
  {
	  VivaldiPosition	other = (VivaldiPosition)_other;

	  Coordinates other_coords = other.getCoordinates();

	  if ( coordinates.atOrigin() || other_coords.atOrigin()){

		  return( Float.NaN );
	  }

	  return( estimateRTT( other_coords ));
  }

  @Override
  public void
  update(
	byte[]				_other_id,
	DHTNetworkPosition	_other,
	float				rtt )
  {
    VivaldiPositionImpl	other = (VivaldiPositionImpl)_other;

	Coordinates other_coords = other.getCoordinates();

	update( rtt,  other_coords, other.getErrorEstimate());
  }

  @Override
  public int
  getSerialisedSize()
  {
	  return( 16 ); 	// 4 floats
  }

  @Override
  public void
  serialise(
	 DataOutputStream	os )

  	throws IOException
  {
	float[]	data = toFloatArray();

	for (int i=0;i<data.length;i++){

	  os.writeFloat( data[i] );
	}
  }
}
